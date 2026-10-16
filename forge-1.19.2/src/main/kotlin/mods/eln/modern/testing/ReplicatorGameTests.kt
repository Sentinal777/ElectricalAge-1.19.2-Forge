package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object ReplicatorGameTests {
    private class Yard(val h: GameTestHelper,val voltage: Double=50.0) {
        val at=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+8,280,it.z+8) }
        val cable: FaceNodeEntity
        val source: FaceNodeEntity
        val p get()=cable.parts[0]!!
        init {
            GameTestYards.clear(h,280,2,5)
            h.level.getEntities(null,AABB(at).inflate(12.0)).filter { it is ReplicatorEntity || it is ItemEntity }.forEach { it.discard() }
            for(x in -5..5)for(z in -5..5)h.level.setBlockAndUpdate(at.offset(x,-1,z),Blocks.STONE.defaultBlockState())
            fun node(pos: BlockPos,id: String): FaceNodeEntity {
                h.level.setBlockAndUpdate(pos,Content.node.get().defaultBlockState())
                return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN);it.changedTopology() }
            }
            cable=node(at,"high_voltage_cable");source=node(at.south(),"electrical_source");source.parts[0]!!.value=voltage
        }
        fun mob(): ReplicatorEntity=Content.replicator.get().create(h.level)!!.also { it.setNoAi(true);it.setNoGravity(true);it.hunger=0.0;it.setPos(at.x+.5,280.0,at.z+1.3);h.level.addFreshEntity(it) }
        fun feed(mob: ReplicatorEntity)=ReplicatorFeeding.feed(mob,ReplicatorFeeding.Cable(cable,p))
        fun ledger(mob: ReplicatorEntity) {
            if(abs(mob.electricalWorkJ-mob.nutritionalWorkJ-mob.dissipatedWorkJ)>1e-6 || abs(p.gridState.getDouble("replicatorWorkJ")-mob.electricalWorkJ-ReplicatorFeeding.pending(mob))>1e-5)
                h.fail("Replicator real resistor work was lost or duplicated: cable=${p.gridState}, food=${mob.electricalWorkJ}/${mob.nutritionalWorkJ}/${mob.dissipatedWorkJ}, pending=${ReplicatorFeeding.pending(mob)}")
        }
    }
    @JvmStatic @GameTest(batch="original_replicator_food",template="grid_empty",timeoutTicks=190)
    fun actualPrivateCableLoadFeedsAtOriginalVoltagePowerAndEntitySaveCannotDuplicateFood(h: GameTestHelper) {
        val y=Yard(h);var mob=y.mob();var savedWork=0.0
        for(tick in 10L..145L)h.runAtTickTime(tick) { if(!mob.isRemoved)y.feed(mob) }
        h.runAtTickTime(75) {
            y.ledger(mob)
            if(mob.electricalWorkJ !in 120.0..175.0 || mob.hunger>=0 || mob.nutritionalWorkJ!=mob.electricalWorkJ)h.fail("Original 50 V / about 50 W feeding did not consume a real private circuit load: ${mob.electricalWorkJ}, hunger=${mob.hunger}")
            val saved=CompoundTag();mob.saveWithoutId(saved);savedWork=mob.electricalWorkJ
            val old=mob;old.discard();mob=Content.replicator.get().create(h.level)!!.also { it.load(saved);h.level.addFreshEntity(it) }
            if(mob===old || abs(mob.electricalWorkJ-savedWork)>1e-5)h.fail("Actual saved Replicator lost its food ledger")
            ReplicatorFeeding.disconnect(old);ReplicatorFeeding.collect(old)
        }
        h.runAtTickTime(145) { y.source.parts[0]!!.enabled=false;y.source.changedTopology() }
        h.runAtTickTime(175) {
            ReplicatorFeeding.collect(mob);y.ledger(mob)
            if(mob.electricalWorkJ<=savedWork+100 || ReplicatorFeeding.pending(mob)>1e-6)h.fail("Reloaded identity failed to resume the real feed or kept drawing from an unpowered cable")
            mob.discard();h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_replicator_reproduction",template="grid_empty",timeoutTicks=2300)
    fun reproductionConsumesMoreThanTenThousandActualJoulesAndRetainsOriginalEightHealthAttributes(h: GameTestHelper) {
        val y=Yard(h,800.0);val mob=y.mob();var done=false
        for(tick in 10L..2250L)h.runAtTickTime(tick) {
            if(!done) {
                y.feed(mob)
                val children=h.level.getEntitiesOfClass(ReplicatorEntity::class.java,AABB(y.at).inflate(5.0)).filter { it.uuid!=mob.uuid }
                if(children.isNotEmpty()) {
                    done=true;ReplicatorFeeding.collect(mob);y.ledger(mob)
                    val afterMetabolism=mob.nutritionalWorkJ-.5*mob.tickCount
                    if(children.size!=1 || afterMetabolism !in 9999.5..10012.0 || mob.hunger !in -.002.. .01 || mob.maxHealth!=8f || mob.getAttributeValue(Attributes.ATTACK_DAMAGE)!=3.0 || mob.getAttributeValue(Attributes.FOLLOW_RANGE)!=8.0)
                        h.fail("Original ten-kilojoule reproduction or combat attributes changed: food=${mob.nutritionalWorkJ}, net=$afterMetabolism, age=${mob.tickCount}, hunger=${mob.hunger}, children=${children.size}")
                    children.forEach { it.discard() };mob.discard();h.succeed()
                }else if(tick==2250L)h.fail("Replicator never reproduced after real feeding: work=${mob.electricalWorkJ}, hunger=${mob.hunger}, voltage=${y.p.voltage}")
            }
        }
    }
    @JvmStatic @GameTest(batch="original_replicator_shock",template="grid_empty",timeoutTicks=120)
    fun originalSuddenVoltageRiseDealsFiveMagicDamageWhileLowVoltageAndSignalCableAreRejected(h: GameTestHelper) {
        val y=Yard(h);val mob=y.mob();var health=0f
        for(tick in 10L..55L)h.runAtTickTime(tick) { y.feed(mob) }
        h.runAtTickTime(56) { health=mob.health;y.source.parts[0]!!.value=200.0 }
        h.runAtTickTime(60) {
            y.feed(mob)
            if(mob.health!=health-5f || mob.feedResistance<400)h.fail("Original 20-percent load mismatch did not deal exactly five magic damage: health=${mob.health}/$health, R=${mob.feedResistance}")
            y.source.parts[0]!!.value=20.0
        }
        h.runAtTickTime(80) {
            y.feed(mob)
            if(ReplicatorFeeding.interesting(ReplicatorFeeding.Cable(y.cable,y.p)) || ReplicatorFeeding.pending(mob)!=0.0)h.fail("Replicator kept feeding below original 30 V threshold")
            val fakeSignal=PartState(PartDefinition.byId.getValue("signal_cable"),Direction.DOWN).apply { voltage=50.0 }
            if(ReplicatorFeeding.interesting(ReplicatorFeeding.Cable(y.cable,fakeSignal)))h.fail("Replicator accepted a signal cable or a replaced part identity")
            y.ledger(mob);mob.discard();h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_replicator_ai",template="grid_empty",timeoutTicks=280)
    fun originalCableGoalActuallyFindsAndFeedsFromALoadedPoweredCable(h: GameTestHelper) {
        val y=Yard(h);val mob=y.mob().apply { setNoAi(false);setNoGravity(false) }
        for(tick in 10L..250L)h.runAtTickTime(tick) {
            if(mob.electricalWorkJ>20) {
                ReplicatorFeeding.disconnect(mob);y.ledger(mob);mob.discard();h.succeed()
            }else if(tick==250L) {
                val probe=ReplicatorCableGoal(mob);probe.canUse()
                h.fail("Actual native AI never acquired a loaded powered node: food=${mob.electricalWorkJ}, ${probe.diagnostics}")
            }
        }
    }
    @JvmStatic @GameTest(batch="original_replicator_drops",template="grid_empty",timeoutTicks=45)
    fun actualDeathDropsExactlyOneOriginalResourceAndBoundedWeatherSearchNeverLoadsAnAbsentChunk(h: GameTestHelper) {
        val y=Yard(h);val mob=y.mob();val before=h.level.forcedChunks.size
        val absent=BlockPos(29000000,280,29000000)
        if(h.level.chunkSource.getChunkNow(absent.x shr 4,absent.z shr 4)!=null)h.fail("Weather no-load fixture unexpectedly present")
        if(ReplicatorWeather.spawnPosition(h.level,absent.x,absent.z)!=null || h.level.forcedChunks.size!=before || h.level.chunkSource.getChunkNow(absent.x shr 4,absent.z shr 4)!=null)h.fail("Weather spawn search forced an absent chunk")
        h.runAtTickTime(10) { mob.hurt(DamageSource.OUT_OF_WORLD,100f) }
        h.runAtTickTime(30) {
            val drops=h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(y.at).inflate(5.0)).map { it.item }
            val valid=setOf(Content.materialItems.getValue("iron_dust").get(),Content.materialItems.getValue("copper_dust").get(),Content.materialItems.getValue("gold_dust").get(),Items.REDSTONE,Items.GLOWSTONE_DUST)
            if(drops.sumOf { it.count }!=1 || drops.any { it.item !in valid })h.fail("Replicator death changed original exactly-one resource drop: $drops")
            h.succeed()
        }
    }
}
