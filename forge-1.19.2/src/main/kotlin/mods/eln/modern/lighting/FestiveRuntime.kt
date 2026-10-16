package mods.eln.modern.lighting

import mods.eln.modern.*
import kotlin.math.abs

/** The original festive load is wireless, fixed to xmas, and has no electrical terminals. */
class FestiveRuntime(val e: ElementRuntime) {
    private val p=e.part
    var supply: LampSupplyRuntime?=null
        private set
    fun prepare(supplies: List<LampSupplyRuntime>) {
        val saved=p.gridState.getString("festiveSupply")
        val savedIndex=p.gridState.getInt("festiveSupplyChannel")
        val retained=supplies.firstOrNull { it.e.part.gridId==saved && it.profiles.getOrNull(savedIndex)?.power=="xmas" }
        // Source caches the chosen handle until it disappears. It deliberately has no range cutoff.
        val closest=retained ?: supplies.filter { s -> s.profiles.any { it.power=="xmas" } }.minByOrNull { it.e.node.blockPos.distSqr(e.node.blockPos) }
        val index=if(closest===retained)savedIndex else closest?.profiles?.indexOfFirst { it.power=="xmas" } ?: -1
        if(closest!=null && index>=0) {
            p.gridState.putString("festiveSupply",closest.e.part.gridId)
            p.gridState.putInt("festiveSupplyChannel",index)
        } else p.gridState.remove("festiveSupply")
        supply=closest?.takeIf { index>=0 && it.states[index] }
        supply?.let { it.conductance+=1/1000.0 }
    }
    fun afterStep(dt: Double) {
        p.voltage=supply?.load?.voltage ?: 0.0;p.voltageB=0.0
        p.current=p.voltage/1000.0
        p.energy+=p.voltage*p.current*dt
        // Keep the source's factor of sixteen and its separate render threshold.
        p.signal=(12*(abs(p.voltage)-180)/20*16).toInt().coerceIn(0,15)
        p.active=p.signal>4
    }
}
