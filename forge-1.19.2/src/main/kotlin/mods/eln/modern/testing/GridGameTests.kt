package mods.eln.modern.testing

import mods.eln.modern.*
import mods.eln.modern.grid.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln")
@PrefixGameTestTemplate(false)
object GridGameTests {
    private fun device(h: GameTestHelper,id: String,x: Int,z: Int=8,rotation: Int=0)=
        InfrastructureGameTests.create(h,id,x,z,rotation).also { n -> GridMultiblock.install(n,n.parts[0]!!) }
    private fun wire(h: GameTestHelper,a: FaceNodeEntity,b: FaceNodeEntity,sa: Direction=Direction.NORTH,sb: Direction=Direction.NORTH)=GridNetwork.link(h.level,a,sa,b,sb)
    private fun check(h: GameTestHelper,value: Boolean,message: String) { if(!value)h.fail(message) }
    @JvmStatic @GameTest(template="empty",timeoutTicks=1600)
    fun actualChunkUnloadAndDeferredGhostBreak(h: GameTestHelper) {
        val level=h.level
        val base=h.absolutePos(BlockPos.ZERO)
        val cx=Math.floorDiv(base.x,16)+512;val cz=Math.floorDiv(base.z,16)+512
        val x=cx*16;val z=cz*16+8
        val forced=listOf(cx-3,cx,cx+3)
        forced.forEach { level.setChunkForced(it,cz,true);level.getChunk(it,cz) }
        val made=mutableListOf<FaceNodeEntity>()
        fun device(id: String,px: Int,rotation: Int=0): FaceNodeEntity {
            val pos=BlockPos(px,120,z)
            GridNetwork.removeAt(level,pos,false)
            (level.getBlockEntity(pos) as? FaceNodeEntity)?.let { old -> old.parts.filterNotNull().forEach { GridMultiblock.remove(old,it) } }
            level.removeBlock(pos,false)
            level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
            val d=PartDefinition.byId.getValue(id)
            level.setBlockAndUpdate(pos,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
            val n=level.getBlockEntity(pos) as FaceNodeEntity
            n.parts[0]=PartState(d,Direction.DOWN).apply { this.rotation=rotation };n.changedTopology()
            GridMultiblock.cells(pos,n.parts[0]!!).forEach { cell -> if(level.hasChunkAt(cell))level.setBlockAndUpdate(cell,Blocks.AIR.defaultBlockState()) }
            GridMultiblock.install(n,n.parts[0]!!);made.add(n);return n
        }
        val left=device("transmission_tower",x-48);val middle=device("transmission_tower",x);val right=device("transmission_tower",x+48)
        val input=device("direct_utility_pole",x-46);device("electrical_source",x-45)
        val output=device("direct_utility_pole",x+50);val resistor=device("creative_power_resistor",x+51,2).parts[0]!!;device("ground_cable",x+52)
        for((a,b) in listOf(input to left,left to middle,middle to right,right to output))GridNetwork.link(level,a,Direction.NORTH,b,Direction.NORTH)
        fun cleanup() {
            for(n in made)if(level.hasChunkAt(n.blockPos)) { GridNetwork.removeAt(level,n.blockPos,false);level.removeBlock(n.blockPos,false) }
            forced.forEach { level.setChunkForced(it,cz,false) }
        }
        fun ensure(value: Boolean,message: String) { if(!value) { cleanup();h.fail(message) } }
        fun ready()=made.filter { n -> level.hasChunkAt(n.blockPos) }.all { n -> level.chunkSource.isPositionTicking(net.minecraft.world.level.ChunkPos.asLong(n.blockPos)) }
        h.startSequence()
            .thenWaitUntil { check(h,ready(),"Forced transmission chunks have not started ticking") }
            .thenIdle(5)
            .thenExecute { ensure(resistor.current>.4,"Cross-chunk tower circuit did not power its load");level.setChunkForced(cx,cz,false) }
            .thenIdle(395)
            .thenExecute {
                ensure(!level.hasChunkAt(middle.blockPos),"The middle tower chunk remained loaded")
                ensure(abs(resistor.current)<1e-6,"An actually unloaded tower left powered ghost connections")
                level.setChunkForced(cx,cz,true);level.getChunk(cx,cz)
            }
            .thenWaitUntil { check(h,level.hasChunkAt(middle.blockPos) && ready(),"Reloaded transmission chunks have not started ticking") }
            .thenIdle(5)
            .thenExecute {
                ensure(resistor.current>.4,"An intact tower failed to reconnect after its actual chunk reloaded")
                val restored=level.getBlockEntity(middle.blockPos) as? FaceNodeEntity
                ensure(restored?.parts?.get(0)?.gridId==middle.parts[0]!!.gridId,"Chunk reload replaced the saved tower identity")
                level.setChunkForced(cx,cz,false)
            }
            .thenIdle(420)
            .thenExecute {
                ensure(!level.hasChunkAt(middle.blockPos),"The middle tower failed to unload a second time")
                ensure(abs(resistor.current)<1e-6,"Second chunk unload left a powered ghost connection")
                val ghost=middle.blockPos.west()
                ensure(level.hasChunkAt(ghost) && level.getBlockEntity(ghost) is GridGhostEntity,"Adjacent ghost chunk was not available for the test")
                level.removeBlock(ghost,false)
                ensure(GridNetwork.data(level).brokenOwners[middle.blockPos]==middle.parts[0]!!.gridId,"Ghost break did not persist a deferred owner removal")
                level.setChunkForced(cx,cz,true);level.getChunk(cx,cz)
            }
            .thenWaitUntil { check(h,level.hasChunkAt(middle.blockPos) && ready(),"Broken tower chunk has not finished reloading") }
            .thenIdle(5)
            .thenExecute {
                ensure(level.getBlockState(middle.blockPos).isAir,"Reloading did not remove the broken tower")
                ensure(GridNetwork.data(level).wires.values.none { it.a.pos==middle.blockPos || it.b.pos==middle.blockPos },"Deferred removal left persistent spans")
                ensure(abs(resistor.current)<1e-6,"Broken tower reconnected after chunk reload")
                cleanup()
            }.thenSucceed()
    }
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=100)
    fun transmissionResistanceAndSavedEndpointDataRestore(h: GameTestHelper) {
        device(h,"electrical_source",1)
        val a=device(h,"direct_utility_pole",2);val middle=device(h,"utility_pole",12);val b=device(h,"direct_utility_pole",22)
        val resistor=device(h,"creative_power_resistor",23,rotation=2).parts[0]!!;device(h,"ground_cable",24)
        wire(h,a,middle);wire(h,middle,b)
        val saved=middle.saveWithoutMetadata()
        h.runAtTickTime(20) {
            check(h,resistor.current>.45,"Overhead grid did not power the real load")
            check(h,abs(a.parts[0]!!.voltage-b.parts[0]!!.voltage-resistor.current*2.0)<.005,"Wire loss did not equal I × original resistance × span length")
            check(h,GridData.load(GridNetwork.data(h.level).save(net.minecraft.nbt.CompoundTag())).wires.values.count { it.a.pos==middle.blockPos || it.b.pos==middle.blockPos }==2,"Saved grid duplicated or lost spans")
        }
        h.runAtTickTime(35) {
            middle.load(saved);middle.changedTopology()
            check(h,GridNetwork.data(h.level).wires.values.count { it.a.pos==middle.blockPos || it.b.pos==middle.blockPos }==2,"Restoring endpoint data deleted persistent connections")
        }
        h.runAtTickTime(60) { check(h,resistor.current>.45,"Saved endpoint did not reconnect after reload");h.succeed() }
    }
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=80)
    fun twoStageGridTransformersPreserveRatios(h: GameTestHelper) {
        device(h,"electrical_source",1).parts[0]!!.value=200.0
        val a=device(h,"direct_utility_pole",2);val transformer=device(h,"grid_dc_dc_converter",10)
        val pole=device(h,"utility_pole_w_dc_dc_converter",18)
        val resistor=device(h,"creative_power_resistor",19,rotation=2).parts[0]!!;device(h,"ground_cable",20)
        wire(h,a,transformer);wire(h,transformer,pole,Direction.SOUTH)
        h.runAtTickTime(35) {
            val t=transformer.parts[0]!!;val p=pole.parts[0]!!
            check(h,abs(t.voltageB/t.voltage-.25)<1e-5,"Grid transformer used wrong ratio")
            check(h,abs(p.voltageB/p.voltage-.25)<1e-5,"Pole transformer used wrong ratio")
            check(h,resistor.current in .12.. .126,"Transformer chain failed to power load at about 12.5 V")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=80)
    fun cableInventoryCostDuplicateRejectionAndGhostBreak(h: GameTestHelper) {
        val a=device(h,"utility_pole",2);val b=device(h,"direct_utility_pole",20)
        h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(a.blockPos).inflate(5.0)).forEach { it.discard() }
        val player=h.makeMockPlayer();player.abilities.instabuild=false
        val cable=Content.partItems.getValue("high_voltage_cable").get();val held=ItemStack(cable,32)
        player.inventory.setItem(0,held)
        GridNetwork.useCable(a,player,held,Direction.UP);GridNetwork.useCable(b,player,held,Direction.UP)
        check(h,held.count==14 && GridNetwork.data(h.level).wires.values.single { it.a.pos==a.blockPos && it.b.pos==b.blockPos }.paidLength==18,"Grid connection did not consume actual span length")
        var duplicateRejected=false
        try { wire(h,a,b) } catch(_: IllegalArgumentException) { duplicateRejected=true }
        check(h,duplicateRejected,"Duplicate grid connection was accepted")
        val tower=device(h,"transmission_tower",45)
        var outOfRange=false
        try { wire(h,a,tower) } catch(_: IllegalArgumentException) { outOfRange=true }
        check(h,outOfRange,"A utility pole exceeded its original 40-block range")
        h.setBlock(2,3,8,Blocks.AIR)
        h.runAtTickTime(15) {
            check(h,GridNetwork.data(h.level).wires.values.none { it.a.pos==a.blockPos || it.b.pos==a.blockPos },"Breaking an upper ghost left a span")
            check(h,h.getBlockState(BlockPos(2,2,8)).isAir && h.getBlockState(BlockPos(2,4,8)).isAir,"Ghost break did not remove the whole pole")
            val refund=h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(a.blockPos).inflate(5.0)).filter { it.item.item===cable }.sumOf { it.item.count }
            check(h,refund==18,"A broken span did not refund its cable exactly once: $refund");h.succeed()
        }
    }
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=180)
    fun motorDrivenGridIsolatorUsesSeparatePowerAndSignalPorts(h: GameTestHelper) {
        device(h,"electrical_source",1).parts[0]!!.value=1000.0
        val a=device(h,"direct_utility_pole",2);val switch=device(h,"grid_switch",10);val b=device(h,"direct_utility_pole",18)
        val resistor=device(h,"creative_power_resistor",19,rotation=2).parts[0]!!;device(h,"ground_cable",20)
        wire(h,a,switch);wire(h,switch,b,Direction.SOUTH)
        fun wallSource(id: String,x: Int,value: Double): FaceNodeEntity {
            h.setBlock(x,1,7,Blocks.STONE)
            h.setBlock(x,1,6,Content.node.get())
            val n=h.getBlockEntity(BlockPos(x,1,6)) as FaceNodeEntity
            n.parts[Direction.SOUTH.get3DDataValue()]=PartState(PartDefinition.byId.getValue(id),Direction.SOUTH).apply { this.value=value }
            n.changedTopology();return n
        }
        wallSource("electrical_source",11,200.0)
        val control=wallSource("signal_source",10,0.0).parts[Direction.SOUTH.get3DDataValue()]!!
        h.runAtTickTime(25) {
            check(h,resistor.current>9,"Closed grid isolator did not conduct")
            control.value=5.0
        }
        h.runAtTickTime(105) {
            val p=switch.parts[0]!!
            check(h,p.gridState.getDouble("drive")>190 && p.gridState.getDouble("control")>4.8,"Isolator ports were not independent: drive=${p.gridState.getDouble("drive")}, control=${p.gridState.getDouble("control")}")
            check(h,!p.gridState.getBoolean("closed") && abs(resistor.current)<1e-5,"Powered signal did not open the isolator")
            control.value=0.0
        }
        h.runAtTickTime(155) { check(h,switch.parts[0]!!.gridState.getBoolean("closed") && resistor.current>9,"Isolator did not close after control reset");h.succeed() }
    }
}

