package mods.eln.modern

import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalGateOutputProcess
import org.junit.Assert.*
import org.junit.Test

class SignalOutputTest {
    @Test fun originalGateStageLimitsShortCircuitCurrentAndRecovers() {
        val root=RootSystem(.005,1);val output=ElectricalLoad();root.addState(output)
        val gate=NbtElectricalGateOutputProcess("gate",output);gate.coulombs=.0001;gate.setVoltageSafe(5.0)
        val load=Resistor(output,null).setResistance(1e-6)
        root.addComponent(gate);root.addComponent(load);repeat(20) { root.step() }
        assertEquals(.1,load.current,1e-6);assertTrue(output.voltage<1e-6)
        load.resistance=1000000.0;repeat(20) { root.step() }
        assertEquals(5.0*1000000/(1000000+50),output.voltage,1e-8)
    }
    @Test fun sensorRangeHasSignedReversedAndSaturatedBehavior() {
        assertEquals(1.25,SensorSettings.output(-.5,"-1,1"),1e-12)
        assertEquals(3.75,SensorSettings.output(-.5,"1,-1"),1e-12)
        assertEquals(0.0,SensorSettings.output(-20.0,"0,50"),0.0)
        assertEquals(5.0,SensorSettings.output(500.0,"0,50"),0.0)
        assertNull(SensorSettings.range("5,5"));assertNull(SensorSettings.range("NaN,1"))
    }
}
