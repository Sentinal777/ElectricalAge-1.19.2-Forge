package mods.eln.modern

import mods.eln.Eln
import mods.eln.config.ForgePortConfig
import mods.eln.misc.RcInterpolator
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.nbt.NbtElectricalGateOutputProcess
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.BaseFireBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

data class EnvironmentSpec(val kind: String,val range: Double,val outputEdge: Int)

/** Original humidity, anemometer, motion detector and wired/battery fire detector controllers. */
class EnvironmentSensorRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private val spec=p.definition.environment!!
    private val level=e.circuit.level
    private val batteryPowered=spec.kind=="FIRE_BUZZER"
    private val load=if(batteryPowered)null else ElectricalLoad().apply { serialResistance=.05;state=p.voltage }
    private val output=load?.let { NbtElectricalGateOutputProcess("environment",it).apply { coulombs=Eln.gateOutputCurrent*.05/ForgePortConfig.simulationSteps.get()/Eln.SVU } }
    private val rc1=RcInterpolator(when(spec.kind) { "HUMIDITY"->3f;"ENTITY"->.4f;else->.6f }).apply { readFromNBT(p.thermalState,"envRC1") }
    private val rc2=RcInterpolator(.4f).apply { readFromNBT(p.thermalState,"envRC2") }
    private var timer=p.thermalState.getDouble("envTimer")
    private var signal=p.thermalState.getDouble("envOutput")
    init {
        if(load!=null && output!=null) { e.circuit.root.addState(load);e.circuit.root.addComponent(output);e.pins[spec.outputEdge]=load }
    }
    fun beforeTick() {
        val powered=if(batteryPowered) {
            val battery=PartInventory.get(e.node,p).getStackInSlot(0);val joules=ElectricalItems.energy(battery);val watts=20000.0/(3600*40)
            if(joules<watts*.05*4)false else {
                ElectricalItems.setEnergy(battery,joules-watts*.05);p.energy+=watts*.05;e.node.setChanged();true
            }
        } else true
        p.thermalState.putBoolean("envPowered",powered)
        if(!powered) { p.thermalState.putBoolean("envFire",false);signal=0.0;return }
        timer+=.05
        when(spec.kind) {
            "HUMIDITY"->if(timer>.2) {
                timer-=.2;val climate=mods.eln.modern.thermal.Climate.sample(level,e.node.blockPos)
                rc1.target=(climate.relativeHumidityPercent/100).toFloat();rc1.step(.05f);signal=rc1.get().toDouble()
                p.temperature=climate.temperatureCelsius;p.thermalState.putDouble("envMeasured",climate.relativeHumidityPercent)
            }
            "WIND"->if(timer>.2) {
                timer-=.2;val wind=WorldWind.speed(level,e.node.blockPos.y)
                p.thermalState.putDouble("envMeasured",wind);signal=wind/spec.range
            }
            "ENTITY"->{
                if(timer>.2) { timer-=.2;rc1.target=entityMotion().toFloat() }
                rc1.step(.05f);rc2.target=rc1.get();rc2.step(.05f);signal=rc2.get().toDouble()
            }
            "FIRE","FIRE_BUZZER"->{
                if(timer>=.5) { timer=0.0;p.thermalState.putBoolean("envFire",fire()) }
                if(!batteryPowered) { rc1.target=if(p.thermalState.getBoolean("envFire"))1f else 0f;rc1.step(.05f);signal=rc1.get().toDouble() }
            }
        }
    }
    private fun entityMotion(): Double {
        val filter=(PartInventory.get(e.node,p).getStackInSlot(0).item as? MaterialItem)?.definition?.id
        val origin=Vec3.atCenterOf(e.node.blockPos)
        val nearby=level.getEntitiesOfClass(LivingEntity::class.java,AABB(e.node.blockPos).inflate(spec.range)).filter { entity ->
            entity.isAlive && when(filter) { "player_filter"->entity is Player;"monster_filter"->entity is Enemy;"animal_filter"->entity is Animal;else->true }
        }
        var value=0.0
        val ids=hashSetOf<java.util.UUID>()
        for(entity in nearby) {
            ids.add(entity.uuid);val old=p.sensedEntityPositions.put(entity.uuid,entity.position()) ?: continue
            val distance=origin.distanceTo(entity.eyePosition)
            if(distance>=spec.range || !LoadedRay.visible(level,origin,entity.eyePosition))continue
            val speed=10*old.distanceTo(entity.position());val weight=if(entity is Player).8 else .4
            value+=speed*weight*(spec.range-distance)/spec.range
        }
        p.sensedEntityPositions.keys.retainAll(ids)
        return value.coerceIn(0.0,1.0)
    }
    private fun fire(): Boolean {
        val half=(spec.range.toInt()-1)/2;val center=e.node.blockPos.relative(p.face.opposite,half)
        val origin=Vec3.atCenterOf(e.node.blockPos)
        for(dx in -half..half)for(dy in -half..half)for(dz in -half..half) {
            val pos=center.offset(dx,dy,dz)
            if(level.isOutsideBuildHeight(pos))continue
            val chunk=level.chunkSource.getChunkNow(pos.x shr 4,pos.z shr 4) ?: continue
            if(chunk.getBlockState(pos).block is BaseFireBlock && LoadedRay.visible(level,origin,Vec3.atCenterOf(pos)))return true
        }
        return false
    }
    fun beforeStep() { output?.setOutputNormalized(signal) }
    fun afterStep(dt: Double) {
        if(load!=null) { p.voltage=load.voltage;p.current=load.current;p.pinVoltages[spec.outputEdge]=load.voltage;p.energy+=maxOf(0.0,p.voltage*p.current)*dt }
        else { p.voltage=0.0;p.current=0.0 }
    }
    fun afterTick() {
        val old=p.active
        p.active=when(spec.kind) { "ENTITY"->signal>.6;"FIRE","FIRE_BUZZER"->p.thermalState.getBoolean("envPowered") && p.thermalState.getBoolean("envFire");else->signal>0 }
        p.signal=if(batteryPowered)if(p.active)15 else 0 else (p.voltage/5*15).toInt().coerceIn(0,15)
        p.powerFactor=signal.coerceIn(0.0,1.0)
        persist();if(old!=p.active)e.node.sync()
    }
    fun persist() {
        p.thermalState.putDouble("envTimer",timer);p.thermalState.putDouble("envOutput",signal)
        rc1.writeToNBT(p.thermalState,"envRC1");rc2.writeToNBT(p.thermalState,"envRC2")
    }
}
