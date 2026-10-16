package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import mods.eln.modern.*
import net.minecraft.client.renderer.MultiBufferSource

internal object TurretVisuals {
    private class Motion(var time: Double,p: PartState) { val mechanical=TurretMotion(p);var shots=p.gridState.getInt("turretShots") }
    private val motion=java.util.WeakHashMap<FaceNodeEntity,Motion>()
    fun render(node: FaceNodeEntity,p: PartState,model: LegacyModel,partialTick: Float,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        fun draw(group: String,color: Int=0xFFFFFF,glow: Boolean=false,alpha: Int=255)=LegacyMeshes.draw(model.groups[group].orEmpty(),pose,buffers,if(glow)15728880 else light,overlay,color,translucent=alpha<255,alpha=alpha)
        val armed=node.level==null || p.gridState.getBoolean("turretArmed")
        var yaw=0f;var gun=0f;var elevation=0f;var shooting=false
        if(node.level!=null) {
            val time=node.level!!.gameTime+partialTick.toDouble();val state=motion.getOrPut(node) { Motion(time,p) };val dt=((time-state.time)/20).coerceIn(0.0,.1).toFloat();state.time=time
            state.mechanical.targets(p.gridState.getFloat("turretYawTarget"),p.gridState.getFloat("turretGunTarget"),p.gridState.getFloat("turretElevationTarget"),p.gridState.getBoolean("turretSeeking"))
            val shots=p.gridState.getInt("turretShots");if(shots!=state.shots) { state.shots=shots;state.mechanical.shooting=.1f }
            state.mechanical.step(dt,armed);yaw=state.mechanical.yaw.position;gun=state.mechanical.gun.position;elevation=state.mechanical.elevation.position;shooting=state.mechanical.shooting>0
        }
        draw("Holder");draw("Joint");pose.pushPose();pose.mulPose(Vector3f.YP.rotationDegrees(yaw));draw("Turret")
        val filter=(PartInventory.peek(p,0).item as? MaterialItem)?.definition
        val color=if(!armed || filter==null)0x808080 else {
            val raw=filter.value.toInt().let { if(p.enabled)it xor 0xFFFFFF else it };val gain=.5+.5*gun
            fun channel(shift: Int)=(((raw shr shift) and 255)*gain).toInt().coerceIn(0,255)
            (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
        draw("Sensor",color,armed);pose.mulPose(Vector3f.ZP.rotationDegrees(-elevation))
        if(shooting)draw("Fire",0x99CCFF,true,102)
        pose.translate(0.0,0.0,gun/4.0);draw("LeftGun");pose.translate(0.0,0.0,-gun/2.0);draw("RightGun");pose.popPose()
    }
}
