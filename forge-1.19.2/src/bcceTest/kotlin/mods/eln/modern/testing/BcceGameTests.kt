package mods.eln.modern.testing

import buildcraft.api.v2.BuildCraftApi
import buildcraft.api.v2.BuildCraftServices
import buildcraft.core.BCCoreBlocks
import buildcraft.core.blockEntity.TileEngineCreative
import buildcraft.lib.internal.enums.EnumEngineType
import buildcraft.lib.internal.properties.BuildCraftProperties
import buildcraft.lib.internal.mj.MjCapabilities
import buildcraft.transport.BCTransportBlocks
import buildcraft.transport.BCTransportItems
import buildcraft.transport.pipe.behaviour.PipeBehaviourWood
import buildcraft.transport.tile.TilePipeHolder
import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import net.minecraftforge.registries.ForgeRegistries
import kotlin.math.abs

/** Compiled only with -PbcceIntegration. These operate published BCCE tiles and real pipe flows. */
@GameTestHolder("eln")
@PrefixGameTestTemplate(false)
object BcceGameTests {
    private fun face(wood: PipeBehaviourWood,direction: Direction) {
        repeat(6) { if(wood.currentDir==direction)return;wood.advanceFacing() }
        check(wood.currentDir==direction) { "Wooden pipe cannot face connected inventory" }
    }
    private fun check(h: GameTestHelper,condition: Boolean,message: String) { if(!condition)h.fail(message) }
    private fun pipe(h: GameTestHelper,x: Int,y: Int,z: Int,stack: ItemStack): TilePipeHolder {
        h.setBlock(x,y,z,BCTransportBlocks.pipeHolder.get())
        val tile=h.getBlockEntity(BlockPos(x,y,z)) as TilePipeHolder
        tile.onPlacedBy(h.makeMockPlayer(),stack);tile.onLoad();return tile
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=145)
    fun liquidHeatFurnaceUsesItsPreciseTankBurnerAndFiniteBuildcraftFuel(h: GameTestHelper) {
        val fuel=ForgeRegistries.FLUIDS.values.firstOrNull { val name=ForgeRegistries.FLUIDS.getKey(it)!!;name.namespace.startsWith("buildcraft") && name.path.startsWith("fuel_dense") } ?: error("No published BCCE dense fuel")
        check(h,FuelFluids.accepts(Behavior.FUEL_HEAT_FURNACE,fuel) && !FuelFluids.accepts(Behavior.FUEL_GENERATOR,fuel),"Diesel compatibility leaked into the gasoline generator")
        val heater=InfrastructureGameTests.create(h,"fuel_heat_furnace",2)
        val p=heater.parts[0]!!;val inventory=PartInventory.get(heater,p)
        inventory.setStackInSlot(0,ItemStack(Content.materialItems.getValue("small_fuel_burner").get()))
        val fluids=heater.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow { IllegalStateException("No liquid heater fluid capability") }
        check(h,fluids.getTankCapacity(0)==25,"Liquid heater lost its original 25 mB tank capacity")
        check(h,fluids.fill(FluidStack(fuel,100),FluidAction.SIMULATE)==25 && heater.tank.isEmpty,"Fill simulation mutated the precise fuel tank")
        check(h,fluids.fill(FluidStack(fuel,100),FluidAction.EXECUTE)==25,"Liquid heater accepted more than its original tank capacity")
        check(h,inventory.insertItem(1,ItemStack(Items.DIAMOND),false).count==1,"Heater accepted an invalid regulator")
        p.value=.25;p.enabled=true
        val original=25*FuelFluids.joulesPerMb(fuel)
        var stopped=0.0
        h.runAtTickTime(50) {
            check(h,p.energy in 3000.0..3300.0 && p.thermalState.getDouble("heatWatts")==1250.0,"Installed small burner did not produce its original controlled power: ${p.energy} J / ${p.thermalState.getDouble("heatWatts")} W")
            check(h,abs(p.energy+p.fuelEnergy+heater.tank.fluidAmount*FuelFluids.joulesPerMb(fuel)-original)<1e-6,"Fractional liquid heating created or discarded fuel energy")
            val saved=heater.saveWithoutMetadata();heater.load(saved);heater.changedTopology()
        }
        h.runAtTickTime(80) {
            val restored=heater.parts[0]!!
            check(h,abs(restored.energy+restored.fuelEnergy+heater.tank.fluidAmount*FuelFluids.joulesPerMb(fuel)-original)<1e-6,"Liquid heater reload lost fractional fuel")
            restored.enabled=false;stopped=restored.energy;heater.changedTopology()
        }
        h.runAtTickTime(110) {
            check(h,abs(heater.parts[0]!!.energy-stopped)<1e-9,"Disabled liquid heater continued burning fuel")
            check(h,heater.parts[0]!!.thermalState.getDouble("delta")>20,"Liquid fuel did not produce real stored heat")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=190)
    fun buildcraftFluidPipeFeedsInstalledLiquidHeatBurner(h: GameTestHelper) {
        val fuel=ForgeRegistries.FLUIDS.values.firstOrNull { FuelFluids.accepts(Behavior.FUEL_HEAT_FURNACE,it) } ?: error("No heater fuel")
        val tank=ForgeRegistries.BLOCKS.getValue(ResourceLocation("buildcraftfactory","tank")) ?: error("No BCCE tank")
        h.setBlock(1,2,2,tank)
        val source=h.getBlockEntity(BlockPos(1,2,2))!!.getCapability(ForgeCapabilities.FLUID_HANDLER,Direction.EAST).orElseThrow { IllegalStateException("Missing tank capability") }
        source.fill(FluidStack(fuel,1000),FluidAction.EXECUTE)
        val tube=pipe(h,2,2,2,ItemStack(BCTransportItems.PIPE_FLUID_WOOD.get()))
        val heater=InfrastructureGameTests.create(h,"fuel_heat_furnace",3)
        val p=heater.parts[0]!!;PartInventory.get(heater,p).setStackInSlot(0,ItemStack(Content.materialItems.getValue("small_fuel_burner").get()))
        p.value=.25;p.enabled=true
        h.runAtTickTime(8) {
            val wood=tube.pipe.behaviour as PipeBehaviourWood;face(wood,Direction.WEST)
            check(h,wood.receivePower(100000000L,FluidAction.EXECUTE)<100000000L,"Actual BCCE wooden pipe failed to extract heater fuel")
        }
        h.runAtTickTime(150) {
            check(h,p.energy>1000 && p.thermalState.getDouble("delta")>20,"Imported BCCE fuel failed to heat the burner")
            check(h,heater.tank.fluidAmount in 0..25,"Pipe bypassed the heater's precise tank capacity")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=1000)
    fun coalHeatExchangerSteamPipeTurbineAndGeneratorPowerAnElectricalLoad(h: GameTestHelper) {
        val furnace=InfrastructureGameTests.create(h,"stone_heat_furnace",1,rotation=2)
        val heat=furnace.parts[0]!!;val inventory=PartInventory.get(furnace,heat)
        inventory.setStackInSlot(0,ItemStack(Items.COAL,2))
        inventory.setStackInSlot(3,ItemStack(Content.materialItems.getValue("combustion_chamber").get(),8))
        heat.enabled=true
        InfrastructureGameTests.create(h,"copper_thermal_cable",2)
        val exchanger=InfrastructureGameTests.create(h,"thermal_heat_exchanger",3)
        InfrastructureGameTests.create(h,"signal_source",4)
        exchanger.getCapability(ForgeCapabilities.FLUID_HANDLER,Direction.DOWN).orElseThrow { IllegalStateException("Missing water input") }
            .fill(FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000),FluidAction.EXECUTE)
        val tube=pipe(h,3,3,2,ItemStack(BCTransportItems.PIPE_FLUID_WOOD.get()))
        h.setBlock(2,3,2,BCCoreBlocks.ENGINE_BC8.get().defaultBlockState().setValue(BuildCraftProperties.ENGINE_TYPE,EnumEngineType.CREATIVE))
        val pump=h.getBlockEntity(BlockPos(2,3,2)) as TileEngineCreative
        pump.onPlacedBy(h.makeMockPlayer(),ItemStack(BCCoreBlocks.ENGINE_BC8.get()));pump.currentOutputIndex=8
        check(h,pump.currentFacing==Direction.EAST,"BuildCraft pump engine did not face its real wooden-pipe receiver")
        h.setBlock(2,3,1,Blocks.REDSTONE_BLOCK)
        fun upper(id: String,x: Int): FaceNodeEntity {
            h.setBlock(x,2,3,Blocks.STONE);h.setBlock(x,3,3,Content.machine.get())
            val node=h.getBlockEntity(BlockPos(x,3,3)) as FaceNodeEntity
            node.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { rotation=1 };node.changedTopology();return node
        }
        val turbine=upper("steam_turbine",3);val generator=upper("generator",4)
        h.setBlock(4,2,4,Blocks.STONE);h.setBlock(4,3,4,Content.node.get())
        val load=h.getBlockEntity(BlockPos(4,3,4)) as FaceNodeEntity
        load.parts[0]=PartState(PartDefinition.byId.getValue("creative_power_resistor"),Direction.DOWN).apply { rotation=3 };load.changedTopology()
        h.setBlock(4,2,5,Blocks.STONE);h.setBlock(4,3,5,Content.node.get())
        val ground=h.getBlockEntity(BlockPos(4,3,5)) as FaceNodeEntity
        ground.parts[0]=PartState(PartDefinition.byId.getValue("ground_cable"),Direction.DOWN);ground.changedTopology()
        var earlierWork=0.0
        h.runAtTickTime(8) {
            val wood=tube.pipe.behaviour as PipeBehaviourWood;face(wood,Direction.DOWN)
        }
        h.runAtTickTime(110) {
            check(h,exchanger.parts[0]!!.thermalState.getDouble("convertedHeatJ")<0,"Coal furnace did not produce real steam from water")
        }
        h.runAtTickTime(650) {
            earlierWork=load.parts[0]!!.energy
            mods.eln.Eln.LOGGER.info("ELN_STEAM_CHAIN startup load={}J steamWork={}J intake={}mB/s shaft={}rad/s",earlierWork,turbine.parts[0]!!.energy,turbine.parts[0]!!.thermalState.getDouble("turbineRate"),turbine.parts[0]!!.shaftSpeed)
            check(h,earlierWork>0 && generator.parts[0]!!.energy>0,"Steam failed to perform real electrical work")
        }
        h.runAtTickTime(900) {
            val p=load.parts[0]!!
            val steam=turbine.parts[0]!!;val gen=generator.parts[0]!!
            mods.eln.Eln.LOGGER.info("ELN_STEAM_CHAIN load={}J/{}A speed={}rad/s steamWork={}J generated={}J input={}mB output={}mB",p.energy,p.current,steam.shaftSpeed,steam.energy,gen.energy,exchanger.exchanger.input.fluidAmount,exchanger.exchanger.output.fluidAmount)
            // Wood supplies about 10 mB/t to this single port, and the original steam rotor has
            // ~0.29% efficiency at rest and a twenty-second intake lag. Let this deliberately
            // undersupplied plant settle, then verify sustained work and complete conservation.
            check(h,p.energy>earlierWork && p.current>0,"Powered pipe failed to sustain steam-driven electrical output")
            check(h,abs((p.voltage-p.voltageB)/p.value-p.current)<1e-5,"Generated voltage/current violated the actual load's Ohm relation")
            check(h,steam.shaftSpeed>0 && abs(steam.shaftSpeed-gen.shaftSpeed)<1e-9,"Connected turbine/generator shafts did not share angular speed")
            check(h,steam.energy+1e-6>=gen.energy/.95+steam.shaftEnergy+gen.shaftEnergy,"Steam-to-shaft-to-electrical conversion created energy")
            check(h,p.energy<=gen.energy+1e-6,"The electrical load received more work than its generator produced")
            check(h,exchanger.exchanger.input.fluidAmount<1000 && exchanger.parts[0]!!.thermalState.getDouble("convertedHeatJ")<0,"Steam did not originate in finite water and transferred furnace heat")
            check(h,abs(heat.energy+heat.fuelEnergy+heat.thermalState.getDouble("fuelBuffer")+PartInventory.get(furnace,heat).getStackInSlot(0).count*80000-160000)<1e-5,"Coal-to-steam generation duplicated fuel")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=80)
    fun nativeMjSimulationLimitsAndPersistence(h: GameTestHelper) {
        val node=InfrastructureGameTests.create(h,"energy_converter",2)
        node.parts[0]!!.value=1.0;node.changedTopology()
        val receiver=node.getCapability(MjCapabilities.CAP_RECEIVER,Direction.UP).orElseThrow { IllegalStateException("No native MJ receiver") }
        val ratio=BuildCraftApi.service(BuildCraftServices.ENERGY).conversion().microMjPerFe()
        check(h,receiver.receivePower(500*ratio,FluidAction.SIMULATE)==0L && node.feStored==0,"MJ simulation created energy")
        check(h,receiver.receivePower(500*ratio,FluidAction.EXECUTE)==0L && node.feStored==500,"MJ import used wrong configured conversion")
        check(h,receiver.receivePower(1000*ratio,FluidAction.EXECUTE)==500*ratio,"MJ adapter bypassed shared transfer limit")
        val saved=node.saveWithoutMetadata();node.load(saved)
        check(h,node.feStored==1000,"Native MJ buffer did not survive NBT reload")
        node.parts[0]!!.value=0.0;node.changedTopology()
        val provider=node.getCapability(MjCapabilities.CAP_PASSIVE_PROVIDER).orElseThrow { IllegalStateException("No native MJ provider") }
        check(h,provider.extractPower(1,500*ratio,false)==500*ratio && node.feStored==1000,"MJ extraction preview mutated state")
        check(h,provider.extractPower(1,500*ratio,true)==500*ratio && node.feStored==500,"MJ extraction created or lost energy")
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=260)
    fun buildcraftEnginePowersElectricalLoad(h: GameTestHelper) {
        val converter=InfrastructureGameTests.create(h,"energy_converter",2)
        converter.parts[0]!!.value=1.0;converter.changedTopology()
        val resistor=InfrastructureGameTests.create(h,"creative_power_resistor",3,rotation=2).parts[0]!!
        InfrastructureGameTests.create(h,"ground_cable",4)
        h.setBlock(2,1,2,BCCoreBlocks.ENGINE_BC8.get().defaultBlockState().setValue(BuildCraftProperties.ENGINE_TYPE,EnumEngineType.CREATIVE))
        val engine=h.getBlockEntity(BlockPos(2,1,2)) as TileEngineCreative
        engine.currentOutputIndex=3
        h.setBlock(1,1,2,Blocks.REDSTONE_BLOCK)
        h.runAtTickTime(220) {
            check(h,converter.feStored>0,"Actual BCCE engine supplied no MJ")
            check(h,resistor.energy>10 && resistor.current>.45,"BCCE engine failed to drive ELN voltage/current simulation")
            h.setBlock(2,1,2,Blocks.AIR)
        }
        h.runAtTickTime(240) { check(h,converter.feStored>=0,"Engine disconnection corrupted buffer");h.succeed() }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=80)
    fun electricalConverterPowersBuildcraftMachine(h: GameTestHelper) {
        InfrastructureGameTests.create(h,"electrical_source",1)
        val converter=InfrastructureGameTests.create(h,"energy_converter",2)
        val quarry=ForgeRegistries.BLOCKS.getValue(ResourceLocation("buildcraftbuilders","quarry")) ?: error("Published BCCE quarry missing")
        h.setBlock(3,2,2,quarry)
        val tile=h.getBlockEntity(BlockPos(3,2,2)) as buildcraft.builders.tile.TileQuarry
        tile.onPlacedBy(h.makeMockPlayer(),ItemStack(quarry))
        h.runAtTickTime(30) {
            val readable=tile.getCapability(MjCapabilities.CAP_READABLE).orElseThrow { IllegalStateException("No quarry MJ readout") }
            check(h,readable.stored>0,"ELN converter did not supply MJ to actual BCCE quarry")
            check(h,converter.parts[0]!!.energy>0,"Quarry power did not originate in electrical circuit")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=400)
    fun buildcraftPipeSuppliesPoweredFurnace(h: GameTestHelper) {
        h.setBlock(1,3,2,Blocks.CHEST)
        val chest=h.getBlockEntity(BlockPos(1,3,2)) as ChestBlockEntity;chest.setItem(0,ItemStack(Items.RAW_IRON))
        val tube=pipe(h,2,3,2,ItemStack(BCTransportItems.PIPE_ITEM_WOOD.get()))
        val furnace=InfrastructureGameTests.create(h,"electrical_furnace",3,rotation=3)
        pipe(h,3,3,2,ItemStack(BCTransportItems.PIPE_ITEM_STONE.get()))
        PartInventory.get(furnace,furnace.parts[0]!!).setStackInSlot(0,ItemStack(Content.materialItems.getValue("800v_tungsten_heating_element").get()))
        InfrastructureGameTests.create(h,"electrical_source",4).parts[0]!!.value=800.0
        h.runAtTickTime(8) {
            val wood=tube.pipe.behaviour as PipeBehaviourWood;face(wood,Direction.WEST)
            val excess=wood.receivePower(100000000L,FluidAction.EXECUTE)
            check(h,excess<100000000L,"Wooden pipe did not extract chest input")
        }
        h.runAtTickTime(360) {
            check(h,chest.getItem(0).isEmpty && furnace.inventory.getStackInSlot(2).`is`(Items.IRON_INGOT),"Actual BCCE pipe failed automated ELN smelting")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=180)
    fun buildcraftPipeExtractsOnlyMachineOutput(h: GameTestHelper) {
        val furnace=InfrastructureGameTests.create(h,"electrical_furnace",3)
        furnace.inventory.setStackInSlot(0,ItemStack(Items.RAW_IRON));furnace.inventory.setStackInSlot(2,ItemStack(Items.IRON_INGOT))
        val tube=pipe(h,3,1,2,ItemStack(BCTransportItems.PIPE_ITEM_WOOD.get()))
        h.setBlock(4,1,2,Blocks.CHEST);val chest=h.getBlockEntity(BlockPos(4,1,2)) as ChestBlockEntity
        h.runAtTickTime(8) {
            val wood=tube.pipe.behaviour as PipeBehaviourWood;face(wood,Direction.UP)
            check(h,wood.receivePower(100000000L,FluidAction.EXECUTE)<100000000L,"Pipe failed to extract output from underside")
        }
        h.runAtTickTime(140) {
            check(h,furnace.inventory.getStackInSlot(0).`is`(Items.RAW_IRON),"BCCE stole machine input")
            check(h,furnace.inventory.getStackInSlot(2).isEmpty && (0 until chest.containerSize).any { chest.getItem(it).`is`(Items.IRON_INGOT) },"BCCE pipe failed output delivery")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=180)
    fun buildcraftFluidPipeFeedsFuelGenerator(h: GameTestHelper) {
        val fuel=ForgeRegistries.FLUIDS.values.firstOrNull { FuelFluids.accepts(Behavior.FUEL_GENERATOR,it) } ?: error("Published BCCE has no compatible fuel")
        val tankBlock=ForgeRegistries.BLOCKS.getValue(ResourceLocation("buildcraftfactory","tank")) ?: error("No BCCE tank")
        h.setBlock(1,2,2,tankBlock)
        val source=h.getBlockEntity(BlockPos(1,2,2))!!.getCapability(ForgeCapabilities.FLUID_HANDLER,Direction.EAST).orElseThrow { IllegalStateException("No BCCE tank capability") }
        source.fill(FluidStack(fuel,1000),FluidAction.EXECUTE)
        val tube=pipe(h,2,2,2,ItemStack(BCTransportItems.PIPE_FLUID_WOOD.get()))
        val generator=InfrastructureGameTests.create(h,"48v_fuel_generator",3)
        val resistor=InfrastructureGameTests.create(h,"creative_power_resistor",4,rotation=2).parts[0]!!
        InfrastructureGameTests.create(h,"ground_cable",5)
        h.runAtTickTime(8) {
            val wood=tube.pipe.behaviour as PipeBehaviourWood;face(wood,Direction.WEST)
            check(h,wood.receivePower(100000000L,FluidAction.EXECUTE)<100000000L,"Wooden fluid pipe did not extract fuel")
        }
        h.runAtTickTime(60) {
            check(h,generator.tank.fluidAmount>0 && !generator.parts[0]!!.enabled,"Actual pipe did not fill the original manually started generator")
            val player=h.makeMockPlayer();player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY)
            generator.blockState.use(h.level,player,net.minecraft.world.InteractionHand.MAIN_HAND,
                net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(generator.blockPos),Direction.UP,generator.blockPos,false))
            check(h,generator.parts[0]!!.enabled,"Original generator right-click failed after receiving piped BuildCraft gasoline")
        }
        h.runAtTickTime(140) {
            check(h,generator.tank.fluidAmount>0,"Actual fluid pipe did not fill ELN generator")
            check(h,resistor.current>.45 && resistor.energy>1,"Fuel generator did not consume imported fuel and power electrical load")
            val amount=generator.tank.fluidAmount;val saved=generator.saveWithoutMetadata();generator.load(saved)
            check(h,generator.tank.fluidAmount==amount,"Fluid save/reload lost stored fuel")
            h.succeed()
        }
    }
}
