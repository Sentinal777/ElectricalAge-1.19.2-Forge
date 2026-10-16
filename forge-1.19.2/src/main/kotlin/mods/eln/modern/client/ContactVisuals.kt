package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import mods.eln.misc.RcInterpolator
import mods.eln.modern.*
import net.minecraft.client.renderer.MultiBufferSource

object ContactVisuals {
    private class Motion(val id: String,tau: Float,on: Boolean) {
        var tick=Long.MIN_VALUE
        val rc=RcInterpolator(tau).apply { setValue(if(on)1f else 0f) }
    }
    private val motions=java.util.WeakHashMap<FaceNodeEntity,Array<Motion?>>()
    private fun motion(node: FaceNodeEntity,p: PartState,model: LegacyModel): Motion {
        val entries=motions.getOrPut(node) { arrayOfNulls(6) };val i=p.face.ordinal
        val existing=entries[i]
        if(existing!=null && existing.id==p.definition.id)return existing
        return Motion(p.definition.id,model.property(if(p.definition.contact!!.relay)"relay0" else "lever","speed",.1f),p.active).also { entries[i]=it }
    }
    fun observe(node: FaceNodeEntity,p: PartState) {
        if(p.definition.contact==null)return
        val world=node.level ?: return;val model=LegacyMeshes.model(p.definition.model) ?: return;val m=motion(node,p,model)
        if(m.tick==world.gameTime)return
        m.tick=world.gameTime;m.rc.target=if(p.active)1f else 0f;m.rc.step(.05f)
    }
    internal fun render(node: FaceNodeEntity,p: PartState,model: LegacyModel,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        observe(node,p);val factor=if(node.level==null)0f else motion(node,p,model).rc.get()
        fun draw(group: String,color: Int=0xFFFFFF)=LegacyMeshes.draw(model.groups[group] ?: emptyList(),pose,buffers,light,overlay,color)
        fun rotate(group: String,angle: Float,axis: Vector3f) {
            val x=model.property(group,"originX").toDouble();val y=model.property(group,"originY").toDouble();val z=model.property(group,"originZ").toDouble()
            pose.pushPose();pose.translate(x,y,z);pose.mulPose(axis.rotationDegrees(angle));pose.translate(-x,-y,-z);draw(group);pose.popPose()
        }
        if(p.definition.contact!!.relay) {
            draw("main");draw("backplate",p.definition.color)
            for(group in listOf("relay0","relay1"))rotate(group,model.property(group,"rOff")+factor*(model.property(group,"rOn")-model.property(group,"rOff")),Vector3f.ZP)
        } else {
            draw("case");rotate("lever",model.property("lever","alphaOff")+factor*(model.property("lever","alphaOn")-model.property("lever","alphaOff")),Vector3f.YP)
        }
    }
}
