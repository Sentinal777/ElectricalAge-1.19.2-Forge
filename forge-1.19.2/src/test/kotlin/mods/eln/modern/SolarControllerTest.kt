package mods.eln.modern

import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.process.PowerSourceBipole
import mods.eln.sim.mna.state.VoltageState
import kotlin.test.*

class SolarControllerTest {
    @Test fun theveninProbePreservesAnUnflushedSourceSetting() {
        val root=RootSystem(.05,1);val node=VoltageState();val source=VoltageSource("probe",node,null).setVoltage(5.0)
        root.addState(node);root.addComponent(source);root.addComponent(Resistor(node,null).setResistance(10.0));root.step()
        source.voltage=12.0
        node.subSystem.getTh(node,source)
        assertEquals(12.0,source.voltage)
        root.step();assertEquals(12.0,node.voltage,1e-10)
    }
    @Test fun floatingBipoleEnforcesVoltagePowerAndCurrentWithoutAdvancingExtraPhysicalTime() {
        val root=RootSystem(.05,50)
        val a=VoltageState();val b=VoltageState();root.addState(a);root.addState(b)
        val sa=VoltageSource("positive",a,null);val sb=VoltageSource("negative",b,null)
        root.addComponent(sa);root.addComponent(sb)
        val load=Resistor(a,b).setResistance(7.5);root.addComponent(load)
        root.addComponent(Resistor(a,null).setResistance(1e12));root.addComponent(Resistor(b,null).setResistance(1e12))
        val controller=PowerSourceBipole(a,b,sa,sb)
        controller.setMaximumVoltage(14.75);controller.setMaximumCurrent(65/14.75*1.5);controller.setPower(65.0);root.addProcess(controller)
        repeat(4) { root.step() };assertEquals(14.75,a.voltage-b.voltage,1e-6)
        controller.setPower(20.0);repeat(4) { root.step() };assertEquals(20.0,load.power,1e-5)
        controller.setMaximumCurrent(.5);controller.setPower(65.0);repeat(4) { root.step() };assertEquals(.5,load.current,1e-6)
        controller.setPower(0.0);repeat(4) { root.step() };assertEquals(0.0,load.current,1e-6)
    }
}
