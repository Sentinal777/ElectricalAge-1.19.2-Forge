package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object ProtectionGameTests {
    private fun device(h: GameTestHelper,id: String,x: Int,z: Int=8): FaceNodeEntity {
        GameTestYards.clear(h,235,2,6)
        val pos=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+x,235,it.z+z) }
        h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(pos,Content.node.get().defaultBlockState())
        return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN);it.changedTopology() }
    }
    private fun material(id: String,count: Int=1)=ItemStack(Content.materialItems.getValue(id).get(),count)
    private fun reload(node: FaceNodeEntity) { val saved=node.saveWithoutMetadata();node.load(saved);node.changedTopology() }
    @JvmStatic @GameTest(batch="original_protection",template="grid_empty",timeoutTicks=105)
    fun allFourOriginalFusesHeatPhysicallyThenLeaveOneBlownFuseAndAnOpenCircuit(h: GameTestHelper) {
        val levels=listOf("low","medium","high","very_high")
        val nodes=levels.mapIndexed { i,level ->
            val z=3+i*4;val n=device(h,"electrical_fuse_holder",8,z);val p=n.parts[0]!!
            val cable=PartDefinition.byId.getValue(level+"_voltage_cable")
            PartInventory.get(n,p).setStackInSlot(0,material("lead_fuse_for_"+level+"_voltage_cables"))
            val total=2*PartDefinition.byId.getValue("very_high_voltage_cable").resistance+cable.resistance+.004001
            device(h,"electrical_source",9,z).parts[0]!!.value=4*cable.maxCurrent*total;device(h,"ground_cable",7,z);n
        }
        h.runAtTickTime(10) {
            for((i,n) in nodes.withIndex()) {
                val p=n.parts[0]!!;val cable=PartDefinition.byId.getValue(levels[i]+"_voltage_cable")
                if(!p.active || p.connectedMask!=5 || p.gridState.getDouble("protectionTemperature") !in 10.0..60.0 || abs((p.voltage-p.voltageB)/p.current-cable.resistance)>1e-6)h.fail("Original fuse was replaced by an instant current limit or lost calibrated branch/heating: ${p.save()}")
                reload(n)
            }
        }
        h.runAtTickTime(75) {
            for(n in nodes) {
                val p=n.parts[0]!!;val fuse=PartInventory.get(n,p).getStackInSlot(0)
                if(fuse.item!=Content.materialItems.getValue("blown_lead_fuse").get() || fuse.count!=1 || p.active || abs(p.current)>1e-6 || p.energy<=0 || h.level.getBlockState(n.blockPos).block!=Content.node.get())h.fail("Real fuse overload did not preserve its holder, leave exactly one blown fuse and interrupt the current: ${p.save()}")
            }
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_protection",template="grid_empty",timeoutTicks=110)
    fun actualFuseReplacementExtractionAndOrdinaryPickupConserveEveryInstalledItem(h: GameTestHelper) {
        val fixture=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+8,235,it.z+8) }
        var ready=false
        for(tick in 1L..80L)h.runAtTickTime(tick) {
            if(ready)return@runAtTickTime
            if(!h.level.isPositionEntityTicking(fixture)) {
                if(tick==80L)h.fail("Fuse pickup fixture never became entity-ticking for its actual dropped items")
                return@runAtTickTime
            }
            ready=true
        var n=device(h,"electrical_fuse_holder",8);val player=h.makeMockPlayer();player.abilities.instabuild=false
        val at=n.blockPos;h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(at).inflate(3.0)).forEach { it.discard() }
        fun click() {
            player.setPos(at.x+.5,236.0,at.z+2.5)
            val point=PartGeometry.body(n.parts[0]!!).center.add(Vec3.atLowerCornerOf(at))
            n.blockState.use(h.level,player,InteractionHand.MAIN_HAND,BlockHitResult(point,Direction.UP,at,false))
        }
        val low=material("lead_fuse_for_low_voltage_cables",2);player.setItemInHand(InteractionHand.MAIN_HAND,low);click()
        if(low.count!=1 || PartInventory.get(n,n.parts[0]!!).getStackInSlot(0).count!=1)h.fail("Actual fuse insertion did not consume exactly one source item")
        player.inventory.setItem(0,low.copy());player.setItemInHand(InteractionHand.MAIN_HAND,material("lead_fuse_for_medium_voltage_cables"));click()
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);click()
        val drops=h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(at).inflate(3.0))
        for(id in listOf("lead_fuse_for_low_voltage_cables","lead_fuse_for_medium_voltage_cables"))if(drops.sumOf { if(it.item.item==Content.materialItems.getValue(id).get())it.item.count else 0 }!=1)h.fail("Actual fuse replacement or empty-hand extraction lost/duplicated the old fuse: expected=$id, actual=${drops.map { it.item }}, installed=${PartInventory.get(n,n.parts[0]!!).getStackInSlot(0)}, entityTicking=${h.level.isPositionEntityTicking(at)}, tileDrops=${h.level.gameRules.getBoolean(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)}")
        if(!PartInventory.get(n,n.parts[0]!!).getStackInSlot(0).isEmpty)h.fail("Empty-hand extraction left an installed fuse")
        drops.forEach { it.discard() }
        player.setItemInHand(InteractionHand.MAIN_HAND,material("blown_lead_fuse"));click()
        h.level.destroyBlock(at,true)
        val carrier=h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(at).inflate(3.0))
        if(carrier.size!=1 || carrier.single().item.item!=Content.partItems.getValue("electrical_fuse_holder").get())h.fail("Holder break duplicated or lost its contained fuse")
        val held=carrier.single().item.copy();carrier.single().discard();player.setItemInHand(InteractionHand.MAIN_HAND,held)
        if(!held.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(at.below()),Direction.UP,at.below(),false))).consumesAction() || !held.isEmpty)h.fail("Picked-up fuse holder could not be placed in survival")
        n=h.level.getBlockEntity(at) as FaceNodeEntity
        val restored=PartInventory.get(n,n.parts[0]!!).getStackInSlot(0)
        if(restored.item!=Content.materialItems.getValue("blown_lead_fuse").get() || restored.count!=1)h.fail("Survival placement lost the exact contained blown fuse")
        h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_protection",template="grid_empty",timeoutTicks=145)
    fun originalBreakerRequiresAFittedCableAndUsesBothInputVoltageBoundsAcrossReload(h: GameTestHelper) {
        val n=device(h,"5a_electrical_breaker",8);var p=n.parts[0]!!
        val source=device(h,"electrical_source",9).parts[0]!!.apply { value=50.0 }
        device(h,"creative_power_resistor",7).parts[0]!!.value=20.0;device(h,"ground_cable",6)
        h.runAtTickTime(10) {
            if(p.enabled || p.connectedMask!=0 || !p.originalBreaker)h.fail("Original breaker did not start open without fitted conductor ports")
            PartInventory.get(n,p).setStackInSlot(0,ItemStack(Content.partItems.getValue("low_voltage_cable").get()));p.enabled=true
        }
        h.runAtTickTime(25) {
            if(p.enabled || !p.tripped || p.active || abs(p.current)>1e-6)h.fail("Original default five-volt maximum did not open the live power contact")
            p.expression="0,60";p.enabled=true;p.tripped=false;n.changedTopology()
        }
        h.runAtTickTime(45) {
            if(!p.active || p.connectedMask!=5 || abs(p.current-50/(20+.0375+.004001))>.01 || abs((p.voltage-p.voltageB)/p.current-.0125)>1e-6)h.fail("Fitted original breaker did not conduct through its calibrated terminal/contact resistances: ${p.save()}")
            reload(n);p=n.parts[0]!!
        }
        h.runAtTickTime(60) { if(!p.active || p.legacyBreaker)h.fail("Closed original breaker was reset on an unrelated topology rebuild");source.value=80.0 }
        h.runAtTickTime(75) { if(p.active || p.enabled || !p.tripped)h.fail("Maximum input voltage did not trip the original breaker");p.expression="60,100";p.enabled=true;p.tripped=false;n.changedTopology() }
        h.runAtTickTime(95) { if(!p.active)h.fail("Valid updated voltage window did not permit closing");source.value=40.0 }
        h.runAtTickTime(115) { if(p.active || p.enabled)h.fail("Minimum input voltage did not trip the original breaker");h.succeed() }
    }
    @JvmStatic @GameTest(batch="original_protection",template="grid_empty",timeoutTicks=110)
    fun originalBreakerThermalTripKeepsItsCableAndInventoryChangesOpenItWhilePreviewSavesStayCompatible(h: GameTestHelper) {
        val n=device(h,"5a_electrical_breaker",8);var p=n.parts[0]!!
        PartInventory.get(n,p).setStackInSlot(0,ItemStack(Content.partItems.getValue("low_voltage_cable").get()))
        p.expression="0,50";p.enabled=true
        device(h,"electrical_source",9).parts[0]!!.value=4*24*(3*.0125+.004001);device(h,"ground_cable",7)
        h.runAtTickTime(10) { if(!p.active || p.gridState.getDouble("protectionTemperature") !in 10.0..60.0)h.fail("Original breaker skipped its physical heating curve");reload(n);p=n.parts[0]!! }
        h.runAtTickTime(65) {
            if(p.enabled || p.active || !p.tripped || abs(p.current)>1e-6 || PartInventory.get(n,p).getStackInSlot(0).count!=1)h.fail("Original .8-warm-limit thermal breaker did not latch open with its reusable cable intact")
            p.enabled=true
            PartInventory.get(n,p).setStackInSlot(0,ItemStack(Content.partItems.getValue("high_voltage_cable").get()))
            if(p.enabled)h.fail("Replacing the breaker's physical conductor did not open its switch")
            val old=p.save().apply { putInt("componentDataVersion",6);remove("legacyBreaker");putBoolean("enabled",true) }
            val restored=PartState.load(old)!!
            if(!restored.legacyBreaker || restored.originalBreaker || PartInventory.slots(restored)!=0 || DeviceControls.expressionKey(restored)!=null || !PartState.load(restored.save())!!.legacyBreaker)h.fail("Preview breaker migration lost its prior manual adapter behavior")
        }
        h.runAtTickTime(85) { if(p.enabled || p.active)h.fail("Replaced-cable breaker closed without a user command");h.succeed() }
    }
}
