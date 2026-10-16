package mods.eln.modern

import mods.eln.Eln
import mods.eln.config.ForgePortConfig
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalGateOutputProcess
import net.minecraft.sounds.SoundSource

/** Original retriggerable timer runs on the 400 Hz fast-thermal clock, independently of MNA precision. */
class TimerRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private val input=ElectricalLoad().apply { serialResistance=.05;state=p.pinVoltages[0] }
    private val out=ElectricalLoad().apply { serialResistance=.05;state=p.voltage }
    private val output=NbtElectricalGateOutputProcess("timer",out).apply { coulombs=Eln.gateOutputCurrent*.05/ForgePortConfig.simulationSteps.get()/Eln.SVU }
    private var high=p.thermalState.getBoolean("timerHigh")
    private var left=p.thermalState.getDouble("timerLeft").coerceAtLeast(0.0)
    private var ticks=p.thermalState.getInt("timerTicks")
    init {
        e.circuit.root.addState(input);e.circuit.root.addState(out)
        e.circuit.root.addComponent(Resistor(input,null).setResistance(1e12));e.circuit.root.addComponent(output)
        e.pins[0]=input;e.pins[2]=out
    }
    fun beforeTick() {
        when(p.thermalState.getString("timerCommand")) { "set"->left=p.value;"reset"->left=0.0 }
        p.thermalState.remove("timerCommand")
    }
    fun beforeStep() { output.setVoltageSafe(if(p.active)5.0 else 0.0) }
    fun fastStep(dt: Double) {
        val old=high
        if(high) { if(input.voltage<1.0)high=false } else if(input.voltage>3.0)high=true
        if(high)left=p.value
        p.active=left!=0.0
        if(p.active && !high) {
            left=(left-dt).coerceAtLeast(0.0);ticks++
            if(ticks%200==0)e.circuit.level.playSound(null,e.node.blockPos,OriginalSounds.event("timer"),SoundSource.BLOCKS,.01f,1f)
        }
        if(old!=high)e.node.sync()
    }
    fun afterStep() { p.voltage=out.voltage;p.current=out.current;p.pinVoltages[0]=input.voltage;p.pinVoltages[2]=out.voltage }
    fun persist() { p.thermalState.putBoolean("timerHigh",high);p.thermalState.putDouble("timerLeft",left);p.thermalState.putInt("timerTicks",ticks) }
    fun afterTick() { p.signal=if(p.active)15 else 0;persist();e.node.setChanged() }
}

object TimerSettings {
    /** Source GUI accepted arbitrary floats; nonnegative finite intervals prevent invalid saved countdowns. */
    fun duration(value: Double)=value.isFinite() && value>=0.0 && value<=Float.MAX_VALUE.toDouble()
    fun command(node: FaceNodeEntity,p: PartState,command: String): Boolean {
        if(p.definition.behavior!=Behavior.TIMER || command !in setOf("set","reset"))return false
        p.thermalState.putString("timerCommand",command);node.setChanged();return true
    }
}
