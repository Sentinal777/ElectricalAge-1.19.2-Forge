package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.modern.*
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

class SignalProcessorScreen(menu: DeviceMenu,inventory: Inventory,title: Component): AbstractContainerScreen<DeviceMenu>(menu,inventory,title) {
    private lateinit var expression: EditBox
    init { imageWidth=220;imageHeight=172;inventoryLabelX=30;inventoryLabelY=79 }
    override fun init() {
        super.init()
        expression=addRenderableWidget(EditBox(font,leftPos+8,topPos+20,204,16,Component.translatable("gui.eln.expression")).apply { setMaxLength(256);value=menu.part?.expression ?: "" })
        addRenderableWidget(Button(leftPos+132,topPos+42,48,18,Component.translatable("gui.eln.apply")) {
            val p=menu.part ?: return@Button
            Network.channel.sendToServer(ConfigureDevice(menu.node.blockPos,menu.face,p.value,p.resistance,p.enabled,p.channel,expression.value))
        })
    }
    override fun renderBg(pose: PoseStack,partialTick: Float,mouseX: Int,mouseY: Int) {
        fill(pose,leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF383838.toInt());fill(pose,leftPos+2,topPos+2,leftPos+imageWidth-2,topPos+imageHeight-2,0xFFC6C6C6.toInt())
        for(slot in menu.slots) {
            val x=leftPos+slot.x;val y=topPos+slot.y
            fill(pose,x-1,y-1,x+17,y+17,0xFF373737.toInt());fill(pose,x,y,x+17,y+17,0xFFFFFFFF.toInt());fill(pose,x,y,x+16,y+16,0xFF8B8B8B.toInt())
        }
    }
    override fun renderLabels(pose: PoseStack,mouseX: Int,mouseY: Int) {
        font.draw(pose,title,8f,6f,0x404040)
        val p=menu.part ?: return
        val state=p.gridState
        val message=when {
            p.expression.isEmpty() -> Component.translatable("gui.eln.processor_empty")
            !state.getBoolean("processorValid") -> Component.translatable("gui.eln.processor_invalid")
            else -> Component.translatable("gui.eln.processor_redstone",state.getInt("processorRedstone"))
        }
        font.draw(pose,message,8f,44f,if(state.getBoolean("processorReady"))0x108F00 else 0xCC2020)
        font.draw(pose,Component.translatable("gui.eln.processor_inputs"),8f,64f,0x404040)
        font.draw(pose,playerInventoryTitle,inventoryLabelX.toFloat(),inventoryLabelY.toFloat(),0x404040)
    }
    override fun render(pose: PoseStack,mouseX: Int,mouseY: Int,partialTick: Float) { renderBackground(pose);super.render(pose,mouseX,mouseY,partialTick);renderTooltip(pose,mouseX,mouseY) }
}
