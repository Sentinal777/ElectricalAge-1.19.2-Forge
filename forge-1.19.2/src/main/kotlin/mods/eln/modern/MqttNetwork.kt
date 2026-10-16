package mods.eln.modern

import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.simple.SimpleChannel
import java.util.function.Supplier

data class ConfigureMqtt(val pos: BlockPos,val face: Int,val command: Int,val param: Int,val text: String) {
    fun encode(b: FriendlyByteBuf) { b.writeBlockPos(pos);b.writeByte(face);b.writeVarInt(command);b.writeVarInt(param);b.writeUtf(text,64) }
    fun handle(context: Supplier<NetworkEvent.Context>) {
        val ctx=context.get();ctx.enqueueWork {
            val player=ctx.sender ?: return@enqueueWork;val menu=player.containerMenu as? DeviceMenu ?: return@enqueueWork;val p=menu.part ?: return@enqueueWork
            if(!menu.stillValid(player) || menu.node.blockPos!=pos || menu.face!=face || !player.level.mayInteract(player,pos))return@enqueueWork
            MqttDevices.configure(menu.node,p,command,param,text)
        };ctx.packetHandled=true
    }
    companion object { fun decode(b: FriendlyByteBuf)=ConfigureMqtt(b.readBlockPos(),b.readByte().toInt(),b.readVarInt(),b.readVarInt(),b.readUtf(64)) }
}
object MqttNetwork {
    fun register(channel: SimpleChannel) {
        channel.messageBuilder(ConfigureMqtt::class.java,4,NetworkDirection.PLAY_TO_SERVER).encoder { p,b -> p.encode(b) }.decoder(ConfigureMqtt::decode).consumerMainThread { p,c -> p.handle(c) }.add()
    }
}
