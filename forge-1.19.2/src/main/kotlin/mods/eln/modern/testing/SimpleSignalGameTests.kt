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

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object SimpleSignalGameTests {
    private class Yard(val h: GameTestHelper) {
        val origin=h.absolutePos(BlockPos.ZERO)
        init { GameTestYards.clear(h,285,20,24) }
        fun at(x: Int,z: Int=8,height: Int=285)=BlockPos(origin.x+x,height,origin.z+z)
        fun device(at: BlockPos,id: String,rotation: Int=0,face: Direction=Direction.DOWN): FaceNodeEntity {
            h.level.setBlockAndUpdate(at.relative(face),Blocks.STONE.defaultBlockState())
            h.level.setBlockAndUpdate(at,Content.node.get().defaultBlockState())
            return (h.level.getBlockEntity(at) as FaceNodeEntity).also { n ->
                n.parts[face.get3DDataValue()]=PartState(PartDefinition.byId.getValue(id),face).apply { this.rotation=rotation };n.changedTopology()
            }
        }
        fun part(at: BlockPos,id: String,rotation: Int=0)=device(at,id,rotation).parts[0]!!
    }
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=70)
    fun simpleControlsAndSensorsExposeOriginalSinglePortsOnEveryMountingFaceAndRotation(h: GameTestHelper) {
        val y=Yard(h);val parts=mutableListOf<PartState>()
        val ids=listOf("signal_trimmer","signal_switch","signal_button","electrical_daylight_sensor","electrical_light_sensor","electrical_weather_sensor")
        for((f,face) in Direction.values().withIndex())for(rotation in 0..3) {
            val at=y.at(4+(rotation%2)*8,4+(rotation/2)*8,270+f*6)
            val n=y.device(at,ids[f],rotation,face);val p=n.parts[face.get3DDataValue()]!!;parts.add(p)
            for(edge in FaceBasis.edges(p))y.device(at.relative(edge),"signal_cable",face=face)
        }
        val legacy=y.device(y.at(8,8,266),"signal_trimmer")
        legacy.parts[0]=PartState.load(legacy.parts[0]!!.save().apply { putInt("componentDataVersion",7);remove("legacySimpleSignal") });legacy.changedTopology()
        for(edge in FaceBasis.edges(legacy.parts[0]!!))y.device(legacy.blockPos.relative(edge),"signal_cable")
        h.runAtTickTime(35) {
            for(p in parts)if(p.connectedMask!=(1 shl SimpleSignals.pin(p)))h.fail("Original single terminal failed: ${p.definition.id}/${p.face}/${p.rotation}: ${p.connectedMask}")
            val old=parts.first().save().apply { putInt("componentDataVersion",7);remove("legacySimpleSignal") }
            val restored=PartState.load(old)!!
            if(!restored.legacySimpleSignal || !PartState.load(restored.save())!!.legacySimpleSignal)h.fail("Preview four-terminal migration was lost")
            if(legacy.parts[0]!!.connectedMask!=15)h.fail("Actual saved preview control lost its four connected terminals")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=90)
    fun actualTrimmerLimitsLoadCurrentWhileCreativeSourceRetainsOriginalIdealVoltage(h: GameTestHelper) {
        val y=Yard(h)
        fun circuit(z: Int,id: String): Pair<FaceNodeEntity,PartState> {
            val n=y.device(y.at(3,z),id)
            val probe=y.device(y.at(4,z),"electrical_probe",1)
            PartInventory.get(probe,probe.parts[0]!!).setStackInSlot(0,ItemStack(Content.partItems.getValue("signal_cable").get()))
            val load=y.part(y.at(5,z),"creative_power_resistor",2).apply { value=1.0 };y.part(y.at(6,z),"ground_cable")
            return n to load
        }
        val (trimmer,limited)=circuit(4,"signal_trimmer");val (creative,ideal)=circuit(12,"signal_source")
        if(trimmer.parts[0]!!.value!=0.0 || !SimpleSignals.configureTrimmer(trimmer,trimmer.parts[0]!!,5.0))h.fail("Trimmer initial value/configuration changed")
        if(SimpleSignals.configureTrimmer(trimmer,trimmer.parts[0]!!,Double.NaN) || SimpleSignals.configureTrimmer(trimmer,trimmer.parts[0]!!,6.0))h.fail("Invalid trimmer setting accepted")
        h.runAtTickTime(35) {
            if(abs(limited.current) !in .07.. .101 || limited.voltage>.11)h.fail("Trimmer output lost original 0.1 A limit: ${limited.current}, ${limited.voltage}")
            val expected=5/(1+.05*3+ideal.resistance*2+PartDefinition.byId.getValue("ground_cable").resistance)
            if(abs(abs(ideal.current)-expected)>.003 || abs(creative.parts[0]!!.voltage-5)>.001)h.fail("Creative signal source is not original ideal voltage: ${ideal.current}, expected $expected")
            val saved=trimmer.saveWithoutMetadata();trimmer.load(saved);trimmer.changedTopology()
        }
        h.runAtTickTime(60) { if(trimmer.parts[0]!!.value!=5.0 || abs(limited.current) !in .07.. .101)h.fail("Trimmer setting or current limit failed actual reload");h.succeed() }
    }
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=65)
    fun originalButtonPulseAndSwitchToggleSurviveCircuitRebuildWithoutExtendingPulse(h: GameTestHelper) {
        val y=Yard(h);val button=y.device(y.at(4),"signal_button");val switch=y.device(y.at(12),"signal_switch")
        var p=button.parts[0]!!
        if(p.enabled || switch.parts[0]!!.enabled)h.fail("Ordinary controls must start off")
        h.runAtTickTime(10) { SimpleSignals.activate(button,p);SimpleSignals.activate(switch,switch.parts[0]!!) }
        h.runAtTickTime(12) {
            if(p.voltage<4.99 || !p.enabled || switch.parts[0]!!.voltage<4.99)h.fail("Actual control failed to drive its terminal")
            val saved=button.saveWithoutMetadata();button.load(saved);p=button.parts[0]!!;button.changedTopology()
        }
        h.runAtTickTime(17) {
            if(p.enabled || abs(p.voltage)>.001 || !switch.parts[0]!!.enabled)h.fail("Original 0.21-second pulse became a ten-tick pulse or restarted on load")
            SimpleSignals.activate(button,p);SimpleSignals.activate(button,p);SimpleSignals.activate(switch,switch.parts[0]!!)
        }
        h.runAtTickTime(22) { if(p.enabled || abs(p.voltage)>.001 || switch.parts[0]!!.enabled)h.fail("Second click failed to cancel control output");h.succeed() }
    }
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=95,batch="simple_signal_weather")
    fun actualWeatherFilterRetainsOriginalSamplingRateAndSavedHistory(h: GameTestHelper) {
        val y=Yard(h);val n=y.device(y.at(8),"electrical_weather_sensor");var p=n.parts[0]!!
        h.level.setWeatherParameters(0,6000,true,true);h.level.setRainLevel(1f);h.level.setThunderLevel(1f)
        var before=0f
        h.runAtTickTime(25) {
            val elapsed=p.gridState.getDouble("simpleSampleElapsed")
            before=p.gridState.getFloat("simpleWeatherFiltered")
            if(before !in .07f.. .13f || abs(p.voltage-before*5)>.001 || elapsed !in 0.0.. .201)h.fail("Weather filter bypassed original RC/sample rate: $before, $elapsed, ${p.voltage}")
            val saved=n.saveWithoutMetadata();n.load(saved);p=n.parts[0]!!;n.changedTopology()
            if(p.gridState.getFloat("simpleWeatherFiltered")!=before)h.fail("Weather filter lost saved history")
        }
        h.runAtTickTime(50) {
            val after=p.gridState.getFloat("simpleWeatherFiltered")
            if(after<=before+.05f || after>=.25f)h.fail("Reload restarted or accelerated weather filter: $before -> $after")
            h.level.setWeatherParameters(6000,0,false,false);h.level.setRainLevel(0f);h.level.setThunderLevel(0f)
        }
        h.runAtTickTime(75) { if(p.gridState.getFloat("simpleWeatherFiltered")>=.20f || p.voltage<=0)h.fail("Weather transition did not decay through original RC");h.succeed() }
    }
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=85)
    fun redstoneConvertersReadActualBlocksAndDriveActualNeighborPower(h: GameTestHelper) {
        val y=Yard(h);val input=y.device(y.at(4,4),"redstone_to_voltage_converter");val output=y.device(y.at(12,12),"voltage_to_redstone_converter")
        val source=y.device(y.at(12,13),"signal_source");source.parts[0]!!.value=2.75
        h.level.setBlockAndUpdate(input.blockPos.west(),Blocks.REDSTONE_BLOCK.defaultBlockState())
        h.runAtTickTime(25) {
            if(abs(input.parts[0]!!.voltage-5)>.001 || output.parts[0]!!.signal!=8 || h.level.getSignal(output.blockPos,Direction.EAST)!=8)h.fail("Real redstone conversion failed: ${input.parts[0]!!.voltage}, ${output.parts[0]!!.signal}")
            h.level.removeBlock(input.blockPos.west(),false);source.parts[0]!!.value=0.0
        }
        h.runAtTickTime(50) { if(abs(input.parts[0]!!.voltage)>.001 || output.parts[0]!!.signal!=0)h.fail("Redstone converter kept stale power after actual source removal");h.succeed() }
    }
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=220,batch="simple_signal_daylight")
    fun originalDaylightCurveFallsAtNightWhileOrdinaryLightSensorStillReadsRealBlockLight(h: GameTestHelper) {
        val y=Yard(h);val daylight=y.device(y.at(4),"electrical_daylight_sensor");val ordinary=y.device(y.at(12),"electrical_light_sensor")
        h.level.setDayTime(6000);h.level.setWeatherParameters(6000,0,false,false);h.level.setRainLevel(0f);h.level.setThunderLevel(0f)
        h.runAtTickTime(25) {
            if(daylight.parts[0]!!.voltage<4.99 || ordinary.parts[0]!!.voltage<4.99)h.fail("Actual midday skylight did not reach original full-scale signal")
            // Keep light from persistent neighboring test assemblies outside this real dark chamber.
            for(dx in -2..2)for(dz in -2..2)for(dy in -1..2)
                if(abs(dx)==2 || abs(dz)==2 || dy==-1 || dy==2)h.level.setBlockAndUpdate(ordinary.blockPos.offset(dx,dy,dz),Blocks.STONE.defaultBlockState())
            h.level.setDayTime(18000);h.level.setBlockAndUpdate(ordinary.blockPos.above(),Blocks.GLOWSTONE.defaultBlockState())
        }
        var brightAt=-1L;var removedAt=-1L;var darkAt=-1L;var done=false
        for(tick in 30L..200L)h.runAtTickTime(tick) {
            if(done)return@runAtTickTime
            if(tick%5==0L) {
                val engine=h.level.chunkSource.lightEngine
                engine.updateSectionStatus(net.minecraft.core.SectionPos.of(ordinary.blockPos),false)
                engine.enableLightSources(net.minecraft.world.level.ChunkPos(ordinary.blockPos),true)
                engine.checkBlock(ordinary.blockPos.above());engine.checkBlock(ordinary.blockPos);engine.tryScheduleUpdate()
            }
            val actual=h.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,ordinary.blockPos)
            if(removedAt<0) {
                if(actual>=14 && brightAt<0)brightAt=tick
                if(brightAt>=0 && tick>=brightAt+6 && actual>=14) {
                    if(abs(daylight.parts[0]!!.voltage)>.001 || abs(ordinary.parts[0]!!.voltage-actual*5.0/15)>.001)h.fail("Nighttime sensor ignored an actual ready light field: $actual, ${daylight.parts[0]!!.voltage}, ${ordinary.parts[0]!!.voltage}")
                    h.level.removeBlock(ordinary.blockPos.above(),false);removedAt=tick
                }else if(tick>=125)h.fail("Actual glowstone lighting never became ready: ${mods.eln.modern.lighting.LightRecovery.describe(h.level,ordinary.blockPos)}")
            }else {
                if(actual==0 && darkAt<0)darkAt=tick
                if(darkAt>=0 && tick>=darkAt+6 && actual==0) {
                    if(abs(ordinary.parts[0]!!.voltage)>.001)h.fail("Removed actual emitter left a stale sensor signal after real darkness")
                    done=true;h.level.setDayTime(6000);h.succeed()
                }else if(tick>=200)h.fail("Actual chamber did not become dark after emitter removal: $actual")
            }
        }
    }
}
