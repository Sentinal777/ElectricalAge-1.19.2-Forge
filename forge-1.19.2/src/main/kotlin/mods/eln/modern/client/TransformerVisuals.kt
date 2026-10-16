package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import mods.eln.modern.*
import net.minecraft.client.renderer.MultiBufferSource

/** Original winding stacking, optional core and casing; all meshes are upstream assets. */
internal object TransformerVisuals {
    fun render(node: FaceNodeEntity,p: PartState,model: LegacyModel,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        fun draw(m: LegacyModel?,group: String) { m?.groups?.get(group)?.let { LegacyMeshes.draw(it,pose,buffers,light,overlay) } }
        draw(model,"main")
        val inv=PartInventory.get(node,p)
        if((inv.getStackInSlot(2).item as? MaterialItem)?.definition?.kind=="FERROMAGNETIC_CORE") {
            draw(LegacyMeshes.model("feromagneticcorea"),"fero")
            for(side in 0..1) {
                val count=inv.getStackInSlot(side).count.coerceIn(0,64)
                if(count==0)continue
                val scale=4f*minOf(count/16f,1f)*2/(count+1)
                pose.pushPose()
                if(side==1)pose.mulPose(Vector3f.YP.rotationDegrees(180f))
                pose.scale(1f,scale,1f);pose.translate(0.0,-.125*(count-1)/4,0.0)
                repeat(count) { draw(model,"sbire");pose.translate(0.0,.25/4,0.0) }
                pose.popPose()
            }
        }
        if((inv.getStackInSlot(3).item as? MaterialItem)?.definition?.kind=="CASING") {
            val shell=LegacyMeshes.model("transformatorcase") ?: return
            draw(shell,"Case")
            val player=net.minecraft.client.Minecraft.getInstance().player
            val opened=player!=null && player.distanceToSqr(node.blockPos.x+.5,node.blockPos.y+.5,node.blockPos.z+.5)<4
            for(group in listOf("DoorL","DoorR")) {
                pose.pushPose()
                val x=shell.property(group,"originX").toDouble();val y=shell.property(group,"originY").toDouble();val z=shell.property(group,"originZ").toDouble()
                pose.translate(x,y,z);pose.mulPose(Vector3f.YP.rotationDegrees(if(opened)90f*(if(group=="DoorL")-1 else 1)else 0f));pose.translate(-x,-y,-z)
                draw(shell,group);pose.popPose()
            }
        }
    }
}
