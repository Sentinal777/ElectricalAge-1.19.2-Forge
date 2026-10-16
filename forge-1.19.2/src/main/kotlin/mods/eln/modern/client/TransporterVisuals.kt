package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import mods.eln.modern.*
import net.minecraft.client.renderer.MultiBufferSource

internal object TransporterVisuals {
    private class Motion(var time: Double,closed: Boolean) {
        val door=mods.eln.misc.PhysicalInterpolator(.2f,8f,5f,0f).apply { maxSpeed=.3f;setPos(if(closed)0f else 1f) }
        val ratio=mods.eln.misc.RcInterpolator(1f)
        val blue=mods.eln.misc.RcInterpolator(.5f)
        var gyro=0f
    }
    private val motion=java.util.WeakHashMap<FaceNodeEntity,Motion>()
    fun render(node: FaceNodeEntity,p: PartState,model: LegacyModel,partialTick: Float,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        fun draw(group: String,color: Int=0xFFFFFF,glow: Boolean=false,alpha: Int=255)=LegacyMeshes.draw(model.groups[group].orEmpty(),pose,buffers,if(glow)15728880 else light,overlay,color,translucent=alpha<255,alpha=alpha)
        if(node.level==null) { for(group in listOf("main","ext_control","ext_power","door_out","indoor_open"))draw(group);return }
        val time=node.level!!.gameTime+partialTick.toDouble();val closed=Transporters.closed(p)
        val state=motion.getOrPut(node) { Motion(time,closed) };val dt=((time-state.time)/20).coerceIn(0.0,.1).toFloat();state.time=time
        state.door.target=if(closed)0f else 1f;state.door.step(dt)
        state.ratio.target=p.processFraction.toFloat();state.ratio.step(dt)
        state.blue.target=if(state.ratio.get()>.1 && closed)1f else 0f;state.blue.step(dt)
        state.gyro=(state.gyro+360*dt*(1-state.door.get()))%360
        fun moving(group: String,color: Int=0xFFFFFF,glow: Boolean=false,alpha: Int=255,gyro: Boolean=false) {
            val x=model.property(group,"originX").toDouble();val y=model.property(group,"originY").toDouble();val z=model.property(group,"originZ").toDouble()
            pose.pushPose();pose.translate(x,y,z);pose.mulPose(Vector3f.ZP.rotationDegrees(state.door.get()*-90))
            if(gyro) {
                val x2=model.property(group,"originX2").toDouble();val y2=model.property(group,"originY2").toDouble();val z2=model.property(group,"originZ2").toDouble()
                val axis=Vector3f(-.11746f,.04275f,0f);axis.normalize()
                pose.translate(x2,y2,z2);pose.mulPose(axis.rotationDegrees(state.gyro));pose.translate(-x2,-y2,-z2)
            }
            pose.translate(-x,-y,-z);draw(group,color,glow,alpha);pose.popPose()
        }
        val lightAt=mods.eln.modern.grid.GridMultiblock.offset(node.blockPos,p,listOf(-1,1,0))
        val lightEnabled=node.level!!.hasChunkAt(lightAt) && node.level!!.getBlockState(lightAt).block==Content.lightSpot.get()
        for(group in listOf("main","ext_control","ext_power"))draw(group)
        moving("door_out")
        moving("door_in",glow=lightEnabled);draw("indoor_open",glow=lightEnabled)
        moving("gyro",if(closed)0xFF8000 else 0xFFFFFF,closed,gyro=true)
        if(closed)moving("gyro_alpha",0xFF8000,true,102,true)
        if(p.voltage/800>.6) {
            draw("led0",0xFF0000,true);draw("led1",0xFF0000,true)
            for(i in 2..9)if(((time/2).toInt()+i*11)%7<3)draw("led$i",0x00FF00,true)
            if(p.voltage/800 in .875..1.2 || (time/8).toInt()%2==0)draw("scr0_electrictity",0xFF8000,true)
            draw("scr1_cables",0xFF8000,true);draw("scr2_transporter",0xFF8000,true)
            if(node.level!!.getEntities(null,Transporters.area(node,p)).isNotEmpty())draw("scr3_userin",0xFF8000,true)
            draw(if(closed)"src4_doorclosed" else "scr5_dooropen",0xFF8000,true)
        }
        if(state.ratio.get()>.005) {
            val alpha=(state.blue.get()*255).toInt().coerceIn(0,255)
            draw("indoor_closed",glow=true,alpha=alpha);moving("door_in_charge",glow=true,alpha=alpha);moving("whiteblur",glow=true,alpha=alpha)
        }
    }
}
