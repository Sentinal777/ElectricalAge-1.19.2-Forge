package mods.eln.modern.grid

import mods.eln.config.ForgePortConfig
import mods.eln.misc.PhysicalInterpolator
import mods.eln.modern.*
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.process.TransformerInterSystemProcess
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import kotlin.math.*

/** The original pole/transformer and motor-driven isolator equations, adapted to the loaded world. */
class GridRuntime(val element: ElementRuntime) {
    private val circuit=element.circuit;val node=element.node;val part=element.part
    private val spec=part.definition.grid!!
    private fun load(r: Double,voltage: Double=0.0)=ElectricalLoad().also {
        it.serialResistance=r;it.state=voltage;circuit.root.addState(it)
        circuit.root.addComponent(Resistor(it,null).setResistance(1e12))
    }
    val primary=load(if(spec.kind=="TRANSFORMER").4 else .1,part.voltage)
    val secondary=if(spec.kind in setOf("TRANSFORMER","POLE_TRANSFORMER","SWITCH"))load(if(spec.kind=="POLE_TRANSFORMER").4 else .1,part.voltageB)else null
    val control=if(spec.kind=="SWITCH")load(.05,part.gridState.getDouble("control"))else null
    val drive=if(spec.kind=="SWITCH")load(.05,part.gridState.getDouble("drive"))else null
    private val driveSink=drive?.let { Resistor(it,null).setResistance(1000.0).also(circuit.root::addComponent) }
    private val contact=if(spec.kind=="SWITCH")Resistor(primary,secondary).setResistance(.1).also(circuit.root::addComponent)else null
    private val interpolation=PhysicalInterpolator(1f,4f,.2f,0f).also {
        val t=part.gridState;it.factorPos=t.getFloat("position");it.factorSpeed=t.getFloat("speed");it.factorFiltered=t.getFloat("filtered")
    }
    private var closed=!part.gridState.contains("closed") || part.gridState.getBoolean("closed")
    init {
        if(spec.kind in setOf("TRANSFORMER","POLE_TRANSFORMER")) {
            val a=VoltageSource("grid_primary",primary,null);val b=VoltageSource("grid_secondary",secondary,null)
            circuit.root.addComponent(a);circuit.root.addComponent(b)
            circuit.root.addProcess(TransformerInterSystemProcess(primary,secondary,a,b).apply { ratio=.25 })
        }
        if(spec.kind=="DIRECT")element.pins.fill(primary)
        if(spec.kind=="POLE_TRANSFORMER")element.pins.fill(secondary)
    }
    fun gridLoad(side: Direction): ElectricalLoad? = when(spec.kind) {
        "TRANSFORMER","SWITCH" -> when(side) { FaceBasis.edges(part)[3] -> primary;FaceBasis.edges(part)[1] -> secondary;else -> null }
        else -> primary
    }
    fun ghostLoads(): List<Pair<BlockPos,ElectricalLoad>> {
        if(spec.kind!="SWITCH")return emptyList()
        val edges=FaceBasis.edges(part);val u=edges[0];val v=edges[1]
        fun at(x: Int,z: Int)=node.blockPos.offset(x*v.stepX-z*u.stepX,0,x*v.stepZ-z*u.stepZ)
        return listOf(at(-1,-1) to drive!!,at(-1,0) to control!!)
    }
    fun beforeTick() {
        if(spec.kind!="SWITCH")return
        interpolation.ff=(drive!!.voltage/200.0).toFloat()
        interpolation.target=mods.eln.sim.SignalLoadSupport.toNormalized(control!!.voltage).toFloat()
        interpolation.step(.05f)
        driveSink!!.resistance=20.0+980.0*(1-abs(interpolation.get()-interpolation.target))
        val maxU=max(primary.voltage,secondary!!.voltage)
        if(closed && interpolation.get()>.5*(maxU/51200))closed=false
        else if(!closed && interpolation.get()<.4*(maxU/51200))closed=true
        contact!!.resistance=if(closed).1 else 1e12
    }
    fun afterStep(dt: Double) {
        part.voltage=primary.voltage;part.voltageB=secondary?.voltage ?: 0.0
        part.current=primary.current;part.energy+=abs(primary.voltage*primary.current)*dt
        val heater=if(spec.kind=="TRANSFORMER")secondary!! else primary
        val maxLoss=2*7.5.pow(2)*.1;val capacity=if(spec.kind=="TRANSFORMER")20000.0 else maxLoss*30/130
        val heat=min(2*heater.current.pow(2)*heater.serialResistance,capacity*130/30*20)
        part.temperature+=(heat-(part.temperature-20)*maxLoss/130)*dt/capacity
        require(part.voltage.isFinite() && part.voltageB.isFinite() && part.temperature.isFinite()) { "Invalid grid state at ${node.blockPos}" }
    }
    fun afterTick() {
        val primaryLimit=if(spec.kind=="TRANSFORMER")120000.0 else spec.voltageLimit
        val secondaryLimit=if(spec.kind=="TRANSFORMER")30000.0 else if(spec.kind=="POLE_TRANSFORMER")3200.0 else 51200.0
        fun fault(key: String,voltage: Double,limit: Double): Boolean {
            val excess=abs(voltage)-limit*1.3;val t=part.gridState
            var wear=t.getDouble(key)
            if(excess<=0 || t.getBoolean(key+"previous"))wear=(wear+excess*.05).coerceAtLeast(0.0)
            t.putDouble(key,wear);t.putBoolean(key+"previous",excess>0)
            return wear>limit*.25
        }
        val overvoltage=fault("primaryFault",primary.voltage,primaryLimit) || (secondary?.let { fault("secondaryFault",it.voltage,secondaryLimit) } ?: false) ||
            (drive?.let { fault("driveFault",it.voltage,200.0) } ?: false)
        part.gridState.putFloat("position",interpolation.factorPos);part.gridState.putFloat("speed",interpolation.factorSpeed)
        part.gridState.putFloat("filtered",interpolation.factorFiltered);part.gridState.putBoolean("closed",closed)
        part.gridState.putDouble("control",control?.voltage ?: 0.0);part.gridState.putDouble("drive",drive?.voltage ?: 0.0)
        part.active=if(spec.kind=="SWITCH")closed else abs(part.current)>1e-6
        if((overvoltage && ForgePortConfig.cableVoltageProtection.get()) || (part.temperature>150 && ForgePortConfig.cableThermalProtection.get())) {
            node.gridFailed=true
            GridNetwork.removeAt(circuit.level,node.blockPos,false)
            GridMultiblock.remove(node,part);node.parts[part.face.get3DDataValue()]=null;circuit.level.removeBlock(node.blockPos,false)
        }
    }
}
