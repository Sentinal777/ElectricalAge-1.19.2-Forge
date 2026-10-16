package mods.eln.modern

data class WatchSpec(val digital: Boolean)

object WatchTime {
    fun ticks(date: Long)=(Math.floorMod(date,24000L)+6000L)%24000L
    fun minutes(date: Long)=(ticks(date)*3/50).toInt()
    fun date(part: PartState,worldDate: Long)=if(part.active)worldDate else if(part.thermalState.contains("watchDate"))part.thermalState.getLong("watchDate") else 1379L
}

/** Original independent battery clock: powered world time, stopped analog hands and blank digital digits. */
class WatchRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private var powered=p.thermalState.getBoolean("watchPowered")
    private var stoppedDate=if(p.thermalState.contains("watchDate"))p.thermalState.getLong("watchDate") else 1379L
    fun beforeTick() {
        val stack=PartInventory.get(e.node,p).getStackInSlot(0)
        val valid=(stack.item as? MaterialItem)?.definition?.kind=="PORTABLE_BATTERY"
        val energy=if(valid)ElectricalItems.energy(stack) else 0.0
        val old=powered;val cost=p.definition.power*.05
        if(!valid || energy<cost*4) {
            powered=false
            if(old) {
                stoppedDate=e.circuit.level.dayTime
                if(valid) { p.energy+=energy;ElectricalItems.setEnergy(stack,0.0) }
            }
        } else {
            powered=true;ElectricalItems.setEnergy(stack,energy-cost);p.energy+=cost
        }
        p.active=powered;p.voltage=0.0;p.current=0.0;p.powerFactor=if(powered)1.0 else 0.0
        p.thermalState.putBoolean("watchPowered",powered);p.thermalState.putLong("watchDate",stoppedDate)
        e.node.setChanged();if(old!=powered)e.node.sync()
    }
}
