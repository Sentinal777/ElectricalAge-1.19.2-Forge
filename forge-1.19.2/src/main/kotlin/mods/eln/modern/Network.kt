package mods.eln.modern

import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.NetworkRegistry
import java.util.function.Supplier

data class ConfigureDevice(val pos: BlockPos, val face: Int, val value: Double, val resistance: Double,
    val enabled: Boolean, val channel: String, val expression: String) {
    fun encode(buffer: FriendlyByteBuf) {
        buffer.writeBlockPos(pos);buffer.writeByte(face);buffer.writeDouble(value);buffer.writeDouble(resistance)
        buffer.writeBoolean(enabled);buffer.writeUtf(channel,64);buffer.writeUtf(expression,256)
    }
    fun handle(context: Supplier<NetworkEvent.Context>) {
        val ctx=context.get()
        ctx.enqueueWork {
            val player=ctx.sender ?: return@enqueueWork
            val menu=player.containerMenu as? DeviceMenu ?: return@enqueueWork
            if(!menu.stillValid(player) || menu.node.blockPos!=pos || menu.face!=face ||
                !player.level.mayInteract(player,pos) || !value.isFinite() || !resistance.isFinite()) return@enqueueWork
            val part=menu.part ?: return@enqueueWork
            if(part.definition.id=="signal_trimmer") { SimpleSignals.configureTrimmer(menu.node,part,value);return@enqueueWork }
            if(SimpleSignals.supported(part) && part.definition.id!="signal_source")return@enqueueWork
            if(part.definition.behavior==Behavior.HUB) { HubSettings.toggle(menu.node,part,channel.toIntOrNull() ?: return@enqueueWork);return@enqueueWork }
            if(part.definition.behavior==Behavior.TURRET) {
                Turrets.configure(menu.node,part,value,enabled);return@enqueueWork
            }
            if(part.definition.behavior==Behavior.TELEPORTER) {
                Transporters.configure(menu.node,part,expression,channel,value,enabled);return@enqueueWork
            }
            if(part.definition.behavior==Behavior.AUTO_MINER) {
                if(value !in setOf(0.0,1.0))return@enqueueWork
                part.value=value;menu.node.changedTopology();return@enqueueWork
            }
            if(part.definition.behavior==Behavior.FABRICATOR) {
                if(value!=value.toInt().toDouble() || value.toInt() !in FabricatorOperations.all.indices)return@enqueueWork
                FabricatorOperations.select(menu.node,part,value.toInt());return@enqueueWork
            }
            if(part.definition.behavior==Behavior.TUTORIAL_SIGN) { TutorialSigns.configure(menu.node,part,expression);return@enqueueWork }
            if(part.definition.behavior==Behavior.COMPUTER_PROBE)return@enqueueWork
            if(part.definition.behavior==Behavior.PROCESSOR) { SignalProcessors.configure(menu.node,part,expression);return@enqueueWork }
            if(part.originalBreaker && ProtectionSettings.window(expression)==null)return@enqueueWork
            if(part.definition.wireless?.kind=="RX" && !part.legacyWireless) {
                if(value!=value.toInt().toDouble() || value.toInt() !in 0..2)return@enqueueWork
                part.value=value
            }
            if(part.definition.wireless!=null && channel.trim()!=part.channel)part.gridState.putBoolean("wirelessRescan",true)
            if(part.definition.behavior==Behavior.TIMER && (!TimerSettings.duration(value) || channel !in setOf("default","set","reset")))return@enqueueWork
            if(part.definition.behavior==Behavior.TACHOMETER && SensorSettings.range(expression)==null)return@enqueueWork
            if(part.definition.id=="digital_display" && DisplaySettings.range(expression)==null)return@enqueueWork
            if(part.definition.behavior in setOf(Behavior.SENSOR,Behavior.THERMAL_SENSOR,Behavior.AMBIENT_SENSOR) && SensorSettings.range(expression)==null)return@enqueueWork
            if(part.definition.behavior==Behavior.FLOODLIGHT && mods.eln.modern.lighting.FloodlightSettings.parse(expression)==null)return@enqueueWork
            if(menu.heatControls && mods.eln.modern.thermal.HeatFurnaceRuntime.target(expression)==null)return@enqueueWork
            part.value=if(DeviceControls.valueKey(part)==null && part.definition.behavior !in setOf(Behavior.FE_CONVERTER,Behavior.SENSOR,Behavior.THERMAL_SENSOR))part.value else when(part.definition.behavior) {
                Behavior.TIMER -> value
                Behavior.FLOODLIGHT -> value.coerceIn(0.0,360.0)
                Behavior.CAPACITOR -> value.coerceIn(1e-9,1000.0)
                Behavior.INDUCTOR -> value.coerceIn(1e-9,1e6)
                Behavior.RESISTOR -> value.coerceIn(1e-6,1e12)
                Behavior.TRANSFORMER -> value.coerceIn(0.001,1000.0)
                Behavior.FE_CONVERTER -> if(value>=0.5)1.0 else 0.0
                Behavior.SENSOR -> if(part.definition.id=="voltage_probe")2.0 else value.toInt().coerceIn(0,2).toDouble()
                Behavior.THERMAL_SENSOR -> if(part.definition.id=="temperature_probe")1.0 else value.toInt().coerceIn(0,1).toDouble()
                Behavior.HEAT_FURNACE -> value.coerceIn(.1,1.0)
                Behavior.FUEL_HEAT_FURNACE -> value.coerceIn(0.0,1.0)
                Behavior.LAMP -> value.coerceIn(part.definition.lamp!!.minimumAngle,part.definition.lamp.maximumAngle)
                else -> value.coerceIn(-120000.0,120000.0)
            }
            part.definition.solar?.takeIf { it.rotating }?.let { spec ->
                if(PartInventory.get(menu.node,part).getStackInSlot(0).isEmpty)
                    part.solarAngle=(value*Math.PI/180+Math.PI/2).coerceIn(spec.minimumAngle,spec.maximumAngle)
                part.value=part.solarAngle*180/Math.PI-90
            }
            if(part.definition.wireless?.kind=="SOURCE")WirelessRuntime.activate(menu.node,part,enabled)
            else if(part.definition.behavior==Behavior.FUEL_GENERATOR)FuelGeneratorRuntime.setEnabled(menu.node,part,enabled)
            else if(DeviceControls.toggle(part.definition)) { part.enabled=enabled;part.tripped=false }
            if(DeviceControls.channel(part.definition))part.channel=channel.trim().take(64)
            if(part.definition.behavior==Behavior.SENSOR)part.channel=channel.takeIf { it in SensorSettings.directions } ?: "both"
            if(menu.heatControls)part.channel=if(part.definition.behavior==Behavior.FURNACE)if(channel=="manual")"manual" else "automatic" else if(channel=="external")"external" else "internal"
            if(DeviceControls.expressionKey(part)!=null) {
                if(part.expression!=expression)part.equationState=net.minecraft.nbt.CompoundTag()
                part.expression=expression.take(256).ifBlank { "A" }
            }
            if(part.definition.behavior==Behavior.TIMER && channel!="default")TimerSettings.command(menu.node,part,channel)
            menu.node.changedTopology()
        }
        ctx.packetHandled=true
    }
    companion object {
        fun decode(buffer: FriendlyByteBuf) = ConfigureDevice(buffer.readBlockPos(),buffer.readByte().toInt(),
            buffer.readDouble(),buffer.readDouble(),buffer.readBoolean(),buffer.readUtf(64),buffer.readUtf(256))
    }
}
data class ConfigureLampChannels(val pos: BlockPos,val face: Int,val entries: List<mods.eln.modern.lighting.LampChannel>) {
    fun encode(b: FriendlyByteBuf) {
        b.writeBlockPos(pos);b.writeByte(face)
        for(i in 0..2) { val c=entries.getOrElse(i) { mods.eln.modern.lighting.LampChannel() };b.writeUtf(c.power,64);b.writeUtf(c.signal,64);b.writeVarInt(c.mode) }
    }
    fun handle(context: Supplier<NetworkEvent.Context>) {
        val ctx=context.get();ctx.enqueueWork {
            val player=ctx.sender ?: return@enqueueWork;val menu=player.containerMenu as? DeviceMenu ?: return@enqueueWork
            val p=menu.part ?: return@enqueueWork
            if(p.definition.behavior!=Behavior.LAMP_SUPPLY || !menu.stillValid(player) || menu.node.blockPos!=pos || menu.face!=face || !player.level.mayInteract(player,pos))return@enqueueWork
            mods.eln.modern.lighting.LampChannels.write(p,entries);menu.node.changedTopology()
        };ctx.packetHandled=true
    }
    companion object {
        fun decode(b: FriendlyByteBuf)=ConfigureLampChannels(b.readBlockPos(),b.readByte().toInt(),List(3) { mods.eln.modern.lighting.LampChannel(b.readUtf(64),b.readUtf(64),b.readVarInt().coerceIn(0,2)) })
    }
}
object Network {
    val channel=NetworkRegistry.newSimpleChannel(ResourceLocation("eln","main"),{"10"},{it=="10"},{it=="10"})
    @JvmStatic fun register() {
        channel.messageBuilder(CoverModifier::class.java,6,NetworkDirection.PLAY_TO_SERVER)
            .encoder { packet,buffer -> buffer.writeBoolean(packet.down) }.decoder { CoverModifier(it.readBoolean()) }
            .consumerMainThread { packet,context -> context.get().let { ctx -> ctx.sender?.let { NodeCovers.setModifier(it,packet.down) };ctx.packetHandled=true } }.add()
        EnergyMeterNetwork.register(channel);MqttNetwork.register(channel);ModbusNetwork.register(channel)
        LoggerNetwork.register(channel)
        channel.messageBuilder(ConfigureDevice::class.java,0,NetworkDirection.PLAY_TO_SERVER)
            .encoder { packet,buffer -> packet.encode(buffer) }
            .decoder(ConfigureDevice::decode)
            .consumerMainThread { packet,ctx -> packet.handle(ctx) }.add()
        channel.messageBuilder(ConfigureLampChannels::class.java,1,NetworkDirection.PLAY_TO_SERVER)
            .encoder { packet,buffer -> packet.encode(buffer) }.decoder(ConfigureLampChannels::decode)
            .consumerMainThread { packet,ctx -> packet.handle(ctx) }.add()
    }
}
