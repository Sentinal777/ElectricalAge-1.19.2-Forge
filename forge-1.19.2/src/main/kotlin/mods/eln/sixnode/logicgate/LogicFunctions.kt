package mods.eln.sixnode.logicgate

import mods.eln.Eln
import mods.eln.misc.INBTTReady
import mods.eln.misc.LinearFunction
import mods.eln.misc.Utils
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component

// Text is translated by Minecraft's common language interface, safe on dedicated servers.
private fun tr(text: String): String = Component.translatable(text.replace(' ', '_')).string.let {
    if (it == text.replace(' ', '_')) text else it
}
object LogicTiming {
    const val nominalCallPeriod = 0.05
    var stepSeconds = 0.005
}

abstract class LogicFunction : INBTTReady {
    open val hasState = false
    abstract val inputCount: Int
    abstract val infos: String

    private fun Double.toDigital() = if (this <= 0.2) false
    else if (this >= 0.6) true
    else Math.random() > 0.5

    protected fun Double?.toDigitalString(): String = when {
        this == null -> "-"
        this >= 0.6 -> tr("ON")
        this <= 0.2 -> tr("OFF")
        else -> tr("UNDEF")
    }

    private fun Array<Double?>.toDigital(): List<Boolean?> = this.map { it?.toDigital() }

    open fun process(inputs: Array<Double?>): Boolean = process(inputs.toDigital())
    open fun process(inputs: List<Boolean?>): Boolean = false

    open fun getWaila(inputs: Array<Double?>, output: Double) = mutableMapOf(
        Pair(tr("Inputs"), (1..inputCount).map { "${listOf("A", "B", "C")[it - 1]}${inputs[it - 1].toDigitalString()}" }.joinToString(" ")),
        Pair(tr("Output"), output.toDigitalString())
    )

    override fun readFromNBT(nbt: CompoundTag, str: String) {}
    override fun writeToNBT(nbt: CompoundTag, str: String) {}
}

class Not : LogicFunction() {
    override val inputCount = 1
    override val infos =
        tr("Inverts the input signal.\nOutputs a voltage representing the\nopposite logic-level to its input.")

    override fun process(inputs: List<Boolean?>): Boolean = !(inputs[0] ?: false)
}

open class And : LogicFunction() {
    override val inputCount = 3
    override val infos =
        tr("Implements logical conjunction.\nA 1 (high) output results only if all of\nthe three inputs to the AND gate are 1 (high).")

    override fun process(inputs: List<Boolean?>): Boolean =
        (inputs[0] ?: true) && (inputs[1] ?: true) && (inputs[2] ?: true)
}

class Nand : And() {
    override val infos =
        tr("Its output is complement (inverted)\nto that of the AND gate.")

    override fun process(inputs: List<Boolean?>): Boolean = !super.process(inputs);
}

open class Or : LogicFunction() {
    override val inputCount = 3
    override val infos =
        tr("Implements logical disjunction.\nA 1 (high) output results if at least\none input to the gate is 1 (high).")

    override fun process(inputs: List<Boolean?>): Boolean =
        (inputs[0] ?: false) || (inputs[1] ?: false) || (inputs[2] ?: false)
}

class Nor : Or() {
    override val infos =
        tr("Its output is complement (inverted)\nto that of the OR gate.")

    override fun process(inputs: List<Boolean?>): Boolean = !super.process(inputs)
}

open class Xor : LogicFunction() {
    override val inputCount = 3
    override val infos =
        tr("Implements an exclusive or.\nAn output of 1 (high) results if one or\nall three inputs to the gate are 1 (high).")

    override fun process(inputs: List<Boolean?>): Boolean =
        (inputs[0] ?: false) xor (inputs[1] ?: false) xor (inputs[2] ?: false)
}

class XNor : Xor() {
    override val infos = tr("Its output is complement (inverted)\nto that of the XOR gate.")

    override fun process(inputs: List<Boolean?>): Boolean = !super.process(inputs)
}

class SchmittTrigger : LogicFunction() {
    override val hasState = true
    override val inputCount = 1
    override val infos =
        tr("If the input voltage is lower than 10V, the\noutput is 0 (low), if the input is bigger or\nequal to 30V, the output will be 1 (high). For\nall voltages in between, the output does not change.")
    private var state = false

    override fun process(inputs: Array<Double?>): Boolean {
        val input = inputs[0]
        if (input != null) {
            if (input <= 0.2) {
                state = false
            } else if (input >= 0.6) {
                state = true
            }
        }

        return state
    }

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        state = nbt.getBoolean(str + "state")
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        nbt.putBoolean(str + "state", state)
    }
}

class Oscillator : LogicFunction() {
    override val hasState = true
    override val inputCount = 1
    override val infos =
        tr("Outputs a rectangular signal which's frequency\ndepends to the input voltage. The higher the\ninput voltage - the higher the frequency.")
    private var ramp = 0.0
    private var state = false

    // 0v = 0.1Hz through 5v = 10Hz
    private val hertzFunction = LinearFunction(0f, 0.1f, Eln.SVU.toFloat(), (1 / LogicTiming.nominalCallPeriod).toFloat())

    override fun process(inputs: Array<Double?>): Boolean {
        val hertz = hertzFunction.getValue(inputs[0]?: 0.0)
        val halfPeriod = (1 / hertz) * 0.5
        ramp += LogicTiming.stepSeconds/halfPeriod
        if (ramp >= 1) {
            ramp = 0.0
            state = !state
        }
        return state
    }

    override fun getWaila(inputs: Array<Double?>, output: Double) = mutableMapOf(
        Pair(tr("Inputs"), "${listOf("A", "B", "C")[0]} ${Utils.plotVolt("", inputs[0] ?: 0.0)}"),
        Pair(tr("Output"), output.toDigitalString())
    )

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        ramp = nbt.getDouble(str + "ramp")
        state = nbt.getBoolean(str + "state")
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        nbt.putDouble(str + "ramp", ramp)
        nbt.putBoolean(str + "state", state)
    }
}

abstract class TriggeredLogicFunction(private val triggerIndex: Int) : LogicFunction() {
    override final val hasState = true

    private var trigger = false
    private var state = false

    override final fun process(inputs: Array<Double?>): Boolean = super.process(inputs)

    override final fun process(inputs: List<Boolean?>): Boolean {
        val newTrigger = inputs.elementAtOrNull(triggerIndex) ?: false
        if (newTrigger != trigger) {
            if (newTrigger)
                state = onRisingEdge(inputs, state)
            else
                state = onFallingEdge(inputs, state)
            trigger = newTrigger
        }
        return state
    }

    open fun onRisingEdge(inputs: List<Boolean?>, state: Boolean): Boolean = state
    open fun onFallingEdge(inputs: List<Boolean?>, state: Boolean): Boolean = state

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        trigger = nbt.getBoolean(str + "trigger")
        state = nbt.getBoolean(str + "state")
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        nbt.putBoolean(str + "trigger", trigger)
        nbt.putBoolean(str + "state", state)
    }
}

class DFlipFlop : TriggeredLogicFunction(1) {
    override val inputCount = 2
    override val infos =
        tr("The D flip-flop captures the value\nof the D-input at a rising edge\nportion of the clock cycle.")

    override fun onRisingEdge(inputs: List<Boolean?>, state: Boolean): Boolean = inputs[0] ?: false
}

class JKFlipFlop : TriggeredLogicFunction(0) {
    override val inputCount = 3
    override val infos =
        tr("If the input J is 1 (high) and K is 0 (low)\nduring a clock pulse, the output becomes 1 (high).\nIf J is 0 (low) and K is 1 (high) during the pulse,\nthe output becomes 0 (low). If both inputs are 0 (low)\nduring the clock pulse, the state is maintained. If both\ninputs are 1 (high) the input is toggled if a rising edge\nwas detected at the clock input.")

    override fun onRisingEdge(inputs: List<Boolean?>, state: Boolean): Boolean =
        when (Pair(inputs[1] ?: true, inputs[2] ?: true)) {
            Pair(true, false) -> true
            Pair(false, true) -> false
            Pair(true, true) -> !state
            else -> state
        }
}

class Pal : LogicFunction() {
    override val inputCount = 3
    override val infos: String =
        tr("A Programmable Array Logic (PAL) is a programmable\nlogic device semiconductors used to  implement any logic\nfunction in only one digital circuit. The function is\nstateless, which means that no intermediate state is saved.")
    val truthTable = Array(8) { false }

    private operator fun Boolean.times(factor: Int): Int = if (this) factor else 0

    private fun Array<Boolean>.toInt(): Int {
        return (0 until count()).filter { this[it] }.sumOf { 1.shl(it) }
    }

    private fun Array<Boolean>.fromInt(value: Int) {
        for (i in 0 until this.count()) {
            this[i] = (value and 1.shl(i)) != 0
        }
    }

    override fun process(inputs: List<Boolean?>): Boolean =
        truthTable[(inputs[0] ?: false) * 4 +
            (inputs[2] ?: false) * 2 +
            ((inputs[2] ?: false) xor (inputs[1] ?: false)) * 1]

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        truthTable.fromInt(nbt.getInt(str + "truthTable"))
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        nbt.putInt(str + "truthTable", truthTable.toInt())
    }
}
