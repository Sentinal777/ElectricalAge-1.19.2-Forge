package mods.eln.modern

import mods.eln.mqtt.*
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import java.security.SecureRandom
import java.util.concurrent.ArrayBlockingQueue

/** Socket callbacks only enqueue commands. All world access and physical changes remain on the server thread. */
object MqttService {
    private data class Key(val location: MqttCoordinate,val meter: Boolean)
    private class Binding(val key: Key,val node: FaceNodeEntity,val part: PartState,val epoch: Long) {
        @Volatile var valid=true
        var serverName=""
        var revision=-1
        var nextPublish=0
    }
    private data class Command(val binding: Binding,val action: ()->Unit)
    private val bindings=linkedMapOf<Key,Binding>()
    private val commands=ArrayBlockingQueue<Command>(1024)
    private val random=SecureRandom()
    @Volatile private var epoch=0L
    private fun key(node: FaceNodeEntity,p: PartState): Key? {
        val level=node.level as? ServerLevel ?: return null
        return Key(MqttCoordinate(level.dimension().location().toString(),node.blockPos.x,node.blockPos.y,node.blockPos.z,p.face.get3DDataValue()),MqttDevices.isMeter(p))
    }
    private fun live(b: Binding): Boolean {
        val level=b.node.level as? ServerLevel ?: return false
        return b.valid && !b.node.isRemoved && b.node.parts[b.part.face.get3DDataValue()]===b.part &&
            level.chunkSource.isPositionTicking(ChunkPos.asLong(b.node.blockPos)) &&
            level.chunkSource.getChunkNow(b.node.blockPos.x shr 4,b.node.blockPos.z shr 4)?.getBlockEntity(b.node.blockPos)===b.node
    }
    fun attach(node: FaceNodeEntity,p: PartState) {
        val key=key(node,p) ?: return
        MqttDevices.initialize(p)
        val previous=bindings[key]
        if(previous!=null && previous.node===node && previous.part===p)return
        previous?.let(::close)
        val used=bindings.values.filter { it.key!=key && it.key.meter==key.meter }.mapTo(hashSetOf()) { it.part.gridState.getString("mqttId") }
        var id=p.gridState.getString("mqttId")
        if(!id.matches(Regex("[A-Za-z0-9_-]{4,32}")) || id in used) {
            id=""
            for(i in 0 until 10000) { val candidate=String.format(java.util.Locale.ROOT,"%06d",random.nextInt(1000000));if(candidate !in used) { id=candidate;break } }
            if(id.isEmpty())id=java.util.UUID.randomUUID().toString().replace("-","").take(16)
        }
        p.gridState.putString("mqttId",id)
        bindings[key]=Binding(key,node,p,epoch).apply { nextPublish=(node.level as ServerLevel).server.tickCount+if(key.meter)100 else 20 }
        node.setChanged()
    }
    private fun unsubscribe(b: Binding) {
        if(b.key.meter)MqttMeterManager.release(b.key.location)else MqttSignalControllerManager.release(b.key.location)
        b.serverName="";b.revision=-1;b.nextPublish=0
    }
    private fun close(b: Binding) { b.valid=false;unsubscribe(b) }
    fun release(node: FaceNodeEntity,p: PartState) { key(node,p)?.let { bindings[it] }?.takeIf { it.node===node && it.part===p }?.let(::unsubscribe) }
    private fun post(b: Binding,action: ()->Unit) { if(b.valid && b.epoch==epoch)commands.offer(Command(b,action)) }
    fun tick(server: MinecraftServer) {
        MqttManager.init()
        for(i in 0 until 64) {
            val command=commands.poll() ?: break
            if(command.binding.epoch==epoch && live(command.binding) && bindings[command.binding.key]===command.binding)command.action()
        }
        val iterator=bindings.values.iterator()
        while(iterator.hasNext()) {
            val b=iterator.next()
            if(!live(b)) { close(b);iterator.remove();continue }
            val p=b.part;val config=MqttManager.getConfig()
            if(p.gridState.getString("mqttServer").isBlank() && !config.disable && config.mqtt.size==1) {
                p.gridState.putString("mqttServer",config.mqtt.single().name);b.node.setChanged();b.node.sync()
            }
            val name=p.gridState.getString("mqttServer")
            val endpoint=if(config.disable)null else MqttManager.getServerByName(name)
            val matched=endpoint!=null
            if(p.gridState.getBoolean("mqttMatched")!=matched) { p.gridState.putBoolean("mqttMatched",matched);b.node.sync() }
            if(!matched) { if(b.serverName.isNotEmpty())unsubscribe(b);continue }
            if(b.serverName!=name || b.revision!=MqttManager.revision) {
                unsubscribe(b);b.serverName=name;b.revision=MqttManager.revision
            }
            if(server.tickCount<b.nextPublish)continue
            b.nextPublish=server.tickCount+if(b.key.meter)100 else 20
            val id=p.gridState.getString("mqttId");val label=p.gridState.getString("mqttName");val location=b.key.location
            if(b.key.meter) {
                MqttMeterManager.ensureSubscription(id,location,endpoint!!) { command -> post(b) {
                    when(command) {
                        MqttMeterCommand.Reset -> MqttDevices.reset(b.node,p)
                        is MqttMeterCommand.SetStatus -> { p.enabled=command.enabled;b.node.setChanged();b.node.sync() }
                    }
                } }
                // The original topic named energyWh actually published kJ. Preserve its wire units.
                val info=MqttMeterInfo(label,name,id,p.enabled)
                MqttMeterManager.publishSnapshot(MqttMeterSnapshot(info,location,location.dimension,p.gridState.getDouble("meterPowerW"),EnergyMeters.counter(p)/1000,EnergyMeters.time(p)/72,p.current,p.voltage,p.enabled))
            }else {
                MqttSignalControllerManager.ensureSubscription(id,location,endpoint!!) { command -> post(b) {
                    when(command) { is MqttSignalControllerCommand.SetPortVoltage -> MqttDevices.setDesired(p,command.port.ordinal,command.voltage) }
                    b.node.setChanged();b.node.sync()
                } }
                val info=MqttSignalControllerInfo(label,name,id)
                val ports=SignalPort.values().map { SignalPortSnapshot(it,MqttDevices.mode(p,it.ordinal),p.pinVoltages[it.ordinal]) }
                MqttSignalControllerManager.publishSnapshot(MqttSignalControllerSnapshot(info,location,location.dimension,ports))
            }
        }
    }
    fun clear() { epoch++;bindings.values.forEach(::close);bindings.clear();commands.clear();MqttManager.reset() }
}
