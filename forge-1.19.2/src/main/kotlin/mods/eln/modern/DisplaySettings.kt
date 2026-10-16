package mods.eln.modern

object DisplaySettings {
    fun range(expression: String): List<Double>? {
        val values=expression.split(',').map { it.trim().toDoubleOrNull() ?: return null }
        return values.takeIf { it.size==2 && it.all { v -> v.isFinite() && kotlin.math.abs(v)<=1e12 } }
    }
    /** Same linear range and integer truncation as ElectricalDigitalDisplayRender. */
    fun reading(voltage: Double,expression: String): Int {
        val range=range(expression) ?: listOf(0.0,1000.0)
        return (range[0].toFloat()+(voltage/5).toFloat()*(range[1]-range[0]).toFloat()).toInt().coerceIn(0,9999)
    }
}
