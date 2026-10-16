package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.block.model.ItemTransforms
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/** Original inventory icon and life-dependent dry overlay; held brushes retain their flat artwork. */
class BrushItemRenderer private constructor(): BlockEntityWithoutLevelRenderer(Minecraft.getInstance().blockEntityRenderDispatcher,Minecraft.getInstance().entityModels) {
    override fun renderByItem(stack: ItemStack,type: ItemTransforms.TransformType,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        val d=(stack.item as? MaterialItem)?.definition ?: return
        val vertices=listOf(MeshPoint(0f,0f,0f),MeshPoint(1f,0f,0f),MeshPoint(1f,1f,0f),MeshPoint(0f,1f,0f))
        val uv=listOf(MeshUV(0f,1f),MeshUV(1f,1f),MeshUV(1f,0f),MeshUV(0f,0f))
        val normals=List(4) { MeshPoint(0f,0f,1f) }
        fun draw(texture: ResourceLocation,alpha: Int=255) {
            LegacyMeshes.draw(listOf(MeshFace(vertices,uv,normals,texture)),pose,buffers,15728880,overlay,translucent=true,alpha=alpha)
        }
        draw(ResourceLocation("eln","textures/"+d.icon+".png"))
        if(Minecraft.getInstance().player?.abilities?.instabuild!=true) {
            pose.pushPose();pose.translate(0.0,0.0,.001)
            draw(ResourceLocation("eln","textures/items/brushdryoverlay.png"),(255*.75*(1-Brushes.life(stack)/32.0)).toInt())
            pose.popPose()
        }
    }
    companion object { val instance by lazy { BrushItemRenderer() } }
}
