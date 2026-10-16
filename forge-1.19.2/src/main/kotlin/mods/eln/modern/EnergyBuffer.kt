package mods.eln.modern

import kotlin.math.floor

/** Counts energy once, retaining sub-FE energy across ticks and saves. */
class EnergyBuffer(val capacity: Int = 100000) {
    var energy = 0.0
        set(value) { field = if(value.isFinite()) value.coerceIn(0.0,capacity.toDouble()) else 0.0 }
    val stored get() = floor(energy).toInt()
    fun add(amount: Double): Double {
        if(!amount.isFinite() || amount<=0) return 0.0
        val accepted=minOf(amount,capacity-energy);energy+=accepted;return accepted
    }
    fun remove(amount: Double): Double {
        if(!amount.isFinite() || amount<=0) return 0.0
        val taken=minOf(amount,energy);energy-=taken;return taken
    }
}

/** Original shaft energy relation: E = 0.5 * mass * rads^2 * shaftEnergyFactor. */
class ShaftPhysics(val mass: Double, energy: Double = 0.0,val fixed: Boolean=false) {
    init { require(mass.isFinite() && mass>0) }
    var energy = if(fixed)0.0 else energy
        set(value) { field=if(!fixed && value.isFinite()) value.coerceAtLeast(0.0) else 0.0 }
    val speed get() = if(fixed)0.0 else kotlin.math.sqrt(2*energy/(mass*0.05))
    fun consume(joules: Double): Double {
        if(!joules.isFinite() || joules<=0) return 0.0
        val used=minOf(joules,energy);energy-=used;return used
    }
}
