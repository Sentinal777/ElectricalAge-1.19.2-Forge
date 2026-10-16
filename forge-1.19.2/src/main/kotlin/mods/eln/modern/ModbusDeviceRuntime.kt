package mods.eln.modern

import mods.eln.Eln
import mods.eln.config.ForgePortConfig
import mods.eln.modbus.ModbusDevice
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalGateOutputProcess
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag

/** Original RTU unit ID, four eight-address physical slots, and four-address radio slots. */
object ModbusDevices {
    fun initialize(p: PartState) {
        if(p.gridState.getBoolean("modbusInitialized"))return
        p.gridState.putBoolean("modbusInitialized",true);p.gridState.putInt("modbusStation",-1)
        for(i in 0..3)p.gridState.putBoolean("modbusHi$i",true)
        p.gridState.put("modbusRadios",ListTag())
    }
    fun entries(p: PartState): List<CompoundTag> = p.gridState.getList("modbusRadios",10).map { it as CompoundTag }
    fun output(p: PartState,i: Int)=p.gridState.getDouble("modbusOut$i").takeIf(Double::isFinite)?.coerceIn(0.0,1.0) ?: 0.0
    fun pin(p: PartState,i: Int): Int {
        // Source LRDU is relative to the mounting face, independent of the housing's front rotation.
        val right=when(p.face) { Direction.WEST,Direction.UP -> Direction.SOUTH;Direction.EAST,Direction.DOWN -> Direction.NORTH;Direction.NORTH -> Direction.WEST;Direction.SOUTH -> Direction.EAST }
        val up=if(p.face.axis==Direction.Axis.Y)Direction.EAST else Direction.UP
        return FaceBasis.edges(p).indexOf(listOf(right.opposite,right,up.opposite,up)[i])
    }
    fun configure(node: FaceNodeEntity,p: PartState,command: Int,uuid: Int,number: Int,text: String): Boolean {
        if(p.definition.behavior!=Behavior.MODBUS || node.isRemoved || node.parts[p.face.get3DDataValue()]!==p || text.length>64 || text.any { it.code<32 })return false
        initialize(p)
        when(command) {
            1 -> { if(number !in -1..255)return false;p.gridState.putInt("modbusStation",number) }
            2 -> p.gridState.putString("modbusName",text.trim())
            3,4 -> {
                val list=p.gridState.getList("modbusRadios",10);if(list.size>=64)return false
                val id=(entries(p).maxOfOrNull { it.getInt("uuid") } ?: 0)+1
                list.add(CompoundTag().apply { putInt("uuid",id);putBoolean("tx",command==3);putInt("offset",-1);putString("channel",text.trim());putDouble("value",0.0) })
            }
            5 -> {
                val entry=entries(p).firstOrNull { it.getInt("uuid")==uuid } ?: return false
                if(number != -1 && (number !in 0..65532 || number<48 && number+4>16 || entries(p).any { it!==entry && it.getInt("offset")>=0 && number<it.getInt("offset")+4 && number+4>it.getInt("offset") }))return false
                entry.putString("channel",text.trim());entry.putInt("offset",number)
            }
            6 -> {
                val list=p.gridState.getList("modbusRadios",10);val index=entries(p).indexOfFirst { it.getInt("uuid")==uuid };if(index<0)return false;list.removeAt(index)
            }
            else -> return false
        }
        ModbusService.reconfigure(node,p);node.setChanged();node.sync();return true
    }
}

class PartModbusRegisters(private val p: PartState): ModbusDevice {
    private val readLatch=hashMapOf<Pair<Int,Boolean>,Int>()
    private val writeLatch=hashMapOf<Int,Int>()
    private fun slot(address: Int): Pair<Int,CompoundTag?> {
        if(address in 16..47)return (address-16)/8 to null
        val radio=ModbusDevices.entries(p).first { it.getInt("offset")>=0 && address in it.getInt("offset") until it.getInt("offset")+4 }
        return radio.getInt("uuid")+4 to radio
    }
    private fun offset(address: Int,radio: CompoundTag?)=if(radio==null)(address-16)%8 else address-radio.getInt("offset")
    override fun valid(address: Int)=address in 16..47 || ModbusDevices.entries(p).any { it.getInt("offset")>=0 && address in it.getInt("offset") until it.getInt("offset")+4 }
    override fun bit(address: Int,input: Boolean): Boolean {
        val (index,radio)=slot(address);val offset=offset(address,radio)
        if(radio!=null)return when(offset) { 0 -> !radio.getBoolean("tx") && radio.getBoolean("connected");1 -> radio.getDouble("value")>=.5;else -> false }
        return when(offset) { 0 -> !input && p.gridState.getBoolean("modbusHi$index");1 -> if(input)p.pinVoltages[ModbusDevices.pin(p,index)]>3.0 else ModbusDevices.output(p,index)>=.5;else -> false }
    }
    override fun word(address: Int,input: Boolean): Int {
        val (index,radio)=slot(address);val offset=offset(address,radio)
        val value=if(radio!=null)radio.getDouble("value") else if(input)(p.pinVoltages[ModbusDevices.pin(p,index)]/5).coerceIn(0.0,1.0) else ModbusDevices.output(p,index)
        return when(offset) {
            0 -> if(radio!=null && !radio.getBoolean("tx") && radio.getBoolean("connected"))1 else 0
            1 -> { val bits=value.toFloat().toRawBits();readLatch[index to input]=bits and 65535;(bits ushr 16) and 65535 }
            2 -> readLatch[index to input] ?: 0
            3 -> (65535*value).toInt() and 65535
            else -> 0
        }
    }
    private fun set(index: Int,radio: CompoundTag?,value: Double) {
        if(!value.isFinite())return
        val safe=value.coerceIn(0.0,1.0)
        if(radio==null)p.gridState.putDouble("modbusOut$index",safe)else if(radio.getBoolean("tx"))radio.putDouble("value",safe)
    }
    override fun writeBit(address: Int,value: Boolean) {
        val (index,radio)=slot(address)
        when(offset(address,radio)) { 0 -> if(radio==null)p.gridState.putBoolean("modbusHi$index",value);1 -> set(index,radio,if(value)1.0 else 0.0) }
    }
    override fun writeWord(address: Int,value: Int) {
        val (index,radio)=slot(address)
        if(radio!=null && !radio.getBoolean("tx"))return
        when(offset(address,radio)) {
            1 -> writeLatch[index]=value and 65535
            2 -> set(index,radio,Float.fromBits(((writeLatch[index] ?: 0) shl 16) or (value and 65535)).toDouble())
            3 -> set(index,radio,(value and 65535)/65535.0)
        }
    }
}

class ModbusRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private val loads=List(4) { i -> ElectricalLoad().apply {
        serialResistance=.05;state=p.pinVoltages[ModbusDevices.pin(p,i)];e.circuit.root.addState(this)
        e.circuit.root.addComponent(Resistor(this,null).setResistance(1e12));e.pins[ModbusDevices.pin(p,i)]=this
    } }
    private val gates=loads.mapIndexed { i,load -> NbtElectricalGateOutputProcess("modbus$i",load).also(e.circuit.root::addComponent) }
    private val modes=Array<Boolean?>(4) { null }
    init { ModbusDevices.initialize(p);ModbusService.attach(e.node,p);beforeTick() }
    fun beforeTick() {
        for(i in 0..3) {
            val high=p.gridState.getBoolean("modbusHi$i")
            if(modes[i]!=high) { gates[i].setHighImpedance(high);gates[i].coulombs=Eln.gateOutputCurrent*.05/ForgePortConfig.simulationSteps.get()/5/(if(high)1000 else 1);modes[i]=high }
            if(!high)gates[i].setVoltageSafe(ModbusDevices.output(p,i)*5)
        }
    }
    fun afterStep() {
        for(i in 0..3)p.pinVoltages[ModbusDevices.pin(p,i)]=loads[i].voltage
        p.voltage=loads[0].voltage;p.current=loads[0].current;p.active=(0..3).any { !p.gridState.getBoolean("modbusHi$it") }
    }
}
