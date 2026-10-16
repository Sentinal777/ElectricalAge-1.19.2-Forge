package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.resources.ResourceLocation

object WatchVisuals {
    internal fun render(node: FaceNodeEntity,p: PartState,model: LegacyModel,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        val held=node.level==null
        val powered=held || p.active
        val time=if(held)1200L else WatchTime.ticks(WatchTime.date(p,node.level!!.dayTime))
        fun draw(group: String,color: Int=0xFFFFFF,glow: Boolean=false,u: Float=0f,v: Float=0f,texture: ResourceLocation?=null,glass: Boolean=false)=
            LegacyMeshes.draw(model.groups[group] ?: emptyList(),pose,buffers,if(glow)15728880 else light,overlay,color,u,v,texture,glass)
        if(p.definition.watch?.digital!=true) {
            draw("base")
            pose.pushPose();pose.mulPose(Vector3f.XN.rotationDegrees(time/12000f*360));draw("cHour");pose.popPose()
            pose.pushPose();pose.mulPose(Vector3f.XN.rotationDegrees((time%1000)/1000f*360));draw("cMin");pose.popPose()
            return
        }
        val minutes=(time*3/50).toInt();val h=minutes/60;val m=minutes%60
        val digits=if(powered)listOf(m%10,m/10,h%10,h/10) else List(4) { 10 }
        val texture=ResourceLocation("eln","model/digitalwallclock/digits.png")
        for(i in 0..3)draw("digit$i",0xF20000,true,digits[i]/16f,texture=texture)
        draw("digitDot",if(powered && minutes%2==0)0xF20000 else 0x0D0000,true,texture=texture)
        draw("base")
        val player=Minecraft.getInstance().player
        val u=if(player==null)0f else player.yRot/360f+((player.x+player.z)/64).toFloat()
        val v=(player?.xRot ?: 0f)/180f*.875f
        draw("glass",u=u,v=v,texture=ResourceLocation("eln","model/digitalwallclock/reflection.png"),glass=true)
    }
}
