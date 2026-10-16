package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import mods.eln.misc.RcInterpolator
import mods.eln.modern.*
import net.minecraft.client.renderer.MultiBufferSource

object EnvironmentVisuals {
    private class WindMotion {
        var tick=Long.MIN_VALUE
        val wind=RcInterpolator(5f)
        var angle=0f
    }
    private val winds=java.util.WeakHashMap<FaceNodeEntity,Array<WindMotion?>>()
    private fun wind(node: FaceNodeEntity,p: PartState): WindMotion {
        val entries=winds.getOrPut(node) { arrayOfNulls(6) }
        return entries[p.face.ordinal] ?: WindMotion().also { entries[p.face.ordinal]=it }
    }
    fun observe(node: FaceNodeEntity,p: PartState) {
        if(p.definition.environment?.kind!="WIND")return
        val world=node.level ?: return;val motion=wind(node,p)
        if(motion.tick==world.gameTime)return
        motion.tick=world.gameTime;motion.wind.target=p.thermalState.getDouble("envMeasured").toFloat();motion.wind.step(.05f)
        motion.angle=(motion.angle+motion.wind.get()*.05f*20)%360f
    }
    internal fun render(node: FaceNodeEntity,p: PartState,model: LegacyModel,partial: Float,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        val spec=p.definition.environment ?: return
        fun draw(group: String,color: Int=0xFFFFFF,glow: Boolean=false)=LegacyMeshes.draw(model.groups[group] ?: emptyList(),pose,buffers,if(glow)15728880 else light,overlay,color)
        when(spec.kind) {
            "WIND"->{
                observe(node,p);draw("base_wall")
                val motion=wind(node,p);val angle=motion.angle+motion.wind.get()*partial*.05f*20
                val x=model.property("anemometer","originX").toDouble();val y=model.property("anemometer","originY").toDouble();val z=model.property("anemometer","originZ").toDouble()
                pose.pushPose();pose.translate(x,y,z);pose.mulPose(Vector3f.YP.rotationDegrees(angle));pose.translate(-x,-y,-z);draw("anemometer");pose.popPose()
            }
            "ENTITY"->{
                draw("Detector")
                if(p.active) {
                    val filter=(PartInventory.get(node,p).getStackInSlot(0).item as? MaterialItem)?.definition
                    draw("HaloMask",filter?.value?.toInt() ?: 0xFFFF00,true)
                }
            }
            "FIRE","FIRE_BUZZER"->{
                draw("Detector")
                val on=p.thermalState.getBoolean("envPowered") && (p.thermalState.getBoolean("envFire") || ((node.level?.gameTime ?: 0L)%100<4))
                draw("Led",if(on)0xFFFFFF else 0x808080,on)
            }
            else->draw("main")
        }
    }
}
