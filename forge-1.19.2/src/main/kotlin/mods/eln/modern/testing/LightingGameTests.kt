package mods.eln.modern.testing

import mods.eln.modern.*
import mods.eln.modern.lighting.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object LightingGameTests {
    @JvmStatic @GameTest(batch="lighting_recovery",template="grid_empty",timeoutTicks=240)
    fun sectionUpdatesPreserveProjectedAndOrdinaryEmittersAndStillExpireTheProjection(h: GameTestHelper) {
        GameTestYards.clear(h,290,2,5)
        val base=h.absolutePos(BlockPos.ZERO);val spot=BlockPos(base.x+7,290,base.z+8);val torch=spot.east(5)
        h.level.setBlockAndUpdate(torch.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(torch,Blocks.TORCH.defaultBlockState())
        val tickets=h.level.forcedChunks.toSet()
        fun revisit() {
            val engine=h.level.chunkSource.lightEngine
            engine.updateSectionStatus(net.minecraft.core.SectionPos.of(spot),false)
            engine.enableLightSources(net.minecraft.world.level.ChunkPos(spot),true)
            engine.checkBlock(spot);engine.checkBlock(torch);engine.tryScheduleUpdate()
        }
        var torchReady=-1L;var relitAt=-1L
        for(tick in 1L..230L)h.runAtTickTime(tick) {
            if(torchReady<0) {
                if(tick%5==0L)revisit()
                if(tick>=10 && h.level.getBrightness(LightLayer.BLOCK,torch)==14)torchReady=tick
                else if(tick>=80)h.fail("Vanilla torch fixture never became ready before adding any projection: ${LightRecovery.describe(h.level,torch)}")
                return@runAtTickTime
            }
            if(relitAt<0 || tick<=relitAt+20)LightSpotBlock.illuminate(h.level,spot,15)
            if(relitAt<0 && tick%5==0L)revisit()
            if(relitAt<0) {
                if(h.level.getBrightness(LightLayer.BLOCK,spot)==15 && h.level.getBrightness(LightLayer.BLOCK,torch)==14) {
                    revisit();relitAt=tick
                } else if(tick-torchReady>=80)h.fail("Real projected/vanilla light did not reach bounded readiness: ${LightRecovery.describe(h.level,spot)}; ${LightRecovery.describe(h.level,torch)}")
            } else if(tick==relitAt+20) {
                if(h.level.getBrightness(LightLayer.BLOCK,spot)!=15 || h.level.getBrightness(LightLayer.BLOCK,torch)!=14 || h.level.forcedChunks.toSet()!=tickets)h.fail("Vanilla section/source updates lost an emitter or changed chunk loading")
            } else if(tick==relitAt+50) {
                if(h.level.getBlockState(spot).block==Content.lightSpot.get() || h.level.getBrightness(LightLayer.BLOCK,spot)>9 || h.level.getBrightness(LightLayer.BLOCK,torch)!=14)h.fail("Projection expiry after relighting left extra light or damaged the ordinary torch")
                h.succeed()
            }
        }
    }
    private class Yard(val h: GameTestHelper) {
        val base=h.absolutePos(BlockPos.ZERO)
        init {
            // These above-terrain fixtures sit outside the template height; clear their own spaced yard on every run.
            for(x in 0..16)for(z in 0..12)for(y in 279..319)h.level.setBlockAndUpdate(BlockPos(base.x+x,y,base.z+z),Blocks.AIR.defaultBlockState())
        }
        fun create(id: String,x: Int,z: Int=2,rotation: Int=0): FaceNodeEntity {
            val pos=BlockPos(base.x+x,280,base.z+z);val d=PartDefinition.byId.getValue(id)
            h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
            h.level.setBlockAndUpdate(pos,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
            return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { n -> n.parts[0]=PartState(d,Direction.DOWN).apply { this.rotation=rotation };n.changedTopology() }
        }
        fun lamp(x: Int,z: Int=2,bulb: String="50v_led_bulb",wired: Boolean=true,channel: String="Default channel"): FaceNodeEntity {
            val n=create("classic_lamp_socket",x,z);n.parts[0]!!.apply { enabled=wired;this.channel=channel }
            val inventory=PartInventory.get(n,n.parts[0]!!)
            inventory.setStackInSlot(0,ItemStack(Content.materialItems.getValue(bulb).get()))
            inventory.setStackInSlot(1,ItemStack(Content.partItems.getValue("low_voltage_cable").get()))
            return n
        }
    }
    @JvmStatic @GameTest(batch="lighting",template="grid_empty",timeoutTicks=320)
    fun realBulbLoadProjectedLightSavedLifetimeAndRemoval(h: GameTestHelper) {
        val yard=Yard(h);yard.create("electrical_source",1);val lamp=yard.lamp(2)
        var ready=false
        for(tick in 95L..240L)h.runAtTickTime(tick) {
            if(ready)return@runAtTickTime
            val p=lamp.parts[0]!!;val spot=BlockPos.of(p.thermalState.getLong("lampSpot"))
            if(p.signal!=14 || abs(p.thermalState.getDouble("lampWatts")-15)>.02)h.fail("Real 50 V / 15 W LED failed light and power equations: ${p.voltage} V, ${p.signal} light, ${p.thermalState}")
            if(spot!=lamp.blockPos.above(3) || h.level.getBlockState(spot).block!=Content.lightSpot.get())h.fail("Original lamp did not place its three-block light projection")
            if(h.level.getBrightness(LightLayer.BLOCK,spot)<14) {
                if(tick==240L)h.fail("Original lamp failed actual light after bounded queued-light readiness: ${mods.eln.modern.lighting.LightRecovery.describe(h.level,spot)}")
                return@runAtTickTime
            }
            ready=true
            val life=PartInventory.get(lamp,p).getStackInSlot(0).tag!!.getDouble("life")
            if(life>=512 || life<511)h.fail("Installed LED failed original finite lifetime")
            val saved=lamp.saveWithoutMetadata();lamp.load(saved);lamp.changedTopology()
            if(PartInventory.get(lamp,lamp.parts[0]!!).getStackInSlot(0).tag!!.getDouble("life")!=life)h.fail("Bulb lifetime or cable was lost on reload")
            h.runAtTickTime(tick+15) { lamp.parts[0]!!.enabled=false;lamp.changedTopology() }
            h.runAtTickTime(tick+30) {
                if(lamp.parts[0]!!.signal!=0 || !h.level.getBlockState(lamp.blockPos.above(3)).isAir || h.level.getBrightness(LightLayer.BLOCK,lamp.blockPos.above(3))!=0)h.fail("Unpowered lamp left its real projected light behind")
                lamp.parts[0]!!.enabled=true
                val inv=PartInventory.get(lamp,lamp.parts[0]!!);inv.getStackInSlot(0).orCreateTag.putDouble("life",.000001);lamp.changedTopology()
            }
            h.runAtTickTime(tick+55) {
                if(!PartInventory.get(lamp,lamp.parts[0]!!).getStackInSlot(0).isEmpty || lamp.parts[0]!!.signal!=0)h.fail("Spent bulb continued to light or consume power")
                h.succeed()
            }
        }
    }
    @JvmStatic @GameTest(batch="lighting",template="grid_empty",timeoutTicks=160)
    fun wirelessLampSupplyBillsActualLoadsAndPreservesToggleChannels(h: GameTestHelper) {
        val yard=Yard(h);yard.create("electrical_source",1)
        val board=yard.create("lamp_supply",2,rotation=2)
        PartInventory.get(board,board.parts[0]!!).setStackInSlot(0,ItemStack(Content.partItems.getValue("low_voltage_cable").get(),2))
        val prefix="lighting-${yard.base.asLong()}-${h.level.gameTime}"
        val channelA="$prefix-a";val channelB="$prefix-b";val channelC="$prefix-c"
        val a=yard.lamp(10,wired=false,channel=channelA)
        val b=yard.lamp(10,6,wired=false,channel=channelB)
        val c=yard.lamp(10,10,wired=false,channel=channelC)
        val control="$prefix-switch"
        LampChannels.write(board.parts[0]!!,listOf(LampChannel(channelA,"true",0),LampChannel(channelB,"false",1),LampChannel(channelC,control,2)))
        val signal=yard.create("signal_source",2,10).parts[0]!!.apply { value=0.0 }
        val tx=yard.create("wireless_signal_transmitter",3,10,rotation=2);tx.parts[0]!!.channel=control;tx.changedTopology()
        h.runAtTickTime(25) {
            if(a.parts[0]!!.signal!=14 || b.parts[0]!!.signal!=0 || c.parts[0]!!.signal!=0)h.fail("Supply channel names and true/false controls failed: a=${a.parts[0]!!.save()}, b=${b.parts[0]!!.signal}, c=${c.parts[0]!!.signal}, board=${board.parts[0]!!.save()}")
            if(abs(board.parts[0]!!.voltage*board.parts[0]!!.current-15)>.03)h.fail("Wireless lamp's real 15 W was not billed to its supply")
            if(abs(board.parts[0]!!.energy-a.parts[0]!!.energy)>1e-4)h.fail("Lamp supply generated or duplicated wireless energy")
            signal.value=5.0
        }
        h.runAtTickTime(50) {
            if(c.parts[0]!!.signal!=14 || abs(board.parts[0]!!.voltage*board.parts[0]!!.current-30)>.04)h.fail("Rising wireless control did not toggle the second real lamp load")
            signal.value=0.0
            val saved=board.saveWithoutMetadata();board.load(saved);board.changedTopology()
            if(LampChannels.read(board.parts[0]!!)[2].power!=channelC || board.parts[0]!!.gridState.getInt("lampToggle") and 4==0)h.fail("Supply channels and toggle memory did not persist")
        }
        h.runAtTickTime(75) { if(c.parts[0]!!.signal!=14)h.fail("Falling control reset the latched supply");signal.value=5.0 }
        h.runAtTickTime(100) {
            if(c.parts[0]!!.signal!=0 || a.parts[0]!!.signal!=14 || b.parts[0]!!.signal!=0)h.fail("Second rising control failed to toggle only its own channel off")
            if(abs(board.parts[0]!!.energy-a.parts[0]!!.energy-c.parts[0]!!.energy)>1e-4)h.fail("Shared lamp supply failed aggregate energy accounting")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="lighting",template="grid_empty",timeoutTicks=155)
    fun fluorescentWarmupAndOpaqueObstacleMoveTheRealLightSpot(h: GameTestHelper) {
        val yard=Yard(h);yard.create("electrical_source",1);val n=yard.lamp(2,bulb="50v_economic_light_bulb")
        h.runAtTickTime(110) {
            val p=n.parts[0]!!
            if(p.signal!=15 || p.thermalState.getDouble("lampStability")<1)h.fail("Economic bulb failed original four-second warm-up")
            h.level.setBlockAndUpdate(n.blockPos.above(2),Blocks.STONE.defaultBlockState())
        }
        h.runAtTickTime(135) {
            if(!n.parts[0]!!.thermalState.getBoolean("lampSpotHere") || h.level.getBlockState(n.blockPos.above(3)).block==Content.lightSpot.get())h.fail("Opaque obstruction did not redirect/expire the projected light")
            if(n.blockState.getLightEmission(h.level,n.blockPos)!=15)h.fail("Obstructed lamp failed to illuminate its own carrier")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="lighting",template="grid_empty",timeoutTicks=110)
    fun emergencyBatteryChargesSurvivesReloadAndRunsDownDuringOutage(h: GameTestHelper) {
        val yard=Yard(h);val source=yard.create("electrical_source",1)
        val lamp=yard.create("50v_emergency_lamp",2);val p=lamp.parts[0]!!.apply { enabled=true }
        var charged=0.0
        h.runAtTickTime(25) {
            if(p.active || p.charge<=.5 || abs(p.voltage*p.current-10)>.02)h.fail("Emergency lamp failed its original 10 W charging mode: ${p.save()}")
            charged=p.charge*6000
            val saved=lamp.saveWithoutMetadata();lamp.load(saved);lamp.changedTopology()
            if(lamp.parts[0]!!.charge*6000!=charged)h.fail("Backup charge failed persistence")
            source.parts[0]!!.enabled=false;source.changedTopology()
        }
        h.runAtTickTime(50) {
            val restored=lamp.parts[0]!!
            if(!restored.active || restored.signal!=6 || restored.charge*6000>=charged || abs((charged-restored.charge*6000)-25*.05*5)>.3)h.fail("Emergency lamp did not draw finite 5 W backup power during the outage: ${restored.save()}")
            if(lamp.blockState.getLightEmission(h.level,lamp.blockPos)!=6)h.fail("Emergency lamp did not illuminate its real carrier")
            restored.charge=.1/6000
        }
        h.runAtTickTime(60) {
            if(lamp.parts[0]!!.charge!=0.0 || lamp.parts[0]!!.signal!=0)h.fail("Empty backup battery continued to illuminate")
            source.parts[0]!!.enabled=true;source.changedTopology()
        }
        h.runAtTickTime(85) {
            if(lamp.parts[0]!!.active || lamp.parts[0]!!.charge<=0)h.fail("Restored power failed to recharge the exhausted lamp")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="lighting",template="grid_empty",timeoutTicks=100)
    fun emergencyLampSupplyAccountsForActualStoredEnergyAndOutage(h: GameTestHelper) {
        val yard=Yard(h);yard.create("electrical_source",1).parts[0]!!.value=200.0
        val board=yard.create("lamp_supply",2,rotation=2);val bp=board.parts[0]!!
        PartInventory.get(board,bp).setStackInSlot(0,ItemStack(Content.partItems.getValue("medium_voltage_cable").get()))
        val exit="exit-${yard.base.asLong()}-${h.level.gameTime}"
        LampChannels.write(bp,listOf(LampChannel(exit,"true",0)))
        val lamp=yard.create("200v_emergency_lamp",10);val p=lamp.parts[0]!!.apply { channel=exit }
        var charge=0.0;var billed=0.0
        h.runAtTickTime(20) { charge=p.charge*12000;billed=bp.energy }
        h.runAtTickTime(45) {
            if(p.signal!=0 || abs(bp.voltage*bp.current-25)>.03 || abs(p.charge*12000-charge-(bp.energy-billed))>1e-4)h.fail("200 V supply did not bill the backup battery's original 25 W charge: ${p.save()}")
            LampChannels.write(bp,listOf(LampChannel(exit,"false",0)));board.changedTopology()
        }
        h.runAtTickTime(65) {
            if(p.signal!=8 || !p.active || bp.current>1e-5)h.fail("Disabled supply failed to trigger the 200 V emergency lamp's backup")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="lighting",template="grid_empty",timeoutTicks=150)
    fun twoHalogenBulbsDriveTheOriginalConeAndRespectOcclusion(h: GameTestHelper) {
        val yard=Yard(h);yard.create("electrical_source",2).parts[0]!!.value=200.0
        val n=yard.create("basic_floodlight",2,3);val p=n.parts[0]!!
        val inv=PartInventory.get(n,p)
        if(inv.isItemValid(0,ItemStack(Content.materialItems.getValue("50v_led_bulb").get())))h.fail("Floodlight accepted an incompatible bulb socket")
        repeat(2) { inv.setStackInSlot(it,ItemStack(Content.materialItems.getValue("200v_halogen_bulb").get())) }
        h.runAtTickTime(25) {
            if(p.signal!=15 || abs(p.thermalState.getDouble("lampWatts")-1000)>5)h.fail("Two original 500 W halogen loads did not draw actual circuit power: ${p.save()}")
            if(h.level.getBlockState(n.blockPos.south(32)).block!=Content.lightSpot.get())h.fail("Two halogen bulbs failed their combined 32-block beam range")
            if(inv.getStackInSlot(0).tag!!.getDouble("life")>=128)h.fail("Powered halogen bulb failed original finite aging")
            p.value=90.0;p.expression="0,45";n.changedTopology()
            h.level.setBlockAndUpdate(n.blockPos.east(6),Blocks.STONE.defaultBlockState())
        }
        h.runAtTickTime(55) {
            if(h.level.getBlockState(n.blockPos.east(4)).block!=Content.lightSpot.get() || !h.level.getBlockState(n.blockPos.east(8)).isAir)h.fail("Floodlight ignored the solid obstacle or left its old beam active")
            if(h.level.getBlockState(n.blockPos.east(6)).block!=Blocks.STONE)h.fail("Projected light overwrote a solid block")
            if(h.level.getBlockState(n.blockPos.south(32)).block==Content.lightSpot.get())h.fail("Aimed-away floodlight left stale light spots")
            p.expression="90,0";n.changedTopology()
        }
        h.runAtTickTime(80) {
            if(h.level.getBlockState(n.blockPos.above(32)).block!=Content.lightSpot.get())h.fail("Vertical tilt failed to redirect the real light beam")
            val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology()
            if(n.parts[0]!!.expression!="90,0" || PartInventory.get(n,n.parts[0]!!).getStackInSlot(1).isEmpty)h.fail("Floodlight aim and bulbs failed reload")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="lighting",template="grid_empty",timeoutTicks=110)
    fun motorizedFloodlightHasThreeIndependentFiveVoltControlInputs(h: GameTestHelper) {
        val yard=Yard(h);yard.create("electrical_source",4).parts[0]!!.value=200.0
        val n=yard.create("motorized_floodlight",4,3);val p=n.parts[0]!!
        PartInventory.get(n,p).setStackInSlot(0,ItemStack(Content.materialItems.getValue("200v_halogen_bulb").get()))
        val head=yard.create("signal_source",5,3);head.parts[0]!!.value=2.5
        val swivel=yard.create("signal_source",3,3);swivel.parts[0]!!.value=1.25
        val spread=yard.create("signal_source",4,4);spread.parts[0]!!.value=2.5
        h.runAtTickTime(30) {
            if(abs(p.gridState.getDouble("swivel")-90)>.02 || abs(p.gridState.getDouble("head")-90)>.02 || abs(p.gridState.getDouble("beamWidth")-22.5)>.02)h.fail("Independent 5 V floodlight control stages failed: ${p.gridState}")
            if(p.signal!=15 || h.level.getBlockState(n.blockPos.above(16)).block!=Content.lightSpot.get())h.fail("Motorized tilt failed to illuminate its actual target")
            head.parts[0]!!.value=0.0;swivel.parts[0]!!.value=0.0;spread.parts[0]!!.value=0.0
        }
        h.runAtTickTime(65) {
            if(p.gridState.getDouble("head")>1e-4 || p.gridState.getDouble("swivel")>1e-4 || p.gridState.getDouble("beamWidth")>1e-4 || h.level.getBlockState(n.blockPos.south(16)).block!=Content.lightSpot.get())h.fail("Motorized controls did not independently return to the narrow horizontal beam")
            h.succeed()
        }
    }
}
