package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import net.minecraftforge.registries.ForgeRegistries
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object TurbineBcceGameTests {
    @JvmStatic @GameTest(batch="shaft_turbines",template="grid_empty",timeoutTicks=240)
    fun realBuildcraftGasFuelRequiresPrimingAndFollowsTheFiveSecondIntake(h: GameTestHelper) {
        val node=TurbineGameTests.create(h,"gas_turbine")
        val fuel=ForgeRegistries.FLUIDS.values.first { val id=ForgeRegistries.FLUIDS.getKey(it)!!;id.namespace.startsWith("buildcraft") && id.path.startsWith("fuel_light") }
        val heat=FuelFluids.joulesPerMb(fuel)
        if(node.tank.fill(FluidStack(fuel,40),FluidAction.EXECUTE)!=4)h.fail("BuildCraft fuel bypassed the original four-millibucket gas intake")
        var initial=4*heat
        h.runAtTickTime(25) {
            val p=node.parts[0]!!
            if(node.tank.fluidAmount!=4 || p.energy!=0.0 || p.thermalState.getDouble("turbineEfficiency")!=0.0)h.fail("Original gas turbine started without shaft priming")
            // Explicit stored rotor energy is the priming fixture, never counted as newly generated work.
            p.shaftEnergy=.5*p.definition.shaftMass*.05*200*200;node.changedTopology()
        }
        h.runAtTickTime(35) {
            val p=node.parts[0]!!;val rate=p.thermalState.getDouble("turbineRate")
            if(abs(p.thermalState.getDouble("turbineIntakeHeat")/heat-2)>.001 || abs(rate-4*(1-Math.pow(.99,10.0)))>.001 || p.energy<=0)h.fail("Primed gas turbine failed its original four mB/s, five-second intake: ${p.save()}")
            if(abs(TurbineGameTests.ledger(node,heat)-initial)>1e-5)h.fail("Primed turbine created fuel energy")
            val saved=node.saveWithoutMetadata();node.load(saved);node.changedTopology()
        }
        h.runAtTickTime(180) {
            val p=node.parts[0]!!
            if(!node.tank.isEmpty || p.energy<=0 || p.energy>initial || p.thermalState.getDouble("turbineChamberHeat")<=0 || abs(TurbineGameTests.ledger(node,heat)-initial)>1e-5)h.fail("Finite BuildCraft fuel or saved gas intake lost conservation")
            h.succeed()
        }
    }
}
