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

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object PassiveGameTests {
    private fun device(h: GameTestHelper,id: String,x: Int,z: Int=8,rotation: Int=1): FaceNodeEntity {
        GameTestYards.clear(h,215,2,6)
        val pos=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+x,215,it.z+z) }
        h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(pos,Content.node.get().defaultBlockState())
        return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { this.rotation=rotation };it.changedTopology() }
    }
    private fun material(id: String,count: Int=1)=ItemStack(Content.materialItems.getValue(id).get(),count)
    private fun reload(n: FaceNodeEntity) { val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology() }
    @JvmStatic @GameTest(batch="original_passives",template="grid_empty",timeoutTicks=85)
    fun coalHardwareAndThermistorActuallySetTwoTerminalResistanceAndSurviveReload(h: GameTestHelper) {
        val nodes=listOf("power_resistor","thermistor").mapIndexed { i,id ->
            val z=4+i*6;val n=device(h,id,8,z);val p=n.parts[0]!!
            PartInventory.get(n,p).setStackInSlot(0,material("coal_dust",48))
            if(i==1)p.thermalState.putDouble("delta",50.0)
            device(h,"electrical_source",9,z).parts[0]!!.value=10.0;device(h,"ground_cable",7,z);n
        }
        h.runAtTickTime(20) {
            for((i,n) in nodes.withIndex()) {
                val p=n.parts[0]!!;val expected=if(i==0)100.0 else PassiveSettings.resistance(48,-.01,p.thermalState.getDouble("delta"))
                if(abs(p.value/expected-1)>.015 || abs((p.voltage-p.voltageB)/p.current/expected-1)>.015 || p.connectedMask!=10)h.fail("Original coal/thermal resistance or left/right power pins failed: ${p.save()}")
                if(DeviceControls.valueKey(p)!=null || PartInventory.get(n,p).isItemValid(0,ItemStack(Items.COAL)))h.fail("Hardware resistor still exposes an editable value or accepts undusted coal")
                reload(n)
            }
        }
        h.runAtTickTime(55) {
            if(nodes.any { it.parts[0]!!.legacyPassive || PartInventory.get(it,it.parts[0]!!).getStackInSlot(0).count!=48 })h.fail("Hardware or migration mode changed during actual node reload")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_passives",template="grid_empty",timeoutTicks=100)
    fun rheostatUsesActualFiveVoltControlAndSeparateThermalPowerPins(h: GameTestHelper) {
        val n=device(h,"rheostat",8);val p=n.parts[0]!!;PartInventory.get(n,p).setStackInSlot(0,material("coal_dust",48))
        device(h,"electrical_source",9).parts[0]!!.value=1.0;device(h,"ground_cable",7)
        val gate=device(h,"signal_source",8,9).parts[0]!!.apply { value=0.0 }
        h.runAtTickTime(20) {
            if(abs(p.value-100.0/101)>.001 || p.connectedMask!=11)h.fail("Unpowered rheostat did not use source minimum resistance: ${p.save()}")
            gate.value=5.0
        }
        h.runAtTickTime(45) {
            if(abs(p.value-100)>.1 || p.pinVoltages[0]<4.99 || abs(p.current-.01)>.0001)h.fail("Signal input did not set physical rheostat resistance")
            val runtime=ElementRuntime(LevelCircuit(h.level),n,p)
            if(runtime.connectionKind(0)!=1 || runtime.connectionKind(1)!=0)h.fail("Rheostat signal terminal joined its power circuit")
            gate.value=2.5
        }
        h.runAtTickTime(70) { if(abs(p.value-100*.51/1.01)>.1)h.fail("Rheostat lost original continuous control scale");h.succeed() }
    }
    @JvmStatic @GameTest(batch="original_passives",template="grid_empty",timeoutTicks=140)
    fun dielectricRedstoneCapacitorChargesThroughARealResistorAndRetainsFieldOnReload(h: GameTestHelper) {
        val n=device(h,"power_capacitor",8);val p=n.parts[0]!!;val inv=PartInventory.get(n,p)
        inv.setStackInSlot(0,ItemStack(Items.REDSTONE));inv.setStackInSlot(1,material("dielectric"))
        device(h,"electrical_source",10).parts[0]!!.value=10.0
        device(h,"creative_power_resistor",9,rotation=0).parts[0]!!.value=10.0;device(h,"ground_cable",7)
        h.runAtTickTime(20) {
            if(p.value!=.1 || p.voltage-p.voltageB !in 5.0..7.0 || inv.getSlotLimit(0)!=13 || inv.getSlotLimit(1)!=20)h.fail("Hardware capacitance, actual RC transient or original slot limits failed: ${p.save()}")
            reload(n)
        }
        h.runAtTickTime(95) {
            val q=n.parts[0]!!
            if(q.voltage-q.voltageB<9.8 || q.gridState.getDouble("passiveStoredJ")<4.8 || PartInventory.get(n,q).getStackInSlot(1).count!=1)h.fail("Real capacitor lost its saved voltage, dielectric or field energy")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_passives",template="grid_empty",timeoutTicks=85)
    fun capacitorHardwareChangesDrainSourceLegalizationEnergyAndRespectDielectricVoltage(h: GameTestHelper) {
        val n=device(h,"power_capacitor",8);val p=n.parts[0]!!
        p.value=.1;p.voltage=10.0;p.voltageB=0.0
        val inv=PartInventory.get(n,p);inv.setStackInSlot(0,ItemStack(Items.REDSTONE,7));inv.setStackInSlot(1,material("dielectric"))
        device(h,"ground_cable",7)
        var first=0.0
        h.runAtTickTime(5) {
            first=p.gridState.getDouble("capacitorLegaliseJ")
            if(first !in 35.0..45.0 || p.value!=1.0)h.fail("Capacitance change did not invoke original finite legalization drain: ${p.save()}")
            reload(n)
        }
        h.runAtTickTime(65) {
            val q=n.parts[0]!!
            if(q.gridState.getDouble("capacitorLegaliseJ")>=first || q.energy<=1.0 || q.voltage-q.voltageB>=10)h.fail("Capacitor legalization did not dissipate real saved field energy")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_passives",template="grid_empty",timeoutTicks=100)
    fun inductorUsesNineteenRealWindingsCoreLossAndInventoryChangeResetsCurrent(h: GameTestHelper) {
        val n=device(h,"power_inductor",8);val p=n.parts[0]!!;val inv=PartInventory.get(n,p)
        inv.setStackInSlot(0,material("copper_cable",19));inv.setStackInSlot(1,material("cheap_ferromagnetic_core"))
        device(h,"electrical_source",9).parts[0]!!.value=5.0;device(h,"ground_cable",7)
        h.runAtTickTime(30) {
            if(p.value!=100.0 || p.current !in .05..1.0 || inv.getSlotLimit(0)!=19)h.fail("Actual wound inductor did not use its source inductance and limited ramp: ${p.save()}")
            reload(n)
        }
        h.runAtTickTime(50) {
            val q=n.parts[0]!!
            if(q.inductorCurrent<.05 || q.value!=100.0)h.fail("Inductor current vanished on a topology rebuild")
            PartInventory.get(n,q).setStackInSlot(1,material("optimal_ferromagnetic_core"))
        }
        h.runAtTickTime(53) { if(n.parts[0]!!.inductorCurrent>.03)h.fail("Changing a real inductor core did not reset its field state") }
        h.runAtTickTime(75) { if(n.parts[0]!!.gridState.getDouble("passiveDissipatedJ")<=0)h.fail("Removed field energy was not accounted for");h.succeed() }
    }
    @JvmStatic @GameTest(batch="original_passives",template="grid_empty",timeoutTicks=100)
    fun variableInductorQuantizesFiveVoltInputWithoutCreatingFieldEnergyAndPreviewSavesRemainCompatible(h: GameTestHelper) {
        val n=device(h,"variable_inductor",8);val p=n.parts[0]!!
        PartInventory.get(n,p).setStackInSlot(0,material("optimal_ferromagnetic_core"));p.inductorCurrent=2.0;p.gridState.remove("passiveReset")
        device(h,"ground_cable",7);device(h,"ground_cable",9)
        val gate=device(h,"signal_source",8,9).parts[0]!!.apply { value=5.0 }
        h.runAtTickTime(15) { if(p.value!=100.0 || p.gridState.getDouble("passiveStoredJ") !in 190.0..200.01)h.fail("Variable inductor initial field/calibration failed");gate.value=0.0 }
        h.runAtTickTime(35) { if(abs(p.value-.1)>1e-8 || p.gridState.getDouble("passiveStoredJ")>200.01)h.fail("Changing control created unpowered field energy");n.changedTopology() }
        h.runAtTickTime(45) { if(p.gridState.getDouble("passiveStoredJ")>200.01)h.fail("An unrelated topology rebuild created variable-inductor field energy");gate.value=5.0 }
        h.runAtTickTime(60) {
            if(p.value!=100.0 || p.gridState.getDouble("passiveStoredJ")>200.01)h.fail("Repeated control changes created energy")
            val old=PartState(PartDefinition.byId.getValue("power_resistor"),Direction.DOWN).save().apply { putInt("componentDataVersion",4);remove("legacyPassive");putDouble("value",345.0) }
            val restored=PartState.load(old)!!
            if(!restored.legacyPassive || restored.value!=345.0 || PartInventory.slots(restored)!=0 || DeviceControls.valueKey(restored)==null || !PartState.load(restored.save())!!.legacyPassive)h.fail("Preview-save migration changed its existing manual value or mode")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_passives",template="grid_empty",timeoutTicks=95)
    fun dielectricOvervoltageAndRealResistorJouleHeatConsumeTheFailedParts(h: GameTestHelper) {
        val cap=device(h,"power_capacitor",5,5);val cp=cap.parts[0]!!
        val inv=PartInventory.get(cap,cp);inv.setStackInSlot(0,ItemStack(Items.REDSTONE));inv.setStackInSlot(1,material("dielectric"))
        device(h,"electrical_source",6,5).parts[0]!!.value=200.0;device(h,"ground_cable",4,5)
        val resistor=device(h,"power_resistor",11,11);device(h,"electrical_source",12,11).parts[0]!!.value=50.0;device(h,"ground_cable",10,11)
        h.runAtTickTime(70) {
            if((h.level.getBlockEntity(cap.blockPos) as? FaceNodeEntity)?.parts?.get(0)?.definition?.id=="power_capacitor")h.fail("Fitted dielectric voltage protection did not consume the overstressed capacitor")
            if((h.level.getBlockEntity(resistor.blockPos) as? FaceNodeEntity)?.parts?.get(0)?.definition?.id=="power_resistor")h.fail("Physical resistor I²R heat did not trigger original thermal destruction")
            h.succeed()
        }
    }
}
