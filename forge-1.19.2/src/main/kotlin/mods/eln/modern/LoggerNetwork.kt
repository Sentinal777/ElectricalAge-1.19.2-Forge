package mods.eln.modern

import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.simple.SimpleChannel
import java.util.function.Supplier

data class ConfigureLogger(val pos: BlockPos,val face: Int,val command: Int,val period: Double,val low: Double,val high: Double,val unit: Int,val zero: Boolean) {
    fun encode(b: FriendlyByteBuf) { b.writeBlockPos(pos);b.writeByte(face);b.writeVarInt(command);b.writeDouble(period);b.writeDouble(low);b.writeDouble(high);b.writeVarInt(unit);b.writeBoolean(zero) }
    fun handle(context: Supplier<NetworkEvent.Context>) {
        val ctx=context.get();ctx.enqueueWork {
            val player=ctx.sender ?: return@enqueueWork;val menu=player.containerMenu as? DeviceMenu ?: return@enqueueWork;val p=menu.part ?: return@enqueueWork
            if(p.definition.behavior!=Behavior.LOGGER || !menu.stillValid(player) || menu.node.blockPos!=pos || menu.face!=face || !player.level.mayInteract(player,pos) || command !in 0..3)return@enqueueWork
            if(command==0)DataLoggers.configure(menu.node,p,period,low,high,unit,zero)else DataLoggers.command(menu.node,p,command)
        };ctx.packetHandled=true
    }
    companion object { fun decode(b: FriendlyByteBuf)=ConfigureLogger(b.readBlockPos(),b.readByte().toInt(),b.readVarInt(),b.readDouble(),b.readDouble(),b.readDouble(),b.readVarInt(),b.readBoolean()) }
}
object LoggerNetwork {
    fun register(channel: SimpleChannel) {
        channel.messageBuilder(ConfigureLogger::class.java,2,NetworkDirection.PLAY_TO_SERVER).encoder { p,b -> p.encode(b) }.decoder(ConfigureLogger::decode).consumerMainThread { p,c -> p.handle(c) }.add()
    }
}

object DataLoggerAutomation {
    fun handler(node: FaceNodeEntity)=object : net.minecraftforge.items.IItemHandler {
        private fun inventory()=if(node.isRemoved)null else node.parts.filterNotNull().firstOrNull { it.definition.behavior==Behavior.LOGGER }?.let { PartInventory.get(node,it) }
        override fun getSlots()=2
        override fun getStackInSlot(slot: Int)=if(slot in 0..1)inventory()?.getStackInSlot(slot) ?: net.minecraft.world.item.ItemStack.EMPTY else net.minecraft.world.item.ItemStack.EMPTY
        override fun insertItem(slot: Int,stack: net.minecraft.world.item.ItemStack,simulate: Boolean)=if(slot==0)inventory()?.insertItem(slot,stack,simulate) ?: stack else stack
        override fun extractItem(slot: Int,amount: Int,simulate: Boolean)=if(slot==1)inventory()?.extractItem(slot,amount,simulate) ?: net.minecraft.world.item.ItemStack.EMPTY else net.minecraft.world.item.ItemStack.EMPTY
        override fun getSlotLimit(slot: Int)=if(slot==0)64 else if(slot==1)1 else 0
        override fun isItemValid(slot: Int,stack: net.minecraft.world.item.ItemStack)=slot==0 && inventory()?.isItemValid(slot,stack)==true
    }
}
