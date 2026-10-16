package mods.eln.modern

import mods.eln.config.ForgePortConfig
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalGateOutputProcess
import net.minecraftforge.common.capabilities.ForgeCapabilities

/** Original comparator-like stationary scanner. Side-specific tanks take precedence over inventory. */
class InventoryScannerRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private val load=ElectricalLoad().apply { serialResistance=.05;state=p.voltage }
    private val output=NbtElectricalGateOutputProcess("scanner",load).apply {
        coulombs=.1*.05/ForgePortConfig.simulationSteps.get()/5
        setVoltageSafe(p.voltage)
    }
    init {
        e.pins[2]=load;e.circuit.root.addState(load);e.circuit.root.addComponent(output)
        e.circuit.root.addComponent(Resistor(load,null).pullDown())
    }
    fun beforeTick() {
        val direction=FaceBasis.edges(p)[0];val pos=e.node.blockPos.relative(direction);val level=e.circuit.level
        val reading=if(!level.hasChunkAt(pos))0.0 else {
            val target=level.getBlockEntity(pos)
            val fluid=target?.getCapability(ForgeCapabilities.FLUID_HANDLER,direction.opposite)?.orElse(null)
            val items=target?.getCapability(ForgeCapabilities.ITEM_HANDLER,direction.opposite)?.orElse(null)
            when {
                fluid!=null -> (0 until fluid.tanks).filter { fluid.getTankCapacity(it)>0 }.let { slots ->
                    if(slots.isEmpty())0.0 else slots.sumOf { fluid.getFluidInTank(it).amount.toDouble()/fluid.getTankCapacity(it) }/slots.size
                }
                items!=null -> {
                    val slots=(0 until items.slots).filter { items.getSlotLimit(it)>0 }
                    if(slots.isEmpty())0.0 else if(p.gridState.getInt("scannerMode")==1)slots.count { !items.getStackInSlot(it).isEmpty }.toDouble()/slots.size
                    else slots.sumOf { items.getStackInSlot(it).count.toDouble() }/slots.sumOf { items.getSlotLimit(it).toDouble() }
                }
                else -> {
                    val state=level.getBlockState(pos)
                    when { state.hasAnalogOutputSignal()->state.getAnalogOutputSignal(level,pos)/15.0;state.isAir->0.0;state.isSolidRender(level,pos)->1.0;else->1.0/3 }
                }
            }
        }.takeIf { it.isFinite() }?.coerceIn(0.0,1.0) ?: 0.0
        p.powerFactor=reading;output.outputNormalized=reading
    }
    fun beforeStep() { output.outputNormalized=p.powerFactor }
    fun afterStep() { p.voltage=load.voltage;p.current=load.current;p.active=p.powerFactor>0.0 }
    companion object {
        fun toggle(node: FaceNodeEntity,p: PartState) { p.gridState.putInt("scannerMode",1-p.gridState.getInt("scannerMode").coerceIn(0,1));node.setChanged();node.sync() }
    }
}
