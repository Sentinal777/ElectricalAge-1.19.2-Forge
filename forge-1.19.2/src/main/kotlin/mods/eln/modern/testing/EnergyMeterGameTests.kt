package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object EnergyMeterGameTests {
    private class Yard(val h: GameTestHelper,id: String="energy_meter") {
        val at=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+8,285,it.z+8) }
        val meter: FaceNodeEntity
        val source: FaceNodeEntity
        val load: FaceNodeEntity
        val p get()=meter.parts[0]!!
        init {
            GameTestYards.clear(h,285,2,5)
            meter=device(at,id);source=device(at.north(),"electrical_source");source.parts[0]!!.value=50.0
            load=device(at.south(),"creative_power_resistor");load.parts[0]!!.apply { rotation=1;value=100.0 };load.changedTopology();device(at.south(2),"ground_cable")
        }
        fun device(pos: BlockPos,id: String): FaceNodeEntity {
            h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(pos,Content.node.get().defaultBlockState())
            return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN);it.changedTopology() }
        }
        fun fit() { PartInventory.get(meter,p).setStackInSlot(0,ItemStack(Content.partItems.getValue("low_voltage_cable").get())) }
        fun command(command: Int,value: Double=0.0,text: String="",password: String="")=EnergyMeterControls.command(meter,p,command,value,text,password)
        fun loadJ()=load.parts[0]!!.energy
    }
    @JvmStatic @GameTest(batch="original_energy_meter",template="grid_empty",timeoutTicks=180)
    fun originalFittedCableAndOffSwitchGateRealPowerWhileTheCounterCannotActAsAnEnergySource(h: GameTestHelper) {
        val y=Yard(h);var held=0.0
        h.runAtTickTime(20) {
            if(y.p.enabled || y.p.connectedMask!=0 || y.loadJ()>1e-5)h.fail("Unfitted original energy meter connected or started closed")
            y.fit()
        }
        h.runAtTickTime(45) {
            if(y.p.enabled || y.p.connectedMask!=10 || y.loadJ()>1e-5)h.fail("Fitted meter ignored its original open switch or front/rear-only topology: ${y.p.save()}")
            y.command(1)
        }
        h.runAtTickTime(100) {
            val current=y.p.current;val expected=(y.p.voltage-y.p.voltageB)/current
            if(current !in .49.. .501 || abs(expected-.0125)>1e-7 || EnergyMeters.counter(y.p)<50 || EnergyMeters.counter(y.p)<y.loadJ() || y.p.gridState.getDouble("meterShuntHeatJ")<=0)h.fail("Real original inline shunt/counter lost fitted resistance, power or signed work: ${y.p.save()}, load=${y.loadJ()}")
            y.source.parts[0]!!.enabled=false;y.source.changedTopology();y.command(3,1e12)
            if(y.meter.getCapability(ForgeCapabilities.ENERGY).isPresent)h.fail("Meter counter exposed a fake energy-storage capability")
        }
        h.runAtTickTime(120) { held=y.loadJ() }
        h.runAtTickTime(155) {
            if(abs(y.loadJ()-held)>1e-5 || abs(y.p.voltage)>1e-4)h.fail("An arbitrary meter reading created real energy after supply loss")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_energy_prepay",template="grid_empty",timeoutTicks=230)
    fun originalPrepayOpensAtExhaustedCreditResumesAfterRefillAndReverseRealPowerReplenishesCredit(h: GameTestHelper) {
        val y=Yard(h);y.fit();y.command(1);y.command(2);y.command(3,5.0);var held=0.0
        h.runAtTickTime(40) {
            val credit=EnergyMeters.counter(y.p)
            if(!EnergyMeters.prepaid(y.p) || credit !in -2.0..0.0 || y.loadJ() !in 4.0..7.0)h.fail("Original prepay cutoff failed its real five-joule allowance and one-controller-step response: credit=$credit, load=${y.loadJ()}")
            held=y.loadJ()
        }
        h.runAtTickTime(70) {
            if(y.loadJ()-held>1e-5)h.fail("Exhausted prepay continued supplying actual work")
            y.command(3,100.0)
        }
        h.runAtTickTime(100) {
            if(y.loadJ()<held+20 || EnergyMeters.counter(y.p)>=90)h.fail("Refilling an original prepay counter did not resume real conduction")
            y.device(y.at.south(2),"electrical_source").parts[0]!!.value=100.0;y.command(2);y.command(3,0.0)
        }
        h.runAtTickTime(140) {
            if(y.p.current>=-.1 || EnergyMeters.counter(y.p)>-10)h.fail("Original meter did not count reverse real power with its sign: ${y.p.save()}")
            y.command(2);y.command(3,-5.0)
        }
        h.runAtTickTime(190) {
            if(!EnergyMeters.prepaid(y.p) || y.p.current>=-.1 || EnergyMeters.counter(y.p)<20)h.fail("Reverse physical current failed to replenish original prepay credit")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_energy_save",template="grid_empty",timeoutTicks=145)
    fun originalCounterTimeUnitsAndPasswordChecksPersistWithoutResettingOrDuplicatingARecordedInterval(h: GameTestHelper) {
        val y=Yard(h,"advanced_energy_meter");y.fit();y.command(1);var counter=0.0
        h.runAtTickTime(45) {
            counter=EnergyMeters.counter(y.p);val time=EnergyMeters.time(y.p)
            val saved=y.meter.saveWithoutMetadata();y.meter.load(saved);y.meter.changedTopology()
            if(EnergyMeters.counter(y.p)!=counter || EnergyMeters.time(y.p)!=time)h.fail("Actual inline meter reload reset/duplicated counter or original 72x time")
            if(!y.command(7,text="original-password") || y.command(3,999.0,password="wrong") || y.command(1,password="wrong"))h.fail("Meter accepted unauthorized counter/switch changes")
            if(!y.command(5,password="original-password") || !y.command(6,password="original-password") || y.p.gridState.getInt("meterEnergyUnit")!=2 || y.p.gridState.getInt("meterTimeUnit")!=1)h.fail("Original selectable MJ/day wheels did not persist")
            y.command(4,password="original-password")
        }
        h.runAtTickTime(65) {
            if(EnergyMeters.time(y.p) !in 68.0..76.0 || EnergyMeters.counter(y.p)<=counter || !EnergyMeterControls.authenticate(y.p,"original-password") || EnergyMeterControls.authenticate(y.p,"wrong"))h.fail("Meter time calibration, resumed work or saved password was lost")
            if(y.command(3,Double.NaN,password="original-password") || y.command(3,Double.POSITIVE_INFINITY,password="original-password"))h.fail("Meter accepted nonfinite counters")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_energy_hardware",template="grid_empty",timeoutTicks=45)
    fun originalRecipesAndActualManualCableInsertionKeepSingleHardwareAndRemovedAutomationCannotDuplicateIt(h: GameTestHelper) {
        val y=Yard(h)
        OriginalCraftingGameTests.craft(h,listOf("IcI","IRI","IcI"),mapOf('I' to "eln:iron_cable",'c' to "eln:copper_cable",'R' to "eln:cheap_chip"),"eln:energy_meter")
        OriginalCraftingGameTests.craft(h,listOf(" c ","PRP"," c "),mapOf('P' to "eln:iron_plate",'c' to "eln:copper_cable",'R' to "eln:advanced_chip"),"eln:advanced_energy_meter")
        val player=h.makeMockPlayer().apply { abilities.instabuild=false;setPos(y.at.x+.5,285.0,y.at.z+2.5) }
        val cable=ItemStack(Content.partItems.getValue("low_voltage_cable").get(),2);player.setItemInHand(InteractionHand.MAIN_HAND,cable)
        if(!cable.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(y.at),Direction.UP,y.at,false))).consumesAction() || cable.count!=1 || PartInventory.peek(y.p,0).count!=1)h.fail("Actual meter click did not consume exactly one fitted cable")
        val handlers=Direction.values().map { y.meter.getCapability(ForgeCapabilities.ITEM_HANDLER,it).orElseThrow { IllegalStateException("Missing meter cable automation") } }
        if(handlers.any { it.slots!=1 || it.getSlotLimit(0)!=1 || it.isItemValid(0,ItemStack(Content.partItems.getValue("high_current_cable").get())) })h.fail("Original meter fitted-cable slot accepted a current-cable descriptor or extra hardware")
        y.meter.setRemoved()
        if(handlers.any { !it.extractItem(0,1,false).isEmpty || !it.getStackInSlot(0).isEmpty })h.fail("Removed meter exposed duplicate fitted cable through stale automation")
        h.succeed()
    }
    @JvmStatic @GameTest(batch="original_energy_overvoltage",template="grid_empty",timeoutTicks=65)
    fun fittedCableVoltageRatingProtectsBothActualMeterTerminalsEvenWithTheSwitchOpen(h: GameTestHelper) {
        val y=Yard(h);y.fit();y.source.parts[0]!!.value=200.0
        h.runAtTickTime(45) {
            if(h.level.getBlockState(y.at).block==Content.node.get())h.fail("Open-switch meter terminal survived above its fitted 50 V cable watchdog")
            h.succeed()
        }
    }
}
