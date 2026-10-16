package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.modern.*
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

class TutorialSignScreen(menu: DeviceMenu,inventory: Inventory,title: Component): AbstractContainerScreen<DeviceMenu>(menu,inventory,title) {
    private lateinit var beacon: EditBox
    init { imageWidth=220;imageHeight=64 }
    override fun init() {
        super.init()
        beacon=EditBox(font,leftPos+8,topPos+24,146,18,Component.translatable("gui.eln.tutorial_beacon")).apply { setMaxLength(64);value=menu.part?.gridState?.getString("tutorialBeacon").orEmpty() };addRenderableWidget(beacon)
        addRenderableWidget(Button(leftPos+160,topPos+23,52,20,Component.translatable("gui.eln.apply")) { Network.channel.sendToServer(ConfigureDevice(menu.node.blockPos,menu.face,0.0,1.0,true,"",beacon.value)) })
    }
    override fun renderBg(pose: PoseStack,partialTick: Float,mouseX: Int,mouseY: Int) {
        fill(pose,leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF383838.toInt());fill(pose,leftPos+2,topPos+2,leftPos+imageWidth-2,topPos+imageHeight-2,0xFFC6C6C6.toInt())
    }
    override fun renderLabels(pose: PoseStack,mouseX: Int,mouseY: Int) { font.draw(pose,title,8f,6f,0x404040);font.draw(pose,Component.translatable("gui.eln.tutorial_beacon"),8f,49f,0x404040) }
    override fun render(pose: PoseStack,mouseX: Int,mouseY: Int,partialTick: Float) { renderBackground(pose);super.render(pose,mouseX,mouseY,partialTick);renderTooltip(pose,mouseX,mouseY) }
}
