package mods.eln.modern

import mods.eln.config.ForgePortConfig
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalGateOutputProcess
import kotlin.math.abs

/** Original sensor's separate measurement and limited 5 V output circuits, with an actual cable slot. */
class SensorRuntime(private val e: ElementRuntime) {
    private val p=e.part;private val root=e.circuit.root
    private val voltageOnly=p.definition.id=="voltage_probe"
    private val cable=(PartInventory.get(e.node,p).getStackInSlot(0).item as? PartItem)?.definition
    private fun load(r: Double,v: Double)=ElectricalLoad().also { it.serialResistance=r;it.state=v;root.addState(it);root.addComponent(Resistor(it,null).setResistance(1e12)) }
    val a=load(1e-6,if(cable==null)0.0 else p.voltage)
    val b=if(voltageOnly)null else load(1e-6,if(cable==null)0.0 else p.pinVoltages[3])
    private val output=load(.05,p.pinVoltages[0])
    private val transfer=b?.let { Resistor(a,it).setResistance(if(cable!=null)cable.resistance*2 else 1e12).also(root::addComponent) }
    private val gate=NbtElectricalGateOutputProcess("sensor_output",output).apply { coulombs=.1*.05/ForgePortConfig.simulationSteps.get()/5.0 }
    init {
        root.addComponent(gate);e.pins[0]=output
        if(cable!=null) {
            if(voltageOnly)e.pins[2]=a else { e.pins[1]=a;e.pins[3]=b }
        }
    }
    fun beforeStep() {
        var physical=if(voltageOnly || p.value.toInt()==2)a.voltage else if(p.value.toInt()==1)transfer!!.current else transfer!!.current*a.voltage
        if(!voltageOnly && p.value.toInt()!=2)physical=when(p.channel) { "forward" -> physical;"reverse" -> -physical;else -> abs(physical) }
        gate.setVoltageSafe(SensorSettings.output(physical,p.expression))
    }
    fun afterStep(dt: Double) {
        p.voltage=a.voltage;p.voltageB=0.0;p.current=transfer?.current ?: 0.0
        for(i in 0..3)p.pinVoltages[i]=e.pins[i]?.voltage ?: 0.0
        p.energy+=abs(p.voltage*p.current)*dt
        require(p.voltage.isFinite() && p.current.isFinite()) { "Invalid probe measurement at ${e.node.blockPos}" }
    }
    fun afterTick() {
        if(!voltageOnly || cable==null)return
        val excess=abs(a.voltage)-cable.nominalVoltage*1.3
        if(excess<=0 || p.wasOvervoltage)p.voltageFault=(p.voltageFault+excess*.05).coerceAtLeast(0.0)
        p.wasOvervoltage=excess>0
        if(p.voltageFault>cable.nominalVoltage*.25 && ForgePortConfig.cableVoltageProtection.get()) {
            e.node.parts[p.face.get3DDataValue()]=null;e.node.changedTopology()
            if(e.node.parts.all { it==null })e.circuit.level.removeBlock(e.node.blockPos,false)
        }
    }
}
