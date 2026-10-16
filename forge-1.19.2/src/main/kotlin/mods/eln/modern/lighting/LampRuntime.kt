package mods.eln.modern.lighting

import mods.eln.Eln
import mods.eln.modern.*
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.*

/** Original bulb load, voltage threshold, fluorescent warm-up and lifetime in hours. */
class LampRuntime(val e: ElementRuntime) {
    private val p=e.part;private val spec=p.definition.lamp!!
    private val inventory=PartInventory.get(e.node,p)
    val bulb=(inventory.getStackInSlot(0).item as? MaterialItem)?.definition?.takeIf { it.bulb?.socket==spec.socket }
    private val cable=(inventory.getStackInSlot(1).item as? PartItem)?.definition?.takeIf { it.behavior==Behavior.CABLE }
    val hasCable get()=cable!=null
    val wired get()=p.enabled
    val resistance get()=bulb?.let { it.nominalVoltage.pow(2)/it.value } ?: 1e9
    private val load=ElectricalLoad().apply { serialResistance=cable?.resistance ?: 1e9;state=p.voltage }
    private val sink=Resistor(load,null).setResistance(resistance)
    var supply: LampSupplyRuntime?=null
    private var watts=0.0
    private var stability=p.thermalState.getDouble("lampStability").coerceAtLeast(0.0)
    init {
        if(wired) {
            e.circuit.root.addState(load);e.circuit.root.addComponent(sink)
            if(cable!=null)for(i in 0..3)if(spec.sidewaysConnections || i in setOf(1,3))e.pins[i]=load
        }
    }
    fun beforeTick() { watts=0.0 }
    fun afterStep(dt: Double) {
        p.voltage=if(wired)load.voltage else supply?.load?.voltage ?: 0.0
        p.current=if(hasCable && bulb!=null)p.voltage/resistance else 0.0
        val power=p.voltage*p.current;watts+=power*dt/.05;p.energy+=power*dt
    }
    private fun direction(): Vec3 {
        val angle=p.value.coerceIn(spec.minimumAngle,spec.maximumAngle)*PI/180
        val edge=FaceBasis.edges(p)[0];val normal=p.face.opposite
        return Vec3(normal.stepX*cos(angle)+edge.stepX*sin(angle),normal.stepY*cos(angle)+edge.stepY*sin(angle),normal.stepZ*cos(angle)+edge.stepZ*sin(angle))
    }
    private fun spot(direction: Vec3): BlockPos? {
        val level=e.circuit.level;val origin=e.node.blockPos;var point=Vec3.atCenterOf(origin);var pos=origin
        for(i in 0 until spec.range) {
            point=point.add(direction);val next=BlockPos(point)
            if(!level.hasChunkAt(next) || level.isOutsideBuildHeight(next))return null
            val state=level.getBlockState(next)
            if(!state.isAir && state.canOcclude() && state.block!=net.minecraft.world.level.block.Blocks.FARMLAND) { point=point.subtract(direction);pos=BlockPos(point);break }
            pos=next
        }
        var clear=0
        while(pos!=origin) {
            val state=level.getBlockState(pos)
            if(state.isAir || state.block==Content.lightSpot.get()) { clear++;if(clear==2)break }
            point=point.subtract(direction);pos=BlockPos(point)
        }
        return pos
    }
    fun afterTick() {
        var light=0
        val b=bulb?.bulb
        if(b!=null && hasCable) {
            val voltage=abs(p.voltage);val minimum=bulb!!.nominalVoltage*(if(b.type=="LED").75 else .5)
            val brightness=if(b.type=="ECO") {
                if(voltage<minimum) { stability=0.0;0.0 }else {
                    stability=(stability+voltage/(bulb.nominalVoltage*.75)*.05/4*.75).coerceAtMost(voltage/(bulb.nominalVoltage*.75))
                    if(e.circuit.level.random.nextDouble()>stability)0.0 else b.nominalLight*voltage/bulb.nominalVoltage*16
                }
            }else if(voltage<minimum)0.0 else b.nominalLight*(voltage-minimum)/(bulb.nominalVoltage-minimum)*15
            light=brightness.toInt().coerceIn(0,15)
            val stack=inventory.getStackInSlot(0)
            val tag=stack.orCreateTag
            var hours=if(tag.contains("life"))tag.getDouble("life").takeIf(Double::isFinite)?.coerceIn(0.0,1e6) ?: b.hours else b.hours
            if(Eln.config.getBooleanOrElse("simulation.lampAging",true) && !(b.type=="LED" && Eln.config.getBooleanOrElse("lamp.infiniteLedLife",false))) {
                val factor=.000008*voltage.pow(3)-.003225*voltage.pow(2)+.33*voltage
                hours=(hours-factor.coerceAtLeast(0.0)*.05/3600).coerceAtLeast(0.0)
            }
            tag.putDouble("life",hours)
            if(hours<=0) { inventory.extractItem(0,1,false);light=0 }
        }
        if(light>0 && b!=null && b.growRate>0 && e.circuit.level.random.nextDouble()<(.05*b.growRate*(abs(p.voltage)/bulb!!.nominalVoltage).pow(3)).coerceIn(0.0,1.0)) {
            var point=Vec3.atCenterOf(e.node.blockPos);val direction=direction()
            for(i in 0 until spec.range+4) {
                point=point.add(direction);val pos=BlockPos(point)
                if(!e.circuit.level.hasChunkAt(pos))break
                val state=e.circuit.level.getBlockState(pos)
                if(state.block is net.minecraft.world.level.block.CropBlock) { state.randomTick(e.circuit.level,pos,e.circuit.level.random);break }
                if(state.canOcclude())break
            }
        }
        val position=spot(direction())
        val here=position==e.node.blockPos
        if(position!=null && !here)LightSpotBlock.illuminate(e.circuit.level,position,light)
        val changed=p.signal!=light || p.thermalState.getBoolean("lampSpotHere")!=here
        p.signal=light;p.active=light>0;p.powerFactor=watts/(bulb?.value ?: 1.0)
        p.thermalState.putDouble("lampStability",stability);p.thermalState.putBoolean("lampSpotHere",here)
        p.thermalState.putDouble("lampWatts",watts);position?.let { p.thermalState.putLong("lampSpot",it.asLong()) }
        if(changed) { e.node.refreshLight();e.node.sync() }
    }
    fun persist() { p.thermalState.putDouble("lampStability",stability) }
}
