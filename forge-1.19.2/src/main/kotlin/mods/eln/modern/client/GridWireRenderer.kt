package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.modern.FaceNodeEntity
import mods.eln.modern.grid.GridGeometry
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import java.util.WeakHashMap

object GridWireRenderer {
    val switchRotors=mapOf(
        "Contact_M1_ContactMMesh_1" to (Vec3(4.5,1.75,.5) to true),
        "Contact_M2_ContactMMesh_2.001" to (Vec3(4.5,1.75,2.5) to false),
        "Contact_F1_ContactFMesh_1" to (Vec3(.5,1.75,.5) to false),
        "Contact_F2_ContactFMesh_2" to (Vec3(.5,1.75,2.5) to true),
        "Belt_pulley_1_BeltPulleyMesh_1" to (Vec3(4.0,1.75,.5) to false),
        "Belt_pulley_2_BeltPulleyMesh_2" to (Vec3(4.0,1.75,2.5) to true))
    private data class Cached(val hash: Int,val faces: List<MeshFace>)
    private val cache=WeakHashMap<FaceNodeEntity,Cached>()
    private val texture=ResourceLocation("eln","textures/wire.png")
    private fun mesh(start: Vec3,end: Vec3): List<MeshFace> {
        val points=GridGeometry.catenary(start,end);val faces=mutableListOf<MeshFace>()
        for(i in 0 until points.lastIndex) {
            val a=points[i];val b=points[i+1];val direction=b.subtract(a).normalize()
            if(a.distanceToSqr(b)<1e-12)continue
            val horizontal=direction.cross(Vec3(0.0,1.0,0.0)).let { if(it.lengthSqr()<1e-12)Vec3(1.0,0.0,0.0)else it.normalize() }.scale(.025)
            val vertical=direction.cross(horizontal).normalize().scale(.025)
            val radial=listOf(horizontal.add(vertical),horizontal.subtract(vertical),horizontal.scale(-1.0).subtract(vertical),vertical.subtract(horizontal))
            for(j in 0..3) {
                val next=(j+1)%4;val v=listOf(a.add(radial[j]),a.add(radial[next]),b.add(radial[next]),b.add(radial[j]))
                val n=radial[j].add(radial[next]).normalize();val normal=MeshPoint(n.x.toFloat(),n.y.toFloat(),n.z.toFloat())
                faces.add(MeshFace(v.map { MeshPoint(it.x.toFloat(),it.y.toFloat(),it.z.toFloat()) },listOf(MeshUV(0f,0f),MeshUV(1f,0f),MeshUV(1f,1f),MeshUV(0f,1f)),List(4) { normal },texture))
            }
        }
        return faces
    }
    fun render(node: FaceNodeEntity,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        val hash=node.gridRenderLinks.hashCode()
        var cached=cache[node]
        if(cached==null || cached.hash!=hash) {
            val origin=Vec3.atLowerCornerOf(node.blockPos)
            val faces=node.gridRenderLinks.flatMap { raw ->
                val t=raw as CompoundTag
                fun point(prefix: String)=Vec3(t.getDouble(prefix+"x"),t.getDouble(prefix+"y"),t.getDouble(prefix+"z")).subtract(origin)
                val a0=point("a0");val a1=point("a1");val b0=point("b0");val b1=point("b1")
                val straight=a0.subtract(b0).normalize().dot(a1.subtract(b1).normalize())
                val crossed=a0.subtract(b1).normalize().dot(a1.subtract(b0).normalize())
                if(crossed<straight)mesh(a0,b0)+mesh(a1,b1)else mesh(a0,b1)+mesh(a1,b0)
            }
            cached=Cached(hash,faces);cache[node]=cached
        }
        LegacyMeshes.draw(cached.faces,pose,buffers,light,overlay)
    }
}
