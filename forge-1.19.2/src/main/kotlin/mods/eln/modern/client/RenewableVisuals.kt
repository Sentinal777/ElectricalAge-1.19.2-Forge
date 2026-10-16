package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import mods.eln.modern.*
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LiquidBlock
import java.util.WeakHashMap
import kotlin.math.*

/** Tick-driven original rotor filters, warning light and blade/wheel sound events. */
object RenewableVisuals {
    private class Motion(val id: String,var time: Long,var angle: Double) {
        var power=0.0;var direction=0.0;var waterPower=0.0;var blink=0.0;var sounds=0
        val active=mutableListOf<SimpleSoundInstance>()
    }
    private val motions=WeakHashMap<FaceNodeEntity,MutableMap<Int,Motion>>()
    private fun motion(node: FaceNodeEntity,p: PartState): Motion {
        val entries=motions.getOrPut(node) { hashMapOf() };val index=p.face.get3DDataValue()
        return entries[index]?.takeIf { it.id==p.definition.id } ?: Motion(p.definition.id,Long.MIN_VALUE,Math.floorMod(node.blockPos.hashCode(),360).toDouble()).also { entries[index]=it }
    }
    fun observe(node: FaceNodeEntity,p: PartState) {
        val level=node.level ?: return;val m=motion(node,p)
        if(m.time==level.gameTime)return
        m.time=level.gameTime;val wind=p.definition.behavior==Behavior.WIND_TURBINE
        m.active.removeIf { !Minecraft.getInstance().soundManager.isActive(it) }
        m.power+=(p.powerFactor.coerceAtLeast(0.0)-m.power)*.05/(if(wind)2 else 1)
        val previous=m.angle
        if(wind)m.angle+=.05*300*sqrt(m.power.coerceAtLeast(0.0))
        else {
            val at=mods.eln.modern.grid.GridMultiblock.offset(node.blockPos,p,listOf(1,-1,0))
            val right=at.relative(FaceBasis.edges(p)[2])
            fun metadata(pos: net.minecraft.core.BlockPos): Int {
                if(!level.hasChunkAt(pos))return 0
                val state=level.getBlockState(pos)
                return if(state.block==Blocks.WATER)state.getValue(LiquidBlock.LEVEL)else 0
            }
            val wet=level.hasChunkAt(at) && level.getBlockState(at).block==Blocks.WATER
            val direction=if(!wet)0.0 else if(metadata(at)>metadata(right))1.0 else -1.0
            m.direction+=(direction-m.direction)*.05/.5
            m.waterPower+=(m.direction*sqrt(p.powerFactor.coerceAtLeast(0.0))-m.waterPower)*.05
            m.angle+=.05*60*m.waterPower
        }
        m.blink=(m.blink+.05)%3
        val crossed=if(wind)floor((previous-45-1e-7)/120)!=floor((m.angle-45-1e-7)/120)else floor(previous/45)!=floor(m.angle/45)
        if(crossed) {
            val amplitude=if(wind).007+sqrt(m.power.coerceAtLeast(0.0))else .007+.2*m.waterPower*m.waterPower
            val pitch=if(wind)1+sqrt(m.power.coerceAtLeast(0.0))/1.3 else 1.1
            val sound=SimpleSoundInstance(OriginalSounds.event(if(wind)"windturbine_big_sf" else "water_turbine"),SoundSource.BLOCKS,amplitude.toFloat(),pitch.toFloat(),net.minecraft.util.RandomSource.create(),node.blockPos)
            m.active.add(sound);Minecraft.getInstance().soundManager.play(sound)
            m.sounds++
        }
    }
    fun emittedSounds(node: FaceNodeEntity,p: PartState)=motion(node,p).sounds
    fun activeSounds(node: FaceNodeEntity,p: PartState)=motion(node,p).active.count { Minecraft.getInstance().soundManager.isActive(it) }
    fun angle(node: FaceNodeEntity,p: PartState)=motion(node,p).angle
    internal fun render(node: FaceNodeEntity,p: PartState,model: LegacyModel,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        val wind=p.definition.behavior==Behavior.WIND_TURBINE;val m=motion(node,p)
        for(name in if(wind)listOf("main")else listOf("Support","Generator"))model.groups[name]?.let { LegacyMeshes.draw(it,pose,buffers,light,overlay) }
        val rotor=if(wind)"rot" else "Wheel"
        pose.pushPose()
        val x=model.property(rotor,"originX").toDouble();val y=model.property(rotor,"originY").toDouble();val z=model.property(rotor,"originZ").toDouble()
        pose.translate(x,y,z);pose.mulPose(Vector3f.XP.rotationDegrees((m.angle%360).toFloat()));pose.translate(-x,-y,-z)
        model.groups[rotor]?.let { LegacyMeshes.draw(it,pose,buffers,light,overlay) };pose.popPose()
        if(wind && m.blink>=2.5)model.groups["halo"]?.let { LegacyMeshes.draw(it,pose,buffers,15728880,overlay,color=0xFF0000,translucent=true) }
    }
}
