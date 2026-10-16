package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.Direction
import net.minecraft.world.level.LightLayer
import net.minecraftforge.client.model.data.ModelData

/** Copy the actual normal block model, including cutout/glass layers and biome-tinted leaves. */
object NodeCoverVisuals {
    fun render(node: FaceNodeEntity,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        val state=NodeCovers.state(node) ?: return
        val mc=Minecraft.getInstance();val world=node.level
        val color=mc.blockColors.getColor(state,world,node.blockPos,0)
        val red=(color shr 16 and 255)/255f;val green=(color shr 8 and 255)/255f;val blue=(color and 255)/255f
        val packed=if(world==null)light else {
            val positions=Direction.values().map(node.blockPos::relative)
            LightTexture.pack(maxOf(state.getLightEmission(world,node.blockPos),positions.maxOf { world.getBrightness(LightLayer.BLOCK,it) }),positions.maxOf { world.getBrightness(LightLayer.SKY,it) })
        }
        val type=ItemBlockRenderTypes.getRenderType(state,false)
        mc.blockRenderer.modelRenderer.renderModel(pose.last(),buffers.getBuffer(type),state,mc.blockRenderer.getBlockModel(state),red,green,blue,packed,overlay,ModelData.EMPTY,type)
    }
}
