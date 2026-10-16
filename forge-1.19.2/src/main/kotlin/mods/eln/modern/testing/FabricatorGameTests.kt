package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object FabricatorGameTests {
    private fun node(h: GameTestHelper,id: String,x: Int,z: Int): FaceNodeEntity {
        val base=h.absolutePos(BlockPos.ZERO);val at=BlockPos(base.x+x,255,base.z+z);val d=PartDefinition.byId.getValue(id)
        h.level.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(at,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
        return (h.level.getBlockEntity(at) as FaceNodeEntity).also { it.parts[0]=PartState(d,Direction.DOWN);it.changedTopology() }
    }
    private fun material(id: String,count: Int=1)=ItemStack(Content.materialItems.getValue(id).get(),count)
    private fun ledger(h: GameTestHelper,p: PartState) {
        val counted=p.progress+p.gridState.getDouble("fabricatorBatchWorkJ")+p.gridState.getDouble("fabricatorDiscardedJ")+p.gridState.getDouble("fabricatorIdleLossJ")
        if(abs(p.energy-counted)>1e-5)h.fail("Fabricator work, paid batches, discarded progress and idle dissipation do not conserve actual resistor work")
    }
    @JvmStatic @GameTest(batch="original_fabricator",template="grid_empty",timeoutTicks=165)
    fun allFifteenOriginalChipOperationsConsumeRealWafersCopperAndFourKilojoulesIncludingAFailedAluBatch(h: GameTestHelper) {
        GameTestYards.clear(h,255,2,6)
        val nodes=FabricatorOperations.all.mapIndexed { i,operation ->
            val x=2+i/3*3;val z=3+i%3*4
            val n=node(h,"fabricator",x,z);val p=n.parts[0]!!;p.value=i.toDouble();p.gridState.putLong("fabricatorRandom",2L)
            PartInventory.get(n,p).apply { setStackInSlot(1,material("silicon_wafer"));setStackInSlot(2,material("copper_plate")) }
            node(h,"electrical_source",x,z-1).parts[0]!!.value=200.0;n.changedTopology();n
        }
        h.runAtTickTime(120) {
            for((i,n) in nodes.withIndex()) {
                val p=n.parts[0]!!;val inv=PartInventory.get(n,p);val operation=FabricatorOperations.all[i]
                if(p.definition.legacyId!=4480 || p.gridState.getInt("fabricatorBatches")!=1 || p.energy<4000 || !inv.getStackInSlot(1).isEmpty || !inv.getStackInSlot(2).isEmpty)h.fail("Original operation $i did not consume exactly one wafer/plate and paid work: ${p.save()}")
                if(i==3) {
                    if(!inv.getStackInSlot(0).isEmpty || p.gridState.getInt("fabricatorFailedBatches")!=1 || p.gridState.getDouble("fabricatorBatchWorkJ")!=4000.0)h.fail("Failed 50% ALU batch produced free output or retained already spent recipe work")
                } else if(inv.getStackInSlot(0).item!==operation.item() || inv.getStackInSlot(0).count!=operation.count)h.fail("Original chip operation $i produced the wrong item/count")
                ledger(h,p)
                val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology()
                if(n.parts[0]!!.value!=i.toDouble() || n.parts[0]!!.gridState.getInt("fabricatorBatches")!=1)h.fail("Fabricator operation/batch accounting was lost on actual node reload")
            }
        }
        h.runAtTickTime(145) { nodes.forEach { ledger(h,it.parts[0]!!) };h.succeed() }
    }
    @JvmStatic @GameTest(batch="original_fabricator",template="grid_empty",timeoutTicks=95)
    fun blockedFabricatorDoesNotConsumeInputsAndOperationSelectionDiscardsOnlyPreviouslyPaidProgress(h: GameTestHelper) {
        GameTestYards.clear(h,255,2,6)
        val n=node(h,"fabricator",8,8);var p=n.parts[0]!!
        PartInventory.get(n,p).apply { setStackInSlot(0,ItemStack(Items.DIRT,64));setStackInSlot(1,material("silicon_wafer",2));setStackInSlot(2,material("copper_plate",2)) }
        node(h,"electrical_source",8,7).parts[0]!!.value=200.0
        var retained=0.0
        h.runAtTickTime(20) {
            if(p.active || p.energy>.001 || p.progress!=0.0 || PartInventory.get(n,p).getStackInSlot(1).count!=2)h.fail("Blocked fabricator consumed real power or ingredients")
            PartInventory.get(n,p).setStackInSlot(0,ItemStack.EMPTY)
        }
        h.runAtTickTime(55) {
            if(p.progress !in 1500.0..1900.0)h.fail("Original 40-ohm load did not accumulate actual partial work")
            ledger(h,p);val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology();p=n.parts[0]!!;retained=p.progress
            FabricatorOperations.select(n,p,7)
            if(p.progress!=0.0 || p.gridState.getDouble("fabricatorDiscardedJ")!=retained || p.value!=7.0)h.fail("Changing operation reused the previous chip's paid progress")
            ledger(h,p)
        }
        h.runAtTickTime(80) { ledger(h,p);if(p.gridState.getInt("fabricatorBatches")!=0)h.fail("Selecting an operation created a free completed batch");h.succeed() }
    }
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=20)
    fun allSixOriginalAutomationSidesExposeOnlyTheCorrectInputsAndOutput(h: GameTestHelper) {
        GameTestYards.clear(h,255,2,6)
        val n=node(h,"fabricator",8,8);val p=n.parts[0]!!
        for(side in Direction.values()) {
            val cap=n.getCapability(ForgeCapabilities.ITEM_HANDLER,side).orElseThrow { IllegalStateException("Missing fabricator automation") }
            if(cap.slots!=3 || cap.isItemValid(0,material("transistor")) || cap.isItemValid(1,material("copper_plate")) || cap.isItemValid(2,material("silicon_wafer")))h.fail("Fabricator automation accepts an output or crossed input type")
            cap.insertItem(1,material("silicon_wafer"),false);cap.insertItem(2,material("copper_plate"),false)
            if(!cap.extractItem(1,64,false).isEmpty || !cap.extractItem(2,64,false).isEmpty)h.fail("Automation extracted fabrication inputs")
        }
        val inv=PartInventory.get(n,p)
        if(inv.getStackInSlot(1).count!=6 || inv.getStackInSlot(2).count!=6)h.fail("Fabricator sided automation duplicated or lost inputs")
        inv.setStackInSlot(0,material("transistor",16))
        val cap=n.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.DOWN).orElseThrow { IllegalStateException("Missing output capability") }
        if(cap.extractItem(0,64,false).count!=16 || !inv.getStackInSlot(0).isEmpty)h.fail("Fabricator output did not extract once")
        h.succeed()
    }
}
