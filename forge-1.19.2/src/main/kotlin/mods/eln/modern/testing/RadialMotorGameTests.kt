package mods.eln.modern.testing

import mods.eln.modern.*
import mods.eln.modern.grid.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object RadialMotorGameTests {
    fun create(h: GameTestHelper): FaceNodeEntity {
        GameTestYards.clear(h,250,2,3)
        val base=h.absolutePos(BlockPos.ZERO);val pos=BlockPos(base.x+4,250,base.z+5)
        val part=PartState(PartDefinition.byId.getValue("radial_motor"),Direction.DOWN)
        for(cell in GridMultiblock.cells(pos,part)+pos)h.level.setBlockAndUpdate(cell,Blocks.AIR.defaultBlockState())
        h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
        h.level.setBlockAndUpdate(pos,Content.machine.get().defaultBlockState())
        return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=part;it.changedTopology() }
    }
    @JvmStatic @GameTest(batch="radial",template="grid_empty",timeoutTicks=90)
    fun originalRadialFootprintPersistsAndCannotGenerateWithoutFuel(h: GameTestHelper) {
        val node=create(h);val p=node.parts[0]!!;val cells=GridMultiblock.cells(node.blockPos,p)
        if(cells.size!=18 || cells.any { it.z>=node.blockPos.z })h.fail("Original radial engine footprint does not lie behind its shaft output")
        if(!GridMultiblock.canPlace(h.level,node.blockPos,p))h.fail("Clear radial engine footprint rejected placement")
        GridMultiblock.install(node,p)
        if(cells.any { h.level.getBlockEntity(it) !is GridGhostEntity })h.fail("Radial engine failed to reserve its complete eighteen-cell body")
        h.runAtTickTime(25) {
            if(p.energy!=0.0 || p.shaftEnergy!=0.0 || node.tank.capacity!=64)h.fail("An empty radial engine generated energy or lost its original 64 mB tank")
            val saved=node.saveWithoutMetadata();node.load(saved);node.changedTopology()
            if(node.parts[0]!!.gridId!=p.gridId)h.fail("Radial engine lost its multiblock ownership on reload")
        }
        h.runAtTickTime(55) {
            if(node.parts[0]!!.energy!=0.0 || cells.any { (h.level.getBlockEntity(it) as? GridGhostEntity)?.ownerId!=p.gridId })h.fail("Empty engine reload created energy or detached its body")
            GridMultiblock.remove(node,node.parts[0]!!)
            if(cells.any { h.level.getBlockEntity(it) is GridGhostEntity })h.fail("Removing the radial engine left orphaned body cells")
            h.succeed()
        }
    }
}
