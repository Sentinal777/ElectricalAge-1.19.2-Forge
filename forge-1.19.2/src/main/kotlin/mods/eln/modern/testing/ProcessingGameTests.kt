package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.*

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object ProcessingGameTests {
    @JvmStatic @GameTest(template="empty",timeoutTicks=170)
    fun countedInputBoostersAllOutputSlotsAutomationAndSavedWork(h: GameTestHelper) {
        fun create(id: String,x: Int,z: Int)=InfrastructureGameTests.create(h,id,x,z)
        create("electrical_source",1,2).parts[0]!!.value=200.0
        val normal=create("200v_magnetizer",2,2);normal.inventory.setStackInSlot(0,ItemStack(Items.IRON_INGOT))
        create("electrical_source",1,6).parts[0]!!.value=200.0
        val boosted=create("200v_magnetizer",2,6);boosted.inventory.setStackInSlot(0,ItemStack(Items.IRON_INGOT,4))
        boosted.inventory.setStackInSlot(1,ItemStack(Content.materialItems.getValue("machine_booster").get(),2))
        boosted.inventory.setStackInSlot(2,ItemStack(Items.DIRT,64));boosted.inventory.setStackInSlot(3,ItemStack(Items.DIRT,64));boosted.inventory.setStackInSlot(4,ItemStack(Items.DIRT,64))
        h.runAtTickTime(15) {
            if(normal.parts[0]!!.active || normal.parts[0]!!.progress>1e-4)h.fail("Two-ingot recipe consumed power with only one ingot")
            val p=boosted.parts[0]!!
            if(!p.active || p.powerFactor<1.8)h.fail("Two original boosters failed to increase resistor demand")
            if(abs(p.progress/p.energy-(1/1.1).pow(2))>1e-8)h.fail("Booster efficiency cost was not charged to electrical input")
            val stored=boosted.saveWithoutMetadata();boosted.load(stored);boosted.changedTopology()
            if(boosted.inventory.slots!=6 || boosted.inventory.getStackInSlot(1).count!=2 || boosted.inventory.getStackInSlot(4).count!=64)h.fail("Saved booster/output inventory was lost")
            normal.inventory.insertItem(0,ItemStack(Items.IRON_INGOT),false)
        }
        h.runAtTickTime(95) {
            val output=boosted.inventory.getStackInSlot(5)
            if(!output.`is`(Content.materialItems.getValue("basic_magnet").get()) || output.count!=2 || !boosted.inventory.getStackInSlot(0).isEmpty)
                h.fail("Four-output packing or two-item consumption failed: output=$output, input=${boosted.inventory.getStackInSlot(0)}")
            val side=boosted.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.NORTH).orElseThrow { IllegalStateException("No side extraction") }
            if(!side.insertItem(0,ItemStack(Items.IRON_INGOT),false).`is`(Items.IRON_INGOT))h.fail("Side extraction port accepted machine input")
            if(side.extractItem(4,1,true).isEmpty || boosted.inventory.getStackInSlot(5).count!=2)h.fail("Fourth-output simulated extraction failed")
            if(side.extractItem(4,1,false).isEmpty || boosted.inventory.getStackInSlot(5).count!=1)h.fail("Fourth-output automation failed")
            if(!normal.inventory.getStackInSlot(2).`is`(Content.materialItems.getValue("basic_magnet").get()))h.fail("Unboosted machine failed after counted input became available")
            h.succeed()
        }
    }
}
