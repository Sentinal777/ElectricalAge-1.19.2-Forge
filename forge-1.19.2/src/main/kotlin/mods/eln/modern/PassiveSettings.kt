package mods.eln.modern

import mods.eln.misc.SeriesFunction
import mods.eln.sim.mna.misc.MnaConst
import kotlin.math.roundToInt

data class PassiveSpec(val kind: String,val temperatureCoefficient: Double=0.0,val controlled: Boolean=false,val large: Boolean=false)

/** Hardware calibration from the pinned resistor and six-node capacitor/inductor descriptors. */
object PassiveSettings {
    private val resistors=SeriesFunction.newE12(-2.0)
    private val storage=SeriesFunction.newE6(-1.0)
    fun resistance(coalDust: Int,coefficient: Double,deltaCelsius: Double,control: Double?=null): Double {
        var value=maxOf(MnaConst.noImpedance,resistors.getValue(coalDust.coerceIn(0,64).toDouble())*(1+coefficient*deltaCelsius))
        if(control!=null)value*=(control.coerceIn(0.0,1.0)+.01)/1.01
        return value
    }
    fun capacitorVoltage(dielectrics: Int)=if(dielectrics<=0)10000.0 else dielectrics.coerceAtMost(20)*50.0
    fun capacitance(redstone: Int,dielectrics: Int): Double {
        if(redstone<=0 || dielectrics<=0)return 1e-6
        val ratio=capacitorVoltage(dielectrics)/50
        return storage.getValue((redstone.coerceAtMost(13)-1)/(ratio*ratio))
    }
    fun inductance(windings: Int)=if(windings<=0)0.0 else storage.getValue((windings.coerceAtMost(19)-1).toDouble())
    fun controlledInductance(control: Double)=inductance((control.coerceIn(0.0,1.0)*18).roundToInt()+1)
    fun coreResistance(multiplier: Double?)=if(multiplier==null)MnaConst.highImpedance else .0125*multiplier
}
