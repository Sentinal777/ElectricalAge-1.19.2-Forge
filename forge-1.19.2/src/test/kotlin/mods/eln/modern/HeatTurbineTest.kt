package mods.eln.modern

import mods.eln.modern.thermal.HeatTurbineRuntime
import org.junit.Assert.*
import org.junit.Test

class HeatTurbineTest {
    @Test fun efficiencyUsesAbsoluteKelvinAndRetainsTheOriginalMinimum() {
        assertEquals(.5,HeatTurbineRuntime.efficiency(300.0,0.0,300.0),1e-12)
        assertEquals(.05,HeatTurbineRuntime.efficiency(0.0,0.0,300.0),1e-12)
        assertEquals(.05,HeatTurbineRuntime.efficiency(-400.0,-500.0,300.0),1e-12)
    }
    @Test fun originalTurbineRegistrationsKeepTheirThermalAndElectricalCalibration() {
        val low=PartDefinition.byId.getValue("50v_turbine");val high=PartDefinition.byId.getValue("200v_turbine")
        assertEquals(257,low.legacyId);assertEquals(264,high.legacyId)
        assertEquals(25.0,low.thermal!!.capacity,0.0);assertEquals(50.0,high.thermal!!.capacity,0.0)
        assertEquals(10.0,low.thermal!!.rp,0.0);assertEquals(7.0,high.thermal!!.rp,0.0)
        assertEquals(.00125,low.resistance,1e-12);assertEquals(.005,high.resistance,1e-12)
        assertEquals(500.0,low.heatTurbine!!.wattsPerVolt,0.0);assertEquals(250.0,high.heatTurbine!!.wattsPerVolt,0.0)
    }
}
