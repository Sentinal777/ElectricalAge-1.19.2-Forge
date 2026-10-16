package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.entity.animal.Chicken
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object IncubatorGameTests {
    private fun node(h: GameTestHelper,id: String,x: Int=8): FaceNodeEntity {
        GameTestYards.clear(h,260,2,6)
        val base=h.absolutePos(BlockPos.ZERO);val at=BlockPos(base.x+x,260,base.z+8);val d=PartDefinition.byId.getValue(id)
        h.level.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(at,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
        return (h.level.getBlockEntity(at) as FaceNodeEntity).also { it.parts[0]=PartState(d,Direction.DOWN);it.changedTopology() }
    }
    @JvmStatic @GameTest(batch="original_incubator",template="grid_empty",timeoutTicks=105)
    fun savedPaidIncubationStopsWithoutPowerAndHatchesExactlyOneBabyForOneRealEgg(h: GameTestHelper) {
        val n=node(h,"50v_egg_incubator");var p=n.parts[0]!!
        val earlierChicks=h.level.getEntitiesOfClass(Chicken::class.java,AABB(n.blockPos).inflate(20.0)).map { it.uuid }.toSet()
        val inventory=PartInventory.get(n,p);inventory.setStackInSlot(0,ItemStack(Items.EGG,2))
        // A genuine partly completed saved job, with the earlier 9940 J already accounted for.
        p.energy=9940.0;p.progress=9940.0;p.gridState.putDouble("incubatorTargetJ",10000.0);p.gridState.putDouble("incubatorRemainingJ",60.0)
        val source=node(h,"electrical_source",7);source.parts[0]!!.value=50.0
        var remaining=0.0;var paid=0.0
        h.runAtTickTime(15) {
            if(p.definition.legacyId!=2624 || p.voltage !in 49.9..50.0 || abs(p.voltage*p.current-50)>.1 || p.gridState.getInt("incubatorHatched")!=0 || PartInventory.get(n,p).getStackInSlot(0).count!=2)h.fail("Original 50 V / 50 W incubator did not draw the real paid load before hatching: ${p.save()}")
            val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology();p=n.parts[0]!!
            remaining=p.gridState.getDouble("incubatorRemainingJ");paid=p.energy
            if(abs(remaining+p.energy-10000)>1e-5)h.fail("Saved incubation progress is not backed by actual paid electrical work")
            source.parts[0]!!.enabled=false;source.changedTopology()
        }
        h.runAtTickTime(40) {
            if(abs(p.gridState.getDouble("incubatorRemainingJ")-remaining)>1e-5 || abs(p.energy-paid)>1e-5 || p.gridState.getInt("incubatorHatched")!=0)h.fail("Unpowered saved incubator advanced or hatched without paid work")
            source.parts[0]!!.enabled=true;source.changedTopology()
        }
        h.runAtTickTime(65) {
            val chicks=h.level.getEntitiesOfClass(Chicken::class.java,AABB(n.blockPos).inflate(5.0)).filter { it.uuid !in earlierChicks }
            if(p.gridState.getInt("incubatorHatched")!=1 || PartInventory.get(n,p).getStackInSlot(0).count!=1 || chicks.size!=1 || !chicks.single().isBaby || p.energy<10000 || p.gridState.getDouble("incubatorTargetJ") !in 10000.0..20000.0)h.fail("Incubator failed one-egg/one-baby paid hatch, saved counter or original random next cost: ${p.save()}, chicks=${chicks.size}")
            PartInventory.get(n,p).extractItem(0,64,false);paid=p.energy
        }
        h.runAtTickTime(90) {
            if(p.active || abs(p.current)>1e-6 || abs(p.energy-paid)>1e-3 || p.gridState.getInt("incubatorHatched")!=1)h.fail("Empty incubator continued its real heating load or duplicated a chick")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=25)
    fun originalIncubatorAutomationAcceptsEggsFromEverySideWithoutExtractingOrDuplicatingThem(h: GameTestHelper) {
        OriginalCraftingGameTests.craft(h,listOf("IGG","E G","CII"),mapOf('I' to "minecraft:iron_ingot",'G' to "minecraft:glass_pane",'E' to "eln:small_50v_tungsten_heating_element",'C' to "eln:cheap_chip"),"eln:50v_egg_incubator")
        val n=node(h,"50v_egg_incubator");val p=n.parts[0]!!
        for(side in Direction.values()) {
            val cap=n.getCapability(ForgeCapabilities.ITEM_HANDLER,side).orElseThrow { IllegalStateException("Missing incubator egg input on $side") }
            if(cap.slots!=1 || cap.getSlotLimit(0)!=64 || cap.isItemValid(0,ItemStack(Items.STONE)) || !cap.isItemValid(0,ItemStack(Items.EGG)))h.fail("Incubator lost original single 64-egg input slot or accepts non-eggs")
            val before=cap.getStackInSlot(0).count
            if(!cap.insertItem(0,ItemStack(Items.EGG),true).isEmpty || cap.getStackInSlot(0).count!=before)h.fail("Simulated egg insertion changed storage")
            if(!cap.insertItem(0,ItemStack(Items.EGG),false).isEmpty || !cap.extractItem(0,64,false).isEmpty)h.fail("Actual egg input failed or automation extracted incubating eggs")
        }
        if(PartInventory.get(n,p).getStackInSlot(0).count!=6)h.fail("Sided incubator automation duplicated or lost input eggs")
        val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology()
        if(PartInventory.get(n,n.parts[0]!!).getStackInSlot(0).count!=6)h.fail("Incubator eggs did not survive actual node reload")
        h.succeed()
    }
    @JvmStatic @GameTest(batch="original_incubator_overvoltage",template="grid_empty",timeoutTicks=55)
    fun originalIncubatorVoltageWatchdogDestroysAnOvervoltedActualMachine(h: GameTestHelper) {
        val n=node(h,"50v_egg_incubator");PartInventory.get(n,n.parts[0]!!).setStackInSlot(0,ItemStack(Items.EGG))
        node(h,"electrical_source",7).parts[0]!!.value=100.0
        h.runAtTickTime(40) {
            if(h.level.getBlockEntity(n.blockPos) is FaceNodeEntity)h.fail("Original 65 V limit and voltage-integrating watchdog did not destroy the overvolted incubator")
            if(h.level.getEntitiesOfClass(Chicken::class.java,AABB(n.blockPos).inflate(5.0)).isNotEmpty())h.fail("Failed incubator hatched without paying the original minimum energy")
            h.succeed()
        }
    }
}
