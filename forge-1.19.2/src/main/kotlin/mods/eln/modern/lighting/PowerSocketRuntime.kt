package mods.eln.modern.lighting

import mods.eln.modern.*
import mods.eln.config.ForgePortConfig
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import kotlin.math.abs

/** A real, bidirectional .1 ohm connection to the nearest matching lamp-supply load. */
class PowerSocketRuntime(val e: ElementRuntime) {
    private val p=e.part
    val load=ElectricalLoad().apply { serialResistance=.1;state=p.voltage }
    var supply: LampSupplyRuntime?=null
    var link: Resistor?=null
    init {
        e.circuit.root.addState(load);e.circuit.root.addComponent(Resistor(load,null).pullDown())
        for(i in 0..3)e.pins[i]=load
    }
    fun afterStep(dt: Double) {
        p.voltage=load.voltage;p.current=load.current
        p.active=link?.resistance==.1
        p.energy+=abs((link?.current ?: 0.0)*load.voltage)*dt
    }
    fun afterTick() {
        var excess=abs(p.voltage)-390.0
        val outside=excess>0
        if(outside && !p.wasOvervoltage)excess=0.0
        p.wasOvervoltage=outside
        val previous=if(p.gridState.contains("socketVoltageTimeout"))p.gridState.getDouble("socketVoltageTimeout")else 75.0
        val remaining=minOf(75.0,previous-.05*excess*(.5+e.circuit.level.random.nextDouble()))
        p.gridState.putDouble("socketVoltageTimeout",remaining)
        if(remaining<0 && ForgePortConfig.cableVoltageProtection.get()) {
            MachineFailure.destroy(e.node,p,1.5f)
        }
    }
}
