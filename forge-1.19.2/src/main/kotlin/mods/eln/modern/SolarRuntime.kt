package mods.eln.modern

import mods.eln.Eln
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.process.PowerSourceBipole
import net.minecraft.core.BlockPos
import kotlin.math.abs

/** Original independently floating sources and Thevenin power controller. */
class SolarRuntime(private val e: ElementRuntime) {
    private val p=e.part;private val d=p.definition;private val spec=d.solar!!
    private fun load(voltage: Double)=ElectricalLoad().also {
        it.state=voltage;it.serialResistance=d.resistance;e.circuit.root.addState(it)
        e.circuit.root.addComponent(Resistor(it,null).setResistance(1e12))
    }
    private val positive=load(p.voltage);private val negative=load(p.voltageB)
    private val positiveSource=VoltageSource("solar_positive",positive,null).setVoltage(p.voltage)
    private val negativeSource=VoltageSource("solar_negative",negative,null).setVoltage(p.voltageB)
    private val power=PowerSourceBipole(positive,negative,positiveSource,negativeSource)
    private var refresh=0
    private var light=0.0
    init {
        e.circuit.root.addComponent(positiveSource);e.circuit.root.addComponent(negativeSource)
        power.setMaximumVoltage(d.nominalVoltage);power.setMaximumCurrent(d.maxCurrent)
        e.circuit.root.addProcess(power)
        if(spec.ground==null) { e.pins[0]=positive;e.pins[2]=negative }
        else e.pins[3]=positive
    }
    fun ghostLoads(): List<Pair<BlockPos,ElectricalLoad>> = spec.ground?.let { a ->
        val u=FaceBasis.edges(p)[0];val v=FaceBasis.edges(p)[1]
        listOf(e.node.blockPos.offset(a[0]*v.stepX-a[2]*u.stepX,a[1],a[0]*v.stepZ-a[2]*u.stepZ) to negative)
    }.orEmpty()
    fun beforeTick() {
        if(refresh--<=0) {
            val sun=SolarExposure.sunAngle(e.circuit.level)
            if(sun<Math.PI && spec.rotating && !PartInventory.get(e.node,p).getStackInSlot(0).isEmpty)
                p.solarAngle=sun.coerceIn(spec.minimumAngle,spec.maximumAngle)
            light=SolarExposure.sample(e.circuit.level,e.node.blockPos,p.solarAngle,spec.offset)
            refresh=2+(e.circuit.level.random.nextInt(2))
        }
        power.setPower(if(p.enabled)light*d.power*Eln.config.getDoubleOrElse("balance.generation.solarPanelPowerFactor",1.0)else 0.0)
    }
    fun afterStep(dt: Double) {
        p.voltage=positive.voltage;p.voltageB=negative.voltage;p.current=positiveSource.current
        p.energy+=(positiveSource.voltage*positiveSource.current+negativeSource.voltage*negativeSource.current).coerceAtLeast(0.0)*dt
        p.active=p.enabled && abs(p.current)>1e-6
        p.powerFactor=light
        p.pinVoltages=DoubleArray(4) { e.pins[it]?.voltage ?: 0.0 }
    }
}
