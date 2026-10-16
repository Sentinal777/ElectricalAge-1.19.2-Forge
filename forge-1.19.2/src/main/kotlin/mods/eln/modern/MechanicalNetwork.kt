package mods.eln.modern

import mods.eln.config.ForgePortConfig
import net.minecraft.core.Direction
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import mods.eln.modern.grid.GridMultiblock
import mods.eln.modern.grid.GridGhostEntity
import java.util.IdentityHashMap
import kotlin.math.*

/** Loaded shaft graph, including the clutch's two independent networks and finite friction coupling. */
class MechanicalNetwork(elements: List<ElementRuntime>) {
    private data class Vertex(val e: ElementRuntime,val side: Int=-1) {
        val p get()=e.part
        val mass get()=p.definition.shaftMass
        val key get()=e.node.blockPos.toShortString()+if(side<0)"" else ":$side"
        val savedGroup get()=if(side<0)p.shaftGroup else p.gridState.getString("clutchGroup$side").ifBlank { p.shaftGroup }
        val savedEnergy get()=if(side<0)p.shaftEnergy else if(p.gridState.contains("clutchEnergy$side"))p.gridState.getDouble("clutchEnergy$side")else p.shaftEnergy/2
        val ports get()=if(side<0)ports(p)else setOf(FaceBasis.edges(p)[side])
    }
    private data class Port(val vertex: Vertex,val position: BlockPos,val direction: Direction)
    private val connectedSides=IdentityHashMap<PartState,MutableSet<Direction>>()
    private val shafts=elements.filter { it.part.definition.mechanical }
    private val groups=IdentityHashMap<PartState,ShaftPhysics>()
    private val graph=linkedMapOf<Vertex,ShaftPhysics>()
    private val mergeFailures=linkedSetOf<ElementRuntime>()
    private data class ClutchFrame(val leftEnergy: Double,val rightEnergy: Double)
    private val frames=IdentityHashMap<PartState,ClutchFrame>()
    init {
        val vertices=shafts.flatMap { e -> if(e.part.definition.behavior==Behavior.CLUTCH)listOf(Vertex(e,1),Vertex(e,3))else listOf(Vertex(e)) }
        val ports=vertices.associateWith { v ->
            v.ports.map { Port(v,v.e.node.blockPos,it) }+if(v.side<0)remotePorts(v.e.circuit.level,v.e.node,v.p).map { Port(v,it.position,it.direction) }else emptyList()
        }
        val at=ports.values.flatten().groupBy { it.position }
        val unseen=vertices.toMutableSet()
        while(unseen.isNotEmpty()) {
            val first=unseen.first();unseen.remove(first);val members=mutableListOf(first);var cursor=0
            while(cursor<members.size) {
                val vertex=members[cursor++]
                for(port in ports.getValue(vertex))for(other in at[port.position.relative(port.direction)].orEmpty()) {
                    val next=other.vertex
                    if(next==vertex || other.direction!=port.direction.opposite)continue
                    if(vertex.p.definition.behavior==Behavior.SHAFT_HUB && port.position==vertex.e.node.blockPos)connectedSides.getOrPut(vertex.p) { hashSetOf() }.add(port.direction)
                    if(next.p.definition.behavior==Behavior.SHAFT_HUB && other.position==next.e.node.blockPos)connectedSides.getOrPut(next.p) { hashSetOf() }.add(other.direction)
                    if(unseen.remove(next))members.add(next)
                }
            }
            var mass=0.0;var energy=0.0
            val joining=members.firstOrNull { it.p.shaftJoinPending }?.e
            for(history in members.groupBy { it.savedGroup.ifBlank { it.key } }.values) {
                val extraMass=history.sumOf { it.mass };val extraEnergy=history.sumOf { it.savedEnergy }
                val speed=if(mass>0)sqrt(2*energy/(mass*.05))else 0.0
                val extraSpeed=sqrt(2*extraEnergy/(extraMass*.05))
                if(mass>0 && joining!=null && abs(speed-extraSpeed)>50-.1*max(speed,extraSpeed))mergeFailures.add(joining)
                val loss=if(mass>0).05*(speed-extraSpeed).pow(2)else 0.0
                energy=(energy+extraEnergy-loss).coerceAtLeast(0.0);mass+=extraMass
            }
            val fixed=members.any { it.p.definition.behavior==Behavior.FIXED_SHAFT }
            val group=ShaftPhysics(if(fixed)1000.0 else mass,energy,fixed);val id=members.minOf { it.key }
            for(vertex in members) {
                graph[vertex]=group
                if(vertex.side<0) { vertex.p.shaftGroup=id;groups[vertex.p]=group }
                else { vertex.p.gridState.putString("clutchGroup${vertex.side}",id);if(vertex.side==1)groups[vertex.p]=group }
            }
        }
        shafts.forEach { it.part.shaftJoinPending=false }
        publish()
    }
    private fun side(e: ElementRuntime,index: Int)=graph[Vertex(e,index)]!!
    fun speed(part: PartState)=groups[part]?.speed ?: 0.0
    fun available(part: PartState)=groups[part]?.energy ?: 0.0
    fun electricalConverters(part: PartState): Int {
        val group=groups[part] ?: return 1
        return shafts.count { it.part.definition.behavior in setOf(Behavior.SHAFT_MOTOR,Behavior.SHAFT_GENERATOR) && it.part.enabled && !it.part.tripped && groups[it.part]===group }.coerceAtLeast(1)
    }
    fun generatorBudget(part: PartState,dt: Double): Double {
        val group=groups[part] ?: return 0.0
        val count=shafts.count { it.part.definition.behavior==Behavior.SHAFT_GENERATOR && it.part.enabled && groups[it.part]===group }.coerceAtLeast(1)
        return group.energy*.95/dt/count
    }
    fun add(part: PartState,joules: Double) { if(joules.isFinite() && joules>0)groups[part]?.let { it.energy+=joules } }
    fun consume(part: PartState,joules: Double)=groups[part]?.consume(joules) ?: 0.0
    fun beforeTick() {
        for(e in shafts)if(e.part.definition.behavior==Behavior.CLUTCH)frames[e.part]=ClutchFrame(side(e,1).energy,side(e,3).energy)
    }
    private fun couple(e: ElementRuntime) {
        val p=e.part;val left=side(e,1);val right=side(e,3);val runtime=e.clutch ?: return
        val input=runtime.control;val inventory=PartInventory.get(e.node,p);val stack=inventory.getStackInSlot(0)
        val plate=(stack.item as? MaterialItem)?.definition?.clutchPlate
        val pin=!inventory.getStackInSlot(1).isEmpty
        val tag=if(stack.isEmpty)net.minecraft.nbt.CompoundTag()else stack.orCreateTag;var wear=tag.getDouble("wear").takeIf(Double::isFinite)?.coerceIn(0.0,1.0) ?: 0.0
        var slipping=if(p.gridState.contains("clutchSlipping"))p.gridState.getBoolean("clutchSlipping")else true
        val difference=abs(left.speed-right.speed);val before=left.energy+right.energy
        if(input<=0 || plate==null || wear>=1.0 || p.tripped)slipping=true
        else if(left===right)slipping=false
        else if(slipping) {
            if(plate.explodes && difference>5 && ForgePortConfig.shaftFailures.get()) {
                MachineFailure.destroy(e.node,p);return
            }
            val faster=if(left.speed>=right.speed)left else right;val slower=if(faster===left)right else left
            val torque=input*(plate.maximumDynamic+(plate.minimumDynamic-plate.maximumDynamic)*wear)
            // Preserve the original twenty-hertz speed increment, stopping at the momentum-conserving intersection.
            val iFast=faster.mass*.05;val iSlow=slower.mass*.05
            val maximumImpulse=if(faster.fixed || slower.fixed)difference*(if(faster.fixed)iSlow else iFast)else difference/(1/iFast+1/iSlow)
            val impulse=min(torque*.05,maximumImpulse).coerceAtLeast(0.0)
            val fastSpeed=(faster.speed-impulse/iFast).coerceAtLeast(0.0);val slowSpeed=slower.speed+impulse/iSlow
            faster.energy=.5*iFast*fastSpeed.pow(2);slower.energy=.5*iSlow*slowSpeed.pow(2)
            if(impulse>=maximumImpulse-1e-12)slipping=false
            else wear=(wear+input*difference/1000*plate.wearSpeed).coerceAtMost(1.0)
        } else {
            val frame=frames[p] ?: ClutchFrame(left.energy,right.energy)
            val flow=left.energy-right.energy-frame.leftEnergy+frame.rightEnergy
            val limit=input*(plate.maximumStatic+(plate.minimumStatic-plate.maximumStatic)*wear)
            if(abs(flow)>limit && !pin) {
                val from=if(flow>0)left else right;val to=if(flow>0)right else left
                to.energy+=from.consume(limit);slipping=true
            }else if(left.fixed || right.fixed) { left.energy=0.0;right.energy=0.0 }
            else {
                val energy=left.energy+right.energy;val mass=left.mass+right.mass
                left.energy=energy*left.mass/mass;right.energy=energy*right.mass/mass
            }
        }
        if(plate!=null)tag.putDouble("wear",wear)
        p.gridState.putBoolean("clutchSlipping",slipping);p.gridState.putDouble("clutchControl",input)
        val lost=(before-left.energy-right.energy).coerceAtLeast(0.0)
        p.thermalState.putDouble("clutchHeat",p.thermalState.getDouble("clutchHeat")+lost)
        p.active=slipping && plate!=null && wear<1 && input>0 && difference>1e-4
        p.powerFactor=(min(1.0,difference/20)*input*.5).coerceIn(0.0,1.0)
    }
    fun tick() {
        if(ForgePortConfig.shaftFailures.get())mergeFailures.forEach { MachineFailure.destroy(it.node,it.part) }
        mergeFailures.clear()
        graph.values.toSet().forEach { it.consume(.02*maxOf(it.speed,1.0)) }
        // Original clutch coupling is a post-process, after the shaft networks' normal drag.
        shafts.filter { it.part.definition.behavior==Behavior.CLUTCH }.forEach(::couple)
        for(e in shafts)ShaftProtectionRuntime.tick(e.node,e.part,if(e.part.definition.behavior==Behavior.CLUTCH)max(side(e,1).speed,side(e,3).speed)else speed(e.part))
        publish()
        shafts.filter { it.part.definition.id=="flywheel" }.forEach { FlywheelRuntime.tick(it.node,it.part) }
    }
    fun publish() {
        for(e in shafts)if(e.part.definition.behavior==Behavior.SHAFT_HUB) {
            var connected=0;var standing=0
            for(direction in Direction.values()) {
                val pos=e.node.blockPos.relative(direction)
                if(direction in connectedSides[e.part].orEmpty())connected=connected or (1 shl direction.get3DDataValue())
                else if(e.circuit.level.hasChunkAt(pos) && e.circuit.level.getBlockState(pos).isSolidRender(e.circuit.level,pos))standing=standing or (1 shl direction.get3DDataValue())
            }
            e.part.gridState.putInt("shaftConnectedSides",connected);e.part.gridState.putInt("shaftStandingSides",standing)
        }
        for((vertex,group) in graph) {
            val share=group.energy*vertex.mass/group.mass
            if(vertex.side<0) { vertex.p.shaftSpeed=group.speed;vertex.p.shaftEnergy=share }
            else { vertex.p.gridState.putDouble("clutchSpeed${vertex.side}",group.speed);vertex.p.gridState.putDouble("clutchEnergy${vertex.side}",share) }
        }
        for(e in shafts)if(e.part.definition.behavior==Behavior.CLUTCH) {
            val p=e.part;p.shaftSpeed=(side(e,1).speed+side(e,3).speed)/2;p.shaftEnergy=p.gridState.getDouble("clutchEnergy1")+p.gridState.getDouble("clutchEnergy3")
        }
    }
    companion object {
        data class RemotePort(val position: BlockPos,val direction: Direction)
        fun portPositions(node: FaceNodeEntity,p: PartState)=p.definition.shaftPorts.orEmpty().map { spec ->
            RemotePort(GridMultiblock.offset(node.blockPos,p,spec.offset),FaceBasis.edges(p)[spec.side])
        }
        fun remotePorts(level: ServerLevel,node: FaceNodeEntity,p: PartState)=portPositions(node,p).filter { port ->
            if(!level.chunkSource.isPositionTicking(net.minecraft.world.level.ChunkPos.asLong(port.position)))false else {
                val chunk=level.chunkSource.getChunkNow(port.position.x shr 4,port.position.z shr 4)
                val ghost=chunk?.getBlockEntity(port.position) as? GridGhostEntity
                ghost!=null && ghost.owner==node.blockPos && ghost.ownerId==p.gridId
            }
        }
        fun ports(part: PartState): Set<Direction> = if(part.definition.behavior==Behavior.SHAFT_HUB)Direction.values().toSet()else FaceBasis.edges(part).let { setOf(it[1],it[3]) }
    }
}
