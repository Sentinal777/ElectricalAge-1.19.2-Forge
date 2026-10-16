package mods.eln.sim

import kotlin.math.abs

/** The original Simulator.thermalStep equations, independent of Minecraft world access. */
object ThermalIntegrator {
    const val FAST_PERIOD = .0025
    fun minimumCapacity(rs: Double,rp: Double)=FAST_PERIOD*3*(1/rs+1/rp)

    fun step(dt: Double,loads: List<ThermalLoad>,connections: List<ThermalConnection>,
             processes: List<IProcess> = emptyList(),roomExchange: (ThermalLoad,Double)->Double? = { _,_ -> null }) {
        require(dt.isFinite() && dt>0)
        for(c in connections) {
            val watts=(c.L2.temperatureCelsius-c.L1.temperatureCelsius)/(c.L1.Rs+c.L2.Rs)
            c.L1.PcTemp+=watts;c.L2.PcTemp-=watts
            c.L1.PrsTemp+=abs(watts);c.L2.PrsTemp+=abs(watts)
        }
        processes.forEach { it.process(dt) }
        for(load in loads) {
            load.PcTemp-=roomExchange(load,dt) ?: (load.temperatureCelsius/load.Rp)
            load.temperatureCelsius+=load.PcTemp*dt/load.heatCapacity
            require(load.temperatureCelsius.isFinite()) { "Invalid thermal load" }
            load.Pc=load.PcTemp;load.Prs=load.PrsTemp;load.Psp=load.PspTemp
            load.PcTemp=0.0;load.PrsTemp=0.0;load.PspTemp=0.0
        }
    }
}
