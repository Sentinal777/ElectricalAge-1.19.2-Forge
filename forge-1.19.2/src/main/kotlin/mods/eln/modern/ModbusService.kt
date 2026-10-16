package mods.eln.modern

import mods.eln.Eln
import mods.eln.config.ForgePortConfig
import mods.eln.modbus.ModbusProtocol
import mods.eln.modbus.ModbusTcpServer
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture

/** The I/O thread captures a device identity; only the Minecraft server may execute that request. */
object ModbusService {
    private class Binding(val node: FaceNodeEntity,val part: PartState) {
        val registers=PartModbusRegisters(part)
        val radios=hashMapOf<Int,PartState>()
        val received=hashMapOf<Int,List<PartState>>()
        var nextScan=0L
    }
    private data class Request(val binding: Binding?,val pdu: ByteArray,val result: CompletableFuture<ByteArray>)
    private val bindings=mutableListOf<Binding>()
    private val requests=ArrayBlockingQueue<Request>(256)
    @Volatile private var units: Map<Int,Binding> = emptyMap()
    private var listener: ModbusTcpServer?=null
    private var settings: Triple<Boolean,String,Int>?=null
    val port get()=listener?.localPort ?: -1
    fun attach(node: FaceNodeEntity,p: PartState) {
        if(bindings.none { it.node===node && it.part===p })bindings.add(Binding(node,p))
    }
    fun reconfigure(node: FaceNodeEntity,p: PartState) {
        bindings.removeAll { b -> if(b.node===node && b.part===p) { b.radios.values.forEach { it.voltage=0.0 };true }else false }
        attach(node,p);units=units.filterValues { it.node!==node || it.part!==p }
    }
    private fun valid(b: Binding): Boolean {
        val level=b.node.level as? ServerLevel ?: return false
        return !b.node.isRemoved && b.node.parts[b.part.face.get3DDataValue()]===b.part && level.chunkSource.isPositionTicking(net.minecraft.world.level.ChunkPos.asLong(b.node.blockPos)) && level.chunkSource.getChunkNow(b.node.blockPos.x shr 4,b.node.blockPos.z shr 4)?.getBlockEntity(b.node.blockPos)===b.node
    }
    fun request(unit: Int,pdu: ByteArray): CompletableFuture<ByteArray> {
        val future=CompletableFuture<ByteArray>();val binding=units[unit]
        if(binding==null || !requests.offer(Request(binding,pdu.copyOf(),future)))future.complete(ModbusProtocol.response(pdu,null))
        return future
    }
    fun tick(server: MinecraftServer) {
        bindings.removeAll { b -> if(!valid(b)) { b.radios.values.forEach { it.voltage=0.0 };true }else false }
        val next=linkedMapOf<Int,Binding>()
        for(b in bindings) {
            val id=b.part.gridState.getInt("modbusStation")
            val registered=id>=0 && next.putIfAbsent(id,b)==null
            if(b.part.gridState.getBoolean("modbusRegistered")!=registered) { b.part.gridState.putBoolean("modbusRegistered",registered);b.node.sync() }
        }
        units=next
        val config=Triple(ForgePortConfig.modbusEnabled.get(),ForgePortConfig.modbusHost.get(),ForgePortConfig.modbusPort.get())
        if(settings!=config) {
            listener?.close();listener=null;settings=config
            if(config.first)try { listener=ModbusTcpServer(config.second,config.third,::request) }catch(_: Exception) { Eln.LOGGER.error("Modbus TCP listener could not start; check its configured address and port") }
        }
        repeat(64) {
            val q=requests.poll() ?: return@repeat
            if(q.result.isCancelled)return@repeat
            val b=q.binding
            if(b==null || !valid(b) || bindings.none { it===b })q.result.complete(ModbusProtocol.response(q.pdu,null))
            else {
                val response=ModbusProtocol.response(q.pdu,b.registers)
                b.part.gridState.putLong(if(response[0].toInt() and 128==0)"modbusActivityUntil" else "modbusErrorUntil",(b.node.level as ServerLevel).gameTime+if(response[0].toInt() and 128==0)2 else 20)
                b.node.setChanged();b.node.sync();q.result.complete(response)
            }
        }
        // Source RX discovery cadence is 1.2 to 2 seconds; values remain live between scans.
        for(b in bindings) {
            val level=b.node.level as ServerLevel
            val scan=level.gameTime>=b.nextScan
            if(scan)b.nextScan=level.gameTime+24+level.random.nextInt(17)
            for(entry in ModbusDevices.entries(b.part)) {
                if(entry.getBoolean("tx")) { b.radios[entry.getInt("uuid")]?.voltage=entry.getDouble("value").coerceIn(0.0,1.0)*5;continue }
                val found=if(scan)CircuitManager.wirelessAt(level,b.node.blockPos).transmitters.filter { it.channel==entry.getString("channel") }.also { b.received[entry.getInt("uuid")]=it } else b.received[entry.getInt("uuid")].orEmpty()
                entry.putBoolean("connected",found.isNotEmpty());entry.putDouble("value",found.maxOfOrNull(WirelessNetwork::normalized) ?: 0.0)
            }
        }
    }
    /** Source IWirelessSignalTx entries share the RTU coordinate and ordinary repeater/range rules. */
    fun radioSources(level: ServerLevel): List<Pair<BlockPos,PartState>> = bindings.filter { it.node.level===level && valid(it) }.flatMap { b ->
        val entries=ModbusDevices.entries(b.part).filter { it.getBoolean("tx") }
        val active=entries.map { it.getInt("uuid") }.toSet()
        b.radios.filterKeys { it !in active }.values.forEach { it.voltage=0.0 }
        b.radios.keys.retainAll(active)
        entries.map { entry ->
            val radio=b.radios.getOrPut(entry.getInt("uuid")) { PartState(PartDefinition.byId.getValue("wireless_signal_transmitter"),b.part.face).apply { gridId=b.part.gridId+":"+entry.getInt("uuid") } }
            radio.channel=entry.getString("channel");radio.voltage=entry.getDouble("value").takeIf(Double::isFinite)?.coerceIn(0.0,1.0)?.times(5) ?: 0.0
            b.node.blockPos to radio
        }
    }
    fun clear() {
        listener?.close();listener=null;settings=null;units=emptyMap()
        bindings.forEach { it.radios.values.forEach { p -> p.voltage=0.0 } };bindings.clear()
        while(true) { val q=requests.poll() ?: break;q.result.cancel(false) }
    }
}
