package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.core.Direction
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate

@GameTestHolder("eln")
@PrefixGameTestTemplate(false)
object CrankGameTests {
    @JvmStatic @GameTest(template="empty",timeoutTicks=155)
    fun humanCrankSuppliesFiniteSharedShaftEnergyAndRetainsItsOriginalSpeedLimit(h: GameTestHelper) {
        val crank=InfrastructureGameTests.create(h,"crank_shaft",2,2)
        val flywheel=InfrastructureGameTests.create(h,"flywheel",2,3)
        val player=h.makeMockPlayer()
        val hit=BlockHitResult(PartGeometry.body(crank.parts[0]!!).center.add(Vec3.atLowerCornerOf(crank.blockPos)),Direction.UP,crank.blockPos,false)
        fun turn() {
            if(!(crank.blockState.block as NodeBlock).use(crank.blockState,h.level,crank.blockPos,player,InteractionHand.MAIN_HAND,hit).consumesAction())h.fail("Real crank interaction was rejected")
        }
        h.runAtTickTime(5) { turn() }
        h.runAtTickTime(10) {
            val sum=crank.parts[0]!!.shaftEnergy+flywheel.parts[0]!!.shaftEnergy
            if(sum !in 8.5..10.0)h.fail("One 200 W / 20 Hz crank activation did not supply finite shaft energy: $sum J")
            if(kotlin.math.abs(crank.parts[0]!!.shaftSpeed-flywheel.parts[0]!!.shaftSpeed)>1e-9)h.fail("Crank failed to drive the connected flywheel")
        }
        for(t in 11L..120L)h.runAtTickTime(t) { turn() }
        var stored=0.0
        h.runAtTickTime(125) {
            val speed=crank.parts[0]!!.shaftSpeed
            if(speed !in 19.0..20.25)h.fail("Crank failed to retain the original 20 rad/s cutoff: $speed")
            stored=crank.parts[0]!!.shaftEnergy+flywheel.parts[0]!!.shaftEnergy
            if(stored>1110 || crank.parts[0]!!.energy>1110)h.fail("Crank duplicated human input energy")
            val a=crank.saveWithoutMetadata();val b=flywheel.saveWithoutMetadata()
            crank.load(a);flywheel.load(b);crank.changedTopology();flywheel.changedTopology()
        }
        h.runAtTickTime(140) {
            val sum=crank.parts[0]!!.shaftEnergy+flywheel.parts[0]!!.shaftEnergy
            if(sum>stored+1e-9 || sum<stored-30)h.fail("Reloading shared crank/flywheel state duplicated or lost its stored shaft energy")
            h.succeed()
        }
    }
}
