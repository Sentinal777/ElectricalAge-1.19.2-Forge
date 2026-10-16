package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.block.model.ItemTransforms
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

internal object IncubatorVisuals {
    fun render(node: FaceNodeEntity,p: PartState,model: LegacyModel,partialTick: Float,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        if(!PartInventory.peek(p,0).isEmpty) {
            pose.pushPose();pose.translate(0.0,-.3,.13)
            pose.mulPose(Vector3f.YP.rotationDegrees((((node.level?.gameTime ?: 0)+partialTick)*3)%360))
            pose.scale(.6f,.6f,.6f)
            Minecraft.getInstance().itemRenderer.renderStatic(ItemStack(Items.EGG),ItemTransforms.TransformType.GROUND,light,overlay,pose,buffers,0)
            pose.popPose()
        }
        LegacyMeshes.draw(model.groups["main"].orEmpty(),pose,buffers,light,overlay)
        LegacyMeshes.draw(model.groups["lampf"].orEmpty(),pose,buffers,light,overlay,0x191919)
        val alpha=(p.powerFactor*p.powerFactor*.5*255).toInt().coerceIn(0,255)
        if(alpha>0)LegacyMeshes.draw(model.groups["lamp"].orEmpty(),pose,buffers,15728880,overlay,0xFF3300,translucent=true,alpha=alpha)
    }
}
