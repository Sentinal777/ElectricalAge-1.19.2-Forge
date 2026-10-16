package mods.eln.modern

import mods.eln.config.ForgePortConfig
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel

data class WirelessReception(val transmitters: List<PartState>,val strengths: Map<String,Double>)

/** A receiver discovers loaded transmitters through a cycle-safe graph of original repeaters. */
object WirelessNetwork {
    fun discover(level: ServerLevel,elements: List<ElementRuntime>,receiver: BlockPos,channel: String?): WirelessReception {
        data class Radio(val at: BlockPos,val part: PartState,val repeater: Boolean=false)
        val transmitters=(elements.filter { it.part.definition.behavior==Behavior.WIRELESS_TX }.map { Radio(it.node.blockPos,it.part) }+
            (ModbusService.radioSources(level)+ComputerProbeRadios.sources(level)).map { Radio(it.first,it.second) }).filter { channel==null || it.part.channel==channel }
        val repeaters=elements.filter { it.part.definition.wireless?.kind=="REPEATER" }.map { Radio(it.node.blockPos,it.part,true) }
        val visited=hashSetOf<PartState>();val found=linkedSetOf<PartState>();val strengths=linkedMapOf<String,Double>()
        val range=ForgePortConfig.wirelessRange.get().toDouble()
        fun visit(at: BlockPos,root: Boolean,strength: Double) {
            val nearby=(transmitters+repeaters).filter { WirelessSettings.inRange(level,it.at,at,range) }
                .let { if(root)it.sortedBy { e -> e.at.distSqr(at) }else it }
            for(element in nearby) {
                val p=element.part
                val received=if(root)range-WirelessSettings.virtualDistance(level,element.at,at)else strength
                if(!element.repeater) {
                    if(found.add(p))strengths[p.gridId]=received
                }else if(visited.add(p))visit(element.at,false,received)
            }
        }
        visit(receiver,true,0.0)
        return WirelessReception(found.toList(),strengths)
    }
    fun normalized(p: PartState)=(if(p.definition.wireless?.kind=="SOURCE")if(p.enabled)1.0 else 0.0 else p.voltage/5)+p.gridState.getDouble("wirelessGlitchOffset")
}
