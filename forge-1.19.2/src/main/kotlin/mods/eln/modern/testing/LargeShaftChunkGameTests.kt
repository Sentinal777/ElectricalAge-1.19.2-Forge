package mods.eln.modern.testing

import mods.eln.Eln
import mods.eln.modern.*
import mods.eln.modern.grid.GridMultiblock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object LargeShaftChunkGameTests {
    @JvmStatic @GameTest(batch="large_shaft_chunks",template="grid_empty",timeoutTicks=2100)
    fun raisedShaftPortsStopAtNonTickingChunksAndSurviveTwoRealUnloadReloadCycles(h: GameTestHelper) {
        val level=h.level;val base=h.absolutePos(BlockPos.ZERO)
        val cx=(base.x shr 4)+1800;val cz=(base.z shr 4)+1800
        val rootChunk=ChunkPos(cx,cz);val remoteChunk=ChunkPos(cx+1,cz)
        val rootPos=BlockPos(cx*16+15,250,cz*16+8);val wheelPos=rootPos.offset(2,1,0)
        fun ticket(chunk: ChunkPos,enabled: Boolean) {
            // Level 32 keeps only this chunk block-ticking; its neighbor stays full at level 33.
            if(enabled)level.chunkSource.addRegionTicket(TicketType.FORCED,chunk,1,chunk)
            else level.chunkSource.removeRegionTicket(TicketType.FORCED,chunk,1,chunk)
        }
        fun loaded(chunk: ChunkPos)=level.chunkSource.getChunkNow(chunk.x,chunk.z)!=null
        fun node(pos: BlockPos)=(level.chunkSource.getChunkNow(pos.x shr 4,pos.z shr 4)?.getBlockEntity(pos) as? FaceNodeEntity)
        fun ticking(pos: BlockPos)=level.chunkSource.isPositionTicking(ChunkPos.asLong(pos))
        fun cleanup() { ticket(rootChunk,false);ticket(remoteChunk,false) }
        fun check(value: Boolean,message: String) { if(!value)h.fail(message) }
        fun ensure(value: Boolean,message: String) { if(!value) { cleanup();h.fail(message) } }
        ticket(rootChunk,true);ticket(remoteChunk,true)
        level.getChunk(cx,cz);level.getChunk(cx+1,cz) // Deliberate initial fixture load only.
        val p=PartState(PartDefinition.byId.getValue("large_steam_turbine"),Direction.DOWN).apply { rotation=1 }
        for(pos in GridMultiblock.cells(rootPos,p)+rootPos+wheelPos)level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState())
        level.setBlockAndUpdate(rootPos.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(rootPos,Content.machine.get().defaultBlockState())
        val root=node(rootPos)!!;root.parts[0]=p;GridMultiblock.install(root,p);root.changedTopology()
        level.setBlockAndUpdate(wheelPos.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(wheelPos,Content.machine.get().defaultBlockState())
        val wheel=node(wheelPos)!!;wheel.parts[0]=PartState(PartDefinition.byId.getValue("flywheel"),Direction.DOWN).apply { rotation=1 }
        root.parts[0]!!.shaftGroup="large-chunk-primer";wheel.parts[0]!!.shaftGroup="large-chunk-primer"
        root.parts[0]!!.shaftEnergy=10000.0*135/185;wheel.parts[0]!!.shaftEnergy=10000.0*50/185;wheel.changedTopology()
        val owner=p.gridId
        var savedEnergy=10000.0;var parkedEnergy=0.0;var parkedSpeed=0.0
        var sequence=h.startSequence()
        sequence=sequence.thenWaitUntil { check(ticking(rootPos) && ticking(wheelPos),"Raised-shaft fixture chunks are not ticking yet") }.thenIdle(10)
        repeat(2) { cycle ->
            sequence=sequence.thenExecute {
                val a=node(rootPos)!!.parts[0]!!;val b=node(wheelPos)!!.parts[0]!!
                ensure(a.gridId==owner && a.shaftSpeed>0 && abs(a.shaftSpeed-b.shaftSpeed)<1e-9,"Live raised shaft did not connect its neighboring chunk: cycle=$cycle root=$rootPos owner=${a.gridId==owner} rootSpeed=${a.shaftSpeed} wheelSpeed=${b.shaftSpeed} rootEnergy=${a.shaftEnergy} wheelEnergy=${b.shaftEnergy} rootGroup=${a.shaftGroup} wheelGroup=${b.shaftGroup} ports=${MechanicalNetwork.remotePorts(level,node(rootPos)!!,a)}")
                ticket(remoteChunk,false)
            }.thenWaitUntil {
                check(ticking(rootPos) && !ticking(wheelPos),"Remote shaft chunk has not stopped ticking independently")
            }.thenIdle(5).thenExecute {
                val a=node(rootPos)!!;val b=node(wheelPos)!!.parts[0]!!
                ensure(MechanicalNetwork.remotePorts(level,a,a.parts[0]!!).none { (it.position.x shr 4)==remoteChunk.x },"Non-ticking ghost port remained active")
                parkedEnergy=b.shaftEnergy;parkedSpeed=b.shaftSpeed
            }.thenIdle(30).thenExecute {
                val a=node(rootPos)!!.parts[0]!!;val b=node(wheelPos)!!.parts[0]!!
                ensure(b.shaftEnergy==parkedEnergy && b.shaftSpeed==parkedSpeed && abs(a.shaftSpeed-b.shaftSpeed)>1e-5,"Inactive remote shaft continued to advance through its owner")
                savedEnergy=a.shaftEnergy+b.shaftEnergy
                ticket(rootChunk,false)
            }.thenIdle(400).thenExecute {
                ensure(!loaded(rootChunk) && !loaded(remoteChunk),"Large machine kept its unloaded body or axle chunks alive")
                Eln.LOGGER.info("ELN_LARGE_SHAFT_CHUNK actual unload cycle {} verified without forced lookups",cycle+1)
                ticket(rootChunk,true);ticket(remoteChunk,true)
            }.thenWaitUntil {
                check(node(rootPos)!=null && node(wheelPos)!=null && ticking(rootPos) && ticking(wheelPos),"Large shaft chunks have not finished reloading")
            }.thenIdle(10).thenExecute {
                val a=node(rootPos)!!.parts[0]!!;val b=node(wheelPos)!!.parts[0]!!
                ensure(a.gridId==owner && a.shaftEnergy+b.shaftEnergy<=savedEnergy+1e-6 && abs(a.shaftSpeed-b.shaftSpeed)<1e-9,"Large shaft reload replaced ownership, duplicated work or lost its remote connection")
            }
        }
        sequence.thenExecute { cleanup() }.thenSucceed()
    }
}
