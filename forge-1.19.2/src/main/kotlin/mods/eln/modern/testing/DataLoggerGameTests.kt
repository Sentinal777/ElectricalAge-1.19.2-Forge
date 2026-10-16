package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object DataLoggerGameTests {
    private class Yard(val h: GameTestHelper) {
        val at=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+8,305,it.z+8) }
        val node: FaceNodeEntity
        val source: FaceNodeEntity
        val p get()=node.parts[0]!!
        init {
            GameTestYards.clear(h,305,2,6)
            fun device(pos: BlockPos,id: String): FaceNodeEntity {
                h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(pos,Content.node.get().defaultBlockState())
                return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN);it.changedTopology() }
            }
            node=device(at,"data_logger");source=device(at.south(),"signal_source");source.parts[0]!!.value=2.5
        }
    }
    @JvmStatic @GameTest(batch="original_logger_history",template="grid_empty",timeoutTicks=360)
    fun originalRearInputAveragesQuantizedSamplesCapsTheRealHistoryAt256AndActuallyPauses(h: GameTestHelper) {
        val y=Yard(h);DataLoggers.configure(y.node,y.p,.05,-50.0,50.0,0,true)
        h.runAtTickTime(35) {
            val samples=DataLoggers.samples(y.p)
            if(y.p.connectedMask!=2 || y.p.voltage !in 2.49..2.501 || y.p.current !in .000024.. .000026 || samples.size<20 || samples.take(5).any { it.toInt()!=0 })h.fail("Original rear-only input, real 50 uA gate load or signed-byte averaging is wrong: ${y.p.save()}")
            y.source.parts[0]!!.value=5.0
        }
        h.runAtTickTime(305) {
            val samples=DataLoggers.samples(y.p)
            if(samples.size!=256 || samples.any { it.toInt()!=127 } || y.p.history.size!=256)h.fail("Actual logger failed to retain exactly the newest 256 signed-byte samples")
            DataLoggers.command(y.node,y.p,2)
            y.source.parts[0]!!.value=0.0
        }
        h.runAtTickTime(335) {
            if(DataLoggers.samples(y.p).any { it.toInt()!=127 })h.fail("Paused original logger continued recording")
            DataLoggers.command(y.node,y.p,1)
            if(DataLoggers.samples(y.p).isNotEmpty() || y.p.gridState.getInt("loggerCount")!=0)h.fail("Paused logger reset left old samples or a partial accumulator")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_logger_print",template="grid_empty",timeoutTicks=125)
    fun actualPaperPrintIsAnImmutableGraphSnapshotAndEveryAutomationSideConservesItsInputAndOutput(h: GameTestHelper) {
        val y=Yard(h)
        val handlers=Direction.values().map { y.node.getCapability(ForgeCapabilities.ITEM_HANDLER,it).orElseThrow { IllegalStateException("Missing logger automation") } }
        if(handlers.any { it.slots!=2 || it.isItemValid(0,ItemStack(Items.IRON_INGOT)) || it.isItemValid(1,ItemStack(Items.PAPER)) || !it.extractItem(0,64,false).isEmpty })h.fail("Logger sided automation exposed invalid paper/output routes")
        if(!handlers.first().insertItem(0,ItemStack(Items.PAPER,2),false).isEmpty)h.fail("Actual logger rejected real paper")
        var printed: ItemStack?=null;var snapshot: CompoundTag?=null
        h.runAtTickTime(45) {
            if(!DataLoggers.print(y.node,y.p) || PartInventory.peek(y.p,0).count!=1 || DataLoggers.print(y.node,y.p))h.fail("Print did not consume exactly one paper or overwrote a blocked output")
            printed=handlers.last().extractItem(1,1,false);snapshot=printed!!.tag!!.copy()
            if(printed!!.item!=Content.materialItems.getValue("data_logger_print").get() || snapshot!!.getByteArray("log").isEmpty() || snapshot!!.getDouble("samplingPeriod")!=.5)h.fail("Actual print did not contain original graph history and axis metadata")
            y.source.parts[0]!!.value=5.0;DataLoggers.configure(y.node,y.p,.05,0.0,10.0,2,false)
        }
        h.runAtTickTime(100) {
            if(printed!!.tag!=snapshot || !DataLoggers.print(y.node,y.p) || !PartInventory.peek(y.p,0).isEmpty || DataLoggers.print(y.node,y.p))h.fail("Printed history changed with the live graph or paper was duplicated")
            y.node.setRemoved()
            if(handlers.any { !it.getStackInSlot(0).isEmpty || !it.extractItem(1,1,false).isEmpty || it.insertItem(0,ItemStack(Items.PAPER),false).isEmpty })h.fail("Removed logger retained usable stale automation")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_logger_save",template="grid_empty",timeoutTicks=145)
    fun partialAveragingStateAndPrintedHistorySurviveReloadWhileConfigurationCopyExcludesLiveSamples(h: GameTestHelper) {
        val y=Yard(h);DataLoggers.configure(y.node,y.p,.5,-10.0,20.0,3,false)
        h.runAtTickTime(28) {
            val state=y.p.gridState.copy();val saved=y.node.saveWithoutMetadata();y.node.load(saved);y.node.changedTopology()
            if(y.p.gridState!=state)h.fail("Actual logger reload reset the partial average, sampling timer or history")
            val clip=CompoundTag();if(!ConfigCopyTool.copy(y.node,y.p,clip) || clip.contains("loggerSamples") || clip.contains("history") || clip.getDouble("period")!=.5 || clip.getDouble("min")!= -10.0 || clip.getInt("unit")!=3)h.fail("Logger clipboard lost its source configuration or copied live history")
            val player=h.makeMockPlayer();player.setPos(y.at.x+.5,305.0,y.at.z+2.5)
            clip.putDouble("period",.1);clip.putDouble("min",-20.0);clip.putDouble("max",30.0);clip.putInt("unit",7)
            if(!ConfigCopyTool.apply(y.node,y.p,clip,player) || DataLoggers.period(y.p)!=.1 || y.p.gridState.getInt("loggerUnit")!=7 || DataLoggers.samples(y.p).isNotEmpty())h.fail("Logger copier did not apply original period/range/unit and reset the changed-period history")
            val before=y.p.gridState.copy()
            if(DataLoggers.configure(y.node,y.p,Double.NaN,0.0,1.0,0,true) || DataLoggers.configure(y.node,y.p,0.0,0.0,1.0,0,true) || y.p.gridState!=before)h.fail("Logger accepted nonfinite/zero sampling intervals or mutated valid state on rejection")
        }
        h.runAtTickTime(100) { if(DataLoggers.samples(y.p).size<30)h.fail("Reloaded and reconfigured actual logger did not resume sampling");h.succeed() }
    }
    @JvmStatic @GameTest(batch="original_logger_clipboard",template="grid_empty",timeoutTicks=30)
    fun probeAndLoggerClipboardShareOriginalPhysicalUnitsRatherThanTheirDifferentInternalModeOrdinals(h: GameTestHelper) {
        val y=Yard(h);val at=y.at.east(4);h.level.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(at,Content.node.get().defaultBlockState())
        val probe=h.level.getBlockEntity(at) as FaceNodeEntity
        probe.parts[0]=PartState(PartDefinition.byId.getValue("electrical_probe"),Direction.DOWN).apply { value=0.0;expression="0,5000" };probe.changedTopology()
        val p=probe.parts[0]!!;val clip=CompoundTag();val player=h.makeMockPlayer().apply { setPos(y.at.x+.5,305.0,y.at.z+2.5) }
        ConfigCopyTool.copy(probe,p,clip);ConfigCopyTool.apply(y.node,y.p,clip,player)
        if(y.p.gridState.getInt("loggerUnit")!=2 || y.p.gridState.getDouble("loggerMax")!=5000.0)h.fail("Power probe clipboard selected voltage instead of the original watt unit")
        DataLoggers.configure(y.node,y.p,.5,-20.0,20.0,1,true);ConfigCopyTool.copy(y.node,y.p,clip);ConfigCopyTool.apply(probe,p,clip,player)
        if(p.value!=1.0 || SensorSettings.range(p.expression)!=(-20.0 to 20.0))h.fail("Logger current/range clipboard did not select the actual probe's current mode")
        h.succeed()
    }
}
