package mods.eln.modern.lighting

import mods.eln.modern.Content
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.Material

/** Original five-tick projected-light leases, compacted to one expiry per brightness. */
class LightSpotEntity(pos: BlockPos,state: BlockState): BlockEntity(Content.lightSpotEntity.get(),pos,state) {
    private var expires=LongArray(16)
    private var lastLightCheck=Long.MIN_VALUE
    private var darkUntil=Long.MIN_VALUE
    override fun saveAdditional(t: CompoundTag) { super.saveAdditional(t);t.putLongArray("expires",expires) }
    override fun load(t: CompoundTag) { super.load(t);expires=t.getLongArray("expires").copyOf(16) }
    override fun onLoad() { super.onLoad();LightSpotLeases.attach(this) }
    override fun setRemoved() { LightSpotLeases.detach(this);(level as? ServerLevel)?.let { LightRecovery.forget(it,blockPos) };super.setRemoved() }
    fun add(light: Int,duration: Int=5) {
        val world=level ?: return;LightSpotLeases.attach(this);expires[light.coerceIn(0,15)]=maxOf(expires[light.coerceIn(0,15)],world.gameTime+duration.coerceIn(1,1200));setChanged();tick()
        // Chunk lighting can complete after the initial placement; revisit the source after that task.
        if(world is ServerLevel && world.gameTime%5==0L && lastLightCheck!=world.gameTime) {
            lastLightCheck=world.gameTime
            world.chunkSource.lightEngine.updateSectionStatus(net.minecraft.core.SectionPos.of(blockPos),false)
            world.chunkSource.lightEngine.enableLightSources(net.minecraft.world.level.ChunkPos(blockPos),true)
            world.chunkSource.lightEngine.checkBlock(blockPos)
            LightRecovery.observe(world,blockPos,blockState.getValue(BlockStateProperties.LEVEL))
        }
    }
    fun tick() {
        val world=level as? ServerLevel ?: return
        val light=(15 downTo 1).firstOrNull { expires[it]>world.gameTime } ?: 0
        if(light==0) {
            LightRecovery.forget(world,blockPos)
            if(darkUntil==Long.MIN_VALUE) {
                darkUntil=world.gameTime+2
                if(blockState.getValue(BlockStateProperties.LEVEL)>0)world.setBlock(blockPos,blockState.setValue(BlockStateProperties.LEVEL,0),3)
                world.chunkSource.lightEngine.checkBlock(blockPos)
            } else if(world.gameTime>=darkUntil)world.removeBlock(blockPos,false)
            return
        }
        darkUntil=Long.MIN_VALUE
        if(blockState.getValue(BlockStateProperties.LEVEL)!=light)world.setBlock(blockPos,blockState.setValue(BlockStateProperties.LEVEL,light),3)
    }
}

/** Loaded view-distance chunks can outlive ordinary block ticking; their short light leases still expire. */
object LightSpotLeases {
    private val loaded=java.util.IdentityHashMap<ServerLevel,MutableMap<Long,LightSpotEntity>>()
    fun attach(entity: LightSpotEntity) {
        val world=entity.level as? ServerLevel ?: return
        if(!entity.isRemoved)loaded.getOrPut(world) { linkedMapOf() }[entity.blockPos.asLong()]=entity
    }
    fun detach(entity: LightSpotEntity) {
        loaded[entity.level]?.let { entries -> if(entries[entity.blockPos.asLong()]===entity)entries.remove(entity.blockPos.asLong()) }
    }
    fun tick(world: ServerLevel) {
        for(entity in loaded[world]?.values?.toList().orEmpty()) {
            val at=entity.blockPos
            val chunk=world.chunkSource.getChunkNow(at.x shr 4,at.z shr 4)
            if(entity.isRemoved || chunk?.getBlockEntity(at)!==entity)detach(entity) else entity.tick()
        }
    }
    fun unload(world: ServerLevel) { loaded.remove(world) }
    fun clear() { loaded.clear() }
}
class LightSpotBlock: BaseEntityBlock(Properties.of(Material.AIR).noCollission().noOcclusion().strength(0f).lightLevel { it.getValue(BlockStateProperties.LEVEL) }) {
    init { registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.LEVEL,0)) }
    override fun createBlockStateDefinition(builder: StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState>) { builder.add(BlockStateProperties.LEVEL) }
    override fun newBlockEntity(pos: BlockPos,state: BlockState)=LightSpotEntity(pos,state)
    override fun getRenderShape(state: BlockState)=RenderShape.INVISIBLE
    override fun getShape(state: BlockState,level: net.minecraft.world.level.BlockGetter,pos: BlockPos,context: net.minecraft.world.phys.shapes.CollisionContext)=net.minecraft.world.phys.shapes.Shapes.empty()
    override fun getLightBlock(state: BlockState,level: net.minecraft.world.level.BlockGetter,pos: BlockPos)=0
    override fun propagatesSkylightDown(state: BlockState,level: net.minecraft.world.level.BlockGetter,pos: BlockPos)=true
    override fun <T: BlockEntity> getTicker(level: Level,state: BlockState,type: BlockEntityType<T>): BlockEntityTicker<T>? =
        if(!level.isClientSide && type==Content.lightSpotEntity.get())BlockEntityTicker { _,_,_,entity -> (entity as LightSpotEntity).tick() }else null
    companion object {
        fun illuminate(world: ServerLevel,pos: BlockPos,light: Int,duration: Int=5) {
            if(!world.hasChunkAt(pos) || world.isOutsideBuildHeight(pos) || light<=0)return
            val state=world.getBlockState(pos)
            if(state.block!=Content.lightSpot.get()) {
                if(!state.isAir)return
                world.setBlock(pos,Content.lightSpot.get().defaultBlockState().setValue(BlockStateProperties.LEVEL,light),3)
                // A projected source may be the first occupied block in a high, previously empty section.
                world.chunkSource.lightEngine.updateSectionStatus(net.minecraft.core.SectionPos.of(pos),false)
                world.chunkSource.lightEngine.enableLightSources(net.minecraft.world.level.ChunkPos(pos),true)
                world.chunkSource.lightEngine.checkBlock(pos)
            }
            (world.getBlockEntity(pos) as? LightSpotEntity)?.add(light,duration)
        }
    }
}
