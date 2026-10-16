package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import net.minecraftforge.registries.ForgeRegistries
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object FuelGeneratorBcceGameTests {
    @JvmStatic @GameTest(batch="fuel_generators",template="grid_empty",timeoutTicks=100)
    fun actualBuildcraftGasolineRetainsTheOriginalTwentyMinuteTankCalibration(h: GameTestHelper) {
        val node=TurbineGameTests.create(h,"48v_fuel_generator");val p=node.parts[0]!!
        val fuel=ForgeRegistries.FLUIDS.values.first { val id=ForgeRegistries.FLUIDS.getKey(it)!!;id.namespace.startsWith("buildcraft") && id.path.startsWith("fuel_light") }
        val dense=ForgeRegistries.FLUIDS.values.first { val id=ForgeRegistries.FLUIDS.getKey(it)!!;id.namespace.startsWith("buildcraft") && id.path.startsWith("fuel_dense") }
        val handler=node.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow { IllegalStateException("No gasoline intake") }
        if(handler.fill(FluidStack(dense,1000),FluidAction.EXECUTE)!=0 || handler.fill(FluidStack(fuel,3000),FluidAction.SIMULATE)!=2000 || !node.tank.isEmpty)h.fail("Generator accepted diesel or mutated its tank during simulated filling")
        if(handler.fill(FluidStack(fuel,3000),FluidAction.EXECUTE)!=2000)h.fail("Real BuildCraft fuel bypassed the original two-bucket tank")
        FuelGeneratorRuntime.setEnabled(node,p,true)
        fun verify() {
            val q=node.parts[0]!!;val input=q.thermalState.getDouble("fuelGeneratorInputJ")
            if(abs(node.tank.fluidAmount*720.0+q.fuelEnergy+input-1440000)>1e-5 || abs(q.thermalState.getDouble("fuelGeneratorTankJ")-1440000)>1e-6 || input<=0 || !q.enabled)h.fail("Published BuildCraft gasoline lost the original 1200-second rated tank or finite idle cost")
        }
        h.runAtTickTime(30) { verify();val saved=node.saveWithoutMetadata();node.load(saved);node.changedTopology() }
        h.runAtTickTime(70) { verify();h.succeed() }
    }
}
