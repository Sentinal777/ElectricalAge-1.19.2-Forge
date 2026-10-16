package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object PortableEnergyGameTests {
    fun item(id: String,joules: Double=0.0)=ItemStack(Content.materialItems.getValue(id).get()).also { ElectricalItems.setEnergy(it,joules) }
    @JvmStatic @GameTest(batch="portable_energy",template="grid_empty",timeoutTicks=40)
    fun originalPortableStoresRetainTheirSeparateRatingsAndBoundedEnergyAcrossItemNbt(h: GameTestHelper) {
        for((id,expected) in listOf(
            "portable_battery" to ElectricalItemSpec(40000.0,125.0,250.0,2),"portable_battery_pack" to ElectricalItemSpec(160000.0,500.0,1000.0,2),
            "portable_condensator" to ElectricalItemSpec(4000.0,2000.0,2000.0,1),"portable_condensator_pack" to ElectricalItemSpec(16000.0,8000.0,8000.0,1))) {
            val stack=item(id);val spec=ElectricalItems.spec(stack)!!;val cap=expected.capacity
            if(stack.maxStackSize!=1 || spec!=expected || ElectricalItems.energy(stack)!=0.0)h.fail("Original portable store ratings or empty single-item state were lost: $id")
            ElectricalItems.setEnergy(stack,cap/2+.125);val restored=ItemStack.of(stack.save(CompoundTag()))
            if(ElectricalItems.energy(restored)!=cap/2+.125)h.fail("Portable item NBT rounded or lost fractional energy")
            ElectricalItems.setEnergy(stack,Double.NaN);if(ElectricalItems.energy(stack)!=0.0)h.fail("Nonfinite portable energy was accepted")
            ElectricalItems.setEnergy(stack,cap*2);if(ElectricalItems.energy(stack)!=cap)h.fail("Portable store exceeded its original capacity")
        }
        h.succeed()
    }
    @JvmStatic @GameTest(batch="portable_energy",template="grid_empty",timeoutTicks=40)
    fun halfSecondPriorityTransferSharesDestinationRateAndNeverDuplicatesReferencedStacks(h: GameTestHelper) {
        val target=item("portable_condensator");val sources=List(3) { item("portable_battery_pack",10000.0) }
        val transferred=ElectricalItems.transfer(listOf(target)+sources)
        if(transferred!=1000.0 || ElectricalItems.energy(target)!=1000.0 || abs(sources.sumOf(ElectricalItems::energy)+ElectricalItems.energy(target)-30000)>1e-9)h.fail("Multiple batteries exceeded one capacitor's half-second charge limit or created energy")
        val equal=item("portable_battery_pack");ElectricalItems.transfer(listOf(sources[0],equal))
        if(ElectricalItems.energy(equal)!=0.0)h.fail("Original equal-priority portable stores equalized their charge")
        val one=item("portable_battery_pack",1000.0);val cap=item("portable_condensator")
        if(ElectricalItems.transfer(listOf(one,one,cap,cap))!=500.0 || ElectricalItems.energy(one)!=500.0 || ElectricalItems.energy(cap)!=500.0)h.fail("Duplicate stack references bypassed physical discharge limits")
        if(ElectricalItems.transfer(listOf(one,cap),Double.NaN)!=0.0 || ElectricalItems.transfer(listOf(one,cap),-1.0)!=0.0)h.fail("Invalid transfer time moved portable energy")
        h.succeed()
    }
}
