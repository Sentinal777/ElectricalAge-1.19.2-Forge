package mods.eln.modern.testing

import mods.eln.config.ForgePortConfig
import mods.eln.modbus.ModbusProtocol
import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import java.io.DataInputStream
import java.net.Socket
import java.util.concurrent.CompletableFuture
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object ModbusGameTests {
    private class Yard(val h: GameTestHelper) {
        val at=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+8,285,it.z+8) }
        init { GameTestYards.clear(h,285,2,5) }
        fun device(at: BlockPos,id: String,rotation: Int=0,value: Double?=null): FaceNodeEntity {
            h.level.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(at,Content.node.get().defaultBlockState())
            return (h.level.getBlockEntity(at) as FaceNodeEntity).also { n -> n.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { this.rotation=rotation;value?.let { this.value=it } };n.changedTopology() }
        }
        fun signalLoad(at: BlockPos): FaceNodeEntity {
            device(at.south(),"electrical_probe").let { PartInventory.get(it,it.parts[0]!!).setStackInSlot(0,ItemStack(Content.partItems.getValue("signal_cable").get())) }
            val load=device(at.south(2),"creative_power_resistor",1,10000.0);device(at.south(3),"ground_cable");return load
        }
    }
    private fun bytes(vararg b: Int)=b.map(Int::toByte).toByteArray()
    @JvmStatic @GameTest(template="empty",timeoutTicks=140)
    fun originalFourPhysicalModbusSlotsHaveRealIndependentLoadsFixedFaceRelativePortsAndSavedReadWriteState(h: GameTestHelper) {
        val y=Yard(h);val node=y.device(y.at,"modbus_rtu");var p=node.parts[0]!!;val load=y.signalLoad(y.at);val input=y.device(y.at.west(),"signal_source",value=2.5)
        val right=mapOf(Direction.DOWN to Direction.NORTH,Direction.UP to Direction.SOUTH,Direction.WEST to Direction.SOUTH,Direction.EAST to Direction.NORTH,Direction.NORTH to Direction.WEST,Direction.SOUTH to Direction.EAST)
        for(face in Direction.values())for(rotation in 0..3) {
            val test=PartState(p.definition,face).apply { this.rotation=rotation };val up=if(face.axis==Direction.Axis.Y)Direction.EAST else Direction.UP
            val expected=listOf(right.getValue(face).opposite,right.getValue(face),up.opposite,up)
            if((0..3).any { FaceBasis.edges(test)[ModbusDevices.pin(test,it)]!=expected[it] })h.fail("Source LRDU changed with housing rotation on $face")
        }
        var registers=PartModbusRegisters(p)
        h.runAtTickTime(25) {
            if((0..3).any { !p.gridState.getBoolean("modbusHi$it") } || abs(load.parts[0]!!.current)>1e-6 || p.pinVoltages[2] !in 2.49..2.51)h.fail("Fresh RTU failed original high-impedance independent input")
            if(registers.word(35,true) !in 32700..32800 || registers.bit(33,true))h.fail("Actual 2.5 V input differs from original normalized register or >3 V digital threshold")
            ModbusProtocol.response(bytes(16,0,17,0,2,4,0x3F,0x40,0,0),registers);ModbusProtocol.response(bytes(5,0,16,0,0),registers)
        }
        h.runAtTickTime(50) {
            if(p.pinVoltages[1] !in 3.70..3.80 || abs(load.parts[0]!!.current) !in .00036.. .00039 || p.pinVoltages[2] !in 2.49..2.51 || registers.word(17,false)!=0x3F40 || registers.word(18,false)!=0)h.fail("Original float registers failed physical output or shorted a separate input: ${p.pinVoltages.toList()}")
            ModbusProtocol.response(bytes(16,0,17,0,2,4,0x7F,0xC0,0,0),registers)
            if(ModbusDevices.output(p,0)!=.75)h.fail("Nonfinite Modbus float was accepted")
            input.parts[0]!!.value=3.5;input.changedTopology()
            p.rotation=1;node.changedTopology()
        }
        h.runAtTickTime(75) {
            if(abs(load.parts[0]!!.current) !in .00036.. .00039 || !registers.bit(33,true) || registers.word(35,true) !in 45800..45900)h.fail("Housing rotation moved the original fixed physical IO or original 3 V threshold failed")
            val tag=node.saveWithoutMetadata();node.load(tag);p=node.parts[0]!!;node.changedTopology();registers=PartModbusRegisters(p)
        }
        h.runAtTickTime(100) {
            if(ModbusDevices.output(p,0)!=.75 || p.gridState.getBoolean("modbusHi0") || abs(load.parts[0]!!.current) !in .00036.. .00039)h.fail("Actual reload discarded saved RTU gates")
            registers.writeBit(16,true)
        }
        h.runAtTickTime(125) { if(abs(load.parts[0]!!.current)>1e-6)h.fail("Modbus high-impedance coil did not open its real output");h.succeed() }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=210)
    fun originalWirelessModbusMappingsDriveAnActualRadioReceiverReadRealTransmittersAndRejectOverlaps(h: GameTestHelper) {
        val y=Yard(h);val node=y.device(y.at,"modbus_rtu");var p=node.parts[0]!!;val receiver=y.device(y.at.east(4),"wireless_signal_receiver",3)
        receiver.parts[0]!!.channel="rtu-out-${y.at.x}";receiver.changedTopology()
        val transmitter=y.device(y.at.west(4),"wireless_switch");transmitter.parts[0]!!.channel="rtu-in-${y.at.x}";transmitter.parts[0]!!.enabled=true;transmitter.changedTopology()
        h.runAtTickTime(20) {
            ModbusDevices.configure(node,p,3,0,0,receiver.parts[0]!!.channel);ModbusDevices.configure(node,p,4,0,0,transmitter.parts[0]!!.channel)
            if(!ModbusDevices.configure(node,p,5,1,100,receiver.parts[0]!!.channel) || !ModbusDevices.configure(node,p,5,2,200,transmitter.parts[0]!!.channel) || ModbusDevices.configure(node,p,5,2,102,"overlap") || ModbusDevices.configure(node,p,5,2,20,"physical-overlap"))h.fail("Original configurable wireless slots or validated address ranges failed")
            PartModbusRegisters(p).writeWord(103,49151)
        }
        h.runAtTickTime(80) {
            val r=PartModbusRegisters(p)
            if(receiver.parts[0]!!.voltage !in 3.70..3.80 || !r.bit(200,true) || r.word(203,true)!=65535 || !r.bit(201,true))h.fail("Actual wireless RTU TX/RX did not join original radio network: ${receiver.parts[0]!!.voltage}, ${ModbusDevices.entries(p)}")
            r.writeBit(201,false);if(r.word(203,true)!=65535)h.fail("A write changed the source read-only wireless receiver")
            WirelessRuntime.activate(transmitter,transmitter.parts[0]!!,false)
            val saved=node.saveWithoutMetadata();node.load(saved);p=node.parts[0]!!;node.changedTopology()
        }
        h.runAtTickTime(135) {
            if(ModbusDevices.entries(p).size!=2 || PartModbusRegisters(p).word(203,true)!=0 || receiver.parts[0]!!.voltage !in 3.70..3.80)h.fail("Actual RTU reload lost radio mappings or receiver stopped reading live input")
            ModbusDevices.configure(node,p,6,1,0,"")
        }
        h.runAtTickTime(190) { if(receiver.parts[0]!!.voltage>.001 || PartModbusRegisters(p).valid(100))h.fail("Deleted virtual transmitter or Modbus mapping remained live");h.succeed() }
    }
    @JvmStatic @GameTest(template="empty",batch="original_modbus_loopback",timeoutTicks=320)
    fun actualTcpWritesDriveRealPortsAndQueuedRequestsCannotControlAReplacedOrDuplicateUnit(h: GameTestHelper) {
        val y=Yard(h);val node=y.device(y.at,"modbus_rtu");val p=node.parts[0]!!;val load=y.signalLoad(y.at)
        val enabled=ForgePortConfig.modbusEnabled.get();val host=ForgePortConfig.modbusHost.get();val port=ForgePortConfig.modbusPort.get()
        var done=false
        fun cleanup() { if(done)return;done=true;ForgePortConfig.modbusEnabled.set(enabled);ForgePortConfig.modbusHost.set(host);ForgePortConfig.modbusPort.set(port);ModbusService.clear() }
        fun at(t: Long,action: ()->Unit)=h.runAtTickTime(t) { try { action() }catch(e: Throwable) { cleanup();throw e } }
        var result: CompletableFuture<Boolean>?=null;var stale: CompletableFuture<ByteArray>?=null;var replacement: PartState?=null
        ForgePortConfig.modbusHost.set("127.0.0.1");ForgePortConfig.modbusPort.set(0);ForgePortConfig.modbusEnabled.set(true)
        ModbusDevices.initialize(p);ModbusDevices.configure(node,p,1,0,201,"")
        at(25) {
            if(ModbusService.port<=0 || !p.gridState.getBoolean("modbusRegistered"))h.fail("Configured actual Modbus listener or unit ID 201 did not register")
            val listening=ModbusService.port
            result=CompletableFuture.supplyAsync {
                Socket("127.0.0.1",listening).use { s ->
                    s.soTimeout=3000;val input=DataInputStream(s.getInputStream());var transaction=0
                    fun request(unit: Int,pdu: ByteArray): ByteArray {
                        transaction++;val data=bytes(transaction shr 8,transaction,0,0,0,pdu.size+1,unit)+pdu
                        for(b in data) { s.getOutputStream().write(b.toInt());s.getOutputStream().flush();Thread.sleep(1) }
                        check(input.readUnsignedShort()==transaction && input.readUnsignedShort()==0);val size=input.readUnsignedShort();check(input.readUnsignedByte()==unit);return ByteArray(size-1).also(input::readFully)
                    }
                    check(request(202,bytes(3,0,17,0,1)).contentEquals(bytes(0x83,11)))
                    check(request(201,bytes(16,0,17,0,2,4,0x3F,0x40,0,0)).contentEquals(bytes(16,0,17,0,2)))
                    check(request(201,bytes(5,0,16,0,0)).contentEquals(bytes(5,0,16,0,0)))
                    check(request(201,bytes(3,0,17,0,2)).contentEquals(bytes(3,4,0x3F,0x40,0,0)))
                    check(request(201,bytes(3,0,15,0,2)).contentEquals(bytes(0x83,2)))
                    true
                }
            }
        }
        at(120) {
            if(result?.isDone!=true || result!!.get()!=true || abs(load.parts[0]!!.current) !in .00036.. .00039)h.fail("Actual fragmented TCP protocol failed or did not deliver real signal current")
            val duplicate=y.device(y.at.west(3),"modbus_rtu");ModbusDevices.initialize(duplicate.parts[0]!!);ModbusDevices.configure(duplicate,duplicate.parts[0]!!,1,0,201,"")
        }
        at(150) {
            val duplicate=(h.level.getBlockEntity(y.at.west(3)) as FaceNodeEntity).parts[0]!!
            if(duplicate.gridState.getBoolean("modbusRegistered"))h.fail("Duplicate unit displaced an existing source station")
            stale=ModbusService.request(201,bytes(5,0,17,255,0))
            h.level.removeBlock(node.blockPos,false);replacement=y.device(node.blockPos,"modbus_rtu").parts[0]!!
        }
        at(180) {
            if(stale?.isDone!=true || !stale!!.get().contentEquals(bytes(0x85,11)) || ModbusDevices.output(replacement!!,0)!=0.0 || abs(load.parts[0]!!.current)>1e-6)h.fail("An old TCP request drove a replacement or duplicate unit")
            cleanup();h.succeed()
        }
    }
}
