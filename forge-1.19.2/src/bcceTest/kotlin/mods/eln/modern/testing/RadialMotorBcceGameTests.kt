package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import net.minecraftforge.registries.ForgeRegistries
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object RadialMotorBcceGameTests {
    private fun fuel()=ForgeRegistries.FLUIDS.values.first { val id=ForgeRegistries.FLUIDS.getKey(it)!!;id.namespace.startsWith("buildcraft") && id.path.startsWith("fuel_light") }
    private fun neighbor(h: GameTestHelper,node: FaceNodeEntity,id: String,direction: Direction): FaceNodeEntity {
        val pos=node.blockPos.relative(direction);val d=PartDefinition.byId.getValue(id)
        h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
        h.level.setBlockAndUpdate(pos,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
        return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(d,Direction.DOWN);it.changedTopology() }
    }
    private fun ledger(node: FaceNodeEntity,heatPerMb: Double): Double {
        val p=node.parts[0]!!
        return node.tank.fluidAmount*heatPerMb+p.fuelEnergy+p.thermalState.getDouble("radialChamberHeat")+p.energy+p.thermalState.getDouble("radialRejectedHeat")
    }
    @JvmStatic @GameTest(batch="radial",template="grid_empty",timeoutTicks=650)
    fun publishedBuildcraftFuelFeedsFiniteRadialIntakeAndSavedInertia(h: GameTestHelper) {
        val node=RadialMotorGameTests.create(h);val flywheel=neighbor(h,node,"flywheel",Direction.SOUTH)
        val fuel=fuel();val heat=FuelFluids.joulesPerMb(fuel);val initial=40*heat
        if(!FuelFluids.accepts(Behavior.RADIAL_MOTOR,fuel) || node.tank.fill(FluidStack(fuel,40),FluidAction.EXECUTE)!=40)h.fail("Radial engine rejected the published BuildCraft light fuel")
        h.runAtTickTime(40) {
            val p=node.parts[0]!!
            if(!node.tank.isEmpty || p.energy<=0 || p.thermalState.getDouble("radialRate")<=0 || p.thermalState.getDouble("radialChamberHeat")<=0)h.fail("Original radial intake/inertia did not consume its real finite fuel")
            if(abs(ledger(node,heat)-initial)>1e-4)h.fail("Radial chamber created or lost unaccounted fuel energy")
            val saved=node.saveWithoutMetadata();val rate=p.thermalState.getDouble("radialRate");node.load(saved);node.changedTopology()
            if(node.parts[0]!!.thermalState.getDouble("radialRate")!=rate)h.fail("Radial intake inertia failed persistence")
        }
        h.runAtTickTime(600) {
            val p=node.parts[0]!!
            if(abs(ledger(node,heat)-initial)>1e-3 || p.energy>initial*.25+1e-6 || p.thermalState.getDouble("radialWatts")>1 || p.shaftSpeed>175)h.fail("Finite radial intake failed its original startup efficiency or continued producing free fuel power: ${p.save()}")
            if(flywheel.parts[0]!!.shaftEnergy<=0 || p.tripped)h.fail("Real radial output did not reach the attached flywheel")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="radial",template="grid_empty",timeoutTicks=120)
    fun actualFiveVoltThrottleControlsTheOriginalSixtyFourMillibucketRate(h: GameTestHelper) {
        val node=RadialMotorGameTests.create(h);neighbor(h,node,"flywheel",Direction.SOUTH)
        val control=neighbor(h,node,"signal_source",Direction.EAST);control.parts[0]!!.value=0.0
        val fuel=fuel();val heat=FuelFluids.joulesPerMb(fuel);node.tank.fill(FluidStack(fuel,20),FluidAction.EXECUTE)
        var intake=0.0
        h.runAtTickTime(25) {
            if(node.tank.fluidAmount!=20 || node.parts[0]!!.energy!=0.0)h.fail("Zero-volt throttle consumed radial fuel")
            control.parts[0]!!.value=2.5
        }
        h.runAtTickTime(35) {
            val p=node.parts[0]!!;intake=p.thermalState.getDouble("radialIntakeHeat")
            if(abs(intake/heat-16)>.05 || p.energy<=0 || abs(p.voltage-2.5)>.002)h.fail("Half throttle did not consume the original 32 mB/s through its real signal stage: ${p.save()}")
            control.parts[0]!!.value=0.0
        }
        h.runAtTickTime(60) {
            if(abs(node.parts[0]!!.thermalState.getDouble("radialIntakeHeat")-intake)>1e-4 || node.parts[0]!!.thermalState.getDouble("radialWatts")<=0)h.fail("Closed throttle consumed new fuel or erased the already-filled intake chamber")
            if(abs(ledger(node,heat)-20*heat)>1e-4)h.fail("Throttle changes duplicated finite fuel energy")
            h.succeed()
        }
    }
}
