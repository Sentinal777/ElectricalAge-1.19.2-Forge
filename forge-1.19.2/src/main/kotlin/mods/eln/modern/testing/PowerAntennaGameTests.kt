package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs
import kotlin.math.pow

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object PowerAntennaGameTests {
    private class Yard(val h: GameTestHelper) {
        val base=h.absolutePos(BlockPos.ZERO)
        init { GameTestYards.clear(h,265,3,10) }
        fun node(id: String,at: BlockPos,face: Direction,rotation: Int=0): FaceNodeEntity {
            val d=PartDefinition.byId.getValue(id)
            h.level.setBlockAndUpdate(at.relative(face),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(at,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
            return (h.level.getBlockEntity(at) as FaceNodeEntity).also { n -> n.parts[face.get3DDataValue()]=PartState(d,face).apply { this.rotation=if(d.powerAntenna!=null)PowerAntennas.initialRotation(face)else rotation };n.changedTopology() }
        }
        fun part(n: FaceNodeEntity)=n.parts.filterNotNull().single()
        fun pair(tier: String="low",z: Int=8): Pairing {
            return pairAt(tier,BlockPos(base.x+2,265,base.z+z),Direction.WEST,10)
        }
        fun pairAt(tier: String,at: BlockPos,mount: Direction,distance: Int): Pairing {
            val tx=node(tier+"_power_transmitter_antenna",at,mount)
            val rx=node(tier+"_power_receiver_antenna",at.relative(mount.opposite,distance),mount.opposite)
            val tp=part(tx);val rp=part(rx);val te=FaceBasis.edges(tp);val re=FaceBasis.edges(rp)
            val source=node("electrical_source",tx.blockPos.relative(te[1]),tp.face);part(source).value=tp.definition.nominalVoltage
            val command=node("signal_source",tx.blockPos.relative(te[2]),tp.face);part(command).value=5.0
            val feedback=node("signal_source",rx.blockPos.relative(re[0]),rp.face);part(feedback).value=3.25
            val resistor=node("creative_power_resistor",rx.blockPos.relative(re[2]),rp.face,rp.rotation)
            part(resistor).value=rp.definition.nominalVoltage.pow(2)/(rp.definition.power*.8)
            node("ground_cable",resistor.blockPos.relative(re[2]),rp.face)
            return Pairing(tx,rx,command,feedback,resistor)
        }
    }
    private data class Pairing(val tx: FaceNodeEntity,val rx: FaceNodeEntity,val command: FaceNodeEntity,val feedback: FaceNodeEntity,val load: FaceNodeEntity) {
        val tp get()=tx.parts.filterNotNull().single();val rp get()=rx.parts.filterNotNull().single()
    }
    @JvmStatic @GameTest(batch="original_power_beams",template="grid_empty",timeoutTicks=140)
    fun allSixAntennasUseActualCommandedInputIndependentFeedbackAndFiniteReceiverEnergy(h: GameTestHelper) {
        val y=Yard(h);h.level.setWeatherParameters(6000,0,false,false)
        val pairs=listOf("low","medium","high").mapIndexed { i,tier -> y.pair(tier,3+i*5) }
        val snapshots=mutableMapOf<Pairing,DoubleArray>();var time=0L
        h.runAtTickTime(25) {
            time=h.level.gameTime
            for(pair in pairs) {
                val t=pair.tp;val r=pair.rp;val spec=t.definition.powerAntenna!!
                val efficiency=spec.efficiencyStart-(spec.efficiencyStart-spec.efficiencyEnd)*10/spec.range
                if(!t.gridState.getBoolean("beamConnected") || abs(t.powerFactor-efficiency)>1e-9 || t.voltage*t.current !in t.definition.power*.98..t.definition.power*1.01 || !r.active || r.energy<=0.0 || abs(t.pinVoltages[0]-3.25)>.001)h.fail("Original antenna tier lost commanded real input, distance loss, physical output or independent receiver feedback: ${t.save()}, receiver=${r.save()}")
                snapshots[pair]=doubleArrayOf(t.energy,r.energy,r.gridState.getDouble("beamBufferJ"),r.gridState.getDouble("beamDissipatedJ"))
            }
        }
        h.runAtTickTime(80) {
            for(pair in pairs) {
                val s=snapshots.getValue(pair);val t=pair.tp;val r=pair.rp
                val received=(t.energy-s[0])*t.powerFactor-2*(h.level.gameTime-time)*.05
                val accounted=r.energy-s[1]+r.gridState.getDouble("beamBufferJ")-s[2]+r.gridState.getDouble("beamDissipatedJ")-s[3]
                if(abs(received-accounted)>1e-5 || r.energy>t.energy || y.part(pair.load).energy>r.energy)h.fail("Wireless power duplicated energy or ignored original transmission/2 W loss: available=$received accounted=$accounted")
                val saved=pair.rx.saveWithoutMetadata();pair.rx.load(saved);pair.rx.changedTopology()
                y.part(pair.command).value=0.0
            }
        }
        h.runAtTickTime(105) {
            for(pair in pairs)if(pair.tp.current>1e-6 || pair.rp.active || abs(y.part(pair.load).current)>1e-6)h.fail("Receiver invented power after the actual transmitter command stopped")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_power_beams",template="grid_empty",timeoutTicks=205)
    fun originalStraightBeamStopsAtStoneAndGlassThenReconnectsOnlyAfterTheObstructionIsRemoved(h: GameTestHelper) {
        val y=Yard(h);val pair=y.pair();val obstacle=pair.tx.blockPos.east(5)
        h.runAtTickTime(25) { if(!pair.rp.active)h.fail("Initial straight power beam did not connect");h.level.setBlockAndUpdate(obstacle,Blocks.STONE.defaultBlockState()) }
        h.runAtTickTime(70) {
            if(pair.tp.gridState.getBoolean("beamConnected") || pair.rp.active || abs(y.part(pair.load).current)>1e-6)h.fail("Power beam transmitted through its first opaque obstruction")
            h.level.setBlockAndUpdate(obstacle,Blocks.GLASS.defaultBlockState())
        }
        h.runAtTickTime(115) {
            if(pair.rp.active)h.fail("Power beam incorrectly used signal-radio transparent-block propagation")
            h.level.removeBlock(obstacle,false)
        }
        h.runAtTickTime(165) { if(!pair.tp.gridState.getBoolean("beamConnected") || !pair.rp.active)h.fail("Clear straight beam failed to reconnect after original two-second scans");h.succeed() }
    }
    @JvmStatic @GameTest(batch="original_power_beam_weather",template="grid_empty",timeoutTicks=165)
    fun originalPowerBeamRainAndThunderLossesMultiplyWithoutChangingRealInputDemand(h: GameTestHelper) {
        val y=Yard(h);h.level.setWeatherParameters(6000,0,false,false);val pair=y.pair()
        var base=0.0;var rain=0.0;var thunder=0.0
        h.runAtTickTime(20) { base=pair.tp.powerFactor;h.level.setWeatherParameters(0,6000,true,false);h.level.setRainLevel(1f);h.level.setThunderLevel(0f) }
        h.runAtTickTime(70) { rain=pair.tp.powerFactor;h.level.setWeatherParameters(0,6000,true,true);h.level.setThunderLevel(1f) }
        h.runAtTickTime(125) {
            thunder=pair.tp.powerFactor;h.level.setWeatherParameters(6000,0,false,false);h.level.setRainLevel(0f);h.level.setThunderLevel(0f)
            if(abs(base-.89)>1e-9 || abs(rain-base*.707)>1e-9 || abs(thunder-base*.707*.707)>1e-9)h.fail("Original antenna distance/rain/thunder efficiency factors changed: $base/$rain/$thunder")
            if(pair.tp.voltage*pair.tp.current !in 245.0..251.0)h.fail("Rain created or removed transmitter input demand instead of adding beam loss")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_power_beam_faces",template="grid_empty",timeoutTicks=75)
    fun allSixMountingDirectionsKeepTheOriginalRearPowerCommandAndFeedbackPorts(h: GameTestHelper) {
        val y=Yard(h);GameTestYards.clear(h,290,23,24)
        val mounts=listOf(
            BlockPos(y.base.x+2,270,y.base.z+3) to Direction.WEST,
            BlockPos(y.base.x+12,274,y.base.z+6) to Direction.EAST,
            BlockPos(y.base.x+4,278,y.base.z+2) to Direction.NORTH,
            BlockPos(y.base.x+12,282,y.base.z+12) to Direction.SOUTH,
            BlockPos(y.base.x+5,285,y.base.z+8) to Direction.DOWN,
            BlockPos(y.base.x+11,306,y.base.z+8) to Direction.UP)
        val pairs=mounts.map { (at,face) -> y.pairAt("low",at,face,7) }
        h.runAtTickTime(30) {
            for(pair in pairs)if(!pair.rp.active || pair.tp.connectedMask!=6 || pair.rp.connectedMask!=5 || abs(pair.tp.pinVoltages[0]-3.25)>.001 || !PartPlacement.supported(h.level,pair.tx.blockPos,pair.tp.face) || !PartPlacement.supported(h.level,pair.rx.blockPos,pair.rp.face))h.fail("Original antenna mounting/ports failed on ${pair.tp.face}: ${pair.tp.save()}, ${pair.rp.save()}")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_power_beam_hazards",template="grid_empty",timeoutTicks=85)
    fun anObstructedPoweredBeamCreatesTheOriginalFireAndIgnitesAnActualEntity(h: GameTestHelper) {
        val y=Yard(h);val pair=y.pair();val stop=pair.tx.blockPos.east(5);val fire=stop.west()
        h.level.setBlockAndUpdate(stop,Blocks.GLASS.defaultBlockState());h.level.setBlockAndUpdate(fire.below(),Blocks.STONE.defaultBlockState())
        val cow=net.minecraft.world.entity.EntityType.COW.create(h.level)!!
        cow.setPos(pair.tx.blockPos.x+2.5,265.0,pair.tx.blockPos.z+.5);cow.setNoGravity(true);h.level.addFreshEntity(cow)
        h.runAtTickTime(55) {
            val ignited=cow.remainingFireTicks>0;cow.discard()
            if(h.level.getBlockState(fire).block!=Blocks.FIRE || !ignited || pair.rp.active || abs(pair.tp.pinVoltages[0]-5)>.001)h.fail("Obstructed original beam lost its physical fire/entity hazard, no-receiver feedback or output isolation")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_power_beam_failures",template="grid_empty",timeoutTicks=75)
    fun originalTransmittersFailOnExcessInputAndUnregulatedUnloadedReceiversFailOnOutputVoltage(h: GameTestHelper) {
        val y=Yard(h);val over=y.pair("low",3);val unloaded=y.pair("low",13);val mismatch=y.pair("high",8)
        val direction=FaceBasis.edges(over.tp)[1]
        val source=h.level.getBlockEntity(over.tx.blockPos.relative(direction)) as FaceNodeEntity
        y.part(source).value=100.0
        y.part(unloaded.load).value=1e9
        mismatch.rx.parts[Direction.EAST.get3DDataValue()]=PartState(PartDefinition.byId.getValue("low_power_receiver_antenna"),Direction.EAST)
        mismatch.rx.changedTopology();y.part(mismatch.load).value=1.0
        h.runAtTickTime(30) {
            if(h.level.getBlockState(over.tx.blockPos).block==Content.machine.get())h.fail("Original low-power transmitter ignored excess physical voltage/input power")
            if(h.level.getBlockState(unloaded.rx.blockPos).block==Content.machine.get() || h.level.getBlockState(unloaded.tx.blockPos).block!=Content.machine.get())h.fail("An unregulated unloaded receiver lost its original voltage failure or destroyed a normally loaded transmitter")
            if(h.level.getBlockState(mismatch.rx.blockPos).block==Content.machine.get() || h.level.getBlockState(mismatch.tx.blockPos).block!=Content.machine.get())h.fail("A mismatched low-tier receiver silently clipped excessive received power instead of its original physical overload failure")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_power_beam_range",template="grid_empty",timeoutTicks=210)
    fun originalTwoHundredBlockLimitIsInclusiveAndBeamScansNeverForceChunks(h: GameTestHelper) {
        val y=Yard(h);val start=BlockPos(y.base.x+1024,300,y.base.z+1024)
        h.level.setWeatherParameters(6000,0,false,false);h.level.setRainLevel(0f);h.level.setThunderLevel(0f)
        val forced=hashSetOf<net.minecraft.world.level.ChunkPos>();val created=mutableListOf<BlockPos>()
        for(x in start.x-2..start.x+204)for(z in start.z-3..start.z+3) {
            val chunk=net.minecraft.world.level.ChunkPos(BlockPos(x,300,z))
            if(!h.level.forcedChunks.contains(chunk.toLong())) { h.level.setChunkForced(chunk.x,chunk.z,true);forced.add(chunk) }
            for(height in 298..303)h.level.setBlockAndUpdate(BlockPos(x,height,z),Blocks.AIR.defaultBlockState())
        }
        fun device(id: String,at: BlockPos,face: Direction,rotation: Int=0)=y.node(id,at,face,rotation).also { created.add(at);created.add(at.relative(face)) }
        val tx=device("low_power_transmitter_antenna",start,Direction.WEST);val tp=y.part(tx);val te=FaceBasis.edges(tp)
        y.part(device("electrical_source",start.relative(te[1]),tp.face)).value=50.0
        val command=device("signal_source",start.relative(te[2]),tp.face);y.part(command).value=0.0
        var rx: FaceNodeEntity
        fun receiver(distance: Int): FaceNodeEntity {
            val at=start.east(distance);val n=device("low_power_receiver_antenna",at,Direction.EAST);val p=y.part(n);val edges=FaceBasis.edges(p)
            val load=device("creative_power_resistor",at.relative(edges[2]),p.face,p.rotation);y.part(load).value=12.5
            device("ground_cable",at.relative(edges[2],2),p.face)
            return n
        }
        rx=receiver(200)
        val before=h.level.forcedChunks.toSet()
        var ready=-1L
        for(tick in 25L..180L)h.runAtTickTime(tick) {
            if(ready<0) {
                if(tp.gridState.getBoolean("beamConnected") && abs(tp.powerFactor-.7)<1e-9) { ready=tick;y.part(command).value=5.0 }
                else if(tick>=100)h.fail("Original 200-block endpoint failed bounded loaded/ticking beam readiness: tx=${tp.save()}, rx=${y.part(rx).save()}, txTicking=${h.level.chunkSource.isPositionTicking(net.minecraft.world.level.ChunkPos.asLong(tx.blockPos))}, rxTicking=${h.level.chunkSource.isPositionTicking(net.minecraft.world.level.ChunkPos.asLong(rx.blockPos))}, rain=${h.level.isRaining}, thunder=${h.level.isThundering}")
            }
            if(ready>=0 && tick==ready+25) {
            if(!y.part(rx).active || y.part(rx).energy<=0)h.fail("In-range original power beam did not deliver actual electrical work")
            y.part(command).value=0.0
            for(x in start.x+199..start.x+204)for(z in start.z-3..start.z+3)for(height in 298..303)h.level.removeBlock(BlockPos(x,height,z),false)
            rx=receiver(201)
            }
            if(ready>=0 && tick==ready+75) {
            if(tp.gridState.getBoolean("beamConnected") || y.part(rx).active || h.level.forcedChunks.toSet()!=before)h.fail("Out-of-range beam connected or its scan forced an unloaded chunk")
            for(at in created.distinct())h.level.removeBlock(at,false)
            for(chunk in forced)h.level.setChunkForced(chunk.x,chunk.z,false)
            h.succeed()
            }
        }
    }
}
