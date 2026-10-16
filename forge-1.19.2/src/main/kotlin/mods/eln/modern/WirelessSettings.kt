package mods.eln.modern

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import kotlin.math.sin
import kotlin.math.pow
import kotlin.math.sqrt

data class WirelessSpec(val kind: String,val autoReset: Boolean=false)

/** Source radio geometry and aggregation. Only already-loaded block data is inspected. */
object WirelessSettings {
    fun virtualDistance(level: ServerLevel,transmitter: BlockPos,receiver: BlockPos): Double {
        val distance=sqrt(transmitter.distSqr(receiver).toDouble())
        if(distance<=2)return distance
        val dx=(transmitter.x-receiver.x)/distance;val dy=(transmitter.y-receiver.y)/distance;val dz=(transmitter.z-receiver.z)/distance
        var x=receiver.x+.5;var y=receiver.y+.5;var z=receiver.z+.5;var result=distance
        var step=0
        while(step<distance-1) {
            x+=dx;y+=dy;z+=dz
            val at=BlockPos(x,y,z)
            if(level.hasChunkAt(at)) {
                val state=level.getBlockState(at)
                if(!state.isAir && state.isSolidRender(level,at))result+=2
            }
            step++
        }
        return result
    }
    fun inRange(level: ServerLevel,transmitter: BlockPos,receiver: BlockPos,range: Double)=transmitter.distSqr(receiver)<=range*range && virtualDistance(level,transmitter,receiver)<=range
    fun maximum(values: Collection<Double>)=values.maxOrNull() ?: 0.0
    fun minimum(values: Collection<Double>)=values.minOrNull() ?: 0.0
    fun glitch(timeLeft: Double,strength: Double)=if(timeLeft>0)sin((6-timeLeft)*Math.PI*2*3)*strength*(timeLeft/6).pow(4)else 0.0
}
