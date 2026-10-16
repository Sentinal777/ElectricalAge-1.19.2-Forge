package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
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
object SensorGameTests {
    private fun device(h: GameTestHelper,id: String,x: Int,z: Int=2,rotation: Int=0)=InfrastructureGameTests.create(h,id,x,z,rotation)
    private fun cable(n: FaceNodeEntity)=PartInventory.get(n,n.parts[0]!!).setStackInSlot(0,ItemStack(Content.partItems.getValue("low_voltage_cable").get()))
    private fun check(h: GameTestHelper,value: Boolean,message: String) { if(!value)h.fail(message) }
    @JvmStatic @GameTest(template="empty",timeoutTicks=90)
    fun logicOscillatorRetainsTheOriginalDeviceSamplingRate(h: GameTestHelper) {
        device(h,"signal_source",1)
        val oscillator=device(h,"oscillator_chip",2).parts[0]!!
        var previous=false;val rising=mutableListOf<Long>()
        for(t in 20L..69L)h.runAtTickTime(t) {
            val high=oscillator.voltage>2.5
            if(high && !previous)rising.add(t)
            previous=high
        }
        h.runAtTickTime(75) {
            // Original caller passes a normalized input into a 0..5 hertz table: 1 -> 4.08 Hz,
            // then the reset-on-threshold ramp sampled at 20 Hz switches every three ticks.
            check(h,rising.size in 8..9 && rising.zipWithNext().all { (a,b) -> b-a==6L },"Oscillator diverged from the source's normalized-input timing: rising ticks $rising")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=90)
    fun voltageProbeHasIndependentScaledOutputAndRealCableInventory(h: GameTestHelper) {
        device(h,"electrical_source",1)
        val probe=device(h,"voltage_probe",2);cable(probe)
        val reader=device(h,"voltage_probe",3);cable(reader)
        h.runAtTickTime(20) {
            check(h,abs(probe.parts[0]!!.voltage-50)<.01 && abs(reader.parts[0]!!.voltage-5)<.01,"Probe shorted its measurement/output or measured the wrong circuit")
            check(h,PartInventory.get(probe,probe.parts[0]!!).insertItem(0,ItemStack(Items.DIAMOND),true).count==1,"Probe accepted unrelated inventory input")
            val saved=probe.saveWithoutMetadata();probe.load(saved)
            check(h,PartInventory.get(probe,probe.parts[0]!!).getStackInSlot(0).count==1,"Probe cable did not survive NBT restore")
            probe.parts[0]!!.expression="0,100";probe.changedTopology()
        }
        h.runAtTickTime(45) {
            check(h,abs(reader.parts[0]!!.voltage-2.5)<.01,"Probe range did not scale the measured voltage")
            val extracted=PartInventory.get(probe,probe.parts[0]!!).extractItem(0,1,false)
            check(h,extracted.count==1,"Probe cable was lost or duplicated on extraction")
        }
        h.runAtTickTime(65) { check(h,abs(reader.parts[0]!!.voltage)<1e-6,"A probe without its cable still measured a disconnected source");h.succeed() }
    }
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=100)
    fun electricalProbeMeasuresCurrentPowerAndDirection(h: GameTestHelper) {
        device(h,"electrical_source",2,5);device(h,"low_voltage_cable",2,4)
        val probe=device(h,"electrical_probe",2,3);cable(probe)
        val p=probe.parts[0]!!;p.value=1.0;p.expression="0,1"
        device(h,"creative_power_resistor",2,2,3);device(h,"ground_cable",2,1)
        val reader=device(h,"voltage_probe",3,3);cable(reader)
        h.runAtTickTime(20) {
            check(h,abs(p.current-.5)<.001 && abs(reader.parts[0]!!.voltage-2.5)<.01,"Inline current probe did not report the analytical circuit")
            p.channel="reverse";p.expression="-1,1";probe.changedTopology()
        }
        h.runAtTickTime(45) {
            check(h,abs(reader.parts[0]!!.voltage-1.25)<.01,"Probe direction was ignored")
            p.value=0.0;p.channel="both";p.expression="0,50";probe.changedTopology()
        }
        h.runAtTickTime(75) {
            check(h,abs(reader.parts[0]!!.voltage-2.5)<.03 && p.voltage*p.current in 24.8..25.1,"Power probe did not measure real consumed watts");h.succeed()
        }
    }
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=80)
    fun signalOutputCannotSupplyAnUnlimitedShortCircuit(h: GameTestHelper) {
        val source=device(h,"signal_trimmer",2,4,rotation=3).parts[0]!!.apply { value=5.0 }
        val probe=device(h,"electrical_probe",2,3);cable(probe)
        device(h,"ground_cable",2,2)
        h.runAtTickTime(20) {
            check(h,probe.parts[0]!!.current in .099.. .1001 && source.voltage<.01,"Signal source bypassed the original 0.1 A output stage")
            h.setBlock(2,2,2,Blocks.AIR)
        }
        h.runAtTickTime(45) { check(h,source.voltage>4.99 && abs(probe.parts[0]!!.current)<1e-6,"Signal output did not recover after removing the short");h.succeed() }
    }
}
