package mods.eln.modern.client

import mods.eln.modern.Content
import mods.eln.modern.ElectricMinecartEntity
import net.minecraft.client.gui.screens.MenuScreens
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent

@Mod.EventBusSubscriber(modid="eln",value=[Dist.CLIENT],bus=Mod.EventBusSubscriber.Bus.MOD)
object ClientEvents {
    @JvmStatic @SubscribeEvent fun setup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            MenuScreens.register(Content.deviceMenu.get()) { menu,inventory,title -> if(menu.part?.definition?.behavior==mods.eln.modern.Behavior.TUTORIAL_SIGN)TutorialSignScreen(menu,inventory,title)else if(menu.part?.definition?.behavior==mods.eln.modern.Behavior.MODBUS)ModbusScreen(menu,inventory,title)else if(menu.part?.let(mods.eln.modern.MqttDevices::supported)==true)MqttScreen(menu,inventory,title)else if(menu.part?.definition?.behavior==mods.eln.modern.Behavior.PROCESSOR)SignalProcessorScreen(menu,inventory,title)else if(menu.part?.definition?.behavior==mods.eln.modern.Behavior.ENERGY_METER)EnergyMeterScreen(menu,inventory,title)else if(menu.part?.definition?.behavior==mods.eln.modern.Behavior.LOGGER)DataLoggerScreen(menu,inventory,title)else DeviceScreen(menu,inventory,title) }
            mods.eln.modern.ClientNodeHooks.tick=ClientAudio::observe
            mods.eln.modern.ClientNodeHooks.creative={ net.minecraft.client.Minecraft.getInstance().player?.abilities?.instabuild==true }
            for(item in Content.materialItems.values)if((item.get() as? mods.eln.modern.MaterialItem)?.definition?.electrical?.flashlight!=null)
                net.minecraft.client.renderer.item.ItemProperties.register(item.get(),net.minecraft.resources.ResourceLocation("eln","light_state")) { stack,_,_,_ -> mods.eln.modern.PortableLights.state(stack).toFloat() }
        }
    }
    @JvmStatic @SubscribeEvent fun renderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerBlockEntityRenderer(Content.nodeEntity.get(),::NodeRenderer)
        event.registerEntityRenderer(Content.replicator.get(),::ReplicatorRenderer)
        event.registerEntityRenderer(Content.electricMinecart.get()) { context -> net.minecraft.client.renderer.entity.MinecartRenderer<ElectricMinecartEntity>(context,net.minecraft.client.model.geom.ModelLayers.MINECART) }
    }
    @JvmStatic @SubscribeEvent fun reload(event: RegisterClientReloadListenersEvent) {
        event.registerReloadListener(ResourceManagerReloadListener { LegacyMeshes.clear() })
    }
}
