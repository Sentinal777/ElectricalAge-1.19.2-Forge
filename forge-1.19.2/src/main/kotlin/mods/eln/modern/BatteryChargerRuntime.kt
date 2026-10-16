package mods.eln.modern

import mods.eln.config.ForgePortConfig
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/** Original one-second, four-slot charger controller; input energy is integrated at physical solver steps. */
class BatteryChargerRuntime(private val e: ElementRuntime) {
    private val p=e.part;private val d=p.definition;private val node=e.node
    private val positive=ElectricalLoad().also {
        it.state=p.voltage;it.serialResistance=d.resistance;e.circuit.root.addState(it)
        e.circuit.root.addComponent(Resistor(it,null).setResistance(1e12))
    }
    private val resistor=Resistor(positive,null).setResistance(if(p.gridState.contains("chargerResistance"))p.gridState.getDouble("chargerResistance")else 1e12)
    private var timer=p.gridState.getDouble("chargerTimer")
    private var buffer=p.gridState.getDouble("chargerEnergy").takeIf(Double::isFinite)?.coerceAtLeast(0.0) ?: 0.0
    private var boost=1.0
    private var efficiency=1.0
    init { e.pins[0]=positive;e.circuit.root.addComponent(resistor) }
    fun beforeTick() {
        val inventory=PartInventory.get(node,p);val n=inventory.getStackInSlot(4).count.coerceIn(0,5)
        boost=1.25.pow(n);efficiency=.9.pow(n)
        timer-=.05
        if(!p.enabled)resistor.resistance=1e12
        if(timer<=0) {
            timer=1.0
            p.gridState.putInt("chargerCycles",p.gridState.getInt("chargerCycles")+1)
            if(p.enabled) {
                for(index in 0..3) {
                    val stack=inventory.getStackInSlot(index);val spec=ElectricalItems.spec(stack) ?: continue
                    val accepted=minOf(buffer,spec.chargePower*boost,(spec.capacity-ElectricalItems.energy(stack)).coerceAtLeast(0.0))
                    ElectricalItems.setEnergy(stack,ElectricalItems.energy(stack)+accepted);buffer-=accepted
                    p.thermalState.putDouble("chargerDeliveredJ",p.thermalState.getDouble("chargerDeliveredJ")+accepted)
                }
                val power=minOf(d.power*boost,(2*d.power*boost-buffer).coerceAtLeast(0.0))
                resistor.resistance=if(power>1e-9)max(p.voltage*p.voltage/power,d.nominalVoltage*d.nominalVoltage/d.power/boost)else 1e12
            }
        }
        var presence=0;var charged=0
        for(index in 0..3)ElectricalItems.spec(inventory.getStackInSlot(index))?.let { spec ->
            presence=presence or (1 shl index)
            if(ElectricalItems.energy(inventory.getStackInSlot(index))>=spec.capacity)charged=charged or (1 shl index)
        }
        p.gridState.putInt("chargerPresence",presence);p.gridState.putInt("chargerCharged",charged)
        persist()
    }
    fun afterStep(dt: Double) {
        p.voltage=positive.voltage;p.voltageB=0.0;p.current=resistor.current
        p.pinVoltages=DoubleArray(4) { e.pins[it]?.voltage ?: 0.0 }
        val input=(p.voltage*p.current).coerceAtLeast(0.0)*dt
        p.energy+=input;buffer+=input*efficiency
        p.thermalState.putDouble("chargerLossJ",p.thermalState.getDouble("chargerLossJ")+input*(1-efficiency))
        p.thermalState.putDouble("chargerWatts",input/dt)
        p.active=p.enabled && input/dt>1e-5;p.powerFactor=if(p.active)input/dt/d.power else 0.0
        persist()
    }
    fun afterTick() {
        var overflow=abs(p.voltage)-d.nominalVoltage*1.3
        val previous=p.gridState.getBoolean("chargerOvervoltage")
        p.gridState.putBoolean("chargerOvervoltage",overflow>0)
        if(overflow>0 && !previous)overflow=0.0
        val reset=d.nominalVoltage*.25
        val tolerance=if(p.gridState.contains("chargerVoltageTolerance"))p.gridState.getDouble("chargerVoltageTolerance")else reset
        val remaining=minOf(reset,tolerance-.05*overflow*(.5+e.circuit.level.random.nextDouble()))
        p.gridState.putDouble("chargerVoltageTolerance",remaining)
        if(remaining<0 && ForgePortConfig.cableVoltageProtection.get())MachineFailure.destroy(node,p)
        node.setChanged()
    }
    fun persist() {
        p.gridState.putDouble("chargerTimer",timer);p.gridState.putDouble("chargerEnergy",buffer);p.gridState.putDouble("chargerResistance",resistor.resistance)
    }
}
