package mods.eln.modern.thermal

import mods.eln.modern.*
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.FurnaceProcess
import mods.eln.sim.mna.component.Resistor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeType
import net.minecraftforge.common.ForgeHooks

/** Original finite combustion buffer, four upgrade slots, and furnace regulator equations. */
class HeatFurnaceRuntime(private val thermal: ThermalRuntime) {
    private val e=thermal.e;private val p=e.part
    private val inventory=PartInventory.get(e.node,p)
    private val furnace=FurnaceProcess(thermal.load).apply {
        nominalCombustibleEnergy=80000.0*2/3;nominalPower=4000.0;combustibleEnergy=p.fuelEnergy
        setGainMin(.1);gain=p.thermalState.getDouble("gain").takeIf { it in .1..1.0 } ?: p.value.coerceIn(.1,1.0)
    }
    private var buffer=p.thermalState.getDouble("fuelBuffer").coerceAtLeast(0.0)
    private var integrated=p.thermalState.getDouble("integrated").takeIf { it.isFinite() } ?: 0.0
    private var lastHit=p.thermalState.getDouble("lastHit").takeIf { it.isFinite() } ?: 0.0
    private var lastRegulator=p.thermalState.getString("regulator")
    private val control=ElectricalLoad().apply { serialResistance=.05 }
    init {
        e.circuit.root.addState(control);e.circuit.root.addComponent(Resistor(control,null).setResistance(1e12))
        e.pins[1]=control;e.pins[3]=control
    }
    private fun material(slot: Int)=(inventory.getStackInSlot(slot).item as? MaterialItem)?.definition
    fun beforeTick() {
        val insulation=material(2)
        var insulationFactor=1.0
        if(insulation?.kind=="THERMAL_INSULATION") {
            if(thermal.load.temperatureCelsius>insulation.maximumTemperature)inventory.extractItem(2,1,false)
            else insulationFactor=insulation.value
        }
        thermal.load.Rp=p.definition.thermal!!.rp/insulationFactor
        furnace.nominalPower=4000.0+500*inventory.getStackInSlot(3).count.coerceAtMost(8)
        if(p.channel=="external")p.enabled=control.voltage/5>=.1
        val fuel=inventory.getStackInSlot(0)
        if(p.enabled && !fuel.isEmpty && furnace.combustibleEnergy+buffer<furnace.nominalCombustibleEnergy) {
            val energy=ForgeHooks.getBurnTime(fuel,RecipeType.SMELTING)*50.0
            if(energy>0) {
                buffer+=energy
                val container=fuel.craftingRemainingItem
                inventory.extractItem(0,1,false)
                if(!container.isEmpty)inventory.setStackInSlot(0,container)
            }
        }
        val transfer=minOf(buffer,(furnace.nominalCombustibleEnergy-furnace.combustibleEnergy).coerceAtLeast(0.0))
        buffer-=transfer;furnace.combustibleEnergy+=transfer
        val regulator=material(1)
        val identity=if(p.channel=="external")"external" else regulator?.id ?: "manual"
        if(identity!=lastRegulator) { integrated=0.0;lastHit=thermal.load.temperatureCelsius;lastRegulator=identity }
        if(identity=="manual")furnace.gain=p.value.coerceIn(.1,1.0)
    }
    fun process(dt: Double) {
        val watts=furnace.power
        furnace.process(dt)
        p.energy+=watts*dt
        p.powerFactor=(watts/4000).coerceIn(0.0,4.0);p.active=watts>1.0
        // The original furnace process precedes its regulator in the fast thermal list.
        val regulator=material(1)
        val hit=thermal.load.temperatureCelsius
        val target=p.expression.toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
        if(p.channel=="external") {
            furnace.gain=(control.voltage/5).coerceIn(.1,1.0)
        } else when(regulator?.kind) {
            "ON_OFF_REGULATOR" -> {
                val half=regulator.value*500/2
                if(hit>target+half)furnace.gain=.1
                if(hit<target-half)furnace.gain=1.0
            }
            "ANALOG_REGULATOR" -> {
                val error=target-hit
                val proportional=error*10/500
                var command=proportional-(hit-lastHit)*.1/500*dt
                integrated+=error*dt*.1/500
                if(integrated>1-proportional)integrated=(1-proportional).coerceAtLeast(0.0)
                else if(integrated< -1+proportional)integrated=(-1+proportional).coerceAtMost(0.0)
                command+=integrated;furnace.gain=command.coerceIn(.1,1.0);lastHit=hit
            }
        }
    }
    fun persist() {
        p.fuelEnergy=furnace.combustibleEnergy
        p.thermalState.putDouble("fuelBuffer",buffer);p.thermalState.putDouble("gain",furnace.gain)
        p.thermalState.putDouble("integrated",integrated);p.thermalState.putDouble("lastHit",lastHit)
        p.thermalState.putString("regulator",lastRegulator)
    }
    companion object {
        fun fuelEnergy(stack: ItemStack)=ForgeHooks.getBurnTime(stack,RecipeType.SMELTING)*50.0
        fun target(text: String)=text.toDoubleOrNull()?.takeIf { it.isFinite() && it in -100.0..1000.0 }
    }
}
