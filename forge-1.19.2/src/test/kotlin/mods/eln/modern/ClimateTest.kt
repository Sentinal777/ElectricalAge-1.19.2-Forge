package mods.eln.modern

import mods.eln.modern.thermal.Climate
import org.junit.Assert.*
import org.junit.Test

class ClimateTest {
    @Test fun originalDayCurveReachesItsDocumentedExtremesAndRepeats() {
        val profile=Climate.profile("plains")
        assertEquals(27.0,Climate.at(profile,10000,false,false).temperatureCelsius,1e-12)
        assertEquals(15.0,Climate.at(profile,23000,false,false).temperatureCelsius,1e-12)
        assertEquals(Climate.dayBlend(23999),Climate.dayBlend(-1),0.0)
        assertEquals(Climate.dayBlend(7000),Climate.dayBlend(31000),0.0)
    }
    @Test fun StormCloudsAndPrecipitationRetainTheOriginalCoolingAndHumidity() {
        val profile=Climate.profile("plains")
        val state=Climate.at(profile,10000,true,true)
        assertEquals(24.0,state.temperatureCelsius,1e-12);assertEquals(95.0,state.relativeHumidityPercent,1e-12)
        assertTrue(state.precipitationActive);assertEquals("rain",state.precipitationType)
        val snow=Climate.at(profile,10000,true,false,true)
        assertEquals(85.0,snow.relativeHumidityPercent,1e-12);assertEquals("snow",snow.precipitationType)
    }
    @Test fun RenamedBiomesKeepTheOriginalProfilesAndUnknownBiomesUsePlains() {
        assertEquals(Climate.profile("ice_plains"),Climate.profile("snowy_plains"))
        assertEquals(Climate.profile("plains"),Climate.profile("unregistered_mod_biome"))
    }
}
