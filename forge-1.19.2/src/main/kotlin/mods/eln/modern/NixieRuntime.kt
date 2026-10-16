package mods.eln.modern

import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor

/** Original digit, shared side blanking and front decimal-point signal inputs. */
class NixieRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private val loads=List(3) { index -> ElectricalLoad().apply { serialResistance=.05;state=p.pinVoltages[listOf(2,1,0)[index]] } }
    init {
        for(load in loads) { e.circuit.root.addState(load);e.circuit.root.addComponent(Resistor(load,null).pullDown()) }
        e.pins[2]=loads[0];e.pins[1]=loads[1];e.pins[3]=loads[1];e.pins[0]=loads[2]
    }
    fun afterStep() {
        for(i in 0..3)p.pinVoltages[i]=e.pins[i]!!.voltage
        p.voltage=loads[0].voltage;p.current=loads[0].current
    }
    fun afterTick() {
        val digit=((loads[0].voltage/5).coerceIn(0.0,1.0)*10).toInt().coerceAtMost(9)
        val blank=loads[1].voltage>=2.5
        val dots=((loads[2].voltage/5).coerceIn(0.0,1.0)*4).toInt().coerceAtMost(3)
        val changed=digit!=p.displayReading || blank!=p.displayBlank || dots!=p.displayBits
        p.displayReading=digit;p.displayBlank=blank;p.displayBits=dots;p.active=!blank
        if(changed)e.node.sync()
    }
}
