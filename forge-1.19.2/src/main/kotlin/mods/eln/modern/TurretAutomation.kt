package mods.eln.modern

import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraftforge.items.IItemHandler

object TurretAutomation {
    fun interact(node: FaceNodeEntity,p: PartState,player: Player,held: ItemStack): Boolean {
        if((held.item as? MaterialItem)?.definition?.kind!="ENTITY_FILTER")return false
        val inventory=PartInventory.get(node,p);val previous=inventory.getStackInSlot(0)
        if(ItemStack.isSameItemSameTags(previous,held))return true
        val old=previous.copy();inventory.setStackInSlot(0,held.copy().apply { count=1 })
        if(!player.abilities.instabuild)held.shrink(1)
        if(!old.isEmpty)Block.popResource(node.level!!,node.blockPos,old)
        node.sync();return true
    }
    fun handler(node: FaceNodeEntity)=object : IItemHandler {
        private fun inventory()=if(node.isRemoved)null else node.parts.filterNotNull().firstOrNull { it.definition.behavior==Behavior.TURRET }?.let { PartInventory.get(node,it) }
        override fun getSlots()=1
        override fun getStackInSlot(slot: Int)=if(slot==0)inventory()?.getStackInSlot(0) ?: ItemStack.EMPTY else ItemStack.EMPTY
        override fun insertItem(slot: Int,stack: ItemStack,simulate: Boolean)=if(slot==0)inventory()?.insertItem(0,stack,simulate) ?: stack else stack
        override fun extractItem(slot: Int,amount: Int,simulate: Boolean)=if(slot==0)inventory()?.extractItem(0,amount,simulate) ?: ItemStack.EMPTY else ItemStack.EMPTY
        override fun getSlotLimit(slot: Int)=if(slot==0)1 else 0
        override fun isItemValid(slot: Int,stack: ItemStack)=slot==0 && inventory()?.isItemValid(0,stack)==true
    }
}
