package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object ContactGameTests {
    private fun device(h: GameTestHelper,id: String,x: Int,z: Int,rotation: Int=0): FaceNodeEntity {
        GameTestYards.clear(h,205,2,6)
        val pos=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+x,205,it.z+z) }
        h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(pos,Content.node.get().defaultBlockState())
        return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { this.rotation=rotation };it.changedTopology() }
    }
    @JvmStatic @GameTest(batch="original_contacts",template="grid_empty",timeoutTicks=110)
    fun allFourOriginalSwitchesStartOpenAndOperateRealContactsWithTheirSourceResistance(h: GameTestHelper) {
        val ids=listOf("low_voltage_switch","medium_voltage_switch","high_voltage_switch","very_high_voltage_switch")
        val nodes=ids.mapIndexed { i,id ->
            val z=3+i*4;val n=device(h,id,8,z);val p=n.parts[0]!!
            device(h,"electrical_source",9,z).parts[0]!!.value=p.definition.nominalVoltage
            device(h,"creative_power_resistor",7,z).parts[0]!!.value=p.definition.nominalVoltage*p.definition.nominalVoltage/100
            device(h,"ground_cable",6,z);n
        }
        fun click(n: FaceNodeEntity) {
            val player=h.makeMockPlayer();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
            n.blockState.use(h.level,player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(n.blockPos),Direction.UP,n.blockPos,false))
        }
        h.runAtTickTime(15) {
            if(nodes.any { it.parts[0]!!.enabled || it.parts[0]!!.active || abs(it.parts[0]!!.current)>it.parts[0]!!.definition.nominalVoltage/1e8 })h.fail("Original switches did not begin physically open")
            nodes.forEach(::click)
        }
        h.runAtTickTime(35) {
            for(n in nodes) {
                val p=n.parts[0]!!;val d=p.definition
                if(!p.active || p.connectedMask!=5 || abs(p.current/(100/d.nominalVoltage)-1)>.02 || abs((p.voltage-p.voltageB)/p.current-d.resistance)>1e-7)h.fail("Original ${d.name} lost its actual two-terminal contact resistance or voltage rating: ${p.save()}")
                val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology()
            }
        }
        h.runAtTickTime(55) { if(nodes.any { !it.parts[0]!!.active })h.fail("Closed switch state was lost on node reload");nodes.forEach(::click) }
        h.runAtTickTime(80) { if(nodes.any { it.parts[0]!!.active || abs(it.parts[0]!!.current)>it.parts[0]!!.definition.nominalVoltage/1e8 })h.fail("Actual switch interaction did not interrupt its own circuit");h.succeed() }
    }
    @JvmStatic @GameTest(batch="original_contacts",template="grid_empty",timeoutTicks=160)
    fun originalVoltageAndSignalRelaysKeepSeparatePinsSchmittHistoryAndNormallyClosedMode(h: GameTestHelper) {
        data class Fixture(val node: FaceNodeEntity,val gate: PartState,val reader: PartState)
        val positions=listOf(5 to 5,13 to 5,5 to 13,13 to 13,9 to 9)
        val ids=listOf("low_voltage_relay","medium_voltage_relay","high_voltage_relay","very_high_voltage_relay","signal_relay")
        val fixtures=ids.mapIndexed { i,id ->
            val (x,z)=positions[i];val n=device(h,id,x,z);val d=n.parts[0]!!.definition
            device(h,if(d.contact!!.signal)"signal_source" else "electrical_source",x,z+1).parts[0]!!.value=d.nominalVoltage
            val gate=device(h,"signal_source",x+1,z).parts[0]!!.apply { value=0.0 }
            val reader=if(d.contact.signal)device(h,"signal_cable",x,z-1).parts[0]!! else {
                val r=device(h,"creative_power_resistor",x,z-1,1).parts[0]!!.apply { value=d.nominalVoltage*d.nominalVoltage/100 }
                device(h,"ground_cable",x,z-2);r
            }
            Fixture(n,gate,reader)
        }
        fun off()=fixtures.all { val p=it.node.parts[0]!!;!p.active && if(p.definition.contact!!.signal)abs(it.reader.voltage)<.001 else abs(it.reader.current)<p.definition.nominalVoltage/1e8 }
        fun on()=fixtures.all {
            val p=it.node.parts[0]!!;p.active && p.connectedMask==11 && if(p.definition.contact!!.signal)it.reader.voltage>4.99 else abs(it.reader.current/(100/p.definition.nominalVoltage)-1)<.02
        }
        h.runAtTickTime(15) { if(!off())h.fail("Original relay gate was joined to a power terminal or contacts started closed");fixtures.forEach { it.gate.value=4.0 } }
        h.runAtTickTime(30) { if(!on())h.fail("Original relay failed to close above its separate 3.5 V control threshold");fixtures.forEach { it.gate.value=2.5 } }
        h.runAtTickTime(50) { if(!on())h.fail("Relay lost its high Schmitt state between 1.5 and 3.5 V");fixtures.forEach { it.gate.value=1.0 } }
        h.runAtTickTime(65) { if(!off())h.fail("Relay failed to open below the original 1.5 V threshold");fixtures.forEach { it.gate.value=2.5 } }
        h.runAtTickTime(80) {
            if(!off())h.fail("Relay retriggered inside its low Schmitt band")
            fixtures.forEach { it.node.parts[0]!!.enabled=true;it.node.changedTopology() }
        }
        h.runAtTickTime(100) {
            if(!on())h.fail("Original normally-closed inversion failed")
            fixtures.forEach { val saved=it.node.saveWithoutMetadata();it.node.load(saved);it.node.changedTopology() }
        }
        h.runAtTickTime(120) { if(!on())h.fail("Relay lost saved Schmitt history or normally-closed selection");fixtures.forEach { it.gate.value=4.0 } }
        h.runAtTickTime(140) { if(!off())h.fail("Normally-closed relay failed to open on high input");h.succeed() }
    }
    @JvmStatic @GameTest(batch="original_contacts",template="grid_empty",timeoutTicks=270)
    fun allThreeCurrentRelaysHeatFromActualRatedCurrentAndFailOnSustainedOverload(h: GameTestHelper) {
        val nodes=listOf("low_current_relay","medium_current_relay","high_current_relay").mapIndexed { i,id ->
            val x=4+i*5;val n=device(h,id,x,10);val d=n.parts[0]!!.definition
            // Actual controlled short: two 0.01-ohm terminals, 0.01-ohm contact and external source/ground impedances.
            device(h,"electrical_source",x,11).parts[0]!!.value=.034001*d.maxCurrent*2.5
            device(h,"ground_cable",x,9);device(h,"signal_source",x+1,10);n
        }
        h.runAtTickTime(65) {
            for(n in nodes) {
                val p=n.parts[0] ?: h.fail("Current relay failed before its original thermal tolerance was exercised").let { return@runAtTickTime }
                if(!p.active || abs(p.current/(p.definition.maxCurrent*2.5)-1)>.01 || p.temperature !in 45.0..140.0 || p.energy<=0 || p.thermalState.getDouble("capacity")<=0)h.fail("Current relay lost its original actual I²R heating, finite capacity or rated current: ${p.save()}")
            }
        }
        h.runAtTickTime(235) {
            if(nodes.any { (h.level.getBlockEntity(it.blockPos) as? FaceNodeEntity)?.parts?.get(0)?.definition?.contact?.currentThermal==true })h.fail("Sustained actual current overload failed to consume an overheated original current relay")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_contacts",template="grid_empty",timeoutTicks=70)
    fun anOpenOriginalSwitchStillProtectsItsLiveInputAgainstSustainedOvervoltage(h: GameTestHelper) {
        val n=device(h,"low_voltage_switch",8,8);device(h,"electrical_source",9,8).parts[0]!!.value=200.0
        h.runAtTickTime(45) {
            if((h.level.getBlockEntity(n.blockPos) as? FaceNodeEntity)?.parts?.get(0)?.definition?.id=="low_voltage_switch")h.fail("Open original switch ignored overvoltage on its live terminal")
            h.succeed()
        }
    }
}
