package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object ElectricalFurnaceGameTests {
    @JvmStatic @GameTest(template="empty",timeoutTicks=190)
    fun residualStoredHeatSmeltsWithoutElectricityAndInsulationReducesItsLoss(h: GameTestHelper) {
        val bare=InfrastructureGameTests.create(h,"electrical_furnace",2,2)
        val insulated=InfrastructureGameTests.create(h,"electrical_furnace",2,6)
        for(n in listOf(bare,insulated)) {
            n.parts[0]!!.apply { channel="manual";enabled=false;thermalState.putDouble("delta",800.0) }
            n.inventory.setStackInSlot(0,ItemStack(Items.RAW_IRON,64));n.changedTopology()
        }
        PartInventory.get(insulated,insulated.parts[0]!!).setStackInSlot(1,ItemStack(Content.materialItems.getValue("thermal_insulation").get()))
        h.runAtTickTime(90) {
            val saved=insulated.saveWithoutMetadata();val progress=insulated.parts[0]!!.progress
            insulated.load(saved);insulated.changedTopology()
            if(insulated.parts[0]!!.progress!=progress || PartInventory.get(insulated,insulated.parts[0]!!).getStackInSlot(1).isEmpty)h.fail("Furnace lost hot chamber, insulation or pending smelting work on reload")
        }
        h.runAtTickTime(170) {
            for(n in listOf(bare,insulated)) {
                val p=n.parts[0]!!;val done=n.inventory.getStackInSlot(2).count
                if(done<1 || p.energy>1e-6)h.fail("Unpowered hot furnace did not use its stored heat")
                val stored=p.thermalState.getDouble("delta")*40
                if(stored+1000*done+p.progress>32000.0001)h.fail("Furnace duplicated stored thermal energy")
            }
            if(insulated.parts[0]!!.thermalState.getDouble("delta")<=bare.parts[0]!!.thermalState.getDouble("delta"))h.fail("Original half-conduction insulation failed to retain chamber heat")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=150)
    fun missingCoilCannotDrawPowerAndRealInstalledCoilHeatsTheChamber(h: GameTestHelper) {
        InfrastructureGameTests.create(h,"electrical_source",1,2).parts[0]!!.value=200.0
        val n=InfrastructureGameTests.create(h,"electrical_furnace",2,2,1)
        n.inventory.setStackInSlot(0,ItemStack(Items.RAW_IRON,8))
        h.runAtTickTime(30) {
            if(n.parts[0]!!.energy>.0001 || n.parts[0]!!.progress>.0001)h.fail("Furnace generated heat without a heating element")
            PartInventory.get(n,n.parts[0]!!).setStackInSlot(0,ItemStack(Content.materialItems.getValue("200v_tungsten_heating_element").get()))
            n.parts[0]!!.expression="180"
            PartInventory.get(n,n.parts[0]!!).setStackInSlot(2,ItemStack(Content.materialItems.getValue("on_off_regulator_1_percent").get()))
        }
        h.runAtTickTime(130) {
            val p=n.parts[0]!!
            if(p.energy<4000 || p.thermalState.getDouble("delta")<100)h.fail("Installed 1200 W heating element failed to supply real chamber heat")
            if(p.thermalState.getDouble("delta")>190)h.fail("Installed on/off regulator failed to stop heating at the target")
            h.succeed()
        }
    }
}
