package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.block.model.ItemTransforms
import net.minecraft.world.item.ItemStack

/** Original fuse item mesh; Forge separate transforms preserve the original flat inventory artwork. */
class FuseItemRenderer private constructor(): BlockEntityWithoutLevelRenderer(Minecraft.getInstance().blockEntityRenderDispatcher,Minecraft.getInstance().entityModels) {
    override fun renderByItem(stack: ItemStack,type: ItemTransforms.TransformType,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        val definition=(stack.item as? MaterialItem)?.definition ?: return
        val model=LegacyMeshes.model("electricalfuse") ?: return
        pose.pushPose();pose.translate(.6,.4,.8)
        val axis=Vector3f(.6f,1f,0f);axis.normalize();pose.mulPose(axis.rotationDegrees(150f));pose.scale(1.5f,1.5f,1.5f)
        ProtectionVisuals.fuse(definition,model,pose,buffers,light,overlay)
        pose.popPose()
    }
    companion object { val instance by lazy { FuseItemRenderer() } }
}
