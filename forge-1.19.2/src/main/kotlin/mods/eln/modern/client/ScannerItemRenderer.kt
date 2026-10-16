package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.block.model.ItemTransforms
import net.minecraft.world.item.ItemStack

/** Original scanner housing, state lamps, text overlays, cracked glass and ray-integrated screen. */
class ScannerItemRenderer private constructor(): BlockEntityWithoutLevelRenderer(Minecraft.getInstance().blockEntityRenderDispatcher,Minecraft.getInstance().entityModels) {
    private var lastTick=Long.MIN_VALUE
    private var lastEye=net.minecraft.world.phys.Vec3.ZERO
    private var lastYaw=0f;private var lastPitch=0f
    private var pixels: Array<Array<ScannerPixel>>?=null
    override fun renderByItem(stack: ItemStack,type: ItemTransforms.TransformType,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        val spec=PortableScanner.spec(stack) ?: return
        val mc=Minecraft.getInstance();val model=LegacyMeshes.model("xrayscanner") ?: return
        val state=PortableScanner.state(stack)
        val holder=if(type in setOf(ItemTransforms.TransformType.FIRST_PERSON_LEFT_HAND,ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND))mc.player
            else mc.level?.players()?.firstOrNull { it.mainHandItem===stack || it.offhandItem===stack }
        pose.pushPose();pose.translate(0.0,.52,.5)
        fun draw(group: String,color: Int=0xFFFFFF,glow: Boolean=false,alpha: Int=255) {
            LegacyMeshes.draw(model.groups[group] ?: emptyList(),pose,buffers,if(glow)15728880 else light,overlay,color,translucent=alpha<255,alpha=alpha)
        }
        draw("Base");draw("Buttons",glow=state!=PortableScanner.IDLE)
        val led=when(state) { PortableScanner.BOOT->0xE66600;PortableScanner.RUN->0x00FF00;PortableScanner.STOP->0xFF0000;else->0x808080 }
        draw("Led",led,state!=PortableScanner.IDLE)
        if(state!=PortableScanner.IDLE)draw("LedHalo",led,true,150)
        if(state==PortableScanner.BOOT)draw("TextInit",glow=true,alpha=102)
        if(state==PortableScanner.RUN) {
            draw("TextRun",glow=true,alpha=102)
            val battery=(ElectricalItems.energy(stack)/ElectricalItems.spec(stack)!!.capacity*4+.5).toInt().coerceIn(0,3)
            draw("TextBat$battery",glow=true,alpha=102)
            if(holder!=null && mc.level!=null) {
                val eye=holder.eyePosition
                if(lastTick!=mc.level!!.gameTime || eye.distanceToSqr(lastEye)>.0001 || kotlin.math.abs(holder.yRot-lastYaw)>.1 || kotlin.math.abs(holder.xRot-lastPitch)>.1) {
                    lastTick=mc.level!!.gameTime;lastEye=eye;lastYaw=holder.yRot;lastPitch=holder.xRot
                    pixels=ScannerImage.sample(mc.level!!,eye,holder.yRot.toDouble(),holder.xRot.toDouble(),mods.eln.config.ForgePortConfig.scannerRange.get(),spec.width,spec.height,spec.fieldOfView)
                }
                pixels?.let { image ->
                    val scale=.5f/spec.width
                    for(y in image.indices)for(x in image[y].indices) {
                        val pixel=image[y][x]
                        val noise=(((x*19+y*7+lastTick)%17)/17.0-.5)*.03
                        fun channel(value: Double)=((value+noise).coerceIn(0.0,1.0)*255).toInt()
                        val color=(channel(pixel.red) shl 16) or (channel(pixel.green) shl 8) or channel(pixel.blue)
                        val x0=.90668f-y*scale;val x1=x0-scale;val z0=-.25078f+x*scale;val z1=z0+scale
                        val points=listOf(MeshPoint(x0,.163f,z0),MeshPoint(x1,.163f,z0),MeshPoint(x1,.163f,z1),MeshPoint(x0,.163f,z1))
                        LegacyMeshes.draw(listOf(MeshFace(points,listOf(MeshUV(0f,0f),MeshUV(0f,1f),MeshUV(1f,1f),MeshUV(1f,0f)),List(4) { MeshPoint(0f,1f,0f) },LegacyMeshes.white)),pose,buffers,15728880,overlay,color)
                    }
                }
            }
        }
        val broken=(PortableScanner.damage(stack)/3).coerceAtMost(3)
        for(i in broken downTo 1)draw("ScreenDamageL$i",glow=state!=PortableScanner.IDLE,alpha=220)
        pose.popPose()
    }
    companion object { val instance by lazy { ScannerItemRenderer() } }
}
