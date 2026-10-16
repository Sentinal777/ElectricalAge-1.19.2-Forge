package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import mods.eln.modern.*
import net.minecraft.client.renderer.MultiBufferSource

internal object FloodlightVisuals {
    fun render(node: FaceNodeEntity,p: PartState,model: LegacyModel,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        fun draw(group: String,glow: Boolean=false) { model.groups[group]?.let { LegacyMeshes.draw(it,pose,buffers,if(glow)15728880 else light,overlay) } }
        fun pivot(angle: Float,axis: Vector3f) { pose.translate(.5,.5,-.5);pose.mulPose(axis.rotationDegrees(angle));pose.translate(-.5,-.5,.5) }
        draw("Lamp_Base_Cube.008");pivot((p.gridState.getDouble("swivel")+90).toFloat(),Vector3f.YP)
        draw("Lamp_Swivel_Cube.014");pivot(-p.gridState.getDouble("head").toFloat(),Vector3f.XP);draw("Lamp_Head_Cylinder.004")
        for(i in 0..1)if(!PartInventory.get(node,p).getStackInSlot(i).isEmpty) {
            val on=p.gridState.getInt("bulbLight$i")>0
            draw(if(i==0)if(on)"Lamp1_ON_Cylinder.000" else "Lamp1_OFF_Cylinder.003" else if(on)"Lamp2_ON_Cylinder.001" else "Lamp2_OFF_Cylinder.002",on)
        }
    }
}
