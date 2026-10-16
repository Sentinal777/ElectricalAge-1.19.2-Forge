package mods.eln.modern

import org.junit.Assert.*
import org.junit.Test

class DisplaySettingsTest {
    @Test fun originalRangeMappingAndTruncation() {
        assertEquals(1500,DisplaySettings.reading(2.5,"1000,2000"))
        assertEquals(199,DisplaySettings.reading(.999,"0,1000"))
        assertEquals(500,DisplaySettings.reading(2.5,"1000,0"))
    }
    @Test fun displayBoundsAndInvalidInput() {
        assertEquals(9999,DisplaySettings.reading(5.0,"0,99999"))
        assertEquals(0,DisplaySettings.reading(0.0,"-50,50"))
        assertNull(DisplaySettings.range("NaN,5"));assertNull(DisplaySettings.range("1,2,3"))
        assertEquals(500,DisplaySettings.reading(2.5,"A"))
    }
}
