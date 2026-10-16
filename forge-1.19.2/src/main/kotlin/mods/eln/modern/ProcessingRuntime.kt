package mods.eln.modern

import mods.eln.config.ForgePortConfig
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import net.minecraft.world.item.ItemStack
import kotlin.math.abs
import kotlin.math.pow

/** Original powered stack-machine equation, four outputs and five booster upgrades. */
class ProcessingRuntime(private val e: ElementRuntime) {
    private val p=e.part;private val d=p.definition;private val node=e.node
    private val load=ElectricalLoad().also { it.state=p.voltage;it.serialResistance=d.resistance;e.circuit.root.addState(it) }
    private val resistor=Resistor(load,null).setResistance(1e12).also(e.circuit.root::addComponent)
    private var recipe: ProcessingRecipe?=null
    private var efficiency=1.0
    init {
        e.circuit.root.addComponent(Resistor(load,null).setResistance(1e12))
        if(d.behavior!=Behavior.MAGNETIZER) { e.pins[0]=load;e.pins[2]=load }else e.pins.fill(load)
    }
    private fun packed(results: List<ItemStack>): List<ItemStack>? {
        val slots=(2..5).map { node.inventory.getStackInSlot(it).copy() }.toMutableList()
        for(result in results) {
            val pending=result.copy()
            for(slot in slots)if(!slot.isEmpty && ItemStack.isSameItemSameTags(slot,pending)) {
                val count=minOf(pending.count,slot.maxStackSize-slot.count);slot.grow(count);pending.shrink(count)
            }
            for(i in slots.indices)if(slots[i].isEmpty && !pending.isEmpty) {
                val count=minOf(pending.count,pending.maxStackSize);slots[i]=pending.copy().apply { this.count=count };pending.shrink(count)
            }
            if(!pending.isEmpty)return null
        }
        return slots
    }
    fun beforeTick() {
        val found=MachineRecipes.find(d.behavior,node.inventory.getStackInSlot(0),e.circuit.level)
        val key=found?.id?.toString().orEmpty()
        if(key!=p.recipeId) { p.progress=0.0;p.recipeId=key }
        recipe=found?.takeIf { packed(it.outputs())!=null }
        val upgrades=node.inventory.getStackInSlot(1).takeIf { (it.item as? MaterialItem)?.definition?.kind=="MACHINE_BOOSTER" }?.count?.coerceIn(0,5) ?: 0
        efficiency=(1/1.1).pow(upgrades)
        val speed=(1.25/(1/1.1)).pow(upgrades)
        resistor.resistance=if(p.enabled && !p.tripped && recipe!=null)d.nominalVoltage.pow(2)/d.power/speed else 1e12
    }
    fun afterStep(dt: Double) {
        p.voltage=load.voltage;p.voltageB=0.0;p.current=resistor.current
        val power=resistor.power.coerceAtLeast(0.0)
        p.energy+=power*dt
        if(p.enabled && !p.tripped && recipe!=null)p.progress+=power*efficiency*dt
        p.pinVoltages=DoubleArray(4) { e.pins[it]?.voltage ?: 0.0 }
    }
    fun afterTick() {
        recipe?.let { r ->
            if(p.enabled && p.progress>=r.joules) {
                packed(r.outputs())?.let { output ->
                    output.forEachIndexed { i,s -> node.inventory.setStackInSlot(i+2,s) }
                    node.inventory.extractItem(0,r.inputCount,false);p.progress-=r.joules;node.setChanged()
                }
            }
        }
        p.active=p.enabled && !p.tripped && recipe!=null && resistor.power>1
        p.powerFactor=if(p.active)resistor.power/d.power else 0.0
        p.processFraction=(p.progress/(recipe?.joules ?: 1.0)).coerceIn(0.0,1.0)
        val overflow=abs(p.voltage)-d.nominalVoltage*1.3
        if(overflow<=0 || p.wasOvervoltage)p.voltageFault=(p.voltageFault+overflow*.05).coerceAtLeast(0.0)
        p.wasOvervoltage=overflow>0
        if(ForgePortConfig.cableVoltageProtection.get() && p.voltageFault>d.nominalVoltage*.25) {
            p.enabled=false;p.tripped=true;node.sync();e.circuit.level.destroyBlock(node.blockPos,true)
        }
    }
}
