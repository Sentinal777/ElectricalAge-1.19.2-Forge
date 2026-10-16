package mods.eln.modern.testing

import mods.eln.modern.*
import mods.eln.modern.grid.GridMultiblock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.*

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object SolarGameTests {
    @JvmStatic @GameTest(template="empty",timeoutTicks=340)
    fun originalPanelPowerTrackerInventoryShadeWeatherNightAndLargeTerminals(h: GameTestHelper) {
        val base=h.absolutePos(BlockPos.ZERO)
        val y=h.level.maxBuildHeight-12
        GameTestYards.clear(h,y,1,11)
        fun make(id: String,x: Int,z: Int,rotation: Int=0): FaceNodeEntity {
            val pos=BlockPos(base.x+x,y,base.z+z);val d=PartDefinition.byId.getValue(id)
            h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
            h.level.setBlockAndUpdate(pos,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
            return (h.level.getBlockEntity(pos) as FaceNodeEntity).also {
                it.parts[0]=PartState(d,Direction.DOWN).apply { this.rotation=rotation };it.changedTopology()
            }
        }
        val manual=make("small_rotating_solar_panel",2,2)
        val tracked=make("small_rotating_solar_panel",2,6)
        for(n in listOf(manual,tracked)) {
            val z=if(n===manual)2 else 6
            make("ground_cable",1,z);make("creative_power_resistor",3,z,2).parts[0]!!.value=1.0;make("ground_cable",4,z)
        }
        PartInventory.get(tracked,tracked.parts[0]!!).setStackInSlot(0,ItemStack(Content.materialItems.getValue("solar_tracker").get()))
        val large=make("2x3_rotating_solar_panel",12,2)
        GridMultiblock.cells(large.blockPos,large.parts[0]!!).forEach { h.level.setBlockAndUpdate(it,Blocks.AIR.defaultBlockState()) }
        if(!GridMultiblock.canPlace(h.level,large.blockPos,large.parts[0]!!))h.fail("Original 2x3 footprint rejected empty space")
        GridMultiblock.install(large,large.parts[0]!!)
        val largeLoad=make("creative_power_resistor",12,1,1).parts[0]!!.apply { value=100.0 }
        make("ground_cable",12,0);make("ground_cable",12,4)
        PartInventory.get(large,large.parts[0]!!).setStackInSlot(0,ItemStack(Content.materialItems.getValue("solar_tracker").get()))
        h.runAtTickTime(40) {
            if(abs(abs(largeLoad.current)-118.0/(100+2*large.parts[0]!!.resistance+2*largeLoad.resistance+.002))>.01)
                h.fail("Large panel's front terminal and separate ghost return failed: ${largeLoad.current} A")
            if(GridMultiblock.cells(large.blockPos,large.parts[0]!!).size!=5)h.fail("Large panel omitted its five reserved cells")
            val saved=large.saveWithoutMetadata();large.load(saved);large.changedTopology()
            if(PartInventory.get(large,large.parts[0]!!).getStackInSlot(0).isEmpty)h.fail("Large-panel tracker was lost on reload")
        }
        h.runAtTickTime(115) { h.level.setDayTime(1000) }
        var clear=0.0
        h.runAtTickTime(135) {
            val a=manual.parts[0]!!;val b=tracked.parts[0]!!
            if(abs(a.solarAngle-PI/2)>1e-9 || abs(b.solarAngle-a.solarAngle)<.1)h.fail("Tracker was automatic without an installed item or failed to move with one")
            if(abs(b.current)<=abs(a.current)*1.05)h.fail("Installed tracker did not increase morning output")
            clear=SolarExposure.sample(h.level,tracked.blockPos,b.solarAngle,b.definition.solar!!.offset)
            h.level.setBlockAndUpdate(tracked.blockPos.above(),Blocks.STONE.defaultBlockState())
        }
        h.runAtTickTime(155) {
            val p=tracked.parts[0]!!
            if(abs(p.current)>1e-7)h.fail("Opaque shade did not stop solar generation")
            h.level.setBlockAndUpdate(tracked.blockPos.above(),Blocks.AIR.defaultBlockState())
            h.level.setWeatherParameters(0,200,true,true);h.level.setRainLevel(1f);h.level.setThunderLevel(1f)
        }
        h.runAtTickTime(180) {
            val p=tracked.parts[0]!!;val light=SolarExposure.sample(h.level,tracked.blockPos,p.solarAngle,p.definition.solar!!.offset)
            if(abs(light/clear-.25)>.02)h.fail("Rain/thunder did not retain the original two half-power factors: $light / $clear")
            h.level.setWeatherParameters(6000,0,false,false);h.level.setRainLevel(0f);h.level.setThunderLevel(0f)
            h.level.setDayTime(18000)
        }
        h.runAtTickTime(210) {
            if(listOf(manual,tracked,large).any { abs(it.parts[0]!!.current)>1e-6 })h.fail("Panels continued to generate at night")
            val owner=large.blockPos;val ghost=GridMultiblock.cells(owner,large.parts[0]!!).first()
            h.level.destroyBlock(ghost,false)
            if(!h.level.getBlockState(owner).isAir)h.fail("Breaking a large-panel reservation failed to remove its owner")
            h.level.setDayTime(6000);h.succeed()
        }
    }
}
