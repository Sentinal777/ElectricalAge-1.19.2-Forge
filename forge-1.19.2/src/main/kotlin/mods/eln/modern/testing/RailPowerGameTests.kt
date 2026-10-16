package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RailBlock
import net.minecraft.world.level.block.state.properties.RailShape
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object RailPowerGameTests {
    private class Yard(val h: GameTestHelper) {
        val base=h.absolutePos(BlockPos.ZERO)
        init { GameTestYards.clear(h,235,2,9);rails(8) }
        fun pos(x: Int,z: Int=8,y: Int=235)=BlockPos(base.x+x,y,base.z+z)
        fun rails(z: Int) { for(x in 2..14) { h.level.setBlockAndUpdate(pos(x,z).below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(pos(x,z),Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE,RailShape.EAST_WEST)) } }
        fun node(id: String,at: BlockPos): FaceNodeEntity {
            val d=PartDefinition.byId.getValue(id)
            h.level.setBlockAndUpdate(at,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
            return (h.level.getBlockEntity(at) as FaceNodeEntity).also { it.parts[0]=PartState(d,Direction.DOWN);it.changedTopology() }
        }
        fun wire(x: Int,voltage: Double): Pair<FaceNodeEntity,PartState> {
            val at=pos(x,y=237);val line=node("overhead_lines",at)
            h.level.setBlockAndUpdate(at.north(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(at.south().below(),Blocks.STONE.defaultBlockState())
            val source=node("electrical_source",at.south()).parts[0]!!.apply { value=voltage }
            return line to source
        }
        fun cart(x: Int=8,z: Int=8,energy: Double=0.0): ElectricMinecartEntity {
            return Content.electricMinecart.get().create(h.level)!!.also { it.setPos(pos(x,z).x+.5,235.0625,pos(x,z).z+.5);it.energyJ=energy;h.level.addFreshEntity(it) }
        }
    }
    @JvmStatic @GameTest(batch="original_electric_rail",template="grid_empty",timeoutTicks=185)
    fun originalOverheadThresholdAndRealChargingConserveEnergyThroughCutoffAndEntityReload(h: GameTestHelper) {
        val y=Yard(h);val (line,source)=y.wire(8,600.0);val p=line.parts[0]!!;var cart=y.cart()
        var held=0.0;val saved=CompoundTag()
        h.runAtTickTime(25) { if(cart.energyJ!=0.0 || RailPower.pending(cart)!=0.0)h.fail("Original cart charged below its 700 V minimum");source.value=800.0 }
        h.runAtTickTime(90) {
            val work=cart.energyJ+RailPower.pending(cart)
            if(work !in 8000.0..9900.0 || p.gridState.getInt("railCarts")!=1 || abs(p.energy-work-p.gridState.getDouble("railDissipatedJ"))>1e-5)h.fail("Cart charging replaced actual private-resistor work or lost its original 10 kJ/200 ohm curve: paid=${p.energy}, stored=$work")
            source.value=0.0
        }
        h.runAtTickTime(110) { held=cart.energyJ;cart.save(saved);cart.discard() }
        h.runAtTickTime(120) {
            cart=Content.electricMinecart.get().create(h.level)!!;cart.load(saved)
            if(!h.level.addFreshEntity(cart) || cart.energyJ!=held || cart.targetJ!=10000.0)h.fail("Electric cart lost its paid energy or target when reloading its real entity NBT")
        }
        h.runAtTickTime(145) { if(cart.energyJ!=held || RailPower.pending(cart)!=0.0 || p.active)h.fail("Disconnected/reloaded electric cart received phantom power");cart.discard();h.succeed() }
    }
    @JvmStatic @GameTest(batch="original_electric_rail",template="grid_empty",timeoutTicks=100)
    fun originalMotorSpendsAtMostFortyJoulesPerRailTickAndStopsProducingThrustWhenEmpty(h: GameTestHelper) {
        val y=Yard(h);y.rails(12)
        val powered=y.cart(4,8,200.0);val empty=y.cart(4,12)
        powered.deltaMovement=Vec3(.01,0.0,0.0);empty.deltaMovement=Vec3(.01,0.0,0.0)
        var old=powered.energyJ
        h.onEachTick { if(!powered.isRemoved) { val spent=old-powered.energyJ;if(spent<-.000001 || spent>40.000001)h.fail("Original electric motor spent more than its per-tick budget");old=powered.energyJ } }
        h.runAtTickTime(35) {
            if(powered.energyJ!=0.0 || powered.motorJ!=200.0 || empty.energyJ!=0.0 || empty.motorJ!=0.0 || powered.x<empty.x+2)h.fail("Original finite cart motor did not accelerate from its paid 40 J ticks or created work while empty")
            val velocity=powered.deltaMovement.horizontalDistance();h.runAfterDelay(15) {
                if(powered.motorJ!=200.0 || powered.energyJ!=0.0 || powered.deltaMovement.horizontalDistance()>velocity+.001)h.fail("Empty cart kept generating thrust")
                powered.discard();empty.discard();h.succeed()
            }
        }
    }
    @JvmStatic @GameTest(batch="original_electric_rail",template="grid_empty",timeoutTicks=100)
    fun changingAndRemovingActualOverheadContactsNeverDuplicatesPaidCartEnergy(h: GameTestHelper) {
        val y=Yard(h);val a=y.wire(5,800.0).first.parts[0]!!;val bNode=y.wire(11,900.0).first;val b=bNode.parts[0]!!;val cart=y.cart(5)
        var first=0.0;var stopped=0.0
        h.runAtTickTime(25) { if(cart.energyJ<=0)h.fail("First overhead contact did not charge the cart");cart.setPos(y.pos(11).x+.5,235.0625,y.pos(11).z+.5) }
        h.runAtTickTime(40) { first=a.energy }
        h.runAtTickTime(60) {
            val ledger=a.energy+b.energy-a.gridState.getDouble("railDissipatedJ")-b.gridState.getDouble("railDissipatedJ")
            if(a.energy!=first || b.energy<=0 || abs(ledger-cart.energyJ-RailPower.pending(cart)-cart.motorJ)>1e-5)h.fail("Cart contact change retained a phantom load or duplicated/lost already paid work")
            h.level.removeBlock(bNode.blockPos,false)
        }
        h.runAtTickTime(70) { stopped=cart.energyJ }
        h.runAtTickTime(85) { if(cart.energyJ!=stopped || RailPower.pending(cart)!=0.0)h.fail("Removed overhead contact continued charging a cart");cart.discard();h.succeed() }
    }
    @JvmStatic @GameTest(batch="original_electric_rail_items",template="grid_empty",timeoutTicks=80)
    fun actualRailPlacementRidingAndOrdinaryBreakPreserveTheElectricItemNameAndPaidEnergy(h: GameTestHelper) {
        val y=Yard(h);val at=y.pos(8);val player=h.makeMockPlayer();player.abilities.instabuild=false;player.setPos(at.x+.5,236.0,at.z+2.5)
        var cart: ElectricMinecartEntity?=null
        val item=ItemStack(Content.materialItems.getValue("electric_minecart").get(),2).apply { orCreateTag.putDouble("EnergyBufferJ",3500.0);orCreateTag.putDouble("EnergyBufferTargetJ",10000.0);setHoverName(net.minecraft.network.chat.Component.literal("QA locomotive")) }
        fun place(stack: ItemStack) { player.setItemInHand(InteractionHand.MAIN_HAND,stack);if(!stack.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(at),Direction.UP,at,false))).consumesAction())h.fail("Electric minecart item could not be used on an actual rail") }
        h.runAtTickTime(5) { place(item);if(item.count!=1)h.fail("Survival cart placement did not consume exactly one item") }
        h.runAtTickTime(15) {
            cart=h.level.getEntitiesOfClass(ElectricMinecartEntity::class.java,AABB(at).inflate(2.0)).singleOrNull()
            if(cart?.energyJ!=3500.0 || cart?.customName?.string!="QA locomotive")h.fail("Real cart spawn lost item energy or name")
            cart!!.hurt(DamageSource.GENERIC,100f)
        }
        h.runAtTickTime(25) {
            val drops=h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(at).inflate(3.0)).filter { it.item.item==Content.materialItems.getValue("electric_minecart").get() }
            if(drops.size!=1 || drops.single().item.count!=1 || drops.single().item.tag?.getDouble("EnergyBufferJ")!=3500.0 || drops.single().item.hoverName.string!="QA locomotive")h.fail("Ordinary cart break lost or duplicated its electric item, name or paid energy")
            val stack=drops.single().item.copy();drops.single().discard();place(stack)
            if(!stack.isEmpty)h.fail("Recovered electric cart item was not consumed on replacement")
        }
        h.runAtTickTime(40) {
            val restored=h.level.getEntitiesOfClass(ElectricMinecartEntity::class.java,AABB(at).inflate(2.0)).singleOrNull() ?: h.fail("Recovered electric cart was not spawned").let { return@runAtTickTime }
            if(restored.energyJ!=3500.0 || restored.targetJ!=10000.0)h.fail("Replanted electric cart lost its stored energy")
            if(!restored.interact(player,InteractionHand.MAIN_HAND).consumesAction() || player.vehicle!==restored)h.fail("Electric minecart could not be ridden through its actual interaction")
            player.stopRiding();restored.discard();h.succeed()
        }
    }
}
