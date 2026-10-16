package mods.eln.modern

import mods.eln.config.ForgePortConfig
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Difficulty
import net.minecraft.world.level.LightLayer
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.EntityAttributeCreationEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.server.ServerLifecycleHooks

@Mod.EventBusSubscriber(modid="eln",bus=Mod.EventBusSubscriber.Bus.MOD)
object ReplicatorAttributes {
    @JvmStatic @SubscribeEvent fun attributes(event: EntityAttributeCreationEvent) { event.put(Content.replicator.get(),ReplicatorEntity.attributes().build()) }
}

/** Source default is disabled; only the overworld's loaded columns participate in thunder spawning. */
@Mod.EventBusSubscriber(modid="eln")
object ReplicatorWeather {
    @JvmStatic @SubscribeEvent fun tick(event: TickEvent.ServerTickEvent) {
        if(event.phase!=TickEvent.Phase.END || !ForgePortConfig.replicatorPop.get())return
        val world=ServerLifecycleHooks.getCurrentServer()?.overworld() ?: return
        val mobs=world.allEntities.filterIsInstance<ReplicatorEntity>().filter { !it.isRemoved }
        val maximum=ForgePortConfig.maxReplicators.get()
        mobs.drop(maximum).forEach { it.discard() }
        if(mobs.size>=maximum || world.difficulty==Difficulty.PEACEFUL || !world.isThundering || world.players().isEmpty())return
        val players=world.players()
        for(player in players) {
            if(world.random.nextDouble()*players.size>=.05*ForgePortConfig.replicatorPopRate.get())continue
            val x=(player.x+world.random.nextDouble()*200-100).toInt()
            val z=(player.z+world.random.nextDouble()*200-100).toInt()
            val at=spawnPosition(world,x,z) ?: continue
            val mob=Content.replicator.get().create(world) ?: continue
            mob.spawnedFromWeather=true;mob.moveTo(at.x+.5,at.y.toDouble(),at.z+.5,0f,0f)
            if(world.addFreshEntity(mob))mob.playAmbientSound()
            break
        }
    }
    fun spawnPosition(world: ServerLevel,x: Int,z: Int): BlockPos? {
        val chunk=world.chunkSource.getChunkNow(x shr 4,z shr 4) ?: return null
        for(y in world.minBuildHeight+2 until world.maxBuildHeight-1) {
            val at=BlockPos(x,y,z)
            if(!world.worldBorder.isWithinBounds(at))return null
            if(chunk.getBlockState(at).isAir && chunk.getBlockState(at.above()).isAir && world.getBrightness(LightLayer.BLOCK,at)<=6)return at
        }
        return null
    }
}
