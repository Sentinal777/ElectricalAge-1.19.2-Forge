package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.animal.Pig
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object FlywheelHazardGameTests {
    @JvmStatic @GameTest(batch="shaft_hazard",template="grid_empty",timeoutTicks=50)
    fun spinningFlywheelDamagesAndFlingsOnlyEntitiesInItsOriginalContactVolume(h: GameTestHelper) {
        val node=TurbineGameTests.create(h,"flywheel",4,4);val p=node.parts[0]!!;val pos=node.blockPos
        h.level.getEntitiesOfClass(Pig::class.java,AABB(pos).inflate(4.0)).forEach { it.discard() }
        fun pig(dx: Double=0.0,dy: Double=1.0): Pig = EntityType.PIG.create(h.level)!!.also {
            it.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)!!.baseValue=20.0;it.health=20f
            it.setNoAi(true);it.setNoGravity(true);it.moveTo(pos.x+.5+dx,pos.y+dy,pos.z+.5,0f,0f);h.level.addFreshEntity(it)
        }
        val stopped=pig();p.shaftSpeed=4.99;FlywheelRuntime.tick(node,p)
        if(stopped.health!=20f || stopped.deltaMovement!=Vec3.ZERO)h.fail("Stopped flywheel harmed a nearby animal")
        stopped.discard()
        for(rotation in 0..1) {
            p.rotation=rotation;p.shaftSpeed=125.0
            val contact=pig();val outside=pig(.51);val tooHigh=pig(dy=2.01)
            FlywheelRuntime.tick(node,p)
            val movement=contact.deltaMovement;val axis=FaceBasis.edges(p)[1].axis
            if(contact.health!=10f || abs(movement.y-.05)>1e-9 || abs((if(axis==Direction.Axis.X)movement.x else movement.z)-.5)>1e-9)h.fail("Flywheel lost its original speed-dependent contact damage or shaft-axis fling")
            if(outside.health!=20f || tooHigh.health!=20f || outside.deltaMovement!=Vec3.ZERO || tooHigh.deltaMovement!=Vec3.ZERO)h.fail("Flywheel harmed an animal outside the original narrow contact volume")
            contact.discard();outside.discard();tooHigh.discard()
        }
        // Verify the normal world tick invokes the same hazard, rather than only this direct fixture.
        p.shaftGroup="hazard-primer";p.shaftEnergy=.5*50*.05*100*100;node.changedTopology()
        val live=pig()
        h.runAtTickTime(10) {
            if(live.health>=20f)h.fail("Running flywheel did not apply its hazard from the server simulation")
            live.discard();h.succeed()
        }
    }
}
