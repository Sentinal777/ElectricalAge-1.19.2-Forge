package mods.eln.modern

import mods.eln.Eln
import mods.eln.config.ForgePortConfig
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.nbt.NbtElectricalGateOutputProcess

/** Original 0–2500 rad/s adjustable tachometer with the standard finite 5 V output stage. */
class TachometerRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private val load=ElectricalLoad().apply { serialResistance=.05;state=p.voltage }
    private val output=NbtElectricalGateOutputProcess("tachometer",load).apply {
        coulombs=Eln.gateOutputCurrent*.05/ForgePortConfig.simulationSteps.get()/Eln.SVU
    }
    init { e.circuit.root.addState(load);e.circuit.root.addComponent(output);e.pins[0]=load;e.pins[2]=load }
    fun beforeStep() { output.setVoltageSafe(SensorSettings.output(e.circuit.mechanics.speed(p),p.expression)) }
    fun afterStep() { p.voltage=load.voltage;p.current=load.current }
    fun afterTick() { p.signal=(p.voltage/5*15).toInt().coerceIn(0,15) }
}
