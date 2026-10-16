package mods.eln.modern

import mods.eln.Eln
import mods.eln.misc.FunctionTable
import mods.eln.misc.RcRcInterpolator
import mods.eln.modern.grid.GridMultiblock
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.PowerSource
import mods.eln.sim.mna.component.Resistor
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LiquidBlock
import kotlin.math.*

/** Original wind and flowing-water power sources, with loaded-only environmental sampling. */
class RenewableRuntime(private val e: ElementRuntime) {
    private val p=e.part;private val d=p.definition;private val node=e.node;private val level=e.circuit.level
    private val wind=d.behavior==Behavior.WIND_TURBINE
    private val factor=Eln.config.getDoubleOrElse(if(wind)"balance.generation.windTurbinePowerFactor" else "balance.generation.waterTurbinePowerFactor",1.0)
    private val nominal=d.power*factor
    private val positive=ElectricalLoad().also {
        it.serialResistance=d.resistance;it.state=p.voltage;e.circuit.root.addState(it)
        e.circuit.root.addComponent(Resistor(it,null).setResistance(1e12))
    }
    private val source=PowerSource(if(wind)"wind_turbine" else "water_turbine",positive)
    private val curve=FunctionTable(doubleArrayOf(0.0,.1,.3,.5,.8,1.0,1.1,1.15,1.2).map { it*nominal }.toDoubleArray(),16.0)
    private val waterFilter=RcRcInterpolator(2f,2f)
    private var refresh=p.gridState.getDouble("renewableRefresh")
    private var exposure=if(p.gridState.contains("windExposure"))p.gridState.getDouble("windExposure")else 0.0
    private var offered=0.0
    init {
        e.pins[if(wind)Math.floorMod(3-p.gridState.getInt("windCableOrientation"),4)else 3]=positive
        e.circuit.root.addComponent(source)
        source.setMaximumVoltage(59.0);source.setMaximumCurrent(nominal*5/59)
        waterFilter.readFromNBT(p.gridState,"waterFilter")
        offered=if(wind)p.thermalState.getDouble("renewableAvailableWatts")else waterFilter.get().toDouble()
    }
    fun beforeTick() {
        refresh-=.05
        if(wind) {
            if(refresh<0) {
                refresh+=10*(.75+level.random.nextDouble()*.5)
                windExposure(level,node.blockPos)?.let { exposure=it;p.gridState.putDouble("windExposure",it) }
            }
            // Upstream explicitly resets the local noise term to zero before taking this sample.
            val speed=WorldWind.speed(level,node.blockPos.y+3)*exposure
            offered=curve.getValue(abs(speed)).coerceAtLeast(0.0)
            p.gridState.putDouble("windSpeed",speed)
        } else if(refresh<0) {
            refresh=.2
            val flow=waterFactor(level,waterPosition(node,p))
            if(flow<0)waterFilter.setValue((waterFilter.get()*(1-.5*.05)).toFloat())
            else { waterFilter.target=(flow*nominal).toFloat();waterFilter.step(.05f) }
            offered=waterFilter.get().toDouble().coerceAtLeast(0.0)
            waterFilter.writeToNBT(p.gridState,"waterFilter")
        }
        source.power=if(p.enabled)offered else 0.0
        persist()
        p.powerFactor=if(p.enabled && nominal>0)offered/nominal else 0.0
        p.thermalState.putDouble("renewableAvailableWatts",if(p.enabled)offered else 0.0)
    }
    fun afterStep(dt: Double) {
        p.voltage=positive.voltage;p.voltageB=0.0;p.current=source.current
        p.pinVoltages=DoubleArray(4) { e.pins[it]?.voltage ?: 0.0 }
        val watts=(source.voltage*source.current).coerceAtLeast(0.0)
        p.energy+=watts*dt;p.active=p.enabled && watts>1e-6
        p.thermalState.putDouble("renewableWatts",watts)
    }
    fun persist() {
        p.gridState.putDouble("renewableRefresh",refresh)
        if(!wind)waterFilter.writeToNBT(p.gridState,"waterFilter")
    }
    companion object {
        fun waterPosition(node: FaceNodeEntity,p: PartState)=GridMultiblock.offset(node.blockPos,p,listOf(1,-1,0))
        fun waterFactor(level: ServerLevel,pos: BlockPos): Double {
            val state=level.chunkSource.getChunkNow(pos.x shr 4,pos.z shr 4)?.getBlockState(pos) ?: return -1.0
            if(state.block!=Blocks.WATER)return -1.0
            if(state.getValue(LiquidBlock.LEVEL)==0)return 0.0
            val weather=if(level.isThundering)1.0 else if(level.isRaining).5 else 0.0
            return (1+.2*sin((level.dayTime/23999.0-.20)*PI*2))*(1+weather*2)
        }
        fun windExposure(level: ServerLevel,pos: BlockPos): Double? {
            var busy=-11 // Original descriptor includes its duplicate ghost reservation in this count.
            for(x in -7..7)for(y in 1..5)for(z in -2..2) {
                val at=pos.offset(x,y,z)
                val state=level.chunkSource.getChunkNow(at.x shr 4,at.z shr 4)?.getBlockState(at) ?: return null
                if(!state.isAir)busy++
            }
            return (1-busy*.07).coerceIn(0.0,1.0)
        }
    }
}
