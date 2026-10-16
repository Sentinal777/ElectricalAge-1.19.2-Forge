package mods.eln.modern.grid

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Material
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.*

class GridGhostEntity(pos: BlockPos,state: BlockState): BlockEntity(Content.gridGhostEntity.get(),pos,state) {
    var owner=pos
    var ownerId=""
    var bounds=AABB(0.0,0.0,0.0,1.0,1.0,1.0)
    override fun saveAdditional(t: CompoundTag) {
        super.saveAdditional(t);t.putLong("owner",owner.asLong());t.putString("ownerId",ownerId)
        listOf(bounds.minX,bounds.minY,bounds.minZ,bounds.maxX,bounds.maxY,bounds.maxZ).forEachIndexed { i,v -> t.putDouble("b$i",v) }
    }
    override fun load(t: CompoundTag) {
        super.load(t);owner=BlockPos.of(t.getLong("owner"));ownerId=t.getString("ownerId")
        val b=(0..5).map { i -> t.getDouble("b$i").takeIf { it.isFinite() }?.coerceIn(0.0,1.0) ?: 0.0 }
        bounds=AABB(b[0],b[1],b[2],b[3],b[4],b[5])
    }
    override fun getUpdateTag()=saveWithoutMetadata()
    override fun getUpdatePacket()=ClientboundBlockEntityDataPacket.create(this)
    override fun onLoad() { super.onLoad();GridMultiblock.loaded(this) }
    override fun setRemoved() { GridMultiblock.unloaded(this);super.setRemoved() }
    fun root(): FaceNodeEntity? {
        val world=level ?: return null
        if(world is ServerLevel) {
            val chunk=world.chunkSource.getChunkNow(Math.floorDiv(owner.x,16),Math.floorDiv(owner.z,16)) ?: return null
            return chunk.getBlockEntity(owner) as? FaceNodeEntity
        }
        return if(world.hasChunkAt(owner))world.getBlockEntity(owner) as? FaceNodeEntity else null
    }
}

class GridGhostBlock: BaseEntityBlock(Properties.of(Material.METAL).strength(1.5f).noOcclusion().dynamicShape()) {
    override fun newBlockEntity(pos: BlockPos,state: BlockState)=GridGhostEntity(pos,state)
    override fun getRenderShape(state: BlockState)=RenderShape.INVISIBLE
    override fun getShape(state: BlockState,level: BlockGetter,pos: BlockPos,context: CollisionContext): VoxelShape =
        (level.getBlockEntity(pos) as? GridGhostEntity)?.let { Shapes.create(it.bounds) } ?: Shapes.empty()
    override fun use(state: BlockState,level: Level,pos: BlockPos,player: Player,hand: InteractionHand,hit: BlockHitResult): InteractionResult {
        val root=(level.getBlockEntity(pos) as? GridGhostEntity)?.root() ?: return InteractionResult.PASS
        return root.blockState.use(level,player,hand,BlockHitResult(hit.location,hit.direction,root.blockPos,hit.isInside))
    }
    override fun getCloneItemStack(state: BlockState,hit: HitResult,level: BlockGetter,pos: BlockPos,player: Player)=
        (level.getBlockEntity(pos) as? GridGhostEntity)?.root()?.let { root ->
            GridMultiblock.part(root)?.let { net.minecraft.world.item.ItemStack(Content.partItems.getValue(it.definition.id).get()) }
        } ?: net.minecraft.world.item.ItemStack.EMPTY
    override fun onRemove(state: BlockState,level: Level,pos: BlockPos,newState: BlockState,moving: Boolean) {
        if(state.block!=newState.block && !level.isClientSide) {
            val ghost=level.getBlockEntity(pos) as? GridGhostEntity
            if(ghost!=null && !GridMultiblock.isRemoving(level,ghost.owner)) {
                if(level.hasChunkAt(ghost.owner)) {
                    val root=ghost.root();val part=root?.let(GridMultiblock::part)
                    if(part!=null && (ghost.ownerId.isBlank() || part.gridId==ghost.ownerId)) {
                        if(part.definition.behavior==Behavior.AUTO_MINER && AutoMiners.isPipe(root!!,part,pos))AutoMiners.releasePipes(root,part)
                        else level.destroyBlock(ghost.owner,true)
                    }
                } else if(ghost.ownerId.isNotBlank() && level is ServerLevel) {
                    val data=GridNetwork.data(level);data.brokenOwners[ghost.owner.immutable()]=ghost.ownerId;data.setDirty()
                }
            }
        }
        super.onRemove(state,level,pos,newState,moving)
    }
}

/** Reserved cells match the original GhostGroup, with collision clipped to each occupied block. */
object GridMultiblock {
    fun part(node: FaceNodeEntity)=node.parts.filterNotNull().firstOrNull { it.definition.multiblock }
    private val removing=mutableSetOf<Pair<Level,BlockPos>>()
    private val loadedGhosts=java.util.IdentityHashMap<ServerLevel,MutableMap<BlockPos,GridGhostEntity>>()
    fun loaded(ghost: GridGhostEntity) { (ghost.level as? ServerLevel)?.let { loadedGhosts.getOrPut(it) { linkedMapOf() }[ghost.blockPos]=ghost } }
    fun unloaded(ghost: GridGhostEntity) { loadedGhosts[ghost.level]?.let { map -> if(map[ghost.blockPos]===ghost)map.remove(ghost.blockPos) } }
    fun unload(level: ServerLevel) { loadedGhosts.remove(level) }
    fun clear() { loadedGhosts.clear();removing.clear() }
    fun maintain(level: ServerLevel) {
        val data=GridNetwork.data(level)
        for((pos,id) in data.brokenOwners.toMap())if(level.hasChunkAt(pos)) {
            data.brokenOwners.remove(pos);data.setDirty()
            val root=level.getBlockEntity(pos) as? FaceNodeEntity
            if(root?.let(::part)?.gridId==id)level.destroyBlock(pos,true)
        }
        if(level.gameTime%20L!=0L)return
        for(ghost in loadedGhosts[level]?.values?.toList().orEmpty()) {
            if(ghost.isRemoved || !level.hasChunkAt(ghost.owner))continue
            val root=ghost.root();val p=root?.let(::part)
            if(p!=null && ghost.ownerId.isBlank()) { ghost.ownerId=p.gridId;ghost.setChanged() }
            if(p==null || ghost.ownerId!=p.gridId || ghost.blockPos !in cells(ghost.owner,p)) {
                val key=level to ghost.owner;removing.add(key)
                try { level.removeBlock(ghost.blockPos,false) } finally { removing.remove(key) }
            }
        }
    }
    fun isRemoving(level: Level,pos: BlockPos)=level to pos in removing
    fun offset(pos: BlockPos,p: PartState,a: List<Int>): BlockPos {
        val edges=FaceBasis.edges(p);val u=edges[0];val v=edges[1]
        return pos.offset(a[0]*v.stepX-a[2]*u.stepX,a[1],a[0]*v.stepZ-a[2]*u.stepZ)
    }
    fun cells(pos: BlockPos,p: PartState)=if(p.definition.behavior==Behavior.TELEPORTER)Transporters.cells(pos,p)
        else p.definition.reservedCells.map { offset(pos,p,it) }+if(p.definition.behavior==Behavior.AUTO_MINER)List(AutoMiners.depth(p)) { pos.below(it+1) }else emptyList()
    fun canPlace(level: Level,pos: BlockPos,p: PartState,player: Player?=null): Boolean {
        if(!p.definition.multiblock)return true
        val needed=cells(pos,p)+if(p.definition.behavior==Behavior.TELEPORTER)Transporters.closedDoor.map { offset(pos,p,it) }else emptyList()
        return (needed+pos).all { cell ->
            level.hasChunkAt(cell) && !level.isOutsideBuildHeight(cell) && level.worldBorder.isWithinBounds(cell) &&
                (player==null || level.mayInteract(player,cell) && player.mayUseItemAt(cell,net.minecraft.core.Direction.UP,player.mainHandItem)) &&
                (cell==pos || level.getBlockState(cell).material.isReplaceable ||
                    (level.getBlockEntity(cell) as? GridGhostEntity)?.owner==pos)
        }
    }
    fun install(node: FaceNodeEntity,p: PartState) {
        val level=node.level ?: return
        if(level.isClientSide || !p.definition.multiblock)return
        for(pos in cells(node.blockPos,p)) {
            if(!level.hasChunkAt(pos))continue
            val ghost=level.getBlockEntity(pos) as? GridGhostEntity
            if(ghost?.owner!=node.blockPos) {
                if(!level.getBlockState(pos).material.isReplaceable)continue
                level.setBlock(pos,Content.gridGhost.get().defaultBlockState(),3)
            }
            val entity=level.getBlockEntity(pos) as? GridGhostEntity ?: continue
            entity.owner=node.blockPos.immutable();entity.ownerId=p.gridId
            val raw=PartGeometry.body(p).move(node.blockPos.x-pos.x.toDouble(),node.blockPos.y-pos.y.toDouble(),node.blockPos.z-pos.z.toDouble())
            val cube=AABB(0.0,0.0,0.0,1.0,1.0,1.0)
            entity.bounds=if(p.definition.behavior==Behavior.TELEPORTER && pos==Transporters.chamber(node,p))AABB(0.0,0.0,0.0,1.0,.0625,1.0)
                else if(raw.intersects(cube))raw.intersect(cube)else AABB(.45,.0,.45,.55,1.0,.55)
            entity.setChanged();level.sendBlockUpdated(pos,entity.blockState,entity.blockState,3)
        }
    }
    fun removeOwnedCell(node: FaceNodeEntity,p: PartState,pos: BlockPos) {
        val level=node.level ?: return
        val key=level to node.blockPos;val alreadyRemoving=key in removing;removing.add(key)
        try {
            val ghost=if(level.hasChunkAt(pos))level.getBlockEntity(pos) as? GridGhostEntity else null
            if(ghost!=null && ghost.owner==node.blockPos && ghost.ownerId==p.gridId)level.removeBlock(pos,false)
        } finally { if(!alreadyRemoving)removing.remove(key) }
    }
    fun remove(node: FaceNodeEntity,p: PartState) {
        val level=node.level ?: return
        val key=level to node.blockPos;removing.add(key)
        try {
            for(pos in cells(node.blockPos,p))if(level.hasChunkAt(pos) && (level.getBlockEntity(pos) as? GridGhostEntity)?.owner==node.blockPos)level.removeBlock(pos,false)
        } finally { removing.remove(key) }
    }
    fun rotate(node: FaceNodeEntity,p: PartState,player: Player): Boolean {
        val level=node.level as? ServerLevel ?: return false
        val previous=p.rotation;p.rotation=(previous+1)%4
        val fits=canPlace(level,node.blockPos,p,player);p.rotation=previous
        if(!fits)return false
        remove(node,p);p.rotation=(previous+1)%4;install(node,p);GridNetwork.rotate(level,node.blockPos)
        if(p.definition.mechanical)p.shaftJoinPending=true
        node.changedTopology();return true
    }
}
