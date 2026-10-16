package mods.eln.modern.thermal

import mods.eln.modern.*
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction
import kotlin.math.ceil

/** Source fluid pairings, temperature gates, conversion ratios, signal control and thermal balance. */
class HeatExchangerRuntime(private val thermal: ThermalRuntime) {
    private val e=thermal.e;private val p=e.part;private val tanks=e.node.exchanger
    private val control=ElectricalLoad().apply { serialResistance=.05 }
    private var heatPerTick=0.0
    init {
        e.pins[0]=control;e.circuit.root.addState(control);e.circuit.root.addComponent(Resistor(control,null).setResistance(1e12))
    }
    fun beforeTick() {
        heatPerTick=0.0;p.thermalState.putInt("inputMb",0);p.thermalState.putInt("outputMb",0)
        p.voltage=control.voltage;p.current=control.current;p.pinVoltages[0]=control.voltage
        p.thermalState.putDouble("control",(control.voltage/5).coerceIn(0.0,1.0))
        if(tanks.unknown || tanks.input.isEmpty)return
        val pair=HeatFluidPairs.all.firstOrNull { it.input.isSame(tanks.input.fluid.fluid) } ?: return
        val temperature=thermal.load.temperatureCelsius
        if(pair.minimum!=null && temperature<pair.minimum || pair.maximum!=null && temperature>pair.maximum)return
        val desired=ceil(pair.maximumInputMb*(control.voltage/5).coerceIn(0.0,1.0)).toInt()
        val available=tanks.output.fill(FluidStack(pair.output,pair.maximumInputMb*pair.ratio),FluidAction.SIMULATE)
        // Reserve whole conversions: the original ceil(space/ratio) could discard output when
        // only a fraction of the converted fluid fitted in the output tank.
        val amount=minOf(desired,tanks.input.fluidAmount,available/pair.ratio)
        if(amount<=0)return
        val produced=amount*pair.ratio
        tanks.input.drain(amount,FluidAction.EXECUTE);check(tanks.output.fill(FluidStack(pair.output,produced),FluidAction.EXECUTE)==produced)
        heatPerTick=produced*pair.joulesPerOutputMb*1.25
        p.thermalState.putInt("inputMb",amount);p.thermalState.putInt("outputMb",produced)
        p.thermalState.putDouble("convertedHeatJ",p.thermalState.getDouble("convertedHeatJ")+heatPerTick)
        p.voltage=control.voltage;p.active=true
    }
    fun process(dt: Double) { thermal.load.movePowerTo(heatPerTick/.05);p.energy+=kotlin.math.abs(heatPerTick/.05)*dt }
    fun persist() {
        p.thermalState.putDouble("heatWatts",heatPerTick/.05)
        p.active=heatPerTick!=0.0;p.powerFactor=kotlin.math.abs(heatPerTick/.05)/16000
    }
}
