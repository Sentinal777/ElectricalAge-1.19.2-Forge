package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import net.minecraftforge.registries.ForgeRegistries
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object LargeShaftBcceGameTests {
    @JvmStatic @GameTest(batch="large_shafts",template="grid_empty",timeoutTicks=150)
    fun largeGasTurbineUsesTheOriginalScaledIntakeAndFinitePublishedBuildcraftFuel(h: GameTestHelper) {
        val node=LargeShaftGameTests.create(h,"large_gas_turbine");val p=node.parts[0]!!
        val control=LargeShaftGameTests.at(h,"signal_source",node.blockPos.east());control.parts[0]!!.value=0.0
        p.shaftEnergy=.5*135*.05*200*200;node.changedTopology()
        val fuel=ForgeRegistries.FLUIDS.values.first { val id=ForgeRegistries.FLUIDS.getKey(it)!!;id.namespace.startsWith("buildcraft") && id.path.startsWith("fuel_light") }
        val heat=FuelFluids.joulesPerMb(fuel)
        if(node.tank.capacity!=108 || node.tank.fill(FluidStack(fuel,1000),FluidAction.EXECUTE)!=108)h.fail("Large gas turbine lost its original 27-times tank and intake")
        h.runAtTickTime(25) {
            if(node.tank.fluidAmount!=108 || p.energy!=0.0)h.fail("Closed large turbine throttle consumed fuel")
            control.parts[0]!!.value=2.5
        }
        h.runAtTickTime(35) {
            if(abs(p.thermalState.getDouble("turbineIntakeHeat")/heat-27)>.01 || p.energy<=0 || abs(p.voltage-2.5)>.01)h.fail("Large turbine did not consume the original half-throttle 54 mB/s through its actual gate")
            val saved=node.saveWithoutMetadata();node.load(saved);node.changedTopology()
        }
        h.runAtTickTime(100) {
            val current=node.parts[0]!!
            if(abs(TurbineGameTests.ledger(node,heat)-108*heat)>1e-4 || current.energy<=0 || current.energy>108*heat || current.tripped || MechanicalNetwork.remotePorts(h.level,node,current).size!=2)h.fail("Large turbine reload duplicated finite BuildCraft fuel or detached its real body/shaft ports")
            h.succeed()
        }
    }
}
