package mods.eln.misc

import net.minecraft.nbt.CompoundTag
import kotlin.test.Test
import kotlin.test.assertEquals

class WindProcessTest {
    @Test fun preservesOriginalHeightClampInsteadOfSilentlyReplacingTheBaselineCurve() {
        val wind=WindProcess();wind.windHit=5.0
        assertEquals(50.0,wind.getWind(50));assertEquals(100.0,wind.getWind(200));assertEquals(-1.5,wind.getWind(-30))
        val curve=FunctionTable(doubleArrayOf(0.0,.1,.3,.5,.8,1.0,1.1,1.15,1.2).map { it*160 }.toDoubleArray(),16.0)
        assertEquals(160.0,curve.getValue(10.0));assertEquals(528.0,curve.getValue(100.0),1e-9)
    }
    @Test fun originalAtmosphereAndOldFilteredSpellingSurviveNbt() {
        val wind=WindProcess();wind.windHit=7.5;wind.windTarget=9.0;wind.windVariation=-.125;wind.windTargetFiltered.setValue(6.25f)
        val saved=CompoundTag();wind.writeToNBT(saved,"");val restored=WindProcess();restored.readFromNBT(saved,"")
        assertEquals(7.5,restored.windHit);assertEquals(9.0,restored.windTarget);assertEquals(-.125,restored.windVariation);assertEquals(6.25f,restored.windTargetFiltered.get())
        saved.remove("windTargetFiltered");saved.putFloat("windTargetFiltred",4.5f);restored.readFromNBT(saved,"");assertEquals(4.5f,restored.windTargetFiltered.get())
    }
}
