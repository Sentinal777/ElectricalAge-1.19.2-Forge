package mods.eln.modern

import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor

data class ElectricalPort(val load: ElectricalLoad,val channel: Int?=null)

/** Sixteen independent upstream loads; a neutral neighbor addresses the black (zero) channel. */
class SignalBusRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private val saved=p.gridState.getLongArray("busVoltages")
    val loads=List(16) { i -> ElectricalLoad().apply {
        serialResistance=.05;state=if(i<saved.size)Double.fromBits(saved[i]).takeIf { it.isFinite() } ?: 0.0 else 0.0
        e.circuit.root.addState(this);e.circuit.root.addComponent(Resistor(this,null).pullDown())
    } }
    fun afterStep() {
        p.voltage=loads[0].voltage;p.current=loads.sumOf { it.current }
        p.active=loads.any { kotlin.math.abs(it.voltage)>1e-6 }
        persist()
    }
    fun persist() { p.gridState.putLongArray("busVoltages",loads.map { it.voltage.toBits() }) }
}
