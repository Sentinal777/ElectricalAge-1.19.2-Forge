package mods.eln.modern.client

import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

/** Device loops use synchronized physical load and stop when their chunk or component disappears. */
@Mod.EventBusSubscriber(modid="eln",value=[Dist.CLIENT])
object ClientAudio {
    private val nodes=java.util.IdentityHashMap<FaceNodeEntity,MutableMap<Int,DeviceLoop>>()
    private val states=java.util.WeakHashMap<FaceNodeEntity,MutableMap<Int,Boolean>>()
    private val alarmPulses=java.util.WeakHashMap<FaceNodeEntity,MutableMap<Int,Int>>()
    private val transporterPulses=java.util.WeakHashMap<FaceNodeEntity,MutableMap<Int,Int>>()
    private fun sound(p: PartState): String?=when(p.definition.behavior) {
        Behavior.PROCESSOR -> "beep_loop"
        Behavior.MACERATOR -> "macerator"
        Behavior.PLATE_MACHINE,Behavior.ROLLING_SHAFT -> "plate_machine"
        Behavior.COMPRESSOR -> "compressor_run"
        Behavior.AUTO_MINER -> "autominer"
        Behavior.MAGNETIZER -> "motor"
        Behavior.ARC_FURNACE -> "arcfurnace_loop"
        Behavior.TRANSFORMER -> "transformer"
        Behavior.FUEL_GENERATOR -> "fuelgenerator"
        Behavior.FUEL_HEAT_FURNACE -> "fuelheatfurnace"
        Behavior.HEAT_TURBINE -> if(p.definition.nominalVoltage==50.0)"heat_turbine_50v" else "heat_turbine_200v"
        Behavior.RADIAL_MOTOR -> "radialengine"
        Behavior.CLUTCH -> "clutch"
        Behavior.SHAFT_MOTOR -> "shaft_motor"
        Behavior.SHAFT_GENERATOR -> "generator"
        Behavior.GAS_TURBINE -> "gas_turbine"
        Behavior.STEAM_TURBINE -> "steam_turbine"
        Behavior.ENVIRONMENT_SENSOR -> if(p.definition.environment?.kind=="FIRE_BUZZER")"firealarm" else null
        else -> null
    }
    private fun volume(p: PartState): Float {
        if(p.definition.behavior==Behavior.PROCESSOR)return if(p.gridState.getBoolean("processorBeep"))p.gridState.getFloat("processorVolume").coerceIn(0f,1f)else 0f
        if(p.definition.alarm!=null)return if(p.active && p.enabled)1f else 0f
        if(p.definition.environment?.kind=="FIRE_BUZZER")return if(p.active)1f else 0f
        if(p.definition.behavior==Behavior.FUEL_GENERATOR)return if(p.enabled && !p.tripped).2f else 0f
        if(p.definition.behavior==Behavior.SHAFT_GENERATOR && p.enabled && !p.tripped && (p.active || p.shaftSpeed>.001))return (.05+kotlin.math.abs(p.thermalState.getDouble("shaftElectricalWatts"))/p.definition.power/4).coerceAtMost(1.0).toFloat()
        if(p.definition.behavior==Behavior.ROLLING_SHAFT && p.enabled && !p.tripped && p.shaftSpeed>.001)return minOf(.5,p.shaftSpeed).toFloat()
        if(!p.active)return 0f
        if(p.definition.behavior==Behavior.AUTO_MINER)return 2.13f
        return when(p.definition.behavior) {
            Behavior.SHAFT_MOTOR -> (kotlin.math.abs(p.thermalState.getDouble("shaftElectricalWatts"))/32000).coerceAtMost(1.0).toFloat()/4
            Behavior.CLUTCH,Behavior.GAS_TURBINE,Behavior.STEAM_TURBINE,Behavior.RADIAL_MOTOR -> p.powerFactor.coerceIn(0.0,1.0).toFloat()
            Behavior.TRANSFORMER -> (.1*((p.powerFactor-.5)/.5).coerceIn(0.0,1.0)).toFloat()
            else -> if(p.powerFactor>=.3)(.3*p.powerFactor).coerceIn(0.0,.8).toFloat()else 0f
        }
    }
    private fun pitch(p: PartState): Float=when(p.definition.behavior) {
        Behavior.PROCESSOR -> p.gridState.getFloat("processorPitch").coerceIn(.5f,4f)
        Behavior.FUEL_GENERATOR -> (.75+p.voltage/(p.definition.nominalVoltage*1.25)).coerceIn(.75,1.75).toFloat()
        Behavior.SHAFT_MOTOR -> maxOf(.05,p.shaftSpeed/100).toFloat()
        Behavior.SHAFT_GENERATOR -> (maxOf(.05,p.shaftSpeed/250)*2).toFloat()
        Behavior.ROLLING_SHAFT -> maxOf(.2,p.shaftSpeed/125).toFloat()
        Behavior.GAS_TURBINE,Behavior.STEAM_TURBINE,Behavior.RADIAL_MOTOR -> maxOf(.05,p.shaftSpeed/250).toFloat()
        else -> 1f
    }
    fun observe(node: FaceNodeEntity) {
        val mc=Minecraft.getInstance();val world=mc.level ?: return
        if(node.level!==world || node.isRemoved)return
        val previous=states.getOrPut(node) { hashMapOf() }
        for(p in node.parts.filterNotNull()) {
            if(p.definition.contact!=null)ContactVisuals.observe(node,p)
            if(p.definition.alarm!=null)ControlVisuals.observe(node,p)
            if(p.definition.behavior==Behavior.ENVIRONMENT_SENSOR)EnvironmentVisuals.observe(node,p)
            if(p.definition.behavior in setOf(Behavior.WIND_TURBINE,Behavior.WATER_TURBINE))RenewableVisuals.observe(node,p)
            val face=p.face.get3DDataValue();val old=previous.put(face,p.active)
            if(p.definition.behavior==Behavior.TELEPORTER) {
                val sequence=p.gridState.getInt("transporterSoundPulse")
                val prior=transporterPulses.getOrPut(node) { hashMapOf() }.put(face,sequence) ?: 0
                if(sequence!=prior && Transporters.phase(p)==Transporters.CHARGE)world.playLocalSound(node.blockPos.x+.5,node.blockPos.y+.5,node.blockPos.z+.5,OriginalSounds.event("transporter"),SoundSource.BLOCKS,.5f,1f,false)
            }
            if(old!=null && old!=p.active && p.definition.behavior in setOf(Behavior.SWITCH,Behavior.RELAY,Behavior.BREAKER)) {
                val id=if(p.definition.behavior==Behavior.BREAKER)if(p.tripped)"circuit_breaker_trip" else if(p.active)"circuit_breaker_close" else "circuit_breaker_open" else "click"
                val contact=p.definition.contact
                world.playLocalSound(node.blockPos.x+.5,node.blockPos.y+.5,node.blockPos.z+.5,OriginalSounds.event(id),SoundSource.BLOCKS,
                    if(contact==null).4f else if(contact.relay).1f else .3f,if(contact==null)1f else if(contact.relay)2f else .6f,false)
            }
            if(old==true && !p.active && p.definition.behavior==Behavior.CLUTCH && !p.gridState.getBoolean("clutchSlipping") && !PartInventory.get(node,p).getStackInSlot(1).isEmpty)world.playLocalSound(node.blockPos.x+.5,node.blockPos.y+.5,node.blockPos.z+.5,OriginalSounds.event("click"),SoundSource.BLOCKS,.4f,1f,false)
            if(p.definition.alarm!=null) {
                val sequence=p.thermalState.getInt("alarmPulse")
                val last=alarmPulses.getOrPut(node) { hashMapOf() }.put(face,sequence) ?: 0
                if(!p.active || !p.enabled)nodes[node]?.remove(face)?.finish()
                else if(sequence!=last) {
                    val loops=nodes.getOrPut(node) { hashMapOf() };loops.remove(face)?.finish()
                    val clip=DeviceLoop(node,face,p.definition.id,p.definition.alarm.sound);loops[face]=clip;mc.soundManager.play(clip)
                }
                continue
            }
            val id=sound(p) ?: continue
            if(volume(p)<=0)continue
            val loops=nodes.getOrPut(node) { hashMapOf() }
            if(loops[face]?.isStopped!=false) {
                val loop=DeviceLoop(node,face,p.definition.id,id);loops[face]=loop;mc.soundManager.play(loop)
            }
        }
    }
    @JvmStatic @SubscribeEvent fun tick(event: TickEvent.ClientTickEvent) {
        if(event.phase!=TickEvent.Phase.END)return
        val world=Minecraft.getInstance().level
        for((node,loops) in nodes.toMap()) {
            if(node.isRemoved || node.level!==world) { loops.values.forEach { it.finish() };nodes.remove(node);states.remove(node);alarmPulses.remove(node) }
            else { loops.entries.removeIf { it.value.isStopped };if(loops.isEmpty())nodes.remove(node) }
        }
        if(world==null) { states.clear();alarmPulses.clear() }
    }
    fun activeAt(node: FaceNodeEntity)=nodes[node]?.values?.count { Minecraft.getInstance().soundManager.isActive(it) } ?: 0
    private class DeviceLoop(private val node: FaceNodeEntity,private val face: Int,private val component: String,id: String):
        AbstractTickableSoundInstance(OriginalSounds.event(id),SoundSource.BLOCKS,RandomSource.create()) {
        init { looping=node.parts.getOrNull(face)?.definition?.alarm==null;delay=0;x=node.blockPos.x+.5;y=node.blockPos.y+.5;z=node.blockPos.z+.5;tick() }
        fun finish()=stop()
        override fun tick() {
            val p=node.parts.getOrNull(face)
            if(node.isRemoved || node.level!==Minecraft.getInstance().level || p?.definition?.id!=component) { stop();return }
            val target=ClientAudio.volume(p)
            if(p.definition.alarm!=null) { volume=target;pitch=1f;if(target<=0)stop();return }
            volume+=(target-volume).coerceIn(-.025f,.025f)
            pitch=ClientAudio.pitch(p)
            if(target<=0 && volume<=0)stop()
        }
    }
}
