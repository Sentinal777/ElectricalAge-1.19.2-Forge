package mods.eln.modern

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkStatus
import net.minecraft.world.phys.Vec3
import net.minecraftforge.registries.ForgeRegistries
import kotlin.math.*

data class ScannerPixel(val red: Double,val green: Double,val blue: Double)

/** Original cell-boundary ray integration. Unavailable chunks remain air and are never requested. */
object ScannerImage {
    private val original=mapOf("coal" to .05,"iron" to .15,"gold" to .4,"lapis" to .4,"redstone" to .4,"diamond" to 1.0,"emerald" to .4,"copper" to .1)
    fun oreFactor(state: BlockState): Double {
        val id=ForgeRegistries.BLOCKS.getKey(state.block) ?: return 0.0
        val name=id.path.removePrefix("deepslate_").removeSuffix("_ore")
        if(id.namespace=="minecraft" && id.path.endsWith("_ore"))original[name]?.let { return it }
        if(id.namespace=="eln")when(name) { "copper"->return .1;"lead","tungsten","cinnabar"->return .2 }
        return if(state.`is`(net.minecraftforge.common.Tags.Blocks.ORES)).15 else 0.0
    }
    fun sample(level: Level,eye: Vec3,yaw: Double,pitch: Double,range: Double,width: Int=32,height: Int=20,fieldOfView: Double=Math.PI/2,maximumRange: Double=10.0): Array<Array<ScannerPixel>> {
        val reach=range.coerceIn(1.0,maximumRange);val cache=hashMapOf<BlockPos,BlockState>()
        val sinY=sin(Math.toRadians(yaw));val cosY=cos(Math.toRadians(yaw));val sinX=sin(Math.toRadians(pitch));val cosX=cos(Math.toRadians(pitch))
        val cam=width/2.0/tan(fieldOfView/2)
        fun block(pos: BlockPos)=cache.getOrPut(pos) {
            if(level.isOutsideBuildHeight(pos))Blocks.AIR.defaultBlockState()
            else level.chunkSource.getChunk(pos.x shr 4,pos.z shr 4,ChunkStatus.FULL,false)?.getBlockState(pos) ?: Blocks.AIR.defaultBlockState()
        }
        return Array(height) { sy -> Array(width) { sx ->
            val x=-(sx-width/2.0);val y=-(sy-height/2.0)
            val py=y*cosX-cam*sinX;val pz=cam*cosX+y*sinX
            var direction=Vec3(x*cosY-pz*sinY,py,pz*cosY+x*sinY).normalize()
            direction=Vec3(if(direction.x==0.0).0001 else direction.x,if(direction.y==0.0).0001 else direction.y,if(direction.z==0.0).0001 else direction.z)
            var point=eye;var distance=0.0;var red=0.0;var green=0.0;var blue=0.0
            while(distance<reach) {
                fun boundary(value: Double,speed: Double)=if(speed>0)(1-(value-floor(value)))/speed else -(value-floor(value))/speed
                val step=minOf(boundary(point.x,direction.x),boundary(point.y,direction.y),boundary(point.z,direction.z))+.01
                val length=minOf(step,reach-distance)
                val state=block(BlockPos(point));green+=oreFactor(state)*length
                if(state.isAir || state.block==Content.lightSpot.get())blue+=.06*length
                else red+=(if(state.canOcclude()).2 else .1)*length
                point=point.add(direction.scale(step));distance+=step
            }
            ScannerPixel(red,green,blue)
        } }
    }
}
