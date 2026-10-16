package mods.eln.modern

import mods.eln.Eln
import mods.eln.config.ForgePortConfig
import mods.eln.sim.ElectricalConnection
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.RootSystem
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.level.LevelEvent
import net.minecraftforge.event.server.ServerStoppedEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.server.ServerLifecycleHooks
import java.util.IdentityHashMap

/** Integer edge coordinates unify flat, inside-corner and outside-corner connections. */
data class EdgeKey(val x: Int, val y: Int, val z: Int) {
    companion object {
        fun of(pos: BlockPos, face: Direction, edge: Direction) = EdgeKey(
            2*pos.x+1+face.stepX+edge.stepX,
            2*pos.y+1+face.stepY+edge.stepY,
            2*pos.z+1+face.stepZ+edge.stepZ)
    }
}

object CircuitManager {
    private val levels = IdentityHashMap<ServerLevel, LevelCircuit>()
    fun attach(node: FaceNodeEntity) {
        val level = node.level as? ServerLevel ?: return
        if(node.isRemoved)return
        val chunk=level.chunkSource.getChunkNow(node.blockPos.x shr 4,node.blockPos.z shr 4) ?: return
        if(chunk.getBlockEntity(node.blockPos)!==node)return
        val circuit = levels.getOrPut(level) { LevelCircuit(level) }
        if (circuit.nodes.put(node.blockPos.immutable(),node) !== node) circuit.dirty = true
    }
    fun detach(node: FaceNodeEntity) {
        val circuit = levels[node.level] ?: return
        if (circuit.nodes[node.blockPos] === node) { circuit.nodes.remove(node.blockPos); circuit.dirty = true }
    }
    fun loadedNodes(level: ServerLevel)=levels[level]?.nodes?.values?.filter { !it.isRemoved && level.chunkSource.isPositionTicking(net.minecraft.world.level.ChunkPos.asLong(it.blockPos)) }.orEmpty()
    fun wirelessAt(level: ServerLevel,at: BlockPos)=levels[level]?.wirelessAt(at) ?: WirelessReception(emptyList(),emptyMap())
    fun invalidate(node: FaceNodeEntity) { levels[node.level]?.dirty = true }
    @SubscribeEvent fun tick(event: TickEvent.ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        WorldWind.tick(server.overworld())
        WirelessLightning.advance()
        PortableTools.tick(server)
        ElectricalItems.tickPlayers(server)
        for (level in server.allLevels) {
            mods.eln.modern.grid.GridMultiblock.maintain(level);levels[level]?.tick()
            mods.eln.modern.lighting.LightSpotLeases.tick(level)
            // Flush partial vanilla lighting batches even when a busy server has no idle polling window.
            level.chunkSource.lightEngine.tryScheduleUpdate()
        }
        MqttService.tick(server);ModbusService.tick(server);ComputerProbeRadios.tick()
    }
    @SubscribeEvent fun unload(event: LevelEvent.Unload) { if (event.level is ServerLevel) { levels.remove(event.level);ReplicatorFeeding.clear(event.level as ServerLevel);WirelessLightning.unload(event.level as ServerLevel);mods.eln.modern.grid.GridMultiblock.unload(event.level as ServerLevel);mods.eln.modern.lighting.LightSpotLeases.unload(event.level as ServerLevel) } }
    @SubscribeEvent fun stop(event: ServerStoppedEvent) { NodeCovers.clearModifiers();ComputerProbes.clear();TutorialSigns.clear();ComputerProbeRadios.clear();ModbusService.clear();MqttService.clear(); levels.clear();ReplicatorFeeding.clear();WirelessLightning.clear();PortableTools.clear();mods.eln.modern.grid.GridNetwork.clearPending();mods.eln.modern.grid.GridMultiblock.clear();mods.eln.modern.lighting.LightSpotLeases.clear() }
    @SubscribeEvent fun entityJoined(event: net.minecraftforge.event.entity.EntityJoinLevelEvent) { WirelessLightning.record(event) }
    @SubscribeEvent fun blockBroken(event: net.minecraftforge.event.level.BlockEvent.BreakEvent) {
        (event.level as? ServerLevel)?.let { mods.eln.modern.thermal.RoomThermal.data(it).invalidate(event.pos) }
    }
    @SubscribeEvent fun blockPlaced(event: net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent) {
        (event.level as? ServerLevel)?.let { mods.eln.modern.thermal.RoomThermal.data(it).invalidate(event.pos) }
    }
    @SubscribeEvent fun explosion(event: net.minecraftforge.event.level.ExplosionEvent.Detonate) {
        (event.level as? ServerLevel)?.let { level -> for(pos in event.affectedBlocks)mods.eln.modern.thermal.RoomThermal.data(level).invalidate(pos) }
    }
}

class LevelCircuit(val level: ServerLevel) {
    val nodes = linkedMapOf<BlockPos, FaceNodeEntity>()
    var dirty = true
    var root = RootSystem(0.005, 1)
        private set
    private var elements = listOf<ElementRuntime>()
    private var fastThermalLoads=listOf<mods.eln.sim.ThermalLoad>()
    private var slowThermalLoads=listOf<mods.eln.sim.ThermalLoad>()
    private var fastThermalLinks=listOf<mods.eln.sim.ThermalConnection>()
    private var slowThermalLinks=listOf<mods.eln.sim.ThermalConnection>()
    private var fastThermalProcesses=listOf<mods.eln.sim.IProcess>()
    private var slowThermalProcesses=listOf<mods.eln.sim.IProcess>()
    private var thermalPositions=java.util.IdentityHashMap<mods.eln.sim.ThermalLoad,BlockPos>()
    private var faulted = false
    private var tickCount = 0
    private var stepCount=10
    private var controllerIterations=50
    private var participants=emptySet<BlockPos>()
    private var remoteShaftParticipants=emptySet<Long>()
    var mechanics=MechanicalNetwork(emptyList())
        private set
    private var powerBeams=PowerBeamNetwork(emptyList())
    private var lighting=mods.eln.modern.lighting.LightingNetwork(emptyList())

    private fun rebuild() {
        elements.forEach { it.persist() }
        root = RootSystem(.05/stepCount, controllerIterations)
        elements = nodes.values.filter { !it.isRemoved && it.blockPos in participants }
            .flatMap { node -> node.parts.filterNotNull().map { ElementRuntime(this,node,it) } }
        data class Endpoint(val element: ElementRuntime, val load: ElectricalLoad, val index: Int, val kind: Int=element.connectionKind(index),val ghost: Boolean=false,val channel: Int?=null,val neighbor: BlockPos?=null)
        val edges = linkedMapOf<EdgeKey, MutableList<Endpoint>>()
        data class ThermalEndpoint(val element: ElementRuntime,val load: mods.eln.sim.ThermalLoad,val index: Int,val kind: Int)
        val thermalEdges = linkedMapOf<EdgeKey, MutableList<ThermalEndpoint>>()
        elements.forEach { element ->
            element.part.connectedMask = 0
            for(i in 0..3)element.part.gridState.putInt("connectionColor$i",0)
            element.build()
            FaceBasis.edges(element.part).forEachIndexed { i, direction ->
                val key = EdgeKey.of(element.node.blockPos,element.part.face,direction)
                element.portLoads(i).forEach { port -> edges.getOrPut(key) { mutableListOf() }.add(Endpoint(element,port.load,i,channel=port.channel)) }
                element.thermal?.port(i)?.let { (load,kind) -> thermalEdges.getOrPut(key) { mutableListOf() }.add(ThermalEndpoint(element,load,i,kind)) }
            }
            (element.autoMiner?.load ?: element.transporter?.load)?.let { load ->
                for(port in RemotePowerPorts.active(level,element.node,element.part)) {
                    val index=FaceBasis.edges(element.part).indexOf(port.direction)
                    edges.getOrPut(EdgeKey.of(port.position,Direction.DOWN,port.direction)) { mutableListOf() }.add(Endpoint(element,load,index,0,true))
                }
            }
            element.computerProbe?.loads?.let { loads ->
                for(side in Direction.values())FaceBasis.edges(side,0).forEachIndexed { i,mount ->
                    edges.getOrPut(EdgeKey.of(element.node.blockPos,side,mount)) { mutableListOf() }.add(Endpoint(element,loads[side.get3DDataValue()],i,1,true,neighbor=element.node.blockPos.relative(side)))
                }
            }
            element.grid?.ghostLoads()?.forEach { (pos,load) ->
                for(face in net.minecraft.core.Direction.values())FaceBasis.edges(face,0).forEachIndexed { i,direction ->
                    edges.getOrPut(EdgeKey.of(pos,face,direction)) { mutableListOf() }.add(Endpoint(element,load,i,if(load===element.grid!!.control)1 else 0,true))
                }
            }
            element.solar?.ghostLoads()?.forEach { (pos,load) ->
                for(face in Direction.values())FaceBasis.edges(face,0).forEachIndexed { i,direction ->
                    edges.getOrPut(EdgeKey.of(pos,face,direction)) { mutableListOf() }.add(Endpoint(element,load,i,0,true))
                }
            }
        }
        val joined = mutableSetOf<Set<ElectricalLoad>>()
        edges.values.forEach { endpoints ->
            for (i in endpoints.indices) for (j in 0 until i) {
                val a = endpoints[i]; val b = endpoints[j]
                if(a.neighbor!=null && a.neighbor!=b.element.node.blockPos || b.neighbor!=null && b.neighbor!=a.element.node.blockPos)continue
                if (a.element === b.element || a.load === b.load || a.kind!=b.kind && a.kind!=2 && b.kind!=2) continue
                if(a.channel!=null && b.channel!=null && a.channel!=b.channel)continue
                if(a.channel!=null && b.channel==null && a.channel!=PaintedWiring.color(b.element.part))continue
                if(b.channel!=null && a.channel==null && b.channel!=PaintedWiring.color(a.element.part))continue
                if(!PaintedWiring.compatible(a.element.part,b.element.part))continue
                if(!a.ghost)a.element.part.gridState.putInt("connectionColor${a.index}",PaintedWiring.color(b.element.part))
                if(!b.ghost)b.element.part.gridState.putInt("connectionColor${b.index}",PaintedWiring.color(a.element.part))
                if(!a.ghost)a.element.part.connectedMask = a.element.part.connectedMask or (1 shl a.index)
                if(!b.ghost)b.element.part.connectedMask = b.element.part.connectedMask or (1 shl b.index)
                val pair = setOf(a.load,b.load)
                if (joined.add(pair)) root.addComponent(ElectricalConnection(a.load,b.load))
            }
        }
        val gridElements=elements.mapNotNull { it.grid }.associateBy { it.node.blockPos }
        for(wire in mods.eln.modern.grid.GridNetwork.data(level).wires.values) {
            val a=gridElements[wire.a.pos] ?: continue;val b=gridElements[wire.b.pos] ?: continue
            if(a.part.gridId!=wire.a.nodeId || b.part.gridId!=wire.b.nodeId)continue
            val al=a.gridLoad(wire.a.side) ?: continue;val bl=b.gridLoad(wire.b.side) ?: continue
            val cable=PartDefinition.byId[wire.cable] ?: continue
            val connection=mods.eln.sim.mna.component.InterSystem()
            connection.connectTo(al,bl);connection.resistance=cable.resistance*wire.length
            root.addComponent(connection)
        }
        mods.eln.modern.grid.GridNetwork.updateRenderData(level,nodes.values.filter { !it.isRemoved })
        val thermalJoined=mutableSetOf<Set<mods.eln.sim.ThermalLoad>>()
        val thermalLinks = thermalEdges.values.flatMap { list ->
            list.indices.flatMap { i -> (0 until i).map { j ->
                val a=list[i];val b=list[j]
                if(!PaintedWiring.compatible(a.element.part,b.element.part) || a.load===b.load || a.kind!=b.kind || a.load.isSlow!=b.load.isSlow || !thermalJoined.add(setOf(a.load,b.load)))null else {
                    a.element.part.connectedMask=a.element.part.connectedMask or (1 shl a.index)
                    b.element.part.connectedMask=b.element.part.connectedMask or (1 shl b.index)
                    mods.eln.sim.ThermalConnection(a.load,b.load)
                }
            } }
        }.filterNotNull()
        val thermal=elements.mapNotNull { it.thermal }.filter { it.hasLoad }
        thermalPositions=java.util.IdentityHashMap<mods.eln.sim.ThermalLoad,BlockPos>().apply { for(runtime in thermal)for(load in runtime.loads)put(load,runtime.e.node.blockPos) }
        fastThermalLoads=thermal.flatMap { it.loads }.filter { !it.isSlow };slowThermalLoads=thermal.flatMap { it.loads }.filter { it.isSlow }
        fastThermalLinks=thermalLinks.filter { !it.L1.isSlow };slowThermalLinks=thermalLinks.filter { it.L1.isSlow }
        fastThermalProcesses=thermal.filter { !it.load.isSlow }.map { it.process };slowThermalProcesses=thermal.filter { it.load.isSlow }.map { it.process }
        mechanics=MechanicalNetwork(elements)
        lighting=mods.eln.modern.lighting.LightingNetwork(elements)
        powerBeams=PowerBeamNetwork(elements)
        dirty = false; faulted = false
    }
    fun wirelessAt(at: BlockPos)=WirelessNetwork.discover(level,elements,at,null)
    fun transporters()=elements.mapNotNull { it.transporter }
    fun wirelessReception(receiver: FaceNodeEntity,channel: String?)=WirelessNetwork.discover(level,elements,receiver.blockPos,channel)
    fun wireless(part: PartState,receiver: FaceNodeEntity)=wirelessValues(part.channel,receiver).maxOrNull() ?: 0.0
    fun wirelessValues(channel: String,receiver: FaceNodeEntity)=wirelessReception(receiver,channel).transmitters.map { (WirelessNetwork.normalized(it)*5).coerceIn(0.0,5.0) }

    fun tick() {
        val active=nodes.values.filter { !it.isRemoved && level.chunkSource.isPositionTicking(net.minecraft.world.level.ChunkPos.asLong(it.blockPos)) }.mapTo(hashSetOf()) { it.blockPos }
        val remote=nodes.values.filter { it.blockPos in active }.flatMap { node ->
            node.parts.filterNotNull().flatMap { p -> (MechanicalNetwork.remotePorts(level,node,p)+RemotePowerPorts.active(level,node,p)).map { it.position.asLong() } }
        }.toSet()
        if(active!=participants || remote!=remoteShaftParticipants) { participants=active;remoteShaftParticipants=remote;dirty=true }
        val configuredSteps=ForgePortConfig.simulationSteps.get()
        if(configuredSteps!=stepCount) { stepCount=configuredSteps;dirty=true }
        val configuredIterations=ForgePortConfig.interSystemIterations.get()
        if(configuredIterations!=controllerIterations) { controllerIterations=configuredIterations;dirty=true }
        if (dirty) rebuild()
        if (faulted || elements.isEmpty()) return
        try {
            val rooms=mods.eln.modern.thermal.RoomThermal.tick(level,thermalPositions.values)
            val exchange: (mods.eln.sim.ThermalLoad,Double)->Double? = { load,time -> thermalPositions[load]?.let { rooms.exchange(it,load.temperatureCelsius,load.Rp,time) } }
            mechanics.beforeTick();lighting.prepare();powerBeams.beforeTick();elements.forEach { it.beforeTick() }
            val dt=.05/stepCount
            repeat(stepCount) {
                elements.forEach { it.beforeStep(dt) }
                root.step()
                elements.forEach { it.afterStep(dt) }
                powerBeams.afterStep(dt)
            }
            elements.forEach { it.thermal?.prepareThermalStep() }
            // Preserve upstream's ordering: fast thermal steps, followed by the slow thermal list.
            repeat(20) { rooms.advance(.0025);mods.eln.sim.ThermalIntegrator.step(.0025,fastThermalLoads,fastThermalLinks,fastThermalProcesses,exchange);elements.forEach { it.fastStep(.0025) } }
            rooms.advance(.05);mods.eln.sim.ThermalIntegrator.step(.05,slowThermalLoads,slowThermalLinks,slowThermalProcesses,exchange)
            elements.forEach { it.afterTick() }
            mechanics.tick()
            tickCount++
            nodes.values.filter { it.blockPos in participants }.forEach {
                it.setChanged()
                if ((tickCount+it.blockPos.hashCode())%ForgePortConfig.syncInterval.get() == 0) it.sync()
            }
        } catch (failure: Exception) {
            faulted = true
            Eln.LOGGER.error("Circuit simulation paused in {} after an error; changing a component rebuilds it",level.dimension().location(),failure)
        }
    }
}
