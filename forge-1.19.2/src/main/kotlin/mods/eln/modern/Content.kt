package mods.eln.modern

import net.minecraft.core.BlockPos
import net.minecraft.world.item.*
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.inventory.MenuType
import net.minecraftforge.common.extensions.IForgeMenuType
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.*

object Content {
    val blocks: DeferredRegister<Block> = DeferredRegister.create(ForgeRegistries.BLOCKS, "eln")
    val items: DeferredRegister<Item> = DeferredRegister.create(ForgeRegistries.ITEMS, "eln")
    val entities: DeferredRegister<BlockEntityType<*>> = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "eln")
    val actors: DeferredRegister<net.minecraft.world.entity.EntityType<*>> = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,"eln")
    val electricMinecart: RegistryObject<net.minecraft.world.entity.EntityType<ElectricMinecartEntity>> = actors.register("electric_minecart") {
        net.minecraft.world.entity.EntityType.Builder.of({ type,world -> ElectricMinecartEntity(type,world) },net.minecraft.world.entity.MobCategory.MISC).sized(.98f,.7f).clientTrackingRange(8).build("eln:electric_minecart")
    }
    val replicator: RegistryObject<net.minecraft.world.entity.EntityType<ReplicatorEntity>> = actors.register("replicator") {
        net.minecraft.world.entity.EntityType.Builder.of({ type,world -> ReplicatorEntity(type,world) },net.minecraft.world.entity.MobCategory.MONSTER).sized(.3f,.7f).clientTrackingRange(8).build("eln:replicator")
    }
    val replicatorEgg = items.register("replicator_spawn_egg") {
        net.minecraftforge.common.ForgeSpawnEggItem({ replicator.get() },0xFF0000,0xFF8000,Item.Properties().tab(tab))
    }
    val menus: DeferredRegister<MenuType<*>> = DeferredRegister.create(ForgeRegistries.MENU_TYPES, "eln")
    val node = blocks.register("six_node") { NodeBlock(false) }
    val machine = blocks.register("machine_node") { NodeBlock(true) }
    val computerProbe = blocks.register("computer_probe") { NodeBlock(true,true) }
    val gridGhost=blocks.register("grid_ghost") { mods.eln.modern.grid.GridGhostBlock() }
    val lightSpot=blocks.register("light_spot") { mods.eln.modern.lighting.LightSpotBlock() }
    val lightSpotEntity: RegistryObject<BlockEntityType<mods.eln.modern.lighting.LightSpotEntity>> = entities.register("light_spot") {
        BlockEntityType.Builder.of({ pos,state -> mods.eln.modern.lighting.LightSpotEntity(pos,state) },lightSpot.get()).build(null)
    }
    val gridGhostEntity: RegistryObject<BlockEntityType<mods.eln.modern.grid.GridGhostEntity>> = entities.register("grid_ghost") {
        BlockEntityType.Builder.of({ pos,state -> mods.eln.modern.grid.GridGhostEntity(pos,state) },gridGhost.get()).build(null)
    }
    val nodeEntity: RegistryObject<BlockEntityType<FaceNodeEntity>> = entities.register("node") {
        BlockEntityType.Builder.of(::FaceNodeEntity, node.get(), machine.get(),computerProbe.get()).build(null)
    }
    val tab = object : CreativeModeTab("eln") {
        override fun makeIcon() = ItemStack(partItems["electrical_source"]!!.get())
    }
    val ores = listOf("copper","lead","tungsten","cinnabar").associateWith { metal ->
        blocks.register("${metal}_ore") { net.minecraft.world.level.block.DropExperienceBlock(
            net.minecraft.world.level.block.state.BlockBehaviour.Properties.of(net.minecraft.world.level.material.Material.STONE)
                .strength(3f,3f).requiresCorrectToolForDrops()) }
    }
    val oreItems=ores.mapValues { (metal,block) -> items.register("${metal}_ore") { BlockItem(block.get(),Item.Properties().tab(tab)) } }
    val arcBlocks=listOf("arc_clay_block","arc_metal_block").associateWith { name ->
        blocks.register(name) { Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of(net.minecraft.world.level.material.Material.STONE).requiresCorrectToolForDrops()) }
    }
    val arcBlockItems=arcBlocks.mapValues { (name,block) -> items.register(name) { BlockItem(block.get(),Item.Properties().tab(tab)) } }
    val partItems: Map<String, RegistryObject<Item>> = PartDefinition.all.associate { definition ->
        definition.id to items.register(definition.id) { PartItem(definition, Item.Properties().tab(tab)) }
    }
    val materialItems = Materials.all.associate { definition ->
        definition.id to items.register(definition.id) {
            val properties=Item.Properties().stacksTo(if(definition.electrical!=null || definition.kind in PortableInstruments.kinds)1 else 64)
            if(!definition.hidden)properties.tab(tab)
            when { definition.kind=="ELECTRIC_MINECART"->ElectricMinecartItem(properties);definition.armor!=null->PortArmorItem(definition,properties);definition.kind=="COPPER_TOOL"->CopperTools.create(definition,properties);else->MaterialItem(definition,properties) }
        }
    }
    val wrench = items.register("wrench") { Item(Item.Properties().tab(tab).stacksTo(1)) }
    val multimeter = items.register("multimeter") { Item(Item.Properties().tab(tab).stacksTo(1)) }
    val thermometer = items.register("thermometer") { Item(Item.Properties().tab(tab).stacksTo(1)) }
    val deviceMenu: RegistryObject<MenuType<DeviceMenu>> = menus.register("device") {
        IForgeMenuType.create { id, inventory, data ->
            val pos = data.readBlockPos(); val face = data.readByte().toInt()
            DeviceMenu(id, inventory, inventory.player.level.getBlockEntity(pos) as FaceNodeEntity, face)
        }
    }
    @JvmStatic fun register(bus: IEventBus) {
        net.minecraftforge.common.crafting.CraftingHelper.register(ScannerCraftingCondition.SERIALIZER)
        net.minecraftforge.common.crafting.CraftingHelper.register(FestiveSeasonCondition.SERIALIZER)
        OriginalSounds.register(bus)
        mods.eln.modern.thermal.SteamFluids.register(bus)
        blocks.register(bus); items.register(bus); entities.register(bus);actors.register(bus); menus.register(bus)
        MachineRecipes.register(bus)
    }
}
