package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.ItemStack
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object ArmorGameTests {
    private val names=listOf("helmet","chestplate","leggings","boots")
    private val slots=listOf(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)
    @JvmStatic @GameTest(batch="portable_armor",template="grid_empty",timeoutTicks=40)
    fun originalCopperArmorAndElectricalShieldKeepTheirSeparateRatingsSlotsTexturesAndSavedCharge(h: GameTestHelper) {
        for(i in names.indices) {
            val copper=ItemStack(Content.materialItems.getValue("copper_${names[i]}").get());val c=copper.item as ArmorItem
            val electric=PortableEnergyGameTests.item("e_coal_${names[i]}",8000.0);val e=electric.item as PortArmorItem
            if(c.slot!=slots[i] || c.defense!=listOf(2,6,5,2)[i] || copper.maxDamage!=listOf(165,240,225,195)[i] || copper.maxStackSize!=1)h.fail("Original iron-material copper armor changed its slot, protection or durability")
            val spec=ElectricalItems.spec(electric)!!
            if(e.slot!=slots[i] || e.defense!=0 || electric.maxStackSize!=1 || electric.isDamageableItem || spec!=ElectricalItemSpec(8000.0,2000.0,0.0,0) || e.isEnchantable(electric))h.fail("E-Coal armor lost original charge ratings or applied ordinary armor reduction twice")
            ElectricalItems.setEnergy(electric,3456.125);val restored=ItemStack.of(electric.save(CompoundTag()))
            if(ElectricalItems.energy(restored)!=3456.125)h.fail("Equippable electrical armor lost fractional stored energy")
            val p=h.makeMockPlayer()
            if(!e.getArmorTexture(electric,p,slots[i],null).endsWith(if(slots[i]==EquipmentSlot.LEGS)"ecoal_layer_2.png" else "ecoal_layer_1.png"))h.fail("Original E-Coal armor selected the wrong texture layer")
        }
        h.succeed()
    }
    @JvmStatic @GameTest(batch="portable_armor",template="grid_empty",timeoutTicks=40)
    fun originalShieldAbsorptionUsesPerPieceChargeScaleQuantizedCapAndIntegerEnergyCost(h: GameTestHelper) {
        val full=names.map { PortableEnergyGameTests.item("e_coal_$it",8000.0) }
        if(ArmorRuntime.absorb(full,10.0)>1e-12 || full.sumOf(ElectricalItems::energy)!=27500.0)h.fail("Original full E-Coal set did not absorb all ten damage with its original 4500 J integer damage cost")
        val partial=PortableEnergyGameTests.item("e_coal_helmet",750.0)
        if(abs(ArmorRuntime.absorb(listOf(partial),10.0)-9.25)>1e-12 || ElectricalItems.energy(partial)!=250.0 || ArmorRuntime.display(partial)!=0)h.fail("Partly charged shield lost original proportional absorption and minimum 500 J damage cost")
        ElectricalItems.setEnergy(partial,30.0)
        if(abs(ArmorRuntime.absorb(listOf(partial),1000.0)-999.96)>1e-9 || ElectricalItems.energy(partial)!=0.0)h.fail("Shield exceeded its original 25-unit finite absorption cap")
        ElectricalItems.setEnergy(partial,10.0)
        if(ArmorRuntime.absorb(listOf(partial),1000.0)!=1000.0 || ElectricalItems.energy(partial)!=10.0)h.fail("Below-cap shield charge created protection")
        h.succeed()
    }
    @JvmStatic @GameTest(batch="portable_armor",template="grid_empty",timeoutTicks=65)
    fun actuallyWornElectricalArmorProtectsThroughLivingDamageAndEmptyPiecesStopAbsorbing(h: GameTestHelper) {
        GameTestYards.clear(h,190,1,5);val base=h.absolutePos(BlockPos.ZERO)
        val cow=EntityType.COW.create(h.level)!!;cow.isNoAi=true;cow.setNoGravity(true);cow.moveTo(base.x+5.5,190.0,base.z+5.5,0f,0f)
        cow.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=100.0;cow.health=100f;h.level.addFreshEntity(cow)
        val pieces=names.map { PortableEnergyGameTests.item("e_coal_$it",8000.0) }
        for(i in slots.indices)cow.setItemSlot(slots[i],pieces[i])
        h.runAtTickTime(5) {
            if(cow.armorValue!=0)h.fail("Electrical shielding also added ordinary armor attributes")
            cow.hurt(DamageSource.GENERIC,10f)
            if(cow.health!=100f || pieces.sumOf(ElectricalItems::energy)!=27500.0)h.fail("Actual worn shield did not absorb damage and spend original item energy")
            pieces.forEach { ElectricalItems.setEnergy(it,0.0) };cow.invulnerableTime=0
            cow.hurt(DamageSource.GENERIC,10f)
            if(cow.health!=90f)h.fail("Empty electrical armor continued absorbing actual damage")
            cow.discard();h.succeed()
        }
    }
}
