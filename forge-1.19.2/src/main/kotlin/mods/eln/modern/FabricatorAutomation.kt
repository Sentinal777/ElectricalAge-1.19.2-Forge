package mods.eln.modern

import net.minecraft.world.item.ItemStack
import net.minecraftforge.items.IItemHandler

object FabricatorAutomation {
    fun handler(node: FaceNodeEntity)=object : IItemHandler {
        private fun inventory()=if(node.isRemoved)null else node.parts.filterNotNull().firstOrNull { it.definition.behavior==Behavior.FABRICATOR }?.let { PartInventory.get(node,it) }
        override fun getSlots()=3
        override fun getStackInSlot(slot: Int)=if(slot in 0..2)inventory()?.getStackInSlot(slot) ?: ItemStack.EMPTY else ItemStack.EMPTY
        override fun insertItem(slot: Int,stack: ItemStack,simulate: Boolean)=if(slot in 1..2)inventory()?.insertItem(slot,stack,simulate) ?: stack else stack
        override fun extractItem(slot: Int,amount: Int,simulate: Boolean)=if(slot==0)inventory()?.extractItem(slot,amount,simulate) ?: ItemStack.EMPTY else ItemStack.EMPTY
        override fun getSlotLimit(slot: Int)=if(slot in 0..2)64 else 0
        override fun isItemValid(slot: Int,stack: ItemStack)=slot in 1..2 && inventory()?.isItemValid(slot,stack)==true
    }
}
