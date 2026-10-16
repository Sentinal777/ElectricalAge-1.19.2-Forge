package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Matrix3f
import com.mojang.math.Matrix4f
import com.mojang.math.Vector3f
import mods.eln.modern.*
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import java.util.WeakHashMap
import kotlin.math.*

class NodeRenderer() : BlockEntityRenderer<FaceNodeEntity> {
    constructor(context: BlockEntityRendererProvider.Context): this()
    private data class Motion(val id: String,var time: Double,var factor: Float,var angle: Float=0f,var angleB: Float=0f)
    private val animations=WeakHashMap<FaceNodeEntity,Array<Motion?>>()

    /** Matrix constructor takes rows; the columns below are the original OBJ's world-space axes. */
    private fun orient(pose: PoseStack,part: PartState,rawMesh: Boolean) {
        if(rawMesh && part.definition.powerAntenna!=null) {
            val edges=FaceBasis.edges(part);val f=part.face;val u=edges[0];val v=edges[1]
            val m=FloatArray(16);m[15]=1f
            for(i in 0..2) {
                fun component(d: net.minecraft.core.Direction)=when(i) { 0->d.stepX;1->d.stepY;else->d.stepZ }.toFloat()
                m[i*4]=component(f);m[i*4+1]=-component(v);m[i*4+2]=component(u);m[i*4+3]=.5f
            }
            val matrix=Matrix4f(m);pose.mulPoseMatrix(matrix);pose.last().normal().mul(Matrix3f(matrix))
            pose.mulPose(Vector3f.XP.rotationDegrees(if(part.definition.powerAntenna.transmitter)270f else 90f));return
        }
        val edges=if(rawMesh && (part.definition.alarm!=null || part.definition.behavior==Behavior.POWER_SOCKET) && part.face.axis!=net.minecraft.core.Direction.Axis.Y)FaceBasis.edges(part.face,0,part.orientationVersion) else FaceBasis.edges(part)
        val u=edges[0];val v=edges[1];val f=part.face
        fun coords(d: net.minecraft.core.Direction) = floatArrayOf(d.stepX.toFloat(),d.stepY.toFloat(),d.stepZ.toFloat())
        val uf=coords(u);val vf=coords(v);val ff=coords(f)
        val m=FloatArray(16);m[15]=1f
        for(i in 0..2) {
            m[i*4]=if(rawMesh)if(part.definition.machine)vf[i] else -ff[i] else uf[i]
            m[i*4+1]=if(rawMesh && !part.definition.machine)-vf[i] else -ff[i]
            m[i*4+2]=if(rawMesh)-uf[i] else vf[i]
            m[i*4+3]=.5f+if(rawMesh)if(part.definition.machine)0f else .5f*ff[i] else .5f*(ff[i]-uf[i]-vf[i])
        }
        val matrix=Matrix4f(m)
        pose.mulPoseMatrix(matrix);pose.last().normal().mul(Matrix3f(matrix))
        // Shaft models use local Z for the axle and local X for the electrical front.
        if(rawMesh && part.definition.mechanical)pose.mulPose(Vector3f.YP.rotationDegrees(90f))
        if(rawMesh) {
            pose.mulPose(Vector3f.YP.rotationDegrees(part.definition.visual.modelYaw.toFloat()))
            part.definition.visual.modelOffset?.let { pose.translate(it[0],it[1],it[2]) }
        }
        if(rawMesh)pose.translate(0.0,part.definition.visual.modelLift,0.0)
        if(rawMesh)part.definition.visual.scale.toFloat().let { pose.scale(it,it,it) }
    }
    override fun render(node: FaceNodeEntity,partialTick: Float,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        ClientAudio.observe(node)
        NodeCoverVisuals.render(node,pose,buffers,light,overlay)
        val time=(node.level?.gameTime ?: 0)+partialTick.toDouble()
        val motions=animations.getOrPut(node) { arrayOfNulls(6) }
        for(part in node.parts.filterNotNull()) {
            val d=part.definition;val kind=d.behavior;val spec=d.visual
            val target=when {
                d.id=="large_rheostat" -> part.gridState.getDouble("passiveControl").toFloat()
                kind==Behavior.LAMP_SUPPLY -> if(net.minecraft.client.Minecraft.getInstance().player?.distanceToSqr(node.blockPos.x+.5,node.blockPos.y+.5,node.blockPos.z+.5)?.let { it<4 }==true)1f else 0f
                d.grid?.kind=="SWITCH" -> part.gridState.getFloat("position")
                kind==Behavior.METER -> (part.voltage/5).coerceIn(0.0,1.0).toFloat()
                d.id=="signal_trimmer" -> (part.value/5).coerceIn(0.0,1.0).toFloat()
                kind==Behavior.RELAY -> if(part.active)1f else 0f
                kind==Behavior.BUTTON -> if(part.timer>0)1f else 0f
                else -> if(part.enabled && !part.tripped)1f else 0f
            }
            val face=part.face.get3DDataValue()
            var motion=motions[face]
            if(motion==null || motion.id!=d.id) { motion=Motion(d.id,time,target);motions[face]=motion }
            val dt=((time-motion.time)/20).coerceIn(0.0,.25);motion.time=time
            if(d.id=="large_rheostat")motion.factor+=(target-motion.factor).coerceIn(-dt.toFloat()*.3f,dt.toFloat()*.3f)
            else motion.factor+=(target-motion.factor)*(1-exp(-dt*12)).toFloat()
            fun shaftDegrees(speed: Double)=(ln(speed.coerceAtLeast(0.0)+1)/ln(1.2)*180/PI).toFloat()
            motion.angle=(motion.angle+dt.toFloat()*(if(d.mechanical)shaftDegrees(part.shaftSpeed)else 360*part.powerFactor.toFloat()))%360f
            if(kind==Behavior.CLUTCH) {
                motion.angle=(motion.angle-dt.toFloat()*shaftDegrees(part.shaftSpeed)+dt.toFloat()*shaftDegrees(part.gridState.getDouble("clutchSpeed1")))%360f
                motion.angleB=(motion.angleB+dt.toFloat()*shaftDegrees(part.gridState.getDouble("clutchSpeed3")))%360f
            }
            if(kind==Behavior.COMPUTER_PROBE) { ComputerProbeVisuals.render(node,pose,buffers,light,overlay);continue }
            pose.pushPose();orient(pose,part,false)
            val cableTexture=ResourceLocation("eln",spec.cableTexture)
            if(kind in setOf(Behavior.CABLE,Behavior.THERMAL_CABLE,Behavior.SIGNAL_BUS)) {
                val b=PartGeometry.localBody(part);val cap=part.connectedMask !in setOf(5,10)
                if(cap)LegacyMeshes.box(b.minX.toFloat(),b.minY.toFloat(),b.minZ.toFloat(),b.maxX.toFloat(),b.maxY.toFloat(),b.maxZ.toFloat(),cableTexture,pose,buffers,light,overlay,true,cap,
                    if(kind==Behavior.THERMAL_CABLE)0xFFFFFF else PaintedWiring.tint(part))
            }
            if(kind==Behavior.ENERGY_METER || d.powerAntenna!=null || kind==Behavior.HUB || d.contact!=null || part.originalBreaker) {
                for(i in 0..3)if(part.connectedMask and (1 shl i)!=0)(if(kind==Behavior.ENERGY_METER)EnergyMeters.cable(part) else if(d.powerAntenna!=null)PowerAntennas.cable(part,i) else if(part.originalBreaker)ProtectionSettings.cable(node,part) else if(kind==Behavior.HUB)HubSettings.cable(node,part,i) else PartDefinition.byId[if(d.contact!!.relay && i==0)"signal_cable" else d.contact.cable])?.let { cable ->
                    val w=cable.visual.cableWidth.toFloat()/2;val h=cable.visual.cableHeight.toFloat()
                    val b=when(i) {
                        0 -> floatArrayOf(.5f,.001f,.5f-w,1f,h,.5f+w)
                        1 -> floatArrayOf(.5f-w,.001f,.5f,.5f+w,h,1f)
                        2 -> floatArrayOf(0f,.001f,.5f-w,.5f,h,.5f+w)
                        else -> floatArrayOf(.5f-w,.001f,0f,.5f+w,h,.5f)
                    }
                    LegacyMeshes.box(b[0],b[1],b[2],b[3],b[4],b[5],ResourceLocation("eln",cable.visual.cableTexture),pose,buffers,light,overlay,true,false,if(kind==Behavior.ENERGY_METER)if(i==3)0xE60000 else 0x0000E6 else PaintedWiring.connectionTint(part,i))
                }
            } else for((i,b) in (0..3).filter { part.connectedMask and (1 shl it)!=0 }.zip(PartGeometry.localConnections(part)))LegacyMeshes.box(b.minX.toFloat(),b.minY.toFloat(),b.minZ.toFloat(),
                b.maxX.toFloat(),b.maxY.toFloat(),b.maxZ.toFloat(),cableTexture,pose,buffers,light,overlay,true,false,if(kind in setOf(Behavior.PROCESSOR,Behavior.MQTT_CONTROLLER))listOf(0xE60000,0x00C000,0x0000E6,0x202020)[i] else if(kind==Behavior.THERMAL_CABLE)0xFFFFFF else PaintedWiring.connectionTint(part,i))
            if(spec.cubeTexture.isNotEmpty())LegacyMeshes.box(0f,0f,0f,1f,1f,1f,ResourceLocation("eln",spec.cubeTexture),pose,buffers,light,overlay)
            pose.popPose()
            val model=LegacyMeshes.model(d.model) ?: continue
            pose.pushPose();orient(pose,part,true)
            if(SimpleSignalVisuals.supported(part)) { SimpleSignalVisuals.render(node,part,model,time,pose,buffers,light,overlay);pose.popPose();continue }
            if(part.originalBreaker || kind==Behavior.FUSE_HOLDER) { ProtectionVisuals.render(node,part,model,partialTick,pose,buffers,light,overlay);pose.popPose();continue }
            if(kind==Behavior.POWER_SOCKET) {
                LegacyMeshes.draw(model.groups["SocketBase"].orEmpty(),pose,buffers,light,overlay)
                LegacyMeshes.draw(model.groups[if(d.id=="type_j_socket")"Socket50V" else "Socket200V"].orEmpty(),pose,buffers,light,overlay,PaintedWiring.tint(PaintedWiring.color(part),.7,.3))
                pose.popPose();continue
            }
            if(kind==Behavior.OVERHEAD_LINES) {
                val world=node.level
                if(world==null || listOf(net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.WEST,net.minecraft.core.Direction.UP,net.minecraft.core.Direction.DOWN,net.minecraft.core.Direction.SOUTH).any { direction ->
                    val at=node.blockPos.relative(direction);world.hasChunkAt(at) && world.getBlockState(at).isSolidRender(world,at)
                })LegacyMeshes.draw(model.groups["OverheadGantry"].orEmpty(),pose,buffers,light,overlay)
                pose.popPose();continue
            }
            if(kind==Behavior.INVENTORY_SCANNER) {
                LegacyMeshes.draw(model.groups["main"].orEmpty(),pose,buffers,light,overlay)
                LegacyMeshes.draw(model.groups["LED_${part.gridState.getInt("scannerMode").coerceIn(0,1)}"].orEmpty(),pose,buffers,15728880,overlay)
                pose.popPose();continue
            }
            if(kind in setOf(Behavior.PROCESSOR,Behavior.MQTT_CONTROLLER) || d.id in setOf("analog_vumeter","led_vumeter","multicolor_led_vumeter")) { SignalDeviceVisuals.render(node,part,model,time,dt,pose,buffers,light,overlay);pose.popPose();continue }
            if(kind==Behavior.TUTORIAL_SIGN) { TutorialSignVisuals.render(node,part,model,time,pose,buffers,light,overlay);pose.popPose();continue }
            if(kind==Behavior.MODBUS) { ModbusVisuals.render(node,part,model,time,pose,buffers,light,overlay);pose.popPose();continue }
            if(kind==Behavior.ENERGY_METER) { EnergyMeterVisuals.render(node,part,model,pose,buffers,light,overlay);pose.popPose();continue }
            if(kind==Behavior.LOGGER) { DataLoggerVisuals.render(part,model,pose,buffers,light,overlay,node.level!=null);pose.popPose();continue }
            if(kind==Behavior.NIXIE) {
                fun draw(group: String,color: Int=0xFFFFFF,glow: Boolean=false,u: Float=0f,texture: ResourceLocation?=null,glass: Boolean=false,alpha: Int=255)=LegacyMeshes.draw(model.groups[group] ?: emptyList(),pose,buffers,if(glow)15728880 else light,overlay,color,u,0f,texture,glass,alpha)
                draw("base")
                val atlas=ResourceLocation("eln","model/nixietube/digit_atlas.png")
                draw("display",0xFF6633,true,(if(part.displayBlank)10 else part.displayReading.coerceIn(0,9))/16f,atlas)
                if(!part.displayBlank && part.displayBits in 1..3)draw("display",0xFF6633,true,(11+part.displayBits)/16f,atlas)
                draw("tube",glass=true,alpha=128);pose.popPose();continue
            }
            if(d.contact!=null) { ContactVisuals.render(node,part,model,pose,buffers,light,overlay);pose.popPose();continue }
            if(kind==Behavior.HUB) {
                LegacyMeshes.draw(model.groups["main"] ?: emptyList(),pose,buffers,light,overlay)
                for(i in 0..5)LegacyMeshes.draw(model.groups["con$i"] ?: emptyList(),pose,buffers,light,overlay,if(HubSettings.connected(part,i))0x282828 else 0x969696)
                pose.popPose();continue
            }
            if(kind==Behavior.WATCH) { WatchVisuals.render(node,part,model,pose,buffers,light,overlay);pose.popPose();continue }
            if(kind in setOf(Behavior.TIMER,Behavior.ALARM)) { ControlVisuals.render(node,part,model,partialTick,pose,buffers,light,overlay);pose.popPose();continue }
            if(kind==Behavior.ENVIRONMENT_SENSOR) { EnvironmentVisuals.render(node,part,model,partialTick,pose,buffers,light,overlay);pose.popPose();continue }
            if(kind==Behavior.SHAFT_HUB) { ShaftVisuals.hub(part,model,motion.angle,pose,buffers,light,overlay);pose.popPose();continue }
            if(kind in setOf(Behavior.WIND_TURBINE,Behavior.WATER_TURBINE)) { RenewableVisuals.render(node,part,model,pose,buffers,light,overlay);pose.popPose();continue }
            if(kind==Behavior.RESIN_COLLECTOR) {
                model.groups["main"]?.let { LegacyMeshes.draw(it,pose,buffers,light,overlay) }
                val empty=1-part.thermalState.getDouble("resinStock").coerceIn(0.0,1.0)
                pose.translate(0.0,0.0,empty*model.property("fill","emptyT"))
                val scale=(1-empty*(1-model.property("fill","emptyS",1f))).toFloat();pose.scale(scale,scale,1f)
                model.groups["fill"]?.let { LegacyMeshes.draw(it,pose,buffers,light,overlay) }
                pose.popPose();continue
            }
            if(kind==Behavior.TURRET) { TurretVisuals.render(node,part,model,partialTick,pose,buffers,light,overlay);pose.popPose();continue }
            if(kind==Behavior.TELEPORTER) { TransporterVisuals.render(node,part,model,partialTick,pose,buffers,light,overlay);pose.popPose();continue }
            if(kind==Behavior.AUTO_MINER) { AutoMinerVisuals.render(node,part,model,partialTick,pose,buffers,light,overlay);pose.popPose();continue }
            if(kind==Behavior.FABRICATOR) { FabricatorVisuals.render(node,part,model,pose,buffers,light,overlay);pose.popPose();continue }
            if(kind==Behavior.INCUBATOR) { IncubatorVisuals.render(node,part,model,partialTick,pose,buffers,light,overlay);pose.popPose();continue }
            if(kind==Behavior.FESTIVE) { FestiveVisuals.render(node,part,model,pose,buffers,light,overlay);pose.popPose();continue }
            if(kind==Behavior.FLOODLIGHT) { FloodlightVisuals.render(node,part,model,pose,buffers,light,overlay);pose.popPose();continue }
            if(kind==Behavior.EMERGENCY_LAMP) { EmergencyLampVisuals.render(part,model,pose,buffers,light,overlay);pose.popPose();continue }
            if(kind==Behavior.LAMP) { LampVisuals.render(node,part,model,partialTick,pose,buffers,light,overlay);pose.popPose();continue }
            if(kind==Behavior.TRANSFORMER) {
                TransformerVisuals.render(node,part,model,pose,buffers,light,overlay);pose.popPose();continue
            }
            for((group,faces) in model.groups) {
                if(d.modelGroups.isNotEmpty() && group !in d.modelGroups)continue
                if(kind==Behavior.FUEL_HEAT_FURNACE && group.startsWith("Burner") && group!="Burner"+('A'.code+part.thermalState.getInt("burnerType")).toChar())continue
                pose.pushPose()
                if(d.id=="rheostat" && group=="ResistorWiper")pose.translate(0.0,0.0,(part.gridState.getDouble("passiveControl")-.5)*.238*2)
                if(d.grid!=null && group!="foot")pose.mulPose(Vector3f.YP.rotationDegrees(part.gridAngle.toFloat()))
                var color=0xFFFFFF;var groupLight=light;var uOffset=0f;var vOffset=0f
                if(d.id=="large_rheostat") {
                    val temperature=part.thermalState.getDouble("delta").toFloat()+273f
                    val tint=(mods.eln.misc.BlackBodyTemperature(temperature)*(mods.eln.misc.BlackBodyPower(temperature)/4000)+mods.eln.misc.BlackBodyColor(1f,1f,1f)).normalize()
                    fun channel(value: Float)=(value.coerceIn(0f,1f)*255).toInt()
                    color=(channel(tint.red) shl 16) or (channel(tint.green) shl 8) or channel(tint.blue)
                    if(group=="wiper")pose.mulPose(Vector3f.YP.rotationDegrees((1-motion.factor)*300))
                }
                if(kind==Behavior.FUEL_GENERATOR && group=="switch" && part.enabled)groupLight=15728880
                var texture: ResourceLocation?=null;var translucent=false
                fun rotate(angle: Float,axis: Vector3f) {
                    val pivot=if(d.mechanical && (group.startsWith("Shaft") || group in setOf("Flywheel","Fan","Rotor","Turbine") || kind==Behavior.ROLLING_SHAFT && group in setOf("rot1","rot2")))ShaftVisuals.pivot(model,if(kind in setOf(Behavior.CLUTCH,Behavior.ROLLING_SHAFT))group else if(kind==Behavior.FLYWHEEL)"Flywheel" else "Shaft")else null
                    val x=pivot?.x?.toDouble() ?: model.property(group,"originX").toDouble();val y=pivot?.y?.toDouble() ?: model.property(group,"originY").toDouble();val z=pivot?.z?.toDouble() ?: model.property(group,"originZ").toDouble()
                    pose.translate(x,y,z);pose.mulPose(axis.rotationDegrees(angle));pose.translate(-x,-y,-z)
                }
                when {
                    kind==Behavior.LAMP_SUPPLY && group=="window" -> {
                        rotate((1-motion.factor)*model.property(group,"windowOpenAngle"),Vector3f.ZP)
                        texture=ResourceLocation("eln","model/distributionboard/glass.png");translucent=true
                        val player=net.minecraft.client.Minecraft.getInstance().player
                        uOffset=(player?.yRot ?: 0f)/360+(node.blockPos.x+node.blockPos.z)/64f+motion.factor*.5f
                        vOffset=(player?.xRot ?: 0f)/180*.65f
                    }
                    d.grid?.kind=="SWITCH" && group in GridWireRenderer.switchRotors -> {
                        val (origin,cw)=GridWireRenderer.switchRotors.getValue(group)
                        pose.translate(-origin.x,-origin.y,-origin.z)
                        pose.mulPose(Vector3f.YP.rotationDegrees((if(cw)-1 else 1)*motion.factor*90))
                        pose.translate(origin.x,origin.y,origin.z)
                    }
                    kind==Behavior.CLUTCH && group in setOf("ShaftXN","ShaftXP") -> rotate(if(group=="ShaftXN")motion.angle else motion.angleB,Vector3f.ZP)
                    d.mechanical && group in setOf("Shaft","Flywheel","Turbine","Rotor","Fan") -> rotate(motion.angle,Vector3f.ZP)
                    group=="lever" -> rotate(Mth.lerp(motion.factor,model.property(group,"alphaOff"),model.property(group,"alphaOn")),Vector3f.YP)
                    kind==Behavior.RELAY && group.startsWith("relay") -> rotate(Mth.lerp(motion.factor,model.property(group,"rOff"),model.property(group,"rOn")),Vector3f.ZP)
                    (d.id=="signal_trimmer" && group=="rot") || (d.id=="analog_vumeter" && group=="Pointer") ->
                        rotate(Mth.lerp(motion.factor,model.property(group,"alphaOff"),model.property(group,"alphaOn")),Vector3f.XP)
                    d.model=="ledswitch" && group=="button" -> pose.translate(model.property(group,"tx",-.04f)*motion.factor.toDouble(),0.0,0.0)
                    kind==Behavior.ROLLING_SHAFT && group in setOf("rot1","rot2") -> rotate(motion.angle,if(group=="rot1")Vector3f.ZP else Vector3f.ZN)
                    kind in setOf(Behavior.MACERATOR,Behavior.PLATE_MACHINE) && group in setOf("rot1","rot2") ->
                        rotate(motion.angle,if(group=="rot1")Vector3f.ZN else Vector3f.ZP)
                    kind==Behavior.ACTIVE_COOLER && group=="rot" -> rotate(motion.angle,Vector3f.YP)
                    kind==Behavior.COMPRESSOR && group=="move" -> pose.translate(0.0,-.125*sqrt(part.processFraction),0.0)
                    kind==Behavior.SOLAR && group=="panel" -> {
                        // Tracking follows the world sun independently of the pedestal's placement yaw.
                        val axis=FaceBasis.edges(part)[1]
                        pose.mulPose(Vector3f.YP.rotationDegrees((atan2(axis.stepZ.toDouble(),axis.stepX.toDouble())*180/PI).toFloat()))
                        rotate((part.solarAngle*180/PI-90).toFloat(),Vector3f.ZP)
                    }
                }
                if(kind in setOf(Behavior.SHAFT_MOTOR,Behavior.SHAFT_GENERATOR) && group.matches(Regex("LED_[0-6]"))) {
                    val electrical=part.thermalState.getDouble("shaftElectricalWatts")
                    val power=if(kind==Behavior.SHAFT_MOTOR)-electrical else electrical
                    val index=group.last().digitToInt()
                    val nominal=if(kind==Behavior.SHAFT_MOTOR)32000.0 else d.power
                    val brightness=if(power<0)if(index==0)(-power/d.power*4).coerceIn(0.0,.60)else 0.0 else ((power-index*nominal/5)/(nominal/5)).coerceIn(0.0,.65)
                    val base=if(power<0 || index>=5)0xFF0000 else if(index==4)0xFFFF00 else 0x00FF00
                    val intensity=(brightness*2).coerceAtMost(1.0)
                    color=(((base shr 16 and 255)*intensity).toInt() shl 16) or (((base shr 8 and 255)*intensity).toInt() shl 8) or ((base and 255)*intensity).toInt()
                    if(color!=0)groupLight=15728880
                }
                if(group.equals("led",true) || group.matches(Regex("led[0-7]"))) {
                    val on=when {
                        d.wireless?.kind in setOf("RX","REPEATER") -> part.active
                        d.wireless?.kind=="SOURCE" -> part.enabled
                        kind==Behavior.LOGGER -> part.enabled
                        d.id=="signal_switch" -> part.enabled
                        kind==Behavior.BUTTON -> part.timer>0
                        group.startsWith("led") && group.length==4 -> part.pinVoltages[group.last().digitToInt()%4]>2.5
                        else -> part.voltage>2.5
                    }
                    color=if(d.id=="multicolor_led_vumeter") {
                        if(part.voltage>.001)Mth.hsvToRgb((part.voltage/5).toFloat().coerceIn(0f,1f),1f,1f) else 0x555555
                    } else if(on)0x44FF44 else 0x661515
                    if(on)groupLight=15728880
                }
                if(kind==Behavior.BATTERY_CHARGER && group.matches(Regex("led[0-3]"))) {
                    val bit=1 shl group.last().digitToInt();val present=part.gridState.getInt("chargerPresence") and bit!=0
                    val charged=part.gridState.getInt("chargerCharged") and bit!=0
                    color=if(!present)0x333333 else if(charged)0x00FF00 else 0xFF0000
                    groupLight=if(present)15728880 else light
                }
                if(kind==Behavior.FUEL_HEAT_FURNACE && group in setOf("PowerLED","HeatLED")) {
                    val on=if(group=="PowerLED")part.enabled else part.active
                    color=if(group=="PowerLED")if(on)0x00FF00 else 0x008000 else if(on)0xFF0000 else 0x800000
                    if(on)groupLight=15728880
                }
                if(d.id=="digital_display") {
                    if(group.startsWith("digit")) {
                        val divisor=10.0.pow(group.last().digitToInt()).toInt()
                        val reading=part.displayReading
                        uOffset=(if(part.displayBlank)10 else reading/divisor%10)/16f;color=part.displayColor;groupLight=15728880
                        texture=ResourceLocation("eln","model/digitaldisplay/digits_led.png")
                    }
                    if(group.startsWith("dot") || group.startsWith("colon")) {
                        val bit=if(group.startsWith("dot"))group.last().digitToInt() else 4+group.last().digitToInt()
                        color=if(part.displayBits and (1 shl bit)!=0)part.displayColor else 0
                        if(color!=0)groupLight=15728880
                    }
                    if(group=="glass") {
                        texture=ResourceLocation("eln","model/digitaldisplay/reflection.png");translucent=true
                        val player=net.minecraft.client.Minecraft.getInstance().player
                        uOffset=(player?.yRot ?: 0f)/360;vOffset=(player?.xRot ?: 0f)/180*.875f
                    }
                }
                if(kind==Behavior.LAMP && part.signal>0)groupLight=15728880
                LegacyMeshes.draw(faces,pose,buffers,groupLight,overlay,color,uOffset,vOffset,texture,translucent)
                pose.popPose()
            }
            pose.popPose()
        }
        GridWireRenderer.render(node,pose,buffers,light,overlay)
    }
    override fun shouldRenderOffScreen(node: FaceNodeEntity)=node.parts.filterNotNull().any { it.definition.grid!=null || it.definition.visual.scale>1 && it.definition.mechanical || it.definition.behavior in setOf(Behavior.FESTIVE,Behavior.WIND_TURBINE,Behavior.WATER_TURBINE) }
    override fun getViewDistance()=128
}
