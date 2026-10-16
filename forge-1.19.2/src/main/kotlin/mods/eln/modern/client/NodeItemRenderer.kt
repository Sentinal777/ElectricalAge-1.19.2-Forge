package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.block.model.ItemTransforms
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack

/** Original 3D item descriptors share their world mesh; inventory sprites use a separate model perspective. */
class NodeItemRenderer private constructor(): BlockEntityWithoutLevelRenderer(Minecraft.getInstance().blockEntityRenderDispatcher,Minecraft.getInstance().entityModels) {
    private val renderer=NodeRenderer()
    private val projections=hashMapOf<String,FaceNodeEntity>()
    override fun renderByItem(stack: ItemStack,type: ItemTransforms.TransformType,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        val d=(stack.item as? PartItem)?.definition ?: return
        val projection=projections.getOrPut(d.id) {
            FaceNodeEntity(BlockPos.ZERO,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState()).apply { parts[0]=PartState(d,Direction.DOWN) }
        }
        pose.pushPose()
        if(d.heldModel) {
            val bounds=PartGeometry.body(projection.parts[0]!!)
            val scale=(1/maxOf(1.0,bounds.xsize,bounds.ysize,bounds.zsize)).toFloat()
            pose.translate(.5,.5,.5);pose.scale(scale,scale,scale)
            pose.translate(-bounds.center.x,-bounds.center.y,-bounds.center.z)
        }
        renderer.render(projection,0f,pose,buffers,light,overlay)
        pose.popPose()
    }
    companion object { val instance by lazy { NodeItemRenderer() } }
}
