package mods.eln.modern.testing

import mods.eln.config.ForgePortConfig
import mods.eln.modern.*
import mods.eln.mqtt.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import java.io.*
import java.net.*
import java.nio.file.Files
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object MqttGameTests {
    private class Yard(val h: GameTestHelper) {
        val at=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+8,285,it.z+8) }
        init { GameTestYards.clear(h,285,2,5) }
        fun device(pos: BlockPos,id: String,rotation: Int=0): FaceNodeEntity {
            h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(pos,Content.node.get().defaultBlockState())
            return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { this.rotation=rotation };it.changedTopology() }
        }
        fun source(pos: BlockPos,value: Double,id: String="signal_source")=device(pos,id).also { it.parts[0]!!.value=value;it.changedTopology() }
        fun load(pos: BlockPos,rotation: Int,resistance: Double=10000.0)=device(pos,"creative_power_resistor",rotation).also { it.parts[0]!!.value=resistance;it.changedTopology() }
        fun bridge(pos: BlockPos,rotation: Int)=device(pos,"electrical_probe",rotation).also { PartInventory.get(it,it.parts[0]!!).setStackInSlot(0,ItemStack(Content.partItems.getValue("signal_cable").get())) }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=110)
    fun originalMqttMeterAcceptsCurrentCableAndPreservesItsSignedNegativeVoltageCounterAcrossReload(h: GameTestHelper) {
        val y=Yard(h);val meter=y.device(y.at,"mqtt_energy_meter");var p=meter.parts[0]!!
        y.source(y.at.north(),-50.0,"electrical_source");val load=y.load(y.at.south(),1,100.0);y.device(y.at.south(2),"ground_cable")
        val inv=PartInventory.get(meter,p)
        if(!p.enabled || !inv.insertItem(0,ItemStack(Content.partItems.getValue("low_current_cable").get()),false).isEmpty)h.fail("Original MQTT meter is not enabled by default or rejected its current cable")
        var saved=0.0
        h.runAtTickTime(30) {
            if(p.current !in -.501..-.49 || EnergyMeters.counter(p)>=-5 || p.gridState.getDouble("meterPowerW")>=-20 || load.parts[0]!!.energy<10 || p.connectedMask!=10)h.fail("Original MQTT signed absolute-input-voltage equation failed: ${p.current}, ${p.gridState}")
            saved=EnergyMeters.counter(p);val tag=meter.saveWithoutMetadata();meter.load(tag);p=meter.parts[0]!!;meter.changedTopology()
            if(abs(EnergyMeters.counter(p)-saved)>1e-9 || !p.enabled)h.fail("MQTT meter counter or switch failed reload")
        }
        h.runAtTickTime(55) {
            if(EnergyMeters.counter(p)>saved-10 || EnergyMeters.time(p)<150)h.fail("MQTT signed counter or 72x time stopped after reload")
            for(side in Direction.values()) {
                val cap=meter.getCapability(ForgeCapabilities.ITEM_HANDLER,side).orElseThrow { IllegalStateException("Missing fitted-cable inventory") }
                if(cap.getSlotLimit(0)!=1 || cap.getStackInSlot(0).count!=1 || !cap.isItemValid(0,ItemStack(Content.partItems.getValue("high_current_cable").get())))h.fail("MQTT all-sided fitted cable differs from original")
            }
            MqttDevices.configure(meter,p,4,0,"")
        }
        h.runAtTickTime(80) { if(abs(p.current)>1e-6)h.fail("MQTT physical switch stayed closed");MqttDevices.reset(meter,p);if(EnergyMeters.counter(p)!=0.0 || EnergyMeters.time(p)!=0.0)h.fail("Original MQTT reset did not reset both counters");h.succeed() }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=120)
    fun mqttControllerHasFourIndependentHighImpedancePortsActualReadWriteModesAndUniquePersistentIds(h: GameTestHelper) {
        val y=Yard(h);val node=y.device(y.at,"mqtt_signal_controller");var p=node.parts[0]!!
        y.bridge(y.at.east(),1);val load=y.load(y.at.east(2),0);y.device(y.at.east(3),"ground_cable");y.source(y.at.south(),2.5)
        var id=""
        h.runAtTickTime(20) {
            if((0..3).any { MqttDevices.mode(p,it)!=SignalPortMode.DISABLED } || abs(p.pinVoltages[0])>.001 || p.pinVoltages[1] !in 2.49..2.51)h.fail("Default disabled gates shorted the independent read input: ${p.pinVoltages.toList()}")
            id=p.gridState.getString("mqttId");if(!id.matches(Regex("\\d{6}")))h.fail("Original six-digit device ID was not allocated")
            MqttDevices.configure(node,p,3,2 shl 8,"");MqttDevices.configure(node,p,3,1 or (1 shl 8),"");MqttDevices.setDesired(p,0,3.75)
        }
        h.runAtTickTime(45) {
            if(p.pinVoltages[0] !in 3.70..3.80 || p.pinVoltages[1] !in 2.49..2.51 || abs(load.parts[0]!!.current) !in .00036.. .00039)h.fail("Original READ/WRITE gates did not drive their real independent load: ${p.pinVoltages.toList()}, I=${load.parts[0]!!.current}")
            if(MqttDevices.configure(node,p,3,4 or (2 shl 8),"") || MqttDevices.configure(node,p,1,0,"bad\nname"))h.fail("Invalid port or control text was accepted")
            MqttDevices.setDesired(p,0,Double.NaN);if(MqttDevices.desired(p,0)!=3.75)h.fail("Nonfinite desired output was accepted")
            MqttDevices.configure(node,p,3,1 shl 8,"")
        }
        h.runAtTickTime(70) {
            if(abs(p.pinVoltages[0])>.001 || abs(load.parts[0]!!.current)>1e-6)h.fail("READ mode still actively drove its port")
            val tag=node.saveWithoutMetadata();node.load(tag);p=node.parts[0]!!;node.changedTopology()
            val copy=y.device(y.at.west(3),"mqtt_signal_controller");copy.parts[0]!!.gridState.putString("mqttId",id);copy.changedTopology()
        }
        h.runAtTickTime(95) {
            val copy=(h.level.getBlockEntity(y.at.west(3)) as FaceNodeEntity).parts[0]!!
            if(p.gridState.getString("mqttId")!=id || copy.gridState.getString("mqttId")==id || MqttDevices.mode(p,0)!=SignalPortMode.READ || MqttDevices.desired(p,0)!=3.75)h.fail("Duplicate identity, saved mode or desired output failed actual reload")
            h.succeed()
        }
    }

    /** Minimal actual MQTT 3.1.1 broker used exclusively by the isolated GameTest below. */
    private class Broker: AutoCloseable {
        val server=ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))
        val subscribed=ConcurrentHashMap.newKeySet<String>()
        val values=ConcurrentHashMap<String,String>()
        val retained=ConcurrentHashMap.newKeySet<String>()
        private val outgoing=ArrayBlockingQueue<ByteArray>(64)
        @Volatile var failure: Throwable?=null
        @Volatile private var running=true
        @Volatile private var socket: Socket?=null
        init { server.soTimeout=200;Thread({ run() },"eln-mqtt-gametest-loopback").apply { isDaemon=true;start() } }
        private fun run() {
            try {
                while(running) {
                    val connected=try { server.accept() }catch(_: SocketTimeoutException) { continue }
                    socket=connected;connected.use { s ->
                        s.soTimeout=25;val input=DataInputStream(s.getInputStream());val output=DataOutputStream(s.getOutputStream())
                        while(running) {
                            while(true) { val data=outgoing.poll() ?: break;output.write(data);output.flush() }
                            val header=try { input.readUnsignedByte() }catch(_: SocketTimeoutException) { continue }catch(_: EOFException) { break }
                            var length=0;var shift=0
                            for(i in 0..3) { val b=input.readUnsignedByte();length=length or ((b and 127) shl shift);if(b and 128==0)break;shift+=7 }
                            require(length<=1048576);val payload=ByteArray(length);input.readFully(payload)
                            val data=DataInputStream(ByteArrayInputStream(payload))
                            when(header shr 4) {
                                1 -> { check(data.readUTF()=="MQTT" && data.readUnsignedByte()==4);output.write(byteArrayOf(0x20,2,0,0));output.flush() }
                                8 -> { val id=data.readUnsignedShort();while(data.available()>0) { subscribed.add(data.readUTF());data.readUnsignedByte() };output.write(byteArrayOf(0x90.toByte(),3,(id shr 8).toByte(),id.toByte(),0));output.flush() }
                                10 -> { val id=data.readUnsignedShort();while(data.available()>0)subscribed.remove(data.readUTF());output.write(byteArrayOf(0xB0.toByte(),2,(id shr 8).toByte(),id.toByte()));output.flush() }
                                3 -> { val topic=data.readUTF();values[topic]=data.readBytes().toString(Charsets.UTF_8);if(header and 1!=0)retained.add(topic) }
                                12 -> { output.write(byteArrayOf(0xD0.toByte(),0));output.flush() }
                                14 -> break
                            }
                        }
                    }
                }
            }catch(t: Throwable) { if(running)failure=t }
        }
        fun publish(topic: String,value: String) {
            val payload=ByteArrayOutputStream().also { DataOutputStream(it).apply { writeUTF(topic);write(value.toByteArray(Charsets.UTF_8)) } }.toByteArray()
            require(payload.size<128);check(outgoing.offer(byteArrayOf(0x30,payload.size.toByte())+payload))
        }
        fun disconnect() {
            values.clear();retained.clear();subscribed.clear()
            socket?.shutdownOutput()
        }
        override fun close() { running=false;runCatching { socket?.close() };runCatching { server.close() } }
    }
    @JvmStatic @GameTest(template="empty",batch="original_mqtt_loopback",timeoutTicks=520)
    fun actualLoopbackBrokerCommandsAndPublishedSnapshotsControlRealLoadedDevicesWithoutStaleCallbacks(h: GameTestHelper) {
        val y=Yard(h);val broker=Broker();val file=Files.createTempFile("eln-mqtt-gametest-",".json");val oldEnabled=ForgePortConfig.mqttEnabled.get()
        var finished=false
        fun cleanup() { if(finished)return;finished=true;broker.close();ForgePortConfig.mqttEnabled.set(oldEnabled);MqttManager.reset();Files.deleteIfExists(file) }
        fun at(tick: Long,action: ()->Unit)=h.runAtTickTime(tick) { try { broker.failure?.let { throw IllegalStateException("Loopback broker failed",it) };action() }catch(t: Throwable) { cleanup();throw t } }
        try {
            Files.newBufferedWriter(file).use { com.google.gson.Gson().toJson(MqttConfiguration(mqtt=listOf(MqttServerConfig("qa","tcp://127.0.0.1:${broker.server.localPort}",prefix="qa"))),it) }
            ForgePortConfig.mqttEnabled.set(true);MqttManager.refreshConfiguration(file)
            val controller=y.device(y.at.west(3),"mqtt_signal_controller");val cp=controller.parts[0]!!
            MqttDevices.configure(controller,cp,1,0,"Test controller");MqttDevices.configure(controller,cp,3,1 shl 8,"");MqttDevices.configure(controller,cp,3,1 or (2 shl 8),"")
            y.source(controller.blockPos.east(),2.5);y.bridge(controller.blockPos.south(),0);val signalLoad=y.load(controller.blockPos.south(2),1);y.device(controller.blockPos.south(3),"ground_cable")
            val meter=y.device(y.at.east(3),"mqtt_energy_meter");val mp=meter.parts[0]!!
            MqttDevices.configure(meter,mp,1,0,"Test meter");PartInventory.get(meter,mp).setStackInSlot(0,ItemStack(Content.partItems.getValue("low_voltage_cable").get()))
            y.source(meter.blockPos.north(),50.0,"electrical_source");val load=y.load(meter.blockPos.south(),1,100.0);y.device(meter.blockPos.south(2),"ground_cable")
            var started=false;var parked=0.0;var replacement: PartState?=null
            for(tick in 20L..180L)at(tick) {
                if(started)return@at
                val controllerBase="qa/eln/signal/${cp.gridState.getString("mqttId")}";val meterBase="qa/eln/meter/${mp.gridState.getString("mqttId")}"
                if("$controllerBase/ctrl/port/B/normal" !in broker.subscribed || "$meterBase/ctrl/status" !in broker.subscribed || !broker.values.containsKey("$meterBase/stat/energy")) {
                    if(tick==180L)h.fail("Actual MQTT subscriptions or snapshots never arrived: ${broker.subscribed}, ${broker.values.keys}")
                    return@at
                }
                started=true
                broker.publish("$controllerBase/ctrl/port/B/normal","0.75");broker.publish("$controllerBase/ctrl/port/A/volts","4");broker.publish("$controllerBase/ctrl/port/C/volts","NaN");broker.publish("$controllerBase/ctrl/port/B/volts","Infinity")
                at(tick+35) {
                    if(cp.pinVoltages[0] !in 2.49..2.51 || cp.pinVoltages[1] !in 3.70..3.80 || abs(signalLoad.parts[0]!!.current) !in .00036.. .00039 || MqttDevices.desired(cp,2)!=0.0 || MqttDevices.desired(cp,1)!=3.75)h.fail("Actual broker command changed a READ port, accepted a nonfinite value or failed physical output: ${cp.pinVoltages.toList()}")
                    if(broker.values["$meterBase/info/name"]!="Test meter" || "$meterBase/info/name" !in broker.retained || broker.values["$meterBase/stat/energy"]!!.toDouble()<=0 || broker.values["$meterBase/stat/current"]!!.toDouble() !in .49.. .501 || broker.values["$controllerBase/info/port/B"]!="write")h.fail("Original MQTT snapshot topics, kJ energy or retained metadata differ: ${broker.values}")
                    broker.publish("$meterBase/ctrl/status","off")
                }
                at(tick+55) { if(mp.enabled || abs(mp.current)>1e-6)h.fail("Remote off did not open the actual shunt");parked=load.parts[0]!!.energy }
                at(tick+75) { if(abs(load.parts[0]!!.energy-parked)>1e-5)h.fail("Remote-off meter still delivered energy");broker.publish("$meterBase/ctrl/reset","1") }
                at(tick+95) {
                    if(abs(EnergyMeters.counter(mp))>1e-9 || EnergyMeters.time(mp)>90)h.fail("Actual broker reset did not reset both original counters")
                    broker.publish("$meterBase/ctrl/status","on")
                    h.level.removeBlock(controller.blockPos,false)
                    replacement=y.device(controller.blockPos,"mqtt_signal_controller").parts[0]!!.also { it.gridState.putString("mqttId","replacement-safe");it.gridState.putIntArray("mqttModes",intArrayOf(0,2,0,0)) }
                    broker.publish("$controllerBase/ctrl/port/B/normal","0.2")
                }
                at(tick+125) {
                    if(!mp.enabled || load.parts[0]!!.energy<parked+5 || EnergyMeters.counter(mp)<5 || MqttDevices.desired(replacement!!,1)!=0.0 || abs(replacement!!.pinVoltages[1])>.001)h.fail("Remote restart failed or an old broker callback drove a replaced device")
                    broker.disconnect()
                }
                at(tick+245) {
                    if(broker.values["$meterBase/info/name"]!="Test meter" || "$meterBase/info/name" !in broker.retained || broker.values["$meterBase/stat/status"]!="on" || "$meterBase/ctrl/status" !in broker.subscribed || "qa/eln/signal/replacement-safe/ctrl/port/B/normal" !in broker.subscribed)h.fail("Reconnect failed to restore subscriptions, retained metadata and actual meter snapshots: ${broker.values}, ${broker.subscribed}")
                    cleanup();h.succeed()
                }
            }
        }catch(t: Throwable) { cleanup();throw t }
    }
}
