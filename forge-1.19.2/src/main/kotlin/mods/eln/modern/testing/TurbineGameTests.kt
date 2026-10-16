package mods.eln.modern.testing

import mods.eln.modern.*
import mods.eln.modern.thermal.SteamFluids
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object TurbineGameTests {
    fun create(h: GameTestHelper,id: String="steam_turbine",x: Int=3,z: Int=3): FaceNodeEntity {
        GameTestYards.clear(h,260)
        val base=h.absolutePos(BlockPos.ZERO);val pos=BlockPos(base.x+x,260,base.z+z);val d=PartDefinition.byId.getValue(id)
        h.level.removeBlock(pos,false)
        h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
        h.level.setBlockAndUpdate(pos,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
        return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(d,Direction.DOWN);it.changedTopology() }
    }
    fun ledger(node: FaceNodeEntity,heatPerMb: Double): Double {
        val p=node.parts[0]!!
        return node.tank.fluidAmount*heatPerMb+p.fuelEnergy+p.thermalState.getDouble("turbineChamberHeat")+p.energy+p.thermalState.getDouble("turbineRejectedHeat")
    }
    @JvmStatic @GameTest(batch="shaft_turbines",template="grid_empty",timeoutTicks=90)
    fun emptyOriginalTurbinesRetainTheirTankAndRotorSizesWithoutGenerating(h: GameTestHelper) {
        val steam=create(h);val gas=create(h,"gas_turbine",7)
        h.runAtTickTime(30) {
            if(steam.tank.capacity!=7200 || gas.tank.capacity!=4 || steam.parts[0]!!.definition.shaftMass!=5.0 || gas.parts[0]!!.definition.shaftMass!=5.0)h.fail("Original turbine tanks or five-mass rotors were replaced with generic sizes")
            for(node in listOf(steam,gas)) {
                val p=node.parts[0]!!
                if(p.energy!=0.0 || p.shaftEnergy!=0.0 || p.thermalState.getDouble("turbineWatts")!=0.0)h.fail("An empty turbine created shaft work")
                val saved=node.saveWithoutMetadata();node.load(saved);node.changedTopology()
            }
        }
        h.runAtTickTime(60) {
            if(steam.parts[0]!!.energy!=0.0 || gas.parts[0]!!.energy!=0.0)h.fail("An empty reloaded turbine created energy")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="shaft_turbines",template="grid_empty",timeoutTicks=180)
    fun steamUsesActualFiveVoltThrottleAndTwentySecondSavedIntakeInertia(h: GameTestHelper) {
        val node=create(h);create(h,"flywheel",3,4)
        val control=create(h,"signal_source",4);control.parts[0]!!.value=0.0
        val steam=SteamFluids.steam.get();val heat=FuelFluids.joulesPerMb(steam)
        if(node.tank.fill(FluidStack(steam,7200),FluidAction.EXECUTE)!=7200)h.fail("Original steam tank rejected native contained steam")
        var intake=0.0;var rate=0.0;var work=0.0
        h.runAtTickTime(25) {
            if(node.tank.fluidAmount!=7200 || node.parts[0]!!.energy!=0.0)h.fail("Zero-volt turbine throttle consumed steam")
            control.parts[0]!!.value=2.5
        }
        h.runAtTickTime(35) {
            val p=node.parts[0]!!;intake=p.thermalState.getDouble("turbineIntakeHeat");rate=p.thermalState.getDouble("turbineRate");work=p.energy
            val expected=3600*(1-Math.pow(1-.05/20,10.0))
            if(abs(intake/heat-1800)>.1 || abs(rate-expected)>.01 || p.energy<=0)h.fail("Steam turbine lost the original half-throttle 3600 mB/s or twenty-second RC intake: ${p.save()}")
            if(abs(ledger(node,heat)-7200*heat)>1e-5)h.fail("Steam intake created or discarded unaccounted energy")
            val saved=node.saveWithoutMetadata();node.load(saved);node.changedTopology()
            if(node.parts[0]!!.thermalState.getDouble("turbineRate")!=rate)h.fail("Turbine RC intake failed persistence")
            control.parts[0]!!.value=0.0
        }
        h.runAtTickTime(75) {
            val p=node.parts[0]!!
            if(abs(p.thermalState.getDouble("turbineIntakeHeat")-intake)>1e-5 || p.energy<=work || p.thermalState.getDouble("turbineRate")>=rate || p.thermalState.getDouble("turbineChamberHeat")<=0)h.fail("Closing the real throttle consumed new steam or erased finite retained intake")
            if(abs(ledger(node,heat)-7200*heat)>1e-5)h.fail("Reloading the turbine duplicated its delayed fluid heat")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="shaft_turbines",template="grid_empty",timeoutTicks=440)
    fun emptyingSteamTankLeavesOnlyItsFiniteDelayedOutput(h: GameTestHelper) {
        val node=create(h);create(h,"flywheel",3,4)
        val steam=SteamFluids.steam.get();val heat=FuelFluids.joulesPerMb(steam);node.tank.fill(FluidStack(steam,720),FluidAction.EXECUTE)
        var work=0.0;var chamber=0.0
        h.runAtTickTime(40) {
            val p=node.parts[0]!!;work=p.energy;chamber=p.thermalState.getDouble("turbineChamberHeat")
            if(!node.tank.isEmpty || chamber<=0 || work<=0)h.fail("Finite steam did not pass through the original delayed intake")
        }
        h.runAtTickTime(400) {
            val p=node.parts[0]!!
            if(p.energy<=work || p.thermalState.getDouble("turbineChamberHeat")>=chamber || p.energy>720*heat || abs(ledger(node,heat)-720*heat)>1e-5)h.fail("An empty turbine created unbacked energy or lost its physical intake tail")
            h.succeed()
        }
    }
}
