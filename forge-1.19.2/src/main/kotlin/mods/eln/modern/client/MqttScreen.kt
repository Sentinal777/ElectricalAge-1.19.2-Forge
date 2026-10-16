package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.modern.*
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import java.util.Locale
import kotlin.math.pow

class MqttScreen(menu: DeviceMenu,inventory: Inventory,title: Component): AbstractContainerScreen<DeviceMenu>(menu,inventory,title) {
    private val meter=menu.part?.let(MqttDevices::isMeter)==true
    private lateinit var name: EditBox
    private lateinit var server: EditBox
    private lateinit var identifier: EditBox
    private var toggle: Button?=null
    private var energyUnit: Button?=null
    private var timeUnit: Button?=null
    private val modes=mutableListOf<Button>()
    init { imageWidth=176;imageHeight=if(meter)240 else 198;inventoryLabelY=149 }
    override fun init() {
        super.init();modes.clear()
        val p=menu.part ?: return
        fun field(y: Int,text: String)=addRenderableWidget(EditBox(font,leftPos+60,topPos+y,108,16,Component.empty()).apply { setMaxLength(64);value=text })
        name=field(20,p.gridState.getString("mqttName"));server=field(42,p.gridState.getString("mqttServer"));identifier=field(64,p.gridState.getString("mqttId")).apply { setEditable(false) }
        fun button(x: Int,y: Int,w: Int,text: Component,action: ()->Unit)=addRenderableWidget(Button(leftPos+x,topPos+y,w,18,text) { action() })
        button(92,86,76,Component.translatable("gui.eln.apply")) { send(1,text=name.value);send(2,text=server.value) }
        if(meter) {
            toggle=button(8,86,76,Component.empty()) { send(4) }
            button(8,108,76,Component.translatable("gui.eln.mqtt_reset")) { send(3) }
            energyUnit=button(88,108,26,Component.empty()) { send(5) };timeUnit=button(118,108,26,Component.empty()) { send(6) }
        }else for(i in 0..3)modes.add(button(8,110+i*20,100,Component.empty()) { val part=menu.part ?: return@button;send(3,i or (MqttDevices.mode(part,i).next().ordinal shl 8)) })
        updateLabels()
    }
    private fun send(command: Int,param: Int=0,text: String="")=Network.channel.sendToServer(ConfigureMqtt(menu.node.blockPos,menu.face,command,param,text))
    private fun updateLabels() {
        val p=menu.part ?: return
        toggle?.message=Component.translatable(if(p.enabled)"gui.eln.enabled" else "gui.eln.disabled")
        energyUnit?.message=Component.literal(listOf("J","kJ","MJ","GJ")[p.gridState.getInt("meterEnergyUnit").coerceIn(0,3)])
        timeUnit?.message=Component.literal(if(p.gridState.getInt("meterTimeUnit")==0)"H" else "D")
        for((i,button) in modes.withIndex())button.message=Component.translatable("gui.eln.mqtt_mode",('A'.code+i).toChar().toString(),Component.translatable("gui.eln.mqtt_mode_${MqttDevices.mode(p,i).ordinal}"))
        if(::server.isInitialized)server.setTextColor(if(p.gridState.getBoolean("mqttMatched"))0x00AA00 else 0xDD2222)
        if(::identifier.isInitialized)identifier.value=p.gridState.getString("mqttId")
    }
    override fun containerTick() { super.containerTick();updateLabels() }
    override fun renderBg(pose: PoseStack,partialTick: Float,mouseX: Int,mouseY: Int) {
        fill(pose,leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF383838.toInt());fill(pose,leftPos+2,topPos+2,leftPos+imageWidth-2,topPos+imageHeight-2,0xFFC6C6C6.toInt())
        for(slot in menu.slots) {
            val x=leftPos+slot.x;val y=topPos+slot.y
            fill(pose,x-1,y-1,x+17,y+17,0xFF373737.toInt());fill(pose,x,y,x+17,y+17,0xFFFFFFFF.toInt());fill(pose,x,y,x+16,y+16,0xFF8B8B8B.toInt())
        }
    }
    override fun renderLabels(pose: PoseStack,mouseX: Int,mouseY: Int) {
        font.draw(pose,title,8f,6f,0x404040)
        for((key,y) in listOf("gui.eln.mqtt_name" to 24,"gui.eln.mqtt_server" to 46,"gui.eln.mqtt_id" to 68))font.draw(pose,Component.translatable(key),8f,y.toFloat(),0x404040)
        val p=menu.part ?: return
        fun format(v: Double)=String.format(Locale.ROOT,"%.2f",v)
        if(meter) {
            font.draw(pose,"${format(p.voltage)} V / ${format(p.current)} A",8f,132f,0x404040)
            font.draw(pose,"${format(EnergyMeters.counter(p)/10.0.pow(p.gridState.getInt("meterEnergyUnit").coerceIn(0,3)*3))} ${energyUnit?.message?.string} / ${format(EnergyMeters.time(p)/72)} s",8f,143f,0x404040)
        }else for(i in 0..3)font.draw(pose,"${format(p.pinVoltages[i])} V",114f,(115+i*20).toFloat(),0x404040)
    }
    override fun render(pose: PoseStack,mouseX: Int,mouseY: Int,partialTick: Float) { renderBackground(pose);super.render(pose,mouseX,mouseY,partialTick);renderTooltip(pose,mouseX,mouseY) }
}
