package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import mods.eln.modern.*
import net.minecraft.client.renderer.MultiBufferSource
import kotlin.math.*

/** Original on/off textures, emissive glass, and entity/weather-driven hanging chains. */
internal object LampVisuals {
    private data class Swing(var time: Double,var py: Double=0.0,var pz: Double=0.0,var vy: Double=0.0,var vz: Double=0.0,var phaseY: Double=0.0,var phaseZ: Double=0.0)
    private val motions=java.util.WeakHashMap<FaceNodeEntity,MutableMap<Int,Swing>>()
    fun render(node: FaceNodeEntity,p: PartState,model: LegacyModel,partialTick: Float,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        val spec=p.definition.lamp!!;val on=p.signal>8
        val hasBulb=!PartInventory.get(node,p).getStackInSlot(0).isEmpty
        val tint=if(spec.paintable)p.displayColor else 0xFFFFFF
        val texture=if(spec.onOffModel)model.textures[if(on)"tOn" else "tOff"]else null
        val player=net.minecraft.client.Minecraft.getInstance().player
        val distance=player?.distanceToSqr(node.blockPos.x+.5,node.blockPos.y+.5,node.blockPos.z+.5)?.let(::sqrt) ?: 100.0
        fun draw(group: String,glow: Boolean=false,alpha: Boolean=false) {
            model.groups[group]?.let { LegacyMeshes.draw(it,pose,buffers,if(glow && on)15728880 else light,overlay,tint,texture=if(alpha)null else texture,translucent=alpha) }
        }
        if(spec.chainLength>0) {
            draw("base")
            val now=(node.level?.gameTime ?: 0)+partialTick.toDouble()
            val swing=motions.getOrPut(node) { hashMapOf() }.getOrPut(p.face.get3DDataValue()) { Swing(now) }
            val dt=((now-swing.time)/20).coerceIn(0.0,.25);swing.time=now
            val world=node.level
            if(spec.swing && world!=null) {
                for(entity in world.getEntities(null,net.minecraft.world.phys.AABB(node.blockPos).move(0.0,-2.0,0.0).inflate(2.0))) {
                    val factor=if(entity is net.minecraft.world.entity.LivingEntity)4.0 else if(entity is net.minecraft.world.entity.projectile.AbstractArrow)1.0 else 0.0
                    swing.vz+=entity.deltaMovement.x*factor*dt;swing.vy+=entity.deltaMovement.z*factor*dt
                }
                if(world.getBrightness(net.minecraft.world.level.LightLayer.SKY,node.blockPos)>3) {
                    val weather=(if(world.isThundering)1.0 else if(world.isRaining).5 else 0.0)*.9+.1
                    swing.phaseY+=(.4-world.random.nextDouble())*dt*PI/.2*weather
                    swing.phaseZ+=(.4-world.random.nextDouble())*dt*PI/.2*weather
                    swing.vy+=world.random.nextDouble()*sin(swing.phaseY)*weather*weather*dt*3
                    swing.vz+=world.random.nextDouble()*cos(swing.phaseY)*weather*weather*dt*3
                    swing.vy+=.4*dt*weather*sign(swing.vy)*world.random.nextDouble()
                    swing.vz+=.4*dt*weather*sign(swing.vz)*world.random.nextDouble()
                }
                swing.vy=(swing.vy-swing.py/10*dt)*(1-.2*dt);swing.py=(swing.py+swing.vy).coerceIn(-15.0,15.0)
                swing.vz=(swing.vz-swing.pz/10*dt)*(1-.2*dt);swing.pz=(swing.pz+swing.vz).coerceIn(-15.0,15.0)
            }
            pose.translate(model.property("base","length").toDouble(),0.0,0.0)
            repeat(spec.chainLength) {
                if(spec.swing) { pose.mulPose(Vector3f.YP.rotationDegrees((swing.py/spec.chainLength).toFloat()));pose.mulPose(Vector3f.ZP.rotationDegrees((swing.pz/spec.chainLength).toFloat())) }
                draw("chain");pose.translate(model.property("chain","length").toDouble(),0.0,0.0)
            }
            draw("socket",true)
        } else {
            if(p.definition.id in setOf("robust_lamp_socket","sconce_lamp_socket"))pose.mulPose(Vector3f.XP.rotationDegrees(-90f))
            draw("socket_unlightable");draw("socket_lightable",true);draw("socket",true);draw("lamp",true)
            if(hasBulb)draw(if(on)"lampOn" else "lampOff",on)
        }
        if(p.signal>0) {
            draw("lightAlpha",true,true)
            if(distance<2)draw("lightAlphaNoDepth",true,true)
        }
    }
}
