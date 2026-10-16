package mods.eln.modern

import mods.eln.config.ForgePortConfig
import mods.eln.modern.grid.GridMultiblock
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Explosion
import kotlin.math.max

object MachineFailure {
    fun destroy(node: FaceNodeEntity,p: PartState,strength: Float=3f) {
        val level=node.level as? ServerLevel ?: return
        if(node.isRemoved || node.parts[p.face.get3DDataValue()]!==p)return
        GridMultiblock.remove(node,p)
        // The failed machine and its stored component inventory are consumed by the failure.
        // Removing the carrier may still release its separate processing inventory.
        node.parts[p.face.get3DDataValue()]=null;node.gridFailed=true
        val pos=node.blockPos
        if(node.parts.all { it==null })level.removeBlock(pos,false)else node.changedTopology()
        if(ForgePortConfig.machineExplosions.get())level.explode(null,pos.x+.5,pos.y+.5,pos.z+.5,strength,Explosion.BlockInteraction.BREAK)
    }
}

/** Original ValueWatchdog: first-excursion joker, two-unit tolerance and randomized overflow integral. */
object ShaftProtectionRuntime {
    fun tick(node: FaceNodeEntity,p: PartState,speed: Double) {
        if(node.isRemoved || node.parts[p.face.get3DDataValue()]!==p)return
        val level=node.level as? ServerLevel ?: return
        var overflow=max(speed-250,-speed)
        val was=p.gridState.getBoolean("shaftSpeedOverflow")
        p.gridState.putBoolean("shaftSpeedOverflow",overflow>0)
        if(overflow>0 && !was)overflow=0.0
        val remaining=if(p.gridState.contains("shaftWatchdogRemaining"))p.gridState.getDouble("shaftWatchdogRemaining")else 2.0
        val next=minOf(2.0,remaining-.05*overflow*(.5+level.random.nextDouble()))
        p.gridState.putDouble("shaftWatchdogRemaining",next)
        if(next<0 && ForgePortConfig.shaftFailures.get())MachineFailure.destroy(node,p)
    }
}
