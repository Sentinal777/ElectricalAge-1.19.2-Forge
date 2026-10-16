package mods.eln.modern

import net.minecraft.world.item.ItemStack
import kotlin.math.min

/** Original 4000 W rolling process, limited by the shaft's available joules at the 20 Hz device rate. */
class RollingShaftRuntime(private val e: ElementRuntime) {
    private val p=e.part;private val node=e.node
    private fun packed(recipe: ProcessingRecipe): ItemStack? {
        val results=recipe.outputs()
        if(results.size!=1)return null
        val result=results.single();val existing=node.inventory.getStackInSlot(2)
        if(!existing.isEmpty && !ItemStack.isSameItemSameTags(existing,result))return null
        if(existing.count+result.count>result.maxStackSize)return null
        return if(existing.isEmpty)result else existing.copy().apply { grow(result.count) }
    }
    fun afterTick() {
        val recipe=MachineRecipes.find(Behavior.PLATE_MACHINE,node.inventory.getStackInSlot(0),e.circuit.level)
        val key=recipe?.id?.toString().orEmpty()
        if(key!=p.recipeId) { p.recipeId=key;p.progress=0.0 }
        val output=recipe?.let(::packed)
        val ready=recipe!=null && output!=null && p.enabled && !p.tripped
        // Upstream's energy supplier returns min(4000, shaft.energy) as watts, not all stored work at once.
        val requested=if(ready)min(4000.0,e.circuit.mechanics.available(p))*.05 else 0.0
        val used=e.circuit.mechanics.consume(p,requested)
        if(!ready)p.progress=0.0 else p.progress+=used
        p.energy+=used;p.active=used>1e-8;p.powerFactor=used/.05/4000
        p.thermalState.putDouble("rollingWatts",used/.05)
        if(ready && p.progress>recipe!!.joules) {
            node.inventory.setStackInSlot(2,output!!);node.inventory.extractItem(0,recipe.inputCount,false)
            // The original StackMachineProcess resets its counter for the next operation.
            p.thermalState.putDouble("rollingWasteJ",p.thermalState.getDouble("rollingWasteJ")+p.progress-recipe.joules)
            p.progress=0.0;node.setChanged()
        }
        p.processFraction=if(ready)(p.progress/recipe!!.joules).coerceIn(0.0,1.0)else 0.0
    }
}
