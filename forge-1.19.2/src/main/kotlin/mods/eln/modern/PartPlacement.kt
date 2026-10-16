package mods.eln.modern

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockGetter

/** Shared by item placement and support-loss handling; a neighbouring carrier is not a solid support. */
object PartPlacement {
    fun supported(level: BlockGetter,pos: BlockPos,face: Direction): Boolean {
        val support=pos.relative(face)
        if(level is net.minecraft.world.level.Level && !level.hasChunkAt(support))return false
        return level.getBlockState(support).isFaceSturdy(level,support,face.opposite)
    }
    fun canMount(level: BlockGetter,pos: BlockPos,part: PartState): Boolean = if(part.definition.behavior in setOf(Behavior.FESTIVE,Behavior.INCUBATOR,Behavior.FABRICATOR,Behavior.TURRET)) {
        if(part.definition.id=="string_lights")Direction.Plane.HORIZONTAL.any { wall ->
            val at=pos.relative(wall)
            (level !is net.minecraft.world.level.Level || level.hasChunkAt(at)) && level.getBlockState(at).isSolidRender(level,at)
        } else {
            val floor=pos.below()
            (level !is net.minecraft.world.level.Level || level.hasChunkAt(floor)) && level.getBlockState(floor).let { it.isSolidRender(level,floor) || it.block==net.minecraft.world.level.block.Blocks.HOPPER }
        }
    } else if(part.definition.behavior!=Behavior.RESIN_COLLECTOR)true else part.face.axis!=Direction.Axis.Y && (level !is net.minecraft.world.level.Level || level.hasChunkAt(pos.relative(part.face))) && level.getBlockState(pos.relative(part.face)).`is`(net.minecraft.tags.BlockTags.LOGS)
    fun fits(part: PartState,node: FaceNodeEntity?): Boolean {
        if(node!=null && (NodeCovers.present(node) || node.blockState.getValue(NodeBlock.COVERED)))return false
        if(part.definition.behavior==Behavior.POWER_SOCKET && part.face.axis==Direction.Axis.Y)return false
        if(part.definition.environment?.kind=="WIND" && part.face.axis==Direction.Axis.Y)return false
        if(part.definition.behavior==Behavior.RESIN_COLLECTOR && part.face.axis==Direction.Axis.Y)return false
        part.definition.lamp?.allowedFaces?.let { if(part.face.get3DDataValue() !in it)return false }
        if(part.definition.visual.floorOnly && part.face!=Direction.DOWN)return false
        if(node==null)return true
        if(part.definition.machine || node.isMachine || node.parts[part.face.get3DDataValue()]!=null)return false
        return node.parts.filterNotNull().none { PartGeometry.overlapsBody(part,it) }
    }
}
