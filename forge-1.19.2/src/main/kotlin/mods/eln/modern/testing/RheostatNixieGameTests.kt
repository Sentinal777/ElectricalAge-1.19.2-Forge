package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object RheostatNixieGameTests {
    private fun device(h: GameTestHelper,id: String,x: Int,z: Int=8,rotation: Int=0): FaceNodeEntity {
        GameTestYards.clear(h,225,2,6)
        val pos=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+x,225,it.z+z) };val d=PartDefinition.byId.getValue(id)
        h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
        h.level.setBlockAndUpdate(pos,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
        return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(d,Direction.DOWN).apply { this.rotation=rotation };it.changedTopology() }
    }
    @JvmStatic @GameTest(batch="original_rheostat_nixie",template="grid_empty",timeoutTicks=140)
    fun largeRheostatUsesOriginalSeriesCoolingMassAndDedicatedBackThermalPort(h: GameTestHelper) {
        val n=device(h,"large_rheostat",8,rotation=1);val p=n.parts[0]!!
        PartInventory.get(n,p).setStackInSlot(0,ItemStack(Content.materialItems.getValue("coal_dust").get(),24))
        device(h,"electrical_source",9).parts[0]!!.value=20.0;device(h,"ground_cable",7)
        val gate=device(h,"signal_source",8,9).parts[0]!!.apply { value=5.0 }
        val thermal=device(h,"copper_thermal_cable",8,7).parts[0]!!
        h.runAtTickTime(25) {
            if(abs(p.value-100)>.01 || abs(p.current-.2)>.002 || p.connectedMask!=15 || p.thermalState.getDouble("capacity")!=50.0)h.fail("Large rheostat lost original coal series, power/gate pins, dedicated thermal port or 50 J/K mass: ${p.save()}")
            p.thermalState.putDouble("delta",50.0);val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology();gate.value=0.0
        }
        h.runAtTickTime(55) {
            val q=n.parts[0]!!
            if(abs(q.value-100.0/101)>.01 || thermal.thermalState.getDouble("delta")<=1.0 || q.thermalState.getDouble("rp")!=.2)h.fail("Large rheostat did not control its actual branch or conduct heat through the separate back connection: ${q.save()}")
            gate.value=5.0
        }
        h.runAtTickTime(95) { if(abs(n.parts[0]!!.value-100)>.01)h.fail("Saved large rheostat did not resume physical control");h.succeed() }
    }
    @JvmStatic @GameTest(batch="original_rheostat_nixie",template="grid_empty",timeoutTicks=90)
    fun largeRheostatFailsAtItsOwnThousandDegreeThermalLimit(h: GameTestHelper) {
        val n=device(h,"large_rheostat",8,rotation=1)
        device(h,"electrical_source",9).parts[0]!!.value=200.0;device(h,"ground_cable",7)
        device(h,"signal_source",8,9).parts[0]!!.value=0.0
        h.runAtTickTime(60) {
            if((h.level.getBlockEntity(n.blockPos) as? FaceNodeEntity)?.parts?.get(0)?.definition?.id=="large_rheostat")h.fail("Large rheostat survived actual overpower without consuming the failed part")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_rheostat_nixie",template="grid_empty",timeoutTicks=125)
    fun nixieKeepsDigitDotsAndSharedSideBlankingIndependentThroughReload(h: GameTestHelper) {
        val n=device(h,"nixie_tube",8);val p=n.parts[0]!!
        val digit=device(h,"signal_source",7).parts[0]!!.apply { value=3.7 }
        val dots=device(h,"signal_source",9).parts[0]!!.apply { value=2.6 }
        val blank=device(h,"signal_source",8,9).parts[0]!!.apply { value=0.0 }
        val shared=device(h,"signal_cable",8,7).parts[0]!!
        h.runAtTickTime(20) {
            if(p.displayReading!=7 || p.displayBits!=2 || p.displayBlank || p.connectedMask!=15 || abs(shared.voltage)>1e-7)h.fail("Nixie digit, point or shared blanking input was aliased: ${p.save()}")
            digit.value=5.0;dots.value=5.0;blank.value=5.0
        }
        h.runAtTickTime(45) {
            if(p.displayReading!=9 || p.displayBits!=3 || !p.displayBlank || shared.voltage<4.99)h.fail("Nixie maximum digit/points or either-side blanking failed")
            val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology()
        }
        h.runAtTickTime(70) {
            val q=n.parts[0]!!
            if(q.displayReading!=9 || q.displayBits!=3 || !q.displayBlank)h.fail("Nixie display was lost during actual node reload")
            blank.value=0.0;digit.value=0.0;dots.value=0.0
        }
        h.runAtTickTime(95) { val q=n.parts[0]!!;if(q.displayReading!=0 || q.displayBits!=0 || q.displayBlank)h.fail("Nixie did not resume digit zero after blanking cleared");h.succeed() }
    }
}
