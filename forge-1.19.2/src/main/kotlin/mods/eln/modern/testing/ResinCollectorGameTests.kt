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
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object ResinCollectorGameTests {
    private fun tree(h: GameTestHelper): BlockPos {
        GameTestYards.clear(h,240)
        val base=h.absolutePos(BlockPos.ZERO);val trunk=BlockPos(base.x+3,240,base.z+4)
        for(y in 240..244) { h.level.setBlockAndUpdate(trunk.east().atY(y),Blocks.AIR.defaultBlockState());h.level.setBlockAndUpdate(trunk.atY(y),Blocks.OAK_LOG.defaultBlockState()) }
        h.level.setBlockAndUpdate(trunk.east().atY(243),Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT,true))
        h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(trunk).inflate(5.0)).forEach { it.discard() }
        return trunk
    }
    private fun collector(h: GameTestHelper,pos: BlockPos): FaceNodeEntity {
        h.level.setBlockAndUpdate(pos,Content.node.get().defaultBlockState())
        return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[4]=PartState(PartDefinition.byId.getValue("tree_resin_collector"),Direction.WEST).apply { rotation=3 };it.changedTopology() }
    }
    @JvmStatic @GameTest(batch="resin",template="grid_empty",timeoutTicks=110)
    fun originalTreeRateSharingHarvestCapAndSavedClock(h: GameTestHelper) {
        val trunk=tree(h);val node=collector(h,trunk.east().above());val player=h.makeMockPlayer()
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
        fun harvest(n: FaceNodeEntity) {
            val hit=BlockHitResult(Vec3.atCenterOf(n.blockPos),Direction.EAST,n.blockPos,false)
            val result=n.blockState.use(h.level,player,InteractionHand.MAIN_HAND,hit)
            if(!result.consumesAction())h.fail("Real tree-collector interaction did not collect its resin")
        }
        fun dropped()=h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(trunk).inflate(5.0)).filter { it.item.item==Content.materialItems.getValue("tree_resin").get() }.sumOf { it.item.count }
        h.runAtTickTime(20) {
            val p=node.parts[4]!!;val rate=ResinCollectorRuntime.rate(node,p)
            if(p.thermalState.getDouble("resinElapsed") !in .9..1.1 || abs(rate-3.0/(60*24))>1e-9)h.fail("Collector did not retain the original five-log production rate and real elapsed clock")
            val second=collector(h,trunk.east().above(2));val q=second.parts[4]!!
            if(abs(ResinCollectorRuntime.rate(node,p)-rate/2)>1e-12 || abs(ResinCollectorRuntime.rate(second,q)-rate/2)>1e-12)h.fail("Two pots on the same trunk side did not share its production")
            p.thermalState.putDouble("resinElapsed",1000.0);q.thermalState.putDouble("resinElapsed",1000.0)
            harvest(node);harvest(second)
            if(dropped()!=2)h.fail("Original per-pot collection did not yield one real resin item each")
            p.thermalState.putDouble("resinElapsed",100000.0);harvest(node);harvest(node)
            if(dropped()!=4 || p.thermalState.getDouble("resinElapsed")!=0.0)h.fail("Original two-item occupancy cap was exceeded or repeated interaction duplicated resin")
            p.thermalState.putDouble("resinElapsed",123.25)
            val saved=node.saveWithoutMetadata();node.load(saved);node.changedTopology()
            if(node.parts[4]!!.thermalState.getDouble("resinElapsed")!=123.25)h.fail("Collector clock failed save/reload")
        }
        h.runAtTickTime(50) {
            h.level.setBlockAndUpdate(trunk.east().atY(243),Blocks.AIR.defaultBlockState())
            val p=node.parts[4]!!
            if(ResinCollectorRuntime.rate(node,p)>1e-10)h.fail("Leafless trunk retained a living tree's production rate")
            p.thermalState.putDouble("resinElapsed",1000.0);val before=dropped();harvest(node)
            if(dropped()!=before || !p.thermalState.getDouble("resinElapsed").isFinite())h.fail("Leafless collector produced free resin or corrupted its clock")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="resin",template="grid_empty",timeoutTicks=70)
    fun survivalPlacementRequiresTheSideOfALogAndConsumesOneCraftedCollector(h: GameTestHelper) {
        val trunk=tree(h);val player=h.makeMockPlayer();player.abilities.instabuild=false
        val item=Content.partItems.getValue("tree_resin_collector").get();val held=ItemStack(item)
        player.setItemInHand(InteractionHand.MAIN_HAND,held)
        fun use(pos: BlockPos,face: Direction)=item.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(pos).add(face.stepX*.5,face.stepY*.5,face.stepZ*.5),face,pos,false)))
        if(use(trunk.above(4),Direction.UP).consumesAction() || held.count!=1)h.fail("Collector accepted a vertical log face")
        val stone=trunk.south(3);h.level.setBlockAndUpdate(stone,Blocks.STONE.defaultBlockState())
        if(use(stone,Direction.EAST).consumesAction() || held.count!=1)h.fail("Collector accepted a non-tree support")
        if(!use(trunk.above(),Direction.EAST).consumesAction() || !held.isEmpty)h.fail("Valid survival placement failed to consume exactly one collector")
        val node=h.level.getBlockEntity(trunk.east().above()) as? FaceNodeEntity ?: h.fail("Collector carrier was not placed").let { return }
        if(node.parts[4]?.definition?.behavior!=Behavior.RESIN_COLLECTOR || node.parts[4]!!.rotation!=3)h.fail("Collector did not retain the original upright side mounting")
        h.runAtTickTime(25) {
            h.level.setBlockAndUpdate(trunk.above(),Blocks.STONE.defaultBlockState())
            if(h.level.getBlockState(node.blockPos).block==Content.node.get())h.fail("Replacing the tree support left an invalid resin collector mounted")
            h.succeed()
        }
    }
}
