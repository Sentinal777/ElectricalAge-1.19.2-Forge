package mods.eln.modern

import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Capacitor
import mods.eln.sim.mna.component.Inductor
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.misc.MnaConst
import kotlin.math.abs
import kotlin.math.sqrt

/** Original coal/redstone/dielectric/winding hardware, with separate signal and power pins. */
class PassiveRuntime(private val e: ElementRuntime) {
    private val p=e.part;private val spec=p.definition.passive!!
    private val inventory=PartInventory.get(e.node,p)
    private fun count(slot: Int)=inventory.getStackInSlot(slot).count
    private val coreIndex=if(spec.controlled)0 else 1
    private val core=if(spec.kind=="INDUCTOR")(inventory.getStackInSlot(coreIndex).item as? MaterialItem)?.definition?.takeIf { it.kind=="FERROMAGNETIC_CORE" } else null
    private val terminalResistance=when(spec.kind) { "RESISTOR" -> MnaConst.noImpedance;"INDUCTOR" -> PassiveSettings.coreResistance(core?.value);else -> .0125 }
    private val a=ElectricalLoad().apply { serialResistance=terminalResistance;state=p.voltage }
    private val b=ElectricalLoad().apply { serialResistance=terminalResistance;state=p.voltageB }
    private val gate=if(spec.controlled)ElectricalLoad().apply { serialResistance=.05;state=p.pinVoltages[0] }else null
    private val resistor=if(spec.kind=="RESISTOR")Resistor(a,b)else null
    private val capacitor=if(spec.kind=="CAPACITOR")Capacitor(a,b)else null
    private val inductor=if(spec.kind=="INDUCTOR")Inductor("hardware_inductor",a,b)else null
    private val discharge=if(capacitor!=null)Resistor(a,b)else null
    private var previousDrop=p.voltage-p.voltageB
    private var pending=p.gridState.getDouble("capacitorLegaliseJ").takeIf { it.isFinite() } ?: 0.0
    private val nominal=if(capacitor!=null)PassiveSettings.capacitorVoltage(count(1))else 0.0
    private val standardLeak: Double
    init {
        for(load in listOfNotNull(a,b,gate)) {
            e.circuit.root.addState(load);e.circuit.root.addComponent(Resistor(load,null).pullDown())
        }
        e.pins[3]=a;e.pins[1]=b;e.pins[0]=gate
        resistor?.let { it.resistance=resistance();p.value=it.resistance;e.circuit.root.addComponent(it) }
        capacitor?.let {
            val newC=PassiveSettings.capacitance(count(0),count(1))
            // The source's inventory-change legalization drains the increase in stored field energy.
            pending+=(newC-p.value)*previousDrop*previousDrop/2
            p.value=newC;it.coulombs=newC;e.circuit.root.addComponent(it)
        }
        standardLeak=if(capacitor!=null)120000.0/p.value else MnaConst.highImpedance
        discharge?.let { it.resistance=if(pending>0)nominal*nominal/400 else standardLeak;e.circuit.root.addComponent(it) }
        inductor?.let {
            it.inductance=inductance()
            if(p.gridState.getBoolean("passiveReset")) {
                p.gridState.putDouble("passiveDissipatedJ",p.gridState.getDouble("passiveDissipatedJ")+p.value*p.inductorCurrent*p.inductorCurrent/2)
                p.inductorCurrent=0.0;p.gridState.remove("passiveReset")
            }
            it.currentState.state=p.inductorCurrent*if(spec.controlled && it.inductance>0)sqrt(p.value.coerceAtLeast(0.0)/it.inductance)else 1.0
            p.value=it.inductance;e.circuit.root.addComponent(it)
        }
        persist()
    }
    private fun control()=if(p.connectedMask and 1==0 && spec.kind=="INDUCTOR")1.0 else ((gate?.voltage ?: 0.0)/5).coerceIn(0.0,1.0)
    private fun resistance()=PassiveSettings.resistance(count(0),spec.temperatureCoefficient,e.thermal?.load?.temperatureCelsius ?: 0.0,if(spec.controlled)control()else null)*if(spec.large)100 else 1
    private fun inductance()=if(spec.controlled)PassiveSettings.controlledInductance(control())else PassiveSettings.inductance(count(0))
    fun beforeTick() {
        e.thermal?.beforeTick()
        resistor?.let { val next=resistance();if(abs(next-it.resistance)>abs(it.resistance)*.01)it.resistance=next;p.value=it.resistance }
        inductor?.takeIf { spec.controlled }?.let {
            val next=inductance()
            if(abs(next-it.inductance)>abs(it.inductance)*.01) {
                // A five-volt control cannot provide field energy. Preserve field joules on adjustment.
                it.currentState.state*=if(next>0)sqrt(it.inductance/next)else 0.0
                it.inductance=next
            }
            p.value=it.inductance
        }
        p.gridState.putDouble("passiveControl",if(spec.controlled)control()else 0.0)
    }
    fun afterStep(dt: Double) {
        val drop=a.voltage-b.voltage
        p.current=when { capacitor!=null -> capacitor.coulombs*(drop-previousDrop)/dt;inductor!=null -> inductor.current;else -> resistor!!.current }
        p.voltage=a.voltage;p.voltageB=b.voltage;previousDrop=drop
        for(i in 0..3)p.pinVoltages[i]=e.pins[i]?.voltage ?: 0.0
        val loss=(resistor?.power ?: 0.0)+(discharge?.power ?: 0.0)+(a.current*a.current+b.current*b.current)*terminalResistance
        p.energy+=loss.coerceAtLeast(0.0)*dt
        resistor?.let { e.thermal?.addHeat(it.power,dt) }
        inductor?.let { p.inductorCurrent=it.current;p.gridState.putDouble("passiveStoredJ",it.energy) }
        capacitor?.let {
            p.gridState.putDouble("passiveStoredJ",it.energy)
            if(pending<=0) { pending=0.0;discharge!!.resistance=standardLeak }
            else { pending-=(discharge!!.power*dt);discharge.resistance=nominal*nominal/400 }
        }
    }
    fun afterTick() {
        if(capacitor!=null)ConductorProtection.tick(e.node,p,"capacitorBipole",a.voltage-b.voltage,nominal)
        e.thermal?.afterTick();persist();e.node.setChanged()
    }
    fun persist() { p.gridState.putDouble("capacitorLegaliseJ",pending);e.thermal?.persist() }
}
