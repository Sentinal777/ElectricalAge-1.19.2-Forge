package mods.eln.modern

import mods.eln.modern.grid.GridGhostEntity
import mods.eln.modern.grid.GridMultiblock
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos

/** Raised electrical terminals participate only while their actual owned ghost is ticking. */
object RemotePowerPorts {
    fun positions(node: FaceNodeEntity,p: PartState)=p.definition.powerPorts.orEmpty().map { spec ->
        MechanicalNetwork.Companion.RemotePort(GridMultiblock.offset(node.blockPos,p,spec.offset),FaceBasis.edges(p)[spec.side])
    }
    fun active(world: ServerLevel,node: FaceNodeEntity,p: PartState)=positions(node,p).filter { port ->
        if(!world.chunkSource.isPositionTicking(ChunkPos.asLong(port.position)))false else {
            val ghost=world.chunkSource.getChunkNow(port.position.x shr 4,port.position.z shr 4)?.getBlockEntity(port.position) as? GridGhostEntity
            ghost!=null && !ghost.isRemoved && ghost.owner==node.blockPos && ghost.ownerId==p.gridId
        }
    }
}
