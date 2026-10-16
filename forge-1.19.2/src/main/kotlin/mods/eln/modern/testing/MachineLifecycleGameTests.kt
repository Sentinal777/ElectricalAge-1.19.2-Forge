package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object MachineLifecycleGameTests {
    @JvmStatic @GameTest(batch="machine_lifecycle",template="grid_empty",timeoutTicks=55)
    fun delayedLoadCallbackCannotReplaceTheActualMachineAtTheSamePosition(h: GameTestHelper) {
        val source=TurbineGameTests.create(h,"electrical_source");source.parts[0]!!.value=50.0
        val resistor=TurbineGameTests.create(h,"creative_power_resistor",4);resistor.parts[0]!!.apply { rotation=2;value=100.0 }
        TurbineGameTests.create(h,"ground_cable",5)
        // Forge can deliver a queued callback after a block at this position has been replaced.
        val stale=FaceNodeEntity(source.blockPos,source.blockState).apply {
            setLevel(h.level);parts[0]=PartState(source.parts[0]!!.definition,Direction.DOWN).apply { value=5.0 }
        }
        stale.onLoad()
        h.runAtTickTime(20) {
            if(resistor.parts[0]!!.voltage<49 || resistor.parts[0]!!.energy<20)h.fail("A stale load callback replaced the live source in the circuit registry")
            stale.setRemoved()
        }
        h.runAtTickTime(35) {
            if(resistor.parts[0]!!.voltage<49)h.fail("Detaching the stale callback also removed the live replacement")
            h.succeed()
        }
    }
}
