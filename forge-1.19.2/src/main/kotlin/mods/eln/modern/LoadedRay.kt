package mods.eln.modern

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/** Exact voxel traversal for original opaque-block sight tests, without requesting missing chunks. */
object LoadedRay {
    fun visible(level: ServerLevel,start: Vec3,end: Vec3): Boolean {
        val delta=end.subtract(start)
        if(listOf(delta.x,delta.y,delta.z).any { !it.isFinite() })return false
        var pos=BlockPos(start);val finish=BlockPos(end)
        fun step(value: Double)=if(value>0)1 else if(value<0)-1 else 0
        val sx=step(delta.x);val sy=step(delta.y);val sz=step(delta.z)
        fun first(value: Double,cell: Int,change: Double,direction: Int)=if(direction==0)Double.POSITIVE_INFINITY else ((if(direction>0)cell+1 else cell)-value)/change
        var tx=first(start.x,pos.x,delta.x,sx);var ty=first(start.y,pos.y,delta.y,sy);var tz=first(start.z,pos.z,delta.z,sz)
        val dx=if(sx==0)Double.POSITIVE_INFINITY else abs(1/delta.x)
        val dy=if(sy==0)Double.POSITIVE_INFINITY else abs(1/delta.y)
        val dz=if(sz==0)Double.POSITIVE_INFINITY else abs(1/delta.z)
        repeat(256) {
            val chunk=level.chunkSource.getChunkNow(pos.x shr 4,pos.z shr 4) ?: return false
            if(level.isOutsideBuildHeight(pos) || chunk.getBlockState(pos).canOcclude())return false
            if(pos==finish)return true
            val next=minOf(tx,ty,tz)
            if(next>1)return true
            var x=pos.x;var y=pos.y;var z=pos.z
            if(tx==next) { x+=sx;tx+=dx }
            if(ty==next) { y+=sy;ty+=dy }
            if(tz==next) { z+=sz;tz+=dz }
            pos=BlockPos(x,y,z)
        }
        return false
    }
}
