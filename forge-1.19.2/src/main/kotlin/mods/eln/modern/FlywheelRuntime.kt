package mods.eln.modern

import net.minecraft.core.Direction
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import kotlin.math.abs

/** Original twenty-hertz contact hazard, independent of the camera and rotor rendering. */
object FlywheelRuntime {
    private val damage=DamageSource("eln.flywheel")
    fun tick(node: FaceNodeEntity,p: PartState) {
        if(node.isRemoved || node.parts[p.face.get3DDataValue()]!==p)return
        val speed=p.shaftSpeed
        if(speed<5)return
        val level=node.level ?: return
        if(level.isClientSide)return
        val pos=node.blockPos;val magnitude=(speed/250).coerceIn(0.0,1.0)
        val axis=FaceBasis.edges(p)[1].axis
        for(entity in level.getEntities(null,AABB(pos).inflate(1.0))) {
            if(abs(entity.x-pos.x-.5)>.5 || abs(entity.z-pos.z-.5)>.5 || abs(entity.y-pos.y-1)>1)continue
            if(entity !is Player || !entity.abilities.instabuild) {
                when(axis) {
                    Direction.Axis.X -> entity.push(magnitude,magnitude*.1,0.0)
                    Direction.Axis.Z -> entity.push(0.0,magnitude*.1,magnitude)
                    else -> entity.push(0.0,magnitude,0.0)
                }
                entity.hurtMarked=true
            }
            val amount=(1+(speed-5)*19/245).toInt().coerceIn(0,1000)
            if(amount>0)entity.hurt(damage,amount.toFloat())
        }
    }
}
