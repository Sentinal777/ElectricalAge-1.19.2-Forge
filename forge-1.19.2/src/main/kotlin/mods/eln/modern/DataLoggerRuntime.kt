package mods.eln.modern

import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import kotlin.math.abs

/** Source DataLogs: newest first, 256 signed-byte samples and a saved partial averaging interval. */
object DataLoggers {
    val units=listOf("V","A","W","°C","%","J","","°F","% RH")
    fun initialize(p: PartState) {
        val s=p.gridState
        if(s.getBoolean("loggerInitialized"))return
        s.putBoolean("loggerInitialized",true);s.putDouble("loggerPeriod",.5);s.putDouble("loggerMin",0.0);s.putDouble("loggerMax",100.0)
        s.putInt("loggerUnit",4);s.putBoolean("loggerZero",true);s.putDouble("loggerLeft",0.0)
        if(p.history.isNotEmpty())s.putByteArray("loggerSamples",p.history.takeLast(256).asReversed().map { quantize(it) }.toByteArray())
    }
    fun period(p: PartState)=p.gridState.getDouble("loggerPeriod").takeIf { it.isFinite() && it>=.05 } ?: .5
    fun samples(p: PartState)=p.gridState.getByteArray("loggerSamples").take(256).toByteArray()
    fun quantize(voltage: Double)=((voltage/5).coerceIn(0.0,1.0)*255.5-128).toInt().toByte()
    fun physical(sample: Byte,low: Double,high: Double)=low+(sample.toInt()+128)/255.0*(high-low)
    fun reset(p: PartState) { p.gridState.putByteArray("loggerSamples",byteArrayOf());p.history.clear();p.gridState.putLong("loggerSum",0);p.gridState.putInt("loggerCount",0) }
    fun configure(node: FaceNodeEntity,p: PartState,period: Double,low: Double,high: Double,unit: Int,zero: Boolean): Boolean {
        if(!period.isFinite() || period !in .05..86400.0 || !low.isFinite() || !high.isFinite() || abs(low)>1e12 || abs(high)>1e12 || low==high || unit !in units.indices)return false
        initialize(p)
        if(period!=DataLoggers.period(p)) { reset(p);p.gridState.putDouble("loggerLeft",.1) }
        val s=p.gridState;s.putDouble("loggerPeriod",period);s.putDouble("loggerMin",low);s.putDouble("loggerMax",high);s.putInt("loggerUnit",unit);s.putBoolean("loggerZero",zero)
        node.setChanged();node.sync();return true
    }
    fun command(node: FaceNodeEntity,p: PartState,command: Int) {
        initialize(p)
        when(command) {
            1 -> reset(p)
            2 -> p.gridState.putBoolean("loggerPaused",!p.gridState.getBoolean("loggerPaused"))
            3 -> print(node,p)
        }
        node.setChanged();node.sync()
    }
    fun print(node: FaceNodeEntity,p: PartState): Boolean {
        val inventory=PartInventory.get(node,p)
        if(inventory.getStackInSlot(0).item!=net.minecraft.world.item.Items.PAPER || !inventory.getStackInSlot(1).isEmpty)return false
        val result=ItemStack(Content.materialItems.getValue("data_logger_print").get())
        result.tag=snapshot(p);inventory.extractItem(0,1,false);inventory.setStackInSlot(1,result);return true
    }
    fun snapshot(p: PartState)=CompoundTag().also { t ->
        t.putByteArray("log",samples(p));t.putDouble("samplingPeriod",period(p));t.putDouble("minValue",p.gridState.getDouble("loggerMin"));t.putDouble("maxValue",p.gridState.getDouble("loggerMax"));t.putByte("unitType",p.gridState.getInt("loggerUnit").toByte());t.putBoolean("showZeroLine",p.gridState.getBoolean("loggerZero"))
    }
}

class DataLoggerRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private val input=ElectricalLoad().apply { serialResistance=.05;state=p.voltage }
    init {
        DataLoggers.initialize(p);e.circuit.root.addState(input);e.circuit.root.addComponent(Resistor(input,null).setResistance(100000.0));e.pins[1]=input
    }
    fun afterStep(dt: Double) {
        p.voltage=input.voltage;p.current=input.current
        val s=p.gridState
        if(s.getBoolean("loggerPaused"))return
        val sum=s.getLong("loggerSum")+DataLoggers.quantize(input.voltage).toLong();val count=s.getInt("loggerCount")+1
        val left=s.getDouble("loggerLeft")-dt
        if(left<=1e-12) {
            val sample=(sum/count).toByte();val history=byteArrayOf(sample)+DataLoggers.samples(p).take(255).toByteArray()
            s.putByteArray("loggerSamples",history);s.putLong("loggerSum",0);s.putInt("loggerCount",0);s.putDouble("loggerLeft",left+DataLoggers.period(p))
            p.history=history.reversed().map { (it.toInt()+128)*5.0/255 }.toMutableList()
        }else { s.putLong("loggerSum",sum);s.putInt("loggerCount",count);s.putDouble("loggerLeft",left) }
    }
}
