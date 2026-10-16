package mods.eln.modern

import org.junit.Assert.*
import org.junit.Test

class EnergyConservationTest {
    @Test fun powerSourceDoesNotStampItsVoltageTwice() {
        val root=mods.eln.sim.mna.RootSystem(.005,1)
        val state=mods.eln.sim.mna.state.VoltageState()
        root.addState(state)
        val source=mods.eln.sim.mna.component.PowerSource("regulated",state)
        source.setMaximumVoltage(50.0);source.setMaximumCurrent(20.0);source.power=1000.0
        root.addComponent(source);root.addComponent(mods.eln.sim.mna.component.Resistor(state,null).setResistance(100.0))
        repeat(10) { root.step() }
        assertEquals(50.0,state.state,1e-9);assertEquals(.5,source.current,1e-9)
        assertEquals(25.0,source.effectivePower,1e-9)
    }
    @Test fun fractionalProductionSurvivesThousandsOfTicks() {
        val b=EnergyBuffer();repeat(10000) { b.add(.025) }
        assertEquals(250.0,b.energy,1e-8);assertTrue(b.stored in 249..250)
    }
    @Test fun simulationAndInvalidRequestsCannotCreateEnergy() {
        val b=EnergyBuffer(100);b.add(10.5)
        assertEquals(0.0,b.remove(-1.0),0.0);assertEquals(0.0,b.add(Double.NaN),0.0)
        assertEquals(10.5,b.remove(100.0),0.0);assertEquals(0.0,b.energy,0.0)
    }
    @Test fun conversionRoundTripLosesEnergy() {
        val export=EnergyBuffer();export.add(1000*.95)
        val restored=export.remove(export.energy)*.95
        assertEquals(902.5,restored,1e-9);assertTrue(restored<1000)
    }
    @Test fun shaftUsesOriginalEnergyEquationAndBoundedConsumption() {
        val s=ShaftPhysics(5.0,5000.0)
        assertEquals(200.0,s.speed,1e-9)
        assertEquals(5000.0,s.consume(8000.0),1e-9);assertEquals(0.0,s.speed,0.0)
    }
    @Test fun splitAndReloadSharesPreserveStoredEnergy() {
        val total=1234.5;val masses=listOf(.5,5.0,50.0)
        val shares=masses.map { total*it/masses.sum() }
        assertEquals(total,shares.sum(),1e-9)
        masses.zip(shares).forEach { (mass,share) -> assertEquals(ShaftPhysics(masses.sum(),total).speed,ShaftPhysics(mass,share).speed,1e-9) }
    }
}
