package mods.eln.modern

import mods.eln.Eln
import mods.eln.config.ForgePortConfig
import mods.eln.mqtt.SignalPortMode
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalGateOutputProcess

/** Native persistent front/right/back/left MQTT controls; broker credentials never enter device state. */
object MqttDevices {
    fun isMeter(p: PartState)=p.definition.id=="mqtt_energy_meter"
    fun supported(p: PartState)=isMeter(p) || p.definition.behavior==Behavior.MQTT_CONTROLLER
    fun initialize(p: PartState) {
        if(p.gridState.getBoolean("mqttInitialized"))return
        p.gridState.putBoolean("mqttInitialized",true)
        p.gridState.putString("mqttName",if(isMeter(p))"Meter" else "Signal Controller")
        p.gridState.putString("mqttServer","");p.gridState.putIntArray("mqttModes",IntArray(4))
    }
    fun mode(p: PartState,index: Int)=SignalPortMode.values()[p.gridState.getIntArray("mqttModes").getOrElse(index) { 0 }.coerceIn(0,2)]
    fun reset(node: FaceNodeEntity,p: PartState) { EnergyMeters.setCounter(node,p,0.0);p.gridState.putDouble("meterTime",0.0);node.setChanged();node.sync() }
    fun desired(p: PartState,index: Int)=p.gridState.getDouble("mqttDesired$index").takeIf(Double::isFinite)?.coerceIn(0.0,5.0) ?: 0.0
    fun setDesired(p: PartState,index: Int,value: Double) {
        if(index !in 0..3 || !value.isFinite())return
        p.gridState.putDouble("mqttDesired$index",value.coerceIn(0.0,5.0))
    }
    fun configure(node: FaceNodeEntity,p: PartState,command: Int,param: Int,text: String): Boolean {
        if(!supported(p) || node.isRemoved || node.parts[p.face.get3DDataValue()]!==p || text.length>64 || text.any { it.code<32 })return false
        initialize(p)
        when(command) {
            1 -> p.gridState.putString("mqttName",text.trim())
            2 -> { MqttService.release(node,p);p.gridState.putString("mqttServer",text.trim()) }
            3 -> if(isMeter(p))reset(node,p)else {
                val index=param and 255;val mode=(param shr 8) and 255
                if(index !in 0..3 || mode !in 0..2)return false
                val modes=IntArray(4) { MqttDevices.mode(p,it).ordinal };modes[index]=mode;p.gridState.putIntArray("mqttModes",modes)
            }
            4 -> { if(!isMeter(p))return false;p.enabled=!p.enabled }
            5 -> { if(!isMeter(p))return false;p.gridState.putInt("meterEnergyUnit",(p.gridState.getInt("meterEnergyUnit")+1)%4) }
            6 -> { if(!isMeter(p))return false;p.gridState.putInt("meterTimeUnit",(p.gridState.getInt("meterTimeUnit")+1)%2) }
            else -> return false
        }
        node.setChanged();node.sync();return true
    }
}

/** Four independent source input/output gates; disabled and read ports retain their original high impedance. */
class MqttControllerRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private val loads=List(4) { i -> ElectricalLoad().apply {
        serialResistance=.05;state=p.pinVoltages[i];e.circuit.root.addState(this)
        e.circuit.root.addComponent(Resistor(this,null).setResistance(1e12));e.pins[i]=this
    } }
    private val gates=loads.mapIndexed { i,load -> NbtElectricalGateOutputProcess("mqtt$i",load).also(e.circuit.root::addComponent) }
    private val previousModes=IntArray(4) { -1 }
    init { MqttDevices.initialize(p);MqttService.attach(e.node,p);beforeTick() }
    fun beforeTick() {
        for(i in 0..3) {
            val mode=MqttDevices.mode(p,i)
            if(previousModes[i]!=mode.ordinal) {
                gates[i].setHighImpedance(mode!=SignalPortMode.WRITE)
                gates[i].coulombs=Eln.gateOutputCurrent*.05/ForgePortConfig.simulationSteps.get()/5/(if(mode==SignalPortMode.WRITE)1 else 1000)
                previousModes[i]=mode.ordinal
            }
            if(mode==SignalPortMode.WRITE)gates[i].setVoltageSafe(MqttDevices.desired(p,i))
        }
    }
    fun afterStep() {
        for(i in 0..3)p.pinVoltages[i]=loads[i].voltage
        p.voltage=loads[3].voltage;p.current=loads[3].current;p.active=(0..3).any { MqttDevices.mode(p,it)==SignalPortMode.WRITE }
    }
}
