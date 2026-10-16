package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object WirelessGameTests {
    private class Yard(val h: GameTestHelper,val height: Int) {
        val forced=hashSetOf<ChunkPos>();val created=mutableListOf<BlockPos>()
        val channel="qa-radio-${h.absolutePos(BlockPos.ZERO).asLong()}-$height"
        fun position(x: Int,z: Int=8)=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+x,height,it.z+z) }
        fun device(id: String,x: Int,z: Int=8,rotation: Int=0): FaceNodeEntity {
            GameTestYards.clear(h,height,2,6)
            val at=position(x,z);val chunk=ChunkPos(at)
            if(!h.level.forcedChunks.contains(chunk.toLong())) { h.level.setChunkForced(chunk.x,chunk.z,true);forced.add(chunk) }
            h.level.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(at,Content.node.get().defaultBlockState());created.add(at)
            return (h.level.getBlockEntity(at) as FaceNodeEntity).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { this.rotation=rotation;this.channel=this@Yard.channel };it.changedTopology() }
        }
        fun finish() { for(at in created)h.level.removeBlock(at,false);for(chunk in forced)h.level.setChunkForced(chunk.x,chunk.z,false);h.succeed() }
    }
    @JvmStatic @GameTest(batch="wireless_repeater",template="grid_empty",timeoutTicks=210)
    fun originalThirtyTwoBlockRadioCrossesARepeaterCycleWithoutDuplicatingTransmitters(h: GameTestHelper) {
        val y=Yard(h,265)
        y.device("signal_source",1).parts[0]!!.value=5.0;y.device("wireless_signal_transmitter",2,rotation=2)
        val receiver=y.device("wireless_signal_receiver",90);val rx=receiver.parts[0]!!
        val output=y.device("signal_cable",91).parts[0]!!
        h.runAtTickTime(25) { if(rx.active || output.voltage>.01)h.fail("Original radio transmitted directly over 88 blocks")
            for((x,z) in listOf(28 to 8,54 to 8,78 to 8,50 to 20))y.device("wireless_signal_repeater",x,z)
        }
        h.runAtTickTime(65) {
            if(!rx.active || output.voltage<4.99 || rx.gridState.getInt("wirelessTransmitters")!=1)h.fail("Cycle-safe repeater path failed or duplicated its transmitter: ${rx.save()}")
            h.level.removeBlock(y.position(54),false);h.level.removeBlock(y.position(50,20),false)
        }
        h.runAtTickTime(100) { if(rx.active || output.voltage>.01)h.fail("Radio continued through removed repeaters");y.device("wireless_signal_repeater",54) }
        h.runAtTickTime(140) { if(output.voltage<4.99)h.fail("Replaced physical repeater did not restore reception");val saved=receiver.saveWithoutMetadata();receiver.load(saved);receiver.changedTopology() }
        h.runAtTickTime(175) { if(output.voltage<4.99)h.fail("Receiver reload lost its channel or relay graph");y.finish() }
    }
    @JvmStatic @GameTest(batch="wireless_attenuation",template="grid_empty",timeoutTicks=215)
    fun opaqueBlocksAddSourceAttenuationWhileGlassDoesNotAndScanningDoesNotForceChunks(h: GameTestHelper) {
        val y=Yard(h,225)
        for(x in 1..31)h.level.setBlockAndUpdate(y.position(x),Blocks.AIR.defaultBlockState())
        y.device("signal_source",1);y.device("wireless_signal_transmitter",2,rotation=2)
        val n=y.device("wireless_signal_receiver",30);val p=n.parts[0]!!;val meter=y.device("signal_cable",31).parts[0]!!
        h.runAtTickTime(20) {
            if(meter.voltage<4.99)h.fail("Clear original 28-block radio path was unavailable")
            for(x in listOf(12,14,16))h.level.setBlockAndUpdate(y.position(x),Blocks.STONE.defaultBlockState())
            p.gridState.putBoolean("wirelessRescan",true)
        }
        h.runAtTickTime(65) {
            if(meter.voltage>.01 || p.active)h.fail("Three opaque blocks did not add six blocks of original attenuation")
            h.level.setBlockAndUpdate(y.position(16),Blocks.GLASS.defaultBlockState());p.gridState.putBoolean("wirelessRescan",true)
        }
        h.runAtTickTime(110) {
            if(meter.voltage<4.99)h.fail("Glass attenuated the original radio or its inclusive 32-block boundary failed")
            val before=h.level.forcedChunks.toLongArray().toSet()
            WirelessSettings.virtualDistance(h.level,y.position(2),y.position(400))
            if(h.level.forcedChunks.toLongArray().toSet()!=before)h.fail("Radio obstruction scan forced a world chunk")
            y.finish()
        }
    }
    @JvmStatic @GameTest(batch="wireless_aggregation",template="grid_empty",timeoutTicks=230)
    fun maxMinAndRisingEdgeToggleUseRealIndependentInputsAndSaveHistory(h: GameTestHelper) {
        val y=Yard(h,185)
        val high=y.device("signal_source",2,3).parts[0]!!.apply { value=5.0 };y.device("wireless_signal_transmitter",3,3,2)
        val low=y.device("signal_source",2,13).parts[0]!!.apply { value=1.0 };y.device("wireless_signal_transmitter",3,13,2)
        val n=y.device("wireless_signal_receiver",11);val p=n.parts[0]!!;val out=y.device("signal_cable",12).parts[0]!!
        h.runAtTickTime(20) { if(out.voltage<4.99 || p.gridState.getInt("wirelessTransmitters")!=2)h.fail("Max receiver did not see both transmitters");p.value=1.0 }
        h.runAtTickTime(45) { if(abs(out.voltage-1)>.01)h.fail("Minimum receiver did not select the lower real signal");p.value=2.0;high.value=0.0;low.value=0.0 }
        h.runAtTickTime(70) { if(out.voltage>.01)h.fail("Toggle receiver did not initialize low");high.value=5.0 }
        h.runAtTickTime(95) { if(out.voltage<4.99)h.fail("Toggle receiver missed a rising radio edge");val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology() }
        h.runAtTickTime(120) { if(out.voltage<4.99)h.fail("Saved toggle history retriggered on topology rebuild");high.value=0.0 }
        h.runAtTickTime(145) { if(out.voltage<4.99)h.fail("Falling radio edge reset toggle state");high.value=5.0 }
        h.runAtTickTime(175) { if(out.voltage>.01)h.fail("Second rising radio edge did not clear the saved toggle");y.finish() }
    }
    @JvmStatic @GameTest(batch="wireless_sources",template="grid_empty",timeoutTicks=115)
    fun originalWirelessButtonAndSwitchUseActualBlockInteractionWithoutWiredPorts(h: GameTestHelper) {
        val y=Yard(h,145);val button=y.device("wireless_button",3,4);val sw=y.device("wireless_switch",3,12)
        val rb=y.device("wireless_signal_receiver",10,4);rb.parts[0]!!.channel=y.channel+"button";button.parts[0]!!.channel=y.channel+"button"
        val rs=y.device("wireless_signal_receiver",10,12);rs.parts[0]!!.channel=y.channel+"switch";sw.parts[0]!!.channel=y.channel+"switch"
        val bo=y.device("signal_cable",11,4).parts[0]!!;val so=y.device("signal_cable",11,12).parts[0]!!
        fun click(n: FaceNodeEntity) { val player=h.makeMockPlayer();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);n.blockState.use(h.level,player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(n.blockPos),Direction.UP,n.blockPos,false)) }
        h.runAtTickTime(20) { if(button.parts[0]!!.enabled || sw.parts[0]!!.enabled)h.fail("Original radio controls did not start off");click(button);click(sw) }
        h.runAtTickTime(23) { if(bo.voltage<4.9 || so.voltage<4.9)h.fail("Actual radio control interaction did not transmit") }
        h.runAtTickTime(35) { if(bo.voltage>.01 || so.voltage<4.9 || button.parts[0]!!.connectedMask!=0 || sw.parts[0]!!.connectedMask!=0)h.fail("Original .21-second push button or independent toggle switch/wireless-only ports failed");click(sw) }
        h.runAtTickTime(60) { if(so.voltage>.01)h.fail("Second actual radio-switch interaction did not turn it off");y.finish() }
    }
    @JvmStatic @GameTest(batch="wireless_lightning",template="grid_empty",timeoutTicks=190)
    fun actualLightningStartsOriginalSixSecondRadioInterferenceThenRestoresTheSignal(h: GameTestHelper) {
        val y=Yard(h,305);y.device("signal_source",1).parts[0]!!.value=2.5
        val tx=y.device("wireless_signal_transmitter",2,rotation=2).parts[0]!!
        y.device("wireless_signal_receiver",8);val out=y.device("signal_cable",9).parts[0]!!
        val samples=mutableListOf<Double>()
        h.runAtTickTime(20) {
            if(abs(out.voltage-2.5)>.01)h.fail("Unperturbed real radio signal was not 2.5 V")
            val bolt=net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(h.level)!!
            bolt.setPos(Vec3.atCenterOf(y.position(2)).add(1.0,0.0,0.0));bolt.setVisualOnly(true);h.level.addFreshEntity(bolt)
        }
        for(tick in 23..45)h.runAtTickTime(tick.toLong()) { samples.add(out.voltage) }
        h.runAtTickTime(60) {
            if(tx.gridState.getDouble("wirelessGlitchLeft") !in 3.5..4.5 || samples.minOrNull()!!>=1 || samples.maxOrNull()!!<=4)h.fail("Actual lightning did not produce original bounded three-Hz radio interference: $samples / ${tx.save()}")
            val old=tx.save().apply { putInt("componentDataVersion",5);remove("legacyWireless") }
            if(!PartState.load(old)!!.legacyWireless || !PartState.load(PartState.load(old)!!.save())!!.legacyWireless)h.fail("Preview radio save compatibility was not preserved")
        }
        h.runAtTickTime(165) {
            if(tx.gridState.getDouble("wirelessGlitchLeft")!=0.0 || tx.gridState.getDouble("wirelessGlitchOffset")!=0.0 || abs(out.voltage-2.5)>.01)h.fail("Radio interference did not expire after six physical seconds")
            y.finish()
        }
    }
}
