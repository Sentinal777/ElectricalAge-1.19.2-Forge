package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import mods.eln.misc.RcInterpolator
import mods.eln.modern.*
import net.minecraft.client.renderer.MultiBufferSource
import java.util.WeakHashMap

object ProtectionVisuals {
    private data class Motion(var time: Double,val filter: RcInterpolator)
    private val motions=WeakHashMap<FaceNodeEntity,Array<Motion?>>()
    private fun color(cable: String?)=when(cable) { "low_voltage_cable" -> 0x8CD6AD;"medium_voltage_cable" -> 0x8CBDD9;"high_voltage_cable" -> 0xF5CC8F;"very_high_voltage_cable" -> 0xDB948C;else -> 0xFFFFFF }
    internal fun fuse(definition: MaterialDefinition,model: LegacyModel,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        LegacyMeshes.draw(model.groups["FuseType"] ?: emptyList(),pose,buffers,light,overlay,color(definition.fuseCable))
        if(definition.fuseCable!=null)LegacyMeshes.draw(model.groups["FuseOk"] ?: emptyList(),pose,buffers,light,overlay)
        LegacyMeshes.draw(model.groups["Fuse"] ?: emptyList(),pose,buffers,light,overlay)
    }
    internal fun render(node: FaceNodeEntity,p: PartState,model: LegacyModel,partialTick: Float,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        if(p.definition.behavior==Behavior.FUSE_HOLDER) {
            pose.mulPose(Vector3f.XP.rotationDegrees(90f))
            LegacyMeshes.draw(model.groups["Case"] ?: emptyList(),pose,buffers,light,overlay)
            val definition=(PartInventory.get(node,p).getStackInSlot(0).item as? MaterialItem)?.definition
            if(definition?.kind=="FUSE")fuse(definition,model,pose,buffers,light,overlay)
            return
        }
        val time=(node.level?.gameTime ?: 0L)+partialTick.toDouble()
        val faces=motions.getOrPut(node) { arrayOfNulls(6) };val index=p.face.ordinal
        val motion=faces[index] ?: Motion(time,RcInterpolator(model.property("lever","speed",.1f)).apply { setValue(if(p.enabled)1f else 0f) }).also { faces[index]=it }
        val dt=((time-motion.time)/20).coerceIn(0.0,.25).toFloat();motion.time=time
        motion.filter.target=if(p.enabled)1f else 0f;motion.filter.step(dt)
        LegacyMeshes.draw(model.groups["case"] ?: emptyList(),pose,buffers,light,overlay)
        val angle=model.property("lever","alphaOff")+motion.filter.get()*(model.property("lever","alphaOn")-model.property("lever","alphaOff"))
        pose.pushPose();pose.mulPose(Vector3f.YP.rotationDegrees(angle));LegacyMeshes.draw(model.groups["lever"] ?: emptyList(),pose,buffers,light,overlay);pose.popPose()
    }
}
