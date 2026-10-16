package mods.eln.modern.lighting

import mods.eln.modern.*
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import kotlin.math.abs

data class LampChannel(val power: String="",val signal: String="",val mode: Int=2)
object LampChannels {
    fun read(p: PartState): List<LampChannel> {
        val list=p.gridState.getList("lampChannels",10)
        return List(3) { i -> if(i<list.size)list.getCompound(i).let { LampChannel(it.getString("power").take(64),it.getString("signal").take(64),it.getInt("mode").coerceIn(0,2)) }else LampChannel() }
    }
    fun write(p: PartState,channels: List<LampChannel>) {
        p.gridState.put("lampChannels",ListTag().also { list -> channels.take(3).forEach { c -> list.add(CompoundTag().also { it.putString("power",c.power.trim().take(64));it.putString("signal",c.signal.trim().take(64));it.putInt("mode",c.mode.coerceIn(0,2)) }) } })
    }
}
class LampSupplyRuntime(val e: ElementRuntime) {
    private val p=e.part
    private val cable=(PartInventory.get(e.node,p).getStackInSlot(0).item as? PartItem)?.definition?.takeIf { it.behavior==Behavior.CABLE }
    val load=ElectricalLoad().apply { serialResistance=cable?.resistance ?: 1e9;state=p.voltage }
    private val sink=Resistor(load,null).setResistance(1e9)
    val states=BooleanArray(3)
    val socketLinks=mutableListOf<Resistor>()
    val profiles get()=LampChannels.read(p)
    val range get()=mods.eln.config.ForgePortConfig.wirelessRange.get().toDouble()+PartInventory.get(e.node,p).getStackInSlot(0).count
    private var toggle=p.gridState.getInt("lampToggle")
    private var previous=p.gridState.getInt("lampPrevious")
    var conductance=0.0
    init {
        e.circuit.root.addState(load);e.circuit.root.addComponent(sink)
        if(cable!=null)e.pins[0]=load
    }
    fun prepare() {
        conductance=0.0
        for((i,c) in profiles.withIndex()) {
            states[i]=when(c.signal.lowercase()) {
                "","true" -> true
                "false" -> false
                else -> {
                    val values=e.circuit.wirelessValues(c.signal,e.node)
                    val high=(if(c.mode==1)values.minOrNull()else values.maxOrNull())?.let { it>=2.5 } ?: false
                    if(c.mode==2) {
                        if(high && previous and (1 shl i)==0)toggle=toggle xor (1 shl i)
                        previous=if(high)previous or (1 shl i)else previous and (1 shl i).inv()
                        toggle and (1 shl i)!=0
                    }else high
                }
            }
        }
        p.gridState.putInt("lampToggle",toggle);p.gridState.putInt("lampPrevious",previous)
        p.gridState.putInt("lampStates",states.indices.fold(0) { mask,i -> if(states[i])mask or (1 shl i)else mask })
    }
    fun connectDemand() { sink.resistance=if(conductance>0)1/conductance else 1e9 }
    fun afterStep(dt: Double) {
        val supplied=sink.power.coerceAtLeast(0.0)+socketLinks.sumOf { (load.voltage*it.current).coerceAtLeast(0.0) }
        p.voltage=load.voltage;p.current=load.current;p.energy+=supplied*dt
        p.active=supplied>1e-6
    }
    fun afterTick() {
        val rating=cable?.nominalVoltage ?: 10000.0;val excess=abs(p.voltage)-rating*1.3
        if(excess<=0 || p.wasOvervoltage)p.voltageFault=(p.voltageFault+excess*.05).coerceAtLeast(0.0)
        p.wasOvervoltage=excess>0
        if(p.voltageFault>rating*.25 && mods.eln.config.ForgePortConfig.cableVoltageProtection.get())e.circuit.level.destroyBlock(e.node.blockPos,true)
    }
}
class LightingNetwork(elements: List<ElementRuntime>) {
    private val supplies=elements.mapNotNull { it.lampSupply }
    private val festive=elements.mapNotNull { it.festive }
    private val lamps=elements.mapNotNull { it.lamp }
    private val emergency=elements.mapNotNull { it.emergencyLamp }
    private val sockets=elements.mapNotNull { it.powerSocket }
    init {
        for(socket in sockets)closest(socket.e.part,socket.e)?.let { supply ->
            socket.supply=supply
            socket.link=Resistor(supply.load,socket.load).setResistance(1e16).also { link -> supply.socketLinks.add(link);socket.e.circuit.root.addComponent(link) }
        }
    }
    private fun closest(p: PartState,e: ElementRuntime): LampSupplyRuntime? = supplies.filter { s -> s.profiles.any { it.power.isNotBlank() && it.power==p.channel } && s.e.node.blockPos.distSqr(e.node.blockPos)<=s.range*s.range }.minByOrNull { it.e.node.blockPos.distSqr(e.node.blockPos) }
    fun prepare() {
        supplies.forEach { it.prepare() }
        for(socket in sockets) {
            val supply=socket.supply;val index=supply?.profiles?.indexOfFirst { it.power==socket.e.part.channel } ?: -1
            socket.link?.resistance=if(index>=0 && supply!!.states[index]).1 else 1e16
        }
        for(lamp in lamps) {
            lamp.supply=null
            if(lamp.wired || lamp.bulb==null || !lamp.hasCable)continue
            val p=lamp.e.part
            val closest=closest(p,lamp.e) ?: continue
            val index=closest.profiles.indexOfFirst { it.power==p.channel }
            if(index>=0 && closest.states[index]) { lamp.supply=closest;closest.conductance+=1/lamp.resistance }
        }
        for(lamp in emergency) {
            lamp.supply=null
            if(lamp.wired)continue
            val p=lamp.e.part;val supply=closest(p,lamp.e) ?: continue
            val index=supply.profiles.indexOfFirst { it.power==p.channel }
            if(index>=0 && supply.states[index]) { lamp.supply=supply;supply.conductance+=1/lamp.resistance }
        }
        festive.forEach { it.prepare(supplies) }
        supplies.forEach { it.connectDemand() }
    }
}
