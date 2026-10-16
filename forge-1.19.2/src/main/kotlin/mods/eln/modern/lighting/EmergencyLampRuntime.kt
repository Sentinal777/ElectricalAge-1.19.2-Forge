package mods.eln.modern.lighting

import mods.eln.modern.*
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import kotlin.math.abs

/** Finite original backup battery: charge while powered, illuminate only during an outage. */
class EmergencyLampRuntime(val e: ElementRuntime) {
    private val p=e.part;private val d=p.definition;private val spec=d.emergency!!
    val wired get()=p.enabled
    val demand get()=if(p.charge<1.0)d.nominalVoltage*d.nominalVoltage/spec.chargePower else 1e9
    private var tickResistance=demand
    val resistance get()=demand
    private val load=ElectricalLoad().apply { serialResistance=d.resistance;state=p.voltage }
    private val sink=Resistor(load,null).setResistance(resistance)
    var supply: LampSupplyRuntime?=null
    private var watts=0.0
    init {
        if(wired) {
            e.circuit.root.addState(load);e.circuit.root.addComponent(sink)
            for(i in 0..3)if(p.face==net.minecraft.core.Direction.UP || i in setOf(0,2))e.pins[i]=load
        }
    }
    fun beforeTick() { watts=0.0;tickResistance=demand;sink.resistance=tickResistance }
    fun afterStep(dt: Double) {
        p.voltage=if(wired)load.voltage else supply?.load?.voltage ?: 0.0
        p.current=p.voltage/tickResistance
        val power=p.voltage*p.current;watts+=power*dt/.05;p.energy+=power*dt
        if(p.voltage>d.nominalVoltage*.5)p.charge=(p.charge+power*dt/d.capacity).coerceAtMost(1.0)
    }
    fun afterTick() {
        val on=p.voltage<=d.nominalVoltage*.5 && p.charge>0.0
        if(on)p.charge=(p.charge-spec.consumption*.05/d.capacity).coerceAtLeast(0.0)
        val light=if(on)spec.lightLevel else 0
        val changed=p.signal!=light
        p.signal=light;p.active=on;p.thermalState.putDouble("lampWatts",watts)
        val excess=abs(p.voltage)-d.nominalVoltage*1.3
        if(excess<=0 || p.wasOvervoltage)p.voltageFault=(p.voltageFault+excess*.05).coerceAtLeast(0.0)
        p.wasOvervoltage=excess>0
        if(p.voltageFault>d.nominalVoltage*.25 && mods.eln.config.ForgePortConfig.cableVoltageProtection.get())e.circuit.level.destroyBlock(e.node.blockPos,true)
        if(changed) { e.node.refreshLight();e.node.sync() }
    }
}
