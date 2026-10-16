package mods.eln.modern

import mods.eln.misc.SlewLimiter
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.*

/** Original mechanical slew rates and the arm-before-elevate / lower-before-disarm interlock. */
class TurretMotion(p: PartState) {
    val yaw=SlewLimiter(70f)
    val gun=SlewLimiter(3f,.5f)
    val elevation=SlewLimiter(100f)
    var shooting=0f
    init {
        for((name,slew) in listOf("Yaw" to yaw,"Gun" to gun,"Elevation" to elevation)) {
            slew.position=p.gridState.getFloat("turret$name");slew.target=p.gridState.getFloat("turret${name}Target")
        }
    }
    fun targets(yaw: Float,gun: Float,elevation: Float,seeking: Boolean) {
        this.yaw.target=yaw.coerceIn(-70f,70f);this.gun.target=gun.coerceIn(0f,1f)
        if(elevation.isFinite())this.elevation.target=elevation.coerceIn(-40f,70f)
        this.yaw.setSlewRate(if(seeking)40f else 70f)
    }
    fun reached()=yaw.targetReached(5f) && gun.targetReached() && elevation.targetReached(2f)
    fun step(dt: Float,enabled: Boolean) {
        if(!enabled) { shooting=0f;return }
        yaw.step(dt);if(elevation.position==0f)gun.step(dt);if(gun.position==1f)elevation.step(dt)
        shooting=maxOf(0f,shooting-dt)
    }
    fun save(p: PartState) {
        for((name,slew) in listOf("Yaw" to yaw,"Gun" to gun,"Elevation" to elevation)) {
            p.gridState.putFloat("turret$name",slew.position);p.gridState.putFloat("turret${name}Target",slew.target)
        }
        p.gridState.putFloat("turretShooting",shooting)
    }
}

object Turrets {
    fun matches(p: PartState,entity: LivingEntity): Boolean {
        val filter=(PartInventory.peek(p,0).item as? MaterialItem)?.definition?.id
        val selected=when(filter) { "player_filter" -> entity is Player;"monster_filter" -> entity is Enemy;"animal_filter" -> entity is Animal;else -> false }
        return if(p.enabled)!selected else selected
    }
    fun configure(node: FaceNodeEntity,p: PartState,watts: Double,spare: Boolean) {
        if(!watts.isFinite() || watts !in 100.0..5000.0)return
        p.value=watts;p.enabled=spare;node.changedTopology()
    }
}

/** Original 800 V defence turret, paid 1000 J / five-damage shots and real entity filters. */
class TurretRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private val world=e.circuit.level
    private val node=e.node
    private val load=ElectricalLoad().apply { serialResistance=PartDefinition.byId.getValue("high_voltage_cable").resistance;state=p.voltage }
    private val resistor=Resistor(load,null).setResistance(1e9)
    private val motion=TurretMotion(p)
    private var phase=p.gridState.getInt("turretPhase").coerceIn(0,4)
    private var waiting=p.gridState.getDouble("turretWait")
    private var scan=p.gridState.getDouble("turretScan")
    private var target=runCatching { UUID.fromString(p.gridState.getString("turretTarget")) }.getOrNull()
    private var yawTarget=p.gridState.getFloat("turretYawTarget")
    private var gunTarget=p.gridState.getFloat("turretGunTarget")
    private var elevationTarget=p.gridState.getFloat("turretElevationTarget")
    init {
        e.circuit.root.addState(load);e.circuit.root.addComponent(resistor);e.pins[1]=load
        if(phase !in 2..4) { gunTarget=0f;elevationTarget=0f }
    }
    private fun sound(id: String) {
        p.gridState.putInt("turretSound_$id",p.gridState.getInt("turretSound_$id")+1)
        world.playSound(null,node.blockPos,OriginalSounds.event(id),net.minecraft.sounds.SoundSource.BLOCKS,when(id) { "turretactivated","turretdeactivated" -> .5f;"turretfire","turretkill" -> .4f;else -> 1f },1f)
    }
    private fun seeking() { phase=2;target=null;gunTarget=0f;elevationTarget=0f;yawTarget=70f }
    private fun angle(entity: LivingEntity): Double {
        val delta=entity.position().subtract(Vec3.atCenterOf(node.blockPos));val edges=FaceBasis.edges(p);val u=edges[0];val f=edges[3]
        return -Math.toDegrees(atan2(delta.x*u.stepX+delta.z*u.stepZ,delta.x*f.stepX+delta.z*f.stepZ))
    }
    private fun visible(entity: LivingEntity)=LoadedRay.visible(world,Vec3.atCenterOf(node.blockPos),entity.eyePosition)
    private fun liveTarget()=(target?.let { world.getEntity(it) } as? LivingEntity)?.takeIf { it.isAlive && !it.isRemoved && !it.isSpectator && Turrets.matches(p,it) }
    fun beforeTick() {
        val watts=when(phase) { 2 -> 25.0;3,4 -> 25+p.value.coerceIn(100.0,5000.0);else -> 0.0 }
        resistor.resistance=if(watts>0 && p.progress<1000 && load.voltage>1)maxOf(1e-6,load.voltage*load.voltage/watts)else 1e9
    }
    fun afterStep(dt: Double) {
        p.voltage=load.voltage;p.voltageB=0.0;p.current=load.current
        val work=resistor.power.coerceAtLeast(0.0)*dt;p.energy+=work
        val stored=minOf(work,(1000-p.progress).coerceAtLeast(0.0));p.progress+=stored
        p.gridState.putDouble("turretDissipatedJ",p.gridState.getDouble("turretDissipatedJ")+work-stored)
        p.active=phase>=2;p.powerFactor=if(p.active)resistor.power/(25+p.value.coerceIn(100.0,5000.0))else 0.0;p.processFraction=p.progress/1000
    }
    fun afterTick() {
        val previousPhase=phase;val previousShots=p.gridState.getInt("turretShots")
        val armedBefore=phase>=2
        if(phase>=2 && load.voltage>1050 && mods.eln.config.ForgePortConfig.cableVoltageProtection.get()) { MachineFailure.destroy(node,p,3f);return }
        if(phase>=2 && load.voltage<540) { phase=1;waiting=5.0;target=null;gunTarget=0f;elevationTarget=0f;sound("turretdeactivated") }
        when(phase) {
            0 -> if(load.voltage>=660) { seeking();sound("turretactivated") }
            1 -> { waiting-=.05;if(waiting<=0)phase=0 }
            2 -> {
                if(motion.yaw.position>=70)yawTarget=-70f else if(motion.yaw.position<=-70)yawTarget=70f
                scan+=.05
                if(scan>=.25) {
                    scan=0.0
                    val candidate=world.getEntitiesOfClass(LivingEntity::class.java,AABB(node.blockPos).inflate(12.0)).firstOrNull { entity ->
                        entity.isAlive && !entity.isSpectator && Turrets.matches(p,entity) && abs(angle(entity))<70 && abs(angle(entity)-motion.yaw.position)<15 && visible(entity)
                    }
                    if(candidate!=null) { target=candidate.uuid;phase=3;gunTarget=1f;sound("turretfire") }
                }
            }
            3 -> {
                val entity=liveTarget()
                if(entity==null || abs(entity.x-node.blockPos.x)>15 || abs(entity.z-node.blockPos.z)>15 || abs(angle(entity))>70 || !visible(entity))seeking()
                else {
                    val dx=entity.x-node.blockPos.x-.5;val dz=entity.z-node.blockPos.z-.5
                    val dy=entity.eyeY-node.blockPos.y-.75;val elevation=Math.toDegrees(asin(dy/sqrt(dx*dx+dz*dz)))
                    yawTarget=angle(entity).toFloat();if(elevation.isFinite())elevationTarget=elevation.toFloat().coerceIn(-40f,70f)
                    motion.targets(yawTarget,gunTarget,elevationTarget,false)
                    if(motion.gun.position==1f && motion.reached() && p.progress>=1000) {
                        // Debit at the shot, so saving between Shoot.enter and Shoot.leave cannot replay paid work.
                        p.progress-=1000;p.gridState.putDouble("turretShotJ",p.gridState.getDouble("turretShotJ")+1000)
                        entity.invulnerableTime=0
                        val damaged=entity.hurt(object : DamageSource("eln_laser") {},5f)
                        p.gridState.putInt("turretShots",p.gridState.getInt("turretShots")+1)
                        if(damaged)p.gridState.putInt("turretHits",p.gridState.getInt("turretHits")+1)
                        if(!entity.isAlive)sound("turretkill")
                        motion.shooting=.1f;phase=4;sound("lasergun")
                        mods.eln.modern.lighting.LightSpotBlock.illuminate(world,node.blockPos.relative(FaceBasis.edges(p)[3]),15,2)
                    }
                }
            }
            4 -> { if(liveTarget()==null)seeking()else { phase=3;gunTarget=1f } }
        }
        val armed=phase>=2
        if(armedBefore && !armed)p.active=false
        motion.targets(yawTarget,gunTarget,elevationTarget,phase==2);motion.step(.05f,armed);persist()
        if(previousPhase!=phase || previousShots!=p.gridState.getInt("turretShots"))node.sync()
    }
    fun persist() {
        p.gridState.putInt("turretPhase",phase);p.gridState.putBoolean("turretArmed",phase>=2);p.gridState.putBoolean("turretSeeking",phase==2)
        p.gridState.putDouble("turretWait",waiting);p.gridState.putDouble("turretScan",scan)
        if(target==null)p.gridState.remove("turretTarget")else p.gridState.putString("turretTarget",target.toString())
        motion.save(p)
    }
}
