package mods.eln.modern

import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.simple.SimpleChannel
import java.util.function.Supplier

data class ConfigureEnergyMeter(val pos: BlockPos,val face: Int,val command: Int,val value: Double,val text: String,val password: String) {
    fun encode(b: FriendlyByteBuf) { b.writeBlockPos(pos);b.writeByte(face);b.writeVarInt(command);b.writeDouble(value);b.writeUtf(text,64);b.writeUtf(password,64) }
    fun handle(context: Supplier<NetworkEvent.Context>) {
        val ctx=context.get();ctx.enqueueWork {
            val player=ctx.sender ?: return@enqueueWork;val menu=player.containerMenu as? DeviceMenu ?: return@enqueueWork;val p=menu.part ?: return@enqueueWork
            if(p.definition.id=="mqtt_energy_meter" || p.definition.behavior!=Behavior.ENERGY_METER || !menu.stillValid(player) || menu.node.blockPos!=pos || menu.face!=face || !player.level.mayInteract(player,pos))return@enqueueWork
            if(!EnergyMeterControls.command(menu.node,p,command,value,text,password))player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.eln.meter_rejected"),true)
        };ctx.packetHandled=true
    }
    companion object { fun decode(b: FriendlyByteBuf)=ConfigureEnergyMeter(b.readBlockPos(),b.readByte().toInt(),b.readVarInt(),b.readDouble(),b.readUtf(64),b.readUtf(64)) }
}
object EnergyMeterNetwork {
    fun register(channel: SimpleChannel) { channel.messageBuilder(ConfigureEnergyMeter::class.java,3,NetworkDirection.PLAY_TO_SERVER).encoder { p,b -> p.encode(b) }.decoder(ConfigureEnergyMeter::decode).consumerMainThread { p,c -> p.handle(c) }.add() }
}
