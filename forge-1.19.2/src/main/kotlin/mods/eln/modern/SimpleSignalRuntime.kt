package mods.eln.modern

import mods.eln.Eln
import mods.eln.config.ForgePortConfig
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.nbt.NbtElectricalGateOutputProcess
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.level.LightLayer
import kotlin.math.ceil

/** Original single-terminal controls/sensors; only the creative signal source joins all four edges. */
object SimpleSignals {
    val sensors=setOf(Behavior.REDSTONE_INPUT,Behavior.REDSTONE_OUTPUT,Behavior.DAYLIGHT_SENSOR,Behavior.LIGHT_SENSOR,Behavior.WEATHER_SENSOR)
    fun supported(p: PartState)=p.definition.behavior in sensors || p.definition.id in setOf("signal_source","signal_trimmer","signal_switch","signal_button") && p.definition.contact==null
    fun pin(p: PartState)=if(p.definition.behavior in sensors)1 else 0
    fun activate(node: FaceNodeEntity,p: PartState): Boolean {
        if(p.definition.id !in setOf("signal_switch","signal_button") || p.definition.contact!=null || node.isRemoved || node.parts[p.face.get3DDataValue()]!==p)return false
        p.enabled=!p.enabled
        if(p.definition.id=="signal_button") { p.gridState.putDouble("simpleButtonLeft",if(p.enabled).21 else 0.0);p.timer=if(p.enabled)5 else 0 }
        val world=node.level as? ServerLevel ?: return false
        world.playSound(null,node.blockPos,SoundEvents.UI_BUTTON_CLICK,SoundSource.BLOCKS,.3f+world.random.nextFloat()*.3f,1f)
        node.setChanged();node.sync();return true
    }
    fun configureTrimmer(node: FaceNodeEntity,p: PartState,value: Double): Boolean {
        if(p.definition.id!="signal_trimmer" || !value.isFinite() || value !in 0.0..5.0 || node.isRemoved || node.parts[p.face.get3DDataValue()]!==p)return false
        p.value=value;node.setChanged();node.sync();return true
    }
}

class SimpleSignalRuntime(private val e: ElementRuntime) {
    private val p=e.part;private val world=e.circuit.level;private val kind=p.definition.behavior
    private val creative=p.definition.id=="signal_source"
    private val input=kind==Behavior.REDSTONE_OUTPUT
    private val load=ElectricalLoad().apply { serialResistance=.05;state=p.voltage;e.circuit.root.addState(this);e.circuit.root.addComponent(Resistor(this,null).setResistance(1e12)) }
    private val ideal=if(creative)VoltageSource("creative_signal",load,null).also(e.circuit.root::addComponent)else null
    private val gate=if(!creative && !input)NbtElectricalGateOutputProcess("simple_signal",load).apply { coulombs=Eln.gateOutputCurrent*.05/ForgePortConfig.simulationSteps.get()/5;setVoltageSafe(p.voltage);e.circuit.root.addComponent(this) }else null
    init {
        if(creative || p.legacySimpleSignal)e.pins.fill(load)else e.pins[SimpleSignals.pin(p)]=load
        if(!p.gridState.getBoolean("simpleInitialized")) {
            p.gridState.putBoolean("simpleInitialized",true);p.gridState.putDouble("simpleOutput",p.voltage)
            p.gridState.putFloat("simpleWeatherFiltered",if(p.legacySimpleSignal)(p.voltage/5).toFloat().coerceIn(0f,1f)else 0f)
            if(p.definition.id=="signal_button" && !p.gridState.contains("simpleButtonLeft")) { p.gridState.putDouble("simpleButtonLeft",p.timer*.05);p.enabled=p.timer>0 }
        }
        setOutput()
    }
    private fun setOutput() {
        val value=when(p.definition.id) {
            "signal_source" -> if(p.enabled)p.value else 0.0
            "signal_trimmer" -> if(!p.legacySimpleSignal || p.enabled)p.value else 0.0
            "signal_switch","signal_button" -> if(p.enabled)5.0 else 0.0
            else -> p.gridState.getDouble("simpleOutput")
        }
        ideal?.voltage=value.takeIf(Double::isFinite)?.coerceIn(0.0,5.0) ?: 0.0;gate?.setVoltageSafe(value)
    }
    fun beforeTick() {
        if(p.definition.id=="signal_button" && p.enabled) {
            val left=p.gridState.getDouble("simpleButtonLeft")-.05;p.gridState.putDouble("simpleButtonLeft",left.coerceAtLeast(0.0));p.timer=ceil(left.coerceAtLeast(0.0)/.05).toInt()
            if(left<0) { p.enabled=false;e.node.sync() }
        }
        if(kind==Behavior.REDSTONE_INPUT) {
            var left=p.gridState.getDouble("simpleRedstoneLeft")
            if(left<=0) {
                var signal=0
                for(side in Direction.values()) {
                    val neighbor=e.node.blockPos.relative(side)
                    if(world.chunkSource.getChunkNow(neighbor.x shr 4,neighbor.z shr 4)==null)continue
                    signal=maxOf(signal,world.getSignal(neighbor,side),world.getSignal(neighbor,p.face.opposite))
                }
                p.gridState.putInt("simpleRedstoneValue",signal.coerceIn(0,15));p.gridState.putDouble("simpleOutput",signal.coerceIn(0,15)*5.0/15)
                left=.15+world.random.nextDouble()*.15
            }else left=(left-.05).coerceAtLeast(0.0)
            p.gridState.putDouble("simpleRedstoneLeft",left)
        }
        if(kind in setOf(Behavior.DAYLIGHT_SENSOR,Behavior.LIGHT_SENSOR,Behavior.WEATHER_SENSOR)) {
            var elapsed=p.gridState.getDouble("simpleSampleElapsed")+.05
            if(elapsed>.2) {
                elapsed-=.2
                if(kind==Behavior.WEATHER_SENSOR) {
                    val target=if(world.isThundering)1f else if(world.isRaining).5f else 0f
                    val old=p.gridState.getFloat("simpleWeatherFiltered")
                    // Preserve the original process: its 3-second RC advances by one 0.05-second step per sample.
                    val filtered=old+(target-old)*(1f/3f)*.05f
                    p.gridState.putFloat("simpleWeatherFiltered",filtered);p.gridState.putDouble("simpleOutput",filtered*5.0)
                }else {
                    var daylight=0
                    if(world.dimensionType().hasSkyLight()) {
                        val sky=(world.getBrightness(LightLayer.SKY,e.node.blockPos)-world.skyDarken).coerceAtLeast(0)
                        var angle=world.getSunAngle(1f)
                        angle+=(if(angle<Math.PI.toFloat())-angle else (2*Math.PI).toFloat()-angle)*.2f
                        daylight=Math.round(sky*Mth.cos(angle)).coerceIn(0,15)
                    }
                    val level=if(kind==Behavior.LIGHT_SENSOR)maxOf(daylight,world.getBrightness(LightLayer.BLOCK,e.node.blockPos))else daylight
                    p.gridState.putInt("simpleLight",level);p.gridState.putDouble("simpleOutput",level*5.0/15)
                }
            }
            p.gridState.putDouble("simpleSampleElapsed",elapsed)
        }
        setOutput()
    }
    fun afterStep(dt: Double) {
        p.voltage=load.voltage;p.voltageB=0.0
        p.current=if(ideal!=null)-ideal.current else gate?.let { (it.voltage-load.voltage)*it.coulombs/dt } ?: load.current
        p.energy+=kotlin.math.abs(p.voltage*p.current)*dt
        p.pinVoltages=DoubleArray(4) { e.pins[it]?.voltage ?: 0.0 }
        p.active=if(input)p.signal>0 else (ideal?.voltage ?: gate?.voltage ?: 0.0)>2.5
    }
    fun afterTick() {
        if(!input)return
        var left=p.gridState.getDouble("simpleRedstoneOutputLeft")
        if(left<=0) {
            val value=Math.round(p.voltage*3).toInt().coerceIn(0,15)
            if(value!=p.signal) { p.signal=value;left=.2;world.updateNeighborsAt(e.node.blockPos,e.node.blockState.block);e.node.sync() }
        }else left=(left-.05).coerceAtLeast(0.0)
        p.gridState.putDouble("simpleRedstoneOutputLeft",left)
    }
}
