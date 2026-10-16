package mods.eln.modern

import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor

object HubSettings {
    // Native ports run clockwise; source LRDU order was Left, Right, Down, Up.
    val pairs=listOf(0 to 1,2 to 3,1 to 2,3 to 0,0 to 2,1 to 3)
    fun connected(p: PartState,index: Int)=p.value.toInt() and (1 shl index)!=0
    fun cable(node: FaceNodeEntity,p: PartState,index: Int)=(PartInventory.get(node,p).getStackInSlot(index).item as? PartItem)?.definition?.takeIf { it.behavior==Behavior.CABLE && !it.id.endsWith("_current_cable") }
    fun toggle(node: FaceNodeEntity,p: PartState,index: Int): Boolean {
        if(p.definition.behavior!=Behavior.HUB || index !in pairs.indices)return false
        p.value=(p.value.toInt().coerceIn(0,63) xor (1 shl index)).toDouble();node.changedTopology();return true
    }
}

/** Original four independently fitted conductors and six selectable finite-resistance links. */
class HubRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private val conductors=List(4) { HubSettings.cable(e.node,p,it) }
    private val loads=Array(4) { i -> ElectricalLoad().apply { serialResistance=PartDefinition.byId.getValue("low_voltage_cable").resistance;state=p.pinVoltages[i] } }
    private val links=mutableListOf<Resistor>()
    init {
        for(i in 0..3)if(conductors[i]!=null) {
            e.circuit.root.addState(loads[i]);e.circuit.root.addComponent(Resistor(loads[i],null).pullDown());e.pins[i]=loads[i]
        }
        for((index,pair) in HubSettings.pairs.withIndex())if(HubSettings.connected(p,index)) {
            val (a,b)=pair;val ca=conductors[a];val cb=conductors[b]
            if(ca!=null && cb!=null) {
                val r=Resistor(loads[a],loads[b]).setResistance(ca.resistance+cb.resistance)
                links.add(r);e.circuit.root.addComponent(r)
            }
        }
    }
    fun afterStep(dt: Double) {
        p.pinVoltages=DoubleArray(4) { i -> if(conductors[i]!=null)loads[i].voltage else 0.0 }
        p.voltage=p.pinVoltages.maxOf { kotlin.math.abs(it) };p.current=links.maxOfOrNull { kotlin.math.abs(it.current) } ?: 0.0
        val loss=links.sumOf { it.current*it.current*it.resistance };p.energy+=loss*dt;p.powerFactor=loss;p.active=p.current>1e-8
    }
    fun afterTick() {
        for(i in 0..3)conductors[i]?.let { ConductorProtection.tick(e.node,p,"hub$i",loads[i].voltage,it.nominalVoltage) }
    }
}

/** Original voltage watchdog: a first-excursion grace and a randomized physical-time tolerance. */
object ConductorProtection {
    fun tick(node: FaceNodeEntity,p: PartState,key: String,voltage: Double,nominal: Double) {
        if(node.isRemoved || node.parts[p.face.ordinal]!==p)return
        val world=node.level as? net.minecraft.server.level.ServerLevel ?: return
        val overflow0=kotlin.math.abs(voltage)-nominal*1.3
        val previous=p.gridState.getBoolean(key+"Overvoltage");p.gridState.putBoolean(key+"Overvoltage",overflow0>0)
        val overflow=if(overflow0>0 && !previous)0.0 else overflow0
        val reset=nominal*.25
        val previousTolerance=if(p.gridState.contains(key+"Tolerance"))p.gridState.getDouble(key+"Tolerance") else reset
        val tolerance=minOf(reset,previousTolerance-.05*overflow*(.5+world.random.nextDouble()))
        p.gridState.putDouble(key+"Tolerance",tolerance)
        if(tolerance<0 && mods.eln.config.ForgePortConfig.cableVoltageProtection.get() && mods.eln.Eln.config.getBooleanOrElse("simulation.watchdog.destruction.voltage",true))MachineFailure.destroy(node,p,1.5f)
    }
}
