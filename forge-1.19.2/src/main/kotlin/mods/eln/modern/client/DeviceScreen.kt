package mods.eln.modern.client

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.modern.*
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Reuses the original container artwork and presents the controls relevant to this component. */
class DeviceScreen(menu: DeviceMenu,inventory: Inventory,title: Component) : AbstractContainerScreen<DeviceMenu>(menu,inventory,title) {
    private val texture=ResourceLocation("eln","sprites/gui/stdcontainer.png")
    private data class Field(val label: String,val y: Int,val box: EditBox)
    private val fields=mutableListOf<Field>()
    private data class LampRow(val power: EditBox,val signal: EditBox,var mode: Int,val button: Button)
    private val lampRows=mutableListOf<LampRow>()
    private val floodSliders=mutableListOf<FloodlightSlider>()
    private val fabricatorButtons=mutableListOf<Button>()
    private var value: EditBox?=null
    private var channel: EditBox?=null
    private var expression: EditBox?=null
    private var error: Component?=null
    private var readoutY=25
    private var transporterStart: Button?=null
    private var toggleButton: Button?=null
    private var modeButton: Button?=null
    private var sensorButton: Button?=null
    private var directionButton: Button?=null
    init { imageWidth=176;imageHeight=if(menu.part?.definition?.behavior==Behavior.TELEPORTER)190 else if(menu.fabricator)204 else if(menu.heatControls || menu.lighting)240 else if(menu.componentSlots>0)226 else if(menu.processing)166 else if(menu.part?.definition?.behavior==Behavior.LOGGER)176 else 142;inventoryLabelY=if(menu.fabricator)111 else if(menu.heatControls || menu.lighting)149 else if(menu.componentSlots>0)131 else 73 }
    override fun init() {
        super.init();transporterStart=null;fabricatorButtons.clear();fields.clear();lampRows.clear();floodSliders.clear();value=null;channel=null;expression=null;toggleButton=null;modeButton=null;sensorButton=null;directionButton=null
        val part=menu.part ?: return
        var y=22
        fun field(label: String,initial: String,max: Int): EditBox {
            val box=EditBox(font,leftPos+66,topPos+y,101,16,Component.translatable(label))
            box.setMaxLength(max);box.value=initial;addRenderableWidget(box);fields.add(Field(label,y,box));y+=22
            return box
        }
        val d=part.definition
        if(d.behavior!=Behavior.FLOODLIGHT)DeviceControls.valueKey(part)?.let { value=field(it,if(d.behavior==Behavior.SOLAR)(part.solarAngle*180/Math.PI-90).toString()else part.value.toString(),24) }
        if(DeviceControls.channel(d))channel=field("gui.eln.channel",part.channel,64)
        if(d.behavior!=Behavior.FLOODLIGHT)DeviceControls.expressionKey(part)?.let { expression=field(it,part.expression,256) }
        if(d.behavior==Behavior.TURRET) {
            sensorButton=addRenderableWidget(Button(leftPos+8,topPos+24,104,18,Component.translatable(if(part.enabled)"gui.eln.turret_spare" else "gui.eln.turret_attack")) {
                menu.part?.let { p -> send(p.value,!p.enabled,p.channel,p.expression) }
            })
            y=54;value=field("gui.eln.turret_power",part.value.toString(),24)
            addRenderableWidget(Button(leftPos+92,topPos+116,76,18,Component.translatable("gui.eln.apply")) { applySettings() })
            readoutY=80
        } else if(d.behavior==Behavior.TELEPORTER) {
            expression=field("gui.eln.transporter_name",part.expression,64)
            channel=field("gui.eln.transporter_destination",part.channel,64)
            value=field("gui.eln.transporter_power",part.value.toString(),24)
            transporterStart=addRenderableWidget(Button(leftPos+8,topPos+90,76,18,Component.translatable("gui.eln.transporter_start")) {
                val watts=value?.value?.toDoubleOrNull()
                if(watts==null || watts !in 2000.0..20000.0)error=Component.translatable("gui.eln.invalid_setting")
                else send(watts,true,channel!!.value,expression!!.value)
            })
            addRenderableWidget(Button(leftPos+92,topPos+90,76,18,Component.translatable("gui.eln.apply")) { applySettings() })
            readoutY=116
        } else if(d.behavior==Behavior.AUTO_MINER) {
            sensorButton=addRenderableWidget(Button(leftPos+8,topPos+24,120,18,Component.translatable("gui.eln.auto_miner_silk",Component.translatable(if(part.value>=.5)"gui.eln.enabled" else "gui.eln.disabled"))) {
                menu.part?.let { p -> send(if(p.value>=.5)0.0 else 1.0,p.enabled,p.channel,p.expression) }
            });readoutY=52
        } else if(menu.fabricator) {
            for((index,operation) in FabricatorOperations.all.withIndex()) {
                val button=object : Button(leftPos+58+(index/3)*22,topPos+22+(index%3)*22,20,20,Component.empty(),{
                    menu.part?.let { p -> send(index.toDouble(),p.enabled,p.channel,p.expression) }
                }) {
                    override fun renderButton(pose: PoseStack,mouseX: Int,mouseY: Int,partialTick: Float) {
                        super.renderButton(pose,mouseX,mouseY,partialTick)
                        net.minecraft.client.Minecraft.getInstance().itemRenderer.renderAndDecorateItem(operation.stack(),this.x+2,this.y+2)
                        if(menu.part?.value?.toInt()==index) {
                            val color=0xFFFFC94A.toInt()
                            fill(pose,this.x,this.y,this.x+width,this.y+1,color);fill(pose,this.x,this.y+height-1,this.x+width,this.y+height,color)
                            fill(pose,this.x,this.y,this.x+1,this.y+height,color);fill(pose,this.x+width-1,this.y,this.x+width,this.y+height,color)
                        }
                    }
                }
                fabricatorButtons.add(addRenderableWidget(button))
            }
            readoutY=90
        } else if(d.behavior in setOf(Behavior.SENSOR,Behavior.THERMAL_SENSOR)) {
            sensorButton=addRenderableWidget(Button(leftPos+58,topPos+46,110,18,sensorLabel(part)) {
                menu.part?.let { p -> send(((p.value.toInt()+1)%(if(d.behavior==Behavior.THERMAL_SENSOR)2 else 3)).toDouble(),p.enabled,p.channel,p.expression) }
            }).also { it.active=d.id !in setOf("voltage_probe","temperature_probe") }
            if(d.id=="electrical_probe")directionButton=addRenderableWidget(Button(leftPos+58,topPos+70,110,18,directionLabel(part)) {
                menu.part?.let { p -> send(p.value,p.enabled,SensorSettings.directions[(SensorSettings.directions.indexOf(p.channel).coerceAtLeast(0)+1)%3],p.expression) }
            })
            addRenderableWidget(Button(leftPos+92,topPos+116,76,18,Component.translatable("gui.eln.apply")) { applySettings() })
            readoutY=94
        } else if(part.originalBreaker) {
            toggleButton=addRenderableWidget(Button(leftPos+8,topPos+70,76,18,toggleLabel(part)) { toggle() })
            addRenderableWidget(Button(leftPos+92,topPos+70,76,18,Component.translatable("gui.eln.apply")) { applySettings() })
            readoutY=99
        } else if(d.behavior==Behavior.FUSE_HOLDER) {
            readoutY=52
        } else if(part.originalPassive) {
            readoutY=52
        } else if(d.wireless?.kind=="RX" && !part.legacyWireless) {
            modeButton=addRenderableWidget(Button(leftPos+8,topPos+y,76,18,lampMode(part.value.toInt())) {
                menu.part?.let { p -> send(((p.value.toInt()+1)%3).toDouble(),p.enabled,p.channel,p.expression) }
            })
            addRenderableWidget(Button(leftPos+92,topPos+y,76,18,Component.translatable("gui.eln.apply")) { applySettings() })
            readoutY=y+26
        } else if(d.behavior==Behavior.HUB) {
            readoutY=22
        } else if(d.behavior==Behavior.TIMER) {
            addRenderableWidget(Button(leftPos+8,topPos+44,76,18,Component.translatable("gui.eln.timer_set")) { send(menu.part!!.value,true,"set",menu.part!!.expression) })
            addRenderableWidget(Button(leftPos+92,topPos+44,76,18,Component.translatable("gui.eln.timer_reset")) { send(menu.part!!.value,true,"reset",menu.part!!.expression) })
            addRenderableWidget(Button(leftPos+92,topPos+118,76,18,Component.translatable("gui.eln.apply")) { applySettings() })
            readoutY=72
        } else if(d.behavior==Behavior.BATTERY_CHARGER) {
            toggleButton=addRenderableWidget(Button(leftPos+8,topPos+22,76,18,toggleLabel(part)) { toggle() })
            readoutY=48
        } else if(d.behavior==Behavior.LAMP_SUPPLY) {
            for((i,c) in mods.eln.modern.lighting.LampChannels.read(part).withIndex()) {
                val yy=34+i*22
                fun box(xx: Int,text: String,label: String)=EditBox(font,leftPos+xx,topPos+yy,54,16,Component.translatable(label)).also { it.setMaxLength(64);it.value=text;addRenderableWidget(it) }
                val powerBox=box(8,c.power,"gui.eln.power_channel");val signalBox=box(66,c.signal,"gui.eln.signal_channel")
                lateinit var row: LampRow
                val button=Button(leftPos+124,topPos+yy,44,16,lampMode(c.mode)) { row.mode=(row.mode+1)%3;row.button.message=lampMode(row.mode) }
                row=LampRow(powerBox,signalBox,c.mode,button);lampRows.add(row);addRenderableWidget(button)
            }
            addRenderableWidget(Button(leftPos+92,topPos+104,76,18,Component.translatable("gui.eln.apply")) {
                Network.channel.sendToServer(ConfigureLampChannels(menu.node.blockPos,menu.face,lampRows.map { mods.eln.modern.lighting.LampChannel(it.power.value,it.signal.value,it.mode) }))
            })
            readoutY=126
        } else if(d.behavior==Behavior.FLOODLIGHT) {
            val aim=mods.eln.modern.lighting.FloodlightSettings.parse(part.expression) ?: (0.0 to 0.0)
            for((i,key) in listOf("gui.eln.flood_pan","gui.eln.flood_tilt","gui.eln.flood_spread").withIndex()) {
                val slider=FloodlightSlider(leftPos+8,topPos+22+i*20,160,key,listOf(360.0,180.0,45.0)[i],listOf(part.value,aim.first,aim.second)[i])
                slider.active=d.id=="basic_floodlight";floodSliders.add(slider);addRenderableWidget(slider)
            }
            if(d.id=="basic_floodlight")addRenderableWidget(Button(leftPos+92,topPos+138,76,18,Component.translatable("gui.eln.apply")) { applySettings() })
            readoutY=104
        } else if(d.behavior in setOf(Behavior.LAMP,Behavior.EMERGENCY_LAMP)) {
            toggleButton=addRenderableWidget(Button(leftPos+92,topPos+82,76,18,toggleLabel(part)) { toggle() })
            addRenderableWidget(Button(leftPos+92,topPos+138,76,18,Component.translatable("gui.eln.apply")) { applySettings() })
            readoutY=104
        } else if(d.behavior==Behavior.CLUTCH) { readoutY=72 }
        else if(d.behavior in setOf(Behavior.SOLAR,Behavior.TRANSFORMER) && menu.componentSlots>0) {
            toggleButton=addRenderableWidget(Button(leftPos+92,topPos+48,76,18,toggleLabel(part)) { toggle() })
            if(d.behavior==Behavior.SOLAR)addRenderableWidget(Button(leftPos+92,topPos+116,76,18,Component.translatable("gui.eln.apply")) { applySettings() })
            readoutY=70
        } else if(d.behavior==Behavior.INCUBATOR) {
            readoutY=62
        } else if(menu.heatControls) {
            toggleButton=addRenderableWidget(Button(leftPos+8,topPos+66,76,18,toggleLabel(part)) { toggle() })
            modeButton=addRenderableWidget(Button(leftPos+88,topPos+66,80,18,modeLabel(part)) {
                menu.part?.let { p -> send(p.value,p.enabled,if(d.behavior==Behavior.FURNACE)if(p.channel=="manual")"automatic" else "manual" else if(p.channel=="external")"internal" else "external",p.expression) }
            })
            addRenderableWidget(Button(leftPos+92,topPos+106,76,18,Component.translatable("gui.eln.apply")) { applySettings() })
            readoutY=127
        } else if(menu.processing) {
            toggleButton=addRenderableWidget(Button(leftPos+118,topPos+19,49,16,toggleLabel(part)) { toggle() })
            readoutY=20
        } else {
            if(d.behavior==Behavior.FE_CONVERTER) {
                modeButton=addRenderableWidget(Button(leftPos+8,topPos+y,160,18,modeLabel(part)) {
                    menu.part?.let { p -> send(if(p.value<.5)1.0 else 0.0,p.enabled,p.channel,p.expression) }
                });y+=24
            }
            if(DeviceControls.toggle(d))toggleButton=addRenderableWidget(Button(leftPos+8,topPos+y,if(d.contact!=null || d.behavior==Behavior.ALARM)160 else 76,18,toggleLabel(part)) { toggle() })
            if(fields.isNotEmpty())addRenderableWidget(Button(leftPos+92,topPos+y,76,18,Component.translatable("gui.eln.apply")) { applySettings() })
            if(DeviceControls.toggle(d) || fields.isNotEmpty())y+=26
            readoutY=y
        }
    }
    private fun toggleLabel(part: PartState) = Component.translatable(if(part.originalBreaker)if(part.enabled)"gui.eln.contact_closed" else "gui.eln.contact_open" else if(part.definition.contact?.relay==true)if(part.enabled)"gui.eln.relay_nc" else "gui.eln.relay_no" else if(part.definition.contact!=null)if(part.enabled)"gui.eln.contact_closed" else "gui.eln.contact_open" else if(part.definition.behavior==Behavior.ALARM)if(part.enabled)"gui.eln.alarm_sound_on" else "gui.eln.alarm_sound_off" else if(part.definition.behavior in setOf(Behavior.LAMP,Behavior.EMERGENCY_LAMP))if(part.enabled)"gui.eln.lamp_wired" else "gui.eln.lamp_supply" else if(part.definition.behavior==Behavior.HEAT_FURNACE)if(part.enabled)"gui.eln.furnace_stopped" else "gui.eln.furnace_fuel" else if(part.tripped)"gui.eln.tripped" else if(part.enabled)"gui.eln.enabled" else "gui.eln.disabled")
    private fun lampMode(mode: Int)=Component.translatable(when(mode) { 0 -> "gui.eln.aggregate_max";1 -> "gui.eln.aggregate_min";else -> "gui.eln.aggregate_toggle" })
    private fun modeLabel(part: PartState)=if(part.definition.wireless?.kind=="RX")lampMode(part.value.toInt())else Component.translatable(if(part.definition.behavior==Behavior.FURNACE)if(part.channel=="manual")"gui.eln.furnace_manual" else "gui.eln.furnace_auto" else if(menu.heatControls)if(part.channel=="external")"gui.eln.furnace_external" else "gui.eln.furnace_internal" else if(part.value<.5)"gui.eln.export_power" else "gui.eln.import_power")
    private fun sensorLabel(part: PartState)=Component.translatable(if(part.definition.behavior==Behavior.THERMAL_SENSOR && part.value.toInt()==1)"gui.eln.sensor_temperature" else when(part.value.toInt()) { 0 -> "gui.eln.sensor_power";1 -> "gui.eln.sensor_current";else -> "gui.eln.sensor_voltage" })
    private fun directionLabel(part: PartState)=Component.translatable("gui.eln.sensor_"+(part.channel.takeIf { it in SensorSettings.directions } ?: "both"))
    private fun toggle() { menu.part?.let { send(it.value,!it.enabled,it.channel,it.expression) } }
    private fun send(v: Double,enabled: Boolean,c: String,e: String) {
        val part=menu.part ?: return
        Network.channel.sendToServer(ConfigureDevice(menu.node.blockPos,menu.face,v,part.resistance,enabled,c,e))
    }
    private fun applySettings() {
        val part=menu.part ?: return
        if(part.definition.behavior==Behavior.FLOODLIGHT && floodSliders.size==3) {
            send(floodSliders[0].degrees,part.enabled,part.channel,"${floodSliders[1].degrees},${floodSliders[2].degrees}");error=null;return
        }
        val v=value?.value?.toDoubleOrNull() ?: if(value==null)part.value else Double.NaN
        if(part.definition.id=="signal_trimmer" && (!v.isFinite() || v !in 0.0..5.0)) { error=Component.translatable("gui.eln.invalid_setting");return }
        val expr=expression?.value ?: part.expression
        if(part.definition.behavior==Behavior.TURRET && (!v.isFinite() || v !in 100.0..5000.0)) { error=Component.translatable("gui.eln.invalid_setting");return }
        if(part.definition.behavior==Behavior.TELEPORTER && (!v.isFinite() || v !in 2000.0..20000.0)) { error=Component.translatable("gui.eln.invalid_setting");return }
        if(part.originalBreaker && ProtectionSettings.window(expr)==null) { error=Component.translatable("gui.eln.invalid_setting");return }
        val triples=part.definition.id in setOf("pid_regulator","configurable_summing_unit")
        if(menu.heatControls && mods.eln.modern.thermal.HeatFurnaceRuntime.target(expr)==null) { error=Component.translatable("gui.eln.invalid_setting");return }
        if(!v.isFinite() || part.definition.behavior==Behavior.TIMER && !TimerSettings.duration(v) || part.definition.behavior in setOf(Behavior.SENSOR,Behavior.THERMAL_SENSOR,Behavior.AMBIENT_SENSOR) && SensorSettings.range(expr)==null || part.definition.id=="digital_display" && DisplaySettings.range(expr)==null || triples && (expr.split(',').size!=3 || expr.split(',').any { it.trim().toDoubleOrNull()?.isFinite()!=true })) {
            error=Component.translatable("gui.eln.invalid_setting");return
        }
        send(v,part.enabled,channel?.value ?: part.channel,expr);error=null
    }
    override fun renderBg(pose: PoseStack,partialTick: Float,mouseX: Int,mouseY: Int) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);RenderSystem.setShaderColor(1f,1f,1f,1f);RenderSystem.setShaderTexture(0,texture)
        if(menu.processing && !menu.heatControls)blit(pose,leftPos,topPos,0,0,176,166)
        else {
            blit(pose,leftPos,topPos,0,0,176,18)
            var y=18
            while(y<imageHeight-7) {
                val h=minOf(16,imageHeight-7-y)
                blit(pose,leftPos,topPos+y,0,32,176,h);y+=h
            }
            blit(pose,leftPos,topPos+imageHeight-7,0,159,176,7)
        }
        if(menu.processing) {
            for(slot in menu.slots.take(if(menu.stackMachine)6 else 2))blit(pose,leftPos+slot.x-1,topPos+slot.y-1,7,83,18,18)
            blit(pose,leftPos+83,topPos+40,176,31,22,16)
            val progress=((menu.part?.processFraction ?: 0.0)*22).roundToInt()
            if(progress>0)blit(pose,leftPos+83,topPos+40,176,14,progress,16)
        }
        if(menu.inventoryVisible)for(slot in menu.slots)blit(pose,leftPos+slot.x-1,topPos+slot.y-1,7,83,18,18)
    }
    private fun number(n: Double,places: Int=2)=String.format(Locale.ROOT,"%.${places}f",n)
    override fun renderLabels(pose: PoseStack,mouseX: Int,mouseY: Int) {
        font.draw(pose,font.plainSubstrByWidth(title.string,160),8f,7f,0x303030)
        for(field in fields)font.draw(pose,Component.translatable(field.label),8f,(field.y+4).toFloat(),0x404040)
        val part=menu.part ?: return
        if(menu.heatControls)font.draw(pose,Component.translatable(if(part.definition.behavior==Behavior.FUEL_HEAT_FURNACE)"gui.eln.fuel_heater_slots" else if(part.definition.behavior==Behavior.FURNACE)"gui.eln.electric_furnace_slots" else "gui.eln.furnace_slots"),8f,96f,0x404040)
        else if(part.definition.behavior==Behavior.LAMP_SUPPLY) {
            font.draw(pose,Component.translatable("gui.eln.power_channel"),8f,23f,0x404040)
            font.draw(pose,Component.translatable("gui.eln.signal_channel"),66f,23f,0x404040)
            font.draw(pose,Component.translatable("gui.eln.aggregate"),124f,23f,0x404040)
            font.draw(pose,Component.translatable("gui.eln.sensor_cable"),8f,94f,0x404040)
            for(i in 0..2)fill(pose,170,38+i*22,173,45+i*22,if(part.gridState.getInt("lampStates") and (1 shl i)!=0)0xFF33BB44.toInt()else 0xFF555555.toInt())
        } else if(part.definition.behavior==Behavior.LAMP)font.draw(pose,Component.translatable("gui.eln.lamp_slots"),18f,71f,0x404040)
        else if(part.definition.behavior==Behavior.CLUTCH)font.draw(pose,Component.translatable("gui.eln.clutch_slots"),8f,38f,0x404040)
        else if(part.originalBreaker)font.draw(pose,Component.translatable("gui.eln.breaker_cable"),8f,52f,0x404040)
        else if(part.definition.behavior==Behavior.FUSE_HOLDER)font.draw(pose,Component.translatable("gui.eln.fuse_hardware"),8f,28f,0x404040)
        else if(menu.componentSlots>0 && !part.originalPassive && part.definition.behavior !in setOf(Behavior.TURRET,Behavior.AUTO_MINER,Behavior.FABRICATOR,Behavior.INCUBATOR,Behavior.HUB,Behavior.FLOODLIGHT,Behavior.BATTERY_CHARGER,Behavior.ENVIRONMENT_SENSOR,Behavior.WATCH))font.draw(pose,Component.translatable(if(part.definition.behavior==Behavior.SOLAR)"gui.eln.solar_tracker" else if(part.definition.behavior==Behavior.TRANSFORMER)"gui.eln.transformer_slots" else "gui.eln.sensor_cable"),18f,38f,0x404040)
        var y=readoutY
        fun row(key: String,vararg args: Any) { font.draw(pose,Component.translatable(key,*args),8f,y.toFloat(),0x303030);y+=12 }
        if(menu.processing && !menu.heatControls) {
            row("gui.eln.power",number(if(part.definition.mechanical)part.powerFactor*part.definition.power else abs((part.voltage-part.voltageB)*part.current),0))
            y=61;row(if(part.active)"gui.eln.processing" else "gui.eln.idle")
            font.draw(pose,playerInventoryTitle,8f,73f,0x404040)
        } else if(part.definition.behavior==Behavior.TURRET) {
            row("gui.eln.electrical_readout",number(part.voltage,1),number(part.current,2))
            row("gui.eln.turret_buffer",number(part.progress,0))
            row("gui.eln.turret_shots",part.gridState.getInt("turretShots"))
        } else if(part.definition.behavior==Behavior.TELEPORTER) {
            row("gui.eln.electrical_readout",number(part.voltage,1),number(part.current,2))
            row("gui.eln.transporter_work",number(part.progress,0),number(part.gridState.getDouble("transporterTargetJ"),0))
            row("gui.eln.transporter_status",Component.translatable("gui.eln.transporter_phase_"+Transporters.phase(part)))
            val event=part.gridState.getString("transporterEvent")
            if(event.isNotEmpty())row("gui.eln.transporter_message",Component.translatable("gui.eln.transporter_event_"+event))
        } else if(part.definition.behavior==Behavior.AUTO_MINER) {
            row("gui.eln.electrical_readout",number(part.voltage,1),number(part.current,2))
            row("gui.eln.auto_miner_depth",AutoMiners.depth(part))
            row("gui.eln.auto_miner_state",Component.translatable("gui.eln.auto_miner_job_"+AutoMiners.job(part)))
            row("gui.eln.auto_miner_work",number(part.progress,0),number(part.gridState.getDouble("autoMinerTargetJ"),0))
        } else if(menu.fabricator) {
            row("gui.eln.electrical_readout",number(part.voltage,1),number(part.current,2))
            row("gui.eln.fabricator_progress",number(part.progress,0))
            font.draw(pose,playerInventoryTitle,8f,111f,0x404040)
        } else if(part.definition.behavior==Behavior.BATTERY_CHARGER) {
            fun label(key: String,x: Int,y: Int,vararg args: Any)=font.draw(pose,Component.translatable(key,*args),x.toFloat(),y.toFloat(),0x404040)
            label("gui.eln.charger_batteries",8,41);label("gui.eln.charger_booster",54,87)
            label("gui.eln.electrical_readout",86,51,number(part.voltage,1),number(part.current,2))
            label("gui.eln.power",86,63,number(part.thermalState.getDouble("chargerWatts"),0))
            label("gui.eln.charger_buffer",86,75,number(part.gridState.getDouble("chargerEnergy"),0))
            val n=PartInventory.get(menu.node,part).getStackInSlot(4).count.coerceIn(0,5)
            label("gui.eln.charger_speed",8,99,number(Math.pow(1.25,n.toDouble())*100,0))
            label("gui.eln.charger_efficiency",8,111,number(Math.pow(.9,n.toDouble())*100,0))
            font.draw(pose,playerInventoryTitle,8f,131f,0x404040)
        } else if(part.originalBreaker || part.definition.behavior==Behavior.FUSE_HOLDER) {
            row("gui.eln.electrical_readout",number(if(part.originalBreaker)part.voltage else part.voltage-part.voltageB,2),number(part.current,3))
            row(if(part.active)"gui.eln.contact_closed" else "gui.eln.contact_open")
            if(!part.originalBreaker)row("gui.eln.protection_heat",number(part.gridState.getDouble("protectionTemperature"),1))
            font.draw(pose,playerInventoryTitle,8f,131f,0x404040)
        } else if(part.definition.behavior==Behavior.OVERHEAD_LINES) {
            row("gui.eln.electrical_readout",number(part.voltage,1),number(part.current,3))
            row("gui.eln.rail_carts",part.gridState.getInt("railCarts"))
            row("gui.eln.energy",number(part.energy/3600,2))
        } else if(part.definition.powerAntenna!=null) {
            row("gui.eln.electrical_readout",number(part.voltage,1),number(part.current,3))
            row("gui.eln.power",number(if(part.definition.powerAntenna.transmitter)part.voltage*part.current else part.gridState.getDouble("beamOutputWatts"),1))
            row("gui.eln.beam_efficiency",number(part.powerFactor*100,1))
            if(part.definition.powerAntenna.transmitter)row("gui.eln.beam_connection",Component.translatable(if(part.gridState.getBoolean("beamConnected"))"gui.eln.yes" else "gui.eln.no"))
        } else if(part.definition.wireless!=null) {
            if(part.definition.wireless.kind in setOf("TX","RX"))row("gui.eln.electrical_readout",number(part.voltage,2),number(part.current,3))
            row("gui.eln.wireless_range",mods.eln.config.ForgePortConfig.wirelessRange.get())
            if(part.definition.wireless.kind=="RX")row("gui.eln.wireless_count",part.gridState.getInt("wirelessTransmitters"))
            if(part.definition.wireless.kind=="SOURCE")row("gui.eln.wireless_state",Component.translatable(if(part.enabled)"gui.eln.enabled" else "gui.eln.disabled"))
        } else if(part.definition.behavior==Behavior.SIGNAL_BUS) {
            val values=part.gridState.getLongArray("busVoltages")
            for(i in 0..15) {
                val x=8+(i%4)*41;val by=y+(i/4)*21
                fill(pose,x,by,x+37,by+18,0xFF202020.toInt())
                font.draw(pose,number(if(i<values.size)Double.fromBits(values[i])else 0.0,2),x+2f,by+5f,PaintedWiring.tint(i,.7,.3))
            }
        } else if(part.definition.behavior==Behavior.NIXIE) {
            row("gui.eln.nixie_digit",part.displayReading)
            row("gui.eln.nixie_dots",part.displayBits)
            row("gui.eln.nixie_blank",Component.translatable(if(part.displayBlank)"gui.eln.yes" else "gui.eln.no"))
        } else if(part.originalPassive) {
            val spec=part.definition.passive!!
            val key=when(spec.kind) { "RESISTOR" -> "gui.eln.passive_resistance";"CAPACITOR" -> "gui.eln.passive_capacitance";else -> "gui.eln.passive_inductance" }
            font.draw(pose,Component.translatable("gui.eln.passive_hardware"),8f,28f,0x404040)
            row(key,String.format(Locale.ROOT,"%.4g",part.value))
            row("gui.eln.electrical_readout",number(part.voltage-part.voltageB,2),number(part.current,3))
            if(spec.kind=="RESISTOR")row("message.eln.temperature",number(part.temperature,1))
            else row("gui.eln.sensor_stored",number(part.gridState.getDouble("passiveStoredJ"),2))
            if(spec.kind=="CAPACITOR")row("gui.eln.passive_voltage",number(PassiveSettings.capacitorVoltage(PartInventory.get(menu.node,part).getStackInSlot(1).count),0))
            if(spec.controlled)row("gui.eln.alarm_input",number(part.pinVoltages[0],2))
            font.draw(pose,playerInventoryTitle,8f,131f,0x404040)
        } else if(part.definition.contact!=null) {
            row(if(part.active)"gui.eln.contact_closed" else "gui.eln.contact_open")
            row("gui.eln.electrical_readout",number(part.voltage-part.voltageB,3),number(part.current,3))
            if(part.definition.contact.relay)row("gui.eln.alarm_input",number(part.pinVoltages[0],2))
            if(part.definition.contact.currentThermal)row("message.eln.temperature",number(part.temperature,1))
        } else if(part.definition.behavior==Behavior.HUB) {
            HubCanvas.draw(pose,part)
            for((index,xy) in listOf(158 to 54,101 to 116,14 to 54,101 to 25).withIndex())font.draw(pose,(index+1).toString(),xy.first.toFloat(),xy.second.toFloat(),0x404040)
            font.draw(pose,playerInventoryTitle,8f,131f,0x404040)
        } else if(part.definition.behavior==Behavior.TIMER) {
            row("gui.eln.timer_remaining",number(part.thermalState.getDouble("timerLeft"),2))
            row("gui.eln.timer_input",Component.translatable(if(part.thermalState.getBoolean("timerHigh"))"gui.eln.yes" else "gui.eln.no"))
            row("gui.eln.timer_output",Component.translatable(if(part.active)"gui.eln.yes" else "gui.eln.no"))
        } else if(part.definition.behavior==Behavior.ALARM) {
            row("gui.eln.alarm_engaged",Component.translatable(if(part.active)"gui.eln.yes" else "gui.eln.no"))
            row("gui.eln.alarm_input",number(part.voltage,2))
        } else if(part.definition.watch!=null) {
            val date=WatchTime.date(part,menu.node.level?.dayTime ?: 0L);val minutes=WatchTime.minutes(date)
            row(if(part.active)"gui.eln.clock_running" else "gui.eln.clock_stopped")
            row("gui.eln.clock_time",String.format(Locale.ROOT,"%02d:%02d",minutes/60,minutes%60))
            val battery=PartInventory.get(menu.node,part).getStackInSlot(0);val cap=ElectricalItems.spec(battery)?.capacity ?: 1.0
            font.draw(pose,Component.translatable("gui.eln.sensor_battery"),8f,52f,0x404040)
            font.draw(pose,Component.translatable("gui.eln.sensor_battery_charge",number(ElectricalItems.energy(battery)/cap*100,1)),34f,69f,0x404040)
            font.draw(pose,Component.translatable("gui.eln.sensor_stored",number(ElectricalItems.energy(battery),1)),34f,82f,0x404040)
            y=102;row("gui.eln.sensor_consumption",number(if(part.active)part.definition.power else 0.0,3))
            font.draw(pose,playerInventoryTitle,8f,131f,0x404040)
        } else if(part.definition.environment!=null) {
            val spec=part.definition.environment
            fun yes(value: Boolean)=Component.translatable(if(value)"gui.eln.yes" else "gui.eln.no")
            if(spec.kind!="FIRE_BUZZER")row("gui.eln.sensor_output",number(part.voltage,3))
            when(spec.kind) {
                "HUMIDITY"->{ row("gui.eln.humidity",number(part.thermalState.getDouble("envMeasured"),1));row("gui.eln.ambient_temperature",number(part.temperature,1)) }
                "WIND"->row("gui.eln.wind",number(part.thermalState.getDouble("envMeasured"),1))
                "ENTITY"->row("gui.eln.motion_state",yes(part.active))
                "FIRE","FIRE_BUZZER"->row("gui.eln.fire_state",yes(part.thermalState.getBoolean("envFire")))
            }
            if(spec.kind=="FIRE_BUZZER") {
                row("gui.eln.sensor_powered",yes(part.thermalState.getBoolean("envPowered")))
                val battery=PartInventory.get(menu.node,part).getStackInSlot(0);val cap=ElectricalItems.spec(battery)?.capacity ?: 1.0
                font.draw(pose,Component.translatable("gui.eln.sensor_battery"),8f,52f,0x404040)
                font.draw(pose,Component.translatable("gui.eln.sensor_battery_charge",number(ElectricalItems.energy(battery)/cap*100,1)),34f,69f,0x404040)
                font.draw(pose,Component.translatable("gui.eln.sensor_stored",number(ElectricalItems.energy(battery),1)),34f,82f,0x404040)
                y=102;row("gui.eln.sensor_consumption",number(if(part.thermalState.getBoolean("envPowered"))part.definition.power else 0.0,3))
            } else if(spec.kind=="ENTITY")font.draw(pose,Component.translatable("gui.eln.entity_filter"),8f,52f,0x404040)
            if(menu.inventoryVisible)font.draw(pose,playerInventoryTitle,8f,131f,0x404040)
        } else {
            if(menu.heatControls)row("gui.eln.furnace_state",number(part.temperature,1),number(if(part.definition.behavior==Behavior.FUEL_HEAT_FURNACE)part.thermalState.getDouble("heatWatts") else if(part.definition.behavior==Behavior.FURNACE)part.thermalState.getDouble("heatingWatts") else part.thermalPower,0))
            else if(part.definition.behavior in setOf(Behavior.INCUBATOR,Behavior.WIND_TURBINE,Behavior.WATER_TURBINE,Behavior.FUEL_GENERATOR))row("gui.eln.electrical_readout",number(part.voltage-part.voltageB,1),number(part.current,2))
            else if(part.definition.behavior !in setOf(Behavior.TRANSFORMER,Behavior.LAMP_SUPPLY))row("gui.eln.readout",number(part.voltage-part.voltageB,1),number(part.current,2),number(part.temperature,0))
            if(part.definition.mechanical)row("gui.eln.shaft",number(part.shaftSpeed,1),number(part.shaftEnergy,0))
            if(part.definition.fluidMachine && part.definition.behavior!=Behavior.HEAT_EXCHANGER && !menu.heatControls)row("gui.eln.fluid",menu.node.tank.fluidAmount)
            when(part.definition.behavior) {
                Behavior.INCUBATOR -> {
                    row("gui.eln.incubator_remaining",number(part.gridState.getDouble("incubatorRemainingJ"),0))
                    row("gui.eln.power",number(part.voltage*part.current,1))
                }
                Behavior.FUEL_GENERATOR -> row("gui.eln.power",number(part.thermalState.getDouble("fuelGeneratorWatts"),0))
                Behavior.WIND_TURBINE,Behavior.WATER_TURBINE -> {
                    row("gui.eln.power",number(part.thermalState.getDouble("renewableWatts"),1))
                    if(part.definition.behavior==Behavior.WIND_TURBINE) {
                        row("gui.eln.wind",number(part.gridState.getDouble("windSpeed"),1))
                        row("gui.eln.wind_exposure",number(part.gridState.getDouble("windExposure")*100,0))
                    }
                }
                Behavior.LAMP_SUPPLY -> {
                    row("gui.eln.board_power",number(part.voltage,1),number(part.voltage*part.current,1))
                    row("gui.eln.board_range",32+PartInventory.get(menu.node,part).getStackInSlot(0).count)
                    font.draw(pose,playerInventoryTitle,8f,149f,0x404040)
                }
                Behavior.FLOODLIGHT -> {
                    row("gui.eln.floodlight_power",part.signal,number(part.thermalState.getDouble("lampWatts"),1))
                    row("gui.eln.floodlight_angles",number(part.gridState.getDouble("swivel"),0),number(part.gridState.getDouble("head"),0),number(part.gridState.getDouble("beamWidth"),0))
                    font.draw(pose,playerInventoryTitle,8f,149f,0x404040)
                }
                Behavior.EMERGENCY_LAMP -> {
                    row("gui.eln.emergency_charge",number(part.charge*100,1),number(part.charge*part.definition.capacity,0))
                    row(if(part.active)"gui.eln.emergency_on" else "gui.eln.emergency_off")
                    font.draw(pose,playerInventoryTitle,8f,149f,0x404040)
                }
                Behavior.LAMP -> {
                    row("gui.eln.lamp_light",part.signal,number(part.thermalState.getDouble("lampWatts"),1))
                    val bulb=PartInventory.get(menu.node,part).getStackInSlot(0)
                    (bulb.item as? MaterialItem)?.definition?.bulb?.let { row("gui.eln.lamp_life",number(if(bulb.tag?.contains("life")==true)bulb.tag!!.getDouble("life")else it.hours,2)) }
                    font.draw(pose,playerInventoryTitle,8f,149f,0x404040)
                }
                Behavior.SHAFT_MOTOR,Behavior.SHAFT_GENERATOR -> {
                    val watts=part.thermalState.getDouble("shaftElectricalWatts")
                    row(if(watts>=0)"gui.eln.shaft_generating" else "gui.eln.shaft_motoring",number(abs(watts),0))
                    val efficiency=if(part.definition.behavior==Behavior.SHAFT_MOTOR)if(watts>=0)1/9.9 else .99 else if(watts>=0).95 else .7125
                    row("gui.eln.mechanical_efficiency",number(efficiency*100,1))
                }
                Behavior.GAS_TURBINE,Behavior.STEAM_TURBINE -> {
                    row("gui.eln.fluid_intake",number(part.thermalState.getDouble("turbineRate"),2))
                    row("gui.eln.mechanical_efficiency",number(part.thermalState.getDouble("turbineEfficiency")*100,1))
                    row("gui.eln.power",number(part.thermalState.getDouble("turbineWatts"),0))
                }
                Behavior.RADIAL_MOTOR -> {
                    row("gui.eln.fluid_intake",number(part.thermalState.getDouble("radialRate"),2))
                    row("gui.eln.mechanical_efficiency",number(part.thermalState.getDouble("radialEfficiency")*100,1))
                    row("gui.eln.power",number(part.thermalState.getDouble("radialWatts"),0))
                }
                Behavior.CLUTCH -> {
                    row("gui.eln.clutch_speeds",number(part.gridState.getDouble("clutchSpeed1"),1),number(part.gridState.getDouble("clutchSpeed3"),1))
                    val wear=PartInventory.get(menu.node,part).getStackInSlot(0).tag?.getDouble("wear") ?: 0.0
                    row("gui.eln.clutch_control",number(part.voltage,2),number(wear*100,3))
                    row(if(part.gridState.getBoolean("clutchSlipping"))"gui.eln.clutch_slipping" else "gui.eln.clutch_locked")
                    font.draw(pose,playerInventoryTitle,8f,131f,0x404040)
                }
                Behavior.TRANSFORMER -> {
                    row("gui.eln.primary",number(part.voltage,1),number(part.current,2))
                    row("gui.eln.secondary",number(part.voltageB,1),number(part.gridState.getDouble("outputCurrent"),2))
                    row("gui.eln.transformer_ratio",number(part.value,3))
                }
                Behavior.SENSOR,Behavior.THERMAL_SENSOR -> {
                    row("gui.eln.sensor_output",number(part.pinVoltages[0],3))
                    font.draw(pose,playerInventoryTitle,8f,131f,0x404040)
                }
                Behavior.THERMAL_CABLE,Behavior.PASSIVE_COOLER,Behavior.ACTIVE_COOLER -> row("gui.eln.power",number(part.thermalPower,1))
                Behavior.HEAT_TURBINE -> {
                    row("gui.eln.turbine_warm",number(part.temperature,1));row("gui.eln.turbine_cold",number(part.thermalState.getDouble("coldTemperature"),1))
                    row("gui.eln.turbine_efficiency",number(part.thermalState.getDouble("efficiency")*100,1))
                }
                Behavior.HEAT_EXCHANGER -> {
                    row("gui.eln.exchanger_input",menu.node.exchanger.input.fluidAmount);row("gui.eln.exchanger_output",menu.node.exchanger.output.fluidAmount)
                    row("gui.eln.exchanger_flow",part.thermalState.getInt("inputMb"),part.thermalState.getInt("outputMb"))
                    row("gui.eln.exchanger_control",number(part.thermalState.getDouble("control")*100,0))
                }
                Behavior.FURNACE -> { row("gui.eln.smelt_progress",number(part.processFraction*100,0));font.draw(pose,playerInventoryTitle,8f,149f,0x404040) }
                Behavior.HEAT_FURNACE,Behavior.FUEL_HEAT_FURNACE -> {
                    if(part.definition.behavior==Behavior.FUEL_HEAT_FURNACE)row("gui.eln.liquid_fuel",menu.node.tank.fluidAmount,number(part.fuelEnergy,0))
                    else row("gui.eln.furnace_energy",number(part.fuelEnergy+part.thermalState.getDouble("fuelBuffer"),0))
                    font.draw(pose,playerInventoryTitle,8f,149f,0x404040)
                }
                Behavior.BATTERY -> {
                    row("gui.eln.battery",(part.charge*100).toInt(),(part.life*100).toInt())
                    fill(pose,8,y+1,168,y+9,0xFF484848.toInt())
                    fill(pose,9,y+2,9+(158*part.charge.coerceIn(0.0,1.0)).toInt(),y+8,0xFF68AA58.toInt())
                }
                Behavior.SOLAR -> {
                    row("gui.eln.solar_output",number(kotlin.math.abs((part.voltage-part.voltageB)*part.current),1))
                    row("gui.eln.solar_angle",number(part.solarAngle*180/Math.PI-90,1))
                    if(menu.componentSlots>0)row(if(PartInventory.get(menu.node,part).getStackInSlot(0).isEmpty)"gui.eln.solar_manual" else "gui.eln.solar_tracking")
                }
                Behavior.LOGGER -> {
                    val history=part.history
                    fill(pose,8,y+2,168,imageHeight-12,0xFF152019.toInt())
                    if(history.size>1) {
                        val min=minOf(0.0,history.min());val max=maxOf(5.0,history.max());val h=imageHeight-18-y
                        var last=0
                        for(i in history.indices) {
                            val x=9+i*157/(history.size-1)
                            val sample=(y+5+h*(1-(history[i]-min)/(max-min))).roundToInt()
                            if(i>0)fill(pose,x-1,minOf(last,sample),x+1,maxOf(last,sample)+1,0xFF73E78B.toInt())
                            last=sample
                        }
                    }
                }
                Behavior.FE_CONVERTER -> row("gui.eln.fe_stored",menu.node.feStored)
                else -> if(part.definition.id=="digital_display")row("gui.eln.display_reading",part.displayReading) else row("gui.eln.energy",number(part.energy/3600))
            }
        }
        error?.let { font.draw(pose,it,8f,(imageHeight-18).toFloat(),0xA02020) }
    }
    override fun render(pose: PoseStack,mouseX: Int,mouseY: Int,partialTick: Float) {
        // Read server-confirmed state, including automatic breaker trips.
        val part=menu.part
        if(part?.definition?.behavior==Behavior.TELEPORTER) {
            val idle=Transporters.phase(part)==Transporters.IDLE
            transporterStart?.active=idle;fields.forEach { it.box.setEditable(idle) }
        }
        if(part!=null) { toggleButton?.message=toggleLabel(part);modeButton?.message=modeLabel(part);sensorButton?.message=if(part.definition.behavior==Behavior.TURRET)Component.translatable(if(part.enabled)"gui.eln.turret_spare" else "gui.eln.turret_attack")else if(part.definition.behavior==Behavior.AUTO_MINER)Component.translatable("gui.eln.auto_miner_silk",Component.translatable(if(part.value>=.5)"gui.eln.enabled" else "gui.eln.disabled"))else sensorLabel(part);directionButton?.message=directionLabel(part) }
        renderBackground(pose);super.render(pose,mouseX,mouseY,partialTick);renderTooltip(pose,mouseX,mouseY)
        for((index,button) in fabricatorButtons.withIndex())if(button.isMouseOver(mouseX.toDouble(),mouseY.toDouble())) {
            val operation=FabricatorOperations.all[index]
            renderTooltip(pose,Component.translatable("gui.eln.fabricator_operation",operation.stack().hoverName,operation.count,(operation.chance*100).toInt()),mouseX,mouseY)
        }
        if(part?.definition?.behavior==Behavior.HUB)HubCanvas.indexAt((mouseX-leftPos).toDouble(),(mouseY-topPos).toDouble())?.let { renderTooltip(pose,HubCanvas.label(part,it),mouseX,mouseY) }
    }
    override fun mouseClicked(x: Double,y: Double,button: Int): Boolean {
        menu.part?.takeIf { it.definition.behavior==Behavior.HUB }?.let { p ->
            if(button==0)HubCanvas.indexAt(x-leftPos,y-topPos)?.let { send(p.value,p.enabled,it.toString(),p.expression);return true }
        }
        return super.mouseClicked(x,y,button)
    }
    override fun containerTick() {
        super.containerTick();fields.forEach { it.box.tick() }
        lampRows.forEach { it.power.tick();it.signal.tick() }
        menu.part?.takeIf { it.definition.solar?.rotating==true }?.let { p ->
            val tracked=!PartInventory.get(menu.node,p).getStackInSlot(0).isEmpty
            value?.active=!tracked
            if(tracked)value?.value=number(p.solarAngle*180/Math.PI-90,1)
        }
        menu.part?.takeIf { it.definition.behavior==Behavior.FURNACE }?.let { toggleButton?.active=it.channel=="manual" }
        menu.part?.takeIf { it.definition.id=="motorized_floodlight" }?.let { p ->
            for((i,key) in listOf("swivel","head","beamWidth").withIndex())floodSliders.getOrNull(i)?.setDegrees(p.gridState.getDouble(key))
        }
    }
    override fun keyPressed(key: Int,scan: Int,mods: Int): Boolean {
        if(key==256)return super.keyPressed(key,scan,mods)
        fields.firstOrNull { it.box.isFocused }?.let { it.box.keyPressed(key,scan,mods);return true }
        return super.keyPressed(key,scan,mods)
    }
}

class FloodlightSlider(x: Int,y: Int,width: Int,private val label: String,private val maximum: Double,initial: Double): net.minecraft.client.gui.components.AbstractSliderButton(x,y,width,16,Component.empty(),initial/maximum) {
    val degrees get()=value*maximum
    init { updateMessage() }
    fun setDegrees(degrees: Double) { value=(degrees/maximum).coerceIn(0.0,1.0);updateMessage() }
    override fun updateMessage() { message=Component.translatable(label,String.format(Locale.ROOT,"%.0f",degrees)) }
    override fun applyValue() { updateMessage() }
}
