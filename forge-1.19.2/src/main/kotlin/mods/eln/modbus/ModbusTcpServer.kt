package mods.eln.modbus

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.*

/** Bounded I/O workers never access the world: the dispatcher completes on the server thread. */
class ModbusTcpServer(host: String,port: Int,private val dispatch: (Int,ByteArray)->CompletableFuture<ByteArray>): AutoCloseable {
    private val server=ServerSocket()
    private val connections=ConcurrentHashMap.newKeySet<Socket>()
    private val workers=ThreadPoolExecutor(0,8,30,TimeUnit.SECONDS,SynchronousQueue(),ThreadFactory { task -> Thread(task,"eln-modbus-client").apply { isDaemon=true } })
    @Volatile private var running=true
    val localPort get()=server.localPort
    init {
        try { server.bind(InetSocketAddress(host,port),8) }catch(t: Throwable) { server.close();workers.shutdownNow();throw t }
        Thread({ accept() },"eln-modbus-accept").apply { isDaemon=true;start() }
    }
    private fun accept() {
        while(running) {
            val socket=try { server.accept() }catch(_: Exception) { break }
            connections.add(socket)
            try { workers.execute { serve(socket) } }catch(_: RejectedExecutionException) { connections.remove(socket);socket.close() }
        }
    }
    private fun serve(socket: Socket) {
        try {
            socket.use { s ->
                s.soTimeout=5000;s.tcpNoDelay=true
                val input=DataInputStream(s.getInputStream());val output=DataOutputStream(s.getOutputStream())
                while(running) {
                    val transaction=input.readUnsignedShort();val protocol=input.readUnsignedShort();val size=input.readUnsignedShort()
                    if(protocol!=0 || size !in 2..254)break
                    val unit=input.readUnsignedByte();val pdu=ByteArray(size-1);input.readFully(pdu)
                    val future=dispatch(unit,pdu)
                    val response=try { future.get(2,TimeUnit.SECONDS) }catch(_: Exception) { future.cancel(false);byteArrayOf((pdu[0].toInt() or 128).toByte(),11) }
                    output.writeShort(transaction);output.writeShort(0);output.writeShort(response.size+1);output.writeByte(unit);output.write(response);output.flush()
                }
            }
        }catch(_: Exception) { }finally { connections.remove(socket);runCatching { socket.close() } }
    }
    override fun close() {
        running=false;runCatching { server.close() }
        connections.forEach { runCatching { it.close() } };connections.clear();workers.shutdownNow()
    }
}
