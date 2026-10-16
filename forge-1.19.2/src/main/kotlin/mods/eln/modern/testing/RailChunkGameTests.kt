package mods.eln.modern.testing

import mods.eln.Eln
import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RailBlock
import net.minecraft.world.level.block.state.properties.RailShape
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object RailChunkGameTests {
    @JvmStatic @GameTest(batch="original_rail_chunk",template="grid_empty",timeoutTicks=1000)
    fun cartStopsChargingBeforeEntitySleepThenSurvivesAnActualUnloadAndStaleCartCallback(h: GameTestHelper) {
        val level=h.level;val base=h.absolutePos(BlockPos.ZERO)
        val chunk=ChunkPos((base.x shr 4)+2000,(base.z shr 4)+2000)
        val rail=BlockPos(chunk.minBlockX+8,245,chunk.minBlockZ+8);val wire=rail.above(2)
        fun ticket(distance: Int,on: Boolean) {
            if(on)level.chunkSource.addRegionTicket(TicketType.FORCED,chunk,distance,chunk)
            else level.chunkSource.removeRegionTicket(TicketType.FORCED,chunk,distance,chunk)
        }
        fun node(at: BlockPos)=level.chunkSource.getChunkNow(chunk.x,chunk.z)?.getBlockEntity(at) as? FaceNodeEntity
        fun check(value: Boolean,message: String) { if(!value)h.fail(message) }
        fun ensure(value: Boolean,message: String) { if(!value) { ticket(2,false);ticket(1,false);h.fail(message) } }
        ticket(2,true);level.getChunk(chunk.x,chunk.z)
        for(x in 5..11)for(z in 6..10)for(y in 244..250)level.setBlockAndUpdate(BlockPos(chunk.minBlockX+x,y,chunk.minBlockZ+z),Blocks.AIR.defaultBlockState())
        for(x in -2..2) { level.setBlockAndUpdate(rail.offset(x,-1,0),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(rail.offset(x,0,0),Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE,RailShape.EAST_WEST)) }
        level.setBlockAndUpdate(wire,Content.machine.get().defaultBlockState())
        node(wire)!!.apply { parts[0]=PartState(PartDefinition.byId.getValue("overhead_lines"),Direction.DOWN);changedTopology() }
        val source=wire.south();level.setBlockAndUpdate(source.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(source,Content.node.get().defaultBlockState())
        node(source)!!.apply { parts[0]=PartState(PartDefinition.byId.getValue("electrical_source"),Direction.DOWN).apply { value=800.0 };changedTopology() }
        val old=Content.electricMinecart.get().create(level)!!;old.setPos(rail.x+.5,245.0625,rail.z+.5);level.addFreshEntity(old)
        val uuid=old.uuid;var parked=0.0;var reloaded: ElectricMinecartEntity?=null;var afterReload=0.0
        h.startSequence().thenWaitUntil {
            check(level.isPositionEntityTicking(rail) && old.energyJ>100,"Rail fixture has not become entity-ticking and electrically active")
        }.thenExecute {
            ticket(1,true);ticket(2,false)
        }.thenWaitUntil {
            check(level.chunkSource.isPositionTicking(ChunkPos.asLong(wire)) && !level.isPositionEntityTicking(rail),"Cart chunk has not entered block-only ticking")
        }.thenIdle(5).thenExecute {
            parked=old.energyJ+RailPower.pending(old)
        }.thenIdle(25).thenExecute {
            ensure(old.energyJ+RailPower.pending(old)==parked,"Block-ticking overhead kept charging an inactive entity")
            ticket(1,false)
        }.thenIdle(400).thenExecute {
            ensure(level.chunkSource.getChunkNow(chunk.x,chunk.z)==null && level.getEntity(uuid)==null,"Electric cart or overhead supply forced its unloaded chunk to stay alive")
            Eln.LOGGER.info("ELN_RAIL_CHUNK actual cart and overhead unload verified without forced lookups")
            ticket(2,true)
        }.thenWaitUntil {
            check(node(wire)!=null && level.getEntity(uuid) is ElectricMinecartEntity && level.isPositionEntityTicking(rail),"Cart entity and overhead have not completed actual reload")
        }.thenIdle(10).thenExecute {
            val cart=level.getEntity(uuid) as ElectricMinecartEntity;reloaded=cart
            val p=node(wire)!!.parts[0]!!
            ensure(cart!==old && cart.energyJ>=parked && abs(p.energy-p.gridState.getDouble("railDissipatedJ")-cart.energyJ-RailPower.pending(cart)-cart.motorJ)<1e-5,"Actual cart reload duplicated/lost paid energy or reused the removed entity")
            RailPower.disconnect(old);ensure(RailPower.collect(old)==0.0,"Stale cart callback collected the new entity's paid energy")
            afterReload=cart.energyJ
        }.thenIdle(15).thenExecute {
            val cart=reloaded!!;val p=node(wire)!!.parts[0]!!
            ensure(cart.energyJ>afterReload && p.gridState.getInt("railCarts")==1,"Stale old-cart callback detached or froze the reloaded cart's live electrical contact")
            cart.discard();ticket(2,false)
        }.thenSucceed()
    }
}
