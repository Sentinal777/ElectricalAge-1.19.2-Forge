package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import mods.eln.misc.PhysicalInterpolator
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.util.Mth
import java.util.WeakHashMap

/** Original pointer inertia, color LEDs and processor door/indicator meshes. */
internal object SignalDeviceVisuals {
    private data class State(val id: String,val movement: PhysicalInterpolator,var last: Double)
    private val motion=WeakHashMap<FaceNodeEntity,MutableMap<Int,State>>()
    fun render(node: FaceNodeEntity,p: PartState,model: LegacyModel,time: Double,dt: Double,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        val processor=p.definition.behavior in setOf(Behavior.PROCESSOR,Behavior.MQTT_CONTROLLER)
        val held=node.level==null
        // Source vuMeters use front.right(), while ElectricalMath and the energy meters use front.left().
        if(!processor && !held && p.face.axis==net.minecraft.core.Direction.Axis.Y)pose.mulPose(Vector3f.XP.rotationDegrees(180f))
        val states=motion.getOrPut(node) { hashMapOf() };val face=p.face.get3DDataValue()
        val s=states[face]?.takeIf { it.id==p.definition.id } ?: State(p.definition.id,if(processor)PhysicalInterpolator(.4f,8f,.9f,.2f)else PhysicalInterpolator(.4f,2f,1.5f,.2f).apply { setPos((p.voltage/5).coerceIn(0.0,1.0).toFloat()) },time).also { states[face]=it }
        val elapsed=((time-s.last)/20).coerceIn(0.0,.25);s.last=time
        if(processor && held)s.movement.setPos(.7f)
        s.movement.target=if(processor)if(held).7f else if(Minecraft.getInstance().player?.distanceToSqr(node.blockPos.x+.5,node.blockPos.y+.5,node.blockPos.z+.5)?.let { it<1 }==true)1f else 0f else (p.voltage/5).coerceIn(0.0,1.0).toFloat()
        var left=elapsed
        while(left>1e-8) { val step=minOf(.02,left);s.movement.step(step.toFloat());left-=step }
        fun draw(group: String,color: Int=0xFFFFFF,glow: Boolean=false,alpha: Int=255)=LegacyMeshes.draw(model.groups[group].orEmpty(),pose,buffers,if(glow)15728880 else light,overlay,color,translucent=alpha<255,alpha=alpha)
        fun rotate(group: String,angle: Float,axis: Vector3f) {
            pose.pushPose()
            val x=model.property(group,"originX").toDouble();val y=model.property(group,"originY").toDouble();val z=model.property(group,"originZ").toDouble()
            pose.translate(x,y,z);pose.mulPose(axis.rotationDegrees(angle));pose.translate(-x,-y,-z);draw(group);pose.popPose()
        }
        if(processor) {
            draw("main");rotate("door",(1-s.movement.get())*model.property("door","alphaOff"),Vector3f.YP)
            for(i in 0..7) {
                val on=if(held)i in setOf(0,2,4,5,6)else i%4==0 || kotlin.math.sin(kotlin.math.floor(time/8)*17.1+i*7.9)>0.59
                draw("led$i",if(on)if(i%4==0)0xCC0000 else 0x00CC00 else 0x4D4D4D,on)
            }
        }else if(p.definition.id=="analog_vumeter") {
            draw("Vumeter");rotate("Pointer",Mth.lerp(s.movement.get(),model.property("Pointer","alphaOff"),model.property("Pointer","alphaOn")),Vector3f.XP)
        }else {
            draw("main")
            val rgb=p.definition.id=="multicolor_led_vumeter";val factor=if(rgb)s.movement.get()else (p.voltage/5).coerceIn(0.0,1.0).toFloat()
            val on=if(rgb)factor>.005f else factor>.5f
            val color=if(rgb)if(on)Mth.hsvToRgb(factor,1f,1f)else 0x808080 else if(on)0x00B300 else 0xB30000
            draw("Led",color,true)
            if(on || !rgb)draw("halo",color,true,96)
        }
    }
}
