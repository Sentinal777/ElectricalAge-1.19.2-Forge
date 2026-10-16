package mods.eln.modern

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.*

/** Common geometry for selection, placement and rendering. Values are original model block units. */
object PartGeometry {
    private data class CableShapeKey(val face: net.minecraft.core.Direction,val rotation: Int,val version: Int,
        val mask: Int,val width: Double,val height: Double)
    private val cableShapes=java.util.concurrent.ConcurrentHashMap<CableShapeKey,VoxelShape>()
    fun localToBlock(part: PartState,x: Double,y: Double,z: Double): Vec3 {
        val edges=FaceBasis.edges(part);val u=edges[0];val v=edges[1];val f=part.face
        return Vec3(.5+f.stepX*(.5-y)+(x-.5)*u.stepX+(z-.5)*v.stepX,
            .5+f.stepY*(.5-y)+(x-.5)*u.stepY+(z-.5)*v.stepY,
            .5+f.stepZ*(.5-y)+(x-.5)*u.stepZ+(z-.5)*v.stepZ)
    }
    fun transform(part: PartState,box: AABB): AABB {
        val a=localToBlock(part,box.minX,box.minY,box.minZ)
        val b=localToBlock(part,box.maxX,box.maxY,box.maxZ)
        return AABB(min(a.x,b.x),min(a.y,b.y),min(a.z,b.z),max(a.x,b.x),max(a.y,b.y),max(a.z,b.z))
    }
    fun localBody(part: PartState): AABB {
        val spec=part.definition.visual
        if(part.definition.behavior in setOf(Behavior.CABLE,Behavior.THERMAL_CABLE,Behavior.SIGNAL_BUS)) {
            val cap=part.connectedMask !in setOf(5,10)
            val w=spec.cableWidth/2+if(cap)1.0/16 else 0.0
            return AABB(.5-w,0.0,.5-w,.5+w,spec.cableHeight+if(cap)1.0/16 else 0.0,.5+w)
        }
        val b=spec.bounds
        return AABB(b[0],b[1],b[2],b[3],b[4],b[5])
    }
    fun body(part: PartState): AABB = transform(part,localBody(part))
    fun localConnections(part: PartState): List<AABB> {
        val d=part.definition.visual
        return (0..3).filter { part.connectedMask and (1 shl it)!=0 }.map { edge ->
            val contact=part.definition.contact
            val visual=if(part.definition.behavior==Behavior.ENERGY_METER)EnergyMeters.cable(part)?.visual ?: d else if(part.definition.powerAntenna!=null)PowerAntennas.cable(part,edge).visual else if(part.originalBreaker)ProtectionSettings.cable(part)?.visual ?: d else if(contact==null)d else PartDefinition.byId.getValue(if(contact.relay && edge==0)"signal_cable" else contact.cable).visual
            val w=visual.cableWidth/2;val h=visual.cableHeight
            when(edge) {
            0 -> AABB(.5,0.001,.5-w,1.0,h,.5+w)
            1 -> AABB(.5-w,0.001,.5,.5+w,h,1.0)
            2 -> AABB(0.0,0.001,.5-w,.5,h,.5+w)
            else -> AABB(.5-w,0.001,0.0,.5+w,h,.5)
        } }
    }
    fun boxes(part: PartState): List<AABB> = listOf(body(part))+localConnections(part).map { transform(part,it) }
    private fun buildShape(part: PartState): VoxelShape = boxes(part).fold(Shapes.empty()) { shape,box -> Shapes.or(shape,Shapes.create(box)) }
    fun shape(part: PartState): VoxelShape {
        val d=part.definition
        if(d.behavior !in setOf(Behavior.CABLE,Behavior.THERMAL_CABLE,Behavior.SIGNAL_BUS) || d.contact!=null || d.powerAntenna!=null)return buildShape(part)
        // Cache complete cable geometry, never an empty carrier before its BE data arrives.
        val key=CableShapeKey(part.face,Math.floorMod(part.rotation,4),if(part.orientationVersion<2)1 else 2,
            part.connectedMask,d.visual.cableWidth,d.visual.cableHeight)
        cableShapes[key]?.let { return it }
        // A snapshot keeps the published key and built shape consistent across client/server queries.
        val snapshot=PartState(d,key.face).apply { rotation=key.rotation;orientationVersion=key.version;connectedMask=key.mask }
        val shape=buildShape(snapshot)
        if(cableShapes.size>=4096)cableShapes.clear()
        return cableShapes.putIfAbsent(key,shape) ?: shape
    }
    fun overlapsBody(a: PartState,b: PartState) = body(a).deflate(0.002).intersects(body(b).deflate(0.002))
    fun distanceTo(part: PartState,point: Vec3): Double = boxes(part).minOf { box ->
        val dx=max(max(box.minX-point.x,0.0),point.x-box.maxX)
        val dy=max(max(box.minY-point.y,0.0),point.y-box.maxY)
        val dz=max(max(box.minZ-point.z,0.0),point.z-box.maxZ)
        dx*dx+dy*dy+dz*dz
    }
}
