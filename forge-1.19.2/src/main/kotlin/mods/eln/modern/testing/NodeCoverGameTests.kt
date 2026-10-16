package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.NbtUtils
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.event.level.BlockEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object NodeCoverGameTests {
    private fun node(h: GameTestHelper,id: String,x: Int,z: Int=4,rotation: Int=0)=InfrastructureGameTests.create(h,id,x,z,rotation)
    private fun hit(n: FaceNodeEntity)=BlockHitResult(Vec3.atCenterOf(n.blockPos),Direction.UP,n.blockPos,false)
    private fun fit(n: FaceNodeEntity,p: net.minecraft.world.entity.player.Player,stack: ItemStack): Boolean {
        p.setItemInHand(InteractionHand.MAIN_HAND,stack);NodeCovers.setModifier(p,true)
        return NodeCovers.interact(n,p,InteractionHand.MAIN_HAND,hit(n))?.consumesAction()==true
    }
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=110,batch="original_node_cover")
    fun originalCoverConsumesOneBlockKeepsActualCircuitAndSurvivesRealReloadWhileFirstBreakPeelsOnlyTheCover(h: GameTestHelper) {
        val source=node(h,"electrical_source",2);val cable=node(h,"low_voltage_cable",3)
        val load=node(h,"creative_power_resistor",4,rotation=2).parts[0]!!;node(h,"ground_cable",5)
        val player=FakePlayerFactory.getMinecraft(h.level);player.abilities.instabuild=false
        player.setPos(cable.blockPos.x+.5,cable.blockPos.y+1.0,cable.blockPos.z+2.0)
        val blocks=ItemStack(Items.OAK_PLANKS,3)
        if(!fit(cable,player,blocks) || blocks.count!=2 || !cable.blockState.getValue(NodeBlock.COVERED))h.fail("Original cover did not consume exactly one survival block")
        if(!net.minecraft.world.level.block.Block.isShapeFullBlock(cable.blockState.getCollisionShape(h.level,cable.blockPos)) || cable.blockState.getLightBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,cable.blockPos)!=15)h.fail("Covered carrier did not expose immutable full collision/opacity")
        if(PartPlacement.fits(PartState(PartDefinition.byId.getValue("signal_cable"),Direction.UP),cable))h.fail("A new component was inserted through the closed cover")
        h.runAtTickTime(25) {
            if(abs(load.current-.5)>.01 || !NodeCovers.present(cable))h.fail("Covering changed the actual electrical network")
            val saved=cable.saveWithoutMetadata();cable.load(saved);cable.changedTopology()
            if(NodeCovers.state(cable)?.block!==Blocks.OAK_PLANKS || NodeCovers.stack(cable).count!=1)h.fail("Cover state/item failed actual node reload")
        }
        h.runAtTickTime(45) {
            val before=h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(cable.blockPos).inflate(2.0)).map { it.uuid }.toSet()
            player.gameMode.destroyBlock(cable.blockPos)
            val drops=h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(cable.blockPos).inflate(2.0)).filter { it.uuid !in before }
            if(NodeCovers.present(cable) || h.level.getBlockEntity(cable.blockPos)!==cable || cable.parts[0]?.definition?.id!="low_voltage_cable" || drops.filter { it.item.`is`(Items.OAK_PLANKS) }.sumOf { it.item.count }!=1 || drops.any { it.item.item is PartItem })h.fail("First player break removed circuit hardware or duplicated/lost its cover")
            NodeCovers.setModifier(player,false)
        }
        h.runAtTickTime(70) { if(abs(load.current-.5)>.01 || source.parts[0]!!.energy<=0)h.fail("Peeling cover disconnected or reset the live circuit");h.succeed() }
    }
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=50,batch="original_node_cover")
    fun coverRetainsOriginalLogStateAndGlassOpacityRejectsContainersAndHonorsForgePlacementProtection(h: GameTestHelper) {
        val n=node(h,"signal_cable",4);val p=h.makeMockPlayer();p.abilities.instabuild=false
        val log=ItemStack(Items.OAK_LOG,2);log.getOrCreateTagElement("BlockStateTag").putString("axis","x")
        if(!fit(n,p,log) || NodeCovers.state(n)?.getValue(BlockStateProperties.AXIS)!=Direction.Axis.X || log.count!=1)h.fail("Cover lost original log state or inventory accounting")
        val tag=n.saveWithoutMetadata();n.load(tag);n.changedTopology()
        if(NodeCovers.state(n)?.getValue(BlockStateProperties.AXIS)!=Direction.Axis.X || !NodeCovers.peel(n,p))h.fail("Saved log cover could not be peeled")
        val glass=ItemStack(Items.GLASS)
        if(!fit(n,p,glass) || !glass.isEmpty || n.blockState.getLightBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,n.blockPos)!=0)h.fail("Glass cover incorrectly became an opaque solid material")
        NodeCovers.peel(n,p)
        for(item in listOf(Items.CHEST,Items.FURNACE,Items.OAK_STAIRS)) {
            val stack=ItemStack(item)
            if(fit(n,p,stack) || stack.count!=1 || NodeCovers.present(n))h.fail("Container or unsupported partial-shape data was copied into a cover")
        }
        val listener=object { @SubscribeEvent fun placed(e: BlockEvent.EntityPlaceEvent) { if(e.pos==n.blockPos)e.isCanceled=true } }
        MinecraftForge.EVENT_BUS.register(listener)
        try { val protected=ItemStack(Items.STONE,2);if(fit(n,p,protected) || protected.count!=2 || NodeCovers.present(n))h.fail("A canceled Forge placement still fitted or consumed cover material") }
        finally { MinecraftForge.EVENT_BUS.unregister(listener);NodeCovers.setModifier(p,false) }
        h.succeed()
    }
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=50,batch="original_node_cover")
    fun creativePeelingAndWholeCarrierRemovalNeverDuplicateCoverOrContainedPartsAndUnknownCoverDataSurvives(h: GameTestHelper) {
        val n=node(h,"signal_cable",4);val p=h.makeMockPlayer();p.abilities.instabuild=true
        val creative=ItemStack(Items.GLASS,2)
        if(!fit(n,p,creative) || creative.count!=2)h.fail("Creative cover placement consumed material")
        val before=h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(n.blockPos).inflate(2.0)).map { it.uuid }.toSet()
        if(!NodeCovers.peel(n,p) || h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(n.blockPos).inflate(2.0)).any { it.uuid !in before })h.fail("Creative peeling dropped free material")
        p.abilities.instabuild=false
        val stack=ItemStack(Items.STONE)
        if(!fit(n,p,stack))h.fail("Survival cover setup failed")
        val unknown=n.saveWithoutMetadata().apply { getCompound("nodeCoverState").putString("Name","missing_cover_mod:preserved_block");getCompound("nodeCoverItem").putString("id","missing_cover_mod:preserved_block") }
        n.load(unknown);n.changedTopology()
        if(n.saveWithoutMetadata().getCompound("nodeCoverState").getString("Name")!="missing_cover_mod:preserved_block" || NodeCovers.peel(n,p))h.fail("Unavailable cover identity was destroyed instead of retained")
        n.load(unknown.apply { put("nodeCoverState",NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState()));put("nodeCoverItem",ItemStack(Items.STONE).save(net.minecraft.nbt.CompoundTag())) });n.changedTopology()
        val seen=h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(n.blockPos).inflate(2.0)).map { it.uuid }.toSet()
        h.level.destroyBlock(n.blockPos,true)
        val drops=h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(n.blockPos).inflate(2.0)).filter { it.uuid !in seen }
        if(drops.filter { it.item.`is`(Items.STONE) }.sumOf { it.item.count }!=1 || drops.filter { it.item.item==Content.partItems.getValue("signal_cable").get() }.sumOf { it.item.count }!=1)h.fail("Whole-carrier removal lost or duplicated cover or circuit part")
        NodeCovers.setModifier(p,false);h.succeed()
    }
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=50,batch="original_node_cover")
    fun previewSignalFourTerminalMigrationSurvivesActualSurvivalPickupAndPlacement(h: GameTestHelper) {
        val n=node(h,"signal_trimmer",4);n.parts[0]!!.legacySimpleSignal=true;n.parts[0]!!.value=3.25;n.changedTopology()
        val p=h.makeMockPlayer();p.abilities.instabuild=false;p.setShiftKeyDown(true);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Content.wrench.get()))
        val seen=h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(n.blockPos).inflate(2.0)).map { it.uuid }.toSet()
        n.blockState.use(h.level,p,InteractionHand.MAIN_HAND,hit(n))
        val drops=h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(n.blockPos).inflate(2.0)).filter { it.uuid !in seen && it.item.item==Content.partItems.getValue("signal_trimmer").get() }
        if(drops.sumOf { it.item.count }!=1)h.fail("Preview signal pickup did not produce one configured item")
        val saved=drops.single().item.copy();drops.single().discard();p.setShiftKeyDown(false);p.setItemInHand(InteractionHand.MAIN_HAND,saved)
        val below=n.blockPos.below();val result=saved.useOn(UseOnContext(p,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(below).add(0.0,.5,0.0),Direction.UP,below,false)))
        val placed=h.level.getBlockEntity(n.blockPos) as? FaceNodeEntity ?: error("Preview signal did not place")
        if(!result.consumesAction() || !saved.isEmpty || placed.parts[0]?.legacySimpleSignal!=true || placed.parts[0]?.value!=3.25)h.fail("Actual survival pickup/placement lost original preview wiring or trimmer setting")
        h.succeed()
    }
}
