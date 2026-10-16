package mods.eln.modern.thermal

import mods.eln.modern.*
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor

/** Original liquid heater: installed burner, two inventory slots, precise finite fuel, and controls. */
class FuelHeatFurnaceRuntime(private val thermal: ThermalRuntime) {
    private val e=thermal.e;private val p=e.part
    private val inventory=PartInventory.get(e.node,p)
    private val control=ElectricalLoad().apply { serialResistance=.05 }
    private var gain=p.thermalState.getDouble("gain").coerceIn(0.0,1.0)
    private var integrated=p.thermalState.getDouble("integrated").takeIf { it.isFinite() } ?: 0.0
    private var lastHit=p.thermalState.getDouble("lastHit").takeIf { it.isFinite() } ?: 0.0
    private var lastRegulator=p.thermalState.getString("regulator")
    private fun material(slot: Int)=(inventory.getStackInSlot(slot).item as? MaterialItem)?.definition
    init {
        e.circuit.root.addState(control);e.circuit.root.addComponent(Resistor(control,null).setResistance(1e12))
        e.pins[0]=control;e.pins[1]=control;e.pins[3]=control
    }
    fun beforeTick() {
        if(material(0)?.kind!="FUEL_BURNER")p.enabled=false
        p.thermalState.putInt("burnerType",material(0)?.takeIf { it.kind=="FUEL_BURNER" }?.variant ?: -1)
        val regulator=if(p.channel=="external")"external" else material(1)?.id ?: "manual"
        if(regulator!=lastRegulator) { integrated=0.0;lastHit=thermal.load.temperatureCelsius;lastRegulator=regulator }
    }
    fun process(dt: Double) {
        val hit=thermal.load.temperatureCelsius
        val target=HeatFurnaceRuntime.target(p.expression) ?: 0.0
        gain=if(p.channel=="external")(control.voltage/5).coerceIn(0.0,1.0)else p.value.coerceIn(0.0,1.0)
        if(p.channel!="external")when(val regulator=material(1)) {
            null -> Unit
            else -> when(regulator.kind) {
                "ON_OFF_REGULATOR" -> {
                    gain=p.thermalState.getDouble("gain").coerceIn(0.0,1.0)
                    val half=regulator.value*500/2
                    if(hit>target+half)gain=0.0
                    if(hit<target-half)gain=1.0
                }
                "ANALOG_REGULATOR" -> {
                    val error=target-hit;val proportional=error*20/500
                    var command=proportional-(hit-lastHit)*.1/500*dt
                    integrated+=error*dt*.2/500
                    if(integrated>1-proportional)integrated=(1-proportional).coerceAtLeast(0.0)
                    else if(integrated< -1+proportional)integrated=(-1+proportional).coerceAtMost(0.0)
                    command+=integrated;gain=command.coerceIn(0.0,1.0);lastHit=hit
                }
                else -> Unit
            }
        }
        val nominal=if(p.enabled)material(0)?.takeIf { it.kind=="FUEL_BURNER" }?.value ?: 0.0 else 0.0
        val used=e.node.consumeFuel(p,gain*nominal*dt)
        val watts=used/dt;thermal.load.movePowerTo(watts)
        p.energy+=used;p.active=watts>1;p.powerFactor=if(nominal>0)watts/nominal else 0.0
        p.thermalState.putDouble("gain",gain);p.thermalState.putDouble("heatWatts",watts)
    }
    fun persist() {
        p.thermalState.putDouble("gain",gain);p.thermalState.putDouble("integrated",integrated);p.thermalState.putDouble("lastHit",lastHit)
        p.thermalState.putString("regulator",lastRegulator)
    }
}
