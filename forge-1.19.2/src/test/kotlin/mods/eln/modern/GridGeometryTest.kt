package mods.eln.modern

import mods.eln.modern.grid.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.Vec3
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class GridGeometryTest {
    @Test fun catenaryRetainsExactDifferentHeightEndpointsAndSlack() {
        val a=Vec3(0.0,10.0,0.0);val b=Vec3(30.0,14.0,5.0);val points=GridGeometry.catenary(a,b)
        assertEquals(a,points.first());assertEquals(b,points.last())
        assertTrue(points.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
        assertTrue(points[8].y<12.0);assertTrue(points.zipWithNext().sumOf { (x,y) -> x.distanceTo(y) }>a.distanceTo(b))
    }
    @Test fun verticalWireHasNoCatenarySingularity() {
        val a=Vec3(2.0,1.0,2.0);val b=Vec3(2.0,8.0,2.0)
        assertEquals(listOf(a,b),GridGeometry.catenary(a,b))
    }
    @Test fun savedWireOwnsItsCableCostOnceAndKeepsTerminalSides() {
        val w=GridWire(UUID.randomUUID(),GridEnd(BlockPos(1,2,3),UUID.randomUUID().toString(),Direction.NORTH),
            GridEnd(BlockPos(50,8,3),UUID.randomUUID().toString(),Direction.SOUTH),"high_voltage_cable",50,50)
        val data=GridData();data.wires[w.id]=w
        data.brokenOwners[w.a.pos]=w.a.nodeId
        val restored=GridData.load(data.save(CompoundTag()))
        assertEquals(listOf(w),restored.wires.values.toList());assertEquals(50,restored.wires.values.sumOf { it.paidLength })
        assertEquals(data.brokenOwners,restored.brokenOwners)
    }
    @Test fun gridPortsFollowRotationAndOriginalKinds() {
        val p=PartState(PartDefinition.byId.getValue("grid_dc_dc_converter"),Direction.DOWN)
        assertFalse(GridNetwork.sideValid(p,Direction.EAST));assertTrue(GridNetwork.sideValid(p,Direction.NORTH))
        p.rotation=1;assertTrue(GridNetwork.sideValid(p,Direction.EAST));assertFalse(GridNetwork.sideValid(p,Direction.NORTH))
        val pole=PartState(PartDefinition.byId.getValue("utility_pole"),Direction.DOWN)
        assertTrue(GridNetwork.sideValid(pole,Direction.UP));assertEquals(3,GridMultiblock.cells(BlockPos.ZERO,pole).size)
    }
}
