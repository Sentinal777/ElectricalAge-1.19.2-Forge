package mods.eln.modern.lighting

import mods.eln.Eln
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.ChunkPos
import java.util.WeakHashMap

/** Revisit ordinary vanilla light updates when a loaded emitter's actual light data is stale. */
object LightRecovery {
    private class State {
        val darkSince=mutableMapOf<Long,Long>()
        val requested=mutableMapOf<Long,Long>()
    }
    private val states=WeakHashMap<ServerLevel,State>()
    fun forget(world: ServerLevel,pos: BlockPos) { states[world]?.darkSince?.remove(pos.asLong()) }
    fun observe(world: ServerLevel,pos: BlockPos,emission: Int) {
        if(emission<=0) { forget(world,pos);return }
        val state=states.getOrPut(world) { State() };val key=pos.asLong()
        if(world.getBrightness(LightLayer.BLOCK,pos)>=emission) { state.darkSince.remove(key);return }
        val since=state.darkSince.getOrPut(key) { world.gameTime }
        if(world.gameTime-since<15)return
        val chunk=world.chunkSource.getChunkNow(pos.x shr 4,pos.z shr 4) ?: return
        val column=ChunkPos.asLong(pos);val previous=state.requested[column]
        if(previous!=null && world.gameTime-previous<100)return
        state.requested[column]=world.gameTime
        if(state.requested.size>128)state.requested.entries.removeIf { world.gameTime-it.value>1200 }
        Eln.LOGGER.debug("Revisiting stale block-light data for active ELN emitter at {}",pos)
        val engine=world.chunkSource.lightEngine
        engine.updateSectionStatus(SectionPos.of(pos),false)
        engine.enableLightSources(chunk.pos,true)
        engine.checkBlock(pos)
        engine.tryScheduleUpdate()
    }
    fun describe(world: ServerLevel,pos: BlockPos): String {
        val block=world.getBlockState(pos);val chunk=world.chunkSource.getChunkNow(pos.x shr 4,pos.z shr 4)
        val section=SectionPos.of(pos);val engine=world.chunkSource.lightEngine
        val layer=engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(section)
        val lightChunk=world.chunkSource.getChunkForLighting(pos.x shr 4,pos.z shr 4)
        return "$pos=$block air=${block.isAir} emission=${block.getLightEmission(world,pos)} cachedEmission=${block.lightEmission} actual=${world.getBrightness(LightLayer.BLOCK,pos)} lightingGetter=${lightChunk?.javaClass?.simpleName} lightingEmission=${lightChunk?.getLightEmission(pos)} sameChunk=${lightChunk===chunk} correct=${chunk?.isLightCorrect} section=${engine.getDebugData(LightLayer.BLOCK,section)} layerEmpty=${layer?.isEmpty} work=${engine.hasLightWork()} relight=${states[world]?.requested?.get(ChunkPos.asLong(pos))} blockTicking=${world.chunkSource.isPositionTicking(ChunkPos.asLong(pos))} entityTicking=${world.isPositionEntityTicking(pos)} gameTime=${world.gameTime} blockEntity=${world.getBlockEntity(pos)?.saveWithoutMetadata()}"
    }
}
