package mods.eln.modern.testing

import mods.eln.modern.*
import mods.eln.modern.thermal.Climate
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln")
@PrefixGameTestTemplate(false)
object ThermalGameTests {
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=140,batch="original_ambient_sensor")
    fun ambientClimateSensorRetainsOriginalSlowFilterAndSamplePhaseThroughUnrelatedCircuitRebuilds(h: GameTestHelper) {
        val sensor=device(h,"thermometer_sensor",3,3)
        var p=sensor.parts[0]!!
        val ambient=Climate.sample(h.level,sensor.blockPos).temperatureCelsius
        p.expression="${ambient-45},${ambient+45}";sensor.changedTopology()
        val unrelated=device(h,"creative_cable",11,11)
        for(tick in 15L..65L)h.runAtTickTime(tick) { unrelated.changedTopology() }
        var before=0.0
        h.runAtTickTime(80) {
            before=p.voltage
            if(before !in .55.. .85)h.fail("Original ambient-sensor RC/sample cadence was reset by unrelated wiring or became instantaneous: $before V")
            val phase=p.thermalState.getDouble("ambientTimer");val filter=p.thermalState.getFloat("ambientFilter")
            val saved=sensor.saveWithoutMetadata();sensor.load(saved);p=sensor.parts[0]!!;sensor.changedTopology()
            if(p.thermalState.getDouble("ambientTimer")!=phase || p.thermalState.getFloat("ambientFilter")!=filter)h.fail("Actual ambient-sensor reload lost filter history or sample phase")
        }
        h.runAtTickTime(120) { if(p.voltage<=before+.15 || p.voltage>=1.2)h.fail("Reload restarted or accelerated the original3s RC advanced0.05s per0.2s sample: $before -> ${p.voltage}");h.succeed() }
    }
    private fun device(h: GameTestHelper,id: String,x: Int,z: Int,rotation: Int=0)=InfrastructureGameTests.create(h,id,x,z,rotation)
    private fun check(h: GameTestHelper,value: Boolean,message: String) { if(!value)h.fail(message) }
    private fun cable(node: FaceNodeEntity,id: String)=PartInventory.get(node,node.parts[0]!!).setStackInSlot(0,ItemStack(Content.partItems.getValue(id).get()))
    private fun warm(node: FaceNodeEntity,delta: Double) { node.parts[0]!!.thermalState.putDouble("delta",delta);node.changedTopology() }

    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=100)
    fun heatDrivenTurbineConservesStoredHeatAndGeneratedElectricalWork(h: GameTestHelper) {
        val turbine=device(h,"50v_turbine",2,3)
        val original=PartDefinition.byId.getValue("50v_turbine")
        // Insulate both reservoirs for this energy accounting test, so room/ambient loss does
        // not hide any accidental creation of heat while the turbine performs electrical work.
        val insulated=original.copy(thermal=original.thermal!!.copy(rp=Double.POSITIVE_INFINITY))
        val p=PartState(insulated,net.minecraft.core.Direction.DOWN);p.thermalState.putDouble("delta",250.0)
        turbine.parts[0]=p;turbine.changedTopology()
        val load=device(h,"creative_power_resistor",3,3,2).parts[0]!!
        device(h,"ground_cable",4,3)
        h.runAtTickTime(5) {
            check(h,load.current>.5 && load.voltage>50,"Stored heat failed to drive the real electrical load: ${load.voltage} V / ${load.current} A")
            val target=mods.eln.misc.FunctionTable(doubleArrayOf(0.0,.1,.85,1.0,1.1,1.15,1.18,1.19,1.25),400.0).getValue(p.thermalState.getDouble("delta")-p.thermalState.getDouble("coldDelta"))*50
            val internal=p.voltage+p.current*original.resistance*30
            check(h,abs(internal-(target-p.powerFactor*original.power/original.heatTurbine!!.wattsPerVolt))<.15,"Turbine electrical regulator diverged from its original droop curve")
        }
        h.runAtTickTime(60) {
            val heat=25*(p.thermalState.getDouble("delta")+p.thermalState.getDouble("coldDelta"))
            check(h,p.energy>50 && load.energy>50,"The turbine did not deliver actual electrical work")
            check(h,abs(heat+p.energy-p.thermalState.getDouble("internalLossJ")-6250)<1e-6,"Thermal reservoirs plus generated work failed energy conservation: $heat J heat / ${p.energy} J work")
            check(h,p.thermalState.getDouble("delta")<250 && p.thermalState.getDouble("coldDelta")>0,"Turbine did not cool its hot port and warm its cold port")
            h.succeed()
        }
    }

    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=75)
    fun heatDrivenTurbineWithNoTemperatureDifferenceProducesNoEnergy(h: GameTestHelper) {
        val turbine=device(h,"200v_turbine",2,3)
        val load=device(h,"creative_power_resistor",3,3,2).parts[0]!!;device(h,"ground_cable",4,3)
        h.runAtTickTime(50) {
            check(h,abs(load.current)<1e-8 && turbine.parts[0]!!.energy<1e-8,"An isothermal turbine created energy")
            h.succeed()
        }
    }

    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=90)
    fun reversedThermalDifferenceProducesSignedVoltageWhileConservingHeat(h: GameTestHelper) {
        val turbine=device(h,"50v_turbine",2,3)
        val d=PartDefinition.byId.getValue("50v_turbine")
        val p=PartState(d.copy(thermal=d.thermal!!.copy(rp=Double.POSITIVE_INFINITY)),net.minecraft.core.Direction.DOWN)
        p.thermalState.putDouble("delta",-50.0);turbine.parts[0]=p;turbine.changedTopology()
        val load=device(h,"creative_power_resistor",3,3,2).parts[0]!!;device(h,"ground_cable",4,3)
        h.runAtTickTime(60) {
            check(h,load.voltage< -4 && p.energy>.5,"The original signed temperature/voltage curve failed to drive a reversed load")
            val heat=25*(p.thermalState.getDouble("delta")+p.thermalState.getDouble("coldDelta"))
            check(h,abs(heat+p.energy-p.thermalState.getDouble("internalLossJ")+1250)<1e-6,"Reversed generation created heat or electrical energy")
            check(h,p.thermalState.getDouble("delta")> -50 && p.thermalState.getDouble("coldDelta")<0,"Reversed generation moved heat from the colder reservoir")
            h.succeed()
        }
    }

    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=260)
    fun coalFurnaceThermalDistributionTurbineAndCoolingDriveARealElectricalLoad(h: GameTestHelper) {
        val heater=device(h,"stone_heat_furnace",2,5,1)
        val furnace=heater.parts[0]!!;furnace.enabled=true;furnace.value=.5
        PartInventory.get(heater,furnace).setStackInSlot(0,ItemStack(Items.COAL))
        val hotPipe=device(h,"copper_thermal_cable",2,4)
        val turbine=device(h,"50v_turbine",2,3)
        device(h,"copper_thermal_cable",2,2)
        val cooler=device(h,"small_passive_thermal_dissipator",2,1)
        val load=device(h,"creative_power_resistor",3,3,2).parts[0]!!;device(h,"ground_cable",4,3)
        h.runAtTickTime(220) {
            val p=turbine.parts[0]!!
            check(h,load.energy>25 && load.current>.1 && p.energy>25,"Coal heat failed to power a real electrical load: ${load.energy} J / ${load.current} A")
            check(h,hotPipe.parts[0]!!.thermalState.getDouble("delta")>75,"Thermal distribution failed to carry furnace heat")
            check(h,p.thermalState.getDouble("coldDelta")>0 && cooler.parts[0]!!.thermalState.getDouble("delta")>0,"Turbine's cold port failed to reject heat into its cooling network")
            check(h,abs(furnace.energy+furnace.fuelEnergy+furnace.thermalState.getDouble("fuelBuffer")-80000)<1e-5,"Heat-to-electric operation duplicated furnace fuel")
            h.succeed()
        }
    }

    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=135)
    fun enclosedRoomAbsorbsMachineHeatAndOpenBoundaryDoorsIncreaseCooling(h: GameTestHelper) {
        for(x in 0..6)for(y in 1..5)for(z in 0..6)if(x==0 || x==6 || y==1 || y==5 || z==0 || z==6)
            h.setBlock(x,y,z,net.minecraft.world.level.block.Blocks.STONE)
        val cooler=device(h,"small_passive_thermal_dissipator",2,2);warm(cooler,100.0)
        val bottom=BlockPos(0,2,3);val door=net.minecraft.world.level.block.Blocks.OAK_DOOR
        h.setBlock(bottom,door.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER))
        h.setBlock(bottom.above(),door.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER))
        val seed=h.absolutePos(BlockPos(4,2,4))
        val room=mods.eln.modern.thermal.RoomThermal.scan(h.level,seed,listOf(cooler.blockPos)) ?: throw IllegalStateException("Enclosed room was not detected")
        check(h,room.cells.size==74,"Room volume did not exclude the machine: ${room.cells.size}")
        h.runAtTickTime(50) {
            check(h,room.delta>.01,"Machine heat did not enter the enclosed room air")
            val saved=mods.eln.modern.thermal.RoomThermal.data(h.level).save(net.minecraft.nbt.CompoundTag())
            val loaded=mods.eln.modern.thermal.RoomThermalData.load(saved)
            check(h,abs(loaded.at(seed)!!.delta-room.delta)<1e-12,"Saved room temperature was lost")
            h.setBlock(bottom,h.getBlockState(bottom).setValue(net.minecraft.world.level.block.DoorBlock.OPEN,true))
            h.setBlock(bottom.above(),h.getBlockState(bottom.above()).setValue(net.minecraft.world.level.block.DoorBlock.OPEN,true))
        }
        h.runAtTickTime(80) {
            check(h,room.openDoors==1,"Room door scanner failed to count an open door once")
            val hole=BlockPos(3,5,3)
            // This fixture must open into exterior air, even where the generated terrain covers its roof.
            for(dy in 0..mods.eln.config.ForgePortConfig.roomMaxSpan.get()+1)h.setBlock(hole.above(dy),net.minecraft.world.level.block.Blocks.AIR)
            mods.eln.modern.thermal.RoomThermal.data(h.level).invalidate(h.absolutePos(hole))
            check(h,mods.eln.modern.thermal.RoomThermal.scan(h.level,seed,listOf(cooler.blockPos))==null,"Open room was falsely treated as enclosed")
            check(h,mods.eln.modern.thermal.RoomThermal.data(h.level).at(seed)==null,"Breaking the ceiling left a stale heat reservoir")
            h.succeed()
        }
    }

    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=100)
    fun furnaceRegulatorsInsulationAndExternalSignalsControlActualCombustion(h: GameTestHelper) {
        val heater=device(h,"stone_heat_furnace",2,2)
        val p=heater.parts[0]!!;p.enabled=true;p.expression="150";warm(heater,160.0)
        val inventory=PartInventory.get(heater,p)
        inventory.setStackInSlot(0,ItemStack(Items.COAL,2))
        inventory.setStackInSlot(1,ItemStack(Content.materialItems.getValue("on_off_regulator_1_percent").get()))
        inventory.setStackInSlot(2,ItemStack(Content.materialItems.getValue("thermal_insulation").get()))
        inventory.setStackInSlot(3,ItemStack(Content.materialItems.getValue("combustion_chamber").get(),8))
        val signal=device(h,"signal_source",2,3);signal.parts[0]!!.value=0.0
        h.runAtTickTime(5) {
            check(h,abs(p.thermalState.getDouble("gain")-.1)<1e-9,"Installed regulator failed to reduce combustion above its target")
            check(h,abs(p.thermalState.getDouble("rp")-.39)<1e-9,"Thermal insulation did not halve heat leakage")
            check(h,inventory.getSlotLimit(3)==8 && inventory.getSlotLimit(1)==1,"Furnace upgrade slot limits changed")
            p.expression="400";heater.changedTopology()
        }
        h.runAtTickTime(15) {
            check(h,abs(p.thermalState.getDouble("gain")-1.0)<1e-9,"Temperature target did not affect combustion: gain ${p.thermalState.getDouble("gain")} / delta ${p.thermalState.getDouble("delta")}")
            check(h,p.powerFactor>1.5,"Eight installed combustion chambers failed to increase furnace power")
            p.channel="external";heater.changedTopology()
        }
        h.runAtTickTime(25) {
            check(h,!p.enabled && abs(p.thermalState.getDouble("gain")-.1)<1e-9,"Low external signal failed to stop new fuel intake")
            signal.parts[0]!!.value=5.0;signal.changedTopology()
        }
        h.runAtTickTime(40) {
            check(h,p.enabled && p.thermalState.getDouble("gain")>.99,"External signal failed to drive actual furnace combustion")
            h.succeed()
        }
    }

    @JvmStatic @GameTest(template="empty",timeoutTicks=45)
    fun thermalInsulationAboveItsOriginalRatingIsConsumed(h: GameTestHelper) {
        val heater=device(h,"stone_heat_furnace",2,2);warm(heater,550.0)
        val p=heater.parts[0]!!;val inventory=PartInventory.get(heater,p)
        inventory.setStackInSlot(2,ItemStack(Content.materialItems.getValue("thermal_insulation").get()))
        h.runAtTickTime(10) {
            check(h,inventory.getStackInSlot(2).isEmpty,"Thermal insulation survived above its original 500 °C delta rating")
            check(h,abs(p.thermalState.getDouble("rp")-.195)<1e-9,"Burned insulation continued reducing heat leakage")
            h.succeed()
        }
    }

    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=210)
    fun fuelHeatFurnacePowersThermalCableProbeAndCoolingWithoutCreatingFuel(h: GameTestHelper) {
        val heater=device(h,"stone_heat_furnace",2,5,3)
        val inventory=PartInventory.get(heater,heater.parts[0]!!)
        val automation=heater.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow { IllegalStateException("Missing heat furnace fuel automation") }
        check(h,automation.insertItem(0,ItemStack(Items.DIAMOND),false).count==1,"Heat furnace accepted nonfuel")
        check(h,automation.insertItem(0,ItemStack(Items.COAL),false).isEmpty,"Fuel automation rejected coal")
        check(h,automation.extractItem(0,1,false).isEmpty,"Heat furnace exposed extractable fuel")
        heater.parts[0]!!.enabled=true
        val pipe=device(h,"copper_thermal_cable",2,6)
        val probe=device(h,"thermal_probe",2,7);cable(probe,"copper_thermal_cable")
        probe.parts[0]!!.expression="0,200"
        device(h,"small_passive_thermal_dissipator",2,8)
        val reader=device(h,"voltage_probe",3,7);cable(reader,"signal_cable")
        h.runAtTickTime(100) {
            val p=heater.parts[0]!!;val delta=pipe.parts[0]!!.thermalState.getDouble("delta")
            check(h,inventory.getStackInSlot(0).isEmpty && p.fuelEnergy>0,"The furnace did not consume its finite coal fuel")
            check(h,p.energy>10000 && delta>20,"The furnace failed to transfer real heat into the pipe: ${p.energy} J / $delta K")
            check(h,abs(p.energy+p.fuelEnergy+p.thermalState.getDouble("fuelBuffer")-80000)<1e-5,"The furnace duplicated or discarded combustion energy")
            val measured=probe.parts[0]!!.temperature/200*5
            check(h,abs(reader.parts[0]!!.voltage-measured)<.05,"Thermal probe output did not track actual temperature: ${reader.parts[0]!!.voltage} vs $measured")
            val saved=heater.saveWithFullMetadata();heater.setRemoved();heater.load(saved);heater.clearRemoved();heater.onLoad()
        }
        h.runAtTickTime(170) {
            val p=heater.parts[0]!!
            check(h,abs(p.energy+p.fuelEnergy+p.thermalState.getDouble("fuelBuffer")-80000)<1e-5,"Reloading the furnace changed its finite fuel energy")
            check(h,PartInventory.get(heater,p).getStackInSlot(0).isEmpty,"Reload restored already-burned coal")
            h.succeed()
        }
    }

    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=95)
    fun activeCoolingConsumesRealElectricalPowerAndReturnsToPassiveCoolingWhenDisconnected(h: GameTestHelper) {
        val source=device(h,"electrical_source",1,2)
        val cooler=device(h,"small_active_thermal_dissipator",2,2)
        val pipe=device(h,"copper_thermal_cable",2,3)
        warm(cooler,80.0);warm(pipe,80.0)
        h.runAtTickTime(25) {
            val p=cooler.parts[0]!!
            check(h,abs(p.voltage*p.current-50)<.1,"Active cooler did not consume the original 50 W rating")
            check(h,abs(p.thermalState.getDouble("rp")-.03)<.0001,"Powered cooling did not use the original thermal resistance")
            check(h,p.thermalState.getDouble("delta")<75,"Powered cooler did not remove heat")
            source.parts[0]!!.enabled=false;source.changedTopology()
        }
        h.runAtTickTime(65) {
            val p=cooler.parts[0]!!
            check(h,abs(p.current)<1e-6 && abs(p.thermalState.getDouble("rp")-.15)<1e-6,"An unpowered fan continued active cooling")
            h.succeed()
        }
    }

    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=150)
    fun temperatureProbeMeasuresElectricalCableHeatThroughItsOriginalUniversalInput(h: GameTestHelper) {
        device(h,"electrical_source",1,2)
        val wire=device(h,"low_voltage_cable",2,2);warm(wire,80.0)
        val load=device(h,"creative_power_resistor",2,3,3);warm(load,80.0)
        device(h,"ground_cable",2,4)
        val probe=device(h,"temperature_probe",3,2);cable(probe,"low_voltage_cable")
        probe.parts[0]!!.expression="0,200"
        val reader=device(h,"voltage_probe",4,2);cable(reader,"signal_cable")
        h.runAtTickTime(45) {
            val p=probe.parts[0]!!
            check(h,abs(load.parts[0]!!.current-50/100.031001)<.001,"The temperature probe changed its measured electrical circuit")
            val delta=p.thermalState.getDouble("delta");val wireDelta=wire.parts[0]!!.thermalState.getDouble("delta")
            check(h,delta>60 && delta<wireDelta,"The probe did not show the original finite thermal response: $delta vs $wireDelta K above local ambient")
            check(h,abs(reader.parts[0]!!.voltage-p.temperature/200*5)<.02,"Temperature output did not reach the signal circuit")
            check(h,PartInventory.get(probe,p).insertItem(0,ItemStack(Items.DIAMOND),true).count==1,"Probe accepted an invalid sensor cable")
        }
        h.runAtTickTime(120) {
            // Two installed cable capacities and their serial thermal resistances give a finite
            // response time. The continuing cooling of the source also produces a small lag.
            // The source ThermalSensorProcess adds the sensor's own local ambient to this
            // relative thermal state. Adjacent blocks can have different biome profiles;
            // comparing their displayed absolute temperatures is not a settling test.
            val delta=probe.parts[0]!!.thermalState.getDouble("delta");val wireDelta=wire.parts[0]!!.thermalState.getDouble("delta")
            check(h,abs(delta-wireDelta)<.6,"The probe failed to settle toward the connected cable thermal state: $delta vs $wireDelta K above local ambient")
            h.succeed()
        }
    }
}
