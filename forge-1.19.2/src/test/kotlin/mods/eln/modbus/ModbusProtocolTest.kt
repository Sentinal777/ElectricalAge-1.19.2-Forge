package mods.eln.modbus

import org.junit.Test
import kotlin.test.*
import java.io.*
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class ModbusProtocolTest {
    private class Device: ModbusDevice {
        val coils=BooleanArray(32);val words=IntArray(32)
        override fun valid(address: Int)=address in 16..47
        override fun bit(address: Int,input: Boolean)=if(input)address%2==1 else coils[address-16]
        override fun word(address: Int,input: Boolean)=if(input)address*1000 else words[address-16]
        override fun writeBit(address: Int,value: Boolean) { coils[address-16]=value }
        override fun writeWord(address: Int,value: Int) { words[address-16]=value }
    }
    private fun bytes(vararg b: Int)=b.map(Int::toByte).toByteArray()
    @Test fun everyOriginalFunctionReadsAndWritesTheCorrectTableWithUnsignedWordsAndPackedCoils() {
        val d=Device()
        fun run(vararg request: Int)=ModbusProtocol.response(bytes(*request),d)
        assertContentEquals(bytes(5,0,16,255,0),run(5,0,16,255,0));assertTrue(d.coils[0])
        assertContentEquals(bytes(6,0,16,255,254),run(6,0,16,255,254));assertEquals(65534,d.words[0])
        assertContentEquals(bytes(15,0,16,0,10),run(15,0,16,0,10,2,0xA5,3))
        assertContentEquals(bytes(1,2,0xA5,3),run(1,0,16,0,10))
        assertContentEquals(bytes(2,2,0xAA,2),run(2,0,16,0,10))
        assertContentEquals(bytes(16,0,17,0,2),run(16,0,17,0,2,4,0xAB,0xCD,0xEF,0x01))
        assertContentEquals(bytes(3,6,255,254,0xAB,0xCD,0xEF,0x01),run(3,0,16,0,3))
        assertContentEquals(bytes(4,4,0x3E,0x80,0x42,0x68),run(4,0,16,0,2))
    }
    @Test fun malformedRequestsAndRangesAreRejectedBeforeAnyPartialWrite() {
        val d=Device()
        assertContentEquals(bytes(0x90,2),ModbusProtocol.response(bytes(16,0,47,0,2,4,0,9,0,10),d));assertTrue(d.words.all { it==0 })
        assertContentEquals(bytes(0x8F,3),ModbusProtocol.response(bytes(15,0,16,0,9,1,255),d));assertTrue(d.coils.none { it })
        assertContentEquals(bytes(0x85,3),ModbusProtocol.response(bytes(5,0,16,0,1),d))
        assertContentEquals(bytes(0x83,3),ModbusProtocol.response(bytes(3,0,16,0,0),d))
        assertContentEquals(bytes(0x83,2),ModbusProtocol.response(bytes(3,255,255,0,2),d))
        assertContentEquals(bytes(0xC1,1),ModbusProtocol.response(bytes(65),d))
        assertContentEquals(bytes(0x83,11),ModbusProtocol.response(bytes(3,0,16,0,1),null))
        for(size in 1..4)assertEquals(2,ModbusProtocol.response(bytes(3,0,16,0,1).copyOf(size),d).size)
    }
    @Test fun actualTcpHandlesFragmentedHeadersAndPayloadsAndTwoRequestsInOneWrite() {
        val d=Device();val calls=java.util.concurrent.atomic.AtomicInteger()
        ModbusTcpServer("127.0.0.1",0) { unit,pdu -> calls.incrementAndGet();CompletableFuture.completedFuture(ModbusProtocol.response(pdu,if(unit==201)d else null)) }.use { server ->
            Socket("127.0.0.1",server.localPort).use { s ->
                s.soTimeout=3000;val output=s.getOutputStream();val input=DataInputStream(s.getInputStream())
                val first=bytes(0xCA,0xFE,0,0,0,6,201,6,0,16,0xBE,0xEF)
                for(b in first) { output.write(b.toInt());output.flush();Thread.sleep(2) }
                fun response(): ByteArray { val tid=input.readUnsignedShort();assertEquals(0xCAFE,tid);assertEquals(0,input.readUnsignedShort());val size=input.readUnsignedShort();assertEquals(201,input.readUnsignedByte());return ByteArray(size-1).also(input::readFully) }
                assertContentEquals(bytes(6,0,16,0xBE,0xEF),response())
                val read=bytes(0xCA,0xFE,0,0,0,6,201,3,0,16,0,1);output.write(read+read);output.flush()
                repeat(2) { assertContentEquals(bytes(3,2,0xBE,0xEF),response()) };assertEquals(3,calls.get())
            }
        }
    }
    @Test fun oversizedFramingIsClosedBeforeDispatchAndAnUnansweredClientDoesNotBlockAnother() {
        val calls=java.util.concurrent.atomic.AtomicInteger();val pending=CompletableFuture<ByteArray>()
        ModbusTcpServer("127.0.0.1",0) { unit,pdu -> calls.incrementAndGet();if(unit==1)pending else CompletableFuture.completedFuture(ModbusProtocol.response(pdu,null)) }.use { server ->
            Socket("127.0.0.1",server.localPort).use { bad -> bad.soTimeout=1000;bad.getOutputStream().write(bytes(0,1,0,0,127,255));assertEquals(-1,bad.getInputStream().read());assertEquals(0,calls.get()) }
            Socket("127.0.0.1",server.localPort).use { stalled ->
                stalled.getOutputStream().write(bytes(0,1,0,0,0,6,1,3,0,16,0,1))
                Socket("127.0.0.1",server.localPort).use { good -> good.soTimeout=1000;good.getOutputStream().write(bytes(0,2,0,0,0,6,2,3,0,16,0,1));val data=ByteArray(9);DataInputStream(good.getInputStream()).readFully(data);assertContentEquals(bytes(0,2,0,0,0,3,2,0x83,11),data) }
            }
        }
    }
}
