package mods.eln.modern

import mods.eln.config.ForgePortConfig
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.process.TransformerInterSystemProcess
import kotlin.math.abs

/** Original configurable transformer: real copper windings and core determine population and losses. */
class DcDcRuntime(private val e: ElementRuntime) {
    private val p=e.part;private val inventory=PartInventory.get(e.node,p)
    private val variable=p.definition.id=="variable_dc_dc_converter"
    private val primaryTurns=inventory.getStackInSlot(0).count
    private val secondaryTurns=inventory.getStackInSlot(1).count
    private val core=(inventory.getStackInSlot(2).item as? MaterialItem)?.definition?.takeIf { it.kind=="FERROMAGNETIC_CORE" }
    private val populated=core!=null && primaryTurns>=(if(variable)4 else 1) && secondaryTurns>=(if(variable)4 else 1)
    private fun load(voltage: Double,r: Double)=ElectricalLoad().also {
        it.state=voltage;it.serialResistance=r;e.circuit.root.addState(it);e.circuit.root.addComponent(Resistor(it,null).setResistance(1e12))
    }
    private val resistance=if(populated && p.enabled)core!!.value*.01 else 1e9
    private val primary=load(p.voltage,resistance);private val secondary=load(p.voltageB,resistance)
    private val inputSource=VoltageSource("dc_dc_primary",primary,null).also(e.circuit.root::addComponent)
    private val outputSource=VoltageSource("dc_dc_secondary",secondary,null).also(e.circuit.root::addComponent)
    private val transformer=TransformerInterSystemProcess(primary,secondary,inputSource,outputSource).also(e.circuit.root::addProcess)
    private val control=if(variable)load(p.pinVoltages[1],.05)else null
    init { e.pins[2]=primary;e.pins[0]=secondary;if(variable) { e.pins[1]=control;e.pins[3]=control } }
    fun beforeTick() {
        p.value=if(variable)1-(control!!.voltage/5).coerceIn(0.0,.9)else if(populated)(secondaryTurns.toDouble()/primaryTurns).coerceIn(1.0/16,16.0)else 1.0
        transformer.ratio=p.value
    }
    fun afterStep(dt: Double) {
        p.voltage=primary.voltage;p.voltageB=secondary.voltage;p.current=-inputSource.current
        p.energy+=(-inputSource.power).coerceAtLeast(0.0)*dt
        p.pinVoltages=DoubleArray(4) { e.pins[it]?.voltage ?: 0.0 }
    }
    fun afterTick() {
        p.active=populated && p.enabled && abs(p.current)>1e-6
        p.powerFactor=(maxOf(abs(inputSource.current),abs(outputSource.current))/5).coerceIn(0.0,1.0)
        p.gridState.putBoolean("populated",populated);p.gridState.putDouble("outputCurrent",outputSource.current)
        val overflow=maxOf(abs(p.voltage),abs(p.voltageB))-120000*1.3
        if(overflow<=0 || p.wasOvervoltage)p.voltageFault=(p.voltageFault+overflow*.05).coerceAtLeast(0.0)
        p.wasOvervoltage=overflow>0
        if(p.voltageFault>120000*.25 && ForgePortConfig.cableVoltageProtection.get())e.circuit.level.destroyBlock(e.node.blockPos,true)
    }
}
