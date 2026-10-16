package mods.eln.sixnode

import mods.eln.Eln
import mods.eln.misc.INBTTReady
import mods.eln.misc.LinearFunction
import mods.eln.misc.Utils
import mods.eln.solver.Equation
import mods.eln.solver.Constant
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import kotlin.math.sin

private fun tr(text: String): String = Component.translatable(text.replace(' ', '_')).string.let {
    if (it == text.replace(' ', '_')) text else it
}

abstract class AnalogFunction : INBTTReady {
    companion object {
        val inputColors = arrayOf("§c", "§a", "§9")
    }

    open val hasState = false
    abstract val inputCount: Int
    abstract val infos: String

    internal fun Double.toDigital() = if (this <= 0.2) false
    else if (this >= 0.6) true
    else Math.random() > 0.5

    internal fun Array<Double?>.toDigital(): List<Boolean?> = this.map { it?.toDigital() }

    abstract fun process(inputs: Array<Double?>, deltaTime: Double): Double

    open fun getWaila(inputs: Array<Double?>, output: Double) = mutableMapOf(
        Pair(tr("Inputs"), (1..inputCount).map { "${inputColors[it - 1]}${Utils.plotVolt("", inputs[it - 1] ?: 0.0)}" }.joinToString(" ")),
        Pair(tr("Output"), Utils.plotVolt("", output))
    )

    override fun readFromNBT(nbt: CompoundTag, str: String) {}
    override fun writeToNBT(nbt: CompoundTag, str: String) {}
}

class OpAmp : AnalogFunction() {
    override val inputCount = 2
    override val infos: String = tr("Operational Amplifier - DC coupled\nhigh-gain voltage amplifier with\ndifferential input. Can be used to\ncompare voltages or as configurable amplifier.")

    override fun process(inputs: Array<Double?>, deltaTime: Double): Double =
        10000 * ((inputs[0] ?: 0.0) - (inputs[1] ?: 0.0))
}

class PIDRegulator : AnalogFunction() {
    override val hasState = true
    override val inputCount = 2
    override val infos = tr("Proportional–integral–derivative controller. A PID\ncontroller continuously calculates an error value as\nthe difference between a desired setpoint and a measured\nprocess variable and applies a correction based on\nproportional, integral, and derivative terms.")

    internal var Kp = 1.0
    internal var Ki = 0.0
    internal var Kd = 0.0
    private val pid = Equation.Pid()

    override fun process(inputs: Array<Double?>, deltaTime: Double): Double {
        pid.setOperator(arrayOf(
            Constant((inputs[0] ?: 0.0) / Eln.SVU),
            Constant((inputs[1] ?: 0.0) / Eln.SVU),
            Constant(Kp), Constant(Ki), Constant(Kd)
        ))
        pid.process(deltaTime)
        return Eln.SVU * pid.getValue()
    }

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        Kp = nbt.getDouble("Kp")
        Ki = nbt.getDouble("Ki")
        Kd = nbt.getDouble("Kd")
        pid.readFromNBT(nbt, "pid")
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        nbt.putDouble("Kp", Kp)
        nbt.putDouble("Ki", Ki)
        nbt.putDouble("Kd", Kd)
        pid.writeToNBT(nbt, "pid")
    }

    override fun getWaila(inputs: Array<Double?>, output: Double): MutableMap<String, String> {
        val info = super.getWaila(inputs, output)
        info[tr("Params")] = "Kp = $Kp, Ki = $Ki, Kd = $Kd"
        if (Eln.config.getBooleanOrElse("ui.waila.easyMode", false)) {
            info[tr("State")] = "Si = ${pid.iStack}"
        }
        return info
    }
}


open class VoltageControlledSawtoothOscillator : AnalogFunction() {
    override val hasState = true
    override val inputCount = 1
    override val infos = tr("A voltage-controlled oscillator or VCO is\nan electronic oscillator whose oscillation\nfrequency is controlled by a voltage input.")

    private var out = 0.0

    // 0v = 0.1Hz through 5v = 10Hz
    open val hertzFunction = LinearFunction(0f, 0.1f, Eln.SVU.toFloat(), (1 / 0.05).toFloat())

    override fun process(inputs: Array<Double?>, deltaTime: Double): Double {
        val hertz = hertzFunction.getValue(inputs[0]?: 0.0)
        val halfPeriod = (1 / hertz) * 0.5
        out += deltaTime/halfPeriod
        if (out > Eln.SVU) {
            out = 0.0
        }
        return out
    }

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        out = nbt.getDouble("out")
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        nbt.putDouble("out", out)
    }
}

class VoltageControlledSineOscillator : VoltageControlledSawtoothOscillator() {
    val svu2 = Eln.SVU/2

    override val hertzFunction = LinearFunction(0f, 0.01f, Eln.SVU.toFloat(), 0.5f)

    override fun process(inputs: Array<Double?>, deltaTime: Double) =
        svu2 + svu2 * sin(Math.PI * 2 * super.process(inputs, deltaTime))
}

class Amplifier : AnalogFunction() {
    override val hasState = true
    override val inputCount = 1
    override val infos = tr("An amplifier increases the voltage\nof an input signal by a configurable\ngain and outputs that voltage.")

    internal var gain = 1.0

    override fun process(inputs: Array<Double?>, deltaTime: Double) = gain * (inputs[0] ?: 0.0)

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        gain = nbt.getDouble("gain")
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        nbt.putDouble("gain", gain)
    }

    override fun getWaila(inputs: Array<Double?>, output: Double): MutableMap<String, String> {
        val info = super.getWaila(inputs, output)
        info[tr("Gain")] = Utils.plotValue(gain)
        return info
    }
}


class VoltageControlledAmplifier : AnalogFunction() {
    override val inputCount = 2
    override val infos = tr("A voltage-controlled amplifier (VCA)\nis an electronic amplifier that varies\nits gain depending on the control voltage.")

    override fun process(inputs: Array<Double?>, deltaTime: Double) = (inputs[1] ?: 5.0) / 5.0 * (inputs[0] ?: 0.0)

    override fun getWaila(inputs: Array<Double?>, output: Double): MutableMap<String, String> {
        val info = super.getWaila(inputs, output)
        info[tr("Gain")] = Utils.plotValue((inputs[1] ?: 5.0) / 5.0)
        return info
    }
}

class SummingUnit : AnalogFunction() {
    override val hasState = true
    override val inputCount = 3
    override val infos = tr("The summing unit outputs the sum of\nthe three weighted inputs.The\ngain for each input can be configured.")

    internal val gains = arrayOf(1.0, 1.0, 1.0)

    override fun process(inputs: Array<Double?>, deltaTime: Double) =
        gains[0] * (inputs[0] ?: 0.0) + gains[1] * (inputs[1] ?: 0.0) + gains[2] * (inputs[2] ?: 0.0)

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        for (i in gains.indices) {
            gains[i] = nbt.getDouble("gain$i")
        }
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        for (i in gains.indices) {
            nbt.putDouble("gain$i", gains[i])
        }
    }

    override fun getWaila(inputs: Array<Double?>, output: Double): MutableMap<String, String> {
        val info = super.getWaila(inputs, output)
        info[tr("Gains")] = (0..2).map { "${inputColors[it]}${Utils.plotValue(gains[it])}" }.joinToString(" ")
        return info
    }
}


class SampleAndHold : AnalogFunction() {
    override val hasState = true
    override val inputCount = 2
    override val infos = tr("Samples the voltage of a varying analog signal when\nthe clock input changes from 0 to 1 and holds its\noutput voltage at a constant level until next clock pulse.\nYou can see it as an analog D-Flipflop.")
    private var clock = false
    private var value = 0.0

    override fun process(inputs: Array<Double?>, deltaTime: Double): Double {
        val clock = inputs[1] ?: 0.0 > 0.5
        if (clock && !this.clock) value = inputs[0] ?: 0.0
        this.clock = clock
        return value
    }

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        clock = nbt.getBoolean("clock")
        value = nbt.getDouble("value")
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        nbt.putBoolean("clock", clock)
        nbt.putDouble("value", value)
    }
}

class Filter: AnalogFunction() {
    override val hasState = true
    override val inputCount = 1
    override val infos = tr("Lowpass filter - Passes signals with a\nfrequency lower than a certain cutoff frequency\nand attenuates signals with frequencies higher\nthan the cutoff frequency.")

    internal var feedback = 2.0 * Math.PI * 5.0
    private var output = 0.0

    override fun process(inputs: Array<Double?>, deltaTime: Double): Double {
        output = Utils.limit(output + ((inputs[0] ?: 0.0) - output) * feedback * deltaTime, 0.0, 50.0)
        return output
    }

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        nbt.apply {
            feedback = getDouble("feedback")
            output = getDouble("output")
        }
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        nbt.apply {
            putDouble("feedback", feedback)
            putDouble("output", output)
        }
    }
}

