package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.modern.*
import net.minecraft.client.renderer.MultiBufferSource

internal object FabricatorVisuals {
    fun render(node: FaceNodeEntity,p: PartState,model: LegacyModel,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        LegacyMeshes.draw(model.groups["Cube.002_Cube.013"].orEmpty(),pose,buffers,light,overlay)
        pose.pushPose()
        if(p.active)node.level?.random?.let { pose.translate(.3*it.nextDouble()-.15,-.1*it.nextDouble(),0.0) }
        LegacyMeshes.draw(model.groups["Cube.003_Cube.012"].orEmpty(),pose,buffers,light,overlay)
        LegacyMeshes.draw(model.groups["Cube.001_Cube.010"].orEmpty(),pose,buffers,light,overlay)
        pose.popPose()
    }
}
