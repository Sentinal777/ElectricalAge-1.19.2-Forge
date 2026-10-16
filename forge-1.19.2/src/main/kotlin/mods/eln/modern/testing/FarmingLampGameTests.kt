package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.FarmBlock
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object FarmingLampGameTests {
    @JvmStatic @GameTest(batch="lighting",template="grid_empty",timeoutTicks=190)
    fun originalFarmingBulbActuallyGrowsTheInterceptedCropWithNaturalRandomTicksDisabled(h: GameTestHelper) {
        GameTestYards.clear(h,170,1,12);val b=h.absolutePos(BlockPos.ZERO)
        val crop=BlockPos(b.x+5,172,b.z+5);val control=BlockPos(b.x+12,172,b.z+5)
        for(center in listOf(crop,control)) {
            for(dx in -1..1)for(dz in -1..1)h.level.setBlockAndUpdate(center.offset(dx,-1,dz),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7))
            if(center==crop)for(dx in -1..1)for(dz in -1..1)for(dy in 0..5)
                if(dx!=0 || dz!=0 || dy==5)h.level.setBlockAndUpdate(center.offset(dx,dy,dz),Blocks.STONE.defaultBlockState())
        }
        fun device(id: String,pos: BlockPos): FaceNodeEntity {
            h.level.setBlockAndUpdate(pos,Content.node.get().defaultBlockState())
            return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { n -> n.parts[Direction.UP.ordinal]=PartState(PartDefinition.byId.getValue(id),Direction.UP);n.changedTopology() }
        }
        val lamp=device("classic_lamp_socket",crop.above(4));val source=device("electrical_source",crop.above(4).east())
        val p=lamp.parts[Direction.UP.ordinal]!!;p.enabled=true
        val inventory=PartInventory.get(lamp,p)
        inventory.setStackInSlot(0,ItemStack(Content.materialItems.getValue("50v_farming_lamp").get()))
        inventory.setStackInSlot(1,ItemStack(Content.partItems.getValue("very_high_voltage_cable").get()))
        // Four times voltage makes the original cubic growth probability reach 1, keeping this test bounded.
        source.parts[Direction.UP.ordinal]!!.value=200.0
        val random=h.level.gameRules.getRule(GameRules.RULE_RANDOMTICKING);val previous=random.get();random.set(0,h.level.server)
        // Wheat requires light to survive placement; plant only after the real light engine is ready.
        h.runAtTickTime(30) {
            h.level.setBlockAndUpdate(crop,Blocks.WHEAT.defaultBlockState())
            h.level.setBlockAndUpdate(control,Blocks.WHEAT.defaultBlockState())
        }
        h.runAtTickTime(150) {
            try {
                if(p.signal!=15 || p.energy<1000 || h.level.getBlockState(crop).block!=Blocks.WHEAT || h.level.getBlockState(control).block!=Blocks.WHEAT || h.level.getBlockState(crop).getValue(CropBlock.AGE)<=0 || h.level.getBlockState(control).getValue(CropBlock.AGE)!=0 || h.level.getBlockState(crop.below()).block!=Blocks.FARMLAND)h.fail("Powered farming lamp did not grow only its intercepted crop while natural random ticks were disabled: signal=${p.signal}, U=${p.voltage}, crop=${h.level.getBlockState(crop)}, control=${h.level.getBlockState(control)}")
                h.succeed()
            }finally { random.set(previous,h.level.server) }
        }
    }
}
