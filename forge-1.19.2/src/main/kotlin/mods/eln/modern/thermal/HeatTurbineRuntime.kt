package mods.eln.modern.thermal

import mods.eln.misc.FunctionTable
import mods.eln.modern.*
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.ThermalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.misc.IRootSystemPreStepProcess
import kotlin.math.*

/** Original turbine voltage/power curves and Thevenin regulator, with conserved heat/work. */
class HeatTurbineRuntime(private val thermal: ThermalRuntime) {
    private val e=thermal.e;private val p=e.part;private val d=p.definition;private val spec=d.heatTurbine!!
    val cold=ThermalLoad().apply {
        val t=d.thermal!!;set(t.rs,t.rp,t.capacity)
        temperatureCelsius=p.thermalState.getDouble("coldDelta").takeIf { it.isFinite() } ?: 0.0
    }
    private fun load(r: Double,v: Double)=ElectricalLoad().also {
        it.serialResistance=r;it.state=v;e.circuit.root.addState(it);e.circuit.root.addComponent(Resistor(it,null).setResistance(1e12))
    }
    private val input=load(d.resistance,p.voltage)
    private val positive=load(1e-6,p.voltage)
    private val source=VoltageSource("heat_turbine",positive,null)
    private val toInput=Resistor(input,positive).setResistance(d.resistance*30)
    private val voltageCurve=FunctionTable(doubleArrayOf(0.0,.1,.85,1.0,1.1,1.15,1.18,1.19,1.25),8.0/5).duplicate(spec.nominalDelta,d.nominalVoltage)!!
    private val heatCurve=FunctionTable(doubleArrayOf(0.0,.2,.4,.6,.8,1.0,1.3,1.8,2.7),8.0/5).duplicate(d.power,d.power)!!
    private var generatedJoules=0.0
    init {
        e.pins[0]=input;e.pins[2]=input
        e.circuit.root.addComponent(source);e.circuit.root.addComponent(toInput)
        e.circuit.root.addProcess(IRootSystemPreStepProcess { regulate() })
    }
    private fun regulate() {
        val target=voltageCurve.getValue(thermal.load.temperatureCelsius-cold.temperatureCelsius)
        val equivalent=positive.subSystem.getTh(positive,source)
        // Preserve the source's signed DC curve while using the same regulator for either polarity.
        val sign=if(target<0)-1.0 else 1.0
        val targetMagnitude=abs(target);val voltage=equivalent.voltage*sign;val resistance=equivalent.resistance
        var result=if(targetMagnitude<voltage)voltage else if(equivalent.isHighImpedance)targetMagnitude else {
            val a=1/resistance;val b=spec.wattsPerVolt-voltage/resistance;val c= -spec.wattsPerVolt*targetMagnitude
            (-b+sqrt(b*b-4*a*c))/(2*a)
        }
        val power=(result-voltage)/resistance*result
        val maximum=d.power*1.5
        if(power>maximum)result=minOf((sqrt(voltage*voltage+4*maximum*resistance)+voltage)/2,targetMagnitude).coerceAtLeast(voltage)
        source.voltage=if(result.isFinite())result*sign else 0.0
    }
    fun beforeTick() { generatedJoules=0.0 }
    fun afterStep(dt: Double) {
        val generated=maxOf(0.0,source.power)
        generatedJoules+=generated*dt;p.energy+=generated*dt
        p.voltage=input.voltage;p.voltageB=0.0;p.current=source.current
        for(i in 0..3)p.pinVoltages[i]=e.pins[i]?.voltage ?: 0.0
        p.active=generated>1;p.powerFactor=(generated/d.power).coerceIn(0.0,4.0)
    }
    fun process(dt: Double) {
        val warm=thermal.load
        val (hot,cool)=if(warm.temperatureCelsius>=cold.temperatureCelsius)warm to cold else cold to warm
        val efficiency=efficiency(hot.temperatureCelsius,cool.temperatureCelsius,thermal.ambient+273.15)
        val work=generatedJoules/.05
        val heat=maxOf(work,heatCurve.getValue(work)/efficiency)
        // Upstream sends the entire input heat to the cold load while also creating electrical
        // work. Subtract that work from the rejected heat so the full circuit conserves energy.
        hot.movePowerTo(-heat);cool.movePowerTo(heat-work)
        warm.movePowerTo(toInput.power)
        p.thermalState.putDouble("internalLossJ",p.thermalState.getDouble("internalLossJ")+toInput.power*dt)
        p.thermalState.putDouble("efficiency",efficiency)
    }
    fun persist() {
        p.thermalState.putDouble("coldDelta",cold.temperatureCelsius)
        p.thermalState.putDouble("coldTemperature",cold.temperatureCelsius+thermal.ambient)
    }
    companion object {
        fun efficiency(hotDelta: Double,coldDelta: Double,ambientKelvin: Double): Double {
            val hot=hotDelta+ambientKelvin;val cold=coldDelta+ambientKelvin
            return if(hot<=1e-6 || cold<=1e-6).05 else abs(1-cold/hot).coerceAtLeast(.05)
        }
    }
}
