package mods.eln.modern

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import kotlin.test.*

class NativeStateTest {
    @Test fun faceEdgesMeetAcrossAllCornerTypes() {
        val p=BlockPos(-5,32,8)
        for(face in Direction.values()) for(edge in FaceBasis.edges(face,0)) {
            val origin=EdgeKey.of(p,face,edge)
            assertEquals(origin,EdgeKey.of(p.relative(edge),face,edge.opposite))
            assertEquals(origin,EdgeKey.of(p,edge,face))
            assertEquals(origin,EdgeKey.of(p.relative(edge).relative(face),edge.opposite,face.opposite))
        }
    }
    @Test fun rotationsAreBijectionsInEveryFacePlane() {
        for(face in Direction.values()) for(rotation in -8..8) {
            val directions=FaceBasis.edges(face,rotation)
            assertEquals(4,directions.toSet().size)
            assertTrue(directions.none { it.axis==face.axis })
            assertEquals(directions[0].opposite,directions[2])
        }
    }
    @Test fun persistedBatteryRetainsEnergyChargeAndSettings() {
        val part=PartState(PartDefinition.byId.getValue("cost_oriented_battery"),Direction.DOWN)
        part.charge=.412;part.energy=1987.2;part.temperature=51.4;part.channel="plant-a";part.rotation=3
        part.progress=4100.0;part.recipeId="eln:grind_iron"
        val restored=PartState.load(part.save())!!
        assertEquals(part.charge,restored.charge);assertEquals(part.energy,restored.energy)
        assertEquals(part.temperature,restored.temperature);assertEquals(part.rotation,restored.rotation)
        assertEquals(part.progress,restored.progress);assertEquals(part.recipeId,restored.recipeId)
    }
    @Test fun malformedNumericStateIsRejectedOrClamped() {
        val t=PartState(PartDefinition.byId.getValue("power_capacitor"),Direction.UP).save()
        t.putDouble("resistance",Double.NaN);t.putDouble("charge",Double.POSITIVE_INFINITY)
        t.putInt("rotation",-1)
        val p=PartState.load(t)!!
        assertTrue(p.resistance.isFinite() && p.resistance>0);assertTrue(p.charge.isFinite());assertEquals(3,p.rotation)
        t.putInt("face",99);assertNull(PartState.load(t))
        assertNull(PartState.load(CompoundTag()))
    }
}
