package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln")
@PrefixGameTestTemplate(false)
object PortGameTests {
    private fun part(helper: GameTestHelper, id: String, x: Int, z: Int=2, rotation: Int=0, y: Int=2): PartState {
        val definition=PartDefinition.byId.getValue(id)
        helper.setBlock(x,y-1,z,Blocks.STONE)
        helper.setBlock(x,y,z,if(definition.machine)Content.machine.get() else Content.node.get())
        val entity=helper.getBlockEntity(BlockPos(x,y,z)) as FaceNodeEntity
        val part=PartState(definition,Direction.DOWN);part.rotation=rotation
        entity.parts[0]=part;entity.changedTopology()
        if(definition.behavior==Behavior.SENSOR)PartInventory.get(entity,part).setStackInSlot(0,ItemStack(Content.partItems.getValue("high_voltage_cable").get()))
        return part
    }
    private fun node(h: GameTestHelper,x: Int,z: Int=2,y: Int=2)=h.getBlockEntity(BlockPos(x,y,z)) as FaceNodeEntity
    private fun expect(helper: GameTestHelper,condition: Boolean,message: String) { if(!condition)helper.fail(message) }

    @JvmStatic @GameTest(template="empty",timeoutTicks=100)
    fun resistorCircuitAndDisconnection(h: GameTestHelper) {
        part(h,"electrical_source",1)
        part(h,"low_voltage_cable",2)
        val resistor=part(h,"creative_power_resistor",3,rotation=2)
        part(h,"ground_cable",4)
        h.runAtTickTime(20) {
            expect(h,abs(resistor.current-.5)<.01,"50 V / 100 Ω circuit current was ${resistor.current}")
            expect(h,abs(resistor.voltage-resistor.voltageB-50)<.1,"Incorrect resistor voltage")
            h.setBlock(2,2,2,Blocks.AIR)
        }
        h.runAtTickTime(40) {
            expect(h,abs(resistor.current)<1e-6,"Current continued after wire removal: ${resistor.current}")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=100)
    fun breakerTripsAndResetWorks(h: GameTestHelper) {
        part(h,"electrical_source",1)
        val breaker=part(h,"10a_electrical_breaker",2,rotation=2)
        part(h,"creative_power_resistor",3,rotation=2).value=1.0;node(h,3).changedTopology()
        part(h,"ground_cable",4)
        h.runAtTickTime(20) {
            expect(h,breaker.tripped && !breaker.enabled,"10 A preview breaker did not trip under 50 A load")
            node(h,3).parts[0]!!.value=100.0
            breaker.tripped=false;breaker.enabled=true;node(h,2).changedTopology()
        }
        h.runAtTickTime(40) {
            expect(h,!breaker.tripped && abs(breaker.current-.5)<.01,"Breaker did not conduct safely after reset")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=320)
    fun poweredFurnaceSmeltsAndConsumesInput(h: GameTestHelper) {
        part(h,"electrical_source",1).value=800.0
        part(h,"electrical_furnace",2,rotation=1)
        val furnace=node(h,2)
        PartInventory.get(furnace,furnace.parts[0]!!).setStackInSlot(0,ItemStack(Content.materialItems.getValue("800v_tungsten_heating_element").get()))
        furnace.inventory.setStackInSlot(0,ItemStack(Items.RAW_IRON))
        h.runAtTickTime(260) {
            expect(h,furnace.inventory.getStackInSlot(2).`is`(Items.IRON_INGOT),"Powered furnace produced no iron ingot")
            expect(h,furnace.inventory.getStackInSlot(0).isEmpty,"Furnace did not consume the raw iron")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=180)
    fun maceratorUsesDatapackRecipe(h: GameTestHelper) {
        part(h,"electrical_source",1).value=200.0
        part(h,"240v_macerator",2)
        val machine=node(h,2);machine.inventory.setStackInSlot(0,ItemStack(Items.RAW_COPPER))
        h.runAtTickTime(175) {
            val output=machine.inventory.getStackInSlot(2)
            expect(h,output.`is`(Content.materialItems["copper_dust"]!!.get()) && output.count==2,"Macerator failed ore doubling recipe")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=100)
    fun andGateRespondsToChangingInputs(h: GameTestHelper) {
        val left=part(h,"signal_source",2,3)
        part(h,"signal_source",3,4)
        part(h,"and_chip",3,3)
        val meter=part(h,"voltage_probe",4,3)
        h.runAtTickTime(20) {
            expect(h,meter.voltage>4.9,"AND output was not high with both inputs high")
            left.enabled=false;node(h,2,3).changedTopology()
        }
        h.runAtTickTime(40) {
            expect(h,abs(meter.voltage)<.01,"AND output remained high after an input fell")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=100)
    fun wirelessChannelsRemainSeparate(h: GameTestHelper) {
        part(h,"signal_source",1)
        part(h,"wireless_signal_transmitter",2,rotation=2).channel="alpha"
        val rx=part(h,"wireless_signal_receiver",6);rx.channel="beta"
        val meter=part(h,"voltage_probe",7)
        h.runAtTickTime(20) {
            expect(h,meter.voltage<.01,"Wireless channel leaked into another channel")
            rx.channel="alpha";node(h,6).changedTopology()
        }
        h.runAtTickTime(40) {
            expect(h,meter.voltage>4.9,"Matching wireless channel did not transmit")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=200)
    fun capacitorChargesAndSurvivesTopologyRebuild(h: GameTestHelper) {
        part(h,"electrical_source",1)
        part(h,"creative_power_resistor",2,rotation=2)
        val cap=part(h,"creative_power_capacitor",3,rotation=2);cap.value=.01
        part(h,"ground_cable",4)
        h.runAtTickTime(20) {
            val v=cap.voltage-cap.voltageB
            expect(h,v in 25.0..39.0,"RC transient was $v V after one second")
        }
        h.runAtTickTime(120) { h.setBlock(1,2,2,Blocks.AIR) }
        h.runAtTickTime(160) {
            expect(h,cap.voltage-cap.voltageB>48.0,"Topology rebuild lost stored capacitor energy")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=100)
    fun batteryDischargesAndDataRoundTrips(h: GameTestHelper) {
        part(h,"ground_cable",0)
        val battery=part(h,"cost_oriented_battery",1)
        part(h,"creative_power_resistor",2,rotation=2).value=10.0
        part(h,"ground_cable",3)
        h.runAtTickTime(40) {
            expect(h,battery.charge<.5 && battery.charge>.49,"Battery charge did not track discharge: ${battery.charge}")
            val entity=node(h,1);val stored=entity.saveWithFullMetadata()
            val charge=battery.charge
            entity.load(stored);entity.changedTopology()
            expect(h,abs(entity.parts[0]!!.charge-charge)<1e-10,"Battery NBT lost stored charge")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=100)
    fun floatingSolarPanelsWorkInSeries(h: GameTestHelper) {
        h.level.setDayTime(6000)
        h.level.setWeatherParameters(6000,0,false,false)
        // GameTest may place templates below sea level in the generated test world.
        // Test the actual world adapter above the terrain, with a clear path to the sun.
        val testY=h.level.maxBuildHeight-h.absolutePos(BlockPos.ZERO).y-10
        part(h,"ground_cable",0,y=testY)
        val first=part(h,"small_solar_panel",1,y=testY)
        val second=part(h,"small_solar_panel",2,y=testY)
        for(x in 1..2) {
            val base=node(h,x,y=testY).blockPos
            for(y in base.y+1 until h.level.maxBuildHeight)h.level.setBlockAndUpdate(BlockPos(base.x,y,base.z),Blocks.AIR.defaultBlockState())
        }
        val load=part(h,"creative_power_resistor",3,rotation=2,y=testY);load.value=7.5
        part(h,"ground_cable",4,y=testY)
        h.runAtTickTime(40) {
            val pos=node(h,1,y=testY).blockPos
            val overhead=(pos.y+1 until h.level.maxBuildHeight).map { BlockPos(pos.x,it,pos.z) }.filter { !h.level.getBlockState(it).isAir }.take(8)
            val expected=2*14.75/(7.5+4*first.definition.resistance+2*load.resistance+2*PartDefinition.byId.getValue("ground_cable").resistance)
            expect(h,abs(abs(first.current)-expected)<.005 && abs(abs(second.current)-expected)<.005,"Original series-panel voltage/resistance limit failed: ${load.voltage-load.voltageB} V, ${first.current} A; expected $expected A, sky=${SolarExposure.sample(h.level,pos,false)}, overhead=$overhead")
            expect(h,abs(load.voltage-load.voltageB-expected*7.5)<.04,"Floating series terminals failed to add their voltage")
            val panel=node(h,2,y=testY)
            val saved=panel.saveWithFullMetadata();panel.load(saved);panel.changedTopology()
        }
        h.runAtTickTime(60) {
            expect(h,load.voltage-load.voltageB>29,"Loading panel state grounded its floating terminal")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=100)
    fun thirdLogicInputWorks(h: GameTestHelper) {
        part(h,"signal_source",2,3)
        part(h,"signal_source",3,4)
        val third=part(h,"signal_source",3,2);third.enabled=false
        part(h,"and_chip",3,3)
        val meter=part(h,"voltage_probe",4,3)
        h.runAtTickTime(20) {
            expect(h,meter.voltage<.01,"AND ignored its connected third input")
            third.enabled=true
        }
        h.runAtTickTime(40) {
            expect(h,meter.voltage>4.9,"Three-input AND failed with all inputs high")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=140)
    fun flipFlopRetainsStateAcrossReload(h: GameTestHelper) {
        val data=part(h,"signal_source",2,3)
        val clock=part(h,"signal_source",3,4);clock.enabled=false
        part(h,"d_flip_flop_chip",3,3)
        val meter=part(h,"voltage_probe",4,3)
        h.runAtTickTime(20) { expect(h,meter.voltage<.01,"Flip-flop changed without clock");clock.enabled=true }
        h.runAtTickTime(40) {
            expect(h,meter.voltage>4.9,"Flip-flop missed rising clock")
            data.enabled=false
            val entity=node(h,3,3);entity.load(entity.saveWithFullMetadata());entity.changedTopology()
        }
        h.runAtTickTime(60) { expect(h,meter.voltage>4.9,"Reload or changing data lost latched output");clock.enabled=false }
        h.runAtTickTime(80) { clock.enabled=true }
        h.runAtTickTime(100) { expect(h,meter.voltage<.01,"Next rising clock did not capture low data");h.succeed() }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=120)
    fun analogSampleAndHoldLatchesVoltage(h: GameTestHelper) {
        val data=part(h,"signal_source",2,3);data.value=2.0
        val clock=part(h,"signal_source",3,4);clock.enabled=false
        part(h,"sample_and_hold",3,3)
        val meter=part(h,"voltage_probe",4,3)
        h.runAtTickTime(20) { expect(h,meter.voltage<.01,"Sample-and-hold changed without a clock");clock.enabled=true }
        h.runAtTickTime(40) { expect(h,abs(meter.voltage-2)<.01,"Sample-and-hold sampled wrong input");data.value=4.0 }
        h.runAtTickTime(60) { expect(h,abs(meter.voltage-2)<.01,"Sample-and-hold did not hold its output");clock.enabled=false }
        h.runAtTickTime(80) { clock.enabled=true }
        h.runAtTickTime(100) { expect(h,abs(meter.voltage-4)<.01,"Sample-and-hold failed next sample");h.succeed() }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=100)
    fun surfacePlacementSelectionAndSupportLoss(h: GameTestHelper) {
        val player=h.makeMockPlayer();player.abilities.instabuild=true
        val base=BlockPos(2,1,3);val pos=h.absolutePos(base.above())
        h.setBlock(base,Blocks.STONE);h.setBlock(3,1,3,Blocks.STONE)
        player.setPos(pos.x+.5,pos.y.toDouble(),pos.z+2.0)
        fun place(id: String,clicked: net.minecraft.core.BlockPos,face: Direction,point: net.minecraft.world.phys.Vec3): net.minecraft.world.InteractionResult {
            val item=Content.partItems.getValue(id).get()
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack(item,8))
            return item.useOn(net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,
                net.minecraft.world.phys.BlockHitResult(point,face,clicked,false)))
        }
        val center=net.minecraft.world.phys.Vec3.atCenterOf(pos)
        expect(h,place("signal_cable",pos.below(),Direction.UP,center.add(0.0,-.5,0.0)).consumesAction(),"Floor placement failed")
        val first=h.level.getBlockEntity(pos) as FaceNodeEntity
        expect(h,first.parts[0]?.orientationVersion==2,"New placement did not use the modern orientation")
        expect(h,place("signal_cable",pos,Direction.EAST,center.add(.03,-.47,0.0)).consumesAction(),"Extending a mounted cable failed")
        val next=h.level.getBlockEntity(pos.east()) as? FaceNodeEntity
        expect(h,next?.parts?.get(0)!=null,"Extension lost its floor mounting face")
        h.level.setBlockAndUpdate(pos.north(),Blocks.STONE.defaultBlockState())
        expect(h,place("analog_vumeter",pos,Direction.SOUTH,center.add(0.0,0.0,-.49)).consumesAction(),"Adding a wall device failed")
        val wall=first.parts[Direction.NORTH.get3DDataValue()]
        expect(h,wall!=null,"Wall device was not stored in its mounting face")
        expect(h,first.partAt(center.add(0.0,0.0,-.49))===wall,"Selection returned the floor part for a wall hit")
        expect(h,place("data_logger",pos,Direction.SOUTH,center)==net.minecraft.world.InteractionResult.FAIL,"Floor-only logger accepted a wall")
        h.level.removeBlock(pos.north(),false)
        h.runAtTickTime(5) {
            expect(h,first.parts[Direction.NORTH.get3DDataValue()]==null,"Unsupported wall device stayed attached")
            expect(h,first.parts[0]!=null,"Removing wall support also removed the floor cable")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=100)
    fun machineVisualStateFollowsPowerAndPause(h: GameTestHelper) {
        part(h,"electrical_source",1).value=48.0
        val state=part(h,"48v_macerator",2)
        val machine=node(h,2);machine.inventory.setStackInSlot(0,ItemStack(Items.RAW_COPPER,2))
        var paused=0.0
        h.runAtTickTime(20) {
            expect(h,state.active && state.powerFactor>.9 && state.processFraction>0,"Machine visuals never became active")
            state.enabled=false;paused=state.progress
        }
        h.runAtTickTime(40) {
            expect(h,!state.active && state.powerFactor==0.0,"Paused machine continued its active animation")
            expect(h,abs(state.progress-paused)<1e-6,"Paused machine continued processing")
            state.enabled=true
        }
        h.runAtTickTime(60) {
            expect(h,state.active && state.progress>paused,"Machine did not resume processing and animation")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=100)
    fun signalControlsWorkByDirectInteraction(h: GameTestHelper) {
        val state=part(h,"signal_switch",2)
        val meter=part(h,"voltage_probe",3)
        val player=h.makeMockPlayer();val entity=node(h,2)
        val center=net.minecraft.world.phys.Vec3.atCenterOf(entity.blockPos)
        player.setPos(center.x,center.y+1,center.z+1)
        fun click() {
            val hit=net.minecraft.world.phys.BlockHitResult(center.add(0.0,-.4,0.0),Direction.UP,entity.blockPos,false)
            entity.blockState.use(h.level,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit)
        }
        h.runAtTickTime(20) { expect(h,!state.enabled && meter.voltage<.01,"Original signal switch did not start off");click() }
        h.runAtTickTime(40) { expect(h,state.enabled && meter.voltage>4.9,"Right-click did not switch on");click() }
        h.runAtTickTime(60) { expect(h,!state.enabled && meter.voltage<.01,"Right-click did not switch off");h.succeed() }
    }

}
