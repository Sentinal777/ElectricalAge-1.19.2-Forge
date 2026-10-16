package mods.eln.modern.testing

import dan200.computercraft.shared.computer.blocks.TileComputerBase
import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import net.minecraftforge.registries.ForgeRegistries
import java.nio.ByteBuffer
import kotlin.math.abs

/** Executes actual Lua on the published CC: Tweaked computer and its real Forge peripheral capability. */
@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object CcProbeGameTests {
    @JvmStatic @GameTest(template="empty",batch="original_actual_cc_lua",timeoutTicks=800)
    fun actualComputerCraftLuaCallsAllNineOriginalProbeMethodsControlsRealLoadsAndReattachesAfterReload(h: GameTestHelper) {
        val at=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+8,285,it.z+8) };GameTestYards.clear(h,285,2,5)
        fun device(pos: BlockPos,id: String,rotation: Int=0,value: Double?=null): FaceNodeEntity {
            h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(pos,(if(id=="computer_probe")Content.computerProbe.get()else Content.node.get()).defaultBlockState())
            return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { n -> n.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { this.rotation=rotation;value?.let { this.value=it } };n.changedTopology() }
        }
        val probe=device(at,"computer_probe");var p=probe.parts[0]!!
        device(at.north(),"signal_source",value=2.5)
        device(at.south(),"electrical_probe").let { PartInventory.get(it,it.parts[0]!!).setStackInSlot(0,ItemStack(Content.partItems.getValue("signal_cable").get())) }
        val load=device(at.south(2),"creative_power_resistor",1,10000.0);device(at.south(3),"ground_cable")
        val input="actual-cc-in-${at.x}";val output="actual-cc-out-${at.x}"
        device(at.east(4),"wireless_signal_transmitter").let { it.parts[0]!!.channel=input;it.changedTopology() };device(at.east(5),"signal_source",value=2.0)
        device(at.east(4).south(4),"wireless_switch").let { it.parts[0]!!.channel=input;it.parts[0]!!.enabled=true;it.changedTopology() }
        val receiver=device(at.west(4),"wireless_signal_receiver").also { it.parts[0]!!.channel=output;it.changedTopology() }
        val computerPos=at.west();h.level.setBlockAndUpdate(computerPos.below(),Blocks.STONE.defaultBlockState())
        val block=ForgeRegistries.BLOCKS.getValue(ResourceLocation("computercraft","computer_normal")) ?: error("Pinned CC: Tweaked computer is missing")
        h.level.setBlockAndUpdate(computerPos,block.defaultBlockState())
        val tile=h.level.getBlockEntity(computerPos) as TileComputerBase;val computer=tile.createServerComputer();val root=computer.createRootMount() ?: error("Actual computer root mount is unavailable")
        val program="""
            local ok, err = pcall(function()
              local p
              for i=1,100 do p=peripheral.find("ElnProbe"); if p then break end; sleep(0.1) end
              assert(p, "actual ElnProbe peripheral missing")
              assert(p.signalGetDir("ZN")=="in", "default input mode")
              assert(math.abs(p.signalGetIn("ZN")-0.5)<0.01, "real input voltage")
              p.signalSetDir("ZP","out"); p.signalSetOut("ZP",0.75)
              assert(p.signalGetDir("ZP")=="out" and math.abs(p.signalGetOut("ZP")-0.75)<0.001)
              assert(not pcall(p.signalSetOut,"ZP",0/0), "NaN accepted")
              p.wirelessSet("$output",0.6); p.wirelessSet("throwaway",0.2); p.wirelessRemove("throwaway")
              local big, small
              for i=1,60 do
                big=p.wirelessGet("$input","bigger"); small=p.wirelessGet("$input","smaller")
                if math.abs(big-1)<0.01 and math.abs(small-0.4)<0.01 then break end
                sleep(0.1)
              end
              assert(math.abs(big-1)<0.01 and math.abs(small-0.4)<0.01, "actual radio aggregation")
              os.setComputerLabel("ELN_PROBE_READY")
              os.pullEvent("eln_probe_reload")
              p=peripheral.find("ElnProbe"); assert(p, "peripheral did not reattach")
              assert(p.signalGetDir("ZP")=="out" and math.abs(p.signalGetOut("ZP")-0.75)<0.001,"saved output")
              p.wirelessRemove("$output"); p.wirelessSet("remove-all",0.1); p.wirelessRemoveAll()
              p.signalSetDir("ZP","in")
              os.setComputerLabel("ELN_PROBE_DONE")
            end)
            if not ok then
              local f=fs.open("eln_probe_error.txt","w"); f.write(tostring(err)); f.close()
              os.setComputerLabel("ELN_PROBE_ERROR")
            end
        """.trimIndent()
        root.openForWrite("startup.lua").use { channel -> val bytes=ByteBuffer.wrap(program.toByteArray(Charsets.UTF_8));while(bytes.hasRemaining())channel.write(bytes) }
        computer.turnOn()
        var stage=0;var reloadAt=0L
        for(tick in 20L..760L)h.runAtTickTime(tick) {
            if(stage==3)return@runAtTickTime
            if(computer.label=="ELN_PROBE_ERROR") {
                val buffer=ByteBuffer.allocate(4096);root.openForRead("eln_probe_error.txt").use { it.read(buffer) };buffer.flip()
                h.fail("Actual ComputerCraft Lua failed: "+Charsets.UTF_8.decode(buffer))
            }
            // READY confirms the Lua calls, not the receiver's next discovery scan.
            // Original receivers deliberately cache transmitter discovery for 1.2–2 seconds.
            if(stage==0 && computer.label=="ELN_PROBE_READY") { stage=1;reloadAt=tick+60 }
            if(stage==1 && tick==reloadAt) {
                if(abs(load.parts[0]!!.current) !in .00036.. .00039 || receiver.parts[0]!!.voltage !in 2.95..3.05 || ComputerProbes.entries(p).size!=1)h.fail("Actual Lua calls did not drive physical and radio outputs: I=${load.parts[0]!!.current}, RX=${receiver.parts[0]!!.voltage}, radios=${ComputerProbes.entries(p).size}, scan=${receiver.parts[0]!!.gridState.getDouble("wirelessScanLeft")}")
                val old=p;val saved=probe.saveWithoutMetadata();probe.load(saved);p=probe.parts[0]!!;probe.changedTopology()
                if(runCatching { ComputerProbes.call(probe,old,2,"ZP",.2) }.isSuccess)h.fail("Old peripheral binding controlled a reloaded part")
                stage=2;h.runAtTickTime(tick+30) { computer.queueEvent("eln_probe_reload",emptyArray()) }
            }
            if(stage==2 && computer.label=="ELN_PROBE_DONE") {
                stage=3;h.runAtTickTime(tick+45) {
                    if(abs(load.parts[0]!!.current)>1e-6 || receiver.parts[0]!!.voltage>.001 || ComputerProbes.entries(p).isNotEmpty())h.fail("Actual Lua input mode or remove-all left outputs live")
                    h.succeed()
                }
            }
            if(tick==760L && stage<3)h.fail("Actual ComputerCraft did not finish its Lua probe exercise: stage=$stage, label=${computer.label}")
        }
    }
}
