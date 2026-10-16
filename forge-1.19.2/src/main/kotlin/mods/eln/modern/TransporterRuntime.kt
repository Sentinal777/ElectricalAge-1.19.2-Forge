package mods.eln.modern

import mods.eln.config.ForgePortConfig
import mods.eln.modern.grid.GridGhostEntity
import mods.eln.modern.grid.GridMultiblock
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import kotlin.math.abs
import kotlin.math.sqrt

/** Exact original chamber and door reservations in the transparent-node reference frame. */
object Transporters {
    const val IDLE=0;const val START=1;const val CLOSE=2;const val CHARGE=3
    const val TELEPORT=4;const val OPEN=5;const val RESERVED=6;const val RESET=7
    val openDoor=listOf(listOf(-4,2,0),listOf(-3,2,0))
    val closedDoor=listOf(listOf(-2,0,0),listOf(-2,1,0))
    fun phase(p: PartState)=p.gridState.getInt("transporterPhase").coerceIn(0,7)
    fun closed(p: PartState)=p.gridState.getBoolean("transporterDoorClosed")
    fun chamber(node: FaceNodeEntity,p: PartState)=GridMultiblock.offset(node.blockPos,p,listOf(-1,0,0))
    fun area(node: FaceNodeEntity,p: PartState)=AABB(chamber(node,p)).expandTowards(0.0,1.0,0.0)
    fun cells(pos: BlockPos,p: PartState): List<BlockPos> {
        val moving=p.gridState.getBoolean("transporterDoorMoving")
        val raw=p.definition.reservedCells.filter { it !in openDoor }+
            (if(!closed(p) || moving)openDoor else emptyList())+(if(closed(p) || moving)closedDoor else emptyList())
        return raw.distinct().map { GridMultiblock.offset(pos,p,it) }
    }
    fun door(node: FaceNodeEntity,p: PartState,closed: Boolean,moving: Boolean): Boolean {
        val world=node.level as? ServerLevel ?: return false
        val previous=cells(node.blockPos,p);val wasClosed=Transporters.closed(p);val wasMoving=p.gridState.getBoolean("transporterDoorMoving")
        p.gridState.putBoolean("transporterDoorClosed",closed);p.gridState.putBoolean("transporterDoorMoving",moving)
        val next=cells(node.blockPos,p)
        val clear=next.all { at ->
            val chunk=world.chunkSource.getChunkNow(at.x shr 4,at.z shr 4)
            val ghost=chunk?.getBlockEntity(at) as? GridGhostEntity
            chunk!=null && !world.isOutsideBuildHeight(at) && world.worldBorder.isWithinBounds(at) &&
                (chunk.getBlockState(at).material.isReplaceable || ghost!=null && ghost.owner==node.blockPos && ghost.ownerId==p.gridId)
        }
        if(!clear) {
            p.gridState.putBoolean("transporterDoorClosed",wasClosed);p.gridState.putBoolean("transporterDoorMoving",wasMoving);return false
        }
        for(at in previous-next.toSet())GridMultiblock.removeOwnedCell(node,p,at)
        GridMultiblock.install(node,p);node.setChanged();node.sync();return true
    }
    fun configure(node: FaceNodeEntity,p: PartState,name: String,target: String,watts: Double,start: Boolean) {
        if(phase(p)!=IDLE || !watts.isFinite() || watts !in 2000.0..20000.0 || name.length>64 || target.length>64)return
        p.expression=name;p.channel=target;p.value=watts
        if(start)p.gridState.putBoolean("transporterStart",true)
        node.changedTopology()
    }
}

/** Original paired transporter: real resistor work, named reservation, doors and occupant-dependent cost. */
class TransporterRuntime(private val e: ElementRuntime) {
    val p=e.part
    val node=e.node
    private val world=e.circuit.level
    val load=ElectricalLoad().apply { serialResistance=PartDefinition.byId.getValue("high_voltage_cable").resistance;state=p.voltage }
    private val resistor=Resistor(load,null).setResistance(1e9)
    init {
        e.circuit.root.addState(load);e.circuit.root.addComponent(resistor);for(i in 0..3)e.pins[i]=load
        // Circuit rebuilds retain the same PartState. A genuinely loaded/replanted state resets its interrupted trip.
        if(!p.transporterInitialized) {
            p.transporterInitialized=true
            if(Transporters.phase(p)!=Transporters.IDLE) {
                discard();p.gridState.putInt("transporterPhase",Transporters.RESET)
                p.gridState.remove("transporterMaster");p.gridState.remove("transporterPair")
            }
        }
    }
    fun available(): Boolean {
        val at=node.blockPos
        return !node.isRemoved && node.parts[p.face.ordinal]===p && world.chunkSource.isPositionTicking(ChunkPos.asLong(at)) &&
            world.chunkSource.getChunkNow(at.x shr 4,at.z shr 4)?.getBlockEntity(at)===node &&
            Transporters.cells(at,p).all { cell ->
                val ghost=world.chunkSource.getChunkNow(cell.x shr 4,cell.z shr 4)?.getBlockEntity(cell) as? GridGhostEntity
                ghost!=null && ghost.owner==at && ghost.ownerId==p.gridId
            }
    }
    private fun targets(name: String)=e.circuit.transporters().filter { it.available() && it.p.expression==name }
    private fun partner()=e.circuit.transporters().firstOrNull { it.available() && it.p.gridId==p.gridState.getString("transporterPair") }
    private fun occupants()=world.getEntities(null,Transporters.area(node,p)).filter { !it.isRemoved && !it.isSpectator }
    private fun required(target: TransporterRuntime?): Double {
        if(target==null)return 0.0
        val entities=occupants();val players=entities.count { it is Player };val living=entities.count { it is LivingEntity && it !is Player };val items=entities.count { it is ItemEntity }
        val distance=sqrt(Transporters.chamber(node,p).distSqr(Transporters.chamber(target.node,target.p)))
        return (10000+40000.0*players+5000*living+5000*items)*(1+sqrt(distance/250))
    }
    private fun event(id: String) { p.gridState.putString("transporterEvent",id);node.sync() }
    private fun discard() {
        p.gridState.putDouble("transporterDiscardedJ",p.gridState.getDouble("transporterDiscardedJ")+p.progress)
        p.progress=0.0;p.processFraction=0.0
    }
    private fun phase(next: Int): Boolean {
        val old=Transporters.phase(p);if(old==next)return true
        if(next==Transporters.CLOSE && !Transporters.door(node,p,true,true)) { event("obstructed");return false }
        if(next==Transporters.OPEN && !Transporters.door(node,p,false,Transporters.closed(p))) { event("obstructed");return false }
        if(old==Transporters.CHARGE) {
            if(next==Transporters.TELEPORT) {
                p.gridState.putDouble("transporterSpentJ",p.gridState.getDouble("transporterSpentJ")+p.progress)
                p.gridState.putDouble("transporterLastPaidJ",p.progress);p.progress=0.0
            } else discard()
            p.processFraction=0.0
        }
        p.gridState.putInt("transporterPhase",next);p.gridState.putDouble("transporterTime",0.0)
        if(next==Transporters.CHARGE)p.gridState.putInt("transporterSoundTick",0)
        if(next==Transporters.IDLE) { p.gridState.remove("transporterMaster");p.gridState.remove("transporterPair");p.processFraction=0.0 }
        node.setChanged();node.sync();return true
    }
    private fun reserve(master: TransporterRuntime): Boolean {
        if(Transporters.phase(p)!=Transporters.IDLE || load.voltage<640)return false
        p.gridState.putString("transporterMaster",master.p.gridId)
        p.gridState.putLong("transporterRefresh",world.gameTime)
        phase(Transporters.RESERVED);return true
    }
    private fun refresh(master: TransporterRuntime) {
        if(p.gridState.getString("transporterMaster")!=master.p.gridId)return
        p.gridState.putLong("transporterRefresh",world.gameTime);p.processFraction=master.p.processFraction
        val desired=Transporters.closed(master.p)
        if(desired!=Transporters.closed(p))phase(if(desired)Transporters.CLOSE else Transporters.OPEN)
    }
    fun beforeTick() {
        val target=if(Transporters.phase(p)==Transporters.IDLE)targets(p.channel).singleOrNull()else partner()
        val needed=required(target);p.gridState.putDouble("transporterTargetJ",needed)
        resistor.resistance=if(Transporters.phase(p)==Transporters.CHARGE)800.0*800/p.value.coerceIn(2000.0,20000.0)else 1e9
        val blink=(p.gridState.getInt("transporterBlink")+1)%9;p.gridState.putInt("transporterBlink",blink)
        if(blink==0 && (load.voltage/800-.5)*3>world.random.nextDouble())
            mods.eln.modern.lighting.LightSpotBlock.illuminate(world,GridMultiblock.offset(node.blockPos,p,listOf(-1,1,0)),12,11)
    }
    fun afterStep(dt: Double) {
        p.voltage=load.voltage;p.voltageB=0.0;p.current=load.current
        val work=resistor.power.coerceAtLeast(0.0)*dt;p.energy+=work
        if(resistor.resistance<1e9)p.progress+=work else p.gridState.putDouble("transporterIdleLossJ",p.gridState.getDouble("transporterIdleLossJ")+work)
        p.active=Transporters.phase(p)==Transporters.CHARGE && resistor.power>1
        p.powerFactor=if(p.active)resistor.power/p.value.coerceIn(2000.0,20000.0)else 0.0
        if(Transporters.phase(p)==Transporters.CHARGE) {
            val target=p.gridState.getDouble("transporterTargetJ");p.processFraction=if(target>0)(p.progress/target).coerceIn(0.0,1.0)else 0.0
        }
    }
    private fun unstableTransport() {
        // Preserve the experimental malfunction without the legacy unbounded chunk/height lookup.
        for(entity in occupants()) {
            for(attempt in 0 until 64) {
                val x=entity.blockX+(world.random.nextDouble()*2000-1000).toInt();val z=entity.blockZ+(world.random.nextDouble()*2000-1000).toInt()
                val chunk=world.chunkSource.getChunkNow(x shr 4,z shr 4) ?: continue
                val y=chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x and 15,z and 15)+1
                val at=BlockPos(x,y,z)
                if(world.isOutsideBuildHeight(at.above()) || !world.worldBorder.isWithinBounds(at) || !chunk.getBlockState(at).isAir || !chunk.getBlockState(at.above()).isAir)continue
                teleport(entity,x+.5,y.toDouble(),z+.5);break
            }
        }
    }
    private fun teleport(entity: Entity,x: Double,y: Double,z: Double) {
        val velocity=entity.deltaMovement
        if(entity is ServerPlayer)entity.connection.teleport(x,y,z,entity.yRot,entity.xRot) else entity.teleportTo(x,y,z)
        entity.deltaMovement=velocity
    }
    fun afterTick() {
        var timer=p.gridState.getDouble("transporterTime")+.05;p.gridState.putDouble("transporterTime",timer)
        val slave=p.gridState.getString("transporterMaster").isNotEmpty()
        when(Transporters.phase(p)) {
            Transporters.IDLE -> if(p.gridState.getBoolean("transporterStart")) {
                val candidates=targets(p.channel)
                when {
                    p.channel==p.expression -> event("same")
                    candidates.isEmpty() -> event("missing")
                    candidates.size>1 -> event("multiple")
                    load.voltage<640 -> event("power")
                    else -> {
                        val target=candidates.single()
                        if(!target.reserve(this))event("busy") else {
                            discard();p.gridState.putString("transporterPair",target.p.gridId)
                            p.gridState.putString("transporterTargetName",p.channel);event("found");phase(Transporters.START)
                        }
                    }
                }
            }
            Transporters.START -> {
                if(occupants().isEmpty()) { timer=0.0;p.gridState.putDouble("transporterTime",0.0) }
                if(timer>2 && !phase(Transporters.CLOSE))phase(Transporters.OPEN)
            }
            Transporters.CLOSE -> if(timer>3) {
                if(Transporters.door(node,p,true,false))phase(if(slave)Transporters.RESERVED else Transporters.CHARGE)
            }
            Transporters.CHARGE -> {
                val name=p.gridState.getString("transporterTargetName");val matches=targets(name);val target=partner()
                when {
                    name==p.expression -> { event("same");phase(Transporters.OPEN) }
                    matches.size!=1 || target==null || matches.singleOrNull()!==target -> { event(if(matches.size>1)"multiple" else "missing");phase(Transporters.OPEN) }
                    load.voltage<640 -> { event("unstable");unstableTransport();phase(Transporters.OPEN) }
                    else -> {
                        val soundTick=p.gridState.getInt("transporterSoundTick")
                        if(soundTick%18==0)p.gridState.putInt("transporterSoundPulse",p.gridState.getInt("transporterSoundPulse")+1)
                        p.gridState.putInt("transporterSoundTick",soundTick+1)
                        if(p.progress>=p.gridState.getDouble("transporterTargetJ"))phase(Transporters.TELEPORT)
                    }
                }
            }
            Transporters.TELEPORT -> {
                val target=partner()
                if(target!=null) {
                    val from=Transporters.chamber(node,p);val to=Transporters.chamber(target.node,target.p)
                    val entities=occupants()
                    for(entity in entities)teleport(entity,entity.x+to.x-from.x,entity.y+to.y-from.y,entity.z+to.z-from.z)
                    p.gridState.putInt("transporterTrips",p.gridState.getInt("transporterTrips")+1)
                    p.gridState.putInt("transporterMoved",p.gridState.getInt("transporterMoved")+entities.size)
                } else event("missing")
                phase(Transporters.OPEN)
            }
            Transporters.OPEN -> if(timer>3 && Transporters.door(node,p,false,false))phase(if(slave)Transporters.RESERVED else Transporters.IDLE)
            Transporters.RESERVED -> if(p.gridState.getLong("transporterRefresh")<world.gameTime-1) {
                if(Transporters.closed(p))phase(Transporters.OPEN) else phase(Transporters.IDLE)
            }
            Transporters.RESET -> if(Transporters.door(node,p,false,false))phase(Transporters.IDLE)
        }
        if(!slave && Transporters.phase(p)!=Transporters.IDLE)partner()?.refresh(this)
        p.gridState.putBoolean("transporterStart",false)
        var excess=abs(p.voltage)-1040;val outside=excess>0
        if(outside && !p.wasOvervoltage)excess=0.0;p.wasOvervoltage=outside
        val timeout=minOf(200.0,(if(p.gridState.contains("transporterVoltageTimeout"))p.gridState.getDouble("transporterVoltageTimeout")else 200.0)-.05*excess*(.5+world.random.nextDouble()))
        p.gridState.putDouble("transporterVoltageTimeout",timeout)
        if(timeout<0 && ForgePortConfig.cableVoltageProtection.get())MachineFailure.destroy(node,p,3f)
    }
}
