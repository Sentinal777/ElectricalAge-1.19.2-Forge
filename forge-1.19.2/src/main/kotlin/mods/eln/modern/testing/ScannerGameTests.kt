package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.crafting.conditions.ICondition
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object ScannerGameTests {
    @JvmStatic @GameTest(batch="portable_scanner",template="grid_empty",timeoutTicks=40)
    fun originalScannerBootStopEnergyNbtDamageAndDropStatesFollowActualItemCallbacks(h: GameTestHelper) {
        val stack=ItemStack(Content.materialItems.getValue("x_ray_scanner").get());val player=h.makeMockPlayer();player.setItemInHand(InteractionHand.MAIN_HAND,stack)
        val spec=ElectricalItems.spec(stack)!!
        if(spec.capacity!=100000.0 || spec.chargePower!=400.0 || spec.dischargePower!=300.0 || ElectricalItems.energy(stack)!=20000.0 || PortableScanner.state(stack)!=PortableScanner.BOOT || stack.maxStackSize!=1)h.fail("Original scanner ratings or its 20-percent charged initial boot changed")
        val previous=stack.copy();PortableScanner.consume(stack,.5)
        if(stack.item.shouldCauseReequipAnimation(previous,stack,false) || stack.item.shouldCauseBlockBreakReset(previous,stack) || !stack.item.shouldCauseReequipAnimation(previous,stack,true))h.fail("Operating scanner NBT repeatedly lowered its held display or interrupted mining; a real slot change must still animate")
        ElectricalItems.setEnergy(stack,20000.0)
        repeat(40) { stack.item.inventoryTick(stack,h.level,player,9,false) }
        val restored=ItemStack.of(stack.save(CompoundTag()));player.setItemInHand(InteractionHand.MAIN_HAND,restored)
        if(restored.tag!!.getShort("c").toInt()!=40 || restored.tag!!.getDouble("e")!=20000.0 || restored.tag!!.contains("energy"))h.fail("Scanner lost its original saved e/s/c/d NBT")
        repeat(39) { restored.item.inventoryTick(restored,h.level,player,9,false) }
        if(PortableScanner.state(restored)!=PortableScanner.BOOT)h.fail("Scanner boot completed before its original four seconds")
        restored.item.inventoryTick(restored,h.level,player,9,false)
        if(PortableScanner.state(restored)!=PortableScanner.RUN || PortableScanner.consume(restored,.5)!=150.0)h.fail("Scanner failed its running transition or original 300 W consumption")
        restored.item.use(h.level,player,InteractionHand.MAIN_HAND)
        if(PortableScanner.state(restored)!=PortableScanner.STOP || PortableScanner.consume(restored,.5)!=150.0)h.fail("Actual item use failed scanner shutdown or stopped consuming before shutdown completed")
        repeat(20) { restored.item.inventoryTick(restored,h.level,player,9,false) }
        if(PortableScanner.state(restored)!=PortableScanner.IDLE || ElectricalItems.energy(restored)!=19700.0)h.fail("Scanner did not finish its one-second stop with conserved energy")
        restored.item.use(h.level,player,InteractionHand.MAIN_HAND);restored.item.onDroppedByPlayer(restored,player)
        if(PortableScanner.state(restored)!=PortableScanner.IDLE)h.fail("Dropping scanner did not stop it")
        repeat(12) { restored.item.onBlockStartBreak(restored,h.absolutePos(BlockPos.ZERO),player) }
        restored.item.use(h.level,player,InteractionHand.MAIN_HAND)
        if(PortableScanner.damage(restored)!=12 || PortableScanner.state(restored)!=PortableScanner.IDLE || PortableScanner.consume(restored,.5)!=0.0)h.fail("Scanner did not retain its four original broken-screen levels and stop when fully damaged")
        h.succeed()
    }
    @JvmStatic @GameTest(batch="portable_scanner",template="grid_empty",timeoutTicks=40)
    fun scannerPriorityChargingOperatingDrainLowEnergyCutoffAndCraftingSwitchRemainFinite(h: GameTestHelper) {
        val scanner=PortableEnergyGameTests.item("x_ray_scanner",20000.0);scanner.orCreateTag.putByte("s",PortableScanner.RUN.toByte())
        val donor=PortableEnergyGameTests.item("portable_battery_pack",5000.0);var consumed=0.0
        repeat(20) { consumed+=PortableScanner.consume(scanner,.5);ElectricalItems.transfer(listOf(scanner,donor)) }
        if(consumed!=3000.0 || ElectricalItems.energy(scanner)!=21000.0 || ElectricalItems.energy(donor)!=1000.0)h.fail("Scanner drain and original 400 W charging did not conserve the 25000 J carried inventory")
        ElectricalItems.setEnergy(scanner,150.0);PortableScanner.consume(scanner,.5)
        if(PortableScanner.state(scanner)!=PortableScanner.IDLE || ElectricalItems.energy(scanner)!=0.0)h.fail("Exhausted scanner did not stop immediately")
        ElectricalItems.setEnergy(scanner,300.0);val p=h.makeMockPlayer();p.setItemInHand(InteractionHand.MAIN_HAND,scanner);scanner.item.use(h.level,p,InteractionHand.MAIN_HAND)
        if(PortableScanner.state(scanner)!=PortableScanner.IDLE)h.fail("Scanner lost its original strict one-second start-energy threshold")
        val config=mods.eln.config.ForgePortConfig.scannerCrafting;val old=config.get()
        try {
            config.set(false);if(ScannerCraftingCondition().test(ICondition.IContext.EMPTY))h.fail("Disabled original scanner crafting condition remained enabled")
            config.set(true);if(!ScannerCraftingCondition().test(ICondition.IContext.EMPTY))h.fail("Enabled scanner crafting condition remained disabled")
        }finally { config.set(old) }
        h.succeed()
    }
    @JvmStatic @GameTest(batch="portable_scanner",template="grid_empty",timeoutTicks=50)
    fun realScannerRayIntegrationFindsOreBehindStoneHonorsRangeAndDoesNotLoadMissingChunks(h: GameTestHelper) {
        GameTestYards.clear(h,303,1,6);val b=h.absolutePos(BlockPos.ZERO)
        for(x in 2..10)for(y in 303..307)h.level.setBlockAndUpdate(BlockPos(b.x+x,y,b.z+8),Blocks.STONE.defaultBlockState())
        val ore=BlockPos(b.x+5,304,b.z+9);h.level.setBlockAndUpdate(ore,Blocks.DIAMOND_ORE.defaultBlockState())
        val eye=Vec3(b.x+5.5,304.5,b.z+5.5)
        val image=ScannerImage.sample(h.level,eye,0.0,0.0,5.0)
        if(image.size!=20 || image[0].size!=32 || image[10][16].green<.9 || image[10][16].red<.35)h.fail("Original 32-by-20 scanner did not integrate diamond ore behind the actual stone wall")
        val away=ScannerImage.sample(h.level,eye,180.0,0.0,5.0)
        if(away[10][16].green!=0.0)h.fail("Scanner ignored the actual user's look direction")
        h.level.setBlockAndUpdate(ore,Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(ore.south(2),Blocks.DIAMOND_ORE.defaultBlockState())
        if(ScannerImage.sample(h.level,eye,0.0,0.0,5.0)[10][16].green!=0.0)h.fail("Scanner saw ore beyond its original five-block range")
        val far=BlockPos(20000000,304,20000000)
        if(h.level.chunkSource.getChunkNow(far.x shr 4,far.z shr 4)!=null)h.fail("Missing-chunk scanner fixture unexpectedly loaded")
        val missing=ScannerImage.sample(h.level,Vec3.atCenterOf(far),0.0,0.0,10.0)
        if(h.level.chunkSource.getChunkNow(far.x shr 4,far.z shr 4)!=null || missing.any { row -> row.any { it.green!=0.0 || !it.blue.isFinite() } })h.fail("Scanner forced an unavailable chunk or produced nonfinite pixels")
        h.succeed()
    }
}
