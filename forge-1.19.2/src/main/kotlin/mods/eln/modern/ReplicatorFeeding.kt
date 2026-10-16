package mods.eln.modern

import mods.eln.sim.ElectricalConnection
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import java.util.UUID
import java.util.WeakHashMap
import kotlin.math.pow

/** Contacts are transient and identity guarded. Food already paid into a contact is collected once. */
object ReplicatorFeeding {
    data class Cable(val node: FaceNodeEntity,val part: PartState)
    class Contact(val mob: ReplicatorEntity,val cable: Cable,var seen: Long,var resistance: Double) { var paid=0.0 }
    private val worlds=WeakHashMap<ServerLevel,MutableMap<UUID,Contact>>()
    fun candidates(world: ServerLevel)=CircuitManager.loadedNodes(world).flatMap { node ->
        node.parts.filterNotNull().filter { it.definition.behavior==Behavior.CABLE && it.definition.id!="signal_cable" }.map { Cable(node,it) }
    }
    fun interesting(cable: Cable): Boolean {
        val level=cable.node.level as? ServerLevel ?: return false
        val chunk=level.chunkSource.getChunkNow(cable.node.blockPos.x shr 4,cable.node.blockPos.z shr 4) ?: return false
        return !cable.node.isRemoved && chunk.getBlockEntity(cable.node.blockPos)===cable.node && cable.node.parts[cable.part.face.get3DDataValue()]===cable.part &&
            level.chunkSource.isPositionTicking(net.minecraft.world.level.ChunkPos.asLong(cable.node.blockPos)) && cable.part.voltage>=30 && cable.part.definition.behavior==Behavior.CABLE && cable.part.definition.id!="signal_cable"
    }
    fun pending(mob: ReplicatorEntity)=worlds[mob.level]?.get(mob.uuid)?.takeIf { it.mob===mob }?.paid ?: 0.0
    fun collect(mob: ReplicatorEntity) {
        val c=worlds[mob.level]?.get(mob.uuid)?.takeIf { it.mob===mob } ?: return
        val work=c.paid;c.paid=0.0;mob.eatElectricity(work)
    }
    fun disconnect(mob: ReplicatorEntity) {
        val contacts=worlds[mob.level] ?: return
        val c=contacts[mob.uuid]?.takeIf { it.mob===mob } ?: return
        collect(mob);contacts.remove(mob.uuid);CircuitManager.invalidate(c.cable.node)
    }
    fun feed(mob: ReplicatorEntity,cable: Cable) {
        val level=mob.level as? ServerLevel ?: return
        if(!interesting(cable) || cable.node.blockPos.distToCenterSqr(mob.position())>=4 || !level.isPositionEntityTicking(mob.blockPosition())) { disconnect(mob);return }
        val next=(cable.part.voltage/50).pow(-.3)*cable.part.voltage*cable.part.voltage/50
        if(!next.isFinite() || next<=0) { disconnect(mob);return }
        if(mob.feedResistance<.8*next)mob.hurt(DamageSource.MAGIC,5f)
        mob.feedResistance=next
        if(!mob.isAlive) { disconnect(mob);return }
        val contacts=worlds.getOrPut(level) { linkedMapOf() }
        val old=contacts[mob.uuid]
        if(old==null || old.mob!==mob || old.cable.node!==cable.node || old.cable.part!==cable.part) {
            if(old!=null)disconnect(old.mob)
            contacts[mob.uuid]=Contact(mob,cable,level.gameTime,next);CircuitManager.invalidate(cable.node)
        }else { old.seen=level.gameTime;old.resistance=next }
    }
    fun contacts(node: FaceNodeEntity,p: PartState)=worlds[node.level]?.values?.filter { it.cable.node===node && it.cable.part===p && !it.mob.isRemoved }.orEmpty()
    fun clear(world: ServerLevel?=null) { if(world==null)worlds.clear()else worlds.remove(world) }
}

/** High-voltage cable descriptor Rs times ten, private resistor and original V-dependent load. */
class ReplicatorCableLoad(private val element: ElementRuntime) {
    private data class Tap(val contact: ReplicatorFeeding.Contact,val load: ElectricalLoad,val resistor: Resistor)
    private val taps=ReplicatorFeeding.contacts(element.node,element.part).map { contact ->
        val load=ElectricalLoad().apply { serialResistance=1.0;state=element.part.voltage }
        val resistor=Resistor(load,null).setResistance(contact.resistance)
        element.circuit.root.addState(load);element.circuit.root.addComponent(resistor)
        element.circuit.root.addComponent(ElectricalConnection(load,element.pins[0]!!));Tap(contact,load,resistor)
    }
    fun beforeStep() {
        for(tap in taps) {
            val c=tap.contact;val world=element.circuit.level
            val active=c.mob.isAlive && !c.mob.isRemoved && world.gameTime-c.seen<=3 && world.isPositionEntityTicking(c.mob.blockPosition()) &&
                ReplicatorFeeding.interesting(c.cable) && c.cable.node.blockPos.distToCenterSqr(c.mob.position())<4
            tap.resistor.resistance=if(active)c.resistance else 1e12
        }
    }
    fun afterStep(dt: Double) {
        for(tap in taps) {
            val work=tap.resistor.power.coerceAtLeast(0.0)*dt
            tap.contact.paid+=work
            val state=element.part.gridState
            state.putDouble("replicatorWorkJ",state.getDouble("replicatorWorkJ")+work)
        }
    }
}
