package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.modern.*
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import java.util.Locale

class EnergyMeterScreen(menu: DeviceMenu,inventory: Inventory,title: Component): AbstractContainerScreen<DeviceMenu>(menu,inventory,title) {
    private lateinit var password: EditBox
    private lateinit var counter: EditBox
    private lateinit var passwordButton: Button
    private lateinit var switch: Button
    private lateinit var mode: Button
    private lateinit var energyUnit: Button
    private lateinit var timeUnit: Button
    private val protectedButtons=mutableListOf<Button>()
    private var authenticated=false
    private var proof=""
    init { imageWidth=176;imageHeight=240 }
    override fun init() {
        super.init();protectedButtons.clear()
        val p=menu.part ?: return
        authenticated=EnergyMeterControls.authenticate(p,proof)
        password=addRenderableWidget(EditBox(font,leftPos+8,topPos+23,70,16,Component.translatable("gui.eln.meter_password")).apply { setMaxLength(64) })
        counter=addRenderableWidget(EditBox(font,leftPos+8,topPos+71,70,16,Component.translatable("gui.eln.meter_counter")).apply { setMaxLength(24);value="0" })
        fun button(x: Int,y: Int,w: Int,key: String,guarded: Boolean=true,action: ()->Unit): Button {
            val b=addRenderableWidget(Button(leftPos+x,topPos+y,w,18,Component.translatable(key)) { action() })
            if(guarded)protectedButtons.add(b);return b
        }
        passwordButton=button(84,21,84,"gui.eln.meter_unlock",false) {
            if(!authenticated) { proof=password.value;authenticated=EnergyMeterControls.authenticate(p,proof);send(0) }
            else { send(7,text=password.value);proof=password.value;password.value="" }
        }
        switch=button(8,45,70,"gui.eln.meter_switch_off") { send(1) }
        mode=button(84,45,84,"gui.eln.meter_counter") { send(2) }
        button(84,69,84,"gui.eln.meter_set_counter") { counter.value.toDoubleOrNull()?.takeIf(Double::isFinite)?.let { send(3,it*1000) } }
        energyUnit=button(8,93,34,"gui.eln.meter_counter") { send(5) }
        timeUnit=button(44,93,34,"gui.eln.meter_counter") { send(6) }
        button(84,93,84,"gui.eln.meter_reset_time") { send(4) }
        refresh()
    }
    private fun send(command: Int,value: Double=0.0,text: String="")=Network.channel.sendToServer(ConfigureEnergyMeter(menu.node.blockPos,menu.face,command,value,text,proof))
    private fun refresh() {
        val p=menu.part ?: return
        if(!::passwordButton.isInitialized)return
        passwordButton.message=Component.translatable(if(authenticated)"gui.eln.meter_change_password" else "gui.eln.meter_unlock")
        switch.message=Component.translatable(if(p.enabled)"gui.eln.meter_switch_on" else "gui.eln.meter_switch_off")
        mode.message=Component.translatable(if(EnergyMeters.prepaid(p))"gui.eln.meter_prepay" else "gui.eln.meter_counter")
        energyUnit.message=Component.literal(listOf("J","kJ","MJ","GJ")[p.gridState.getInt("meterEnergyUnit").coerceIn(0,3)])
        timeUnit.message=Component.literal(if(p.gridState.getInt("meterTimeUnit")==0)"H" else "D")
        protectedButtons.forEach { it.active=authenticated }
        energyUnit.active=authenticated && p.definition.id=="advanced_energy_meter";timeUnit.active=energyUnit.active;counter.setEditable(authenticated)
    }
    override fun containerTick() { super.containerTick();refresh() }
    override fun renderBg(pose: PoseStack,partialTick: Float,mouseX: Int,mouseY: Int) {
        fill(pose,leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF383838.toInt());fill(pose,leftPos+2,topPos+2,leftPos+imageWidth-2,topPos+imageHeight-2,0xFFC6C6C6.toInt())
        for(slot in menu.slots) { val x=leftPos+slot.x;val y=topPos+slot.y;fill(pose,x-1,y-1,x+17,y+17,0xFF373737.toInt());fill(pose,x,y,x+17,y+17,0xFFFFFFFF.toInt());fill(pose,x,y,x+16,y+16,0xFF8B8B8B.toInt()) }
    }
    override fun renderLabels(pose: PoseStack,mouseX: Int,mouseY: Int) {
        font.draw(pose,title,8f,6f,0x404040)
        val p=menu.part ?: return
        font.draw(pose,Component.translatable("gui.eln.meter_cable"),136f,109f,0x404040)
        fun n(v: Double)=String.format(Locale.ROOT,"%.2f",v)
        font.draw(pose,"${n(p.voltage)} / ${n(p.voltageB)} V",8f,118f,0x404040)
        font.draw(pose,"${n(p.current)} A · ${n(p.gridState.getDouble("meterPowerW"))} W",8f,129f,0x404040)
        val unit=p.gridState.getInt("meterEnergyUnit").coerceIn(0,3)
        font.draw(pose,"${n(EnergyMeters.counter(p)/Math.pow(1000.0,unit.toDouble()))} ${listOf("J","kJ","MJ","GJ")[unit]}",8f,140f,0x404040)
        val days=p.gridState.getInt("meterTimeUnit")==1;val time=EnergyMeters.time(p)/(if(days)86400 else 3600)
        val text=n(time)+if(days)" D" else " H";font.draw(pose,text,(168-font.width(text)).toFloat(),140f,0x404040)
    }
    override fun render(pose: PoseStack,mouseX: Int,mouseY: Int,partialTick: Float) { renderBackground(pose);super.render(pose,mouseX,mouseY,partialTick);renderTooltip(pose,mouseX,mouseY) }
}
