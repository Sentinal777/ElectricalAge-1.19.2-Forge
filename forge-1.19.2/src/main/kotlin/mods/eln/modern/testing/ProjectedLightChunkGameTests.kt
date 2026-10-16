package mods.eln.modern.testing

import mods.eln.modern.Content
import mods.eln.modern.lighting.LightSpotBlock
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.LightLayer
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object ProjectedLightChunkGameTests {
    @JvmStatic @GameTest(batch="projected_light_dormant_chunk",template="grid_empty",timeoutTicks=600)
    fun realProjectedLightExpiresInALoadedFullChunkWithoutNormalBlockOrEntityTicking(h: GameTestHelper) {
        val base=h.absolutePos(BlockPos.ZERO)
        val at=BlockPos(((base.x shr 4)+3000)*16+8,290,((base.z shr 4)+3000)*16+8)
        val column=ChunkPos(at);val forced=h.level.forcedChunks.toSet()
        // Load the isolated QA fixture, then lower its ticket before testing production expiry.
        h.level.chunkSource.addRegionTicket(TicketType.FORCED,column,2,column)
        h.level.getChunk(column.x,column.z)
        h.level.chunkSource.addRegionTicket(TicketType.FORCED,column,0,column)
        h.level.chunkSource.removeRegionTicket(TicketType.FORCED,column,2,column)
        var began=-1L;var stopped=-1L
        fun fail(message: String) { h.level.chunkSource.removeRegionTicket(TicketType.FORCED,column,0,column);h.fail(message) }
        for(tick in 1L..580L)h.runAtTickTime(tick) {
            if(stopped>=0) {
                // The lease is five ticks, followed by two zero-emission removal ticks.
                // Keep a strict block-removal deadline; threaded light propagation has its
                // own completion time, especially while new remote chunks are being lit.
                if(tick==stopped+10 && (h.level.getBlockState(at).block==Content.lightSpot.get() || h.level.getBlockState(at).lightEmission!=0 || h.level.forcedChunks.toSet()!=forced))fail("Dormant block lease outlived its removal deadline at tick $tick (stopped=$stopped); ${mods.eln.modern.lighting.LightRecovery.describe(h.level,at)}")
                if(tick==stopped+10 && (h.level.chunkSource.getChunkNow(column.x,column.z)==null || h.level.chunkSource.isPositionTicking(column.toLong()) || h.level.isPositionEntityTicking(at)))fail("Dormant expiry fixture stopped being a loaded non-ticking chunk")
                if(tick>=stopped+10) {
                    if(h.level.getBlockState(at).block==Content.lightSpot.get() || h.level.forcedChunks.toSet()!=forced)fail("Dormant light lease reappeared or changed forced chunks after removal")
                    if(h.level.getBrightness(LightLayer.BLOCK,at)==0) {
                        mods.eln.Eln.LOGGER.info("ELN_DORMANT_LIGHT actual zero-light field after {} ticks; original block lease removed within 10 ticks",tick-stopped)
                        h.level.chunkSource.removeRegionTicket(TicketType.FORCED,column,0,column);h.succeed()
                    } else if(tick>=stopped+120)fail("Dormant asynchronous light removal never completed at tick $tick (stopped=$stopped); ${mods.eln.modern.lighting.LightRecovery.describe(h.level,at)}")
                }
            } else if(stopped<0) {
                val chunk=h.level.chunkSource.getChunkNow(column.x,column.z)
                if(chunk!=null && !h.level.chunkSource.isPositionTicking(column.toLong()) && !h.level.isPositionEntityTicking(at)) {
                    if(began<0) { began=tick;h.level.setBlockAndUpdate(at,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()) }
                    LightSpotBlock.illuminate(h.level,at,15)
                    if(tick-began>=20 && h.level.getBrightness(LightLayer.BLOCK,at)==15)stopped=tick
                }
                if(stopped<0 && (began<0 && tick>=450 || began>=0 && tick-began>=120))fail("Dormant projection readiness failed: began=$began, tick=$tick, loaded=${chunk!=null}; ${if(chunk!=null)mods.eln.modern.lighting.LightRecovery.describe(h.level,at) else "unloaded"}")
            }
        }
    }
}
