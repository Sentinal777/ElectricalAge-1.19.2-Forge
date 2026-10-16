package mods.eln.misc

import mods.eln.Eln
import java.util.Locale

/** Platform-independent logging and meter formatting retained at the solver boundary. */
object Utils {
    @JvmStatic fun println(value: Any?) { Eln.LOGGER.debug("{}", value) }
    @JvmStatic fun plotVolt(prefix: String, value: Double) = prefix + String.format(Locale.ROOT, "%.3f V", value)
    @JvmStatic fun plotAmpere(prefix: String, value: Double) = prefix + String.format(Locale.ROOT, "%.3f A", value)
    @JvmStatic fun plotSignal(value: Double) = String.format(Locale.ROOT, "%.1f %%", value * 20.0)
    @JvmStatic fun rand(low: Double, high: Double) = low + Math.random() * (high - low)
    @JvmStatic fun limit(value: Double, low: Double, high: Double) = value.coerceIn(low,high)
    @JvmStatic fun plotValue(value: Double) = String.format(Locale.ROOT,"%.3f",value)
}
