package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.modern.*
import net.minecraft.client.renderer.MultiBufferSource

internal object FestiveVisuals {
    fun render(node: FaceNodeEntity,p: PartState,model: LegacyModel,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        fun draw(group: String,glow: Boolean=false,glass: Boolean=false)=LegacyMeshes.draw(model.groups[group].orEmpty(),pose,buffers,if(glow)15728880 else light,overlay,translucent=glass)
        when(p.definition.id) {
            "christmas_tree" -> {
                val alternate=((node.level?.gameTime ?: 0)%21)>10
                draw("StarOn_Star.002",p.active)
                draw("Strip1_Star.000",p.active && !alternate)
                draw("Strip2_Star.001",p.active && alternate)
                draw("Tree_Cone.006")
            }
            "holiday_candle" -> {
                draw("CandleLamp_Cylinder.001")
                if(p.active)draw("LampOn_Cylinder.002",true)
                draw("Glass_Cylinder.000",glass=true)
            }
            "string_lights" -> {
                draw("Lights_Cube.009")
                if(p.active)draw("LightOn_Cube.002",true)
            }
        }
    }
}
