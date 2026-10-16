package mods.eln.mqtt

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*

/** Real loopback sockets exercise MQTT wire compatibility without contacting an external endpoint. */
class MqttTransportTest {
    private fun packet(input: DataInputStream): Pair<Int,ByteArray> {
        val header=input.readUnsignedByte();var length=0;var multiplier=1
        for(i in 0..3) {
            val byte=input.readUnsignedByte();length+=(byte and 127)*multiplier
            if(byte and 128==0) { require(length<=1048576);return header to ByteArray(length).also(input::readFully) }
            multiplier*=128
        }
        error("Malformed fixture packet")
    }
    private fun publish(topic: String,text: String): ByteArray {
        val body=ByteArrayOutputStream().also { b -> DataOutputStream(b).apply { writeUTF(topic);write(text.toByteArray(Charsets.UTF_8)) } }.toByteArray()
        require(body.size<128);return byteArrayOf(0x30,body.size.toByte())+body
    }
    @Test fun originalConnectSubscribeRetainedPublishAndFragmentedInboundCommandsUseRealMqtt311Frames() {
        ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use { broker ->
            broker.soTimeout=8000
            val completed=CompletableFuture<Unit>();val value=AtomicReference<String>();val delivered=CountDownLatch(1)
            val worker=Thread({
                try {
                    broker.accept().use { socket ->
                        socket.soTimeout=6000;val input=DataInputStream(socket.getInputStream());val output=socket.getOutputStream()
                        val connect=packet(input);assertEquals(0x10,connect.first)
                        DataInputStream(ByteArrayInputStream(connect.second)).use { fields ->
                            assertEquals("MQTT",fields.readUTF());assertEquals(4,fields.readUnsignedByte());assertEquals(0xC2,fields.readUnsignedByte());assertEquals(30,fields.readUnsignedShort())
                            assertTrue(fields.readUTF().length<=23);assertEquals("test_user",fields.readUTF());assertEquals("test_password",fields.readUTF())
                        }
                        output.write(byteArrayOf(0x20,0x02));output.flush();Thread.sleep(30);output.write(byteArrayOf(0,0));output.flush()
                        val subscribe=packet(input);assertEquals(0x82,subscribe.first)
                        DataInputStream(ByteArrayInputStream(subscribe.second)).use { fields -> fields.readUnsignedShort();assertEquals("qa/eln/signal/123456/ctrl/port/A/normal",fields.readUTF());assertEquals(0,fields.readUnsignedByte()) }
                        val inbound=publish("qa/eln/signal/123456/ctrl/port/A/normal","0.75")
                        for(part in inbound.toList().chunked(7)) { output.write(part.toByteArray());output.flush();Thread.sleep(20) }
                        val retained=packet(input);assertEquals(0x31,retained.first)
                        DataInputStream(ByteArrayInputStream(retained.second)).use { fields -> assertEquals("qa/eln/meter/654321/info/name",fields.readUTF());assertEquals("Meter",String(fields.readBytes(),Charsets.UTF_8)) }
                    }
                    completed.complete(Unit)
                }catch(t: Throwable) { completed.completeExceptionally(t) }
            },"eln-mqtt-loopback-protocol-test").apply { isDaemon=true;start() }
            val client=SimpleMqttClient(MqttServerConfig("test","tcp://127.0.0.1:${broker.localPort}","test_user","test_password","qa"))
            val subscription=client.subscribe("qa/eln/signal/123456/ctrl/port/A/normal") { _,bytes -> value.set(bytes.toString(Charsets.UTF_8));delivered.countDown() }
            try {
                assertTrue(delivered.await(6,TimeUnit.SECONDS),"Fragmented MQTT command was lost")
                assertEquals("0.75",value.get())
                client.publish("qa/eln/meter/654321/info/name","Meter".toByteArray(),true)
                completed.get(6,TimeUnit.SECONDS)
            }finally { subscription.close();client.shutdown();broker.close();worker.join(1000) }
        }
    }
    @Test fun oversizedIncomingLengthClosesTheSocketBeforeAllocatingItsAdvertisedPayload() {
        ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use { broker ->
            broker.soTimeout=8000;val closed=CompletableFuture<Boolean>()
            val worker=Thread({ try {
                broker.accept().use { socket ->
                    socket.soTimeout=5000;val input=DataInputStream(socket.getInputStream());packet(input)
                    socket.getOutputStream().apply { write(byteArrayOf(0x20,2,0,0));write(byteArrayOf(0x30,0xFF.toByte(),0xFF.toByte(),0x7F));flush() }
                    closed.complete(input.read()==-1)
                }
            }catch(t: Throwable) { closed.completeExceptionally(t) } },"eln-mqtt-loopback-bounds-test").apply { isDaemon=true;start() }
            val client=SimpleMqttClient(MqttServerConfig("bounds","tcp://127.0.0.1:${broker.localPort}"))
            try { assertTrue(closed.get(6,TimeUnit.SECONDS),"Oversized frame was not rejected") }finally { client.shutdown();broker.close();worker.join(1000) }
        }
    }
    @Test fun blockedBrokerCannotBlockThePublishingThreadAndTheBackgroundQueueStaysBounded() {
        ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use { broker ->
            broker.soTimeout=8000;val connected=CountDownLatch(1);val finish=CountDownLatch(1)
            val worker=Thread({ runCatching { broker.accept().use { socket ->
                socket.receiveBufferSize=1024;socket.soTimeout=5000;val input=DataInputStream(socket.getInputStream());packet(input)
                socket.getOutputStream().apply { write(byteArrayOf(0x20,2,0,0));flush() };check(packet(input).first==0x82);connected.countDown();finish.await(7,TimeUnit.SECONDS)
            } } },"eln-mqtt-loopback-backpressure-test").apply { isDaemon=true;start() }
            val client=SimpleMqttClient(MqttServerConfig("blocked","tcp://127.0.0.1:${broker.localPort}"))
            val subscription=client.subscribe("qa/ready") { _,_-> }
            try {
                assertTrue(connected.await(6,TimeUnit.SECONDS));Thread.sleep(100)
                val bytes=ByteArray(16384);val start=System.nanoTime()
                repeat(2048) { client.publish("qa/eln/test",bytes) }
                assertTrue((System.nanoTime()-start)/1e9<2.0,"Publishing blocked on the broker socket")
            }finally { subscription.close();client.shutdown();finish.countDown();broker.close();worker.join(1000) }
        }
    }
}
