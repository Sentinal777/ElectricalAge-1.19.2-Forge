package mods.eln.modern

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import kotlin.math.*

/** Native world/opacity adapter for SolarPannelSlowProcess's sun-ray algorithm. */
object SolarExposure {
    fun sunAngle(level: ServerLevel)=(level.getSunAngle(0f).toDouble()+PI/2)%(2*PI)
    fun trackingAngle(sunAngle: Double): Double {
        val alpha=((sunAngle+PI/2)%(2*PI)+2*PI)%(2*PI)
        return alpha.coerceIn(PI/4,3*PI/4)
    }
    fun sample(level: ServerLevel, pos: BlockPos, tracking: Boolean): Double {
        return sample(level,pos,if(tracking)trackingAngle(level.getSunAngle(0f).toDouble())else PI/2,listOf(0,1,0))
    }
    fun sample(level: ServerLevel,pos: BlockPos,panelAngle: Double,offset: List<Int>): Double {
        if(!level.dimensionType().hasSkyLight()) return 0.0
        val angle=sunAngle(level)
        if(angle>=PI) return 0.0
        var light=max(0.0,cos(panelAngle-angle))
        if(level.isRaining)light*=0.5
        if(level.isThundering)light*=0.5
        val normalizer=max(abs(cos(angle)),sin(angle)).coerceAtLeast(1e-6)
        val dx=cos(angle)/normalizer;val dy=sin(angle)/normalizer
        // The source offsets are world-axis offsets, independently of the pedestal yaw.
        var x=pos.x+0.5+offset[0];var y=pos.y+0.5+offset[1];val z=pos.z+offset[2]
        // Bounded traversal of loaded chunks only; 1.19's build height replaces 256.
        repeat(1024) {
            if(y>=level.maxBuildHeight || light<=0.0)return light
            val sample=BlockPos(floor(x).toInt(),floor(y).toInt(),z)
            if(!level.chunkSource.hasChunk(sample.x shr 4,sample.z shr 4))return light
            val opacity=level.getBlockState(sample).getLightBlock(level,sample).coerceIn(0,15)
            light*=(15-opacity)/15.0
            x+=dx;y+=dy
        }
        return light
    }
}
