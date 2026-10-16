package mods.eln.modern

import mods.eln.Eln
import mods.eln.config.ForgePortConfig
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalGateOutputProcess
import mods.eln.solver.Equation
import mods.eln.solver.ISymbole
import net.minecraft.nbt.CompoundTag

/** Original ElectricalMath hardware and expression state, with independently connected inputs. */
object SignalProcessors {
    const val outputPin=3
    val inputPins=intArrayOf(0,1,2)
    fun handler(node: FaceNodeEntity)=object : net.minecraftforge.items.IItemHandler {
        private fun inventory()=if(node.isRemoved)null else node.parts.filterNotNull().firstOrNull { it.definition.behavior==Behavior.PROCESSOR }?.let { PartInventory.get(node,it) }
        override fun getSlots()=1
        override fun getStackInSlot(slot: Int)=if(slot==0)inventory()?.getStackInSlot(slot) ?: net.minecraft.world.item.ItemStack.EMPTY else net.minecraft.world.item.ItemStack.EMPTY
        override fun insertItem(slot: Int,stack: net.minecraft.world.item.ItemStack,simulate: Boolean)=if(slot==0)inventory()?.insertItem(slot,stack,simulate) ?: stack else stack
        override fun extractItem(slot: Int,amount: Int,simulate: Boolean)=if(slot==0)inventory()?.extractItem(slot,amount,simulate) ?: net.minecraft.world.item.ItemStack.EMPTY else net.minecraft.world.item.ItemStack.EMPTY
        override fun getSlotLimit(slot: Int)=if(slot==0)64 else 0
        override fun isItemValid(slot: Int,stack: net.minecraft.world.item.ItemStack)=slot==0 && inventory()?.isItemValid(slot,stack)==true
    }
    fun configure(node: FaceNodeEntity,p: PartState,text: String): Boolean {
        if(text.length>256 || text.any { it.code<32 })return false
        if(p.expression!=text) { p.expression=text;p.equationState=CompoundTag() }
        node.changedTopology();return true
    }
}

class SignalProcessorRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private val compiledExpression=p.expression
    private val equation=Equation().apply { setUpDefaultOperatorAndMapper();iterationLimit=100 }
    private fun load(index: Int)=ElectricalLoad().apply {
        serialResistance=.05;state=p.pinVoltages[index]
        e.circuit.root.addState(this);e.circuit.root.addComponent(Resistor(this,null).setResistance(100000.0))
    }
    private val output=load(SignalProcessors.outputPin)
    private val inputs=SignalProcessors.inputPins.map(::load)
    private val gate=NbtElectricalGateOutputProcess("processor",output).apply {
        coulombs=Eln.gateOutputCurrent*.05/ForgePortConfig.simulationSteps.get()/Eln.SVU
        setVoltageSafe(p.gridState.getDouble("processorOutput"))
    }
    init {
        val symbols=inputs.mapIndexed { i,input -> object : ISymbole {
            override fun getName()=('A'.code+i).toChar().toString()
            override fun getValue()=(input.voltage/5).coerceIn(0.0,1.0)
        } }
        equation.symbolList.addAll(symbols)
        equation.symbolList.add(object : ISymbole {
            override fun getName()="daytime"
            override fun getValue()=e.circuit.level.dayTime/(24000.0-1.0)
        })
        equation.preProcess(p.expression);equation.readFromNBT(p.equationState,"")
        e.pins[SignalProcessors.outputPin]=output
        for(i in inputs.indices)if(equation.isSymboleUsed(symbols[i]))e.pins[SignalProcessors.inputPins[i]]=inputs[i]
        e.circuit.root.addComponent(gate)
        p.gridState.putBoolean("processorValid",equation.isValid)
        p.gridState.putInt("processorRedstone",if(equation.isValid)equation.operatorCount else 0)
    }
    fun beforeTick() {
        val ready=equation.isValid && equation.operatorCount<=PartInventory.peek(p,0).count
        p.gridState.putBoolean("processorReady",ready)
        val value=if(ready)equation.getValue(.05)else 0.0
        gate.setVoltageSafe(if(value.isFinite())value.coerceIn(0.0,1.0)*5 else 0.0)
        p.gridState.putDouble("processorOutput",gate.voltage)
        val active=ready && equation.beepList.any { it.active }
        p.gridState.putBoolean("processorBeep",active)
        p.gridState.putFloat("processorPitch",if(active)equation.beepList.filter { it.active }.maxOf { it.pitch }.coerceAtLeast(1.0).coerceAtMost(4.0).toFloat()else 1f)
        p.gridState.putFloat("processorVolume",if(active)equation.beepList.filter { it.active }.maxOf { it.volume }.coerceIn(.5,1.0).toFloat()else 0f)
    }
    fun afterStep() {
        for(i in 0..3)p.pinVoltages[i]=e.pins[i]?.voltage ?: 0.0
        p.voltage=output.voltage;p.current=output.current;p.active=p.gridState.getBoolean("processorReady")
    }
    fun persist() { if(p.expression==compiledExpression)equation.writeToNBT(p.equationState,"") }
}

/** A real original gate input, rather than the preview's four shorted terminals. */
class VuMeterRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private val input=ElectricalLoad().apply { serialResistance=.05;state=p.voltage }
    init { e.circuit.root.addState(input);e.circuit.root.addComponent(Resistor(input,null).setResistance(100000.0));e.pins[3]=input }
    fun afterStep() { p.voltage=input.voltage;p.current=input.current;for(i in 0..3)p.pinVoltages[i]=if(i==3)input.voltage else 0.0 }
}
