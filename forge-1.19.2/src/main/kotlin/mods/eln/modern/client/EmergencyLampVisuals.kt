package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.modern.*
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.Direction

internal object EmergencyLampVisuals {
    fun render(p: PartState,model: LegacyModel,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        fun draw(group: String,glow: Boolean=false) { model.groups[group]?.let { LegacyMeshes.draw(it,pose,buffers,if(glow && p.active)15728880 else light,overlay) } }
        if(p.face==Direction.UP) { draw("coreCeil");draw("panelCeil",true);if(p.active)draw("lightCeil",true) }
        else { draw("coreWall",true);draw(if(p.rotation==3)"coreWallL" else "coreWallR",true);if(p.active)draw("lightWall",true) }
    }
}
