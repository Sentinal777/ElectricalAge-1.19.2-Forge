package mods.eln.sim

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.exp

class ThermalIntegratorTest {
    @Test fun isolatedConnectionConservesHeatAndApproachesCapacityWeightedTemperature() {
        val hot=ThermalLoad(100.0,Double.POSITIVE_INFINITY,.1,10.0)
        val cold=ThermalLoad(0.0,Double.POSITIVE_INFINITY,.1,30.0)
        val connection=ThermalConnection(hot,cold)
        repeat(4000) { ThermalIntegrator.step(.0025,listOf(hot,cold),listOf(connection)) }
        assertEquals(1000.0,hot.temperatureCelsius*10+cold.temperatureCelsius*30,1e-8)
        assertEquals(25.0,hot.temperatureCelsius, .11)
        assertEquals(25.0,cold.temperatureCelsius,.04)
    }
    @Test fun ambientCoolingMatchesAnalyticalExponential() {
        val load=ThermalLoad(120.0,2.0,.1,20.0)
        repeat(4000) { ThermalIntegrator.step(.0025,listOf(load),emptyList()) }
        assertEquals(120*exp(-10/40.0),load.temperatureCelsius,.002)
        assertTrue(load.Pc<0)
    }
    @Test fun powerSourceAddsExactlyItsFiniteEnergyWhenNoHeatEscapes() {
        val load=ThermalLoad(0.0,Double.POSITIVE_INFINITY,1.0,50.0)
        val furnace=FurnaceProcess(load).apply { nominalCombustibleEnergy=1000.0;nominalPower=100.0;combustibleEnergy=1000.0 }
        repeat(4000) { ThermalIntegrator.step(.0025,listOf(load),emptyList(),listOf(furnace)) }
        assertEquals(1000.0,load.temperatureCelsius*50+furnace.combustibleEnergy,1e-8)
        assertTrue(furnace.combustibleEnergy in 367.0..369.0)
    }
    @Test fun originalCopperPipeCalibrationRemainsStableAtFourHundredHertz() {
        val capacity=ThermalIntegrator.minimumCapacity(.0005,50.0)
        assertEquals(15.00015,capacity,1e-10)
        val loads=List(5) { ThermalLoad(if(it==0)500.0 else 0.0,50.0,.0005,capacity) }
        val links=(1..4).map { ThermalConnection(loads[0],loads[it]) }
        repeat(20000) { ThermalIntegrator.step(.0025,loads,links) }
        assertTrue(loads.all { it.temperatureCelsius.isFinite() && it.temperatureCelsius in 0.0..500.0 })
        assertTrue(loads.maxOf { it.temperatureCelsius }-loads.minOf { it.temperatureCelsius }<.001)
    }
}
