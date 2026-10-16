package mods.eln.modern

import net.minecraft.world.item.ItemStack
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import kotlin.math.floor

/** E-Coal's priority-100 shield, including the original Forge 25-unit cap and integer damage charge.
 * Source: https://github.com/MinecraftForge/MinecraftForge/blob/1.7.10/src/main/java/net/minecraftforge/common/ISpecialArmor.java
 * Ordinary modern armor processes the remaining damage normally; no second E-Coal armor reduction.
 */
@Mod.EventBusSubscriber(modid="eln")
object ArmorRuntime {
    private fun armor(stack: ItemStack)=(stack.item as? PortArmorItem)?.takeIf { it.definition.electrical!=null }?.definition?.armor
    fun ratio(stack: ItemStack): Double {
        val spec=armor(stack) ?: return 0.0
        return minOf(1.0,ElectricalItems.energy(stack)/(spec.protection*500.0))*spec.protection/20.0
    }
    fun display(stack: ItemStack)=(ratio(stack)*20).toInt()
    fun absorb(stacks: Iterable<ItemStack>,damage: Double): Double {
        if(!damage.isFinite() || damage<=0)return damage
        var absorbed=0.0
        for(stack in stacks) {
            if(armor(stack)==null)continue
            val energy=ElectricalItems.energy(stack)
            val blocked=minOf(damage*ratio(stack),floor(energy/20.0)/25.0)
            if(blocked<=0)continue
            val cost=maxOf(1,floor(blocked).toInt())*500.0
            ElectricalItems.setEnergy(stack,(energy-cost).coerceAtLeast(0.0));absorbed+=blocked
        }
        return (damage-absorbed).coerceAtLeast(0.0)
    }
    @JvmStatic @SubscribeEvent(priority=EventPriority.LOWEST)
    fun hurt(event: LivingHurtEvent) {
        if(event.entity.level.isClientSide || event.amount<=0)return
        event.amount=absorb(event.entity.armorSlots,event.amount.toDouble()).toFloat()
    }
}
