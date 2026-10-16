package mods.eln.modern

import mods.eln.config.ForgePortConfig
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.PowerSource
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.misc.MnaConst
import mods.eln.sim.nbt.NbtElectricalGateOutputProcess
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import kotlin.math.abs
import kotlin.math.pow

data class PowerAntennaSpec(val transmitter: Boolean,val cable: String,val range: Int=0,val efficiencyStart: Double=0.0,val efficiencyEnd: Double=0.0)
object PowerAntennas {
    fun initialRotation(face: net.minecraft.core.Direction)=when(face) {
        net.minecraft.core.Direction.UP,net.minecraft.core.Direction.DOWN->1
        net.minecraft.core.Direction.WEST,net.minecraft.core.Direction.SOUTH->2
        else->0
    }
    fun powerPin(p: PartState)=if(p.definition.powerAntenna!!.transmitter)1 else 2
    fun cable(p: PartState,index: Int)=PartDefinition.byId.getValue(if(index==powerPin(p))p.definition.powerAntenna!!.cable else "signal_cable")
}

/** Original commanded input resistor and finite received-power source, including the independent feedback signal. */
class PowerAntennaRuntime(val e: ElementRuntime) {
    val p=e.part;val spec=p.definition.powerAntenna!!
    val front get()=p.face.opposite
    private val power=ElectricalLoad().apply { serialResistance=PartDefinition.byId.getValue(spec.cable).resistance;state=p.voltage }
    private val signal=ElectricalLoad().apply { serialResistance=.05;state=p.pinVoltages[0] }
    private val command=if(spec.transmitter)ElectricalLoad().apply { serialResistance=.05;state=p.pinVoltages[2] }else null
    val input=if(spec.transmitter)Resistor(power,null).setResistance(MnaConst.highImpedance)else null
    private val source=if(!spec.transmitter)PowerSource("beam",power).apply {
        setMaximumVoltage(p.definition.nominalVoltage*1.3*2)
        setMaximumCurrent(p.definition.nominalVoltage*1.3*p.definition.power*1.3*2)
    }else null
    private val signalOut=if(spec.transmitter)NbtElectricalGateOutputProcess("feedback",signal).apply { coulombs=.1*.05/ForgePortConfig.simulationSteps.get()/5;setVoltageSafe(5.0) }else null
    var receiver: PowerAntennaRuntime?=null
    var efficiency=0.0
    var scanLeft=p.gridState.getDouble("beamScanLeft")
    var firstScan=true
    var lastInputWatts=0.0
        private set
    private var buffer=p.gridState.getDouble("beamBufferJ").takeIf { it.isFinite() }?.coerceIn(0.0,p.definition.power*.1) ?: 0.0
    init {
        for(load in listOfNotNull(power,signal,command)) { e.circuit.root.addState(load);e.circuit.root.addComponent(Resistor(load,null).pullDown()) }
        e.pins[PowerAntennas.powerPin(p)]=power;e.pins[0]=signal
        if(command!=null)e.pins[2]=command
        input?.let(e.circuit.root::addComponent);source?.let(e.circuit.root::addComponent);signalOut?.let(e.circuit.root::addComponent)
    }
    fun beforeStep(dt: Double) {
        input?.resistance=(command!!.voltage/5).coerceIn(0.0,1.0).let { if(it==0.0)MnaConst.highImpedance else p.definition.nominalVoltage.pow(2)/p.definition.power/it }
        signalOut?.setVoltageSafe(receiver?.signal?.voltage ?: 5.0)
        source?.power=buffer/dt
    }
    fun afterStep(dt: Double) {
        p.voltage=power.voltage;p.current=if(spec.transmitter)input!!.current else source!!.current
        for(i in 0..3)p.pinVoltages[i]=e.pins[i]?.voltage ?: 0.0
        if(spec.transmitter) {
            lastInputWatts=input!!.power.coerceAtLeast(0.0);p.energy+=lastInputWatts*dt
            p.active=command!!.voltage>0 && lastInputWatts>1e-6
        }else {
            val work=source!!.effectivePower.coerceAtLeast(0.0)*dt
            buffer=(buffer-work).coerceAtLeast(0.0);p.energy+=work;p.active=work>1e-9
            p.gridState.putDouble("beamOutputWatts",work/dt)
        }
        persist()
    }
    fun receive(joules: Double,dt: Double) {
        // Only actually consumed transmitter energy can enter the receiver's one-step field budget.
        val maximum=maxOf(joules.coerceAtLeast(0.0),p.definition.power*1.3*dt)
        val accepted=minOf(joules.coerceAtLeast(0.0),(maximum-buffer).coerceAtLeast(0.0))
        buffer+=accepted
        p.gridState.putDouble("beamDissipatedJ",p.gridState.getDouble("beamDissipatedJ")+joules-accepted)
        persist()
    }
    fun afterTick() {
        val watts=if(spec.transmitter)lastInputWatts else p.gridState.getDouble("beamOutputWatts")
        p.powerFactor=efficiency
        if(watts>p.definition.power*1.3 || p.voltage>p.definition.nominalVoltage*1.3)MachineFailure.destroy(e.node,p,2f)
    }
    fun persist() { p.gridState.putDouble("beamBufferJ",buffer);p.gridState.putDouble("beamScanLeft",scanLeft) }
}

/** Loaded, ticking participants only; original straight beam, two-second scan, attenuation and hazards. */
class PowerBeamNetwork(elements: List<ElementRuntime>) {
    private val antennas=elements.mapNotNull { it.powerAntenna }
    private val receivers=antennas.filter { !it.spec.transmitter }.associateBy { it.e.node.blockPos }
    fun beforeTick() {
        for(rx in receivers.values) { rx.efficiency=0.0;rx.p.gridState.putBoolean("beamConnected",false) }
        for(tx in antennas.filter { it.spec.transmitter }) {
            tx.scanLeft-=.05
            if(tx.firstScan || tx.scanLeft<=0.0) {
                tx.firstScan=false;if(tx.scanLeft<=0.0)tx.scanLeft+=2.0
                scan(tx)
            }
            if(tx.receiver?.let { receivers[it.e.node.blockPos]!==it }==true)tx.receiver=null
            tx.receiver?.let { it.efficiency=tx.efficiency;it.p.gridState.putBoolean("beamConnected",true) }
        }
    }
    private fun scan(tx: PowerAntennaRuntime) {
        val level=tx.e.circuit.level;val start=tx.e.node.blockPos
        tx.receiver=null;tx.efficiency=0.0
        var end=start
        for(distance in 1..tx.spec.range) {
            val pos=start.relative(tx.front,distance);end=pos
            if(!level.hasChunkAt(pos))break
            val state=level.getBlockState(pos)
            if(state.isAir || state.`is`(Blocks.FIRE))continue
            val rx=receivers[pos]
            if(rx!=null && rx.front==tx.front.opposite) {
                tx.receiver=rx
                tx.efficiency=tx.spec.efficiencyStart-(tx.spec.efficiencyStart-tx.spec.efficiencyEnd)*distance/tx.spec.range
                if(level.isRaining)tx.efficiency*=.707
                if(level.isThundering)tx.efficiency*=.707
                rx.efficiency=tx.efficiency
            }
            break
        }
        tx.p.gridState.putBoolean("beamConnected",tx.receiver!=null)
        if(tx.receiver==null && tx.lastInputWatts>50) {
            val flame=end.relative(tx.front.opposite)
            if(flame!=start && level.hasChunkAt(flame) && level.getBlockState(flame).isAir)level.setBlockAndUpdate(flame,Blocks.FIRE.defaultBlockState())
        }
        val box=AABB(start,end).expandTowards(1.0,1.0,1.0)
        val seconds=((tx.lastInputWatts/100).pow(2)+.5).toInt()
        if(seconds>0)for(entity in level.getEntities(null,box))entity.setSecondsOnFire(seconds)
        tx.persist()
    }
    fun afterStep(dt: Double) {
        for(tx in antennas.filter { it.spec.transmitter }) {
            val available=(tx.lastInputWatts*tx.efficiency-2).coerceAtLeast(0.0)*dt
            tx.receiver?.receive(available,dt)
            tx.p.gridState.putDouble("beamLostJ",tx.p.gridState.getDouble("beamLostJ")+tx.lastInputWatts*dt-if(tx.receiver==null)0.0 else available)
        }
    }
}
