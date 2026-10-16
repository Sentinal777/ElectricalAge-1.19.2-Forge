package mods.eln.modern

import mods.eln.config.ForgePortConfig
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalGateOutputProcess
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3

/** Original sampled radio discovery, three receiver aggregators and unpowered radio controls. */
class WirelessRuntime(private val e: ElementRuntime) {
    private val p=e.part;private val spec=p.definition.wireless!!
    private val load=if(spec.kind in setOf("TX","RX"))ElectricalLoad().apply { serialResistance=.05;state=p.pinVoltages[0] }else null
    private val output=if(spec.kind=="RX")NbtElectricalGateOutputProcess("wireless_output",load!!).apply { coulombs=.1*.05/ForgePortConfig.simulationSteps.get()/5;setVoltageSafe(p.voltage) }else null
    private var cached: WirelessReception?=null
    init {
        load?.let { e.circuit.root.addState(it);e.circuit.root.addComponent(Resistor(it,null).pullDown());e.pins[0]=it }
        output?.let { e.circuit.root.addComponent(it);p.value=p.value.toInt().coerceIn(0,2).toDouble() }
    }
    fun beforeTick() {
        if(spec.kind=="REPEATER")p.active=true
        if(spec.kind=="SOURCE" && spec.autoReset && p.enabled) {
            val left=(if(p.gridState.contains("wirelessButtonLeft"))p.gridState.getDouble("wirelessButtonLeft")else .21)-.05
            p.gridState.putDouble("wirelessButtonLeft",left)
            if(left<0) { p.enabled=false;e.node.sync() }
        }
        if(spec.kind in setOf("TX","SOURCE")) {
            var left=p.gridState.getDouble("wirelessGlitchLeft")-.05
            var strength=p.gridState.getDouble("wirelessGlitchStrength")
            val distance=WirelessLightning.closest(e.circuit.level,e.node.blockPos)
            if(distance<64 && left<=0) { left=6.0;strength=Math.pow((64-distance)/64,1.4) }
            p.gridState.putDouble("wirelessGlitchLeft",left.coerceAtLeast(0.0));p.gridState.putDouble("wirelessGlitchStrength",strength)
            p.gridState.putDouble("wirelessGlitchOffset",WirelessSettings.glitch(left,strength))
        }
        if(spec.kind=="SOURCE") { p.active=p.enabled;p.voltage=if(p.enabled)5.0 else 0.0;p.current=0.0 }
    }
    fun beforeStep(dt: Double) {
        if(output==null)return
        var timer=p.gridState.getDouble("wirelessScanLeft")-dt
        if(cached==null || timer<0 || p.gridState.getBoolean("wirelessRescan")) {
            cached=e.circuit.wirelessReception(e.node,p.channel)
            if(timer<0)timer+=1.2+e.circuit.level.random.nextDouble()*.8
            p.gridState.remove("wirelessRescan")
        }
        p.gridState.putDouble("wirelessScanLeft",timer)
        val values=cached!!.transmitters.map(WirelessNetwork::normalized)
        val mode=p.value.toInt();var normalized=0.0
        if(values.isNotEmpty()) {
            val high=WirelessSettings.maximum(values)
            normalized=when(mode) {
                1 -> WirelessSettings.minimum(values)
                2 -> {
                    val previous=if(p.gridState.contains("wirelessPrevious"))p.gridState.getDouble("wirelessPrevious")else 1.0
                    var toggle=p.gridState.getBoolean("wirelessToggle")
                    if(high>.5 && previous<=.5)toggle=!toggle
                    p.gridState.putDouble("wirelessPrevious",high);p.gridState.putBoolean("wirelessToggle",toggle)
                    if(toggle)1.0 else 0.0
                }
                else -> high
            }
        }
        p.active=values.isNotEmpty();p.gridState.putInt("wirelessTransmitters",values.size)
        output.setVoltageSafe(normalized.coerceIn(0.0,1.0)*5)
    }
    fun afterStep() {
        load?.let { p.voltage=it.voltage;p.current=it.current;p.pinVoltages[0]=it.voltage }
    }
    companion object {
        fun activate(node: FaceNodeEntity,p: PartState,enabled: Boolean=!p.enabled) {
            p.enabled=enabled
            if(p.definition.wireless?.autoReset==true)p.gridState.putDouble("wirelessButtonLeft",if(enabled).21 else 0.0)
            p.active=p.enabled;node.setChanged();node.sync()
        }
    }
}

/** Lightning affects transmitters only for the new-strike tick; its six-second waveform is saved per device. */
object WirelessLightning {
    private var next=mutableMapOf<ServerLevel,MutableList<Vec3>>()
    private var current=emptyMap<ServerLevel,List<Vec3>>()
    fun record(event: net.minecraftforge.event.entity.EntityJoinLevelEvent) {
        val world=event.level as? ServerLevel ?: return
        if(event.entity is net.minecraft.world.entity.LightningBolt)next.getOrPut(world) { mutableListOf() }.add(event.entity.position())
    }
    fun advance() { current=next;next=mutableMapOf() }
    fun closest(world: ServerLevel,position: net.minecraft.core.BlockPos)=current[world]?.minOfOrNull { it.distanceTo(Vec3.atLowerCornerOf(position)) } ?: Double.POSITIVE_INFINITY
    fun clear() { current=emptyMap();next.clear() }
    fun unload(world: ServerLevel) { next.remove(world);current=current.filterKeys { it!==world } }
}
