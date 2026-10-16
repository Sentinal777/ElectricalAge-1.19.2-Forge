package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.block.model.ItemTransforms
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import kotlin.math.abs
import kotlin.math.sqrt

internal object DataLoggerVisuals {
    fun render(p: PartState,model: LegacyModel,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int,world: Boolean=true) {
        LegacyMeshes.draw(model.groups["main"].orEmpty(),pose,buffers,light,overlay,p.displayColor)
        if(!world)return
        LegacyMeshes.draw(model.groups["led"].orEmpty(),pose,buffers,15728880,overlay)
        LegacyMeshes.draw(model.groups["reflection"].orEmpty(),pose,buffers,light,overlay,0xFFFFFF,0f,0f,null,true,(model.property("main","reflc",.4f)*255).toInt())
        pose.pushPose();pose.translate(model.property("main","tx").toDouble(),model.property("main","ty").toDouble(),model.property("main","tz").toDouble())
        val axis=Vector3f(model.property("main","rx"),model.property("main","ry"),model.property("main","rz"))
        if(axis.normalize())pose.mulPose(axis.rotationDegrees(model.property("main","ra")))
        pose.scale(model.property("main","sx",1f),model.property("main","sy",1f),model.property("main","sz",1f))
        val color=when(p.definition.id) { "data_logger"->0xFF8000;"modern_data_logger"->0x00FF00;else->0x4080FF }
        graph(DataLoggers.snapshot(p),pose,buffers,overlay,color,model.property("main","mx",1.15f)-.05f,model.property("main","my",1.2f),lineHalfWidth=.009f,depth=-.006f)
        pose.popPose()
    }
    fun quad(x0: Float,y0: Float,x1: Float,y1: Float,z: Float,texture: ResourceLocation=LegacyMeshes.white)=MeshFace(
        listOf(MeshPoint(x0,y0,z),MeshPoint(x1,y0,z),MeshPoint(x1,y1,z),MeshPoint(x0,y1,z)),
        listOf(MeshUV(0f,0f),MeshUV(1f,0f),MeshUV(1f,1f),MeshUV(0f,1f)),List(4) { MeshPoint(0f,0f,1f) },texture)
    fun graph(tag: CompoundTag,pose: PoseStack,buffers: MultiBufferSource,overlay: Int,color: Int,width: Float,height: Float,lineHalfWidth: Float=.003f,depth: Float=.002f) {
        fun line(x0: Float,y0: Float,x1: Float,y1: Float,shade: Int) {
            val dx=x1-x0;val dy=y1-y0;val length=sqrt(dx*dx+dy*dy).coerceAtLeast(.0001f)
            val ox=-dy/length*lineHalfWidth;val oy=dx/length*lineHalfWidth
            val face=MeshFace(listOf(MeshPoint(x0+ox,y0+oy,depth),MeshPoint(x1+ox,y1+oy,depth),MeshPoint(x1-ox,y1-oy,depth),MeshPoint(x0-ox,y0-oy,depth)),
                List(4) { MeshUV(0f,0f) },List(4) { MeshPoint(0f,0f,1f) },LegacyMeshes.white)
            LegacyMeshes.draw(listOf(face),pose,buffers,15728880,overlay,shade)
        }
        val samples=tag.getByteArray("log").take(256)
        line(0f,0f,0f,height,color);line(0f,height,width,height,color)
        val low=tag.getDouble("minValue");val high=tag.getDouble("maxValue")
        if(tag.getBoolean("showZeroLine") && low*high<0 && high!=low) { val y=(high/(high-low)*height).toFloat();line(0f,y,width,y,color) }
        if(samples.size>1)for(i in 1 until samples.size) {
            val a=samples[i-1].toInt()+128;val b=samples[i].toInt()+128
            line(width-(i-1)*width/(samples.size-1),height-a*height/255,width-i*width/(samples.size-1),height-b*height/255,color)
        }
        val font=Minecraft.getInstance().font
        fun label(text: String,x: Float,y: Float) {
            pose.pushPose();pose.translate(x.toDouble(),y.toDouble(),(depth+if(depth<0)-.002f else .002f).toDouble());pose.scale(.01f,.01f,.01f)
            font.drawInBatch(text,0f,0f,color,false,pose.last().pose(),buffers,false,0,15728880);pose.popPose()
        }
        val units=DataLoggers.units.getOrElse(tag.getByte("unitType").toInt()) { "" }
        fun format(v: Double)=String.format(java.util.Locale.ROOT,"%.2f",v)
        label("${format(high)} $units",width+.02f,-.04f);label("${format((high+low)/2)} $units",width+.02f,height/2-.04f);label("${format(low)} $units",width+.02f,height-.04f)
        label("${format(samples.size*tag.getDouble("samplingPeriod"))} s",0f,height+.04f);label("0 s",width-.12f,height+.04f)
    }
}

/** Original printed paper renders its saved graph in inventory, hand and on the ground. */
class DataLogPrintRenderer private constructor(): BlockEntityWithoutLevelRenderer(Minecraft.getInstance().blockEntityRenderDispatcher,Minecraft.getInstance().entityModels) {
    override fun renderByItem(stack: ItemStack,type: ItemTransforms.TransformType,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        val firstPerson=type==ItemTransforms.TransformType.FIRST_PERSON_LEFT_HAND || type==ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND
        val scale=if(firstPerson).32f else .62f
        pose.pushPose();pose.translate(.08,if(firstPerson)1.05 else .92,.5);pose.scale(scale,-scale,scale)
        LegacyMeshes.draw(listOf(DataLoggerVisuals.quad(-.05f,-.05f,1.35f,1.15f,0f,ResourceLocation("eln","sprites/paper.png"))),pose,buffers,light,overlay)
        DataLoggerVisuals.graph(stack.tag ?: CompoundTag(),pose,buffers,overlay,0xFF0000,1f,1f,if(type==ItemTransforms.TransformType.GUI).05f else .003f)
        pose.popPose()
    }
    companion object { val instance by lazy { DataLogPrintRenderer() } }
}
