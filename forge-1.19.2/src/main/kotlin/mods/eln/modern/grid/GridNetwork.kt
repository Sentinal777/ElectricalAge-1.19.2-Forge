package mods.eln.modern.grid

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.*

data class GridEnd(val pos: BlockPos,val nodeId: String,val side: Direction) {
    fun save()=CompoundTag().also { it.putLong("pos",pos.asLong());it.putString("node",nodeId);it.putInt("side",side.get3DDataValue()) }
    companion object { fun load(t: CompoundTag)=GridEnd(BlockPos.of(t.getLong("pos")),t.getString("node"),Direction.from3DDataValue(t.getInt("side"))) }
}
data class GridWire(val id: UUID,val a: GridEnd,val b: GridEnd,val cable: String,val length: Int,val paidLength: Int) {
    fun save()=CompoundTag().also { it.putUUID("id",id);it.put("a",a.save());it.put("b",b.save());it.putString("cable",cable);it.putInt("length",length);it.putInt("paid",paidLength) }
    companion object {
        fun load(t: CompoundTag): GridWire? {
            if(!t.hasUUID("id"))return null
            val length=t.getInt("length");if(length !in 1..96)return null
            return GridWire(t.getUUID("id"),GridEnd.load(t.getCompound("a")),GridEnd.load(t.getCompound("b")),t.getString("cable"),length,t.getInt("paid").coerceIn(0,length))
        }
    }
}
class GridData : SavedData() {
    val wires=linkedMapOf<UUID,GridWire>()
    val brokenOwners=linkedMapOf<BlockPos,String>()
    override fun save(t: CompoundTag): CompoundTag {
        t.put("wires",ListTag().also { l -> wires.values.forEach { l.add(it.save()) } })
        t.put("brokenOwners",ListTag().also { l -> brokenOwners.forEach { (pos,id) -> l.add(CompoundTag().also { it.putLong("pos",pos.asLong());it.putString("node",id) }) } })
        return t
    }
    companion object { fun load(t: CompoundTag)=GridData().also { d ->
        t.getList("wires",10).forEach { GridWire.load(it as CompoundTag)?.let { w -> d.wires[w.id]=w } }
        t.getList("brokenOwners",10).forEach { raw -> val c=raw as CompoundTag;d.brokenOwners[BlockPos.of(c.getLong("pos"))]=c.getString("node") }
    } }
}

/** Dimension SavedData owns each wire once. Unloaded endpoints stay saved and do not force chunks. */
object GridNetwork {
    private data class Pending(val dimension: String,val endpoint: GridEnd)
    private val pending=mutableMapOf<UUID,Pending>()
    fun clearPending()=pending.clear()
    fun data(level: ServerLevel): GridData=level.dataStorage.computeIfAbsent(GridData::load,::GridData,"eln_grid_connections")
    fun part(node: FaceNodeEntity)=node.parts.filterNotNull().firstOrNull { it.definition.grid!=null }
    fun sideValid(part: PartState,side: Direction): Boolean {
        val kind=part.definition.grid?.kind ?: return false
        return kind !in setOf("TRANSFORMER","SWITCH") || side in listOf(FaceBasis.edges(part)[1],FaceBasis.edges(part)[3])
    }
    fun link(level: ServerLevel,a: FaceNodeEntity,sideA: Direction,b: FaceNodeEntity,sideB: Direction,paidLength: Int=0): GridWire {
        val pa=part(a) ?: error("Missing first grid device");val pb=part(b) ?: error("Missing second grid device")
        require(a.level===level && b.level===level && a!==b) { "message.eln.grid.invalid" }
        val sa=pa.definition.grid!!;val sb=pb.definition.grid!!
        require(sa.cable==sb.cable && sideValid(pa,sideA) && sideValid(pb,sideB)) { "message.eln.grid.side" }
        val distance=sqrt(a.blockPos.distSqr(b.blockPos))
        require(distance<=min(sa.range,sb.range)) { "message.eln.grid.range" }
        val length=ceil(distance).toInt()
        val d=data(level)
        require(d.wires.values.none { (it.a.pos==a.blockPos && it.b.pos==b.blockPos) || (it.a.pos==b.blockPos && it.b.pos==a.blockPos) }) { "message.eln.grid.duplicate" }
        val wire=GridWire(UUID.randomUUID(),GridEnd(a.blockPos.immutable(),pa.gridId,sideA),GridEnd(b.blockPos.immutable(),pb.gridId,sideB),sa.cable,length,paidLength.coerceIn(0,length))
        d.wires[wire.id]=wire;d.setDirty();a.changedTopology();b.changedTopology();return wire
    }
    fun useCable(node: FaceNodeEntity,player: Player,stack: ItemStack,side: Direction): Boolean {
        val p=part(node) ?: return false
        val held=stack.item as? PartItem ?: return false
        if(held.definition.behavior!=Behavior.CABLE)return false
        val level=node.level as? ServerLevel ?: return true
        if(held.definition.id!=p.definition.grid!!.cable) {
            player.displayClientMessage(Component.translatable("message.eln.grid.cable",Component.translatable(PartDefinition.byId.getValue(p.definition.grid!!.cable).translationKey)),true);return true
        }
        if(!sideValid(p,side)) { player.displayClientMessage(Component.translatable("message.eln.grid.side"),true);return true }
        val first=pending.remove(player.uuid)
        if(first==null || first.dimension!=level.dimension().location().toString() || !level.hasChunkAt(first.endpoint.pos)) {
            pending[player.uuid]=Pending(level.dimension().location().toString(),GridEnd(node.blockPos.immutable(),p.gridId,side))
            player.displayClientMessage(Component.translatable("message.eln.grid.start"),true);return true
        }
        if(first.endpoint.pos==node.blockPos) { player.displayClientMessage(Component.translatable("message.eln.grid.cancel"),true);return true }
        val other=level.getBlockEntity(first.endpoint.pos) as? FaceNodeEntity
        if(other==null || part(other)?.gridId!=first.endpoint.nodeId || !level.mayInteract(player,other.blockPos)) {
            player.displayClientMessage(Component.translatable("message.eln.grid.invalid"),true);return true
        }
        val cost=ceil(sqrt(node.blockPos.distSqr(other.blockPos))).toInt()
        val inventory=player.inventory
        val available=(0 until inventory.containerSize).sumOf { slot -> inventory.getItem(slot).takeIf { it.item===stack.item }?.count ?: 0 }
        if(!player.abilities.instabuild && available<cost) { player.displayClientMessage(Component.translatable("message.eln.grid.cost",cost),true);return true }
        try {
            link(level,other,first.endpoint.side,node,side,if(player.abilities.instabuild)0 else cost)
            if(!player.abilities.instabuild) {
                var needed=cost
                for(slot in 0 until inventory.containerSize) {
                    val item=inventory.getItem(slot);if(item.item!==stack.item)continue
                    val count=min(needed,item.count);item.shrink(count);needed-=count;if(needed==0)break
                }
                inventory.setChanged()
            }
            player.displayClientMessage(Component.translatable("message.eln.grid.connected"),true)
        } catch(e: IllegalArgumentException) { player.displayClientMessage(Component.translatable(e.message ?: "message.eln.grid.invalid"),true) }
        return true
    }
    fun removeAt(level: ServerLevel,pos: BlockPos,drop: Boolean) {
        val d=data(level);val removed=d.wires.values.filter { it.a.pos==pos || it.b.pos==pos }
        for(w in removed) {
            d.wires.remove(w.id)
            if(drop && w.paidLength>0)Content.partItems[w.cable]?.let { item ->
                var left=w.paidLength;while(left>0) { val amount=min(left,64);Block.popResource(level,pos,ItemStack(item.get(),amount));left-=amount }
            }
            for(end in listOf(w.a,w.b))if(level.hasChunkAt(end.pos))(level.getBlockEntity(end.pos) as? FaceNodeEntity)?.changedTopology()
        }
        if(removed.isNotEmpty())d.setDirty()
    }
    fun rotate(level: ServerLevel,pos: BlockPos) {
        val d=data(level)
        d.wires.replaceAll { _,w -> w.copy(a=if(w.a.pos==pos)w.a.copy(side=w.a.side.takeIf { it.axis!=Direction.Axis.Y }?.clockWise ?: w.a.side)else w.a,
            b=if(w.b.pos==pos)w.b.copy(side=w.b.side.takeIf { it.axis!=Direction.Axis.Y }?.clockWise ?: w.b.side)else w.b) }
        d.setDirty()
    }
    fun updateRenderData(level: ServerLevel,nodes: Collection<FaceNodeEntity>) {
        val lookup=nodes.associateBy { it.blockPos };val wires=data(level).wires.values
        val rotated=mutableSetOf<BlockPos>()
        for(node in nodes) {
            val p=part(node) ?: continue
            val links=wires.filter { it.a.pos==node.blockPos || it.b.pos==node.blockPos }
            if(p.definition.grid!!.kind in setOf("OVERHEAD","DIRECT","POLE_TRANSFORMER")) {
                val angles=links.map { w -> val delta=w.a.pos.subtract(w.b.pos);atan2(delta.z.toDouble(),delta.x.toDouble()) }
                val best=(0 until 128).minByOrNull { n -> angles.sumOf { a -> abs(sin(PI*n/128-a)).pow(3) } } ?: 0
                val angle=-180.0*best/128
                if(p.gridAngle!=angle) { p.gridAngle=angle;rotated.add(node.blockPos) }
            }
        }
        for(node in nodes) {
            val p=part(node) ?: continue;val l=ListTag()
            for(w in wires.filter { it.a.pos==node.blockPos }) {
                val other=lookup[w.b.pos];val target=other?.let(::part)
                if(other==null || target==null) {
                    node.gridRenderLinks.firstOrNull { raw -> (raw as CompoundTag).hasUUID("id") && raw.getUUID("id")==w.id }?.let { l.add(it.copy()) }
                    continue
                }
                if(p.gridId!=w.a.nodeId || target.gridId!=w.b.nodeId)continue
                val t=CompoundTag();t.putUUID("id",w.id)
                for(i in 0..1) {
                    val start=GridGeometry.anchor(p,w.a.side,i).add(Vec3.atLowerCornerOf(node.blockPos))
                    val end=GridGeometry.anchor(target,w.b.side,i).add(Vec3.atLowerCornerOf(other.blockPos))
                    for((prefix,v) in listOf("a$i" to start,"b$i" to end)) { t.putDouble(prefix+"x",v.x);t.putDouble(prefix+"y",v.y);t.putDouble(prefix+"z",v.z) }
                }
                l.add(t)
            }
            if(node.gridRenderLinks!=l || node.blockPos in rotated) {
                node.gridRenderLinks=l
                level.chunkSource.getChunkNow(Math.floorDiv(node.blockPos.x,16),Math.floorDiv(node.blockPos.z,16))?.setUnsaved(true)
                node.sync()
            }
        }
    }
}

object GridGeometry {
    fun anchor(p: PartState,side: Direction,index: Int): Vec3 {
        val g=p.definition.grid!!
        val terminal=if(g.kind=="TRANSFORMER")if(side==FaceBasis.edges(p)[3])1 else 0 else if(g.kind=="SWITCH")if(side==FaceBasis.edges(p)[3])0 else 1 else 0
        val name=(if(index==0)"p" else "g")+terminal
        val a=g.anchors[name] ?: g.anchors.entries.firstOrNull { it.key.startsWith(name+"_") }?.value ?: listOf(0.0,3.0,0.0)
        val spec=p.definition.visual;val offset=spec.modelOffset ?: listOf(0.0,0.0,0.0)
        val angle=Math.toRadians(spec.modelYaw+p.gridAngle);val x=a[0]+offset[0];val z=a[2]+offset[2]
        val rx=x*cos(angle)+z*sin(angle);val rz=z*cos(angle)-x*sin(angle)
        val edges=FaceBasis.edges(p);val u=edges[0];val v=edges[1]
        return Vec3(.5+rx*v.stepX-rz*u.stepX,.5+a[1]+offset[1],.5+rx*v.stepZ-rz*u.stepZ)
    }
    /** Original 1.005 slack catenary, including exact endpoints and a safe vertical-span branch. */
    fun catenary(start: Vec3,end: Vec3): List<Vec3> {
        val delta=end.subtract(start);val dw=sqrt(delta.x*delta.x+delta.z*delta.z)
        if(dw<1e-6)return listOf(start,end)
        val k=delta.length()*1.005;var l=.01
        while(l<3 && sinh(l)/l<sqrt(k*k-delta.y*delta.y)/dw)l+=.01
        val a=dw/(2*l);val p=(dw-a*ln((k+delta.y)/(k-delta.y)))*.5
        val q=(delta.y-k*cosh(l)/sinh(l))*.5
        return (0..16).map { n -> if(n==0)start else if(n==16)end else {
            val f=n/16.0;Vec3(start.x+delta.x*f,start.y+a*cosh((dw*f-p)/a)+q,start.z+delta.z*f)
        } }
    }
}
