package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.misc.RcInterpolator
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import java.util.WeakHashMap

internal object TutorialSignVisuals {
    private data class State(val value: RcInterpolator=RcInterpolator(.4f),var time: Double=0.0)
    private val nodes=WeakHashMap<FaceNodeEntity,MutableMap<Int,State>>()
    fun render(node: FaceNodeEntity,p: PartState,model: LegacyModel,time: Double,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        val held=node.level==null
        val selected=TutorialSignOverlay.nearest(Minecraft.getInstance())
        val s=nodes.getOrPut(node) { hashMapOf() }.getOrPut(p.face.get3DDataValue()) { State(time=time) }
        s.value.target=if(held || selected!=null && selected.first===node && selected.second===p)1f else 0f
        s.value.step(((time-s.time)/20).coerceIn(0.0,.25).toFloat());s.time=time
        val factor=if(held)1f else s.value.get()
        val blue=if(factor<.5f)(.4f*(1-factor*2)*255).toInt().coerceIn(0,255)else 0
        val green=if(factor>.5f)((factor-.5f)*2*255).toInt().coerceIn(0,255)else 0
        val tint=(green shl 8) or blue
        LegacyMeshes.draw(model.groups["main"].orEmpty(),pose,buffers,light,overlay)
        LegacyMeshes.draw(model.groups["light"].orEmpty(),pose,buffers,15728880,overlay,tint)
        LegacyMeshes.draw(model.groups["halo"].orEmpty(),pose,buffers,15728880,overlay,tint,translucent=true,alpha=102)
    }
}
