package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.animal.Cow
import net.minecraft.world.entity.animal.IronGolem
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.event.entity.living.LivingAttackEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object TurretGameTests {
    private class Yard(val h: GameTestHelper) {
        val at=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+8,265,it.z+8) }
        val node: FaceNodeEntity
        val source: FaceNodeEntity
        val p get()=node.parts[0]!!
        init {
            GameTestYards.clear(h,265,2,8)
            h.level.getEntities(null,AABB(at).inflate(17.0)).filter { it is Cow || it is IronGolem || it is ItemEntity }.forEach { it.discard() }
            h.level.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(at,Content.machine.get().defaultBlockState())
            node=h.level.getBlockEntity(at) as FaceNodeEntity;node.parts[0]=PartState(PartDefinition.byId.getValue("800v_defence_turret"),Direction.DOWN);node.changedTopology()
            val power=at.south();h.level.setBlockAndUpdate(power.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(power,Content.node.get().defaultBlockState())
            source=h.level.getBlockEntity(power) as FaceNodeEntity;source.parts[0]=PartState(PartDefinition.byId.getValue("electrical_source"),Direction.DOWN).apply { value=800.0 };source.changedTopology()
        }
        fun filter(id: String="animal_filter")=PartInventory.get(node,p).setStackInSlot(0,ItemStack(Content.materialItems.getValue(id).get()))
        fun cow(position: BlockPos=at.north(6)): Cow=EntityType.COW.create(h.level)!!.also { it.setNoAi(true);it.setNoGravity(true);it.setPos(position.x+.5,265.0,position.z+.5);h.level.addFreshEntity(it) }
        fun ledger() {
            val accounted=p.progress+p.gridState.getDouble("turretShotJ")+p.gridState.getDouble("turretDissipatedJ")
            if(abs(p.energy-accounted)>1e-4 || p.progress !in 0.0..1000.00001)h.fail("Turret stored/shot/dissipated work does not conserve actual resistor power: ${p.save()}")
        }
    }
    @JvmStatic @GameTest(batch="original_turret",template="grid_empty",timeoutTicks=460)
    fun actualFiveDamageShotCostsOneThousandPaidJoulesAndAnOutageReloadCannotReplayIt(h: GameTestHelper) {
        val y=Yard(h);val cow=y.cow();val health=cow.health;var first=-1L;var savedBuffer=0.0
        h.runAtTickTime(60) {
            if(y.p.gridState.getInt("turretShots")!=0 || cow.health!=health || !y.p.gridState.getBoolean("turretArmed"))h.fail("Unfiltered original turret attacked a cow or failed to arm at 800 V: ${y.p.save()}, health=${cow.health}/$health")
            y.filter()
        }
        for(tick in 61L..420L)h.runAtTickTime(tick) {
            if(first<0 && y.p.gridState.getInt("turretShots")>0) {
                if(y.p.definition.legacyId!=4224 || y.p.gridState.getInt("turretShots")!=1 || cow.health!=health-5f || y.p.gridState.getDouble("turretShotJ")!=1000.0 || y.p.energy<1000)h.fail("Actual original laser shot lost five damage or its 1000-joule paid debit: ${y.p.save()}, health=${cow.health}/$health")
                first=tick;y.source.parts[0]!!.enabled=false;y.source.changedTopology();y.ledger()
            }
            if(first<0 && tick==380L)h.fail("Original 70-degree seeking/aiming turret never acquired and shot its real filtered cow: ${y.p.save()}")
            if(first>=0 && tick==first+20) { y.ledger();savedBuffer=y.p.progress;val saved=y.node.saveWithoutMetadata();y.node.load(saved);y.node.changedTopology() }
            if(first>=0 && tick==first+45) {
                if(y.p.gridState.getInt("turretShots")!=1 || cow.health!=health-5f || abs(y.p.progress-savedBuffer)>.001 || y.p.gridState.getBoolean("turretArmed"))h.fail("Outage/reload created a free second shot or lost contained charge")
                y.ledger();cow.discard();h.succeed()
            }
        }
    }
    @JvmStatic @GameTest(batch="original_turret",template="grid_empty",timeoutTicks=430)
    fun invertedAnimalFilterSparesTheCowAndOnlyShootsAnotherVisibleLivingClassThroughGlass(h: GameTestHelper) {
        val y=Yard(h);y.filter();y.p.enabled=true;val cow=y.cow(y.at.north(4));val cowHealth=cow.health
        val golem=EntityType.IRON_GOLEM.create(h.level)!!.apply { setNoAi(true);setNoGravity(true);setPos(y.at.x+.5,265.0,y.at.z-6.5);h.level.addFreshEntity(this) }
        val wall=y.at.north(3);h.level.setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(wall.above(),Blocks.STONE.defaultBlockState())
        h.runAtTickTime(100) {
            if(y.p.gridState.getInt("turretShots")!=0 || golem.health!=100f || cow.health!=cowHealth)h.fail("Opaque blocks failed original turret sight occlusion: ${y.p.save()}, cow=${cow.health}/$cowHealth, golem=${golem.health}")
            h.level.setBlockAndUpdate(wall,Blocks.GLASS.defaultBlockState());h.level.setBlockAndUpdate(wall.above(),Blocks.GLASS.defaultBlockState())
        }
        var done=false
        for(tick in 101L..400L)h.runAtTickTime(tick) {
            if(!done && y.p.gridState.getInt("turretShots")>0) {
                done=true
                if(cow.health!=cowHealth || golem.health>=100 || y.p.gridState.getInt("turretHits")<=0)h.fail("Inverted original animal filter attacked the spared cow or ignored its other valid living target")
                y.ledger();cow.discard();golem.discard();h.succeed()
            } else if(!done && tick==400L)h.fail("Original transparent-glass sight and inverted filter never acquired a valid target: ${y.p.save()}")
        }
    }
    @JvmStatic @GameTest(batch="original_turret",template="grid_empty",timeoutTicks=260)
    fun originalVoltageHysteresisAndFiveSecondRecoveryDelayControlActualArming(h: GameTestHelper) {
        val y=Yard(h);y.source.parts[0]!!.value=650.0
        h.runAtTickTime(25) { if(y.p.gridState.getBoolean("turretArmed"))h.fail("Original 660 V arming threshold was ignored");y.source.parts[0]!!.value=670.0 }
        h.runAtTickTime(50) { if(!y.p.gridState.getBoolean("turretArmed"))h.fail("Valid original supply did not arm turret");y.source.parts[0]!!.value=550.0 }
        h.runAtTickTime(75) { if(!y.p.gridState.getBoolean("turretArmed"))h.fail("Turret lost original voltage hysteresis above 540 V");y.source.parts[0]!!.value=530.0 }
        h.runAtTickTime(90) { if(y.p.gridState.getBoolean("turretArmed"))h.fail("Supply below 540 V did not disarm original turret");y.source.parts[0]!!.value=800.0 }
        h.runAtTickTime(160) { if(y.p.gridState.getBoolean("turretArmed"))h.fail("Original five-second brownout recovery delay was bypassed") }
        h.runAtTickTime(200) { if(!y.p.gridState.getBoolean("turretArmed"))h.fail("Turret failed to recover after five seconds on a valid supply");y.ledger();h.succeed() }
    }
    @JvmStatic @GameTest(batch="original_turret",template="grid_empty",timeoutTicks=430)
    fun actualForgeDamageCancellationStillDebitsTheOriginalShotAndCannotCreateFreeAttempts(h: GameTestHelper) {
        val y=Yard(h);y.filter();val cow=y.cow();val health=cow.health;val listener=object { @SubscribeEvent fun attack(event: LivingAttackEvent) { if(event.entity===cow)event.isCanceled=true } }
        MinecraftForge.EVENT_BUS.register(listener);var done=false
        for(tick in 1L..400L)h.runAtTickTime(tick) {
            if(!done && y.p.gridState.getInt("turretShots")>0) {
                done=true;MinecraftForge.EVENT_BUS.unregister(listener)
                if(cow.health!=health || y.p.gridState.getInt("turretHits")!=0 || y.p.gridState.getDouble("turretShotJ")!=1000.0 || y.p.energy<1000)h.fail("Actual Forge cancelled laser damage was ignored or produced a free shot: ${y.p.save()}, health=${cow.health}/$health")
                y.ledger();cow.discard();h.succeed()
            } else if(!done && tick==400L) { MinecraftForge.EVENT_BUS.unregister(listener);h.fail("Protected-target fixture never reached a real paid shot") }
        }
    }
    @JvmStatic @GameTest(batch="original_turret_overvoltage",template="grid_empty",timeoutTicks=55)
    fun originalInstantOvervoltageFailureRemovesTheArmedActualTurret(h: GameTestHelper) {
        val y=Yard(h);y.source.parts[0]!!.value=1100.0
        h.runAtTickTime(25) { if(h.level.getBlockEntity(y.at) is FaceNodeEntity)h.fail("Original active-turret 1050 V instantaneous overvoltage failure was ignored");h.succeed() }
    }
    @JvmStatic @GameTest(batch="original_turret",template="grid_empty",timeoutTicks=100)
    fun originalCraftingManualFilterReplacementAndAllSixAutomationSidesPreserveActualHardware(h: GameTestHelper) {
        OriginalCraftingGameTests.craft(h,listOf(" R ","CMC"," c "),mapOf('R' to "minecraft:redstone_block",'M' to "eln:advanced_machine_block",'C' to "eln:advanced_chip",'c' to "eln:high_voltage_cable"),"eln:800v_defence_turret")
        val y=Yard(h);y.filter();var ready=false
        for(tick in 1L..80L)h.runAtTickTime(tick) {
            if(ready)return@runAtTickTime
            if(!h.level.isPositionEntityTicking(y.at)) { if(tick==80L)h.fail("Turret hardware fixture did not become entity-ticking");return@runAtTickTime }
            ready=true
            h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(y.at).inflate(3.0)).forEach { it.discard() }
            val player=h.makeMockPlayer();player.abilities.instabuild=false;player.setPos(y.at.x+.5,266.0,y.at.z+2.5)
            val held=ItemStack(Content.materialItems.getValue("monster_filter").get(),2);player.setItemInHand(InteractionHand.MAIN_HAND,held)
            val hit=PartGeometry.body(y.p).center.add(Vec3.atLowerCornerOf(y.at))
            y.node.blockState.use(h.level,player,InteractionHand.MAIN_HAND,BlockHitResult(hit,Direction.UP,y.at,false))
            val drops=h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(y.at).inflate(3.0)).filter { it.item.item===Content.materialItems.getValue("animal_filter").get() }.sumOf { it.item.count }
            if(held.count!=1 || PartInventory.get(y.node,y.p).getStackInSlot(0).item!==Content.materialItems.getValue("monster_filter").get() || drops!=1)h.fail("Original held-filter swap failed to consume one new filter and return one old filter")
            var cap: net.minecraftforge.items.IItemHandler?=null
            for(side in Direction.values()) {
                val hardware=y.node.getCapability(ForgeCapabilities.ITEM_HANDLER,side).orElseThrow { IllegalStateException("Missing turret filter capability") };cap=hardware
                if(hardware.slots!=1 || hardware.getSlotLimit(0)!=1 || hardware.isItemValid(0,ItemStack(Items.DIRT)) || !hardware.isItemValid(0,ItemStack(Content.materialItems.getValue("player_filter").get())))h.fail("Original single entity-filter slot changed on $side")
            }
            h.level.destroyBlock(y.at,true)
            if(!cap!!.extractItem(0,1,false).isEmpty)h.fail("Removed turret retained an extractable stale filter inventory")
            h.succeed()
        }
    }
}
