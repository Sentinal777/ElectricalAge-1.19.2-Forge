package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import mods.eln.modern.*
import net.minecraft.client.renderer.MultiBufferSource

internal object AutoMinerVisuals {
    private data class Screen(var tick: Long=Long.MIN_VALUE,var rotation: Int=-1,var depth: Int=-1,var pixels: Array<Array<ScannerPixel>>?=null)
    private val screens=java.util.WeakHashMap<FaceNodeEntity,Screen>()
    private class Motion(var time: Double,depth: Int) {
        val pipe=mods.eln.misc.PhysicalInterpolatorNoRebound(.4f,2f,.8f).apply { setPos(depth.toFloat()) }
        val speed=mods.eln.misc.RcInterpolator(1f)
        var angle=0f
    }
    private val motions=java.util.WeakHashMap<FaceNodeEntity,Motion>()
    fun render(node: FaceNodeEntity,p: PartState,model: LegacyModel,partialTick: Float,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        fun draw(name: String,color: Int=0xFFFFFF,glow: Boolean=false)=LegacyMeshes.draw(model.groups[name].orEmpty(),pose,buffers,if(glow)15728880 else light,overlay,color)
        for(name in listOf("AutominerCore","AutominerGUI","ButtonsFixed","LampSocket"))draw(name)
        val powered=p.gridState.getBoolean("autoMinerPowerOk")
        draw(if(powered)"LampOn" else "LampOff",glow=powered)
        val job=AutoMiners.job(p);val button=listOf("none","done","full","chestFull","ore").indexOf(job)
        for(i in 0..4) { pose.pushPose();pose.translate(0.0,if(powered && i==button)0.0 else .01,0.0);draw("Button$i");pose.popPose() }
        val time=(node.level?.gameTime ?: 0)+partialTick.toDouble()
        for((prefix,count) in listOf("ledA" to 11,"ledP" to 8))for(i in 0 until count)draw("$prefix$i",if(powered && ((time/17).toLong()+i*7)%5<2)0x00FF00 else 0x000000,true)
        val mc=net.minecraft.client.Minecraft.getInstance();val world=node.level
        if(powered && world!=null && mc.player?.distanceToSqr(node.blockPos.x+.5,node.blockPos.y+.5,node.blockPos.z+.5)?.let { it<400 }==true) {
            val screen=screens.getOrPut(node) { Screen() };val depth=AutoMiners.depth(p)
            if(screen.tick==Long.MIN_VALUE || world.gameTime-screen.tick>=10 || screen.rotation!=p.rotation || screen.depth!=depth) {
                screen.tick=world.gameTime;screen.rotation=p.rotation;screen.depth=depth
                // The original passes 130 directly into tan(angle/2). Retain that view, including its units.
                val eye=net.minecraft.world.phys.Vec3(node.blockPos.x+.5,node.blockPos.y+.5-maxOf(0,depth-5),node.blockPos.z+.5)
                screen.pixels=ScannerImage.sample(world,eye,FaceBasis.edges(p)[3].toYRot().toDouble(),-90.0,mods.eln.config.ForgePortConfig.autoMinerRange.get().toDouble(),24,24,130.0,64.0)
            }
            pose.pushPose();pose.translate(0.0,1.5,0.0);pose.mulPose(Vector3f.YP.rotationDegrees(90f))
            pose.translate(-1.57031,.2925,-.2925);pose.mulPose(Vector3f.YP.rotationDegrees(90f))
            val size=.585f;val step=size/24
            screen.pixels?.let { image -> for(y in image.indices)for(x in image[y].indices) {
                val pixel=image[y][x];val noise=(((x*17+y*11+screen.tick)%19)/19.0-.5)*.03
                fun channel(v: Double)=((v+noise).coerceIn(0.0,1.0)*255).toInt()
                val color=(channel(pixel.red*.4) shl 16) or (channel(pixel.green*1.3) shl 8) or channel(pixel.blue*.8)
                val x0=-size+x*step;val y0=-y*step
                val points=listOf(MeshPoint(x0,y0,.001f),MeshPoint(x0+step,y0,.001f),MeshPoint(x0+step,y0-step,.001f),MeshPoint(x0,y0-step,.001f))
                LegacyMeshes.draw(listOf(MeshFace(points,listOf(MeshUV(0f,0f),MeshUV(1f,0f),MeshUV(1f,1f),MeshUV(0f,1f)),List(4) { MeshPoint(0f,0f,1f) },LegacyMeshes.white)),pose,buffers,15728880,overlay,color)
            } }
            pose.pushPose();pose.mulPose(Vector3f.YP.rotationDegrees(180f));pose.scale(1f/128,-1f/128,1f)
            val logs=p.gridState.getList("autoMinerLog",8)
            for(i in 0 until minOf(9,logs.size)) {
                val entry=logs.getString(i);val count=entry.substringAfter(':',"0").toIntOrNull() ?: 0
                val text=net.minecraft.network.chat.Component.translatable("gui.eln.auto_miner_log_"+entry.substringBefore(':'),count)
                mc.font.drawInBatch(text,80f,1f+i*8,if(i==0)0x55FF55 else 0x006600,false,pose.last().pose(),buffers,false,0,15728880)
            }
            pose.popPose();pose.popPose()
        }
        if(world==null)return // The original held model draws the body without a mining column.
        val depth=AutoMiners.depth(p)
        val motion=motions.getOrPut(node) { Motion(time,depth) };val dt=((time-motion.time)/20).coerceIn(0.0,.1).toFloat();motion.time=time
        motion.pipe.target=depth.toFloat();motion.pipe.step(dt);if(motion.pipe.get()<0)motion.pipe.setPos(0f)
        motion.speed.target=if(job=="ore")360f else 0f;motion.speed.step(dt);motion.angle=(motion.angle+motion.speed.get()*dt)%360
        pose.pushPose();pose.translate(0.0,1.5,0.0);pose.mulPose(Vector3f.YP.rotationDegrees(90f))
        pose.mulPose(Vector3f.YP.rotationDegrees(-motion.angle))
        pose.pushPose();pose.scale(.99f,.99f,.99f);draw("MinerPipe");pose.popPose()
        val length=motion.pipe.get().toInt();pose.translate(0.0,-(motion.pipe.get()-length).toDouble(),0.0)
        for(i in length+2 downTo 1) { draw(if(i==1)"MinerHead" else "MinerPipe");pose.translate(0.0,-1.0,0.0) }
        pose.popPose()
    }
}
