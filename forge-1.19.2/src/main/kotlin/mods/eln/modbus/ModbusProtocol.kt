package mods.eln.modbus

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/** Original four Modbus tables. Word values are unsigned on the wire. */
interface ModbusDevice {
    fun valid(address: Int): Boolean
    fun bit(address: Int,input: Boolean): Boolean
    fun word(address: Int,input: Boolean): Int
    fun writeBit(address: Int,value: Boolean)
    fun writeWord(address: Int,value: Int)
}

/** Validates the complete request before changing any device state. MBAP framing is separate. */
object ModbusProtocol {
    fun response(pdu: ByteArray,device: ModbusDevice?): ByteArray {
        val function=pdu.firstOrNull()?.toInt()?.and(255) ?: 0
        fun error(code: Int)=byteArrayOf((function or 128).toByte(),code.toByte())
        if(device==null)return error(11)
        if(function !in setOf(1,2,3,4,5,6,15,16))return error(1)
        if(pdu.size<5)return error(3)
        fun u16(i: Int)=((pdu[i].toInt() and 255) shl 8) or (pdu[i+1].toInt() and 255)
        val address=u16(1);val parameter=u16(3)
        val count=if(function==5 || function==6)1 else parameter
        val maximum=when(function) { 1,2 -> 2000;3,4 -> 125;15 -> 1968;16 -> 123;else -> 1 }
        if(count !in 1..maximum)return error(3)
        if(address+count>65536 || (address until address+count).any { !device.valid(it) })return error(2)
        if(function in 1..6 && pdu.size!=5)return error(3)
        if(function==5 && parameter!=0 && parameter!=65280)return error(3)
        if(function==15 || function==16) {
            val bytes=if(function==15)(count+7)/8 else count*2
            if(pdu.size!=6+bytes || (pdu[5].toInt() and 255)!=bytes)return error(3)
        }
        val result=ByteArrayOutputStream();val out=DataOutputStream(result);out.writeByte(function)
        when(function) {
            1,2 -> {
                val data=ByteArray((count+7)/8)
                for(i in 0 until count)if(device.bit(address+i,function==2))data[i/8]=(data[i/8].toInt() or (1 shl (i%8))).toByte()
                out.writeByte(data.size);out.write(data)
            }
            3,4 -> { out.writeByte(count*2);for(i in 0 until count)out.writeShort(device.word(address+i,function==4)) }
            5 -> { device.writeBit(address,parameter==65280);out.write(pdu,1,4) }
            6 -> { device.writeWord(address,parameter);out.write(pdu,1,4) }
            15 -> {
                for(i in 0 until count)device.writeBit(address+i,(pdu[6+i/8].toInt() and (1 shl (i%8)))!=0)
                out.writeShort(address);out.writeShort(count)
            }
            16 -> { for(i in 0 until count)device.writeWord(address+i,u16(6+i*2));out.writeShort(address);out.writeShort(count) }
        }
        return result.toByteArray()
    }
}
