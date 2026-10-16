package mods.eln.modern

import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import kotlin.math.*

/** Original 64 mB/s radial engine, three-second intake inertia, startup efficiency and throttle gate. */
class RadialMotorRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private val gate=ElectricalLoad().apply { serialResistance=.05;state=p.voltage }
    private var rate=p.thermalState.getDouble("radialRate").coerceIn(0.0,64.0)
    private var chamber=p.thermalState.getDouble("radialChamberHeat").coerceAtLeast(0.0)
    private var heatPerMb=p.thermalState.getDouble("radialHeatPerMb").coerceAtLeast(0.0)
    init {
        e.circuit.root.addState(gate);e.circuit.root.addComponent(Resistor(gate,null).setResistance(1e9))
        for(i in 0..3)e.pins[i]=gate
    }
    fun afterStep() { p.voltage=gate.voltage;p.current=gate.current }
    fun afterTick() {
        val speed=e.circuit.mechanics.speed(p)
        val efficiency=if(speed>175)cos((speed-200)/(200*1.5)*PI/2).pow(3).coerceAtLeast(0.0)*.5 else .25
        val throttle=if(p.connectedMask==0)1.0 else (gate.voltage/5).coerceIn(0.0,1.0)
        if(!e.node.tank.isEmpty)heatPerMb=FuelFluids.joulesPerMb(e.node.tank.fluid.fluid)
        val wanted=if(p.enabled && !p.tripped)64*throttle else 0.0
        val intake=if(heatPerMb>0)e.node.consumeFuel(p,wanted*.05*heatPerMb)else 0.0
        chamber+=intake
        p.thermalState.putDouble("radialIntakeHeat",p.thermalState.getDouble("radialIntakeHeat")+intake)
        val actualRate=if(heatPerMb>0)intake/heatPerMb/.05 else 0.0
        rate+=(actualRate-rate)*(.05/3)
        val burned=min(chamber,rate*.05*heatPerMb).coerceAtLeast(0.0);chamber-=burned
        val work=burned*efficiency;e.circuit.mechanics.add(p,work)
        p.thermalState.putDouble("radialRejectedHeat",p.thermalState.getDouble("radialRejectedHeat")+burned-work)
        p.energy+=work;p.active=work>1e-8;p.powerFactor=if(p.active)max(.75,work/.05/(64*heatPerMb)).coerceAtMost(1.0)else 0.0
        p.thermalState.putDouble("radialEfficiency",efficiency)
        p.thermalState.putDouble("radialWatts",work/.05)
        persist()
    }
    fun persist() { p.thermalState.putDouble("radialRate",rate);p.thermalState.putDouble("radialChamberHeat",chamber);p.thermalState.putDouble("radialHeatPerMb",heatPerMb) }
}
