package mods.eln.modern

import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import kotlin.math.*

/** Original twenty-hertz turbine governor and intake lag, backed by finite consumed fluid heat. */
class TurbineRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private val steam=p.definition.behavior==Behavior.STEAM_TURBINE
    private val maximumRate=p.definition.tankCapacity.toDouble()
    private val inertia=if(steam)20.0 else 5.0
    private val gate=ElectricalLoad().apply { serialResistance=.05;state=p.voltage }
    private var rate=p.thermalState.getDouble("turbineRate").takeIf(Double::isFinite)?.coerceIn(0.0,maximumRate) ?: 0.0
    private var chamber=p.thermalState.getDouble("turbineChamberHeat").takeIf(Double::isFinite)?.coerceAtLeast(0.0) ?: 0.0
    private var heatPerMb=p.thermalState.getDouble("turbineHeatPerMb").takeIf(Double::isFinite)?.coerceAtLeast(0.0) ?: 0.0
    init {
        e.circuit.root.addState(gate);e.circuit.root.addComponent(Resistor(gate,null).setResistance(1e9))
        e.pins[0]=gate;e.pins[2]=gate
    }
    fun afterStep() { p.voltage=gate.voltage;p.current=gate.current }
    fun afterTick() {
        val speed=e.circuit.mechanics.speed(p)
        val curve=cos((speed-200)/(200*(if(steam)1.1 else .5))*PI/2).pow(3)
        val efficiency=if(curve >= (if(steam)0.0 else .5))curve.coerceIn(0.0,1.0)else 0.0
        val throttle=if(p.connectedMask and 5==0)1.0 else (gate.voltage/5).coerceIn(0.0,1.0)
        if(!e.node.tank.isEmpty)heatPerMb=FuelFluids.joulesPerMb(e.node.tank.fluid.fluid)
        val wanted=if(efficiency>0 && p.enabled && !p.tripped)maximumRate*throttle else 0.0
        val intake=if(heatPerMb>0)e.node.consumeFuel(p,wanted*.05*heatPerMb)else 0.0
        chamber+=intake
        p.thermalState.putDouble("turbineIntakeHeat",p.thermalState.getDouble("turbineIntakeHeat")+intake)
        val actualRate=if(heatPerMb>0)intake/heatPerMb/.05 else 0.0
        rate+=(actualRate-rate)*(.05/inertia)
        // A saved chamber keeps the upstream RC tail, without producing unbacked work after emptying the tank.
        val burned=min(chamber,rate*.05*heatPerMb).coerceAtLeast(0.0);chamber-=burned
        val work=burned*efficiency;e.circuit.mechanics.add(p,work)
        p.thermalState.putDouble("turbineRejectedHeat",p.thermalState.getDouble("turbineRejectedHeat")+burned-work)
        p.energy+=work;p.active=work>1e-8
        p.powerFactor=if(heatPerMb>0 && p.active)(work/.05/(maximumRate*heatPerMb)).coerceIn(0.0,1.0)else 0.0
        p.thermalState.putDouble("turbineEfficiency",efficiency)
        p.thermalState.putDouble("turbineWatts",work/.05)
        persist()
    }
    fun persist() {
        p.thermalState.putDouble("turbineRate",rate);p.thermalState.putDouble("turbineChamberHeat",chamber)
        p.thermalState.putDouble("turbineHeatPerMb",heatPerMb)
    }
}
