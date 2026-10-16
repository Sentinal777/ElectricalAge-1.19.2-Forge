package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import mods.eln.misc.RcInterpolator
import mods.eln.modern.*
import net.minecraft.client.renderer.MultiBufferSource

object ControlVisuals {
    private class Motion { var tick=Long.MIN_VALUE;var angle=0f;val speed=RcInterpolator(.4f) }
    private val motions=java.util.WeakHashMap<FaceNodeEntity,Array<Motion?>>()
    private fun motion(node: FaceNodeEntity,p: PartState): Motion {
        val entries=motions.getOrPut(node) { arrayOfNulls(6) }
        return entries[p.face.ordinal] ?: Motion().also { entries[p.face.ordinal]=it }
    }
    fun observe(node: FaceNodeEntity,p: PartState) {
        if(p.definition.alarm==null)return
        val now=node.level?.gameTime ?: return;val m=motion(node,p)
        if(m.tick==now)return
        m.tick=now;m.speed.target=if(p.active)360f else 0f;m.speed.step(.05f);m.angle=(m.angle+m.speed.get()*.05f)%360
    }
    internal fun render(node: FaceNodeEntity,p: PartState,model: LegacyModel,partial: Float,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        val alarm=p.definition.alarm!=null;val held=node.level==null;val on=held || p.active
        val texture=if(alarm)model.textures[if(on)"onTexture" else "offTexture"] else null
        fun draw(group: String,color: Int=0xFFFFFF,glow: Boolean=false)=LegacyMeshes.draw(model.groups[group] ?: emptyList(),pose,buffers,if(glow)15728880 else light,overlay,color,texture=texture)
        draw("main")
        val angle=if(alarm) { observe(node,p);val m=motion(node,p);m.angle+m.speed.get()*partial*.05f } else {
            val factor=if(held)1f else if(p.value>0)(p.thermalState.getDouble("timerLeft")/p.value).coerceIn(0.0,1.0).toFloat() else 0f
            model.property("rot","rotEnd")+(model.property("rot","rotStart")-model.property("rot","rotEnd"))*factor
        }
        val x=model.property("rot","originX").toDouble();val y=model.property("rot","originY").toDouble();val z=model.property("rot","originZ").toDouble()
        pose.pushPose();pose.translate(x,y,z);pose.mulPose(Vector3f.XP.rotationDegrees(angle));pose.translate(-x,-y,-z);draw("rot",glow=alarm);pose.popPose()
        if(alarm)draw("light",glow=true) else draw("led",if(on)0x00B300 else 0xB30000,true)
    }
}
