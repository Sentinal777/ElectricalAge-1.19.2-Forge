package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object ShaftHubGameTests {
    @JvmStatic @GameTest(batch="shaft_turbines",template="grid_empty",timeoutTicks=140)
    fun originalVerticalHubsTransmitRealShaftWorkAndPublishTheirSixSides(h: GameTestHelper) {
        val lower=TurbineGameTests.create(h,"joint_hub")
        fun above(id: String,south: Boolean=false): FaceNodeEntity {
            val pos=lower.blockPos.above().let { if(south)it.south()else it }
            h.level.setBlockAndUpdate(pos,Content.machine.get().defaultBlockState())
            return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN);it.changedTopology() }
        }
        val upper=above("joint_hub");val flywheel=above("flywheel",true)
        for(node in listOf(lower,upper,flywheel))node.parts[0]!!.shaftGroup="vertical-hub-priming-fixture"
        lower.parts[0]!!.shaftEnergy=5000.0;lower.changedTopology()
        h.runAtTickTime(35) {
            val a=lower.parts[0]!!;val b=upper.parts[0]!!;val c=flywheel.parts[0]!!
            if(a.definition.shaftMass!=5.0 || a.shaftSpeed<=0 || abs(a.shaftSpeed-b.shaftSpeed)>1e-9 || abs(b.shaftSpeed-c.shaftSpeed)>1e-9 || c.shaftEnergy<=0 || a.shaftEnergy+b.shaftEnergy+c.shaftEnergy>5000.0)h.fail("Original vertical hubs failed to turn shared finite shaft work through the vertical axis")
            if(a.gridState.getInt("shaftConnectedSides")!=(1 shl Direction.UP.get3DDataValue()) || a.gridState.getInt("shaftStandingSides") and (1 shl Direction.DOWN.get3DDataValue())==0)h.fail("Hub did not publish the connected shaft and opaque floor faces")
            if(b.gridState.getInt("shaftConnectedSides")!=((1 shl Direction.DOWN.get3DDataValue()) or (1 shl Direction.SOUTH.get3DDataValue())))h.fail("Upper hub failed to expose both sides of its right-angle shaft path")
            val saved=lower.saveWithoutMetadata();lower.load(saved);lower.changedTopology()
            h.level.setBlockAndUpdate(lower.blockPos.west(),Blocks.STONE.defaultBlockState())
        }
        h.runAtTickTime(55) {
            if(lower.parts[0]!!.gridState.getInt("shaftStandingSides") and (1 shl Direction.WEST.get3DDataValue())==0)h.fail("Opaque neighboring block did not update the hub's original stand mesh state")
            h.level.destroyBlock(upper.blockPos,true)
        }
        h.runAtTickTime(100) {
            val a=lower.parts[0]!!;val c=flywheel.parts[0]!!
            if(a.gridState.getInt("shaftConnectedSides")!=0 || a.shaftSpeed<=0 || abs(a.shaftSpeed-c.shaftSpeed)<1e-4 || a.shaftEnergy+c.shaftEnergy>5000)h.fail("Breaking a vertical hub retained a phantom shaft connection or duplicated stored work")
            h.succeed()
        }
    }
}
