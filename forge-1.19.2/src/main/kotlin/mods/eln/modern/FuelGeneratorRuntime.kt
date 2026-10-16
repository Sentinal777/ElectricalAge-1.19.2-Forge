package mods.eln.modern

import mods.eln.Eln
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.PowerSource
import mods.eln.sim.mna.component.Resistor
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction
import kotlin.math.ceil
import kotlin.math.max

/** Original constant-power gasoline generator, including its rated twenty-minute tank. */
class FuelGeneratorRuntime(private val e: ElementRuntime) {
    private val p=e.part;private val d=p.definition;private val node=e.node
    private val nominal=d.power*Eln.config.getDoubleOrElse("balance.generation.fuelGeneratorPowerFactor",1.0)
    private val maximumVoltage=d.nominalVoltage*1.25
    private val positive=ElectricalLoad().also {
        it.state=p.voltage;it.serialResistance=d.resistance;e.circuit.root.addState(it)
        e.circuit.root.addComponent(Resistor(it,null).setResistance(1e12))
    }
    private val source=PowerSource("fuel_generator",positive)
    private var outputTick=0.0
    private var outputLimit=0.0
    private var running=false
    init {
        e.pins[0]=positive;e.pins[2]=positive;e.circuit.root.addComponent(source)
        source.setMaximumVoltage(maximumVoltage);source.setMaximumCurrent(5*nominal/maximumVoltage)
        if(!p.gridState.contains("fuelGeneratorGrace"))p.gridState.putDouble("fuelGeneratorGrace",if(p.enabled)1.0 else 0.0)
    }
    private fun reserve(amount: Double) {
        if(p.fuelEnergy>=amount || node.tank.isEmpty || !FuelFluids.accepts(Behavior.FUEL_GENERATOR,node.tank.fluid.fluid))return
        val perMb=nominal*1200/2000
        val count=ceil((amount-p.fuelEnergy)/perMb).toInt().coerceIn(0,node.tank.fluidAmount)
        p.fuelEnergy+=node.tank.drain(count,FluidAction.EXECUTE).amount*perMb
    }
    fun beforeTick() {
        outputTick=0.0
        if(p.enabled && !p.tripped)reserve(nominal*.05*1.5)
        if(p.fuelEnergy<=0 || p.tripped)p.enabled=false
        running=p.enabled
        // Even the final fractional tank cannot issue more work than its remaining fuel.
        outputLimit=if(running)minOf(nominal,p.fuelEnergy/(.05*1.5))else 0.0
    }
    fun beforeStep(dt: Double) {
        source.power=if(running)minOf(outputLimit,p.fuelEnergy/(dt*1.5))else 0.0
    }
    fun afterStep(dt: Double) {
        p.voltage=positive.voltage;p.voltageB=0.0;p.current=source.current
        p.pinVoltages=DoubleArray(4) { e.pins[it]?.voltage ?: 0.0 }
        val work=max(0.0,source.effectivePower)*dt
        check(work<=p.fuelEnergy+1e-6) { "Fuel generator issued unbacked work at ${node.blockPos}" }
        p.fuelEnergy=(p.fuelEnergy-work).coerceAtLeast(0.0);outputTick+=work;p.energy+=work
    }
    fun afterTick() {
        if(running) {
            val input=max(outputTick,nominal*.1*.05)*efficiencyFactor(outputTick/(.05*nominal))
            val loss=minOf(p.fuelEnergy,(input-outputTick).coerceAtLeast(0.0));p.fuelEnergy-=loss
            p.thermalState.putDouble("fuelGeneratorInputJ",p.thermalState.getDouble("fuelGeneratorInputJ")+outputTick+loss)
            p.thermalState.putDouble("fuelGeneratorLossJ",p.thermalState.getDouble("fuelGeneratorLossJ")+loss)
            val grace=p.gridState.getDouble("fuelGeneratorGrace")
            if(grace>0)p.gridState.putDouble("fuelGeneratorGrace",(grace-.05).coerceAtLeast(0.0))
            else if(p.voltage<maximumVoltage*.5)p.enabled=false
            if(p.fuelEnergy<=1e-9 && node.tank.isEmpty)p.enabled=false
        }
        val changed=p.active!=p.enabled;p.active=p.enabled
        p.powerFactor=if(p.active)outputTick/(.05*nominal)else 0.0
        p.thermalState.putDouble("fuelGeneratorWatts",outputTick/.05)
        p.thermalState.putDouble("fuelGeneratorTankJ",nominal*1200)
        node.setChanged();if(changed)node.sync()
    }
    companion object {
        fun setEnabled(node: FaceNodeEntity,p: PartState,enabled: Boolean) {
            val hasFuel=p.fuelEnergy>0 || !node.tank.isEmpty && FuelFluids.accepts(Behavior.FUEL_GENERATOR,node.tank.fluid.fluid)
            if(enabled && !p.enabled && hasFuel)p.gridState.putDouble("fuelGeneratorGrace",1.0)
            p.enabled=enabled && hasFuel;p.tripped=false;node.setChanged();node.sync()
        }
        fun efficiencyFactor(load: Double): Double = when(load.coerceIn(0.0,1.5)) {
            in 0.0..0.1 -> 1.375
            in 0.1..0.2 -> 1.125
            in 0.2..0.3 -> 1.05
            in 0.3..0.4 -> 1.025
            in 0.4..0.5 -> 1.01
            in 0.5..1.1 -> 1.0
            in 1.1..1.2 -> 1.05
            in 1.2..1.3 -> 1.1
            in 1.3..1.4 -> 1.15
            else -> 1.5
        }
    }
}
