package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object WatchGameTests {
    private fun device(h: GameTestHelper,id: String,x: Int=8): FaceNodeEntity {
        GameTestYards.clear(h,205,4,18)
        val pos=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+x,205,it.z+8) }
        h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(pos,Content.node.get().defaultBlockState())
        return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN);it.changedTopology() }
    }
    @JvmStatic @GameTest(batch="original_clocks",template="grid_empty",timeoutTicks=100)
    fun originalClocksAcceptOnlyPortableStoresAndUseTheirDifferentActualEnergyRates(h: GameTestHelper) {
        val analog=device(h,"analog_watch");val digital=device(h,"digital_watch",12)
        for(n in listOf(analog,digital)) {
            val p=n.parts[0]!!;val inv=PartInventory.get(n,p)
            for(id in listOf("portable_battery","portable_battery_pack","portable_condensator","portable_condensator_pack"))
                if(!inv.isItemValid(0,ItemStack(Content.materialItems.getValue(id).get())))h.fail("Clock rejected an original portable store: $id")
            if(inv.isItemValid(0,ItemStack(Items.REDSTONE)) || inv.isItemValid(0,ItemStack(Content.materialItems.getValue("small_flashlight").get())) || inv.getSlotLimit(0)!=1)h.fail("Clock battery slot accepts unrelated or stacked equipment")
            val battery=ItemStack(Content.materialItems.getValue("portable_battery").get());ElectricalItems.setEnergy(battery,100.0)
            val player=h.makeMockPlayer();player.abilities.instabuild=false;player.setItemInHand(InteractionHand.MAIN_HAND,battery)
            n.blockState.use(h.level,player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(n.blockPos),Direction.UP,n.blockPos,false))
            if(!battery.isEmpty || inv.getStackInSlot(0).isEmpty)h.fail("Clock did not consume the actual inserted survival battery")
        }
        h.runAtTickTime(70) {
            for(n in listOf(analog,digital)) {
                val p=n.parts[0]!!;val energy=ElectricalItems.energy(PartInventory.get(n,p).getStackInSlot(0))
                if(!p.active || p.connectedMask!=0 || p.current!=0.0 || p.voltage!=0.0 || abs(p.energy+energy-100)>.000001)h.fail("Clock generated energy or exposed an electrical terminal: ${p.save()}")
                if(p.energy !in p.definition.power*2.9..p.definition.power*3.7)h.fail("Clock did not use its original physical battery consumption: ${p.energy}")
            }
            if(abs(digital.parts[0]!!.energy/analog.parts[0]!!.energy-40.0/15)>.001)h.fail("Digital clock lost its original 15-hour versus analog 40-hour energy ratio")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_clocks",template="grid_empty",timeoutTicks=110)
    fun depletedClockStopsAtWorldTimeDiscardsOriginalFinalReserveAndResumesAfterBatteryChange(h: GameTestHelper) {
        val n=device(h,"analog_watch");var p=n.parts[0]!!;val inv=PartInventory.get(n,p)
        h.runAtTickTime(10) {
            if(p.active || WatchTime.date(p,20000)!=1379L)h.fail("Empty new clock did not retain its original stopped date")
            inv.setStackInSlot(0,ItemStack(Content.materialItems.getValue("portable_battery").get()).also { ElectricalItems.setEnergy(it,p.definition.power*.05*4) })
        }
        var stopped=0L
        h.runAtTickTime(20) {
            if(p.active || ElectricalItems.energy(inv.getStackInSlot(0))!=0.0 || abs(p.energy-p.definition.power*.2)>1e-9)h.fail("Clock did not discard and account for its original four-tick reserve")
            stopped=WatchTime.date(p,h.level.dayTime)
            if(stopped<=1379 || abs(stopped-h.level.dayTime)>12)h.fail("Clock failed to freeze at actual depletion time")
            val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology();p=n.parts[0]!!
        }
        h.runAtTickTime(40) {
            if(p.active || WatchTime.date(p,h.level.dayTime)!=stopped)h.fail("Stopped hands moved or changed after node reload")
            val restored=PartInventory.get(n,p);ElectricalItems.setEnergy(restored.getStackInSlot(0),20.0);n.changedTopology()
        }
        h.runAtTickTime(60) {
            if(!p.active || WatchTime.date(p,h.level.dayTime)!=h.level.dayTime)h.fail("Recharged clock failed to resume current world time")
            val drop=n.drop(p);val restored=PartState.load(drop.tag!!.getCompound("component"))!!
            val before=ElectricalItems.energy(PartInventory.get(n,p).getStackInSlot(0));val after=ElectricalItems.energy(PartInventory.get(n,restored).getStackInSlot(0))
            if(abs(before-after)>1e-9)h.fail("Picked-up clock lost its contained battery energy")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=20)
    fun originalClockDayOffsetAndTwentyFourHourDigitsWrapAcrossMidnight(h: GameTestHelper) {
        for((ticks,minutes) in listOf(0L to 360,6000L to 720,18000L to 0,23999L to 359,24000L to 360,-6000L to 0))
            if(WatchTime.minutes(ticks)!=minutes)h.fail("Clock day-time conversion lost the original six-hour offset or midnight wrap")
        h.succeed()
    }
    @JvmStatic @GameTest(batch="original_clocks",template="grid_empty",timeoutTicks=30)
    fun actualSurvivalClockPlacementKeepsTheOriginalUprightWallFace(h: GameTestHelper) {
        GameTestYards.clear(h,205,4,18);val player=h.makeMockPlayer();player.abilities.instabuild=false
        for((i,face) in listOf(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST).withIndex()) {
            val pos=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+6+i*3,205,it.z+8) };val support=pos.relative(face)
            h.level.setBlockAndUpdate(support,Blocks.STONE.defaultBlockState())
            val held=ItemStack(Content.partItems.getValue(if(i%2==0)"analog_watch" else "digital_watch").get());player.setItemInHand(InteractionHand.MAIN_HAND,held)
            player.setPos(pos.x+.5-face.stepX*2,205.0,pos.z+.5-face.stepZ*2)
            val result=held.useOn(net.minecraft.world.item.context.UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(support),face.opposite,support,false)))
            val p=(h.level.getBlockEntity(pos) as? FaceNodeEntity)?.parts?.get(face.ordinal)
            if(!result.consumesAction() || !held.isEmpty || p==null || FaceBasis.edges(p)[1].opposite!=Direction.UP)h.fail("Actual clock placement produced a sideways wall display on $face")
        }
        h.succeed()
    }
}
