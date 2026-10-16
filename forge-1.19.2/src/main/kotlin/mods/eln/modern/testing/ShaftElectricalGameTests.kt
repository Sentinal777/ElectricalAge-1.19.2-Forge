package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object ShaftElectricalGameTests {
    private fun device(h: GameTestHelper,id: String,x: Int=3,z: Int=3)=TurbineGameTests.create(h,id,x,z)
    private fun prime(nodes: List<FaceNodeEntity>,joules: Double) {
        for(node in nodes)node.parts[0]!!.shaftGroup="electrical-shaft-priming-fixture"
        nodes.first().parts[0]!!.shaftEnergy=joules;nodes.first().changedTopology()
    }
    @JvmStatic @GameTest(batch="shaft_electrical",template="grid_empty",timeoutTicks=130)
    fun originalMotorConsumesRealElectricityAndRetainsItsNinetyNinePercentCoupling(h: GameTestHelper) {
        val motor=device(h,"shaft_motor");val wheel=device(h,"flywheel",3,4)
        device(h,"electrical_source",4).parts[0]!!.value=3200.0
        h.runAtTickTime(65) {
            val p=motor.parts[0]!!;val input=p.thermalState.getDouble("shaftElectricalInputJ");val work=p.thermalState.getDouble("shaftProducedJ")
            if(input<=1000 || p.shaftSpeed<=20 || wheel.parts[0]!!.shaftEnergy<=0 || abs(work/input-.99)>1e-10 || p.shaftEnergy+wheel.parts[0]!!.shaftEnergy>work+1e-6)h.fail("Original motor controller did not convert real finite electrical input into shared shaft work: ${p.save()}")
            if(p.thermalState.getDouble("shaftHeatJ")<=0 || p.thermalState.getDouble("delta")<=0 || p.current<=0)h.fail("Motor winding/efficiency loss did not heat its actual thermal load")
            val saved=motor.saveWithoutMetadata();val emf=p.gridState.getDouble("shaftEmf");motor.load(saved);motor.changedTopology()
            if(motor.parts[0]!!.gridState.getDouble("shaftEmf")!=emf)h.fail("Motor lost its saved shaft EMF state")
        }
        h.runAtTickTime(100) { if(motor.parts[0]!!.shaftSpeed<=0 || motor.parts[0]!!.tripped)h.fail("Loaded motor failed its saved original controller");h.succeed() }
    }
    @JvmStatic @GameTest(batch="shaft_electrical",template="grid_empty",timeoutTicks=130)
    fun originalMotorCanRegenerateWithItsCalibratedPoorGeneratingEfficiency(h: GameTestHelper) {
        val motor=device(h,"shaft_motor");val wheel=device(h,"flywheel",3,4);prime(listOf(motor,wheel),55000.0)
        val load=device(h,"creative_power_resistor",4);load.parts[0]!!.apply { rotation=2;value=100000.0 }
        device(h,"ground_cable",5)
        h.runAtTickTime(80) {
            val p=motor.parts[0]!!;val output=p.thermalState.getDouble("shaftElectricalOutputJ");val used=p.thermalState.getDouble("shaftConsumedJ")
            if(output<=20 || load.parts[0]!!.energy<=20 || abs(used/output-9.9)>1e-8 || p.current>=0)h.fail("Shaft motor lost its original inefficient regeneration: ${p.save()}")
            if(used+p.shaftEnergy+wheel.parts[0]!!.shaftEnergy>55000+1e-6 || load.parts[0]!!.energy>output+1e-6)h.fail("Regenerating motor duplicated shaft or electrical work")
            p.enabled=false
        }
        h.runAtTickTime(105) { if(motor.parts[0]!!.active)h.fail("Disabled motor continued to exchange shaft work");h.succeed() }
    }
    @JvmStatic @GameTest(batch="shaft_electrical",template="grid_empty",timeoutTicks=110)
    fun originalGeneratorDeliversItsVoltageCurveWithConservedNinetyFivePercentEfficiency(h: GameTestHelper) {
        val generator=device(h,"generator");val wheel=device(h,"flywheel",3,4);prime(listOf(generator,wheel),55000.0)
        val load=device(h,"creative_power_resistor",4);load.parts[0]!!.apply { rotation=2;value=2560.0 }
        device(h,"ground_cable",5)
        h.runAtTickTime(80) {
            val p=generator.parts[0]!!;val output=p.thermalState.getDouble("shaftElectricalOutputJ");val used=p.thermalState.getDouble("shaftConsumedJ")
            if(output<=500 || load.parts[0]!!.energy<=500 || p.voltage<1000 || abs(output/used-.95)>1e-10 || p.current<=0)h.fail("Generator failed its original 3200V controller and calibrated efficiency: ${p.save()}")
            if(used+p.shaftEnergy+wheel.parts[0]!!.shaftEnergy>55000+1e-6 || load.parts[0]!!.energy>output+1e-6 || p.thermalState.getDouble("shaftHeatJ")<=0)h.fail("Generator created work or discarded its physical heat losses")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="shaft_electrical",template="grid_empty",timeoutTicks=110)
    fun originalGeneratorCanAlsoActAsAnInefficientMotor(h: GameTestHelper) {
        val generator=device(h,"generator");val wheel=device(h,"flywheel",3,4)
        device(h,"electrical_source",4).parts[0]!!.apply { value=3200.0;resistance=10.0 }
        h.runAtTickTime(75) {
            val p=generator.parts[0]!!;val input=p.thermalState.getDouble("shaftElectricalInputJ");val work=p.thermalState.getDouble("shaftProducedJ")
            if(input<=100 || work<=100 || abs(work/input-.7125)>1e-10 || p.shaftSpeed<=5 || p.current>=0)h.fail("Generator lost its original inefficient reverse motoring behavior: ${p.save()}")
            if(p.shaftEnergy+wheel.parts[0]!!.shaftEnergy>work+1e-6 || p.thermalState.getDouble("shaftHeatJ")<=0)h.fail("Reverse motoring created energy or removed its heat loss")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="shaft_electrical",template="grid_empty",timeoutTicks=130)
    fun twoCoupledGeneratorsCannotOverdrawTheirSmallSharedFlywheel(h: GameTestHelper) {
        val a=device(h,"generator");val wheel=device(h,"flywheel",3,4);val b=device(h,"generator",3,5)
        prime(listOf(a,wheel,b),60.0)
        for(z in 3..5)device(h,"high_voltage_cable",4,z)
        val load=device(h,"creative_power_resistor",5,4);load.parts[0]!!.apply { rotation=2;value=.01 }
        device(h,"ground_cable",6,4)
        h.runAtTickTime(90) {
            val parts=listOf(a,wheel,b).map { it.parts[0]!! }
            val output=listOf(a,b).sumOf { it.parts[0]!!.thermalState.getDouble("shaftElectricalOutputJ") }
            val used=listOf(a,b).sumOf { it.parts[0]!!.thermalState.getDouble("shaftConsumedJ") }
            if(output<=0 || used+parts.sumOf { it.shaftEnergy }>60+1e-6 || abs(output-used*.95)>1e-6 || load.parts[0]!!.energy>output+1e-6 || parts.any { !it.shaftEnergy.isFinite() || it.shaftEnergy<0 })h.fail("Coupled electrical controllers over-drew or duplicated their real shared flywheel")
            h.succeed()
        }
    }
}
