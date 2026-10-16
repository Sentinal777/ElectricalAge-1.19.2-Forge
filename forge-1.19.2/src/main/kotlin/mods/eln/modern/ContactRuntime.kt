package mods.eln.modern

import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.ResistorSwitch
import mods.eln.sim.mna.misc.MnaConst

data class ContactSpec(val relay: Boolean=false,val signal: Boolean=false,val currentThermal: Boolean=false,val cable: String="low_voltage_cable")

/** Source-calibrated two-terminal switches and independent 1.5/3.5 V Schmitt-control relays. */
class ContactRuntime(private val e: ElementRuntime) {
    private val p=e.part;private val d=p.definition;private val spec=d.contact!!
    private val a=ElectricalLoad().apply { serialResistance=d.resistance;state=p.voltage }
    private val b=ElectricalLoad().apply { serialResistance=d.resistance;state=p.voltageB }
    private val gate=if(spec.relay)ElectricalLoad().apply { serialResistance=.05;state=p.pinVoltages[0] } else null
    private val switch=ResistorSwitch("original_contact",a,b).apply { resistance=d.resistance;if(spec.signal)setOffResistance(MnaConst.ultraImpedance) }
    private var high=p.thermalState.getBoolean("contactHigh")
    private var published=p.active
    init {
        for(load in listOfNotNull(a,b,gate)) {
            e.circuit.root.addState(load);e.circuit.root.addComponent(Resistor(load,null).pullDown())
        }
        if(spec.relay) { e.pins[1]=a;e.pins[3]=b;e.pins[0]=gate } else { e.pins[0]=a;e.pins[2]=b }
        e.circuit.root.addComponent(switch)
    }
    fun beforeTick() { e.thermal?.beforeTick() }
    fun beforeStep() {
        if(gate!=null) {
            if(high) { if(gate.voltage<1.5)high=false } else if(gate.voltage>3.5)high=true
        }
        switch.state=if(spec.relay)high xor p.enabled else p.enabled
        p.active=switch.state
    }
    fun afterStep(dt: Double) {
        p.voltage=a.voltage;p.voltageB=b.voltage;p.current=switch.current
        p.pinVoltages=DoubleArray(4) { e.pins[it]?.voltage ?: 0.0 }
        p.energy+=(kotlin.math.abs(switch.power)+(a.current*a.current+b.current*b.current)*d.resistance)*dt
        if(spec.currentThermal)e.thermal?.addHeat(a.current*a.current*d.resistance*2,dt)
    }
    fun afterTick() {
        p.thermalState.putBoolean("contactHigh",high)
        if(published!=p.active) { published=p.active;e.node.sync() }
        ConductorProtection.tick(e.node,p,"contactA",a.voltage,d.nominalVoltage)
        ConductorProtection.tick(e.node,p,"contactB",b.voltage,d.nominalVoltage)
        e.thermal?.afterTick();e.node.setChanged()
    }
    fun persist() { p.thermalState.putBoolean("contactHigh",high);e.thermal?.persist() }
}
