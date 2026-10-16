package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs
import kotlin.math.floor

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object BatteryVariantGameTests {
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=125)
    fun allSevenOriginalBatteriesFollowTheSourceChargeCurveDriveRealLoadsAndRetainFiniteChargeOnReload(h: GameTestHelper) {
        data class Branch(val node: FaceNodeEntity,val load: PartState,val initial: Double,var saved: Double=0.0)
        val branches=listOf("cost_oriented_battery","capacity_oriented_battery","voltage_oriented_battery","current_oriented_battery","life_oriented_battery","single_use_battery","experimental_battery").mapIndexed { i,id ->
            val z=2+i*2
            InfrastructureGameTests.create(h,"ground_cable",2,z)
            val battery=InfrastructureGameTests.create(h,id,3,z)
            val load=InfrastructureGameTests.create(h,"creative_power_resistor",4,z,2).parts[0]!!
            load.value=battery.parts[0]!!.definition.nominalVoltage*battery.parts[0]!!.definition.nominalVoltage/50.0
            InfrastructureGameTests.create(h,"ground_cable",5,z)
            Branch(battery,load,battery.parts[0]!!.charge)
        }
        // Independent literal 1.24.6 reference: FunctionTable([0,.9,1,1.025,1.04,1.05,2],6/5).
        fun voltageFactor(charge: Double): Double {
            val curve=doubleArrayOf(0.0,.9,1.0,1.025,1.04,1.05,2.0)
            val at=charge.coerceIn(0.0,1.2)*5;val i=floor(at).toInt().coerceIn(0,5)
            return curve[i]+(curve[i+1]-curve[i])*(at-i)
        }
        h.runAtTickTime(40) {
            for(b in branches) {
                val p=b.node.parts[0]!!
                if(p.charge>=b.initial || p.charge<=0 || b.load.energy<20 || abs(b.load.current)<.001)h.fail("Original battery failed to spend finite charge into real load: ${p.definition.id}, ${p.charge}, ${b.load.energy}")
                val expected=voltageFactor(p.charge)*p.definition.nominalVoltage
                if(abs(p.voltage-p.voltageB-expected)>.05)h.fail("Original battery charge curve failed: ${p.definition.id}, ${p.voltage-p.voltageB}, expected $expected")
                p.enabled=false;b.node.changedTopology()
            }
        }
        h.runAtTickTime(60) {
            for(b in branches) {
                val p=b.node.parts[0]!!;b.saved=p.charge
                val saved=b.node.saveWithoutMetadata();b.node.load(saved);b.node.changedTopology()
                if(b.node.parts[0]!!.charge!=b.saved)h.fail("Actual battery NBT reload changed charge: ${p.definition.id}")
            }
        }
        h.runAtTickTime(85) {
            for(b in branches) {
                val p=b.node.parts[0]!!
                if(abs(p.charge-b.saved)>1e-10 || abs(b.load.current)>1e-6)h.fail("Stopped reloaded battery continued delivering energy: ${p.definition.id}")
                p.enabled=true;b.node.changedTopology()
            }
        }
        h.runAtTickTime(110) { for(b in branches)if(b.node.parts[0]!!.charge>=b.saved || abs(b.load.current)<.001)h.fail("Re-enabled original battery did not resume spending saved charge");h.succeed() }
    }
}
