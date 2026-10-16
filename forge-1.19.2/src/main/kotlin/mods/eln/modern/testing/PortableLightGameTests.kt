package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object PortableLightGameTests {
    @JvmStatic @GameTest(batch="portable_lights",template="grid_empty",timeoutTicks=40)
    fun realFlashlightUseCyclesSavedModesAndUsesOriginalFinitePowerAndThresholds(h: GameTestHelper) {
        val player=h.makeMockPlayer()
        for((id,capacity,charge) in listOf(Triple("small_flashlight",6000.0,100.0),Triple("improved_flashlight",24000.0,400.0))) {
            val stack=PortableEnergyGameTests.item(id,capacity);player.setItemInHand(InteractionHand.MAIN_HAND,stack)
            val spec=ElectricalItems.spec(stack)!!
            if(stack.maxStackSize!=1 || spec.capacity!=capacity || spec.chargePower!=charge || spec.dischargePower!=0.0 || spec.priority!=0)h.fail("Original flashlight storage, charge power or priority changed")
            stack.item.use(h.level,player,InteractionHand.MAIN_HAND)
            if(PortableLights.state(stack)!=1 || PortableLights.light(stack)!=(if(id=="small_flashlight")10 else 15))h.fail("Actual right-click did not turn on the original flashlight")
            if(PortableLights.consume(stack,.5)!=10.0 || ElectricalItems.energy(stack)!=capacity-10)h.fail("Normal flashlight did not consume 20 watts for half a second")
            stack.item.use(h.level,player,InteractionHand.MAIN_HAND)
            if(PortableLights.state(stack)!=2 || PortableLights.light(stack)!=(if(id=="small_flashlight")12 else 15))h.fail("Actual right-click did not select original boost light")
            if(PortableLights.consume(stack,.5)!=25.0)h.fail("Boosted flashlight did not consume 50 watts")
            val restored=net.minecraft.world.item.ItemStack.of(stack.save(CompoundTag()))
            if(PortableLights.state(restored)!=2 || ElectricalItems.energy(restored)!=capacity-35)h.fail("Flashlight mode or fractional energy did not survive item NBT")
            stack.item.use(h.level,player,InteractionHand.MAIN_HAND)
            if(PortableLights.state(stack)!=0 || PortableLights.consume(stack,.5)!=0.0)h.fail("Third right-click did not turn off flashlight power")
            ElectricalItems.setEnergy(stack,50.0);stack.orCreateTag.putInt("LightState",2)
            if(PortableLights.light(stack)!=0)h.fail("Original one-second emission threshold was lost")
            PortableLights.consume(stack,.5);PortableLights.consume(stack,.5)
            if(PortableLights.state(stack)!=0 || ElectricalItems.energy(stack)!=0.0)h.fail("Empty flashlight remained powered or consumed negative energy")
        }
        h.succeed()
    }
    @JvmStatic @GameTest(batch="portable_lights",template="grid_empty",timeoutTicks=40)
    fun carriedFlashlightReceivesRateLimitedPriorityChargeAfterItsActualConsumption(h: GameTestHelper) {
        val battery=PortableEnergyGameTests.item("portable_battery",1000.0);val light=PortableEnergyGameTests.item("small_flashlight",100.0)
        light.orCreateTag.putInt("LightState",1);var consumed=0.0
        repeat(20) { consumed+=PortableLights.consume(light,.5);ElectricalItems.transfer(listOf(battery,light)) }
        if(consumed!=200.0 || abs(ElectricalItems.energy(battery)+ElectricalItems.energy(light)+consumed-1100)>1e-9 || ElectricalItems.energy(light)!=900.0 || ElectricalItems.energy(battery)!=0.0)h.fail("Portable lamp and donor did not conserve original half-second work and charge limits")
        h.succeed()
    }
    @JvmStatic @GameTest(batch="portable_lights",template="grid_empty",timeoutTicks=125)
    fun unselectedCarriedFlashlightProjectsBothOriginalRaysStopsAtSolidWallAndActuallyExpires(h: GameTestHelper) {
        GameTestYards.clear(h,210,1,7)
        val base=h.absolutePos(BlockPos.ZERO);val player=h.makeMockPlayer()
        player.setPos(base.x+4.5,210.0,base.z+4.5);player.yRot=-90f;player.xRot=0f
        val stack=PortableEnergyGameTests.item("improved_flashlight",24000.0);stack.orCreateTag.putInt("LightState",2)
        for(y in 210..213)h.level.setBlockAndUpdate(BlockPos(base.x+7,y,base.z+4),Blocks.STONE.defaultBlockState())
        val upper=BlockPos(base.x+6,211,base.z+4);val lower=upper.below();val behind=BlockPos(base.x+8,211,base.z+4)
        var litAt=-1L
        for(tick in 1L..110L)h.runAtTickTime(tick) {
            if(litAt<0) {
                stack.item.inventoryTick(stack,h.level,player,12,false)
                val ready=listOf(upper,lower).all { h.level.getBlockState(it).block==Content.lightSpot.get() && h.level.getBrightness(LightLayer.BLOCK,it)==15 }
                if(ready && tick>=15) { litAt=tick;stack.orCreateTag.putInt("LightState",0) }
                else if(tick>=80)h.fail("Projected light failed to register after bounded chunk-light readiness: ${listOf(upper,lower).map { mods.eln.modern.lighting.LightRecovery.describe(h.level,it) }}")
                if(h.level.getBlockState(behind).block==Content.lightSpot.get())h.fail("Carried flashlight projected through a solid wall")
            }else if(tick==litAt+25) {
                for(pos in listOf(upper,lower))if(h.level.getBlockState(pos).block==Content.lightSpot.get() || h.level.getBrightness(LightLayer.BLOCK,pos)!=0) {
                    val nearby=BlockPos.betweenClosed(pos.offset(-14,-14,-14),pos.offset(14,14,14)).mapNotNull { at ->
                        val chunk=h.level.chunkSource.getChunkNow(at.x shr 4,at.z shr 4) ?: return@mapNotNull null
                        val state=chunk.getBlockState(at)
                        if(state.getLightEmission(h.level,at)>0)"${at.immutable()}=$state" else null
                    }
                    h.fail("Switched-off flashlight expiry failed: ${mods.eln.modern.lighting.LightRecovery.describe(h.level,pos)}, nearbyEmitters=$nearby")
                }
                h.succeed()
            }
        }
    }
}
