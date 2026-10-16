package mods.eln.modern

import mods.eln.Eln
import mods.eln.config.ForgePortConfig
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalGateOutputProcess
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.level.ServerLevel

/** Original nine Computer Probe operations, with all world access confined to the server thread. */
object ComputerProbes {
    val methods=arrayOf("signalSetDir","signalGetDir","signalSetOut","signalGetOut","signalGetIn","wirelessSet","wirelessRemove","wirelessRemoveAll","wirelessGet")
    private data class Reception(val part: PartState,val until: Long,val transmitters: List<PartState>)
    private val reception=java.util.WeakHashMap<FaceNodeEntity,Reception>()
    fun part(node: FaceNodeEntity)=node.parts.filterNotNull().firstOrNull { it.definition.behavior==Behavior.COMPUTER_PROBE }
    fun initialize(p: PartState) {
        if(p.gridState.getBoolean("computerProbeInitialized"))return
        p.gridState.putBoolean("computerProbeInitialized",true)
        for(i in 0..5)p.gridState.putBoolean("computerProbeHi$i",true)
        p.gridState.put("computerProbeRadios",ListTag())
    }
    fun entries(p: PartState)=p.gridState.getList("computerProbeRadios",10).map { it as CompoundTag }
    private fun direction(name: String): Direction=when(name.uppercase(java.util.Locale.ROOT)) {
        "XN","WEST" -> Direction.WEST;"XP","EAST" -> Direction.EAST;"YN","DOWN" -> Direction.DOWN
        "YP","UP" -> Direction.UP;"ZN","NORTH" -> Direction.NORTH;"ZP","SOUTH" -> Direction.SOUTH
        else -> throw IllegalArgumentException("Unknown direction")
    }
    fun valid(node: FaceNodeEntity,p: PartState): Boolean {
        val level=node.level as? ServerLevel ?: return false
        return level.server.isSameThread && !node.isRemoved && part(node)===p && level.chunkSource.isPositionTicking(net.minecraft.world.level.ChunkPos.asLong(node.blockPos)) && level.chunkSource.getChunkNow(node.blockPos.x shr 4,node.blockPos.z shr 4)?.getBlockEntity(node.blockPos)===node
    }
    fun call(node: FaceNodeEntity,p: PartState,method: Int,side: String="",value: Double=0.0,text: String="",aggregation: String="bigger"): Array<Any> {
        require(valid(node,p)) { "Computer Probe is no longer loaded" };initialize(p)
        require(method in methods.indices)
        if(method in 0..4) {
            val index=direction(side).get3DDataValue()
            when(method) {
                0 -> { require(text=="in" || text=="out");p.gridState.putBoolean("computerProbeHi$index",text=="in") }
                1 -> return arrayOf(if(p.gridState.getBoolean("computerProbeHi$index"))"in" else "out")
                2 -> { require(value.isFinite());p.gridState.putDouble("computerProbeOut$index",value.coerceIn(0.0,1.0)) }
                3 -> return arrayOf(p.gridState.getDouble("computerProbeOut$index"))
                4 -> return arrayOf(p.gridState.getDouble("computerProbeIn$index").takeIf(Double::isFinite)?.coerceIn(0.0,1.0) ?: 0.0)
            }
        }else {
            require(text.length<=64 && text.none { it.code<32 }) { "Invalid radio channel" }
            val list=p.gridState.getList("computerProbeRadios",10)
            when(method) {
                5 -> {
                    require(value.isFinite());var entry=entries(p).firstOrNull { it.getString("channel")==text }
                    if(entry==null) {
                        require(list.size<64) { "Too many radio channels" }
                        val id=maxOf(p.gridState.getInt("computerProbeNextRadioId"),entries(p).maxOfOrNull { it.getInt("uuid") } ?: 0)+1
                        require(id>0) { "Radio identifier space exhausted" };p.gridState.putInt("computerProbeNextRadioId",id)
                        entry=CompoundTag().apply { putString("channel",text);putInt("uuid",id) };list.add(entry)
                    }
                    entry.putDouble("value",value.coerceIn(0.0,1.0))
                }
                6 -> { val index=entries(p).indexOfFirst { it.getString("channel")==text };if(index>=0)list.removeAt(index) }
                7 -> list.clear()
                8 -> {
                    require(aggregation in setOf("bigger","smaller")) { "Unknown aggregation" }
                    val level=node.level as ServerLevel
                    val cached=reception[node]?.takeIf { it.part===p && level.gameTime<it.until } ?: Reception(p,level.gameTime+20+level.random.nextInt(21),CircuitManager.wirelessAt(level,node.blockPos).transmitters).also { reception[node]=it }
                    val found=cached.transmitters.filter { it.channel==text }.map(WirelessNetwork::normalized)
                    return arrayOf((if(aggregation=="smaller")found.minOrNull()else found.maxOrNull()) ?: 0.0)
                }
            }
        }
        node.setChanged();node.sync();return emptyArray()
    }
    fun clear()=reception.clear()
}

/** Six separate current-limited gates, one for each physical cube side. */
class ComputerProbeRuntime(private val e: ElementRuntime) {
    val loads=List(6) { i -> ElectricalLoad().apply {
        serialResistance=.05;state=(e.part.gridState.getDouble("computerProbeIn$i").takeIf(Double::isFinite)?.coerceIn(0.0,1.0) ?: 0.0)*5;e.circuit.root.addState(this)
        e.circuit.root.addComponent(Resistor(this,null).setResistance(1e12))
    } }
    private val p=e.part
    private val gates=loads.mapIndexed { i,load -> NbtElectricalGateOutputProcess("probe$i",load).also(e.circuit.root::addComponent) }
    private val modes=Array<Boolean?>(6) { null }
    init { ComputerProbes.initialize(p);ComputerProbeRadios.attach(e.node,p);beforeTick() }
    fun beforeTick() {
        for(i in 0..5) {
            val high=p.gridState.getBoolean("computerProbeHi$i")
            if(modes[i]!=high) { gates[i].setHighImpedance(high);gates[i].coulombs=Eln.gateOutputCurrent*.05/ForgePortConfig.simulationSteps.get()/5/(if(high)1000 else 1);modes[i]=high }
            if(!high)gates[i].setVoltageSafe(p.gridState.getDouble("computerProbeOut$i")*5)
        }
    }
    fun afterStep() {
        for(i in 0..5)p.gridState.putDouble("computerProbeIn$i",loads[i].voltage/5)
        p.voltage=loads.maxOf { kotlin.math.abs(it.voltage) };p.current=loads.maxOf { kotlin.math.abs(it.current) };p.active=modes.any { it==false }
    }
}

/** Original virtual radio transmitters share their probe coordinate and ordinary repeater graph. */
object ComputerProbeRadios {
    private data class Binding(val node: FaceNodeEntity,val part: PartState,val proxies: MutableMap<Int,PartState> = hashMapOf())
    private val bindings=mutableListOf<Binding>()
    fun attach(node: FaceNodeEntity,p: PartState) { if(bindings.none { it.node===node && it.part===p })bindings.add(Binding(node,p)) }
    fun tick() {
        bindings.removeAll { b -> if(!ComputerProbes.valid(b.node,b.part)) { b.proxies.values.forEach { it.voltage=0.0 };true }else false }
        for(b in bindings)refresh(b)
    }
    private fun refresh(b: Binding): List<Pair<net.minecraft.core.BlockPos,PartState>> {
        val entries=ComputerProbes.entries(b.part);val ids=entries.map { it.getInt("uuid") }.toSet()
        b.proxies.filterKeys { it !in ids }.values.forEach { it.voltage=0.0 };b.proxies.keys.retainAll(ids)
        return entries.map { entry ->
            val proxy=b.proxies.getOrPut(entry.getInt("uuid")) { PartState(PartDefinition.byId.getValue("wireless_signal_transmitter"),b.part.face).apply { gridId=b.part.gridId+":cc:"+entry.getInt("uuid") } }
            proxy.channel=entry.getString("channel");proxy.voltage=entry.getDouble("value").takeIf(Double::isFinite)?.coerceIn(0.0,1.0)?.times(5) ?: 0.0
            b.node.blockPos to proxy
        }
    }
    fun sources(level: ServerLevel)=bindings.filter { it.node.level===level && ComputerProbes.valid(it.node,it.part) }.flatMap(::refresh)
    fun clear() { bindings.forEach { b -> b.proxies.values.forEach { it.voltage=0.0 } };bindings.clear() }
}
