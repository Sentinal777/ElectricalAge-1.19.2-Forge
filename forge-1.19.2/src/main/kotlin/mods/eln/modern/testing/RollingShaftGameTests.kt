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
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object RollingShaftGameTests {
    private fun rig(h: GameTestHelper): Pair<FaceNodeEntity,FaceNodeEntity> {
        val roller=TurbineGameTests.create(h,"rolling_shaft");val wheel=TurbineGameTests.create(h,"flywheel",3,4)
        roller.parts[0]!!.shaftGroup="rolling-test-primer";wheel.parts[0]!!.shaftGroup="rolling-test-primer"
        wheel.parts[0]!!.shaftEnergy=20000.0;wheel.changedTopology();return roller to wheel
    }
    @JvmStatic @GameTest(batch="shaft_rolling",template="grid_empty",timeoutTicks=135)
    fun originalRollingRateUsesFiniteShaftWorkAndRetainsItsPartialRecipe(h: GameTestHelper) {
        val (roller,wheel)=rig(h);roller.inventory.setStackInSlot(0,ItemStack(Items.IRON_INGOT))
        h.runAtTickTime(20) {
            val p=roller.parts[0]!!
            if(abs(p.energy-4000.0)>1e-6 || abs(p.progress-4000.0)>1e-6 || roller.inventory.getStackInSlot(2).count!=0)h.fail("Original rolling rate was not exactly 4000 W at the physical device rate: ${p.save()}")
            val saved=roller.saveWithoutMetadata();roller.load(saved);roller.changedTopology()
            if(roller.parts[0]!!.progress!=4000.0)h.fail("Rolling machine lost its partial recipe on reload")
        }
        h.runAtTickTime(90) {
            val p=roller.parts[0]!!
            if(!roller.inventory.getStackInSlot(0).isEmpty || !roller.inventory.getStackInSlot(2).`is`(Content.materialItems.getValue("iron_plate").get()) || p.energy<=10000 || p.progress!=0.0 || p.energy+p.shaftEnergy+wheel.parts[0]!!.shaftEnergy>20000+1e-6)h.fail("Original rolling recipe failed input/output or finite mechanical accounting")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="shaft_rolling",template="grid_empty",timeoutTicks=170)
    fun originalRollingHasOneOutputAndRespectsCountedInputAndSidedAutomation(h: GameTestHelper) {
        val (roller,_)=rig(h)
        val top=roller.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.UP).orElseThrow { IllegalStateException("Missing rolling feed") }
        val side=roller.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.EAST).orElseThrow { IllegalStateException("Missing rolling output") }
        if(top.slots!=2 || side.slots!=2)h.fail("Rolling automation exposed extra output or upgrade slots")
        val silicon=Content.materialItems.getValue("silicon_ingot").get()
        if(!top.insertItem(0,ItemStack(silicon,2),false).isEmpty)h.fail("Top feed rejected an original rolling ingredient")
        h.runAtTickTime(20) {
            if(roller.parts[0]!!.energy!=0.0 || roller.inventory.getStackInSlot(0).count!=2)h.fail("Insufficient four-ingot silicon recipe consumed work or items")
            top.insertItem(0,ItemStack(silicon,2),false)
            roller.inventory.setStackInSlot(2,ItemStack(Items.COBBLESTONE,64))
        }
        h.runAtTickTime(40) {
            if(roller.parts[0]!!.energy!=0.0 || roller.inventory.getStackInSlot(0).count!=4)h.fail("Blocked rolling output consumed mechanical work")
            roller.inventory.setStackInSlot(2,ItemStack.EMPTY)
        }
        h.runAtTickTime(120) {
            if(!roller.inventory.getStackInSlot(0).isEmpty || !side.extractItem(1,1,false).`is`(Content.materialItems.getValue("silicon_plate").get()) || !top.extractItem(1,1,false).isEmpty)h.fail("Rolling machine failed its original four-ingot count, single output or top-feed/side-extract policy")
            h.succeed()
        }
    }
}
