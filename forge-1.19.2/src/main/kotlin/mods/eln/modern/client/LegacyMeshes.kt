package mods.eln.modern.client

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.Eln
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import java.io.InputStreamReader
import kotlin.math.sqrt

internal data class MeshPoint(val x: Float,val y: Float,val z: Float)
internal data class MeshUV(val u: Float,val v: Float)
internal data class MeshFace(val vertices: List<MeshPoint>,val uv: List<MeshUV>,val normals: List<MeshPoint>,val texture: ResourceLocation)
internal data class LegacyModel(val groups: Map<String,List<MeshFace>>,val metadata: Map<String,Map<String,Float>>,val textures: Map<String,ResourceLocation> = emptyMap()) {
    fun property(group: String,key: String,default: Float=0f) = metadata[group]?.get(key) ?: default
}

/** Original OBJ coordinates, materials, UVs and vertex normals; no bounding-box rescaling. */
object LegacyMeshes {
    private val cache=mutableMapOf<String,LegacyModel?>()
    internal val white=ResourceLocation("minecraft","textures/block/white_concrete.png")
    private val paths: Map<String,String> by lazy {
        InputStreamReader(LegacyMeshes::class.java.getResourceAsStream("/assets/eln/model_index.json")!!).use {
            Gson().fromJson(it,object : TypeToken<Map<String,String>>() {}.type)
        }
    }
    fun clear() { cache.clear();ShaftVisuals.clear() }
    private fun lines(path: String) = Minecraft.getInstance().resourceManager
        .getResource(ResourceLocation("eln",path)).orElseThrow().open().bufferedReader().use { it.readLines() }
    internal fun model(name: String): LegacyModel? {
        if(name.isEmpty())return null
        if(!cache.containsKey(name))cache[name]=try { load(name) } catch(e: Exception) {
            Eln.LOGGER.error("Unable to read original OBJ model {}",name,e);null
        }
        return cache[name]
    }
    private fun load(name: String): LegacyModel? {
        val path=paths[name] ?: return null
        val folder=path.substringBeforeLast('/')
        val vertices=mutableListOf<MeshPoint>();val uv=mutableListOf<MeshUV>();val normals=mutableListOf<MeshPoint>()
        val materials=mutableMapOf<String,ResourceLocation>();var texture=white;var group=""
        val groups=linkedMapOf<String,MutableList<MeshFace>>()
        fun index(s: String,size: Int): Int { val n=s.toInt();return if(n<0)size+n else n-1 }
        for(line in lines(path)) {
            val t=line.trim().split(Regex("\\s+"))
            when(t[0]) {
                "o","g" -> group=t.drop(1).joinToString(" ")
                "mtllib" -> {
                    var material=""
                    for(mtl in lines(folder+"/"+t.drop(1).joinToString(" "))) {
                        val w=mtl.trim().split(Regex("\\s+"),2)
                        if(w.size<2)continue
                        if(w[0]=="newmtl")material=w[1]
                        if(w[0]=="map_Kd") {
                            val relative=java.nio.file.Path.of(folder).resolve(w[1]).normalize().toString().replace('\\','/')
                            if(!relative.startsWith(".."))materials[material]=ResourceLocation("eln",relative)
                        }
                    }
                }
                "usemtl" -> texture=materials[t.drop(1).joinToString(" ")] ?: white
                "v" -> vertices.add(MeshPoint(t[1].toFloat(),t[2].toFloat(),t[3].toFloat()))
                "vt" -> uv.add(MeshUV(t[1].toFloat(),1-t[2].toFloat()))
                "vn" -> normals.add(MeshPoint(t[1].toFloat(),t[2].toFloat(),t[3].toFloat()))
                "f" -> {
                    if(t.size<4 || group.startsWith("Minecraft_") || group in setOf("Cube","Cadre_Cube"))continue
                    val refs=t.drop(1).map { it.split('/') }
                    val p=refs.map { vertices[index(it[0],vertices.size)] }
                    val tex=refs.map { if(it.size>1 && it[1].isNotEmpty())uv[index(it[1],uv.size)] else MeshUV(0f,0f) }
                    val a=p[1];val b=p[0];val c=p[2]
                    val ax=a.x-b.x;val ay=a.y-b.y;val az=a.z-b.z
                    val bx=c.x-b.x;val by=c.y-b.y;val bz=c.z-b.z
                    val nx=ay*bz-az*by;val ny=az*bx-ax*bz;val nz=ax*by-ay*bx
                    val length=sqrt(nx*nx+ny*ny+nz*nz).coerceAtLeast(1e-9f)
                    val normal=MeshPoint(nx/length,ny/length,nz/length)
                    val vn=refs.map { if(it.size>2 && it[2].isNotEmpty())normals[index(it[2],normals.size)] else normal }
                    val out=groups.getOrPut(group) { mutableListOf() }
                    if(p.size==4)out.add(MeshFace(p,tex,vn,texture))
                    else for(i in 1 until p.size-1) {
                        val indices=listOf(0,i,i+1,i+1)
                        out.add(MeshFace(indices.map { p[it] },indices.map { tex[it] },indices.map { vn[it] },texture))
                    }
                }
            }
        }
        val metadata=mutableMapOf<String,MutableMap<String,Float>>();val textureNames=hashMapOf<String,ResourceLocation>();group=""
        val meta=path.substringBeforeLast('.')+".txt"
        if(Minecraft.getInstance().resourceManager.getResource(ResourceLocation("eln",meta)).isPresent)
            for(line in lines(meta)) {
                val t=line.trim().split(Regex("\\s+"))
                if(t.size==2 && t[0]=="o")group=t[1]
                if(t.size==3 && t[0]=="f")t[2].toFloatOrNull()?.let { metadata.getOrPut(group) { mutableMapOf() }[t[1]]=it }
                if(t.size==3 && t[0]=="s" && t[1] in setOf("tOn","tOff","onTexture","offTexture"))textureNames[t[1]]=ResourceLocation("eln",folder+"/"+t[2].lowercase())
            }
        return LegacyModel(groups,metadata,textureNames)
    }
    internal fun draw(faces: List<MeshFace>,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int,
        color: Int=0xFFFFFF,uOffset: Float=0f,vOffset: Float=0f,texture: ResourceLocation?=null,translucent: Boolean=false,alpha: Int=255) {
        for(face in faces) {
            val tex=texture ?: face.texture
            val consumer=buffers.getBuffer(if(translucent)RenderType.entityTranslucent(tex) else RenderType.entityCutoutNoCull(tex))
            for(i in 0..3) {
                val p=face.vertices[i];val n=face.normals[i];val uv=face.uv[i]
                consumer.vertex(pose.last().pose(),p.x,p.y,p.z)
                    .color((color shr 16) and 255,(color shr 8) and 255,color and 255,alpha.coerceIn(0,255))
                    .uv(uv.u+uOffset,uv.v+vOffset).overlayCoords(overlay).uv2(light)
                    .normal(pose.last().normal(),n.x,n.y,n.z).endVertex()
            }
        }
    }
    internal fun box(x1: Float,y1: Float,z1: Float,x2: Float,y2: Float,z2: Float,texture: ResourceLocation,
        pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int,cable: Boolean=false,cap: Boolean=false,color: Int=0xFFFFFF) {
        val p=listOf(MeshPoint(x1,y1,z1),MeshPoint(x2,y1,z1),MeshPoint(x2,y1,z2),MeshPoint(x1,y1,z2),
            MeshPoint(x1,y2,z1),MeshPoint(x2,y2,z1),MeshPoint(x2,y2,z2),MeshPoint(x1,y2,z2))
        val faces=listOf(listOf(0,3,2,1) to MeshPoint(0f,-1f,0f),listOf(4,5,6,7) to MeshPoint(0f,1f,0f),
            listOf(0,1,5,4) to MeshPoint(0f,0f,-1f),listOf(3,7,6,2) to MeshPoint(0f,0f,1f),
            listOf(0,4,7,3) to MeshPoint(-1f,0f,0f),listOf(1,2,6,5) to MeshPoint(1f,0f,0f))
        for((indices,normal) in faces) {
            val points=indices.map { p[it] }
            val coordinates=if(!cable)listOf(MeshUV(0f,0f),MeshUV(1f,0f),MeshUV(1f,1f),MeshUV(0f,1f)) else points.map { point ->
                // The original 32x16 atlas has the cable strip at U=.25 and junction cap at U=.75.
                val alongX=x2-x1>=z2-z1
                val cross=if(alongX)point.z-.5f else point.x-.5f
                val sideNormal=if(alongX)normal.z else normal.x
                val endNormal=if(alongX)normal.x else normal.z
                val along=if(alongX)point.x else point.z
                MeshUV((if(cap).75f else .25f)+(cross+sideNormal*(y2-point.y))*.5f,along+endNormal*(y2-point.y))
            }
            draw(listOf(MeshFace(points,coordinates,List(4) { normal },texture)),pose,buffers,light,overlay,color)
        }
    }
}
