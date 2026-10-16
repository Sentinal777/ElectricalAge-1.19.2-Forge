package mods.eln.modern

import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseRailBlock
import net.minecraft.world.level.block.state.properties.RailShape

class ElectricMinecartItem(properties: Properties): Item(properties) {
    override fun useOn(context: UseOnContext): InteractionResult {
        val level=context.level;val pos=context.clickedPos;val state=level.getBlockState(pos)
        val rail=state.block as? BaseRailBlock ?: return InteractionResult.PASS
        val player=context.player ?: return InteractionResult.PASS
        if(!level.mayInteract(player,pos) || !player.mayUseItemAt(pos,context.clickedFace,context.itemInHand))return InteractionResult.FAIL
        if(!level.isClientSide) {
            val cart=Content.electricMinecart.get().create(level) ?: return InteractionResult.FAIL
            val shape=rail.getRailDirection(state,level,pos,cart)
            val raised=shape in setOf(RailShape.ASCENDING_EAST,RailShape.ASCENDING_WEST,RailShape.ASCENDING_NORTH,RailShape.ASCENDING_SOUTH)
            cart.setPos(pos.x+.5,pos.y+.0625+if(raised).5 else 0.0,pos.z+.5)
            val tag=context.itemInHand.tag
            cart.energyJ=tag?.getDouble("EnergyBufferJ")?.takeIf(Double::isFinite)?.coerceIn(0.0,1e9) ?: 0.0
            cart.targetJ=tag?.takeIf { it.contains("EnergyBufferTargetJ") }?.getDouble("EnergyBufferTargetJ")?.takeIf(Double::isFinite)?.coerceIn(1.0,1e9) ?: 10000.0
            if(context.itemInHand.hasCustomHoverName())cart.customName=context.itemInHand.hoverName
            if(!level.addFreshEntity(cart))return InteractionResult.FAIL
            if(!player.abilities.instabuild)context.itemInHand.shrink(1)
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }
    override fun appendHoverText(stack: ItemStack,level: Level?,lines: MutableList<Component>,flag: TooltipFlag) {
        lines.add(Component.translatable("tooltip.eln.electric_minecart"))
        if(stack.tag?.contains("EnergyBufferJ")==true)lines.add(Component.translatable("message.eln.cart_energy",String.format(java.util.Locale.ROOT,"%.1f",stack.tag!!.getDouble("EnergyBufferJ"))))
    }
}
