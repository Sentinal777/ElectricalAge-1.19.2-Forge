package mods.eln.modern.thermal

import mods.eln.config.ForgePortConfig
import mods.eln.misc.FunctionTableYProtect
import mods.eln.modern.*
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeType
import kotlin.math.*

/** The original 40 J/K furnace stores coil heat; recipes remove 1000 J of that heat. */
class ElectricalFurnaceRuntime(private val thermal: ThermalRuntime) {
    private val e=thermal.e;private val p=e.part;private val node=e.node
    private val upgrades=PartInventory.get(node,p)
    private val input=ElectricalLoad().apply { serialResistance=.0125;state=p.voltage }
    private val heater=Resistor(input,null).setResistance(1e12)
    private var coil: MaterialDefinition?=null
    private var output=ItemStack.EMPTY
    private var enabledRecipe=false
    private var thermalResistance=1e12
    private var command=if(p.thermalState.contains("furnaceCommand"))p.thermalState.getDouble("furnaceCommand").coerceIn(0.0,1.0)else 1.0
    private var integrated=p.thermalState.getDouble("furnaceIntegral").takeIf(Double::isFinite) ?: 0.0
    private var lastHit=p.thermalState.getDouble("furnaceLastHit").takeIf(Double::isFinite) ?: 0.0
    private var regulatorId=p.thermalState.getString("furnaceRegulator")
    private var heatingJ=0.0
    private var smeltingJ=0.0
    init { e.circuit.root.addState(input);e.circuit.root.addComponent(heater);e.circuit.root.addComponent(Resistor(input,null).setResistance(1e12));e.pins[1]=input }
    private fun material(slot: Int)=(upgrades.getStackInSlot(slot).item as? MaterialItem)?.definition
    fun beforeTick() {
        heatingJ=0.0;smeltingJ=0.0
        coil=material(0)?.takeIf { it.kind=="HEATING_ELEMENT" }
        val stack=node.inventory.getStackInSlot(0)
        val recipe=if(stack.isEmpty)null else e.circuit.level.recipeManager.getRecipeFor(RecipeType.SMELTING,SimpleContainer(stack),e.circuit.level).orElse(null)
        val key=recipe?.id?.toString().orEmpty()
        if(key!=p.recipeId) { p.recipeId=key;p.progress=0.0 }
        output=recipe?.resultItem?.copy() ?: ItemStack.EMPTY
        val existing=node.inventory.getStackInSlot(2)
        enabledRecipe=!output.isEmpty && (existing.isEmpty || ItemStack.isSameItemSameTags(existing,output) && existing.count+output.count<=existing.maxStackSize)
        if(p.channel!="manual")p.enabled=enabledRecipe
        val hit=thermal.load.temperatureCelsius
        val insulation=material(1)?.takeIf { it.kind=="THERMAL_INSULATION" }?.value ?: 1.0
        thermal.load.Rp=(hit/lossCurve.getValue(hit)/insulation).coerceAtLeast(.1)
        thermalResistance=if(enabledRecipe)(abs(hit)+1)/smeltCurve.getValue(abs(hit)+1).coerceAtLeast(1e-9)else 1e12
        val regulator=material(2);val identity=regulator?.id.orEmpty()
        if(identity!=regulatorId) { integrated=0.0;lastHit=hit;command=if(regulator?.kind=="ON_OFF_REGULATOR")0.0 else 1.0;regulatorId=identity }
        updateResistance()
    }
    private fun updateResistance() {
        heater.resistance=if(p.enabled && coil!=null && command>.001)coil!!.nominalVoltage.pow(2)/coil!!.value/command else 1e12
    }
    fun afterElectricalStep(dt: Double) {
        heatingJ+=heater.power.coerceAtLeast(0.0)*dt
        p.voltage=input.voltage;p.voltageB=0.0;p.current=heater.current
        p.energy+=heater.power.coerceAtLeast(0.0)*dt
        p.pinVoltages[1]=input.voltage
    }
    fun process(dt: Double) {
        thermal.load.movePowerTo(heatingJ/.05)
        val smeltPower=if(enabledRecipe)max(0.0,thermal.load.temperatureCelsius/thermalResistance)else 0.0
        thermal.load.movePowerTo(-smeltPower);smeltingJ+=smeltPower*dt
        val hit=thermal.load.temperatureCelsius;val target=p.expression.toDoubleOrNull()?.takeIf(Double::isFinite) ?: 0.0
        val regulator=material(2)
        when(regulator?.kind) {
            "ON_OFF_REGULATOR" -> {
                val half=regulator.value*500/2
                if(hit>target+half)command=0.0
                if(hit<target-half)command=1.0
            }
            "ANALOG_REGULATOR" -> {
                val error=target-hit;val proportional=error*10/500
                var cmd=proportional-(hit-lastHit)*.1/500*dt
                integrated+=error*dt*.1/500
                if(integrated>1-proportional)integrated=(1-proportional).coerceAtLeast(0.0)
                else if(integrated< -1+proportional)integrated=(-1+proportional).coerceAtMost(0.0)
                cmd+=integrated;command=cmd.coerceIn(0.0,1.0);lastHit=hit
            }
            else -> command=1.0
        }
        updateResistance()
    }
    fun afterTick() {
        p.progress+=smeltingJ
        if(enabledRecipe && p.progress>=1000) {
            val stack=node.inventory.getStackInSlot(2)
            node.inventory.setStackInSlot(2,if(stack.isEmpty)output.copy()else stack.copy().apply { grow(output.count) })
            node.inventory.extractItem(0,1,false);p.progress-=1000;node.setChanged()
        }
        p.active=heatingJ/.05>5;p.powerFactor=heatingJ/.05/(coil?.value ?: 1.0);p.processFraction=(p.progress/1000).coerceIn(0.0,1.0)
        val rating=coil?.nominalVoltage ?: 100000.0;val overflow=abs(input.voltage)-rating*1.3
        if(overflow<=0 || p.wasOvervoltage)p.voltageFault=(p.voltageFault+overflow*.05).coerceAtLeast(0.0)
        p.wasOvervoltage=overflow>0
        if(p.voltageFault>rating*.25 && ForgePortConfig.cableVoltageProtection.get())e.circuit.level.destroyBlock(node.blockPos,true)
        p.thermalState.putDouble("heatingWatts",heatingJ/.05);p.thermalState.putDouble("smeltingWatts",smeltingJ/.05)
        persist()
    }
    fun persist() {
        p.thermalState.putDouble("furnaceCommand",command);p.thermalState.putDouble("furnaceIntegral",integrated)
        p.thermalState.putDouble("furnaceLastHit",lastHit);p.thermalState.putString("furnaceRegulator",regulatorId)
    }
    companion object {
        val powers=doubleArrayOf(0.0,20.0,40.0,80.0,160.0,240.0,360.0,540.0,756.0,1058.4,1481.76)
        val smeltCurve=FunctionTableYProtect(powers,800.0,0.0,100000.0)
        val lossCurve=FunctionTableYProtect(DoubleArray(powers.size) { powers[it]*((it+1.0)/powers.size).pow(2)*2 },800.0,.001,10000000.0)
    }
}
