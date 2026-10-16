package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.core.Direction
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object FuelGeneratorGameTests {
    @JvmStatic @GameTest(batch="fuel_generators",template="grid_empty",timeoutTicks=90)
    fun originalManualStartupIdleFuelLossAndStopUseRealBlockInteraction(h: GameTestHelper) {
        val node=TurbineGameTests.create(h,"48v_fuel_generator");val p=node.parts[0]!!;val player=h.makeMockPlayer()
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
        fun click()=node.blockState.use(h.level,player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(node.blockPos),Direction.UP,node.blockPos,false))
        if(p.enabled || p.definition.nominalVoltage!=50.0 || p.definition.power!=1200.0 || node.tank.capacity!=2000)h.fail("Original fuel generator ratings, tank or default stopped state were lost")
        click();if(p.enabled)h.fail("Empty fuel generator accepted a start request")
        p.fuelEnergy=7200.0;click();if(!p.enabled)h.fail("Fueled generator did not start on its original right-click control")
        var stopped=0.0
        h.runAtTickTime(20) {
            val input=p.thermalState.getDouble("fuelGeneratorInputJ")
            if(!p.enabled || abs(p.voltage-62.5)>.01 || p.energy>1e-4 || abs(input-165.0)>8.3 || abs(p.fuelEnergy+input-7200)>1e-6)h.fail("Idle generator lost its 62.5V ceiling, original 13.75% idle fuel cost or conservation: ${p.save()}")
            click();stopped=p.fuelEnergy
        }
        h.runAtTickTime(50) { if(p.enabled || p.active || abs(p.fuelEnergy-stopped)>1e-8)h.fail("Manually stopped generator continued consuming fuel");h.succeed() }
    }
    @JvmStatic @GameTest(batch="fuel_generators",template="grid_empty",timeoutTicks=95)
    fun bothOriginalConstantPowerRatingsAndFiniteFuelSurviveReload(h: GameTestHelper) {
        val machines=listOf("48v_fuel_generator" to 3,"240v_fuel_generator" to 9).map { (id,z) ->
            val node=TurbineGameTests.create(h,id,3,z);val p=node.parts[0]!!
            TurbineGameTests.create(h,"creative_power_resistor",4,z).parts[0]!!.apply { rotation=2;value=p.definition.nominalVoltage*p.definition.nominalVoltage/p.definition.power }
            TurbineGameTests.create(h,"ground_cable",5,z)
            p.fuelEnergy=120000.0;FuelGeneratorRuntime.setEnabled(node,p,true);node
        }
        h.runAtTickTime(30) {
            for(node in machines) {
                val p=node.parts[0]!!;val watts=p.thermalState.getDouble("fuelGeneratorWatts")
                if(!p.enabled || abs(watts-p.definition.power)>1 || abs(p.voltage/p.definition.nominalVoltage-1)>.03)h.fail("Original gasoline generator did not deliver its actual 1200/6000W constant-power curve: ${p.save()}")
                val saved=node.saveWithoutMetadata();node.load(saved);node.changedTopology()
            }
        }
        h.runAtTickTime(70) {
            for(node in machines) {
                val p=node.parts[0]!!;val input=p.thermalState.getDouble("fuelGeneratorInputJ")
                if(!p.enabled || p.energy<p.definition.power*3 || abs(p.fuelEnergy+input-120000)>1e-5 || abs(p.energy-input)>1e-5)h.fail("Rated generator reload lost fuel conservation or its original full-load efficiency")
            }
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="fuel_generators",template="grid_empty",timeoutTicks=95)
    fun overloadedGeneratorGetsOneSecondToStabilizeThenStopsBelowHalfMaximumVoltage(h: GameTestHelper) {
        val node=TurbineGameTests.create(h,"48v_fuel_generator");val p=node.parts[0]!!
        TurbineGameTests.create(h,"creative_power_resistor",4).parts[0]!!.apply { rotation=2;value=.001 }
        TurbineGameTests.create(h,"ground_cable",5)
        p.fuelEnergy=10000.0;FuelGeneratorRuntime.setEnabled(node,p,true)
        var stopped=0.0
        h.runAtTickTime(10) { if(!p.enabled || p.voltage>=31.25 || p.current>96.001)h.fail("Generator lost its original startup grace or five-times current ceiling") }
        h.runAtTickTime(35) { if(p.enabled)h.fail("Overloaded generator did not bail out below half its maximum voltage");stopped=p.fuelEnergy }
        h.runAtTickTime(65) { if(p.enabled || p.active || abs(p.fuelEnergy-stopped)>1e-6)h.fail("Generator continued burning after its overload shutdown");h.succeed() }
    }
}
