package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.modern.*
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

/** Paged original TX/RX mapping editor, including connected state and removal. */
class ModbusScreen(menu: DeviceMenu,inventory: Inventory,title: Component): AbstractContainerScreen<DeviceMenu>(menu,inventory,title) {
    private var page=0
    private var signature=""
    private lateinit var station: EditBox
    private lateinit var name: EditBox
    init { imageWidth=300;imageHeight=230 }
    private fun send(command: Int,uuid: Int=0,number: Int=0,text: String="")=Network.channel.sendToServer(ConfigureModbus(menu.node.blockPos,menu.face,command,uuid,number,text))
    override fun init() {
        super.init();val p=menu.part ?: return
        fun field(x: Int,y: Int,w: Int,text: String,max: Int=64)=EditBox(font,leftPos+x,topPos+y,w,16,Component.empty()).also { it.setMaxLength(max);it.value=text;addRenderableWidget(it) }
        fun button(x: Int,y: Int,w: Int,key: String,action: ()->Unit)=addRenderableWidget(Button(leftPos+x,topPos+y,w,18,Component.translatable(key)) { action() })
        station=field(42,22,40,p.gridState.getInt("modbusStation").toString(),4)
        name=field(125,22,108,p.gridState.getString("modbusName"))
        button(239,21,53,"gui.eln.apply") { val id=station.value.toIntOrNull() ?: return@button;send(1,number=id);send(2,text=name.value) }
        button(8,65,90,"gui.eln.modbus_add_tx") { send(3,text="newTX") };button(104,65,90,"gui.eln.modbus_add_rx") { send(4,text="newRX") }
        val entries=ModbusDevices.entries(p)
        page=page.coerceIn(0,((entries.size-1)/4).coerceAtLeast(0))
        for((row,entry) in entries.drop(page*4).take(4).withIndex()) {
            val y=99+row*25;val uuid=entry.getInt("uuid")
            val channel=field(37,y,117,entry.getString("channel"));val offset=field(161,y,47,entry.getInt("offset").toString(),5)
            button(214,y-1,52,"gui.eln.apply") { val id=offset.value.toIntOrNull() ?: return@button;send(5,uuid,id,channel.value) }
            button(273,y-1,19,"gui.eln.modbus_delete") { send(6,uuid) }
        }
        button(8,204,35,"gui.eln.previous") { page=(page-1).coerceAtLeast(0);rebuildWidgets() }
        button(257,204,35,"gui.eln.next") { page=(page+1).coerceAtMost(((entries.size-1)/4).coerceAtLeast(0));rebuildWidgets() }
        signature=entries.joinToString { "${it.getInt("uuid")}:${it.getString("channel")}:${it.getInt("offset")}" }
    }
    override fun containerTick() {
        super.containerTick();val p=menu.part ?: return
        val current=ModbusDevices.entries(p).joinToString { "${it.getInt("uuid")}:${it.getString("channel")}:${it.getInt("offset")}" }
        if(current!=signature)rebuildWidgets()
    }
    override fun renderBg(pose: PoseStack,partialTick: Float,mouseX: Int,mouseY: Int) {
        fill(pose,leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF383838.toInt());fill(pose,leftPos+2,topPos+2,leftPos+imageWidth-2,topPos+imageHeight-2,0xFFC6C6C6.toInt())
    }
    override fun renderLabels(pose: PoseStack,mouseX: Int,mouseY: Int) {
        font.draw(pose,title,8f,6f,0x404040)
        font.draw(pose,Component.translatable("gui.eln.modbus_unit"),8f,26f,0x404040);font.draw(pose,Component.translatable("gui.eln.mqtt_name"),91f,26f,0x404040)
        val p=menu.part ?: return
        font.draw(pose,Component.translatable(if(p.gridState.getBoolean("modbusRegistered"))"gui.eln.modbus_registered" else "gui.eln.modbus_unassigned"),8f,46f,if(p.gridState.getBoolean("modbusRegistered"))0x008800 else 0xAA2222)
        font.draw(pose,Component.translatable("gui.eln.modbus_channel"),37f,88f,0x404040);font.draw(pose,Component.translatable("gui.eln.modbus_address"),161f,88f,0x404040)
        for((row,entry) in ModbusDevices.entries(p).drop(page*4).take(4).withIndex())font.draw(pose,if(entry.getBoolean("tx"))"TX" else "RX",8f,(103+row*25).toFloat(),if(entry.getBoolean("tx") || entry.getBoolean("connected"))0x008800 else 0xAA2222)
        font.draw(pose,Component.translatable("gui.eln.modbus_page",page+1),110f,209f,0x404040)
    }
    override fun render(pose: PoseStack,mouseX: Int,mouseY: Int,partialTick: Float) { renderBackground(pose);super.render(pose,mouseX,mouseY,partialTick);renderTooltip(pose,mouseX,mouseY) }
}
