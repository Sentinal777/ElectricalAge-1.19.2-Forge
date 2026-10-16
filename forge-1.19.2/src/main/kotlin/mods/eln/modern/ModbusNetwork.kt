package mods.eln.modern

import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.simple.SimpleChannel
import java.util.function.Supplier

data class ConfigureModbus(val pos: BlockPos,val face: Int,val command: Int,val uuid: Int,val number: Int,val text: String) {
    fun encode(b: FriendlyByteBuf) { b.writeBlockPos(pos);b.writeByte(face);b.writeVarInt(command);b.writeVarInt(uuid);b.writeVarInt(number);b.writeUtf(text,64) }
    fun handle(context: Supplier<NetworkEvent.Context>) {
        val ctx=context.get();ctx.enqueueWork {
            val player=ctx.sender ?: return@enqueueWork;val menu=player.containerMenu as? DeviceMenu ?: return@enqueueWork;val p=menu.part ?: return@enqueueWork
            if(!menu.stillValid(player) || menu.node.blockPos!=pos || menu.face!=face || !player.level.mayInteract(player,pos))return@enqueueWork
            ModbusDevices.configure(menu.node,p,command,uuid,number,text)
        };ctx.packetHandled=true
    }
    companion object { fun decode(b: FriendlyByteBuf)=ConfigureModbus(b.readBlockPos(),b.readByte().toInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readUtf(64)) }
}
object ModbusNetwork {
    fun register(channel: SimpleChannel) {
        channel.messageBuilder(ConfigureModbus::class.java,5,NetworkDirection.PLAY_TO_SERVER).encoder { p,b -> p.encode(b) }.decoder(ConfigureModbus::decode).consumerMainThread { p,c -> p.handle(c) }.add()
    }
}
