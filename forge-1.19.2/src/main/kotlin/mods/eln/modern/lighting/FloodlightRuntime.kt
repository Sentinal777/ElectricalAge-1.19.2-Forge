package mods.eln.modern.lighting

import mods.eln.Eln
import mods.eln.modern.*
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.*

object FloodlightSettings {
    fun parse(text: String): Pair<Double,Double>? {
        val v=text.split(',').map { it.trim().toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null }
        return if(v.size==2 && v[0] in 0.0..180.0 && v[1] in 0.0..45.0)v[0] to v[1]else null
    }
}
/** Original two independent halogen loads and the original flat-ended, multi-ray light cone. */
class FloodlightRuntime(val e: ElementRuntime) {
    private val p=e.part;private val inventory=PartInventory.get(e.node,p)
    private val motorized=p.definition.id=="motorized_floodlight"
    private val bulbs=List(2) { i -> (inventory.getStackInSlot(i).item as? MaterialItem)?.definition?.takeIf { it.bulb?.socket=="TUBE" } }
    private val load=ElectricalLoad().apply { serialResistance=.05;state=p.voltage }
    private val sinks=bulbs.map { b -> Resistor(load,null).setResistance(b?.let { it.nominalVoltage.pow(2)/it.value } ?: 1e9) }
    private val controls=List(3) { ElectricalLoad().apply { serialResistance=.05 } }
    private var watts=0.0
    init {
        e.circuit.root.addState(load);sinks.forEach(e.circuit.root::addComponent);e.pins[3]=load
        if(motorized)for(i in 0..2) { val gate=controls[i];e.circuit.root.addState(gate);e.circuit.root.addComponent(Resistor(gate,null).setResistance(1e9));e.pins[i]=gate }
    }
    fun beforeTick() { watts=0.0 }
    fun afterStep(dt: Double) { p.voltage=load.voltage;p.current=sinks.sumOf { it.current };val power=load.voltage*p.current;watts+=power*dt/.05;p.energy+=power*dt }
    fun afterTick() {
        val (tilt,spread)=FloodlightSettings.parse(p.expression) ?: (0.0 to 0.0)
        val swivel=if(motorized)(controls[2].voltage/5).coerceIn(0.0,1.0)*360 else p.value.coerceIn(0.0,360.0)
        val head=if(motorized)(controls[0].voltage/5).coerceIn(0.0,1.0)*180 else tilt
        val width=if(motorized)(controls[1].voltage/5).coerceIn(0.0,1.0)*45 else spread
        p.gridState.putDouble("swivel",swivel);p.gridState.putDouble("head",head);p.gridState.putDouble("beamWidth",width)
        var light=0;var range=0
        for((i,b) in bulbs.withIndex())if(b!=null) {
            val spec=b.bulb!!;val voltage=abs(p.voltage)
            var brightness=((voltage-b.nominalVoltage*.5)/(b.nominalVoltage*.5)*spec.nominalLight*15).toInt().coerceIn(0,15)
            val tag=inventory.getStackInSlot(i).orCreateTag
            var hours=if(tag.contains("life"))tag.getDouble("life").takeIf(Double::isFinite)?.coerceIn(0.0,1e6) ?: spec.hours else spec.hours
            if(Eln.config.getBooleanOrElse("simulation.lampAging",true) && !Eln.config.getBooleanOrElse("lamp.infiniteHalogenLife",false))hours=(hours-(.000008*voltage.pow(3)-.003225*voltage.pow(2)+.33*voltage).coerceAtLeast(0.0)*.05/3600).coerceAtLeast(0.0)
            tag.putDouble("life",hours)
            if(hours<=0) { inventory.extractItem(i,1,false);brightness=0 }else range+=spec.range
            p.gridState.putInt("bulbLight$i",brightness);light=max(light,brightness)
        }
        if(light>0)placeSpots(light,range,swivel,head,width)
        val changed=p.signal!=light;p.signal=light;p.active=light>0;p.thermalState.putDouble("lampWatts",watts)
        val excess=abs(p.voltage)-200*1.3
        if(excess<=0 || p.wasOvervoltage)p.voltageFault=(p.voltageFault+excess*.05).coerceAtLeast(0.0)
        p.wasOvervoltage=excess>0
        if(p.voltageFault>50 && mods.eln.config.ForgePortConfig.cableVoltageProtection.get())e.circuit.level.destroyBlock(e.node.blockPos,true)
        if(changed) { e.node.refreshLight();e.node.sync() }
    }
    private fun direction(horizontal: Double,vertical: Double): Vec3 {
        val h=horizontal*PI/180;val v=vertical*PI/180;val edges=FaceBasis.edges(p)
        val r=edges[0];val f=edges[1];val up=p.face.opposite
        val right=cos(v)*sin(h);val forward=cos(v)*cos(h);val height=sin(v)
        return Vec3(r.stepX*right+f.stepX*forward+up.stepX*height,r.stepY*right+f.stepY*forward+up.stepY*height,r.stepZ*right+f.stepZ*forward+up.stepZ*height)
    }
    private fun adjustments(vertical: Double,offset: Double,diagonal: Double): Pair<Double,Double> {
        val k0=vertical*PI/180;val o0=offset*PI/180;val d0=diagonal*PI/180
        val d=tan(d0);val o=acos(sqrt((cos(o0).pow(2)+d.pow(2))/(1+d.pow(2))))
        val b=atan(sqrt(cos(o).pow(2)/(cos(o).pow(2)-d.pow(2)*sin(o).pow(2))-1))*sign(d0)
        val a=sqrt(cos(o).pow(2)/(1+tan(k0+b).pow(2)))
        return (PI/2-atan(sign(cos(k0+b))*a/sin(o)))*180/PI to atan(sign(sin(k0+b))*sqrt((cos(o).pow(2)-a.pow(2))/(sin(o).pow(2)+a.pow(2))))*180/PI
    }
    private fun placeSpots(light: Int,range: Int,horizontal: Double,vertical: Double,width: Double) {
        val count=ceil(width*4/45).toInt();val fractions=if(count==0)listOf(0.0)else (count downTo -count).map { it.toDouble()/count }
        val rays=mutableListOf<Pair<Vec3,Double>>()
        for(fraction in fractions) {
            val offset=width/2*fraction;rays.add(direction(horizontal,vertical+offset) to offset)
            if(fraction>0)for(other in fractions)if(abs(other)!=1.0) {
                val (h,k)=adjustments(vertical,offset,90*other)
                if(h.isFinite() && k.isFinite()) { rays.add(direction(horizontal+h,k) to offset);rays.add(direction(horizontal-h,k) to offset) }
            }
        }
        val level=e.circuit.level
        for((ray,offset) in rays) {
            val distance=(range/cos(offset*PI/180)).toInt().coerceIn(0,40);var point=Vec3.atCenterOf(e.node.blockPos)
            for(step in 0 until distance) {
                point=point.add(ray);val pos=BlockPos(point)
                if(!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos) || level.getBlockState(pos).canOcclude())break
                if(step%2==1 || step==distance-1)LightSpotBlock.illuminate(level,pos,light)
            }
        }
    }
}
