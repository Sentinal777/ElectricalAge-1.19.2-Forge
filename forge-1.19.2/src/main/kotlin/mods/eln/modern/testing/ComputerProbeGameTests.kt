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
object ComputerProbeGameTests {
    @JvmStatic @GameTest(template="empty",timeoutTicks=150)
    fun allSixPhysicalComputerProbeSidesRemainIndependentAndActualOutputAndRadiosPersistWithoutLoadingComputerCraft(h: GameTestHelper) {
        val at=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+8,285,it.z+8) };GameTestYards.clear(h,285,4,5)
        fun device(pos: BlockPos,id: String,face: Direction=Direction.DOWN,rotation: Int=0,value: Double?=null): FaceNodeEntity {
            h.level.setBlockAndUpdate(pos.relative(face),Blocks.STONE.defaultBlockState())
            h.level.setBlockAndUpdate(pos,(if(id=="computer_probe")Content.computerProbe.get()else Content.node.get()).defaultBlockState())
            return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { node -> node.parts[face.get3DDataValue()]=PartState(PartDefinition.byId.getValue(id),face).apply { this.rotation=rotation;value?.let { this.value=it } };node.changedTopology() }
        }
        val node=device(at,"computer_probe");var p=node.parts[0]!!
        val names=mapOf(Direction.DOWN to "YN",Direction.UP to "YP",Direction.NORTH to "ZN",Direction.SOUTH to "ZP",Direction.WEST to "XN",Direction.EAST to "XP")
        // Avoid outside-corner joins between the six test sources themselves: no pair mounts on each other.
        val mounting=mapOf(Direction.DOWN to Direction.WEST,Direction.WEST to Direction.NORTH,Direction.NORTH to Direction.UP,Direction.UP to Direction.EAST,Direction.EAST to Direction.SOUTH,Direction.SOUTH to Direction.DOWN)
        for(side in Direction.values())device(at.relative(side),"signal_source",mounting.getValue(side),value=.5+side.get3DDataValue()*.25)
        var load: FaceNodeEntity?=null;var old: PartState?=null
        h.runAtTickTime(35) {
            if(!h.level.getBlockState(at).isSolidRender(h.level,at) || h.level.getBlockState(at).getLightBlock(h.level,at)!=15)h.fail("Original Computer Probe cube lost its solid body or immutable light blocking")
            for(side in Direction.values()) {
                val expected=(.5+side.get3DDataValue()*.25)/5
                if(ComputerProbes.call(node,p,1,names.getValue(side))[0]!="in" || abs((ComputerProbes.call(node,p,4,names.getValue(side))[0] as Double)-expected)>.005)h.fail("Original cube face $side was shorted to another physical side: ${p.gridState}")
            }
            h.level.removeBlock(at.south(),false)
            device(at.south(),"electrical_probe").let { PartInventory.get(it,it.parts[0]!!).setStackInSlot(0,ItemStack(Content.partItems.getValue("signal_cable").get())) }
            load=device(at.south(2),"creative_power_resistor",rotation=1,value=10000.0);device(at.south(3),"ground_cable")
            ComputerProbes.call(node,p,0,"ZP",text="out");ComputerProbes.call(node,p,2,"ZP",.75)
            ComputerProbes.call(node,p,5,value=.6,text="saved-one");ComputerProbes.call(node,p,5,value=.2,text="saved-two")
            if(runCatching { ComputerProbes.call(node,p,2,"ZP",Double.NaN) }.isSuccess || runCatching { ComputerProbes.call(node,p,0,"bad",text="out") }.isSuccess)h.fail("Invalid direction or nonfinite output was accepted")
        }
        h.runAtTickTime(65) {
            if(abs(load!!.parts[0]!!.current) !in .00036.. .00039 || ComputerProbes.call(node,p,3,"ZP")[0]!=.75)h.fail("Original Computer Probe did not drive its real current-limited 3.75 V output")
            old=p;val saved=node.saveWithoutMetadata();node.load(saved);p=node.parts[0]!!;node.changedTopology()
            if(runCatching { ComputerProbes.call(node,old!!,2,"ZP",.1) }.isSuccess)h.fail("A callback for a previous part identity changed the reloaded Computer Probe")
        }
        h.runAtTickTime(95) {
            if(abs(load!!.parts[0]!!.current) !in .00036.. .00039 || ComputerProbes.entries(p).map { it.getString("channel") }.toSet()!=setOf("saved-one","saved-two"))h.fail("Actual reload lost separate signal or radio state")
            ComputerProbes.call(node,p,6,text="saved-one");ComputerProbes.call(node,p,5,value=.7,text="new-channel")
            if(ComputerProbes.entries(p).map { it.getInt("uuid") }.toSet().size!=2)h.fail("Radio identifier was reused across deleted and saved entries")
            ComputerProbes.call(node,p,7);ComputerProbes.call(node,p,0,"ZP",text="in")
        }
        h.runAtTickTime(125) {
            if(abs(load!!.parts[0]!!.current)>1e-6 || ComputerProbes.entries(p).isNotEmpty())h.fail("High impedance or remove-all did not stop the real probe output/radios")
            OriginalCraftingGameTests.craft(h,listOf("cIw","ICI","WIc"),mapOf('C' to "eln:advanced_chip",'c' to "eln:signal_cable",'I' to "eln:iron_cable",'w' to "eln:wireless_signal_receiver",'W' to "eln:wireless_signal_transmitter"),"eln:computer_probe")
            h.succeed()
        }
    }
}
