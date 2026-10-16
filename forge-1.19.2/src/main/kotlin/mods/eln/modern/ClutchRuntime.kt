package mods.eln.modern

import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor

/** Original clutch input is a high-impedance five-volt gate; torque is handled by the shaft graph. */
class ClutchRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private val load=ElectricalLoad().apply { serialResistance=.05;state=p.voltage }
    val control get()=(load.voltage/5).coerceIn(0.0,1.0)
    init {
        e.circuit.root.addState(load);e.circuit.root.addComponent(Resistor(load,null).setResistance(1e9))
        for(i in 0..3)e.pins[i]=load
    }
    fun afterStep() { p.voltage=load.voltage;p.current=load.current }
}
