package mods.eln.modern

import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.misc.IRootSystemPreStepProcess
import kotlin.math.*

/** Original bidirectional shaft machines and Thevenin controllers, with finite shaft energy and real losses. */
class ShaftElectricalRuntime(private val e: ElementRuntime) {
    private val p=e.part;private val d=p.definition
    private val motor=d.behavior==Behavior.SHAFT_MOTOR
    private val floating=d.shaftElectrical?.polarized==true
    private val generatingEfficiency=if(motor)1/9.9 else .95
    private val motoringEfficiency=if(motor).99 else .75*.95
    private var dt=.005
    private var watts=0.0
    private var tickJoules=0.0
    private fun load(value: Double)=ElectricalLoad().apply {
        serialResistance=d.resistance;state=value;e.circuit.root.addState(this)
        e.circuit.root.addComponent(Resistor(this,null).setResistance(1e12))
    }
    private val wire=load(p.voltage)
    private val negative=if(floating)load(p.voltageB)else null
    private val positive=load(p.gridState.getDouble("shaftEmf")+(negative?.voltage ?: 0.0))
    private val source=VoltageSource("shaft_emf",positive,negative).setVoltage(p.gridState.getDouble("shaftEmf"))
    private val series=Resistor(wire,positive).setResistance(d.resistance)
    init {
        p.resistance=d.resistance
        e.pins[0]=wire;e.pins[2]=negative ?: wire
        e.circuit.root.addComponent(source);e.circuit.root.addComponent(series)
        e.circuit.root.addProcess(IRootSystemPreStepProcess { regulate() })
    }
    private fun speed()=e.circuit.mechanics.speed(p)
    // Upstream electrical processes run at 20 Hz. Numerical subdivisions must not multiply this drag.
    private fun dragWatts()=.02*max(speed(),1.0)/.05*(if(motor).99 else .95)
    private fun regulate() {
        if(!p.enabled || p.tripped) { source.voltage=0.0;return }
        val target=d.nominalVoltage*speed()/200
        val system=positive.subSystem
        val previous=source.voltage
        // Probe the source's relative terminal voltage, including a floating negative terminal.
        source.voltage=previous+5
        val testCurrent=system.solve(source.currentState)
        source.voltage=previous
        val initialCurrent=system.solve(source.currentState)
        val measured=5/(initialCurrent-testCurrent)
        val resistance=if(measured.isFinite() && measured>0)measured.coerceAtMost(1e19)else 1e19
        val openVoltage=if(resistance<1e18)(previous+5+resistance*testCurrent).takeIf(Double::isFinite) ?: target else target
        val slope=25*d.power/d.nominalVoltage
        var command=if(target<openVoltage)openVoltage*(if(motor).997 else .999)+target*(if(motor).003 else .001)
        else if(resistance>=1e18)target
        else {
            val a=1/resistance;val b=slope-openVoltage/resistance;val c= -slope*target
            (-b+sqrt((b*b-4*a*c).coerceAtLeast(0.0)))/(2*a)
        }
        val expected=command*(command-openVoltage)/resistance
        val allocation=e.circuit.mechanics.available(p)/e.circuit.mechanics.electricalConverters(p)
        val budget=((allocation-dragWatts()*dt).coerceAtLeast(0.0)*generatingEfficiency/dt)*.999999
        if(expected>budget)command=(openVoltage+sqrt((openVoltage*openVoltage+4*budget*resistance).coerceAtLeast(0.0)))/2
        source.voltage=if(command.isFinite())command else 0.0
    }
    fun beforeTick() { tickJoules=0.0 }
    fun beforeStep(seconds: Double) {
        dt=seconds;series.resistance=if(p.enabled && !p.tripped)d.resistance else 1e12
    }
    fun afterStep(seconds: Double) {
        val electrical=source.power.takeIf(Double::isFinite) ?: 0.0
        val generated=(electrical*seconds).coerceAtLeast(0.0)
        val received=(-electrical*seconds).coerceAtLeast(0.0)
        val drag=if(p.enabled && !p.tripped)dragWatts()*seconds else 0.0
        var loss=0.0
        if(generated>0) {
            val required=generated/generatingEfficiency
            val used=e.circuit.mechanics.consume(p,required)
            p.thermalState.putDouble("shaftConsumedJ",p.thermalState.getDouble("shaftConsumedJ")+used)
            p.thermalState.putDouble("shaftElectricalOutputJ",p.thermalState.getDouble("shaftElectricalOutputJ")+generated)
            loss+=(used-generated).coerceAtLeast(0.0)
            if(required-used>max(1e-6,required*1e-6))error("Shaft electrical controller exceeded its finite shared-energy allocation")
        }else if(received>0) {
            val work=received*motoringEfficiency;e.circuit.mechanics.add(p,work)
            p.thermalState.putDouble("shaftProducedJ",p.thermalState.getDouble("shaftProducedJ")+work)
            p.thermalState.putDouble("shaftElectricalInputJ",p.thermalState.getDouble("shaftElectricalInputJ")+received)
            loss+=received-work
        }
        val friction=e.circuit.mechanics.consume(p,drag);loss+=friction
        p.thermalState.putDouble("shaftFrictionJ",p.thermalState.getDouble("shaftFrictionJ")+friction)
        // The original two serial shares cover the exposed terminal and the internal winding resistor.
        loss+=(wire.current.pow(2)*d.resistance*2+(negative?.current?.pow(2) ?: 0.0)*d.resistance)*seconds
        e.thermal?.addHeat(loss/seconds,seconds)
        p.thermalState.putDouble("shaftHeatJ",p.thermalState.getDouble("shaftHeatJ")+loss)
        watts=electrical;tickJoules+=abs(electrical)*seconds;p.energy+=abs(electrical)*seconds
        p.voltage=wire.voltage;p.voltageB=negative?.voltage ?: 0.0;p.current=if(motor)-source.current else source.current
        for(i in 0..3)p.pinVoltages[i]=e.pins[i]?.voltage ?: 0.0
        p.gridState.putDouble("shaftEmf",source.voltage)
    }
    fun afterTick() {
        e.thermal?.afterTick()
        p.thermalState.putDouble("shaftElectricalWatts",watts)
        p.active=p.enabled && !p.tripped && tickJoules>1e-5
        p.powerFactor=(tickJoules/.05/d.power).coerceIn(0.0,100.0)
    }
}
