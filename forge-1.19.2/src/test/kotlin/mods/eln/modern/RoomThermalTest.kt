package mods.eln.modern

import mods.eln.modern.thermal.RoomThermalData
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import org.junit.Assert.*
import org.junit.Test

class RoomThermalTest {
    @Test fun roomExchangeAddsTheSameHeatThatLeavesTheMachine() {
        val data=RoomThermalData();val cells=(0..2).flatMap { x -> (0..2).flatMap { y -> (0..2).map { z -> BlockPos(x,y,z) } } }.toSet()
        val anchor=BlockPos(1,1,1);val room=data.register(cells,listOf(anchor))!!;data.activeRooms=setOf(room.id)
        val watts=data.exchange(anchor,100.0,2.0,.05)!!
        assertEquals(50.0,watts,1e-12)
        assertEquals(watts*.05,room.delta*room.heatCapacity,1e-12)
        assertEquals(1200.0*27,room.heatCapacity,0.0)
    }
    @Test fun roomStateAndThermalAnchorsSurviveSavedDataRoundTrip() {
        val data=RoomThermalData();val cells=setOf(BlockPos(-5,-20,42),BlockPos(-4,-20,42));val anchor=BlockPos(-4,-20,42)
        val room=data.register(cells,listOf(anchor))!!;room.delta=12.5
        val restored=RoomThermalData.load(data.save(CompoundTag()))
        assertEquals(12.5,restored.at(anchor)!!.delta,0.0);assertEquals(cells,restored.at(anchor)!!.cells)
        assertEquals(2400.0,restored.at(anchor)!!.heatCapacity,0.0)
        assertTrue(restored.activeRooms.isEmpty())
    }
    @Test fun openDoorsAddTheOriginalThirtyWattsPerKelvinAndBlockChangesInvalidateRooms() {
        val data=RoomThermalData();val pos=BlockPos(1,2,3);val room=data.register(setOf(pos),listOf(pos))!!
        assertEquals(.72,room.conductance,1e-12)
        room.openDoors=1;assertEquals(30.72,room.conductance,1e-12)
        data.invalidate(pos.above());assertNull(data.at(pos));assertTrue(data.rooms.isEmpty())
    }
}
