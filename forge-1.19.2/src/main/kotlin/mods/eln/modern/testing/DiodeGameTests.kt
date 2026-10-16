package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object DiodeGameTests {
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=150)
    fun allOriginalDiodesUseSourceForwardResistanceProperCableMasksRealHeatAndReverseBlocking(h: GameTestHelper) {
        data class Branch(val node: FaceNodeEntity,val diode: PartState,val load: PartState,val expected: Double)
        val branches=listOf("10a_diode","25a_diode","signal_diode").mapIndexed { i,id ->
            val z=3+i*4
            val source=InfrastructureGameTests.create(h,if(i==2)"signal_source" else "electrical_source",2,z).parts[0]!!
            val n=InfrastructureGameTests.create(h,id,3,z,2);val d=n.parts[0]!!
            // An inline electrical probe bridges a signal terminal to a real power load.
            val probe=InfrastructureGameTests.create(h,"electrical_probe",4,z,1)
            val cable=if(i==2)"signal_cable" else "low_voltage_cable"
            PartInventory.get(probe,probe.parts[0]!!).setStackInSlot(0,net.minecraft.world.item.ItemStack(Content.partItems.getValue(cable).get()))
            val load=InfrastructureGameTests.create(h,"creative_power_resistor",5,z,2).parts[0]!!
            val ground=InfrastructureGameTests.create(h,"ground_cable",6,z).parts[0]!!
            val expected=source.value/(load.value+1.0/listOf(10.0,25.0,.1)[i]+d.definition.resistance*2+PartDefinition.byId.getValue(cable).resistance*2+load.resistance*2+(if(i==2).05 else source.definition.resistance)+ground.resistance+(if(i==2)0.0 else .002)+1e-6)
            Branch(n,d,load,expected)
        }
        h.runAtTickTime(40) {
            for(b in branches) {
                val d=b.diode
                if(abs(abs(b.load.current)-b.expected)>1e-5 || d.connectedMask!=5)h.fail("Original diode forward path/mask failed: ${d.definition.id}, ${b.load.current} A, expected ${b.expected}, mask=${d.connectedMask}")
                val rated=d.definition.maxCurrent
                if(abs(d.definition.value-1/rated)>1e-12 || abs(d.thermalState.getDouble("capacity")-rated*1.2*30/130)>1e-12 || d.thermalState.getDouble("delta")<=.0001)h.fail("Original diode resistor/heat capacity/real loss missing: ${d.definition.id}, ${d.thermalState}")
                b.node.parts[0]!!.rotation=0;b.node.changedTopology()
            }
        }
        h.runAtTickTime(70) {
            for(b in branches) {
                if(abs(b.load.current)>1e-6 || b.diode.gridState.getBoolean("diodeOn"))h.fail("Reversed original diode continued conducting: ${b.diode.definition.id}, ${b.load.current}")
                b.diode.rotation=2;b.node.changedTopology()
            }
        }
        h.runAtTickTime(100) {
            for(b in branches) {
                if(abs(abs(b.load.current)-b.expected)>1e-5)h.fail("Diode did not recover after returning to forward bias")
                val saved=b.node.saveWithoutMetadata();b.node.load(saved);b.node.changedTopology()
            }
        }
        h.runAtTickTime(125) { for(b in branches)if(abs(abs(b.load.current)-b.expected)>1e-5)h.fail("Actual diode reload lost conducting state or original resistance");h.succeed() }
    }
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=90)
    fun allThreeOriginalDiodesHaveActualThermalFailureInsteadOfIndestructibleDecorativeRatings(h: GameTestHelper) {
        val nodes=listOf("10a_diode","25a_diode","signal_diode").mapIndexed { i,id -> InfrastructureGameTests.create(h,id,3+i*4,7).also { it.parts[0]!!.thermalState.putDouble("delta",500.0);it.changedTopology() } }
        h.runAtTickTime(60) {
            for(n in nodes)if((h.level.getBlockEntity(n.blockPos) as? FaceNodeEntity)?.parts?.filterNotNull()?.any { it.definition.behavior==Behavior.DIODE }==true)h.fail("Overheated original diode survived the actual thermal watchdog")
            h.succeed()
        }
    }
}
