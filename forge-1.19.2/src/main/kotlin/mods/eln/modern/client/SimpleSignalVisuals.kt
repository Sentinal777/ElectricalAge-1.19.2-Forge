package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import mods.eln.modern.FaceNodeEntity
import mods.eln.modern.PartState
import net.minecraft.client.renderer.MultiBufferSource
import java.util.WeakHashMap

/** Original trimmer and illuminated button motion, LED colors and translucent halo. */
internal object SimpleSignalVisuals {
    private data class Motion(val id: String,var factor: Float,var time: Double)
    private val motions=WeakHashMap<FaceNodeEntity,MutableMap<Int,Motion>>()
    fun supported(p: PartState)=p.definition.contact==null && p.definition.id in setOf("signal_trimmer","signal_switch","signal_button")
    fun render(node: FaceNodeEntity,p: PartState,model: LegacyModel,time: Double,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        val pot=p.definition.id=="signal_trimmer"
        val target=if(node.level==null)0f else if(pot)(p.value/5).toFloat().coerceIn(0f,1f)else if(p.enabled)1f else 0f
        val states=motions.getOrPut(node) { hashMapOf() }
        val state=states[p.face.ordinal]?.takeIf { it.id==p.definition.id } ?: Motion(p.definition.id,target,time).also { states[p.face.ordinal]=it }
        var elapsed=((time-state.time)/20).coerceIn(0.0,.25);state.time=time
        val tau=model.property(if(pot)"rot" else "button","speed",if(pot).3f else .11f).coerceAtLeast(.001f)
        while(elapsed>1e-8) { val dt=minOf(elapsed,.02);state.factor+=(target-state.factor)*(dt.toFloat()/tau);elapsed-=dt }
        fun draw(group: String,color: Int=0xFFFFFF,glow: Boolean=false,alpha: Int=255)=LegacyMeshes.draw(model.groups[group].orEmpty(),pose,buffers,if(glow)15728880 else light,overlay,color,translucent=alpha<255,alpha=alpha)
        draw("main")
        pose.pushPose()
        if(pot) {
            val x=model.property("rot","originX").toDouble();val y=model.property("rot","originY").toDouble();val z=model.property("rot","originZ").toDouble()
            pose.translate(x,y,z);pose.mulPose(Vector3f.XP.rotationDegrees(model.property("rot","alphaOff")+state.factor*(model.property("rot","alphaOn")-model.property("rot","alphaOff"))));pose.translate(-x,-y,-z);draw("rot")
        }else {
            pose.translate((model.property("button","tx")*state.factor).toDouble(),0.0,0.0);draw("button")
            val color=if(state.factor>.5f)0x00B300 else 0xB30000
            draw("led",color,true);draw("halo",color,true,96)
        }
        pose.popPose()
    }
}
