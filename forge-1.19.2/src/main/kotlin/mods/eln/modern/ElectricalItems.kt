package mods.eln.modern

import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStack
import java.util.IdentityHashMap

data class FlashlightSpec(val lightLow: Int,val rangeLow: Int,val wattsLow: Double,val lightHigh: Int,val rangeHigh: Int,val wattsHigh: Double)
data class ElectricalItemSpec(val capacity: Double,val chargePower: Double,val dischargePower: Double,val priority: Int,val flashlight: FlashlightSpec?=null,val tool: ElectricalToolSpec?=null,val scanner: ScannerSpec?=null)

/** Original joule-valued item stores and half-second priority transfer, with shared per-item rate limits. */
object ElectricalItems {
    fun spec(stack: ItemStack)=when(val item=stack.item) { is MaterialItem->item.definition.electrical;is PortArmorItem->item.definition.electrical;else->null }
    fun energy(stack: ItemStack): Double {
        val spec=spec(stack) ?: return 0.0
        if(spec.scanner!=null)PortableScanner.initialize(stack)
        return (stack.tag?.getDouble(if(spec.scanner!=null)"e" else "energy") ?: 0.0).takeIf(Double::isFinite)?.coerceIn(0.0,spec.capacity) ?: 0.0
    }
    fun setEnergy(stack: ItemStack,joules: Double) {
        val spec=spec(stack) ?: return
        if(spec.scanner!=null)PortableScanner.initialize(stack)
        stack.orCreateTag.putDouble(if(spec.scanner!=null)"e" else "energy",if(joules.isFinite())joules.coerceIn(0.0,spec.capacity)else 0.0)
    }
    fun transfer(stacks: List<ItemStack>,seconds: Double=.5): Double {
        if(!seconds.isFinite() || seconds<=0)return 0.0
        val seen=java.util.Collections.newSetFromMap(IdentityHashMap<ItemStack,Boolean>())
        val items=stacks.filter { !it.isEmpty && spec(it)!=null && seen.add(it) }
        val input=IdentityHashMap<ItemStack,Double>();items.forEach { input[it]=spec(it)!!.chargePower*seconds }
        var total=0.0
        for(source in items.sortedByDescending { spec(it)!!.priority }) {
            val s=spec(source)!!;var available=minOf(energy(source),s.dischargePower*seconds)
            for(target in items.sortedBy { spec(it)!!.priority }) {
                val t=spec(target)!!
                if(t.priority>=s.priority || available<=0)continue
                val sent=minOf(available,input[target] ?: 0.0,(t.capacity-energy(target)).coerceAtLeast(0.0))
                if(sent<=0)continue
                setEnergy(source,energy(source)-sent);setEnergy(target,energy(target)+sent)
                available-=sent;input[target]=(input[target] ?: 0.0)-sent;total+=sent
            }
        }
        return total
    }
    fun tickPlayers(server: MinecraftServer) {
        if(server.tickCount%10!=0)return
        for(player in server.playerList.players) {
            val stacks=(player.inventory.armor.toList()+player.inventory.items.toList()+player.inventory.offhand.toList()).filter { !PortableTools.excluded(it,server.tickCount) }
            stacks.forEach { PortableLights.consume(it,.5);PortableScanner.consume(it,.5) }
            if(mods.eln.config.ForgePortConfig.infinitePortableBattery.get())for(stack in stacks)spec(stack)?.let { setEnergy(stack,energy(stack)+it.chargePower*.5) }
            else transfer(stacks)
            player.inventory.setChanged()
        }
    }
}
