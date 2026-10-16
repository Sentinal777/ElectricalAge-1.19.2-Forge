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
object SignalDeviceGameTests {
    private class Yard(val h: GameTestHelper) {
        val at=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+8,285,it.z+8) }
        init { GameTestYards.clear(h,285,2,5) }
        fun device(pos: BlockPos,id: String,rotation: Int=0): FaceNodeEntity {
            h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(pos,Content.node.get().defaultBlockState())
            return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { this.rotation=rotation };it.changedTopology() }
        }
        fun source(pos: BlockPos,value: Double)=device(pos,"signal_source").also { it.parts[0]!!.value=value;it.changedTopology() }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=110)
    fun processorActualRedstoneBudgetAndOnlyUsedIndependentInputsControlRealOutput(h: GameTestHelper) {
        val y=Yard(h);val n=y.device(y.at,"signal_processor");val p=n.parts[0]!!
        SignalProcessors.configure(n,p,"A+B*C")
        y.source(y.at.east(),2.5);y.source(y.at.south(),5.0);y.source(y.at.west(),1.25)
        val probe=y.device(y.at.north(),"electrical_probe")
        PartInventory.get(probe,probe.parts[0]!!).setStackInSlot(0,ItemStack(Content.partItems.getValue("signal_cable").get()))
        val loadNode=y.device(y.at.north(2),"creative_power_resistor",1);val load=loadNode.parts[0]!!.apply { value=10000.0 };y.device(y.at.north(3),"ground_cable")
        h.runAtTickTime(20) {
            if(!p.gridState.getBoolean("processorValid") || p.gridState.getInt("processorRedstone")!=2 || abs(p.voltage)>.001)h.fail("Processor ignored its original two-redstone budget: ${p.gridState}")
            PartInventory.get(n,p).setStackInSlot(0,ItemStack(Items.REDSTONE,1))
        }
        h.runAtTickTime(40) {
            if(abs(p.voltage)>.001)h.fail("One redstone powered a two-operator equation")
            PartInventory.get(n,p).setStackInSlot(0,ItemStack(Items.REDSTONE,2))
        }
        h.runAtTickTime(60) {
            if(p.voltage !in 3.70..3.80 || load.current !in .00036.. .00039 || PartInventory.peek(p,0).count!=2 || p.connectedMask!=15)h.fail("Independent normalized A/B/C inputs or real output failed: ${p.voltage}, ${load.current}, ${p.connectedMask}")
            SignalProcessors.configure(n,p,"A")
        }
        h.runAtTickTime(85) {
            if(p.voltage !in 2.45..2.55 || p.connectedMask!=9 || p.gridState.getInt("processorRedstone")!=0)h.fail("Unused B/C remained electrical terminals: ${p.connectedMask} ${p.voltage}")
            load.value=1.0;loadNode.changedTopology()
        }
        h.runAtTickTime(100) { if(load.current !in 0.0.. .101 || p.voltage>.11)h.fail("Original current-limited output became an ideal unlimited source");h.succeed() }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=110)
    fun processorOriginalIntegratorSurvivesActualReloadWithoutFreeResetAndInvalidOrEmptyEquationStaysOff(h: GameTestHelper) {
        val y=Yard(h);val n=y.device(y.at,"signal_processor");var p=n.parts[0]!!
        PartInventory.get(n,p).setStackInSlot(0,ItemStack(Items.REDSTONE,64));SignalProcessors.configure(n,p,"integrate(0.1,0)")
        var before=0.0
        h.runAtTickTime(25) {
            before=p.voltage
            if(before !in .4.. .8)h.fail("Original integrator did not advance at the real 20 Hz device rate: $before")
            val saved=n.saveWithoutMetadata();n.load(saved);p=n.parts[0]!!;n.changedTopology()
        }
        h.runAtTickTime(45) {
            if(p.voltage<before+.3 || p.voltage>before+.7 || PartInventory.peek(p,0).count!=64)h.fail("Integrator state or real redstone failed actual save/reload: $before -> ${p.voltage}")
            SignalProcessors.configure(n,p,"bad(")
        }
        h.runAtTickTime(65) {
            if(p.gridState.getBoolean("processorValid") || abs(p.voltage)>.001 || p.connectedMask!=0)h.fail("Invalid equation generated a signal or input terminals")
            SignalProcessors.configure(n,p,"")
            if(PartState.load(p.save())!!.expression!="")h.fail("Empty source expression silently became A after reload")
        }
        h.runAtTickTime(85) { if(abs(p.voltage)>.001)h.fail("Empty equation generated output");h.succeed() }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=90)
    fun processorDaytimeAndBeepAreOriginalSymbolsAndHardwareRemovalStopsThem(h: GameTestHelper) {
        val y=Yard(h);val n=y.device(y.at,"signal_processor");val p=n.parts[0]!!
        PartInventory.get(n,p).setStackInSlot(0,ItemStack(Items.REDSTONE,64));SignalProcessors.configure(n,p,"beep(daytime,1)")
        h.runAtTickTime(25) {
            val expected=(h.level.dayTime/(24000.0-1.0)).coerceIn(0.0,1.0)*5
            if(!p.gridState.getBoolean("processorBeep") || abs(p.voltage-expected)>.02 || p.connectedMask!=0)h.fail("Original daytime/beep runtime was not evaluated: ${p.gridState} ${p.voltage}, expected=$expected")
            PartInventory.get(n,p).extractItem(0,64,false)
        }
        h.runAtTickTime(50) {
            if(p.gridState.getBoolean("processorBeep") || abs(p.voltage)>.001)h.fail("Removed redstone left an active beep or signal")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=45)
    fun processorClipboardMovesRealRedstoneAndAllSidedHandlersCannotDuplicateRemovedHardware(h: GameTestHelper) {
        val y=Yard(h);val a=y.device(y.at,"signal_processor");val b=y.device(y.at.east(3),"signal_processor");val pa=a.parts[0]!!;val pb=b.parts[0]!!
        SignalProcessors.configure(a,pa,"A+B");PartInventory.get(a,pa).setStackInSlot(0,ItemStack(Items.REDSTONE,32))
        val clipboard=net.minecraft.nbt.CompoundTag();if(!ConfigCopyTool.copy(a,pa,clipboard))h.fail("Processor has no original clipboard configuration")
        val player=h.makeMockPlayer();player.inventory.setItem(0,ItemStack(Items.REDSTONE,11))
        if(!ConfigCopyTool.apply(b,pb,clipboard,player) || pb.expression!="A+B" || PartInventory.peek(pb,0).count!=11 || !player.inventory.getItem(0).isEmpty)h.fail("Clipboard minted redstone or lost original expression")
        for(side in Direction.values()) {
            val cap=b.getCapability(ForgeCapabilities.ITEM_HANDLER,side).orElseThrow { IllegalStateException("Missing original processor inventory") }
            if(cap.getSlotLimit(0)!=64 || cap.isItemValid(0,ItemStack(Items.DIAMOND)) || cap.insertItem(0,ItemStack(Items.REDSTONE,64),true).count!=11)h.fail("Processor slot count or validation failed")
        }
        val stale=b.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow { IllegalStateException() };b.setRemoved()
        if(!stale.extractItem(0,64,false).isEmpty || stale.insertItem(0,ItemStack(Items.REDSTONE),false).count!=1)h.fail("Removed processor handler remained usable")
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=75)
    fun allThreeOriginalVuMetersMeasureOneRealGateWithoutShortingOtherEdges(h: GameTestHelper) {
        val y=Yard(h);val meters=listOf("analog_vumeter","led_vumeter","multicolor_led_vumeter").mapIndexed { i,id ->
            val at=y.at.west(4).east(i*4);val n=y.device(at,id);y.source(at.north(),4.0);y.source(at.east(),1.0);n
        }
        h.runAtTickTime(35) {
            for(n in meters) {
                val p=n.parts[0]!!
                if(p.connectedMask!=8 || p.voltage !in 3.99..4.01 || p.current !in 0.0.. .0001 || p.pinVoltages.take(3).any { abs(it)>1e-8 })h.fail("VuMeter still shorts four edges: ${p.definition.id} mask=${p.connectedMask} V=${p.voltage} I=${p.current}")
            }
            h.succeed()
        }
    }
}
