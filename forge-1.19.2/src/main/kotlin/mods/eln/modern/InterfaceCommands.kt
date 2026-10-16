package mods.eln.modern

import mods.eln.mqtt.MqttManager
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid="eln")
object InterfaceCommands {
    @JvmStatic @SubscribeEvent fun register(event: RegisterCommandsEvent) {
        event.dispatcher.register(Commands.literal("eln").requires { it.hasPermission(2) }.then(
            Commands.literal("mqtt").then(Commands.literal("reload").executes { context ->
                MqttManager.refreshConfiguration()
                context.source.sendSuccess(Component.translatable("message.eln.mqtt_reloaded"),false);1
            })
        ))
    }
}
