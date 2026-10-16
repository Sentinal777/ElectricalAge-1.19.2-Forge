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
object BatteryChargerGameTests {
    private fun device(h: GameTestHelper,id: String,x: Int=5): FaceNodeEntity {
        GameTestYards.clear(h,220)
        val base=h.absolutePos(BlockPos.ZERO);val pos=BlockPos(base.x+x,220,base.z+5)
        h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(pos,Content.node.get().defaultBlockState())
        return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN);it.changedTopology() }
    }
    private fun inventory(n: FaceNodeEntity)=PartInventory.get(n,n.parts[0]!!)
    private fun charged(n: FaceNodeEntity)=(0..3).sumOf { ElectricalItems.energy(inventory(n).getStackInSlot(it)) }
    private fun checkLedger(h: GameTestHelper,n: FaceNodeEntity) {
        val p=n.parts[0]!!
        if(abs(p.energy-charged(n)-p.gridState.getDouble("chargerEnergy")-p.thermalState.getDouble("chargerLossJ"))>1e-5)h.fail("Charger input, finite buffer, portable charge and booster loss did not conserve actual electrical work")
    }
    @JvmStatic @GameTest(batch="battery_chargers",template="grid_empty",timeoutTicks=170)
    fun actualBlockInsertionFourSlotChargingSavedBufferAndManualStopConserveWork(h: GameTestHelper) {
        val n=device(h,"50v_battery_charger");var p=n.parts[0]!!;val player=h.makeMockPlayer();player.abilities.instabuild=false
        if(p.enabled || inventory(n).slots!=5)h.fail("Original charger lost its default stopped state or five real slots")
        repeat(4) {
            val held=PortableEnergyGameTests.item("portable_battery");player.setItemInHand(InteractionHand.MAIN_HAND,held)
            n.blockState.use(h.level,player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(n.blockPos),Direction.UP,n.blockPos,false))
            if(!held.isEmpty || inventory(n).getStackInSlot(it).isEmpty)h.fail("Real block interaction did not insert and consume one held portable battery")
        }
        device(h,"electrical_source",6).parts[0]!!.value=50.0;p.enabled=true
        var cycles=0;var stopped=0.0
        h.runAtTickTime(20) { cycles=p.gridState.getInt("chargerCycles") }
        h.runAtTickTime(40) { if(p.gridState.getInt("chargerCycles")!=cycles+1)h.fail("Charger controller did not retain its original one-second cadence");cycles++ }
        h.runAtTickTime(60) {
            if(p.gridState.getInt("chargerCycles")!=cycles+1 || p.energy<1000 || charged(n)<600 || (0..2).any { ElectricalItems.energy(inventory(n).getStackInSlot(it))<200 } || ElectricalItems.energy(inventory(n).getStackInSlot(3))>=ElectricalItems.energy(inventory(n).getStackInSlot(0)))h.fail("Original four-slot ordering, per-item charge rate or actual supply failed: ${p.save()}")
            checkLedger(h,n);val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology();p=n.parts[0]!!;checkLedger(h,n)
        }
        h.runAtTickTime(100) { checkLedger(h,n);p.enabled=false;stopped=charged(n);n.changedTopology() }
        h.runAtTickTime(140) { if(charged(n)!=stopped || p.active)h.fail("Stopped charger kept charging portable items");checkLedger(h,n);h.succeed() }
    }
    @JvmStatic @GameTest(batch="battery_chargers",template="grid_empty",timeoutTicks=130)
    fun originalChargerBoostersUseTheirOwnSpeedAndEfficiencyWithFiniteLoss(h: GameTestHelper) {
        val n=device(h,"50v_battery_charger");val p=n.parts[0]!!;val inv=inventory(n)
        inv.setStackInSlot(0,PortableEnergyGameTests.item("portable_condensator_pack"))
        inv.setStackInSlot(4,ItemStack(Content.materialItems.getValue("machine_booster").get(),2))
        if(inv.getSlotLimit(4)!=5 || inv.getSlotLimit(0)!=1)h.fail("Original charger booster/battery stack limits changed")
        device(h,"electrical_source",6).parts[0]!!.value=50.0;p.enabled=true
        h.runAtTickTime(95) {
            val stored=charged(n)+p.gridState.getDouble("chargerEnergy")
            if(charged(n)<1000 || abs(stored/p.energy-.81)>1e-9 || abs(p.thermalState.getDouble("chargerLossJ")/p.energy-.19)>1e-9 || p.thermalState.getDouble("chargerWatts") !in 600.0..625.01)h.fail("Two charger boosters did not retain original 1.25-squared speed and 0.9-squared efficiency")
            checkLedger(h,n);h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="battery_chargers",template="grid_empty",timeoutTicks=90)
    fun allThreeOriginalChargerRatingsDrawTheirCalibratedRealElectricalPower(h: GameTestHelper) {
        val nodes=listOf("weak_50v_battery_charger" to 3,"50v_battery_charger" to 8,"200v_battery_charger" to 13).map { (id,x) ->
            val n=device(h,id,x);inventory(n).setStackInSlot(0,PortableEnergyGameTests.item("portable_condensator"))
            device(h,"electrical_source",x+1).parts[0]!!.value=n.parts[0]!!.definition.nominalVoltage;n.parts[0]!!.enabled=true;n
        }
        h.runAtTickTime(50) {
            for(n in nodes) {
                val p=n.parts[0]!!
                if(p.energy/p.definition.power !in 2.3..2.6 || p.voltage/p.definition.nominalVoltage !in .97..1.001 || charged(n)<=100)h.fail("Original 200/400/1000 W charger calibration failed: ${p.save()}")
                checkLedger(h,n)
            }
            h.succeed()
        }
    }
}
