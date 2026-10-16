package mods.eln.modern.testing

import mods.eln.modern.*
import mods.eln.modern.thermal.SteamFluids
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln")
@PrefixGameTestTemplate(false)
object HeatExchangerGameTests {
    private fun check(h: GameTestHelper,value: Boolean,message: String) { if(!value)h.fail(message) }
    @JvmStatic @GameTest(template="empty",timeoutTicks=160)
    fun originalSteamConversionReservesWholeOutputAndAcceptsARealWaterBucket(h: GameTestHelper) {
        val node=InfrastructureGameTests.create(h,"thermal_heat_exchanger",2)
        val p=node.parts[0]!!;p.thermalState.putDouble("delta",200.0)
        InfrastructureGameTests.create(h,"signal_source",3)
        check(h,!node.getCapability(ForgeCapabilities.FLUID_HANDLER,Direction.EAST).isPresent,"Exchanger exposed a fluid tank on a horizontal signal port")
        val input=node.getCapability(ForgeCapabilities.FLUID_HANDLER,Direction.DOWN).orElseThrow { IllegalStateException("Missing bottom input") }
        val output=node.getCapability(ForgeCapabilities.FLUID_HANDLER,Direction.UP).orElseThrow { IllegalStateException("Missing top output") }
        check(h,input.getTankCapacity(0)==1000 && output.getTankCapacity(0)==1000,"Original tank sizes changed")
        val player=h.makeMockPlayer();player.abilities.instabuild=false;player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Items.WATER_BUCKET))
        val hit=BlockHitResult(Vec3.atCenterOf(node.blockPos),Direction.DOWN,node.blockPos,false)
        val result=(node.blockState.block as NodeBlock).use(node.blockState,h.level,node.blockPos,player,InteractionHand.MAIN_HAND,hit)
        check(h,result==InteractionResult.SUCCESS && input.getFluidInTank(0).amount==1000 && player.getItemInHand(InteractionHand.MAIN_HAND).`is`(Items.BUCKET),"Real water-bucket interaction failed to fill the exchanger")
        output.fill(FluidStack(SteamFluids.steam.get(),995),FluidAction.EXECUTE)
        var ready=false
        // In a large batch the signal source can cross into a chunk that starts ticking later.
        // Keep the output physically blocked until the actual control input is ready.
        for(tick in 10L..80L)h.runAtTickTime(tick) {
            if(ready)return@runAtTickTime
            if(p.thermalState.getDouble("control")>.99) {
                ready=true
                check(h,input.getFluidInTank(0).amount==1000 && output.getFluidInTank(0).amount==995,"Full output tank consumed input or discarded steam")
                output.drain(10,FluidAction.EXECUTE)
                h.runAtTickTime(tick+20) {
                    check(h,input.getFluidInTank(0).amount==999 && output.getFluidInTank(0).amount==995,"Exchanger did not preserve the original 1:10 water/steam ratio")
                    val heat=p.thermalState.getDouble("convertedHeatJ")
                    check(h,abs(heat+10/.45*1.25)<1e-9,"Original steam heat cost changed")
                    check(h,-heat>10*FuelFluids.joulesPerMb(SteamFluids.steam.get()),"Steam production created more usable energy than the heat consumed")
                    val saved=node.saveWithoutMetadata();node.load(saved);node.changedTopology()
                }
                h.runAtTickTime(tick+45) {
                    check(h,node.exchanger.input.fluidAmount==999 && node.exchanger.output.fluidAmount==995,"Exchanger reload lost one of its sided tanks")
                    h.succeed()
                }
            }else if(tick==80L)h.fail("Exchanger's separate signal port did not receive its control within 80 ticks: ${p.pinVoltages.toList()}, mask=${p.connectedMask}, state=${p.thermalState}")
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=80)
    fun coldExchangerPreservesWaterDespiteAFullControlSignal(h: GameTestHelper) {
        val node=InfrastructureGameTests.create(h,"thermal_heat_exchanger",2)
        InfrastructureGameTests.create(h,"signal_source",3)
        val input=node.getCapability(ForgeCapabilities.FLUID_HANDLER,Direction.DOWN).orElseThrow { IllegalStateException("Missing input tank") }
        check(h,input.fill(FluidStack(Fluids.WATER,1000),FluidAction.SIMULATE)==1000 && node.exchanger.input.isEmpty,"Input simulation mutated its tank")
        input.fill(FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE)
        h.runAtTickTime(50) {
            check(h,node.exchanger.input.fluidAmount==1000 && node.exchanger.output.isEmpty && node.parts[0]!!.energy<1e-9,"Cold water generated steam without the original minimum thermal temperature")
            h.succeed()
        }
    }
}
