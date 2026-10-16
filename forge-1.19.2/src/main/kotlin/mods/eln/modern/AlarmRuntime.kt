package mods.eln.modern

import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor

data class AlarmSpec(val sound: String,val period: Double)

/** Original quarter-second signal sampler, randomized initial sound delay and independently muted beacon. */
class AlarmRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private val load=ElectricalLoad().apply { serialResistance=.05;state=p.voltage }
    private var timer=p.thermalState.getDouble("alarmTimer")
    private var timeout=if(p.thermalState.contains("alarmTimeout"))p.thermalState.getDouble("alarmTimeout") else e.circuit.level.random.nextDouble()*2
    private var oldWarm=p.active
    private var oldAudible=p.thermalState.getBoolean("alarmAudible")
    init { e.circuit.root.addState(load);e.circuit.root.addComponent(Resistor(load,null).setResistance(1e12));e.pins[0]=load }
    fun afterStep() { p.voltage=load.voltage;p.current=load.current;p.pinVoltages[0]=load.voltage }
    fun afterTick() {
        timer+=.05
        if(timer>.25) {
            timer-=.25;p.active=load.voltage>2.5;p.signal=if(p.active)7 else 0
            if(p.active && p.enabled && timeout==0.0) {
                p.thermalState.putInt("alarmPulse",p.thermalState.getInt("alarmPulse")+1);timeout=p.definition.alarm!!.period;e.node.sync()
            }
            if(oldWarm && !p.active || oldAudible && !p.enabled)timeout=0.0
            if(oldWarm!=p.active) { e.node.refreshLight();e.node.sync() }
            if(oldAudible!=p.enabled)e.node.sync()
            oldWarm=p.active;oldAudible=p.enabled
        }
        timeout=(timeout-.05).coerceAtLeast(0.0);p.signal=if(p.active)7 else 0;persist();e.node.setChanged()
    }
    fun persist() { p.thermalState.putDouble("alarmTimer",timer);p.thermalState.putDouble("alarmTimeout",timeout);p.thermalState.putBoolean("alarmAudible",oldAudible) }
}
