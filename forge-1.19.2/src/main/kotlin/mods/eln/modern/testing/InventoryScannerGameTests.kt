package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ComposterBlock
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object InventoryScannerGameTests {
    private class Yard(val h: GameTestHelper) {
        val base=h.absolutePos(BlockPos.ZERO)
        init { GameTestYards.clear(h,255,2,4) }
        fun pos(x: Int)=BlockPos(base.x+x,255,base.z+8)
        fun device(id: String,x: Int): FaceNodeEntity {
            val d=PartDefinition.byId.getValue(id);h.level.setBlockAndUpdate(pos(x).below(),Blocks.STONE.defaultBlockState())
            h.level.setBlockAndUpdate(pos(x),(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
            return (h.level.getBlockEntity(pos(x)) as FaceNodeEntity).also { it.parts[0]=PartState(d,Direction.DOWN);it.changedTopology() }
        }
    }
    @JvmStatic @GameTest(batch="original_inventory_scanner",template="grid_empty",timeoutTicks=90)
    fun originalStationaryScannerReadsSolidAirTransparentAndRealComparatorOutput(h: GameTestHelper) {
        val y=Yard(h);val n=y.device("scanner",8);val p=n.parts[0]!!;val target=y.pos(9)
        h.level.setBlockAndUpdate(target,Blocks.STONE.defaultBlockState())
        val output=y.device("signal_cable",7).parts[0]!!
        h.runAtTickTime(15) { if(abs(p.voltage-5)>.001 || p.connectedMask!=4 || abs(output.voltage-5)>.001)h.fail("Stationary scanner lost its back-only finite signal output or solid-block reading");h.level.removeBlock(target,false) }
        h.runAtTickTime(30) { if(abs(p.voltage)>1e-6)h.fail("Scanner treated air as an opaque block");h.level.setBlockAndUpdate(target,Blocks.GLASS.defaultBlockState()) }
        h.runAtTickTime(45) { if(abs(p.voltage-5.0/3)>.001)h.fail("Scanner lost the original one-third reading for transparent blocks");h.level.setBlockAndUpdate(target,Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL,7)) }
        h.runAtTickTime(60) { if(abs(p.voltage-7.0/3)>.001)h.fail("Scanner ignored the target block's actual comparator override");h.succeed() }
    }
    @JvmStatic @GameTest(batch="original_inventory_scanner",template="grid_empty",timeoutTicks=85)
    fun actualScannerRightClickChangesInventoryFillToOccupiedSlotsAndPersistsIt(h: GameTestHelper) {
        val y=Yard(h);val n=y.device("scanner",8);var p=n.parts[0]!!
        h.level.setBlockAndUpdate(y.pos(9),Blocks.CHEST.defaultBlockState())
        val chest=h.level.getBlockEntity(y.pos(9)) as ChestBlockEntity
        chest.setItem(0,ItemStack(Items.STONE,32));chest.setItem(1,ItemStack(Items.WOODEN_PICKAXE));chest.setChanged()
        h.runAtTickTime(15) {
            if(abs(p.voltage-5.0*33/(27*64))>.001)h.fail("Scanner substituted comparator stack-size weighting for the original inventory count reading: ${p.voltage}")
            val player=h.makeMockPlayer();player.setPos(n.blockPos.x+.5,256.0,n.blockPos.z+2.0);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
            val point=PartGeometry.body(p).center.add(Vec3.atLowerCornerOf(n.blockPos))
            n.blockState.use(h.level,player,InteractionHand.MAIN_HAND,BlockHitResult(point,Direction.UP,n.blockPos,false))
            if(p.gridState.getInt("scannerMode")!=1)h.fail("Actual scanner right-click did not change its original mode")
        }
        h.runAtTickTime(30) {
            if(abs(p.voltage-10.0/27)>.001)h.fail("Scanner did not count occupied slots independently from item totals")
            val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology();p=n.parts[0]!!
            if(p.gridState.getInt("scannerMode")!=1)h.fail("Stationary scanner mode was not saved")
            chest.setItem(2,ItemStack(Items.DIRT));chest.setChanged()
        }
        h.runAtTickTime(50) { if(abs(p.voltage-15.0/27)>.001)h.fail("Reloaded scanner stopped reading live inventory changes");h.succeed() }
    }
    @JvmStatic @GameTest(batch="original_inventory_scanner",template="grid_empty",timeoutTicks=85)
    fun scannerReadsActualForgeFluidCapabilityAndItsOutputCannotSupplyUnlimitedCurrent(h: GameTestHelper) {
        val y=Yard(h);val scanner=y.device("scanner",8);val p=scanner.parts[0]!!
        val tank=y.device("48v_fuel_generator",9);tank.tank.setFluid(FluidStack(Fluids.WATER,1000))
        h.runAtTickTime(15) {
            if(abs(p.voltage-2.5)>.001 || abs(p.powerFactor-.5)>1e-6)h.fail("Scanner failed to read the actual side-specific Forge fluid capability")
            val fuse=y.device("electrical_fuse_holder",7)
            PartInventory.get(fuse,fuse.parts[0]!!).setStackInSlot(0,ItemStack(Content.materialItems.getValue("lead_fuse_for_low_voltage_cables").get()))
            y.device("ground_cable",6)
        }
        h.runAtTickTime(35) {
            val actual=(h.level.getBlockEntity(y.pos(7)) as FaceNodeEntity).parts[0]!!.current
            // Original capacitor outputs supply a current proportional to their target voltage.
            // ElectricalLoad.getCurrent reports half that branch current because Capacitor.getCurrent is zero.
            if(abs(actual) !in .049.. .0501 || p.voltage !in 0.0.. .03 || abs(p.powerFactor-.5)>1e-6)h.fail("Scanner exceeded its original finite signal drive under a physical near-short: actual=$actual, ${p.save()}")
            tank.tank.setFluid(FluidStack(Fluids.WATER,1500))
        }
        h.runAtTickTime(55) {
            val actual=(h.level.getBlockEntity(y.pos(7)) as FaceNodeEntity).parts[0]!!.current
            if(abs(p.powerFactor-.75)>1e-6 || abs(actual) !in .074.. .0751)h.fail("Loaded scanner stopped reading fluid changes or exceeded its proportional finite signal drive: $actual")
            h.succeed()
        }
    }
}
