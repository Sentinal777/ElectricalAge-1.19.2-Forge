package mods.eln.modern

import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Capacitor
import mods.eln.sim.mna.component.CurrentSource
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.state.VoltageState
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedCurrentRegressionTest {
    @Test fun independentCurrentSourcesSumAndRemovalRebuildsTheCircuit() {
        val root=RootSystem(.1,1);val node=VoltageState()
        val two=CurrentSource("2A",node,null).setCurrent(2.0)
        val three=CurrentSource("3A",node,null).setCurrent(3.0)
        root.addState(node);root.addComponent(two);root.addComponent(three)
        root.addComponent(Resistor(node,null).setResistance(10.0))
        root.step();assertEquals(50.0,node.voltage,1e-8)
        root.removeComponent(two);two.breakConnection()
        root.step();assertEquals(30.0,node.voltage,1e-8)
    }
    @Test fun constantCurrentChargesCapacitorAtIOverC() {
        val root=RootSystem(.1,1);val node=VoltageState()
        root.addState(node);root.addComponent(CurrentSource("2A",node,null).setCurrent(2.0))
        root.addComponent(Capacitor(node,null).apply { coulombs=2.0 })
        repeat(10) { root.step() }
        assertEquals(1.0,node.voltage,1e-8)
    }
}
