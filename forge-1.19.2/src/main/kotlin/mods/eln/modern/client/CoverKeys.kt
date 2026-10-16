package mods.eln.modern.client

import com.mojang.blaze3d.platform.InputConstants
import mods.eln.modern.*
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.lwjgl.glfw.GLFW

@Mod.EventBusSubscriber(modid="eln",value=[Dist.CLIENT],bus=Mod.EventBusSubscriber.Bus.MOD)
object CoverKeys {
    val key=KeyMapping("key.eln.cover",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_C,"key.categories.eln")
    fun down()=Minecraft.getInstance().screen==null && key.isDown
    @JvmStatic @SubscribeEvent fun register(event: RegisterKeyMappingsEvent) { event.register(key);ClientNodeHooks.coverModifier=::down }
}

@Mod.EventBusSubscriber(modid="eln",value=[Dist.CLIENT])
object CoverKeyState {
    private var last=false;private var ticks=0
    @JvmStatic @SubscribeEvent fun tick(event: TickEvent.ClientTickEvent) {
        if(event.phase!=TickEvent.Phase.END)return
        val mc=Minecraft.getInstance();ticks++
        if(mc.player==null || mc.connection==null) { last=false;return }
        val down=CoverKeys.down()
        if(down!=last || down && ticks%20==0) { Network.channel.sendToServer(CoverModifier(down));last=down }
    }
}
