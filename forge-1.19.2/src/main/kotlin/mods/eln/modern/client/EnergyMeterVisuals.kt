package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import kotlin.math.abs
import kotlin.math.pow

/** Source mechanical number wheels, including the original cubic / 27th-power carry interpolation. */
internal object EnergyMeterVisuals {
    fun render(node: FaceNodeEntity,p: PartState,model: LegacyModel,pose: PoseStack,buffers: MultiBufferSource,light: Int,overlay: Int) {
        fun draw(name: String)=LegacyMeshes.draw(model.groups[name].orEmpty(),pose,buffers,light,overlay)
        val held=node.level==null
        val energyUnit=(p.gridState.getInt("meterEnergyUnit").takeIf { p.gridState.contains("meterEnergyUnit") } ?: 1).coerceIn(0,3)
        val timeUnit=p.gridState.getInt("meterTimeUnit").coerceIn(0,1)
        val energy=if(held)13896.0 else EnergyMeters.counter(p)/10.0.pow(energyUnit*3-1)
        val time=if(held)1511.0 else EnergyMeters.time(p)/(if(timeUnit==0)360.0 else 8640.0)
        draw("Base")
        pose.pushPose()
        val ox=model.property("PowerDisk","originX",.15f).toDouble();val oy=model.property("PowerDisk","originY").toDouble();val oz=model.property("PowerDisk","originZ").toDouble()
        pose.translate(ox,oy,oz);pose.mulPose(Vector3f.YP.rotationDegrees((-energy%360).toFloat()));pose.translate(-ox,-oy,-oz);draw("PowerDisk");pose.popPose()
        if(!held && Minecraft.getInstance().player?.distanceToSqr(node.blockPos.x+.5,node.blockPos.y+.5,node.blockPos.z+.5)?.let { it>=400 }==true)return
        fun rotate(name: String,angle: Double,y: Double) {
            pose.pushPose();pose.translate(.20859,y,0.0);pose.mulPose(Vector3f.ZP.rotationDegrees((angle%360).toFloat()));pose.translate(-.20859,-y,0.0);draw(name);pose.popPose()
        }
        val sign=if(energy>.5)0.0 else if(energy<-.5)1.0 else .5-energy
        rotate("EnergySignWheel",sign*36,.15625);rotate("EnergyUnitWheel",energyUnit*36.0,.15625)
        fun digits(prefix: String,count: Int,number: Double,carryPower: Int,y: Double) {
            var value=abs(number);var propagate=value>=5;var delta=0.0
            for(i in 0 until count) {
                var rotation=value%10
                if(i==1)delta=((rotation%1)*2-1).pow(carryPower)*.5
                if(i!=0)rotation=if(propagate) { if(rotation>.5 && rotation<9.5)propagate=false;rotation.toInt()+delta }else rotation.toInt().toDouble()
                rotate(prefix+i,rotation*36,y);value/=10
            }
        }
        digits("EnergyNumberWheel",if(p.definition.id=="advanced_energy_meter")7 else 8,energy,3,.15625)
        rotate("TimeUnitWheel",timeUnit*36.0,.03125)
        digits("TimeNumberWheel",if(p.definition.id=="advanced_energy_meter")8 else 0,time,27,.03125)
    }
}
