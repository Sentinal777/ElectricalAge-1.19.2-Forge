package mods.eln.sim.mna

import mods.eln.disableLog4jJmx
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.misc.ISubSystemProcessI
import mods.eln.sim.mna.state.VoltageState
import kotlin.test.Test
import kotlin.test.assertEquals

class LinearRhsTest {
    @Test fun sensitivityMatchesAnalyticalNetworkAfterResistanceChangesAndRetainsUnknownCallbacks() {
        disableLog4jJmx()
        val system=SubSystem(null,.1)
        val mains=VoltageState();val sensed=VoltageState();val port=VoltageState()
        listOf(mains,sensed,port).forEach(system::addState)
        val supply=VoltageSource("mains",mains,null).setVoltage(50.0)
        val probe=VoltageSource("probe",port,null).setVoltage(8.25)
        val series=Resistor(mains,sensed).setResistance(2.0)
        listOf(supply,probe,series,Resistor(sensed,null).setResistance(3.0),Resistor(sensed,port).setResistance(.25)).forEach(system::addComponent)
        for(resistance in listOf(2.0,5.0,.001,1000.0)) {
            series.resistance=resistance;sensed.state=3.2
            val measured=system.getTh(sensed,probe)
            assertEquals(50*3/(resistance+3),measured.voltage,1e-8)
            assertEquals(resistance*3/(resistance+3)+.25,measured.resistance,1e-10)
            assertEquals(8.25,probe.voltage,0.0)
        }
        var calls=0
        system.addProcess(ISubSystemProcessI { calls++;it.addToI(sensed,.1*calls) })
        system.getTh(sensed,probe)
        assertEquals(2,calls,"Unknown/stateful stamps must retain both original callbacks after a cached linear solve")
    }
}
