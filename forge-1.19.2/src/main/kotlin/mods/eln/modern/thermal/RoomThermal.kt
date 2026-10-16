package mods.eln.modern.thermal

import mods.eln.config.ForgePortConfig
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.level.saveddata.SavedData
import java.util.ArrayDeque

data class RoomBounds(val minX: Int,val minY: Int,val minZ: Int,val maxX: Int,val maxY: Int,val maxZ: Int) {
    val width get()=maxX-minX+1;val height get()=maxY-minY+1;val depth get()=maxZ-minZ+1
    val surfaceArea get()=2*(width*height+width*depth+height*depth)
    fun contains(pos: BlockPos)=pos.x in minX..maxX && pos.y in minY..maxY && pos.z in minZ..maxZ
    companion object {
        fun of(cells: Set<BlockPos>)=RoomBounds(cells.minOf { it.x },cells.minOf { it.y },cells.minOf { it.z },cells.maxOf { it.x },cells.maxOf { it.y },cells.maxOf { it.z })
    }
}
data class ThermalRoom(val id: String,val cells: Set<BlockPos>,var anchors: Set<BlockPos>,var delta: Double=0.0) {
    val bounds=RoomBounds.of(cells)
    val heatCapacity=cells.size*1200.0
    var openDoors=0
    var doorScanTick=Long.MIN_VALUE
    val conductance get()=bounds.surfaceArea*.12+openDoors*30
}

/** Saved per dimension; indices and active chunk state are rebuilt rather than serialized. */
class RoomThermalData: SavedData() {
    val rooms=linkedMapOf<String,ThermalRoom>()
    private val cells=hashMapOf<BlockPos,String>()
    private val anchors=hashMapOf<BlockPos,String>()
    var immediateScan=true
    var activeRooms=emptySet<String>()
    fun at(pos: BlockPos)=rooms[cells[pos] ?: anchors[pos]]
    fun refreshIndices() {
        cells.clear();anchors.clear()
        for(room in rooms.values) { for(pos in room.cells)cells[pos]=room.id;for(pos in room.anchors)anchors[pos]=room.id }
    }
    fun register(cells: Set<BlockPos>,thermalNodes: Collection<BlockPos>): ThermalRoom? {
        val bounds=RoomBounds.of(cells)
        val heatAnchors=thermalNodes.filterTo(hashSetOf()) { bounds.contains(it) && (it in cells || Direction.values().any { side -> it.relative(side) in cells }) }
        if(heatAnchors.isEmpty())return null
        var hash=1469598103934665603L
        for(pos in cells.sortedWith(compareBy<BlockPos> { it.x }.thenBy { it.y }.thenBy { it.z })) {
            var cellHash=1469598103934665603L
            cellHash=(cellHash xor pos.x.toLong())*1099511628211L;cellHash=(cellHash xor pos.y.toLong())*1099511628211L;cellHash=(cellHash xor pos.z.toLong())*1099511628211L
            hash=(hash xor cellHash)*1099511628211L
        }
        val id="${bounds.minX},${bounds.minY},${bounds.minZ}:${bounds.maxX},${bounds.maxY},${bounds.maxZ}:${cells.size}:$hash"
        val room=rooms.getOrPut(id) { ThermalRoom(id,cells,heatAnchors) };room.anchors=heatAnchors
        refreshIndices();setDirty();return room
    }
    fun invalidate(pos: BlockPos) {
        val affected=(Direction.values().map { pos.relative(it) }+pos).mapNotNull { at(it)?.id }.toSet()
        for(id in affected)rooms.remove(id)
        if(affected.isNotEmpty()) { refreshIndices();setDirty() }
        immediateScan=true
    }
    fun exchange(pos: BlockPos,loadDelta: Double,rp: Double,dt: Double): Double? {
        if(rp<=0 || !rp.isFinite())return null
        val room=at(pos)?.takeIf { it.id in activeRooms } ?: return null
        val watts=(loadDelta-room.delta)/rp
        room.delta+=watts*dt/room.heatCapacity
        if(watts!=0.0)setDirty()
        return watts
    }
    fun advance(dt: Double) {
        for(id in activeRooms) {
            val room=rooms[id] ?: continue
            room.delta-=room.delta*room.conductance*dt/room.heatCapacity
            if(room.delta!=0.0)setDirty()
        }
    }
    override fun save(tag: CompoundTag): CompoundTag {
        tag.putInt("version",1)
        tag.put("rooms",ListTag().also { list -> for(room in rooms.values)list.add(CompoundTag().also {
            it.putString("id",room.id);it.putDouble("delta",room.delta)
            it.putLongArray("cells",room.cells.map(BlockPos::asLong));it.putLongArray("anchors",room.anchors.map(BlockPos::asLong))
        }) });return tag
    }
    companion object {
        fun load(tag: CompoundTag)=RoomThermalData().also { data ->
            if(tag.getInt("version")==1)for(entry in tag.getList("rooms",10).take(4096)) {
                val t=entry as CompoundTag;val raw=t.getLongArray("cells")
                if(raw.isEmpty() || raw.size>65536 || !t.getDouble("delta").isFinite())continue
                val positions=raw.mapTo(hashSetOf(),BlockPos::of)
                val bounds=RoomBounds.of(positions)
                if(maxOf(bounds.width,bounds.height,bounds.depth)>96)continue
                val id=t.getString("id").take(256);if(id.isEmpty())continue
                data.rooms[id]=ThermalRoom(id,positions,t.getLongArray("anchors").take(65536).mapTo(hashSetOf(),BlockPos::of),t.getDouble("delta").coerceIn(-273.15,1e6))
            }
            data.refreshIndices()
        }
    }
}

/** Original bounded air flood fill and room/door heat exchange, without loading absent chunks. */
object RoomThermal {
    fun data(level: ServerLevel)=level.dataStorage.computeIfAbsent(RoomThermalData::load,::RoomThermalData,"eln_room_thermal")
    private fun state(level: ServerLevel,pos: BlockPos)=level.chunkSource.getChunkNow(pos.x shr 4,pos.z shr 4)?.getBlockState(pos)
    fun scan(level: ServerLevel,seed: BlockPos,thermalNodes: Collection<BlockPos>): ThermalRoom? {
        if(state(level,seed)?.let { it.isAir || it.block==mods.eln.modern.Content.lightSpot.get() }!=true)return null
        val visited=hashSetOf(seed.immutable());val queue=ArrayDeque<BlockPos>();queue.add(seed.immutable())
        var minX=seed.x;var minY=seed.y;var minZ=seed.z;var maxX=seed.x;var maxY=seed.y;var maxZ=seed.z
        val maxSpan=ForgePortConfig.roomMaxSpan.get();val maxVolume=ForgePortConfig.roomMaxVolume.get()
        while(queue.isNotEmpty()) {
            val pos=queue.removeFirst()
            for(side in Direction.values()) {
                val next=pos.relative(side)
                if(next.y<level.minBuildHeight || next.y>=level.maxBuildHeight)return null
                if(next in visited)continue
                val block=state(level,next) ?: return null
                if(!block.isAir && block.block!=mods.eln.modern.Content.lightSpot.get())continue
                visited.add(next);queue.add(next)
                minX=minOf(minX,next.x);minY=minOf(minY,next.y);minZ=minOf(minZ,next.z)
                maxX=maxOf(maxX,next.x);maxY=maxOf(maxY,next.y);maxZ=maxOf(maxZ,next.z)
                if(maxOf(maxX-minX+1,maxY-minY+1,maxZ-minZ+1)>maxSpan || visited.size>maxVolume)return null
            }
        }
        return data(level).register(visited,thermalNodes)
    }
    fun tick(level: ServerLevel,thermalNodes: Collection<BlockPos>): RoomThermalData {
        val data=data(level)
        if(data.immediateScan || level.gameTime%40L==0L) {
            data.immediateScan=false
            if(thermalNodes.isNotEmpty())for(player in level.players()) {
                var seed: BlockPos?=null
                search@ for(radius in 0..2)for(dx in -radius..radius)for(dy in -radius..radius)for(dz in -radius..radius) {
                    if(kotlin.math.abs(dx)+kotlin.math.abs(dy)+kotlin.math.abs(dz)>radius)continue
                    val pos=player.blockPosition().offset(dx,dy,dz)
                    if(state(level,pos)?.let { it.isAir || it.block==mods.eln.modern.Content.lightSpot.get() }==true) { seed=pos;break@search }
                }
                seed?.let { scan(level,it,thermalNodes) }
            }
        }
        data.activeRooms=data.rooms.values.filter { room ->
            val b=room.bounds
            ((b.minX shr 4)..(b.maxX shr 4)).all { x -> ((b.minZ shr 4)..(b.maxZ shr 4)).all { z -> level.chunkSource.isPositionTicking(net.minecraft.world.level.ChunkPos.asLong(x,z)) } }
        }.mapTo(hashSetOf()) { it.id }
        for(room in data.rooms.values.filter { it.id in data.activeRooms }) {
            if(room.doorScanTick!=Long.MIN_VALUE && level.gameTime-room.doorScanTick<10)continue
            room.doorScanTick=level.gameTime
            val doors=hashSetOf<BlockPos>()
            for(pos in room.cells)for(side in Direction.values()) {
                val next=pos.relative(side);if(next in room.cells)continue
                val block=state(level,next) ?: continue
                if(block.block !is DoorBlock)continue
                val bottom=if(block.getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER)next.below()else next
                if(state(level,bottom)?.let { it.block is DoorBlock && it.getValue(DoorBlock.OPEN) }==true)doors.add(bottom)
            }
            room.openDoors=doors.size
        }
        return data
    }
}
