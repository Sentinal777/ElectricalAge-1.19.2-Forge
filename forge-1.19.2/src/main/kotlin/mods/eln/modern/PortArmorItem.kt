package mods.eln.modern

import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.*
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.level.Level

data class ArmorSpec(val slot: String,val protection: Int)

/** Copper uses the original iron material. E-Coal absorption is handled before ordinary armor. */
class PortArmorItem(val definition: MaterialDefinition,properties: Item.Properties): ArmorItem(
    if(definition.electrical==null)ArmorMaterials.IRON else ShieldMaterial,
    EquipmentSlot.valueOf(definition.armor!!.slot),properties) {
    override fun shouldCauseReequipAnimation(oldStack: ItemStack,newStack: ItemStack,slotChanged: Boolean)=
        if(definition.electrical!=null)slotChanged || oldStack.item!=newStack.item else super.shouldCauseReequipAnimation(oldStack,newStack,slotChanged)
    override fun getArmorTexture(stack: ItemStack,entity: Entity,slot: EquipmentSlot,type: String?)=
        "eln:textures/armor/${if(definition.electrical==null)"copper" else "ecoal"}_layer_${if(slot==EquipmentSlot.LEGS)2 else 1}.png"
    override fun isBarVisible(stack: ItemStack)=if(definition.electrical!=null)true else super.isBarVisible(stack)
    override fun getBarWidth(stack: ItemStack)=definition.electrical?.let { (13*ElectricalItems.energy(stack)/it.capacity).toInt().coerceIn(0,13) } ?: super.getBarWidth(stack)
    override fun getBarColor(stack: ItemStack)=definition.electrical?.let { net.minecraft.util.Mth.hsvToRgb((ElectricalItems.energy(stack)/it.capacity/3).toFloat(),1f,1f) } ?: super.getBarColor(stack)
    override fun isValidRepairItem(stack: ItemStack,ingredient: ItemStack)=definition.electrical==null && super.isValidRepairItem(stack,ingredient)
    override fun isEnchantable(stack: ItemStack)=definition.electrical==null && super.isEnchantable(stack)
    override fun canApplyAtEnchantingTable(stack: ItemStack,enchantment: net.minecraft.world.item.enchantment.Enchantment)=definition.electrical==null && super.canApplyAtEnchantingTable(stack,enchantment)
    override fun isBookEnchantable(stack: ItemStack,book: ItemStack)=definition.electrical==null && super.isBookEnchantable(stack,book)
    override fun appendHoverText(stack: ItemStack,level: Level?,lines: MutableList<Component>,flag: TooltipFlag) {
        super.appendHoverText(stack,level,lines,flag)
        definition.electrical?.let {
            lines.add(Component.translatable("tooltip.eln.portable_energy",ElectricalItems.energy(stack).toInt(),it.capacity.toInt()))
            lines.add(Component.translatable("tooltip.eln.armor_charge",it.chargePower.toInt()))
            lines.add(Component.translatable("tooltip.eln.armor_absorption",String.format(java.util.Locale.ROOT,"%.1f",ArmorRuntime.ratio(stack)*100)))
        }
    }
    private object ShieldMaterial: ArmorMaterial {
        override fun getDurabilityForSlot(slot: EquipmentSlot)=0
        override fun getDefenseForSlot(slot: EquipmentSlot)=0
        override fun getEnchantmentValue()=0
        override fun getEquipSound()=SoundEvents.ARMOR_EQUIP_IRON
        override fun getRepairIngredient()=Ingredient.EMPTY
        override fun getName()="eln:ecoal"
        override fun getToughness()=0f
        override fun getKnockbackResistance()=0f
    }
}
