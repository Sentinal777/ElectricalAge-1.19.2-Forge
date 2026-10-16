package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.modern.FaceNodeEntity
import net.minecraft.core.Direction
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.resources.ResourceLocation

/** Original fixed six textures; this block's direction names stay in world coordinates. */
internal object ComputerProbeVisuals {
    private fun face(name: String,normal: MeshPoint,vararg points: MeshPoint)=MeshFace(points.toList(),listOf(MeshUV(0f,0f),MeshUV(0f,1f),MeshUV(1f,1f),MeshUV(1f,0f)),List(4) { normal },ResourceLocation("eln","textures/blocks/computerprobe_$name.png"))
    private fun p(x: Int,y: Int,z: Int)=MeshPoint(x.toFloat(),y.toFloat(),z.toFloat())
    private val faces=listOf(
        face("yn",p(0,-1,0),p(0,0,1),p(0,0,0),p(1,0,0),p(1,0,1)),
        face("yp",p(0,1,0),p(0,1,0),p(0,1,1),p(1,1,1),p(1,1,0)),
        face("zn",p(0,0,-1),p(1,1,0),p(1,0,0),p(0,0,0),p(0,1,0)),
        face("zp",p(0,0,1),p(0,1,1),p(0,0,1),p(1,0,1),p(1,1,1)),
        face("xn",p(-1,0,0),p(0,1,0),p(0,0,0),p(0,0,1),p(0,1,1)),
        face("xp",p(1,0,0),p(1,1,1),p(1,0,1),p(1,0,0),p(1,1,0)))
    fun render(node: FaceNodeEntity,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        val level=node.level
        for(side in Direction.values()) {
            // A solid cube has no sky light at its own center. Sample the exposed neighbor as vanilla does.
            val faceLight=if(level!=null)LevelRenderer.getLightColor(level,node.blockPos.relative(side))else light
            val shade=if(level!=null)level.getShade(side,true)else 1f
            val value=(shade*255).toInt().coerceIn(0,255);val tint=(value shl 16) or (value shl 8) or value
            LegacyMeshes.draw(listOf(faces[side.get3DDataValue()]),pose,buffers,faceLight,overlay,tint)
        }
    }
}
