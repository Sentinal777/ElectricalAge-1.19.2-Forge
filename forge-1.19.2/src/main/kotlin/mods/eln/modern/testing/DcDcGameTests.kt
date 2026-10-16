package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object DcDcGameTests {
    private fun hardware(n: FaceNodeEntity,a: Int,b: Int,core: String="optimal_ferromagnetic_core") {
        val inv=PartInventory.get(n,n.parts[0]!!)
        inv.setStackInSlot(0,ItemStack(Content.materialItems.getValue("copper_cable").get(),a))
        inv.setStackInSlot(1,ItemStack(Content.materialItems.getValue("copper_cable").get(),b))
        inv.setStackInSlot(2,ItemStack(Content.materialItems.getValue(core).get()))
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=150)
    fun realWindingsDetermineRatioCoreLossAndUnpopulatedIsolation(h: GameTestHelper) {
        InfrastructureGameTests.create(h,"electrical_source",1)
        val n=InfrastructureGameTests.create(h,"dc_dc_converter",2)
        val load=InfrastructureGameTests.create(h,"creative_power_resistor",3,rotation=2).parts[0]!!
        InfrastructureGameTests.create(h,"ground_cable",4)
        hardware(n,1,4)
        var optimal=0.0
        h.runAtTickTime(30) {
            optimal=load.voltage-load.voltageB
            if(optimal !in 198.0..200.0 || abs(n.parts[0]!!.value-4)>1e-9)h.fail("Real 1:4 copper windings failed: $optimal V")
            hardware(n,1,4,"cheap_ferromagnetic_core")
        }
        h.runAtTickTime(60) {
            if(load.voltage-load.voltageB>=optimal-10)h.fail("100x core loss factor did not produce its original voltage drop")
            val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology()
            if(PartInventory.get(n,n.parts[0]!!).getStackInSlot(1).count!=4)h.fail("Winding inventory did not survive reload")
            PartInventory.get(n,n.parts[0]!!).extractItem(2,1,false)
        }
        h.runAtTickTime(90) {
            if(abs(load.current)>1e-6)h.fail("Converter supplied output without its real core")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=170)
    fun variableConverterUsesSeparateFiveVoltControlAndFourRequiredWindings(h: GameTestHelper) {
        InfrastructureGameTests.create(h,"electrical_source",1)
        val n=InfrastructureGameTests.create(h,"variable_dc_dc_converter",2)
        val load=InfrastructureGameTests.create(h,"creative_power_resistor",3,rotation=2).parts[0]!!
        InfrastructureGameTests.create(h,"ground_cable",4)
        val signal=InfrastructureGameTests.create(h,"signal_source",2,1).parts[0]!!.apply { value=2.5 }
        hardware(n,4,4)
        h.runAtTickTime(30) {
            if(load.voltage-load.voltageB !in 24.0..25.1 || abs(n.parts[0]!!.value-.5)>.001)h.fail("2.5 V control failed original half-voltage transfer")
            signal.value=5.0
        }
        h.runAtTickTime(60) {
            if(load.voltage-load.voltageB !in 4.8..5.1 || abs(n.parts[0]!!.value-.1)>.001)h.fail("5 V control failed original 0.1 minimum ratio")
            PartInventory.get(n,n.parts[0]!!).extractItem(0,1,false)
        }
        h.runAtTickTime(90) {
            if(abs(load.current)>1e-6)h.fail("Variable converter ran with fewer than four primary wires")
            hardware(n,4,4);signal.value=0.0
        }
        h.runAtTickTime(120) {
            if(load.voltage-load.voltageB !in 49.0..50.1)h.fail("Restored windings or zero signal failed full-voltage transfer")
            h.succeed()
        }
    }
}
