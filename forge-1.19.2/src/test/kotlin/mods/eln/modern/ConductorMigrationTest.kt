package mods.eln.modern

import net.minecraft.core.Direction
import org.junit.Assert.*
import org.junit.Test

class ConductorMigrationTest {
    @Test fun oldDefaultResistanceMigratesToAuditedOriginalValue() {
        val part=PartState(PartDefinition.byId.getValue("low_voltage_cable"),Direction.DOWN)
        val legacy=part.save();legacy.remove("componentDataVersion");legacy.putDouble("resistance",.0001)
        val restored=PartState.load(legacy)!!
        assertEquals(.0125,restored.resistance,1e-12)
        assertEquals(.0125,PartState.load(restored.save())!!.resistance,1e-12)
    }
    @Test fun customWireResistanceAndFaultHistorySurviveReload() {
        val part=PartState(PartDefinition.byId.getValue("low_voltage_cable"),Direction.DOWN)
        val legacy=part.save();legacy.remove("componentDataVersion");legacy.putDouble("resistance",.02)
        legacy.putDouble("voltageFault",4.5);legacy.putBoolean("wasOvervoltage",true)
        val restored=PartState.load(legacy)!!
        assertEquals(.02,restored.resistance,1e-12);assertEquals(4.5,restored.voltageFault,0.0);assertTrue(restored.wasOvervoltage)
    }
}
