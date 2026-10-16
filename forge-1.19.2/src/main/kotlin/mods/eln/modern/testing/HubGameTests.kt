package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object HubGameTests {
    private fun device(h: GameTestHelper,id: String,x: Int=8,z: Int=8,rotation: Int=0): FaceNodeEntity {
        GameTestYards.clear(h,205,4,18)
        val pos=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+x,205,it.z+z) }
        h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(pos,Content.node.get().defaultBlockState())
        return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { this.rotation=rotation };it.changedTopology() }
    }
    private fun fit(n: FaceNodeEntity,slots: List<String?>) {
        val inv=PartInventory.get(n,n.parts[0]!!)
        slots.forEachIndexed { i,id -> if(id!=null)inv.setStackInSlot(i,ItemStack(Content.partItems.getValue(id).get())) }
    }
    @JvmStatic @GameTest(batch="original_hubs",template="grid_empty",timeoutTicks=90)
    fun originalHubCrossingsCarryDifferentVoltagesWithoutJoiningTheirIndependentCircuits(h: GameTestHelper) {
        val n=device(h,"hub");val p=n.parts[0]!!
        fit(n,listOf("low_voltage_cable","medium_voltage_cable","low_voltage_cable","medium_voltage_cable"))
        device(h,"electrical_source",9).parts[0]!!.value=10.0;device(h,"electrical_source",8,9).parts[0]!!.value=40.0
        val west=device(h,"creative_power_resistor",7).parts[0]!!;device(h,"ground_cable",6)
        val north=device(h,"creative_power_resistor",8,7,1).parts[0]!!;device(h,"ground_cable",8,6)
        h.runAtTickTime(50) {
            if(p.value!=48.0 || p.connectedMask!=15 || abs(west.current-.1)>.001 || abs(north.current-.4)>.002 || p.pinVoltages[0] !in 9.99..10.01 || p.pinVoltages[1] !in 39.99..40.01 || p.energy<=0 || !p.energy.isFinite())h.fail("Hub crossings shorted their separate voltages or lost actual finite conductor resistance: ${p.save()}")
            if(abs(10-p.pinVoltages[0]-west.current*(.002+.001+.0125))>1e-5)h.fail("Hub terminal lost the original low-voltage-cable calibration of Eln.applySmallRs")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_hubs",template="grid_empty",timeoutTicks=130)
    fun sixOriginalHubLinksRerouteActualPowerAndKeepTheirSavedConfiguration(h: GameTestHelper) {
        val n=device(h,"hub");var p=n.parts[0]!!;fit(n,listOf("low_voltage_cable","low_voltage_cable","low_voltage_cable",null))
        device(h,"electrical_source",9).parts[0]!!.value=10.0
        val west=device(h,"creative_power_resistor",7).parts[0]!!;device(h,"ground_cable",6)
        val south=device(h,"creative_power_resistor",8,9,3).parts[0]!!;device(h,"ground_cable",8,10)
        h.runAtTickTime(30) {
            if(abs(west.current-.1)>.001 || abs(south.current)>1e-8 || p.connectedMask!=7)h.fail("Empty hub terminal or default disconnected link conducted current")
            HubSettings.toggle(n,p,0)
        }
        h.runAtTickTime(50) {
            if(abs(west.current-.1)>.001 || abs(south.current-.1)>.001)h.fail("Closing original hub corner link did not route actual power")
            HubSettings.toggle(n,p,4)
        }
        h.runAtTickTime(70) {
            if(abs(west.current)>1e-8 || abs(south.current-.1)>.001 || p.value!=33.0)h.fail("Opening original crossing link failed to isolate its own terminal")
            val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology();p=n.parts[0]!!
        }
        h.runAtTickTime(90) {
            if(p.value!=33.0 || abs(west.current)>1e-8 || abs(south.current-.1)>.001)h.fail("Hub lost its fitted conductors or link state during node reload")
            PartInventory.get(n,p).extractItem(0,1,false)
        }
        h.runAtTickTime(110) {
            if(abs(west.current)>1e-8 || abs(south.current)>1e-8 || p.connectedMask!=6 || HubSettings.toggle(n,p,6))h.fail("Removing a hub cable kept supplying power or accepted an invalid internal link")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_hubs",template="grid_empty",timeoutTicks=45)
    fun actualHubWrenchAndOrdinaryPickupPreserveFourFittedCablesExactlyOnce(h: GameTestHelper) {
        var n=device(h,"hub");val cables=listOf("low_voltage_cable","signal_cable","medium_voltage_cable","high_voltage_cable")
        fit(n,cables);n.parts[0]!!.value=52.0
        for(wrench in listOf(true,false)) {
            val p=n.parts[0]!!;val inv=PartInventory.get(n,p)
            if(inv.isItemValid(0,ItemStack(Items.REDSTONE)) || inv.isItemValid(0,ItemStack(Content.partItems.getValue("low_current_cable").get())) || inv.getSlotLimit(0)!=1)h.fail("Hub accepted a cable absent from the original voltage-conductor slot or allowed stacks")
            val pos=n.blockPos;val player=h.makeMockPlayer();player.abilities.instabuild=false
            h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(pos).inflate(2.0)).forEach { it.discard() }
            if(wrench) {
                player.setShiftKeyDown(true);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Content.wrench.get()))
                n.blockState.use(h.level,player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false))
            } else h.level.destroyBlock(pos,true)
            val drops=h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(pos).inflate(2.0))
            if(drops.size!=1 || drops.single().item.item!=Content.partItems.getValue("hub").get() || drops.single().item.count!=1 || !h.level.getBlockState(pos).isAir)h.fail("Hub pickup lost or duplicated its contained conductor items")
            val held=drops.single().item.copy();drops.single().discard();player.setShiftKeyDown(false);player.setItemInHand(InteractionHand.MAIN_HAND,held)
            val support=pos.below()
            if(!held.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(support),Direction.UP,support,false))).consumesAction() || !held.isEmpty)h.fail("Picked-up hub failed actual survival placement")
            n=h.level.getBlockEntity(pos) as FaceNodeEntity
            val restored=n.parts[0]!!;val inventory=PartInventory.get(n,restored)
            if(restored.value!=52.0 || cables.indices.any { inventory.getStackInSlot(it).item!=Content.partItems.getValue(cables[it]).get() || inventory.getStackInSlot(it).count!=1 })h.fail("Placed hub lost its exact cable slots or link selection")
        }
        h.succeed()
    }
    @JvmStatic @GameTest(batch="original_hubs",template="grid_empty",timeoutTicks=65)
    fun actualHubFittedSignalConductorFailsUnderSustainedPowerOvervoltage(h: GameTestHelper) {
        val n=device(h,"hub");fit(n,listOf("signal_cable",null,null,null));device(h,"electrical_source",9)
        h.runAtTickTime(40) {
            if((h.level.getBlockEntity(n.blockPos) as? FaceNodeEntity)?.parts?.filterNotNull()?.any { it.definition.behavior==Behavior.HUB }==true)h.fail("Hub ignored the original fitted signal cable's voltage watchdog")
            h.succeed()
        }
    }
}
