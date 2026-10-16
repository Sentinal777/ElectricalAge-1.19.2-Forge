package mods.eln.modern.testing

import mods.eln.modern.*
import mods.eln.modern.grid.GridMultiblock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.animal.Cow
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs
import kotlin.math.sqrt

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object TransporterGameTests {
    private class Yard(val h: GameTestHelper) {
        val base=h.absolutePos(BlockPos.ZERO)
        private val prefix="t-${base.asLong()}-${h.level.gameTime}:"
        fun name(value: String)=prefix+value
        val sources=mutableMapOf<FaceNodeEntity,FaceNodeEntity>()
        val a: FaceNodeEntity
        val b: FaceNodeEntity
        init {
            GameTestYards.clear(h,260,2,7)
            h.level.getEntities(null,net.minecraft.world.phys.AABB(base.x.toDouble(),258.0,base.z.toDouble(),base.x+17.0,265.0,base.z+17.0)).filter { it is Cow || it is ItemEntity }.forEach { it.discard() }
            a=device(4,8,"A");b=device(14,8,"B")
        }
        fun p(n: FaceNodeEntity)=n.parts[0]!!
        fun device(x: Int,z: Int,name: String): FaceNodeEntity {
            val at=BlockPos(base.x+x,260,base.z+z)
            h.level.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(at,Content.machine.get().defaultBlockState())
            val n=h.level.getBlockEntity(at) as FaceNodeEntity
            n.parts[0]=PartState(PartDefinition.byId.getValue("experimental_transporter"),Direction.DOWN).apply { expression=this@Yard.name(name);channel=this@Yard.name("unused");value=20000.0 }
            GridMultiblock.install(n,p(n));n.changedTopology()
            val port=RemotePowerPorts.positions(n,p(n)).first();val supply=port.position.relative(port.direction)
            h.level.setBlockAndUpdate(supply.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(supply,Content.node.get().defaultBlockState())
            val source=h.level.getBlockEntity(supply) as FaceNodeEntity
            source.parts[0]=PartState(PartDefinition.byId.getValue("electrical_source"),Direction.DOWN).apply { value=800.0 };source.changedTopology();sources[n]=source
            return n
        }
        fun cow(node: FaceNodeEntity=a): Cow {
            val at=Transporters.chamber(node,p(node));return EntityType.COW.create(h.level)!!.also {
                it.setNoAi(true);it.setNoGravity(true);it.setPos(at.x+.5,at.y+.08,at.z+.5);it.deltaMovement=Vec3.ZERO;h.level.addFreshEntity(it)
            }
        }
        fun item(node: FaceNodeEntity=a): ItemEntity {
            val at=Transporters.chamber(node,p(node));return ItemEntity(h.level,at.x+.5,at.y+.6,at.z+.5,ItemStack(Items.DIAMOND)).also {
                it.setNoGravity(true);it.deltaMovement=Vec3.ZERO;h.level.addFreshEntity(it)
            }
        }
        fun start(node: FaceNodeEntity=a,target: String="B")=Transporters.configure(node,p(node),p(node).expression,name(target),20000.0,true)
        fun ledger(node: FaceNodeEntity=a) {
            val p=p(node);val accounted=p.progress+p.gridState.getDouble("transporterSpentJ")+p.gridState.getDouble("transporterDiscardedJ")+p.gridState.getDouble("transporterIdleLossJ")
            if(abs(p.energy-accounted)>1e-4)h.fail("Transporter paid work is not conserved: ${p.energy} versus $accounted")
        }
    }
    @JvmStatic @GameTest(batch="original_transporter",template="grid_empty",timeoutTicks=245)
    fun actualNamedPairClosesBothDoorsPaysOriginalOccupantAndDistanceCostAndMovesRealEntitiesOnce(h: GameTestHelper) {
        val y=Yard(h);val cow=y.cow();val item=y.item();val before=h.level.forcedChunks.toSet()
        val from=Transporters.chamber(y.a,y.p(y.a));val to=Transporters.chamber(y.b,y.p(y.b))
        val expected=20000*(1+sqrt(sqrt(from.distSqr(to))/250))
        h.runAtTickTime(10) {
            if(y.p(y.a).definition.legacyId!=4160 || abs(h.level.getBlockState(from).getCollisionShape(h.level,from).bounds().maxY-.0625)>1e-9 || RemotePowerPorts.active(h.level,y.a,y.p(y.a)).size!=8)h.fail("Original transporter registration, walkable 1/16-block floor or eight raised contact edges changed")
            y.start()
        }
        h.runAtTickTime(30) {
            if(Transporters.phase(y.p(y.a))!=Transporters.START || Transporters.phase(y.p(y.b))!=Transporters.RESERVED || abs(y.p(y.a).gridState.getDouble("transporterTargetJ")-expected)>1e-6)h.fail("Named target reservation or original player/pet/item/distance charge formula failed: ${y.p(y.a).save()}")
        }
        h.runAtTickTime(75) {
            if(!Transporters.closed(y.p(y.a)) || !Transporters.closed(y.p(y.b)))h.fail("Master and reserved receiver did not close their actual doors")
            for(n in listOf(y.a,y.b))for(offset in Transporters.closedDoor)if(h.level.getBlockState(GridMultiblock.offset(n.blockPos,y.p(n),offset)).block!=Content.gridGhost.get())h.fail("Closed transporter door has no physical owned reservation")
        }
        h.runAtTickTime(165) {
            val p=y.p(y.a)
            if(p.gridState.getInt("transporterTrips")!=1 || p.gridState.getInt("transporterMoved")!=2 || p.gridState.getDouble("transporterSpentJ")<expected || !Transporters.area(y.b,y.p(y.b)).contains(cow.position()) || !Transporters.area(y.b,y.p(y.b)).contains(item.position()) || item.item.count!=1)h.fail("Real cow and item did not arrive exactly once after paying original charge: ${p.save()}, cow=${cow.position()}, item=${item.position()}")
            y.ledger();y.ledger(y.b)
        }
        h.runAtTickTime(220) {
            if(Transporters.phase(y.p(y.a))!=Transporters.IDLE || Transporters.phase(y.p(y.b))!=Transporters.IDLE || Transporters.closed(y.p(y.a)) || Transporters.closed(y.p(y.b)) || y.p(y.a).gridState.getInt("transporterTrips")!=1 || h.level.forcedChunks.toSet()!=before)h.fail("Completed trip left a stale reservation, closed door, duplicate trip or additional forced chunk")
            y.ledger();cow.discard();item.discard();h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_transporter",template="grid_empty",timeoutTicks=255)
    fun circuitRebuildRetainsChargeButActualNodeReloadResetsItsInterruptedTripWithoutFreeArrival(h: GameTestHelper) {
        val y=Yard(h);val cow=y.cow();val item=y.item();var paid=0.0
        h.runAtTickTime(10) { y.start() }
        h.runAtTickTime(120) {
            if(Transporters.phase(y.p(y.a))!=Transporters.CHARGE || y.p(y.a).progress<1000)h.fail("Reload fixture did not reach real partially paid charging")
            paid=y.p(y.a).progress;y.a.changedTopology()
        }
        h.runAtTickTime(123) {
            if(Transporters.phase(y.p(y.a))!=Transporters.CHARGE || y.p(y.a).progress<paid)h.fail("Ordinary circuit rebuild reset the running paired reservation or paid charge")
            val saved=y.a.saveWithoutMetadata();paid=y.p(y.a).progress;y.a.load(saved);y.a.changedTopology()
        }
        h.runAtTickTime(225) {
            if(Transporters.phase(y.p(y.a))!=Transporters.IDLE || Transporters.phase(y.p(y.b))!=Transporters.IDLE || y.p(y.a).gridState.getInt("transporterTrips")!=0 || y.p(y.a).gridState.getDouble("transporterDiscardedJ")<paid || !Transporters.area(y.a,y.p(y.a)).contains(cow.position()) || !Transporters.area(y.a,y.p(y.a)).contains(item.position()))h.fail("Actual reload kept a stale peer reservation, free arrival or lost paid charge accounting: ${y.p(y.a).save()}")
            y.ledger();cow.discard();item.discard();h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_transporter",template="grid_empty",timeoutTicks=100)
    fun originalNameErrorsBusyReservationAndCraftingAreRealWithoutStartingUnpaidTrips(h: GameTestHelper) {
        OriginalCraftingGameTests.craft(h,listOf("RMR","RMR"," R "),mapOf('R' to "eln:advanced_chip",'M' to "eln:advanced_machine_block"),"eln:experimental_transporter")
        val y=Yard(h);val c=y.device(14,13,"B")
        h.runAtTickTime(10) { y.start(target="Missing") }
        h.runAtTickTime(20) { if(y.p(y.a).gridState.getString("transporterEvent")!="missing")h.fail("Missing destination did not report the original error");y.start(target="A") }
        h.runAtTickTime(30) { if(y.p(y.a).gridState.getString("transporterEvent")!="same")h.fail("Same-name destination did not reject self transport");y.start() }
        h.runAtTickTime(40) {
            if(y.p(y.a).gridState.getString("transporterEvent")!="multiple")h.fail("Duplicate names did not reject an ambiguous destination")
            Transporters.configure(c,y.p(c),y.name("C"),y.name("B"),20000.0,false)
        }
        h.runAtTickTime(50) { y.start(c) }
        h.runAtTickTime(65) { y.start() }
        h.runAtTickTime(80) {
            if(y.p(y.a).gridState.getString("transporterEvent")!="busy" || Transporters.phase(y.p(y.a))!=Transporters.IDLE || Transporters.phase(y.p(y.b))!=Transporters.RESERVED || Transporters.phase(y.p(c))!=Transporters.START || y.p(y.a).progress!=0.0 || y.p(y.a).gridState.getInt("transporterTrips")!=0)h.fail("Busy reservation, empty-chamber wait or unpaid trip isolation failed")
            y.ledger();y.ledger(c);h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_transporter",template="grid_empty",timeoutTicks=250)
    fun unstableActualChargingSupplyTriggersOriginalMalfunctionDiscardsPaidWorkAndNeverForcesRandomChunks(h: GameTestHelper) {
        val y=Yard(h);val cow=y.cow();val item=y.item();val forced=h.level.forcedChunks.toSet();var paid=0.0
        h.runAtTickTime(10) { y.start() }
        h.runAtTickTime(120) {
            if(Transporters.phase(y.p(y.a))!=Transporters.CHARGE || y.p(y.a).progress<=1000)h.fail("Unstable-supply fixture did not reach actual paid charging")
            paid=y.p(y.a).progress;y.sources.getValue(y.a).apply { parts[0]!!.enabled=false;changedTopology() }
        }
        h.runAtTickTime(145) {
            if(y.p(y.a).gridState.getString("transporterEvent")!="unstable" || y.p(y.a).gridState.getDouble("transporterDiscardedJ")<paid || y.p(y.a).gridState.getInt("transporterTrips")!=0 || Transporters.closed(y.p(y.a)) || h.level.forcedChunks.toSet()!=forced)h.fail("Experimental power-loss malfunction kept paid work, completed a free trip, left a closed door or forced a random chunk")
            for(entity in listOf(cow,item))if(h.level.chunkSource.getChunkNow(entity.blockX shr 4,entity.blockZ shr 4)==null)h.fail("Experimental malfunction moved an entity into an unavailable random chunk")
            y.ledger()
        }
        h.runAtTickTime(225) {
            if(Transporters.phase(y.p(y.b))!=Transporters.IDLE || Transporters.closed(y.p(y.b)))h.fail("Failed master left its destination reserved or closed")
            cow.discard();item.discard();h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_transporter_overvoltage",template="grid_empty",timeoutTicks=60)
    fun originalTransporterOvervoltageWatchdogDestroysItsActualMultiblock(h: GameTestHelper) {
        val y=Yard(h);val cells=GridMultiblock.cells(y.a.blockPos,y.p(y.a));y.sources.getValue(y.a).apply { parts[0]!!.value=2000.0;changedTopology() }
        h.runAtTickTime(40) {
            if(h.level.getBlockEntity(y.a.blockPos) is FaceNodeEntity || cells.any { h.level.getBlockState(it).block==Content.gridGhost.get() })h.fail("Original transporter watchdog left an overvolted machine or owned ghosts behind")
            h.succeed()
        }
    }
}
