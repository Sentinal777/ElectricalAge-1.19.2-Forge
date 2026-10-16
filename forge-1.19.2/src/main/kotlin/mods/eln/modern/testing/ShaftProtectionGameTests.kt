package mods.eln.modern.testing

import mods.eln.modern.*
import mods.eln.config.ForgePortConfig
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object ShaftProtectionGameTests {
    private fun placeJoint(h: GameTestHelper,pos: net.minecraft.core.BlockPos) {
        val player=h.makeMockPlayer();player.abilities.instabuild=false;player.yRot=90f
        val stack=ItemStack(Content.partItems.getValue("joint").get());player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack)
        h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
        val support=pos.below()
        val result=(stack.item as PartItem).useOn(net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,
            net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(support).add(0.0,.5,0.0),Direction.UP,support,false)))
        if(!result.consumesAction() || !stack.isEmpty)h.fail("Survival shaft connection did not place and consume one actual joint")
    }
    private fun prime(node: FaceNodeEntity,speed: Double) {
        val p=node.parts[0]!!;p.shaftEnergy=.5*p.definition.shaftMass*.05*speed*speed;node.changedTopology()
    }
    @JvmStatic @GameTest(batch="shaft_protection",template="grid_empty",timeoutTicks=70)
    fun briefOverspeedGetsOriginalToleranceAndRecoversInsteadOfInstantlyErasingShaftEnergy(h: GameTestHelper) {
        val node=TurbineGameTests.create(h,"flywheel");prime(node,260.0)
        h.runAtTickTime(2) {
            val p=node.parts[0] ?: h.fail("Brief overspeed immediately destroyed the shaft").let { return@runAtTickTime }
            if(node.isRemoved || p.shaftEnergy<=0 || !p.gridState.contains("shaftWatchdogRemaining"))h.fail("Original first-excursion tolerance failed or erased the spinning rotor")
            prime(node,249.0)
        }
        h.runAtTickTime(45) {
            val p=node.parts[0]!!
            if(node.isRemoved || p.shaftSpeed<=240 || p.gridState.getBoolean("shaftSpeedOverflow") || p.gridState.getDouble("shaftWatchdogRemaining")!=2.0)h.fail("Recovered shaft did not reset its original overspeed tolerance")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="shaft_protection",template="grid_empty",timeoutTicks=50)
    fun sustainedSevereOverspeedConsumesTheFailedMachineAndRunsItsActualBlast(h: GameTestHelper) {
        val node=TurbineGameTests.create(h,"flywheel");val pos=node.blockPos
        h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(pos).inflate(4.0)).forEach { it.discard() }
        h.level.setBlockAndUpdate(pos.east(),Blocks.GLASS.defaultBlockState());prime(node,1000.0)
        h.runAtTickTime(15) {
            if(h.level.getBlockState(pos).block==Content.machine.get())h.fail("Sustained overspeed left the original shaft machine intact")
            if(ForgePortConfig.machineExplosions.get() && !h.level.getBlockState(pos.east()).isAir)h.fail("Original machine failure never produced its real neighboring-block blast")
            if(h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(pos).inflate(4.0)).any { it.item.item==Content.partItems.getValue("flywheel").get() })h.fail("Destroyed shaft was returned as a reusable stored-energy machine")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="shaft_protection",template="grid_empty",timeoutTicks=60)
    fun originalCoalPlateFailsOnARealControlledSpeedMismatch(h: GameTestHelper) {
        val left=TurbineGameTests.create(h,"flywheel",3,4)
        val clutch=TurbineGameTests.create(h,"clutch",4,4)
        val right=TurbineGameTests.create(h,"flywheel",5,4)
        for(n in listOf(left,clutch,right))n.parts[0]!!.rotation=1
        left.parts[0]!!.shaftGroup="coal-failure-left";prime(left,40.0)
        right.parts[0]!!.shaftGroup="coal-failure-right"
        val p=clutch.parts[0]!!
        p.gridState.putString("clutchGroup1","coal-failure-left");p.gridState.putString("clutchGroup3","coal-failure-right")
        p.gridState.putDouble("clutchEnergy1",.5*p.definition.shaftMass*.05*40*40);p.gridState.putDouble("clutchEnergy3",0.0)
        PartInventory.get(clutch,p).setStackInSlot(0,ItemStack(Content.materialItems.getValue("coal_clutch_plate").get()))
        TurbineGameTests.create(h,"signal_source",4,3).parts[0]!!.value=5.0
        clutch.changedTopology()
        h.runAtTickTime(20) {
            if(h.level.getBlockState(clutch.blockPos).block==Content.machine.get())h.fail("Coal clutch plate survived its original greater-than-five-radian mismatch under a real 5V control")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="shaft_protection",template="grid_empty",timeoutTicks=75)
    fun physicallyPlacingAJointBetweenMismatchedRotorsTriggersOriginalMergeFailure(h: GameTestHelper) {
        val left=TurbineGameTests.create(h,"flywheel",3,4);val right=TurbineGameTests.create(h,"flywheel",5,4)
        left.parts[0]!!.rotation=1;right.parts[0]!!.rotation=1;prime(left,100.0)
        val join=left.blockPos.east()
        h.runAtTickTime(15) { if(left.parts[0]!!.shaftSpeed<99 || right.parts[0]!!.shaftSpeed!=0.0)h.fail("Merge-failure fixture was already coupled");placeJoint(h,join) }
        h.runAtTickTime(35) {
            if(h.level.getBlockState(join).block==Content.machine.get())h.fail("A newly placed cold joint survived the original unsafe shaft-speed mismatch")
            if(h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(join).inflate(4.0)).any { it.item.item==Content.partItems.getValue("joint").get() })h.fail("Failed connecting joint was refunded as an intact machine")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="shaft_protection",template="grid_empty",timeoutTicks=80)
    fun physicallyConnectingSlowRotorsStaysWithinTheOriginalSafeMergeThreshold(h: GameTestHelper) {
        val left=TurbineGameTests.create(h,"flywheel",3,4);val right=TurbineGameTests.create(h,"flywheel",5,4)
        left.parts[0]!!.rotation=1;right.parts[0]!!.rotation=1;prime(left,5.0)
        val initial=left.parts[0]!!.shaftEnergy;val join=left.blockPos.east()
        h.runAtTickTime(10) { placeJoint(h,join) }
        h.runAtTickTime(40) {
            val middle=h.level.getBlockEntity(join) as? FaceNodeEntity ?: h.fail("Safe physical shaft merge destroyed its joint").let { return@runAtTickTime }
            val parts=listOf(left,middle,right).map { it.parts[0]!! }
            if(parts.any { it.shaftSpeed<=0 || kotlin.math.abs(it.shaftSpeed-parts[0].shaftSpeed)>1e-9 } || parts.sumOf { it.shaftEnergy }>initial+1e-6)h.fail("Safe physical shaft merge lost its connection or duplicated rotational energy")
            h.succeed()
        }
    }
}
