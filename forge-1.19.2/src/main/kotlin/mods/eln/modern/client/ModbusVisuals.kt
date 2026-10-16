package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import mods.eln.misc.PhysicalInterpolator
import mods.eln.modern.FaceNodeEntity
import mods.eln.modern.PartState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import java.util.WeakHashMap

internal object ModbusVisuals {
    private data class Door(val motion: PhysicalInterpolator,var time: Double)
    private val doors=WeakHashMap<FaceNodeEntity,MutableMap<Int,Door>>()
    fun render(node: FaceNodeEntity,p: PartState,model: LegacyModel,time: Double,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        val held=node.level==null
        val door=doors.getOrPut(node) { hashMapOf() }.getOrPut(p.face.get3DDataValue()) { Door(PhysicalInterpolator(.4f,8f,.9f,.2f),time) }
        if(held)door.motion.setPos(.7f)
        door.motion.target=if(held).7f else if(Minecraft.getInstance().player?.distanceToSqr(node.blockPos.x+.5,node.blockPos.y+.5,node.blockPos.z+.5)?.let { it<1 }==true)1f else 0f
        var remaining=((time-door.time)/20).coerceIn(0.0,.25);door.time=time
        while(remaining>1e-8) { val step=minOf(.02,remaining);door.motion.step(step.toFloat());remaining-=step }
        fun draw(group: String,color: Int=0xFFFFFF,glow: Boolean=false)=LegacyMeshes.draw(model.groups[group].orEmpty(),pose,buffers,if(glow)15728880 else light,overlay,color)
        draw("main")
        pose.pushPose();val x=model.property("door","originX").toDouble();val y=model.property("door","originY").toDouble();val z=model.property("door","originZ").toDouble()
        pose.translate(x,y,z);pose.mulPose(Vector3f.YP.rotationDegrees((1-door.motion.get())*model.property("door","alphaOff")));pose.translate(-x,-y,-z);draw("door");pose.popPose()
        draw("led-power",0x00CC00,true)
        val active=p.gridState.getLong("modbusActivityUntil")>time;val error=p.gridState.getLong("modbusErrorUntil")>time
        draw("led-activity",if(active)0xCCCC00 else 0x66661A,active);draw("led-error",if(error)0xCC0000 else 0x663333,error)
        draw("display",if(door.motion.get()>.9)0xFFFFFF else 0x808080,door.motion.get()>.9)
    }
}
