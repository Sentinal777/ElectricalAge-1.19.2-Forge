package mods.eln.modern.client

import com.mojang.blaze3d.systems.RenderSystem
import mods.eln.modern.ArmorRuntime
import mods.eln.modern.PortArmorItem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiComponent
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RenderGuiOverlayEvent
import net.minecraftforge.client.gui.overlay.ForgeGui
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

/** The original special-armor display reports current shield charge, rather than fixed armor points. */
@Mod.EventBusSubscriber(modid="eln",value=[Dist.CLIENT])
object ArmorHud {
    @JvmStatic @SubscribeEvent fun render(event: RenderGuiOverlayEvent.Pre) {
        if(event.overlay.id()!=VanillaGuiOverlay.ARMOR_LEVEL.id())return
        val mc=Minecraft.getInstance();val player=mc.player ?: return
        if(player.armorSlots.none { (it.item as? PortArmorItem)?.definition?.electrical!=null })return
        event.isCanceled=true
        if(mc.gameMode?.canHurtPlayer()!=true || player.isSpectator)return
        val gui=mc.gui as ForgeGui
        val armor=(player.armorValue+player.armorSlots.sumOf { ArmorRuntime.display(it) }).coerceIn(0,20)
        val x=event.window.guiScaledWidth/2-91;val y=event.window.guiScaledHeight-gui.leftHeight
        gui.leftHeight+=10
        if(armor<=0)return
        RenderSystem.enableBlend();RenderSystem.setShaderColor(1f,1f,1f,1f);RenderSystem.setShaderTexture(0,GuiComponent.GUI_ICONS_LOCATION)
        for(index in 0 until 10) {
            val u=if(index*2+1<armor)34 else if(index*2+1==armor)25 else 16
            GuiComponent.blit(event.poseStack,x+index*8,y,0,u.toFloat(),9f,9,9,256,256)
        }
        RenderSystem.disableBlend()
    }
}
