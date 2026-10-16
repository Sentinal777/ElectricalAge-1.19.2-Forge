package mods.eln.modern

import mods.eln.config.ForgePortConfig
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.ResistorSwitch
import kotlin.math.abs

/** Original inline meter. Its counter/credit is a measurement, never an energy source or item charge. */
object EnergyMeters {
    fun cable(p: PartState)=(PartInventory.peek(p,0).item as? PartItem)?.definition?.takeIf { it.behavior==Behavior.CABLE && (p.definition.id=="mqtt_energy_meter" || !it.id.endsWith("_current_cable")) }
    fun initialize(p: PartState) {
        val s=p.gridState
        if(s.getBoolean("meterInitialized"))return
        s.putBoolean("meterInitialized",true);s.putDouble("meterCounterJ",p.energy.takeIf(Double::isFinite) ?: 0.0);s.putDouble("meterTime",0.0)
        s.putInt("meterMode",0);s.putInt("meterEnergyUnit",1);s.putInt("meterTimeUnit",0)
    }
    fun counter(p: PartState)=p.gridState.getDouble("meterCounterJ")
    fun time(p: PartState)=p.gridState.getDouble("meterTime")
    fun prepaid(p: PartState)=p.gridState.getInt("meterMode")==1
    fun setCounter(node: FaceNodeEntity,p: PartState,joules: Double): Boolean {
        if(!joules.isFinite() || abs(joules)>1e15)return false
        p.gridState.putDouble("meterCounterJ",joules);p.energy=joules;node.setChanged();node.sync();return true
    }
}

/** Two fitted-cable series terminals and the original 0.0125-ohm switched shunt. */
class EnergyMeterRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private val mqtt=MqttDevices.isMeter(p)
    private val cable=EnergyMeters.cable(p)
    private val a=ElectricalLoad().apply { serialResistance=cable?.resistance ?: 1e12;state=p.voltage }
    private val b=ElectricalLoad().apply { serialResistance=cable?.resistance ?: 1e12;state=p.voltageB }
    private val shunt=ResistorSwitch("meterShunt",a,b).apply { resistance=.0125;state=p.enabled }
    init {
        EnergyMeters.initialize(p)
        if(mqtt) { MqttDevices.initialize(p);MqttService.attach(e.node,p) }
        for(load in listOf(a,b)) { e.circuit.root.addState(load);e.circuit.root.addComponent(Resistor(load,null).pullDown()) }
        e.circuit.root.addComponent(shunt)
        if(cable!=null) { e.pins[3]=a;e.pins[1]=b }
    }
    fun beforeTick() {
        val blocked=!mqtt && EnergyMeters.prepaid(p) && EnergyMeters.counter(p)<0 && p.gridState.getDouble("meterPowerW")>0
        shunt.resistance=if(blocked)1e12 else .0125;shunt.state=p.enabled && cable!=null
    }
    fun afterStep(dt: Double) {
        val current=shunt.current
        val watts=current*(if(mqtt)abs(a.voltage)else a.voltage)
        val work=if(mqtt && !p.enabled)0.0 else watts*dt
        val counter=EnergyMeters.counter(p)+if(!mqtt && EnergyMeters.prepaid(p))-work else work
        p.gridState.putDouble("meterCounterJ",counter);p.gridState.putDouble("meterPowerW",watts)
        p.gridState.putDouble("meterMeasuredJ",p.gridState.getDouble("meterMeasuredJ")+work)
        p.gridState.putDouble("meterShuntHeatJ",p.gridState.getDouble("meterShuntHeatJ")+shunt.power.coerceAtLeast(0.0)*dt)
        p.energy=counter;p.voltage=a.voltage;p.voltageB=b.voltage;p.current=current;p.active=abs(current)>1e-7
    }
    fun afterTick() {
        val s=p.gridState;s.putDouble("meterTime",EnergyMeters.time(p)+.05*72)
        val fitted=cable ?: return
        for((name,voltage) in listOf("A" to a.voltage,"B" to b.voltage)) {
            var excess=abs(voltage)-fitted.nominalVoltage*1.3
            val outside=excess>0
            if(outside && !s.getBoolean("meterOutside$name"))excess=0.0
            s.putBoolean("meterOutside$name",outside)
            val maximum=fitted.nominalVoltage*.25
            val remaining=minOf(maximum,(if(s.contains("meterVoltage$name"))s.getDouble("meterVoltage$name")else maximum)-.05*excess*(.5+e.circuit.level.random.nextDouble()))
            s.putDouble("meterVoltage$name",remaining)
            if(remaining<0 && ForgePortConfig.cableVoltageProtection.get()) { MachineFailure.destroy(e.node,p,1.5f);return }
        }
    }
}
