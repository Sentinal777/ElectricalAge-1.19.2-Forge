package mods.eln.modern.testing

import mods.eln.modern.*
import mods.eln.config.ForgePortConfig
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object TimerAlarmGameTests {
    private fun device(h: GameTestHelper,id: String,x: Int=8,z: Int=8): FaceNodeEntity {
        GameTestYards.clear(h,205,4,18)
        val pos=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+x,205,it.z+z) }
        h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(pos,Content.node.get().defaultBlockState())
        return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN);it.changedTopology() }
    }
    @JvmStatic @GameTest(batch="original_timers",template="grid_empty",timeoutTicks=150)
    fun actualTimerRetriggersOnHighKeepsOriginalSchmittBandAndTimesItsIndependentOutput(h: GameTestHelper) {
        val n=device(h,"electrical_timer");val p=n.parts[0]!!;val source=device(h,"signal_source",9).parts[0]!!;val wire=device(h,"signal_cable",7).parts[0]!!
        h.runAtTickTime(20) {
            if(!p.active || !p.thermalState.getBoolean("timerHigh") || abs(p.thermalState.getDouble("timerLeft")-2)>.001 || wire.voltage<4.99 || p.connectedMask!=5)h.fail("Timer did not retrigger through its actual input and separate output pins")
            source.value=2.0
        }
        h.runAtTickTime(40) {
            if(!p.thermalState.getBoolean("timerHigh") || abs(p.thermalState.getDouble("timerLeft")-2)>.001)h.fail("Timer lost its high Schmitt state in the original 1–3 V band")
            source.value=.5
        }
        h.runAtTickTime(55) {
            if(p.thermalState.getBoolean("timerHigh") || p.thermalState.getDouble("timerLeft") !in 1.20..1.31 || wire.voltage<4.99)h.fail("Timer did not count physical seconds after input fell low")
            source.value=2.0
        }
        h.runAtTickTime(90) {
            if(p.active || p.thermalState.getBoolean("timerHigh") || wire.voltage>.001)h.fail("Low Schmitt state retriggered inside the band or timer output stayed powered after expiration")
            source.value=5.0
        }
        h.runAtTickTime(110) {
            if(!p.active || abs(p.thermalState.getDouble("timerLeft")-2)>.001 || wire.voltage<4.99)h.fail("Expired timer could not be retriggered")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_timers",template="grid_empty",timeoutTicks=110)
    fun timerManualSetResetAndSavedCountdownSurviveActualCircuitRebuilds(h: GameTestHelper) {
        val n=device(h,"electrical_timer");var p=n.parts[0]!!;p.value=3.0
        h.runAtTickTime(10) { if(!TimerSettings.command(n,p,"set"))h.fail("Timer rejected its original manual set command") }
        var left=0.0
        h.runAtTickTime(25) {
            left=p.thermalState.getDouble("timerLeft")
            if(!p.active || left !in 2.20..2.31 || p.thermalState.getInt("timerTicks") !in 280..320)h.fail("Manual timer did not run on the original 400 Hz clock")
            val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology();p=n.parts[0]!!
            if(p.thermalState.getDouble("timerLeft")!=left)h.fail("Timer reload lost its remaining interval")
        }
        h.runAtTickTime(40) {
            if(abs(p.thermalState.getDouble("timerLeft")-(left-.75))>.051)h.fail("Timer restarted its countdown when its physical circuit was rebuilt")
            TimerSettings.command(n,p,"reset")
        }
        h.runAtTickTime(50) {
            if(p.active || p.thermalState.getDouble("timerLeft")!=0.0)h.fail("Timer reset did not clear output")
            p.value=.5;TimerSettings.command(n,p,"set")
        }
        h.runAtTickTime(70) {
            if(p.active || p.thermalState.getDouble("timerLeft")!=0.0 || TimerSettings.duration(Double.NaN) || TimerSettings.duration(-1.0) || !TimerSettings.duration(200000.0) || TimerSettings.command(n,p,"invalid"))h.fail("Timer manual interval or server command validation is incorrect")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="timer_precision",template="grid_empty",timeoutTicks=90)
    fun changingActualMnaPrecisionDoesNotChangeTheOriginalFourHundredHertzTimer(h: GameTestHelper) {
        val n=device(h,"electrical_timer");val p=n.parts[0]!!;p.value=2.0;val previous=ForgePortConfig.simulationSteps.get()
        var left=0.0;var ticks=0
        h.runAtTickTime(15) { TimerSettings.command(n,p,"set") }
        h.runAtTickTime(20) { left=p.thermalState.getDouble("timerLeft");ticks=p.thermalState.getInt("timerTicks");ForgePortConfig.simulationSteps.set(1) }
        h.runAtTickTime(30) {
            val correct=abs(p.thermalState.getDouble("timerLeft")-(left-.5))<.001 && p.thermalState.getInt("timerTicks")==ticks+200
            ForgePortConfig.simulationSteps.set(40)
            if(!correct) { ForgePortConfig.simulationSteps.set(previous);h.fail("One MNA step per tick changed the physical timer rate") }
        }
        h.runAtTickTime(40) {
            ForgePortConfig.simulationSteps.set(previous)
            if(abs(p.thermalState.getDouble("timerLeft")-(left-1.0))>.001 || p.thermalState.getInt("timerTicks")!=ticks+400)h.fail("Forty MNA steps per tick changed the physical timer rate")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_alarms",template="grid_empty",timeoutTicks=160)
    fun originalAlarmClipsKeepDifferentRepeatPeriodsAndSignalSampling(h: GameTestHelper) {
        val nuclear=device(h,"nuclear_alarm",7);val standard=device(h,"standard_alarm",12)
        val ns=device(h,"signal_source",8).parts[0]!!;val ss=device(h,"signal_source",13).parts[0]!!
        h.runAtTickTime(125) {
            val a=nuclear.parts[0]!!;val b=standard.parts[0]!!
            if(!a.active || !b.active || a.signal!=7 || b.signal!=7 || a.connectedMask!=1 || b.connectedMask!=1 || a.thermalState.getInt("alarmPulse")!=1 || b.thermalState.getInt("alarmPulse") !in 4..6)h.fail("Original alarm clip periods, light level or input-only terminal differ: ${a.save()} ${b.save()}")
            ns.value=2.49;ss.value=2.49
        }
        h.runAtTickTime(140) {
            if(nuclear.parts[0]!!.active || standard.parts[0]!!.active || nuclear.parts[0]!!.signal!=0 || standard.parts[0]!!.signal!=0)h.fail("Alarm failed to switch off below the original 2.5 V threshold")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_alarms",template="grid_empty",timeoutTicks=300)
    fun mutingAndReloadingActualAlarmPreserveItsBeaconAndMinecraftBlockLight(h: GameTestHelper) {
        val n=device(h,"standard_alarm");var p=n.parts[0]!!;val source=device(h,"signal_source",9).parts[0]!!
        var started=false
        // Section lighting is asynchronous, especially while the large isolated fixtures are loading.
        for(t in 30L..170L)h.runAtTickTime(t) {
            if(!started && p.active && n.blockState.getLightEmission(h.level,n.blockPos)==7 && h.level.getBrightness(LightLayer.BLOCK,n.blockPos)==7) {
                val lightingView=object : net.minecraft.world.level.BlockGetter by h.level {
                    override fun getBlockEntity(pos: BlockPos): net.minecraft.world.level.block.entity.BlockEntity?=null
                }
                if(n.blockState.getLightEmission(lightingView,n.blockPos)!=7)h.fail("Alarm light depends on a block entity unavailable to the lighting worker")
                started=true;p.enabled=false;n.changedTopology();var sequence=0
                h.runAfterDelay(15) {
                    sequence=p.thermalState.getInt("alarmPulse")
                    val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology();p=n.parts[0]!!
                }
                h.runAfterDelay(40) {
                    if(!p.active || p.enabled || p.signal!=7 || p.thermalState.getInt("alarmPulse")!=sequence || h.level.getBrightness(LightLayer.BLOCK,n.blockPos)!=7)h.fail("Muting or reloading alarm extinguished its independent beacon or kept repeating sound")
                    p.enabled=true;n.changedTopology()
                }
                h.runAfterDelay(60) {
                    if(p.thermalState.getInt("alarmPulse")<=sequence || !p.active)h.fail("Unmuted alarm did not resume its original sound sequence")
                    source.value=0.0
                }
                var cleared=false
                for(delay in 80L..110L)h.runAfterDelay(delay) {
                    if(!cleared && !p.active && n.blockState.getLightEmission(lightingView,n.blockPos)==0 && h.level.getBrightness(LightLayer.BLOCK,n.blockPos)==0) { cleared=true;h.succeed() }
                }
                h.runAfterDelay(115) { if(!cleared)h.fail("Deactivated alarm left stale block state or actual Minecraft beacon light") }
            }
        }
        h.runAtTickTime(175) { if(!started)h.fail("Powered alarm never acquired real Minecraft block light: block=${h.level.getBlockState(n.blockPos)}, emission=${n.blockState.getLightEmission(h.level,n.blockPos)}, actual=${h.level.getBrightness(LightLayer.BLOCK,n.blockPos)}, pending=${h.level.chunkSource.lightEngine.hasLightWork()}, state=${p.save()}") }
    }
}
