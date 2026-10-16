package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import mods.eln.modern.*
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.Direction
import java.util.IdentityHashMap

/** Original hub caps/stands and mesh-centred rotor axes. */
internal object ShaftVisuals {
    private val centers=IdentityHashMap<LegacyModel,MutableMap<String,MeshPoint>>()
    fun clear()=centers.clear()
    fun pivot(model: LegacyModel,group: String): MeshPoint? {
        centers[model]?.get(group)?.let { return it }
        val vertices=model.groups[group]?.flatMap { it.vertices }?.takeIf { it.isNotEmpty() } ?: return null
        return centers.getOrPut(model) { hashMapOf() }.getOrPut(group) {
            MeshPoint((vertices.minOf { it.x }+vertices.maxOf { it.x })/2,(vertices.minOf { it.y }+vertices.maxOf { it.y })/2,(vertices.minOf { it.z }+vertices.maxOf { it.z })/2)
        }
    }
    fun hub(p: PartState,model: LegacyModel,angle: Float,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        val mask=p.gridState.getInt("shaftConnectedSides");val standing=p.gridState.getInt("shaftStandingSides")
        val connected=Direction.values().filter { mask and (1 shl it.get3DDataValue())!=0 }
        val cowl=standing!=0 || !(connected.size==1 || connected.size==2 && connected[0].opposite==connected[1])
        if(cowl)model.groups["Cowl"]?.let { LegacyMeshes.draw(it,pose,buffers,light,overlay) }
        val pivot=pivot(model,"Shaft")
        for(direction in Direction.values()) {
            val attached=direction in connected;val opaque=standing and (1 shl direction.get3DDataValue())!=0
            val group=if(attached)"Shaft" else if(opaque)"Stand" else if(cowl)"Cap" else continue
            val faces=model.groups[group] ?: continue
            pose.pushPose()
            when(FaceBasis.edges(p).indexOf(direction)) {
                1 -> pose.mulPose(Vector3f.YP.rotationDegrees(-90f))
                2 -> pose.mulPose(Vector3f.YP.rotationDegrees(180f))
                3 -> pose.mulPose(Vector3f.YP.rotationDegrees(90f))
                -1 -> pose.mulPose(Vector3f.ZP.rotationDegrees(if(direction==p.face)-90f else 90f))
            }
            if(attached && pivot!=null) {
                pose.translate(pivot.x.toDouble(),pivot.y.toDouble(),pivot.z.toDouble())
                val sign=if(direction in setOf(Direction.EAST,Direction.NORTH,Direction.UP))1 else -1
                pose.mulPose(Vector3f.XP.rotationDegrees(sign*angle))
                pose.translate(-pivot.x.toDouble(),-pivot.y.toDouble(),-pivot.z.toDouble())
            }
            LegacyMeshes.draw(faces,pose,buffers,light,overlay);pose.popPose()
        }
    }
}
