package mods.eln.modern.testing

import mods.eln.modern.*
import mods.eln.modern.thermal.SteamFluids
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.material.Fluids
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object MachinePickupGameTests {
    private fun fixture(h: GameTestHelper,definition: PartDefinition,x: Int): FaceNodeEntity {
        GameTestYards.clear(h,230)
        val base=h.absolutePos(BlockPos.ZERO);val pos=BlockPos(base.x+x,230,base.z+4)
        h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(pos).inflate(2.0)).forEach { it.discard() }
        h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
        h.level.setBlockAndUpdate(pos,Content.machine.get().defaultBlockState())
        return (h.level.getBlockEntity(pos) as FaceNodeEntity).also {
            it.parts[0]=PartState(definition,Direction.DOWN).apply { enabled=false };it.changedTopology()
        }
    }
    private fun pickAndPlace(h: GameTestHelper,node: FaceNodeEntity,wrench: Boolean): FaceNodeEntity {
        val pos=node.blockPos;val definition=node.parts[0]!!.definition;val player=h.makeMockPlayer()
        player.abilities.instabuild=false
        if(wrench) {
            player.setShiftKeyDown(true);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Content.wrench.get()))
            node.blockState.use(h.level,player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false))
        }else h.level.destroyBlock(pos,true)
        val drops=h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(pos).inflate(2.0)).filter { it.item.item==Content.partItems.getValue(definition.id).get() }
        if(drops.sumOf { it.item.count }!=1 || !h.level.getBlockState(pos).isAir)h.fail("Machine pickup did not remove one machine and drop exactly one item")
        val stack=drops.single().item.copy();drops.single().discard();player.setShiftKeyDown(false)
        player.setItemInHand(InteractionHand.MAIN_HAND,stack)
        val support=pos.below()
        val result=(stack.item as PartItem).useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(support).add(0.0,.5,0.0),Direction.UP,support,false)))
        if(!result.consumesAction() || !stack.isEmpty)h.fail("Stored machine did not place and consume its survival item")
        return h.level.getBlockEntity(pos) as? FaceNodeEntity ?: error("Machine carrier missing after placement")
    }
    @JvmStatic @GameTest(batch="machine_pickup",template="grid_empty",timeoutTicks=40)
    fun wrenchAndBlockBreakPreserveFiniteTanksAndFractionalConverterEnergy(h: GameTestHelper) {
        var steam=fixture(h,PartDefinition.byId.getValue("steam_turbine"),3)
        steam.tank.fill(FluidStack(SteamFluids.steam.get(),4321),FluidAction.EXECUTE)
        steam.parts[0]!!.fuelEnergy=12.75;steam.parts[0]!!.thermalState.putDouble("turbineChamberHeat",987.125)
        steam.inventory.setStackInSlot(0,ItemStack(Items.IRON_INGOT,3))
        var converter=fixture(h,PartDefinition.all.first { it.behavior==Behavior.FE_CONVERTER },8)
        converter.energyBuffer.energy=12345.625
        var exchanger=fixture(h,PartDefinition.all.first { it.behavior==Behavior.HEAT_EXCHANGER },13)
        exchanger.exchanger.input.fill(FluidStack(Fluids.WATER,789),FluidAction.EXECUTE)
        exchanger.exchanger.output.fill(FluidStack(SteamFluids.steam.get(),456),FluidAction.EXECUTE)
        for(wrench in listOf(true,false)) {
            steam=pickAndPlace(h,steam,wrench);converter=pickAndPlace(h,converter,wrench);exchanger=pickAndPlace(h,exchanger,wrench)
            val p=steam.parts[0]!!
            if(steam.tank.fluidAmount!=4321 || p.fuelEnergy!=12.75 || p.thermalState.getDouble("turbineChamberHeat")!=987.125)h.fail("Pickup lost or duplicated turbine fluid or finite chamber energy")
            if(converter.energyBuffer.energy!=12345.625)h.fail("Converter pickup rounded or lost fractional stored energy")
            if(exchanger.exchanger.input.fluidAmount!=789 || exchanger.exchanger.output.fluidAmount!=456)h.fail("Heat exchanger pickup lost one of its two separate fluids")
            if((0 until steam.inventory.slots).any { !steam.inventory.getStackInSlot(it).isEmpty })h.fail("Carrier inventory was duplicated inside the picked-up item")
            val iron=h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(steam.blockPos).inflate(2.0)).filter { it.item.`is`(Items.IRON_INGOT) }.sumOf { it.item.count }
            if(iron!=3)h.fail("Machine pickup lost or duplicated the separately dropped inventory")
        }
        h.succeed()
    }
    @JvmStatic @GameTest(batch="machine_pickup",template="grid_empty",timeoutTicks=40)
    fun unavailableModFluidsSurvivePickupAndOversizedItemStorageIsBounded(h: GameTestHelper) {
        var steam=fixture(h,PartDefinition.byId.getValue("steam_turbine"),3)
        val missing=CompoundTag().apply { putString("FluidName","eln_missing_test:unavailable_steam");putInt("Amount",5000);put("Tag",CompoundTag().apply { putString("batch","retained") }) }
        val saved=steam.saveWithoutMetadata();saved.put("fluid",missing.copy());steam.load(saved);steam.changedTopology()
        steam=pickAndPlace(h,steam,true)
        val recovered=steam.saveWithoutMetadata().getCompound("fluid")
        if(recovered!=missing || !steam.tank.isEmpty)h.fail("Picking up an unavailable-mod fluid erased its identity, amount or custom data")
        val def=PartDefinition.all.first { it.behavior==Behavior.FE_CONVERTER };val converter=fixture(h,def,8)
        converter.restoreItemStorage(converter.parts[0]!!,CompoundTag().apply { putDouble("feEnergy",Double.POSITIVE_INFINITY) })
        if(converter.energyBuffer.energy!=0.0)h.fail("Nonfinite item energy entered the converter")
        steam.restoreItemStorage(steam.parts[0]!!,CompoundTag().apply { put("fluid",missing.copy().apply { putInt("Amount",Int.MAX_VALUE) }) })
        if(steam.saveWithoutMetadata().getCompound("fluid").getInt("Amount")!=7200)h.fail("Oversized item tank exceeded the original machine capacity")
        h.succeed()
    }
}
