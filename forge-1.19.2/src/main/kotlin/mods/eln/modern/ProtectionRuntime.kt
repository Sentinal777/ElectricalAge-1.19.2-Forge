package mods.eln.modern

import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.misc.MnaConst
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block

object ProtectionSettings {
    fun window(text: String): Pair<Double,Double>? {
        val values=text.split(',').map { it.trim().toDoubleOrNull() ?: return null }
        if(values.size!=2 || values.any { !it.isFinite() || kotlin.math.abs(it)>1e12 })return null
        return values[0] to values[1]
    }
    fun cable(node: FaceNodeEntity,p: PartState): PartDefinition? {
        PartInventory.get(node,p)
        return cable(p)
    }
    fun cable(p: PartState): PartDefinition? {
        val item=PartInventory.peek(p,0).item
        return if(p.definition.behavior==Behavior.FUSE_HOLDER)(item as? MaterialItem)?.definition?.fuseCable?.let(PartDefinition.byId::get)
            else (item as? PartItem)?.definition?.takeIf { it.behavior==Behavior.CABLE && !it.id.endsWith("_current_cable") }
    }
    fun fuseInteraction(node: FaceNodeEntity,p: PartState,player: Player,held: ItemStack): Boolean {
        val installed=(held.item as? MaterialItem)?.definition?.kind=="FUSE"
        if(!held.isEmpty && !installed)return false
        val inventory=PartInventory.get(node,p);val previous=inventory.getStackInSlot(0).copy()
        if(held.isEmpty && previous.isEmpty)return false
        inventory.setStackInSlot(0,if(installed)held.copy().apply { count=1 }else ItemStack.EMPTY)
        if(installed && !player.abilities.instabuild)held.shrink(1)
        if(!previous.isEmpty)Block.popResource(node.level!!,node.blockPos,previous)
        node.sync();return true
    }
}

/** Original sacrificial fuse and fitted-cable voltage-window breaker, with source electrical-rate heating. */
class ProtectionRuntime(private val e: ElementRuntime) {
    private val p=e.part;private val breaker=p.originalBreaker
    private val inventory=PartInventory.get(e.node,p)
    private val fitted=ProtectionSettings.cable(e.node,p)
    private val terminalResistance=if(breaker)fitted?.resistance ?: MnaConst.highImpedance else PartDefinition.byId.getValue("very_high_voltage_cable").resistance
    private val a=ElectricalLoad().apply { serialResistance=terminalResistance;state=p.voltage }
    private val b=ElectricalLoad().apply { serialResistance=terminalResistance;state=p.voltageB }
    private val branch=Resistor(a,b)
    private var heat=p.gridState.getDouble("protectionTemperature").takeIf { it.isFinite() }?.coerceIn(0.0,1e12) ?: 0.0
    private var published=p.active
    init {
        for(load in listOf(a,b)) { e.circuit.root.addState(load);e.circuit.root.addComponent(Resistor(load,null).pullDown()) }
        if(!breaker || fitted!=null) { e.pins[0]=a;e.pins[2]=b }
        branch.resistance=desiredResistance();e.circuit.root.addComponent(branch)
        if(breaker && ProtectionSettings.window(p.expression)==null)p.expression="0,5"
    }
    private fun cable()=if(breaker)fitted else ProtectionSettings.cable(e.node,p)
    private fun desiredResistance()=cable()?.resistance?.takeIf { !breaker || p.enabled } ?: MnaConst.highImpedance
    fun beforeStep() {
        val resistance=desiredResistance();if(resistance!=branch.resistance)branch.resistance=resistance
    }
    fun afterStep(dt: Double) {
        p.voltage=a.voltage;p.voltageB=b.voltage;p.current=branch.current
        p.pinVoltages=DoubleArray(4) { e.pins[it]?.voltage ?: 0.0 }
        p.energy+=(branch.power+(a.current*a.current+b.current*b.current)*terminalResistance)*dt
        val cable=cable()
        if(cable==null)heat=0.0 else {
            val nominalLoss=2*cable.maxCurrent*cable.maxCurrent*cable.resistance
            val capacity=nominalLoss*30/130;val rp=130/nominalLoss
            heat+=((a.current*a.current*2*cable.resistance-heat/rp*.9)/capacity)*dt
        }
        if(breaker) {
            val (minimum,maximum)=ProtectionSettings.window(p.expression) ?: (0.0 to 5.0)
            if(p.enabled && (a.voltage>=maximum || a.voltage<minimum || heat>104.0)) {
                p.enabled=false;p.tripped=true;branch.resistance=MnaConst.highImpedance
            }
        }else if(cable!=null && heat>130.0) {
            // The actual pinned Kotlin expression uses the full cable warm limit for fuses.
            inventory.setStackInSlot(0,ItemStack(Content.materialItems.getValue("blown_lead_fuse").get()))
            branch.resistance=MnaConst.highImpedance
        }
        p.active=ProtectionSettings.cable(e.node,p)!=null && (!breaker || p.enabled)
        persist()
    }
    fun afterTick() {
        if(published!=p.active) { published=p.active;e.node.sync() }
        e.node.setChanged()
    }
    fun persist() { p.gridState.putDouble("protectionTemperature",heat) }
}
