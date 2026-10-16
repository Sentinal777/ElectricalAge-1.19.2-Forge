package mods.eln.modern

import net.minecraft.core.Direction
import net.minecraft.nbt.NbtUtils
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraftforge.common.util.BlockSnapshot
import net.minecraftforge.event.ForgeEventFactory
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

/** Original SixNode cache/disguise: one consumed block, live hidden circuits, first break peels the cover. */
@Mod.EventBusSubscriber(modid="eln")
object NodeCovers {
    private val modifiers=java.util.WeakHashMap<Player,Int>()
    fun setModifier(player: Player,down: Boolean) { if(down)modifiers[player]=player.tickCount else modifiers.remove(player) }
    fun modifier(player: Player)=if(player.level.isClientSide)ClientNodeHooks.coverModifier()else modifiers[player]?.let { player.tickCount-it in 0..40 }==true
    fun clearModifiers()=modifiers.clear()
    fun present(node: FaceNodeEntity)=!node.isMachine && !node.coverStateData.isEmpty
    fun state(node: FaceNodeEntity): BlockState? {
        if(!present(node))return null
        return runCatching { NbtUtils.readBlockState(node.coverStateData) }.getOrNull()?.takeUnless { it.isAir } ?: Blocks.STONE.defaultBlockState()
    }
    fun stack(node: FaceNodeEntity)=if(!present(node))ItemStack.EMPTY else ItemStack.of(node.coverItemData).also { if(!it.isEmpty)it.count=1 }
    private fun accepted(node: FaceNodeEntity,stack: ItemStack): BlockState? {
        val level=node.level ?: return null
        if(node.blockState.block!=Content.node.get() || node.parts.filterNotNull().isEmpty() || node.parts.filterNotNull().any { it.definition.machine })return null
        val item=stack.item as? BlockItem ?: return null
        if(stack.isEmpty || stack.tag?.contains("BlockEntityTag")==true)return null
        val data=NbtUtils.writeBlockState(item.block.defaultBlockState())
        stack.getTagElement("BlockStateTag")?.let { properties ->
            val merged=data.getCompound("Properties").copy();merged.merge(properties);data.put("Properties",merged)
        }
        val state=runCatching { NbtUtils.readBlockState(data) }.getOrNull() ?: return null
        if(state.block is NodeBlock || state.hasBlockEntity() || state.renderShape!=RenderShape.MODEL || !state.fluidState.isEmpty)return null
        // The original accepted normal cube/log/quartz renderers and rejected container blocks.
        // Modern block-entity and partial-shape models are not copied into a node.
        if(!Block.isShapeFullBlock(state.getCollisionShape(level,node.blockPos)))return null
        return state
    }
    fun interact(node: FaceNodeEntity,player: Player,hand: InteractionHand,hit: BlockHitResult): InteractionResult? {
        if(present(node) || node.blockState.getValue(NodeBlock.COVERED))return InteractionResult.PASS
        if(!modifier(player))return null
        val held=player.getItemInHand(hand);val cover=accepted(node,held) ?: return null
        val level=node.level ?: return InteractionResult.FAIL
        if(!level.mayInteract(player,node.blockPos) || !player.mayUseItemAt(node.blockPos,hit.direction,held))return InteractionResult.FAIL
        if(level.isClientSide)return InteractionResult.SUCCESS
        if(level !is ServerLevel || node.isRemoved || level.getBlockEntity(node.blockPos)!==node)return InteractionResult.FAIL
        // Covering changes this carrier's metadata. Let protection mods reject that placement before any mutation.
        val snapshot=BlockSnapshot.create(level.dimension(),level,node.blockPos)
        if(ForgeEventFactory.onBlockPlace(player,snapshot,hit.direction) || level.getBlockEntity(node.blockPos)!==node || player.getItemInHand(hand)!==held || held.isEmpty)return InteractionResult.FAIL
        node.coverStateData=NbtUtils.writeBlockState(cover)
        node.coverItemData=held.copy().apply { count=1 }.save(net.minecraft.nbt.CompoundTag())
        if(!player.abilities.instabuild)held.shrink(1)
        node.setChanged();node.sync()
        level.playSound(null,node.blockPos,cover.soundType.placeSound,net.minecraft.sounds.SoundSource.BLOCKS,(cover.soundType.volume+1)/2,cover.soundType.pitch*.8f)
        mods.eln.modern.thermal.RoomThermal.data(level).invalidate(node.blockPos)
        return InteractionResult.SUCCESS
    }
    /** Original C modifier, including a held sneaking player; preserve ordinary adjacent block placement. */
    @JvmStatic @SubscribeEvent(priority=EventPriority.LOWEST)
    fun rightClick(event: PlayerInteractEvent.RightClickBlock) {
        if(event.useBlock==Event.Result.DENY || !modifier(event.entity))return
        val node=event.level.getBlockEntity(event.pos) as? FaceNodeEntity ?: return
        if(present(node))return
        val result=interact(node,event.entity,event.hand,event.hitVec) ?: return
        event.cancellationResult=result;event.isCanceled=true
    }
    fun peel(node: FaceNodeEntity,player: Player): Boolean {
        val level=node.level as? ServerLevel ?: return false
        if(!present(node) || node.isRemoved || level.getBlockEntity(node.blockPos)!==node || !level.mayInteract(player,node.blockPos))return false
        val item=stack(node)
        // Retain unavailable-mod item/state data rather than silently destroying it.
        if(!player.abilities.instabuild && item.isEmpty)return false
        node.coverStateData=net.minecraft.nbt.CompoundTag();node.coverItemData=net.minecraft.nbt.CompoundTag()
        node.setChanged();node.sync();mods.eln.modern.thermal.RoomThermal.data(level).invalidate(node.blockPos)
        if(!player.abilities.instabuild)Block.popResource(level,node.blockPos,item)
        return true
    }
    fun drop(node: FaceNodeEntity) {
        if(!present(node))return
        val item=stack(node)
        node.coverStateData=net.minecraft.nbt.CompoundTag();node.coverItemData=net.minecraft.nbt.CompoundTag()
        if(!item.isEmpty)node.level?.let { Block.popResource(it,node.blockPos,item) }
    }
}
