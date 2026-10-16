package mods.eln.modern.testing

import mods.eln.modern.*
import mods.eln.modern.grid.GridMultiblock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object RenewableGameTests {
    private fun device(h: GameTestHelper,id: String,x: Int=8,z: Int=8): FaceNodeEntity {
        GameTestYards.clear(h,290,3,8)
        val base=h.absolutePos(BlockPos.ZERO);val pos=BlockPos(base.x+x,290,base.z+z);val d=PartDefinition.byId.getValue(id)
        h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(pos,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
        return (h.level.getBlockEntity(pos) as FaceNodeEntity).also {
            it.parts[0]=PartState(d,Direction.DOWN).apply { if(d.behavior==Behavior.WIND_TURBINE)rotation=3 };GridMultiblock.install(it,it.parts[0]!!);it.changedTopology()
        }
    }
    private fun load(h: GameTestHelper,resistance: Double,wind: Boolean): FaceNodeEntity {
        val r=device(h,"creative_power_resistor",if(wind)7 else 8,if(wind)8 else 7)
        r.parts[0]!!.apply { rotation=if(wind)2 else 1;value=resistance }
        device(h,"ground_cable",if(wind)6 else 8,if(wind)8 else 6);return r
    }
    @JvmStatic @GameTest(batch="renewables",template="grid_empty",timeoutTicks=360)
    fun originalWindCurveProducesRealPowerAndResamplesItsObstructedAirVolume(h: GameTestHelper) {
        h.level.setWeatherParameters(6000,0,false,false)
        h.level.setRainLevel(0f);h.level.setThunderLevel(0f)
        val node=device(h,"wind_turbine");val p=node.parts[0]!!;val resistor=load(h,5.0,true)
        h.runAtTickTime(25) {
            if(p.definition.legacyId!=3136 || GridMultiblock.cells(node.blockPos,p).size!=7 || p.gridState.getDouble("windExposure")!=1.0 || p.voltage !in 40.0..59.001 || resistor.parts[0]!!.energy<200 || abs(p.thermalState.getDouble("renewableAvailableWatts")-528)>1e-4)h.fail("Original wind curve, legacy height response, reservations or actual electrical output were lost: ${p.save()}")
            for(x in -7..-3)for(z in -2..2)h.level.setBlockAndUpdate(node.blockPos.offset(x,3,z),Blocks.STONE.defaultBlockState())
        }
        h.runAtTickTime(45) { if(p.gridState.getDouble("windExposure")!=1.0)h.fail("Wind turbine skipped its original delayed environmental rescan") }
        h.runAtTickTime(310) {
            if(p.gridState.getDouble("windExposure")!=0.0 || p.thermalState.getDouble("renewableAvailableWatts")!=0.0 || abs(p.current)>1e-5)h.fail("Obstructed original wind turbine continued generating after its actual air-volume rescan")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="renewables",template="grid_empty",timeoutTicks=100)
    fun windWrenchMovesOnlyItsCableAndReconnectsTheActualElectricalLoad(h: GameTestHelper) {
        val node=device(h,"wind_turbine");val p=node.parts[0]!!;val r=load(h,5.0,true);val cells=GridMultiblock.cells(node.blockPos,p)
        val player=h.makeMockPlayer();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Content.wrench.get()))
        fun wrench()=node.blockState.use(h.level,player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(node.blockPos),Direction.UP,node.blockPos,false))
        var energy=0.0
        h.runAtTickTime(20) { if(r.parts[0]!!.energy<=100)h.fail("Original wind cable did not connect its initial load");wrench();energy=r.parts[0]!!.energy }
        h.runAtTickTime(40) {
            if(p.rotation!=3 || p.gridState.getInt("windCableOrientation")!=1 || cells!=GridMultiblock.cells(node.blockPos,p) || abs(r.parts[0]!!.energy-energy)>1e-4)h.fail("Wind wrench rotated its fixed body or left the old electrical cable connected")
            repeat(3) { wrench() }
        }
        h.runAtTickTime(70) {
            if(p.rotation!=3 || p.gridState.getInt("windCableOrientation")!=0 || r.parts[0]!!.energy<=energy+100)h.fail("Rotating the cable back did not restore the real wind-powered load")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="renewables",template="grid_empty",timeoutTicks=280)
    fun realFlowingWaterDrivesOriginalSavedDoubleFilterWhileStationaryAndDryIntakesDoNot(h: GameTestHelper) {
        val node=device(h,"water_turbine");val resistor=load(h,80.0,false);var p=node.parts[0]!!
        val intake=RenewableRuntime.waterPosition(node,p)
        for(x in -3..3) {
            h.level.setBlockAndUpdate(intake.offset(x,-1,0),Blocks.STONE.defaultBlockState())
            for(z in listOf(-1,1))h.level.setBlockAndUpdate(intake.offset(x,0,z),Blocks.STONE.defaultBlockState())
        }
        for(x in listOf(-3,3))h.level.setBlockAndUpdate(intake.offset(x,0,0),Blocks.STONE.defaultBlockState())
        h.level.setBlockAndUpdate(intake,Blocks.WATER.defaultBlockState())
        h.runAtTickTime(20) {
            if(p.definition.legacyId!=3152 || GridMultiblock.cells(node.blockPos,p).size!=6 || p.energy>1e-5 || RenewableRuntime.waterFactor(h.level,intake)!=0.0)h.fail("Stationary source water produced power or original wheel placement was lost")
            h.level.setBlockAndUpdate(intake,Blocks.AIR.defaultBlockState());h.level.setBlockAndUpdate(intake.east(2),Blocks.WATER.defaultBlockState())
        }
        var wetPower=0.0
        h.runAtTickTime(155) {
            if(RenewableRuntime.waterFactor(h.level,intake)<=0 || p.energy<=1 || resistor.parts[0]!!.energy<=1 || p.thermalState.getDouble("renewableAvailableWatts") !in .1..30.0)h.fail("Real flowing water failed the original two-stage intake power filter: ${p.save()}, intake=$intake, water=${(-2..2).map { h.level.getBlockState(intake.east(it)) }}, blockTicks=${h.level.isPositionEntityTicking(intake)}, scheduled=${h.level.fluidTicks.hasScheduledTick(intake,net.minecraft.world.level.material.Fluids.WATER)}")
            val saved=node.saveWithoutMetadata();val filter=p.gridState.copy();node.load(saved);node.changedTopology();p=node.parts[0]!!
            if(p.gridState!=filter)h.fail("Water turbine lost its original double-filter state on reload")
        }
        h.runAtTickTime(185) {
            wetPower=p.thermalState.getDouble("renewableAvailableWatts")
            for(x in -2..2)h.level.setBlockAndUpdate(intake.offset(x,0,0),Blocks.AIR.defaultBlockState())
        }
        h.runAtTickTime(245) {
            val watts=p.thermalState.getDouble("renewableAvailableWatts")
            if(RenewableRuntime.waterFactor(h.level,intake)>=0 || watts<=0 || watts>=wetPower)h.fail("Dry wheel did not decay its retained original rotor/filter output")
            h.succeed()
        }
    }
}
