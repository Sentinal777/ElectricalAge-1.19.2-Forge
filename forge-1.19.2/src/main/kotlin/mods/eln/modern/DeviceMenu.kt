package mods.eln.modern

import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraftforge.items.SlotItemHandler

class DeviceMenu(id: Int, inventory: Inventory, val node: FaceNodeEntity, val face: Int) : AbstractContainerMenu(Content.deviceMenu.get(), id) {
    val part get() = node.parts.getOrNull(face)
    private val boundId=part?.definition?.id
    val processing = part?.definition?.let { it.processing || it.behavior in setOf(Behavior.FURNACE,Behavior.ROLLING_SHAFT) }==true
    val fabricator=part?.definition?.behavior==Behavior.FABRICATOR
    val stackMachine=part?.definition?.processing==true
    val lighting=part?.definition?.behavior in setOf(Behavior.LAMP,Behavior.LAMP_SUPPLY,Behavior.EMERGENCY_LAMP,Behavior.FLOODLIGHT)
    val componentSlots=part?.let(PartInventory::slots) ?: 0
    val heatControls get()=part?.definition?.behavior in setOf(Behavior.HEAT_FURNACE,Behavior.FUEL_HEAT_FURNACE,Behavior.FURNACE)
    val inventoryVisible=processing || componentSlots>0 || lighting
    private val machineSlots = if(stackMachine)6 else (if(processing)2 else 0)+componentSlots
    init {
        if(stackMachine) {
            addSlot(SlotItemHandler(node.inventory,0,53,40));addSlot(SlotItemHandler(node.inventory,1,53,18))
            for(i in 0..3)addSlot(object : SlotItemHandler(node.inventory,2+i,109+(i%2)*18,40+(i/2)*18) { override fun mayPlace(stack: ItemStack)=false })
        } else if (processing) for (index in 0..1) addSlot(object : SlotItemHandler(node.inventory,index*2,53+index*65,40) {
            override fun mayPlace(stack: ItemStack) = index == 0 && super.mayPlace(stack)
        })
        if(componentSlots>0) {
            val handler=object : net.minecraftforge.items.IItemHandlerModifiable {
                private fun current()=part?.takeIf { it.definition.id==boundId }?.let { PartInventory.get(node,it) }
                override fun getSlots()=componentSlots
                override fun setStackInSlot(slot: Int,stack: ItemStack) { current()?.setStackInSlot(slot,stack) }
                override fun getStackInSlot(slot: Int)=current()?.getStackInSlot(slot) ?: ItemStack.EMPTY
                override fun insertItem(slot: Int,stack: ItemStack,simulate: Boolean)=current()?.insertItem(slot,stack,simulate) ?: stack
                override fun extractItem(slot: Int,amount: Int,simulate: Boolean)=current()?.extractItem(slot,amount,simulate) ?: ItemStack.EMPTY
                override fun getSlotLimit(slot: Int)=current()?.getSlotLimit(slot) ?: 0
                override fun isItemValid(slot: Int,stack: ItemStack)=current()?.isItemValid(slot,stack) ?: false
            }
            if(part?.definition?.behavior==Behavior.PROCESSOR)addSlot(SlotItemHandler(handler,0,190,44))
            else if(part?.definition?.behavior==Behavior.ENERGY_METER)addSlot(SlotItemHandler(handler,0,150,119))
            else if(part?.definition?.behavior==Behavior.LOGGER) {
                addSlot(SlotItemHandler(handler,0,44,184));addSlot(SlotItemHandler(handler,1,116,184))
            }else if(part?.definition?.behavior==Behavior.TURRET)addSlot(SlotItemHandler(handler,0,120,25))
            else if(part?.definition?.behavior==Behavior.AUTO_MINER) {
                addSlot(SlotItemHandler(handler,0,134,25));addSlot(SlotItemHandler(handler,1,-3000,-3000));addSlot(SlotItemHandler(handler,2,152,25))
            }else if(fabricator) {
                addSlot(SlotItemHandler(handler,0,24,61));addSlot(SlotItemHandler(handler,1,40,25));addSlot(SlotItemHandler(handler,2,8,25))
            }else if(part?.definition?.behavior==Behavior.BATTERY_CHARGER)repeat(componentSlots) { index ->
                addSlot(SlotItemHandler(handler,index,if(index<4)8+(index%2)*18 else 62,if(index<4)51+(index/2)*18 else 60))
            }else if(part?.definition?.behavior==Behavior.HUB) {
                for((i,xy) in listOf(152 to 66,80 to 112,8 to 66,80 to 21).withIndex())addSlot(SlotItemHandler(handler,i,xy.first,xy.second))
            }else if(part?.originalBreaker==true)addSlot(SlotItemHandler(handler,0,150,49))
            else if(part?.definition?.behavior==Behavior.INCUBATOR)addSlot(SlotItemHandler(handler,0,80,32))
            else if(part?.definition?.behavior==Behavior.FUSE_HOLDER || part?.originalPassive==true)repeat(componentSlots) { addSlot(SlotItemHandler(handler,it,132+it*20,24)) }
            else if(part?.definition?.behavior in setOf(Behavior.ENVIRONMENT_SENSOR,Behavior.WATCH))repeat(componentSlots) { addSlot(SlotItemHandler(handler,it,8+it*18,66)) }
            else repeat(componentSlots) { addSlot(SlotItemHandler(handler,it,if(heatControls || part?.definition?.behavior==Behavior.LAMP_SUPPLY)8+it*18 else 19+it*18,if(heatControls)106 else if(lighting)if(part?.definition?.behavior==Behavior.LAMP_SUPPLY)104 else 82 else 49)) }
        }
        val playerX=if(part?.definition?.behavior==Behavior.PROCESSOR)30 else 8
        val playerY=if(part?.definition?.behavior==Behavior.PROCESSOR)90 else if(part?.definition?.behavior==Behavior.ENERGY_METER)160 else if(part?.definition?.behavior==Behavior.LOGGER)204 else if(fabricator)122 else if(heatControls || lighting)160 else if(componentSlots>0)142 else 84
        if(inventoryVisible) for (row in 0..2) for (column in 0..8) addSlot(Slot(inventory,column+row*9+9,playerX+column*18,playerY+row*18))
        if(inventoryVisible) for (column in 0..8) addSlot(Slot(inventory,column,playerX+column*18,playerY+58))
    }
    override fun stillValid(player: Player) = !node.isRemoved && player.level === node.level &&
        part?.definition?.id==boundId && (if(part?.definition?.multiblock==true)PartGeometry.distanceTo(part!!,player.position().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(node.blockPos)))
            else player.distanceToSqr(node.blockPos.x+0.5,node.blockPos.y+0.5,node.blockPos.z+0.5))<=64
    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        if (index !in slots.indices) return ItemStack.EMPTY
        val slot = slots[index]
        if (!slot.hasItem()) return ItemStack.EMPTY
        val stack = slot.item; val copy = stack.copy()
        val success = if (index < machineSlots) moveItemStackTo(stack,machineSlots,slots.size,true)
            else if (machineSlots>0) moveItemStackTo(stack,0,if(stackMachine)2 else if(componentSlots>0)machineSlots else if(processing)1 else componentSlots,false) else false
        if (!success) return ItemStack.EMPTY
        if (stack.isEmpty) slot.set(ItemStack.EMPTY) else slot.setChanged()
        slot.onTake(player,stack)
        return copy
    }
}
