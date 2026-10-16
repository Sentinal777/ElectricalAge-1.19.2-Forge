package mods.eln.modern.thermal

import mods.eln.Eln
import mods.eln.config.ForgePortConfig
import mods.eln.modern.*
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.IProcess
import mods.eln.sim.ThermalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalGateOutputProcess
import kotlin.math.abs

/** Thermal and electrical ports retain separate masks, capacities and update rates. */
class ThermalRuntime(val e: ElementRuntime) {
    private val p=e.part;private val d=p.definition
    private val cable=if(d.behavior==Behavior.THERMAL_SENSOR)(PartInventory.get(e.node,p).getStackInSlot(0).item as? PartItem)?.definition else null
    private val temperatureOnly=d.id=="temperature_probe"
    private val spec=cable?.thermal ?: d.thermal
    val load=ThermalLoad().apply {
        spec?.let { set(it.rs,it.rp,it.capacity);if(it.slow)setAsSlow() }
        if(cable?.behavior==Behavior.CABLE)Rp=1e9
        temperatureCelsius=if(p.thermalState.contains("delta"))p.thermalState.getDouble("delta").takeIf { it.isFinite() } ?: 0.0 else p.temperature-20.0
    }
    val hasLoad=spec!=null || d.behavior==Behavior.THERMAL_SENSOR
    private var joules=0.0
    private var electricalJoules=0.0
    private var thermalFault=p.thermalState.getDouble("fault").coerceAtLeast(0.0)
    private var wasOutside=p.thermalState.getBoolean("wasOutside")
    var ambient=20.0
        private set
    private var output: ElectricalLoad?=null
    private var gate: NbtElectricalGateOutputProcess?=null
    private val ambientFilter=mods.eln.misc.RcInterpolator(3f).apply { setValue(p.thermalState.getFloat("ambientFilter").coerceIn(0f,1f)) }
    private var ambientTimer=p.thermalState.getDouble("ambientTimer").takeIf { it.isFinite() && it in 0.0.. .2 } ?: 0.0
    val process=IProcess { dt -> load.movePowerTo(joules/.05);furnace?.process(dt);turbine?.process(dt);fuelHeater?.process(dt);heatExchanger?.process(dt);electricalFurnace?.process(dt) }
    private var furnace: HeatFurnaceRuntime?=null
    private var turbine: HeatTurbineRuntime?=null
    private var fuelHeater: FuelHeatFurnaceRuntime?=null
    private var heatExchanger: HeatExchangerRuntime?=null
    private var electricalFurnace: ElectricalFurnaceRuntime?=null
    val loads get()=if(!hasLoad)emptyList()else listOfNotNull(load,turbine?.cold)
    init {
        if(d.behavior==Behavior.HEAT_FURNACE)furnace=HeatFurnaceRuntime(this)
        if(d.behavior==Behavior.HEAT_TURBINE)turbine=HeatTurbineRuntime(this)
        if(d.behavior==Behavior.FUEL_HEAT_FURNACE)fuelHeater=FuelHeatFurnaceRuntime(this)
        if(d.behavior==Behavior.HEAT_EXCHANGER)heatExchanger=HeatExchangerRuntime(this)
        if(d.behavior==Behavior.FURNACE)electricalFurnace=ElectricalFurnaceRuntime(this)
        if(d.behavior in setOf(Behavior.THERMAL_SENSOR,Behavior.AMBIENT_SENSOR)) {
            val pin=if(d.behavior==Behavior.AMBIENT_SENSOR)3 else 0
            output=ElectricalLoad().also {
                it.serialResistance=.05;it.state=p.pinVoltages[pin];e.circuit.root.addState(it)
                e.circuit.root.addComponent(Resistor(it,null).setResistance(1e12));e.pins[pin]=it
            }
            gate=NbtElectricalGateOutputProcess("thermal_sensor_output",output!!).apply { coulombs=.1*.05/ForgePortConfig.simulationSteps.get()/5 }
            if(d.behavior==Behavior.AMBIENT_SENSOR)gate!!.setVoltageSafe(ambientFilter.get()*5.0)
            e.circuit.root.addComponent(gate!!)
        }
    }
    /** 0 denotes an electrical cable's co-located heat path, 3 a dedicated thermal cable. */
    fun port(index: Int): Pair<ThermalLoad,Int>? {
        if(!hasLoad)return null
        if(d.behavior==Behavior.HEAT_TURBINE && index==3)return turbine!!.cold to 3
        val exposed=if(d.passive?.large==true)index==2 else if(d.contact?.currentThermal==true)index==1 || index==3 else when(d.behavior) {
            Behavior.THERMAL_CABLE,Behavior.PASSIVE_COOLER -> true
            Behavior.ACTIVE_COOLER -> index==1 || index==3
            Behavior.HEAT_FURNACE,Behavior.FUEL_HEAT_FURNACE,Behavior.HEAT_EXCHANGER -> index==2
            Behavior.HEAT_TURBINE -> index==1
            Behavior.THERMAL_SENSOR -> cable!=null && if(temperatureOnly)index==2 else index==1 || index==3
            Behavior.BATTERY,Behavior.SHAFT_MOTOR,Behavior.SHAFT_GENERATOR -> index==0 || index==2
            Behavior.CABLE -> d.id!="signal_cable"
            Behavior.DIODE -> index==0 || index==2
            Behavior.RESISTOR -> e.pins[index]!=null && (!p.originalPassive || index==1 || index==3)
            else -> false
        }
        if(!exposed)return null
        val kind=if(d.passive?.large==true)3 else if(d.contact?.currentThermal==true || d.behavior in setOf(Behavior.CABLE,Behavior.RESISTOR,Behavior.DIODE,Behavior.BATTERY,Behavior.SHAFT_MOTOR,Behavior.SHAFT_GENERATOR) || cable?.behavior==Behavior.CABLE)0 else 3
        return load to kind
    }
    fun beforeTick() {
        joules=0.0;electricalJoules=0.0
        if(p.thermalState.contains("coolingFactor")) { load.temperatureCelsius*=p.thermalState.getDouble("coolingFactor").coerceIn(0.0,1.0);p.thermalState.remove("coolingFactor") }
        if(e.circuit.level.gameTime%20L==0L || ambient==20.0)ambient=Climate.sample(e.circuit.level,e.node.blockPos).temperatureCelsius
        if(gate!=null) {
            val physical=if(d.behavior==Behavior.AMBIENT_SENSOR)ambient else if(p.value.toInt()==0)load.power else load.temperatureCelsius+ambient
            if(d.behavior==Behavior.AMBIENT_SENSOR) {
                ambientTimer+=.05
                if(ambientTimer>.2) {
                    ambientTimer-=.2;ambientFilter.target=(SensorSettings.output(physical,p.expression)/5).toFloat()
                    ambientFilter.step(.05f);gate!!.setVoltageSafe(ambientFilter.get()*5.0)
                }
            } else gate!!.setVoltageSafe(SensorSettings.output(physical,p.expression))
        }
        furnace?.beforeTick()
        turbine?.beforeTick()
        fuelHeater?.beforeTick()
        heatExchanger?.beforeTick()
        electricalFurnace?.beforeTick()
    }
    fun afterElectricalStep(dt: Double) { turbine?.afterStep(dt);electricalFurnace?.afterElectricalStep(dt) }
    fun addHeat(watts: Double,dt: Double) {
        val capped=if(d.behavior==Behavior.CABLE)watts.coerceAtMost(load.heatCapacity*130/30*20)else watts
        joules+=capped*dt
    }
    fun electricalPower(watts: Double,dt: Double) { electricalJoules+=watts*dt }
    fun prepareThermalStep() {
        if(d.behavior==Behavior.ACTIVE_COOLER) {
            val cooling=if(d.id=="200v_active_thermal_dissipator")1200.0 else 800.0
            val poweredFactor=electricalJoules/.05/d.power
            load.Rp=1/(1/spec!!.rp+poweredFactor/(30/cooling))
        }
    }
    fun afterTick() {
        if(hasLoad) { p.temperature=load.temperatureCelsius+ambient;p.thermalPower=load.power }
        if(gate!=null) {
            val pin=if(d.behavior==Behavior.AMBIENT_SENSOR)3 else 0
            p.pinVoltages[pin]=output!!.voltage;p.voltage=output!!.voltage;p.current=output!!.current
            if(!hasLoad)p.temperature=ambient
        }
        p.thermalState.putDouble("delta",load.temperatureCelsius);p.thermalState.putDouble("rp",load.Rp)
        p.thermalState.putDouble("capacity",load.heatCapacity);p.thermalState.putDouble("ambient",ambient)
        furnace?.persist()
        turbine?.persist()
        fuelHeater?.persist()
        heatExchanger?.persist()
        electricalFurnace?.afterTick()
        if(d.contact?.currentThermal==true || d.behavior in setOf(Behavior.THERMAL_CABLE,Behavior.PASSIVE_COOLER,Behavior.ACTIVE_COOLER,Behavior.HEAT_FURNACE,Behavior.FUEL_HEAT_FURNACE,Behavior.HEAT_TURBINE,Behavior.HEAT_EXCHANGER,Behavior.CABLE,Behavior.RESISTOR,Behavior.DIODE,Behavior.BATTERY,Behavior.SHAFT_MOTOR,Behavior.SHAFT_GENERATOR)) {
            val limits=spec!!
            val excess=maxOf(p.temperature-limits.maximum,limits.minimum-p.temperature)
            if(excess<=0 || wasOutside)thermalFault=(thermalFault+excess*.05*e.circuit.level.random.nextDouble().let { .5+it }).coerceAtLeast(0.0)
            wasOutside=excess>0
            p.thermalState.putDouble("fault",thermalFault);p.thermalState.putBoolean("wasOutside",wasOutside)
            val destruction=if(d.behavior==Behavior.CABLE)ForgePortConfig.cableThermalProtection.get() else ForgePortConfig.thermalFailures.get()
            val watchdogKey=if(d.behavior==Behavior.RESISTOR && d.passive?.large!=true)"simulation.watchdog.destruction.resistorHeat" else "simulation.watchdog.destruction.thermal"
            if(thermalFault>limits.maximum && destruction && Eln.config.getBooleanOrElse(watchdogKey,true)) {
                if(d.contact?.currentThermal==true || p.originalPassive)MachineFailure.destroy(e.node,p,if(d.passive?.large==true)3f else 1.5f) else {
                    e.node.parts[p.face.get3DDataValue()]=null;e.node.changedTopology()
                    if(e.node.parts.all { it==null })e.circuit.level.removeBlock(e.node.blockPos,false)
                }
            }
        }
    }
    fun persist() { p.thermalState.putDouble("delta",load.temperatureCelsius);p.thermalState.putFloat("ambientFilter",ambientFilter.get());p.thermalState.putDouble("ambientTimer",ambientTimer);furnace?.persist();turbine?.persist();fuelHeater?.persist();heatExchanger?.persist();electricalFurnace?.persist() }
}
