package mods.eln.modern.testing

import mods.eln.modern.*
import mods.eln.modern.grid.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object LargeShaftGameTests {
    fun create(h: GameTestHelper,id: String="large_steam_turbine",rotation: Int=0): FaceNodeEntity {
        GameTestYards.clear(h,250,2,5)
        val base=h.absolutePos(BlockPos.ZERO)
        return at(h,id,BlockPos(base.x+5,250,base.z+5),rotation)
    }
    fun at(h: GameTestHelper,id: String,pos: BlockPos,rotation: Int=0): FaceNodeEntity {
        val p=PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { this.rotation=rotation }
        h.level.removeBlock(pos,false);h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
        h.level.setBlockAndUpdate(pos,(if(p.definition.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
        val node=h.level.getBlockEntity(pos) as FaceNodeEntity;node.parts[0]=p
        if(!GridMultiblock.canPlace(h.level,pos,p))h.fail("Original large shaft footprint rejected its clear test yard")
        GridMultiblock.install(node,p);node.changedTopology();return node
    }
    @JvmStatic @GameTest(batch="large_shafts",template="grid_empty",timeoutTicks=80)
    fun originalLargeBodiesReserveTwentyCellsAndRotateTheirRemoteAxles(h: GameTestHelper) {
        val node=create(h)
        for(rotation in 0..3) {
            GridMultiblock.remove(node,node.parts[0]!!)
            val p=PartState(PartDefinition.byId.getValue("large_steam_turbine"),Direction.DOWN).apply { this.rotation=rotation }
            node.parts[0]=p;GridMultiblock.install(node,p);node.changedTopology()
            val cells=GridMultiblock.cells(node.blockPos,p);val ports=MechanicalNetwork.portPositions(node,p)
            if(cells.size!=20 || ports.size!=2 || ports.any { it.position.y!=node.blockPos.y+1 || it.position !in cells || it.position!=node.blockPos.above().relative(it.direction) })h.fail("Original large body or raised outward shaft ports did not rotate together")
            if(cells.any { (h.level.getBlockEntity(it) as? GridGhostEntity)?.ownerId!=p.gridId })h.fail("Large body failed UUID ownership")
            val bounds=PartGeometry.body(p)
            val small=PartGeometry.body(PartState(PartDefinition.byId.getValue("steam_turbine"),Direction.DOWN).apply { this.rotation=rotation })
            if(p.definition.shaftMass!=135.0 || p.definition.visual.scale!=3.0 || p.definition.visual.modelLift!=1.0 || abs(bounds.minY-small.minY*3)>1e-8 || abs(bounds.maxY-small.maxY*3)>1e-8)h.fail("Large machine lost its original volume, exact scaled mesh or one-block lift: $bounds")
        }
        h.runAtTickTime(20) {
            if(node.tank.capacity!=194400 || node.parts[0]!!.energy!=0.0 || MechanicalNetwork.remotePorts(h.level,node,node.parts[0]!!).size!=2)h.fail("Empty large turbine generated work, lost its scaled tank or failed its real ghost ports")
            val cells=GridMultiblock.cells(node.blockPos,node.parts[0]!!)
            h.level.destroyBlock(MechanicalNetwork.portPositions(node,node.parts[0]!!).first().position,true)
            if(h.level.getBlockEntity(node.blockPos) is FaceNodeEntity || cells.any { h.level.getBlockEntity(it) is GridGhostEntity })h.fail("Breaking a remote shaft port left an orphaned large machine")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="large_shafts",template="grid_empty",timeoutTicks=140)
    fun originalLargeMotorDrivesTheRaisedGhostShaftAndRetainsOwnershipOnReload(h: GameTestHelper) {
        val motor=create(h,"large_shaft_motor");val p=motor.parts[0]!!
        at(h,"electrical_source",motor.blockPos.east()).parts[0]!!.value=12800.0
        val outlet=MechanicalNetwork.portPositions(motor,p).first { it.direction==Direction.SOUTH }
        val wheel=at(h,"flywheel",outlet.position.south())
        val wrong=at(h,"joint",wheel.blockPos.below())
        h.runAtTickTime(65) {
            val current=motor.parts[0]!!
            if(current.shaftSpeed<50 || abs(current.shaftSpeed-wheel.parts[0]!!.shaftSpeed)>1e-9 || wrong.parts[0]!!.shaftSpeed!=0.0 || current.thermalState.getDouble("shaftProducedJ")<=0)h.fail("Large motor did not transmit real work exclusively through its raised outward ghost axle")
            val saved=motor.saveWithoutMetadata();val id=current.gridId;motor.load(saved);motor.changedTopology()
            if(motor.parts[0]!!.gridId!=id)h.fail("Large motor changed body ownership on reload")
        }
        h.runAtTickTime(100) {
            if(abs(motor.parts[0]!!.shaftSpeed-wheel.parts[0]!!.shaftSpeed)>1e-9 || motor.parts[0]!!.tripped || GridMultiblock.cells(motor.blockPos,motor.parts[0]!!).any { (h.level.getBlockEntity(it) as? GridGhostEntity)?.ownerId!=motor.parts[0]!!.gridId })h.fail("Reload detached a real large shaft or its reserved body")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="large_shafts",template="grid_empty",timeoutTicks=165)
    fun polarizedMotorUsesRelativeVoltageAndRequiresItsSeparateReturnTerminal(h: GameTestHelper) {
        val motor=create(h,"polarized_shaft_motor");val wheel=at(h,"flywheel",motor.blockPos.south())
        at(h,"electrical_source",motor.blockPos.east()).parts[0]!!.value=3500.0
        val negative=at(h,"electrical_source",motor.blockPos.west());negative.parts[0]!!.value=300.0
        var received=0.0
        h.runAtTickTime(100) {
            val p=motor.parts[0]!!
            if(p.shaftSpeed<100 || p.shaftSpeed>201 || abs(p.voltage-p.voltageB-3200)>15 || abs(p.voltageB-300)>15 || p.thermalState.getDouble("shaftProducedJ")<=0 || wheel.parts[0]!!.shaftEnergy<=0)h.fail("Polarized motor used ground voltage instead of its separate relative terminals: ${p.save()}")
            received=p.thermalState.getDouble("shaftElectricalInputJ");h.level.removeBlock(negative.blockPos,false)
        }
        h.runAtTickTime(140) {
            if(motor.parts[0]!!.thermalState.getDouble("shaftElectricalInputJ")-received>.01)h.fail("Polarized motor continued to draw real power with its return wire disconnected")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="large_shafts",template="grid_empty",timeoutTicks=130)
    fun polarizedGeneratorPowersARealFloatingLoopWithoutAHiddenGroundReturn(h: GameTestHelper) {
        val generator=create(h,"polarized_shaft_generator");val pos=generator.blockPos
        val wheel=at(h,"flywheel",pos.south())
        for(n in listOf(generator,wheel))n.parts[0]!!.shaftGroup="floating-generator-primer"
        wheel.parts[0]!!.shaftEnergy=55000.0;wheel.changedTopology()
        for(offset in listOf(1 to 0,1 to 2,0 to 2,-1 to 2,-1 to 1,-1 to 0))at(h,"high_current_cable",pos.offset(offset.first,0,offset.second))
        val load=at(h,"creative_power_resistor",pos.offset(1,0,1),3);load.parts[0]!!.value=10000.0
        h.runAtTickTime(90) {
            val p=generator.parts[0]!!;val output=p.thermalState.getDouble("shaftElectricalOutputJ");val used=p.thermalState.getDouble("shaftConsumedJ")
            if(p.voltage<=0 || p.voltageB>=-100 || p.voltage-p.voltageB<2000 || load.parts[0]!!.energy<500 || abs(output-used*.95)>1e-5 || used+p.shaftEnergy+wheel.parts[0]!!.shaftEnergy>55000+1e-5 || load.parts[0]!!.energy>output+1e-5)h.fail("Polarized generator failed its finite genuinely floating electrical loop: ${p.save()}")
            h.succeed()
        }
    }
}
