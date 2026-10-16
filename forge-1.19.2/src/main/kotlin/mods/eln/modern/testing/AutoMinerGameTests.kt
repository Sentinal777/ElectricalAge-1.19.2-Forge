package mods.eln.modern.testing

import com.mojang.authlib.GameProfile
import mods.eln.modern.*
import mods.eln.modern.grid.GridMultiblock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.event.level.BlockEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object AutoMinerGameTests {
    private class Yard(val h: GameTestHelper) {
        val root=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+8,230,it.z+8) }
        val node: FaceNodeEntity
        val p get()=node.parts[0]!!
        val source: FaceNodeEntity
        val chest: ChestBlockEntity
        init {
            GameTestYards.clear(h,230,9,7)
            h.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity::class.java,net.minecraft.world.phys.AABB(root).inflate(8.0)).forEach { it.discard() }
            h.level.setBlockAndUpdate(root,Content.machine.get().defaultBlockState());node=h.level.getBlockEntity(root) as FaceNodeEntity
            node.parts[0]=PartState(PartDefinition.byId.getValue("auto_miner"),Direction.DOWN);GridMultiblock.install(node,p);node.changedTopology()
            val out=AutoMiners.output(node,p);h.level.setBlockAndUpdate(out,Blocks.CHEST.defaultBlockState());chest=h.level.getBlockEntity(out) as ChestBlockEntity
            val port=RemotePowerPorts.positions(node,p).first();val at=port.position.relative(port.direction)
            h.level.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(at,Content.node.get().defaultBlockState())
            source=h.level.getBlockEntity(at) as FaceNodeEntity
            source.parts[0]=PartState(PartDefinition.byId.getValue("electrical_source"),Direction.DOWN).apply { value=800.0 };source.changedTopology()
        }
        fun inputs(drill: String="turbo_electrical_drill",pipes: Int=8) {
            PartInventory.get(node,p).apply { setStackInSlot(0,ItemStack(Content.materialItems.getValue(drill).get()));setStackInSlot(2,ItemStack(Content.materialItems.getValue("mining_pipe").get(),pipes)) }
        }
        fun ledger() {
            val counted=p.progress+p.gridState.getDouble("autoMinerSpentJ")+p.gridState.getDouble("autoMinerDiscardedJ")+p.gridState.getDouble("autoMinerIdleLossJ")
            if(abs(p.energy-counted)>1e-4)h.fail("Auto Miner created or lost paid work: ${p.energy} versus $counted")
        }
        fun count(item: net.minecraft.world.item.Item)=(0 until chest.containerSize).sumOf { val s=chest.getItem(it);if(s.item===item)s.count else 0 }
    }
    @JvmStatic @GameTest(batch="original_auto_miner",template="grid_empty",timeoutTicks=210)
    fun originalRaisedPowerPortsPayForActualBlocksPipesAndDepthDependentOreScanAndSaveTheWork(h: GameTestHelper) {
        val y=Yard(h);y.inputs();h.level.setBlockAndUpdate(y.root.below(),Blocks.STONE.defaultBlockState())
        val ore=y.root.below(3).east(3);h.level.setBlockAndUpdate(ore,Blocks.IRON_ORE.defaultBlockState())
        h.runAtTickTime(135) {
            if(y.p.definition.legacyId!=2688 || RemotePowerPorts.active(h.level,y.node,y.p).size!=2 || y.p.voltage<790 || AutoMiners.depth(y.p)<3 || y.p.gridState.getInt("autoMinerDrilled")!=2 || y.count(Items.COBBLESTONE)!=1 || y.count(Items.RAW_IRON)!=1 || !h.level.getBlockState(ore).isAir)h.fail("Original Auto Miner did not consume real raised-port power, drill two actual blocks and deposit their exact drops: ${y.p.save()}")
            y.ledger();y.source.parts[0]!!.enabled=false;y.source.changedTopology()
        }
        var retained=0.0
        h.runAtTickTime(155) {
            y.ledger();retained=y.p.progress
            val saved=y.node.saveWithoutMetadata();y.node.load(saved);y.node.changedTopology()
            if(y.p.progress!=retained || AutoMiners.depth(y.p)<3)h.fail("Saved Auto Miner lost already paid work or physical pipe depth")
        }
        h.runAtTickTime(180) {
            y.ledger();if(y.p.active || y.p.progress>retained+1e-3)h.fail("Unpowered Auto Miner advanced a saved job")
            val depth=AutoMiners.depth(y.p);val inv=PartInventory.get(y.node,y.p)
            if(inv.getStackInSlot(2).count+depth!=8)h.fail("Installed pipes were duplicated or consumed without physical depth")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_auto_miner",template="grid_empty",timeoutTicks=160)
    fun fullRearChestBlocksMiningAndSilkTouchUsesOriginalSixfoldWorkWithThreefoldPower(h: GameTestHelper) {
        val y=Yard(h);y.inputs();y.p.value=1.0;h.level.setBlockAndUpdate(y.root.below(),Blocks.DIAMOND_ORE.defaultBlockState())
        for(i in 0 until y.chest.containerSize)y.chest.setItem(i,ItemStack(Items.DIRT,64))
        h.runAtTickTime(25) {
            if(AutoMiners.job(y.p)!="chestFull" || y.p.progress!=0.0 || !h.level.getBlockState(y.root.below()).`is`(Blocks.DIAMOND_ORE))h.fail("Missing free rear storage failed to stop the actual miner")
            y.chest.clearContent()
        }
        h.runAtTickTime(50) {
            val expected=y.p.voltage*y.p.voltage/(800.0*800/30000)
            if(y.p.gridState.getDouble("autoMinerTargetJ")!=60000.0 || abs(y.p.voltage*y.p.current-expected)>1 || y.p.voltage !in 790.0..800.0 || y.p.progress !in 30000.0..42000.0)h.fail("Silk touch did not triple original nominal power with actual terminal-voltage loss and multiply paid operation work by six: ${y.p.save()}")
            y.ledger()
        }
        h.runAtTickTime(90) {
            if(y.count(Items.DIAMOND_ORE)!=1 || y.count(Items.DIAMOND)!=0 || y.p.gridState.getDouble("autoMinerSpentJ")<60000)h.fail("Paid silk-touch job did not deposit the actual ore block exactly once")
            y.ledger();h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_auto_miner",template="grid_empty",timeoutTicks=135)
    fun removingTheDrillRetractsPaidPipesAndBreakingOneOwnedPipeRefundsTheRemainingColumnOnce(h: GameTestHelper) {
        val y=Yard(h);y.inputs(pipes=3)
        h.runAtTickTime(80) {
            if(AutoMiners.depth(y.p)!=3 || !PartInventory.get(y.node,y.p).getStackInSlot(2).isEmpty)h.fail("Finite stack did not become exactly three owned physical pipes")
            PartInventory.get(y.node,y.p).setStackInSlot(0,ItemStack.EMPTY)
        }
        h.runAtTickTime(106) {
            if(AutoMiners.depth(y.p)!=2 || PartInventory.get(y.node,y.p).getStackInSlot(2).count!=1)h.fail("Removing original drill did not retract one paid 50-joule pipe")
            y.ledger();h.level.destroyBlock(y.root.below(),false)
        }
        h.runAtTickTime(120) {
            if(y.node.isRemoved || AutoMiners.depth(y.p)!=0 || (1..3).any { h.level.getBlockState(y.root.below(it)).block==Content.gridGhost.get() })h.fail("Broken mining pipe destroyed its owner or left the rest of its column behind")
            val dropped=h.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity::class.java,net.minecraft.world.phys.AABB(y.root).inflate(5.0)).filter { it.item.item===Content.materialItems.getValue("mining_pipe").get() }.sumOf { it.item.count }
            if(dropped!=2)h.fail("Broken column did not refund exactly its two remaining installed pipes: $dropped")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_auto_miner",template="grid_empty",timeoutTicks=80)
    fun realForgeBreakCancellationAndEverySidedHardwareSlotPreserveBlocksAndIngredients(h: GameTestHelper) {
        OriginalCraftingGameTests.craft(h,listOf("MCM","BOB"," P "),mapOf('M' to "eln:advanced_electrical_motor",'C' to "eln:advanced_chip",'B' to "eln:advanced_machine_block",'O' to "eln:ore_scanner",'P' to "eln:mining_pipe"),"eln:auto_miner")
        val y=Yard(h);y.inputs();val at=y.root.below();h.level.setBlockAndUpdate(at,Blocks.STONE.defaultBlockState())
        val listener=object { @SubscribeEvent fun breakBlock(event: BlockEvent.BreakEvent) { if(event.pos==at)event.isCanceled=true } }
        MinecraftForge.EVENT_BUS.register(listener)
        h.runAtTickTime(60) {
            MinecraftForge.EVENT_BUS.unregister(listener)
            if(!h.level.getBlockState(at).`is`(Blocks.STONE) || y.count(Items.COBBLESTONE)!=0 || y.p.gridState.getInt("autoMinerDrilled")!=0)h.fail("Auto Miner bypassed an actual Forge protected-block break")
            for(side in Direction.values()) {
                val cap=y.node.getCapability(ForgeCapabilities.ITEM_HANDLER,side).orElseThrow { IllegalStateException("Missing original sided hardware") }
                if(cap.slots!=3 || cap.getSlotLimit(0)!=1 || cap.getSlotLimit(2)!=64 || cap.isItemValid(0,ItemStack(Items.DIRT)) || !cap.isItemValid(2,ItemStack(Content.materialItems.getValue("mining_pipe").get())))h.fail("Auto Miner lost its original hardware slots on $side")
            }
            y.ledger();h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_auto_miner",template="grid_empty",timeoutTicks=95)
    fun breakingTheActualBodyReturnsInstalledPipesAndContainedHardwareOnceAndInvalidatesOldAutomation(h: GameTestHelper) {
        val y=Yard(h);y.inputs(pipes=3)
        val previousItems=h.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity::class.java,net.minecraft.world.phys.AABB(y.root).inflate(8.0)).mapTo(hashSetOf()) { it.uuid }
        val cap=y.node.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.DOWN).orElseThrow { IllegalStateException("Missing hardware inventory") }
        h.runAtTickTime(70) {
            if(AutoMiners.depth(y.p)!=2)h.fail("Removal fixture did not install two real pipes")
            h.level.destroyBlock(y.root,true)
        }
        h.runAtTickTime(80) {
            val items=h.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity::class.java,net.minecraft.world.phys.AABB(y.root).inflate(5.0)).filter { it.uuid !in previousItems }.map { it.item }
            val machines=items.filter { it.item===Content.partItems.getValue("auto_miner").get() }
            val pipes=items.filter { it.item===Content.materialItems.getValue("mining_pipe").get() }.sumOf { it.count }
            val saved=machines.singleOrNull()?.tag?.getCompound("component")?.let(PartState::load)
            if(machines.size!=1 || pipes!=2 || saved==null || AutoMiners.depth(saved)!=0 || PartInventory.peek(saved,2).count!=1 || PartInventory.peek(saved,0).item!==Content.materialItems.getValue("turbo_electrical_drill").get())h.fail("Actual body removal duplicated its installed pipes or lost contained original hardware: machines=${machines.size}, pipes=$pipes, saved=${saved?.save()}, actualItems=$items")
            if(!cap.extractItem(0,1,false).isEmpty || (1..3).any { h.level.getBlockState(y.root.below(it)).block==Content.gridGhost.get() })h.fail("Removed Auto Miner retained an extractable stale inventory or physical pipe ghosts")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_auto_miner_overvoltage",template="grid_empty",timeoutTicks=60)
    fun originalVoltageWatchdogDestroysAnActualOvervoltedMiner(h: GameTestHelper) {
        val y=Yard(h);y.source.parts[0]!!.value=2000.0;y.source.changedTopology()
        h.runAtTickTime(40) {
            if(h.level.getBlockEntity(y.root) is FaceNodeEntity)h.fail("Original 1040 V threshold and 200 volt-second watchdog did not destroy the actual Auto Miner")
            h.succeed()
        }
    }
}
