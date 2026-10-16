package mods.eln.modern

import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.config.ForgePortConfig
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Items
import kotlin.math.abs

/** Original 50 V / 50 W incubation, paid by the actual resistor and persisted between rebuilds. */
class IncubatorRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private val inventory=PartInventory.get(e.node,p)
    private val load=ElectricalLoad().apply { serialResistance=.0125;state=p.voltage }
    private val resistor=Resistor(load,null).setResistance(1e9)
    private val world=e.circuit.level
    init {
        e.circuit.root.addState(load);e.circuit.root.addComponent(resistor)
        for(i in 0..3)e.pins[i]=load
        if(!p.gridState.contains("incubatorRemainingJ"))reset()
    }
    private fun hasEgg()=inventory.getStackInSlot(0).`is`(Items.EGG)
    private fun reset() {
        val target=10000+world.random.nextDouble()*10000
        p.gridState.putDouble("incubatorTargetJ",target);p.gridState.putDouble("incubatorRemainingJ",target)
        p.progress=0.0
    }
    fun beforeTick() {
        if(!hasEgg())reset()
        resistor.resistance=if(hasEgg() && p.gridState.getDouble("incubatorRemainingJ")>0)50.0 else 1e9
    }
    fun afterStep(dt: Double) {
        p.voltage=load.voltage;p.voltageB=0.0;p.current=load.current
        val work=resistor.power.coerceAtLeast(0.0)*dt
        p.energy+=work;p.active=hasEgg() && resistor.power>1
        p.powerFactor=if(hasEgg())abs(p.voltage)/50 else 0.0
        if(hasEgg() && resistor.resistance==50.0) {
            p.gridState.putDouble("incubatorRemainingJ",p.gridState.getDouble("incubatorRemainingJ")-work)
            p.progress=p.gridState.getDouble("incubatorTargetJ")-p.gridState.getDouble("incubatorRemainingJ")
        }
    }
    fun afterTick() {
        if(hasEgg() && p.gridState.getDouble("incubatorRemainingJ")<=0) {
            val chicken=EntityType.CHICKEN.create(world)
            if(chicken!=null) {
                chicken.setAge(-24000)
                chicken.moveTo(e.node.blockPos.x+.5,e.node.blockPos.y+.5,e.node.blockPos.z+.5,world.random.nextFloat()*360-180,0f)
                chicken.yHeadRot=chicken.yRot;chicken.yBodyRot=chicken.yRot
                if(world.addFreshEntity(chicken)) {
                    inventory.extractItem(0,1,false);chicken.playAmbientSound();reset()
                    p.gridState.putInt("incubatorHatched",p.gridState.getInt("incubatorHatched")+1)
                    e.node.setChanged();e.node.sync()
                }
            }
        }
        var excess=abs(p.voltage)-65.0
        val outside=excess>0
        if(outside && !p.wasOvervoltage)excess=0.0
        p.wasOvervoltage=outside
        val remaining=minOf(12.5,(if(p.gridState.contains("incubatorVoltageTimeout"))p.gridState.getDouble("incubatorVoltageTimeout")else 12.5)-.05*excess*(.5+world.random.nextDouble()))
        p.gridState.putDouble("incubatorVoltageTimeout",remaining)
        if(remaining<0 && ForgePortConfig.cableVoltageProtection.get())MachineFailure.destroy(e.node,p,3f)
    }
}
