package mods.eln.modern

import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack

data class FabricatorOperation(val output: String,val count: Int=4,val chance: Double=1.0) {
    fun item()=Content.materialItems[output]?.get() ?: Content.partItems.getValue(output).get()
    fun stack()=ItemStack(item(),count)
}

object FabricatorOperations {
    val all=listOf(
        FabricatorOperation("transistor",16),FabricatorOperation("d_flip_flop_chip"),FabricatorOperation("jk_flip_flop_chip"),
        FabricatorOperation("arithmetic_logic_unit",2,.5),FabricatorOperation("pal_chip"),FabricatorOperation("oscillator_chip"),
        FabricatorOperation("opamp"),FabricatorOperation("pid_regulator"),FabricatorOperation("voltage_controlled_sawtooth_oscillator"),
        FabricatorOperation("voltage_controlled_sine_oscillator"),FabricatorOperation("amplifier"),FabricatorOperation("voltage_controlled_amplifier"),
        FabricatorOperation("configurable_summing_unit"),FabricatorOperation("sample_and_hold"),FabricatorOperation("lowpass_filter"))
    fun selected(p: PartState)=all[p.value.toInt().coerceIn(all.indices)]
    fun select(node: FaceNodeEntity,p: PartState,index: Int) {
        if(index !in all.indices)return
        p.gridState.putDouble("fabricatorDiscardedJ",p.gridState.getDouble("fabricatorDiscardedJ")+p.progress)
        p.progress=0.0;p.value=index.toDouble();node.changedTopology()
    }
}

/** Original 40-ohm / 4000 J wafer etching, with atomic inputs/output and paid failed ALU batches. */
class FabricatorRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private val inventory=PartInventory.get(e.node,p)
    private val load=ElectricalLoad().apply { serialResistance=.0125;state=p.voltage }
    private val resistor=Resistor(load,null).setResistance(1e9)
    init {
        e.circuit.root.addState(load);e.circuit.root.addComponent(resistor)
        // The original element uses the absolute north side, independent of its stored front.
        e.pins[FaceBasis.edges(p).indexOf(Direction.NORTH)]=load
        if(!p.gridState.contains("fabricatorRandom"))p.gridState.putLong("fabricatorRandom",e.circuit.level.random.nextLong())
    }
    private fun ready(): Boolean {
        val operation=FabricatorOperations.selected(p)
        val wafer=inventory.getStackInSlot(1);val copper=inventory.getStackInSlot(2);val output=inventory.getStackInSlot(0)
        return wafer.item==Content.materialItems.getValue("silicon_wafer").get() && copper.item==Content.materialItems.getValue("copper_plate").get() &&
            (output.isEmpty || ItemStack.isSameItemSameTags(output,operation.stack()) && output.count+operation.count<=output.maxStackSize)
    }
    fun beforeTick() { resistor.resistance=if(ready() && p.progress<=8000)40.0 else 1e9 }
    fun afterStep(dt: Double) {
        p.voltage=load.voltage;p.voltageB=0.0;p.current=load.current
        val work=resistor.power.coerceAtLeast(0.0)*dt;p.energy+=work
        if(resistor.resistance==40.0)p.progress+=work
        else p.gridState.putDouble("fabricatorIdleLossJ",p.gridState.getDouble("fabricatorIdleLossJ")+work)
        p.active=resistor.power>1;p.processFraction=(p.progress/4000).coerceIn(0.0,1.0)
    }
    fun afterTick() {
        if(p.progress<4000 || !ready())return
        val operation=FabricatorOperations.selected(p)
        inventory.extractItem(1,1,false);inventory.extractItem(2,1,false)
        p.progress-=4000
        p.gridState.putDouble("fabricatorBatchWorkJ",p.gridState.getDouble("fabricatorBatchWorkJ")+4000)
        p.gridState.putInt("fabricatorBatches",p.gridState.getInt("fabricatorBatches")+1)
        // Persist a device-local random stream so rebuilding/saving cannot reroll a paid batch.
        val random=p.gridState.getLong("fabricatorRandom")*6364136223846793005L+1442695040888963407L
        p.gridState.putLong("fabricatorRandom",random)
        val sample=(random ushr 11).toDouble()/9007199254740992.0
        if(sample<operation.chance) {
            val output=operation.stack();output.grow(inventory.getStackInSlot(0).count)
            inventory.setStackInSlot(0,output)
            p.gridState.putInt("fabricatorSuccessfulBatches",p.gridState.getInt("fabricatorSuccessfulBatches")+1)
        } else p.gridState.putInt("fabricatorFailedBatches",p.gridState.getInt("fabricatorFailedBatches")+1)
        e.node.setChanged();e.node.sync()
    }
}
