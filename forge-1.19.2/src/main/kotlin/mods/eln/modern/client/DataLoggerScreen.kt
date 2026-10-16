package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.modern.*
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import kotlin.math.roundToInt

class DataLoggerScreen(menu: DeviceMenu,inventory: Inventory,title: Component): AbstractContainerScreen<DeviceMenu>(menu,inventory,title) {
    private var configuring=false
    private var unit=4
    private var zero=true
    private lateinit var period: EditBox
    private lateinit var low: EditBox
    private lateinit var high: EditBox
    private val configWidgets=mutableListOf<net.minecraft.client.gui.components.AbstractWidget>()
    private val displayButtons=mutableListOf<Button>()
    private lateinit var configuration: Button
    private lateinit var pause: Button
    private lateinit var print: Button
    init { imageWidth=176;imageHeight=286;inventoryLabelY=193 }
    override fun init() {
        super.init();configWidgets.clear();displayButtons.clear()
        val p=menu.part ?: return
        unit=p.gridState.getInt("loggerUnit");zero=p.gridState.getBoolean("loggerZero")
        fun button(x: Int,y: Int,w: Int,text: Component,action: ()->Unit)=addRenderableWidget(Button(leftPos+x,topPos+y,w,18,text) { action() })
        configuration=button(8,19,160,Component.translatable("gui.eln.logger_config")) { configuring=!configuring;visibility() }
        for(i in DataLoggers.units.indices)configWidgets.add(button(8+(i%3)*54,42+(i/3)*20,52,Component.translatable("gui.eln.logger_unit_$i")) { unit=i;send(0) })
        fun field(y: Int,text: String)=addRenderableWidget(EditBox(font,leftPos+86,topPos+y,80,16,Component.empty()).apply { setMaxLength(24);value=text }).also { configWidgets.add(it) }
        low=field(106,p.gridState.getDouble("loggerMin").toString());high=field(126,p.gridState.getDouble("loggerMax").toString());period=field(146,DataLoggers.period(p).toString())
        configWidgets.add(button(8,166,74,Component.translatable("gui.eln.logger_zero")) { zero=!zero;send(0) })
        configWidgets.add(button(88,166,80,Component.translatable("gui.eln.apply")) { send(0) })
        pause=button(8,40,78,Component.empty()) { send(2) };displayButtons.add(pause)
        displayButtons.add(button(90,40,78,Component.translatable("gui.eln.logger_reset")) { send(1) })
        print=button(64,181,48,Component.translatable("gui.eln.logger_print")) { send(3) };displayButtons.add(print)
        visibility()
    }
    private fun visibility() {
        configWidgets.forEach { it.visible=configuring };displayButtons.forEach { it.visible=!configuring }
        if(::configuration.isInitialized)configuration.message=Component.translatable(if(configuring)"gui.eln.logger_back" else "gui.eln.logger_config")
    }
    private fun send(command: Int) {
        val a=period.value.toDoubleOrNull() ?: return;val b=low.value.toDoubleOrNull() ?: return;val c=high.value.toDoubleOrNull() ?: return
        Network.channel.sendToServer(ConfigureLogger(menu.node.blockPos,menu.face,command,a,b,c,unit,zero))
    }
    override fun containerTick() {
        super.containerTick();val p=menu.part ?: return
        if(::pause.isInitialized)pause.message=Component.translatable(if(p.gridState.getBoolean("loggerPaused"))"gui.eln.logger_paused" else "gui.eln.logger_running")
        if(::print.isInitialized)print.active=menu.slots[0].hasItem() && !menu.slots[1].hasItem()
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
        if(configuring) {
            for((key,y) in listOf("gui.eln.logger_min" to 110,"gui.eln.logger_max" to 130,"gui.eln.logger_period" to 150))font.draw(pose,Component.translatable(key),8f,y.toFloat(),0x404040)
            return
        }
        val samples=DataLoggers.samples(p)
        val x0=10;val y0=70;val w=148;val height=92
        fill(pose,x0-1,y0-1,x0+w+1,y0+height+1,0xFF101810.toInt())
        val color=when(p.definition.id) { "data_logger" -> 0xFFFF8000.toInt();"modern_data_logger" -> 0xFF00FF00.toInt();else -> 0xFF4080FF.toInt() }
        val min=p.gridState.getDouble("loggerMin");val max=p.gridState.getDouble("loggerMax")
        if(p.gridState.getBoolean("loggerZero") && min*max<0) {
            val y=y0+(height*(1-(0-min)/(max-min))).roundToInt();fill(pose,x0,y,x0+w,y+1,0xFF777777.toInt())
        }
        if(samples.size>1)for(i in 1 until samples.size) {
            val a=samples[samples.size-i].toInt()+128;val b=samples[samples.size-1-i].toInt()+128
            val xa=x0+(i-1)*w/(samples.size-1);val xb=x0+i*w/(samples.size-1)
            val ya=y0+height-(a*height/255);val yb=y0+height-(b*height/255)
            val steps=maxOf(kotlin.math.abs(xb-xa),kotlin.math.abs(yb-ya),1)
            for(step in 0..steps) { val x=xa+(xb-xa)*step/steps;val y=ya+(yb-ya)*step/steps;fill(pose,x,y,x+1,y+1,color) }
        }
        val units=DataLoggers.units.getOrElse(p.gridState.getInt("loggerUnit")) { "" }
        font.draw(pose,"${format(max)} $units",10f,60f,0x404040);font.draw(pose,"${format(min)} $units",10f,164f,0x404040)
        val time="${format(samples.size*DataLoggers.period(p))} s";font.draw(pose,time,(158-font.width(time)).toFloat(),164f,0x404040)
        val mx=mouseX-leftPos;val my=mouseY-topPos
        if(mx in x0..x0+w && my in y0..y0+height) {
            val value=max-(my-y0)*(max-min)/height;val text="${format(value)} $units"
            font.draw(pose,text,(88-font.width(text)/2).toFloat(),173f,0x404040)
        }
    }
    private fun format(v: Double)=String.format(java.util.Locale.ROOT,"%.2f",v)
    override fun render(pose: PoseStack,mouseX: Int,mouseY: Int,partialTick: Float) { renderBackground(pose);super.render(pose,mouseX,mouseY,partialTick);renderTooltip(pose,mouseX,mouseY) }
}
