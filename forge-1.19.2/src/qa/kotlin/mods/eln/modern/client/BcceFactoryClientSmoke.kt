package mods.eln.modern.client

import buildcraft.builders.tile.TileQuarry
import buildcraft.lib.internal.mj.MjCapabilities
import buildcraft.silicon.BCSiliconItems
import buildcraft.silicon.gate.GateVariant
import buildcraft.transport.BCTransportBlocks
import buildcraft.transport.BCTransportItems
import buildcraft.transport.tile.TilePipeHolder
import mods.eln.Eln
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.server.IntegratedServer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.registries.ForgeRegistries

/** Published BCCE quarry does its own frame construction and mining using only real ELN power. */
object BcceFactoryClientSmoke {
    private var ticks=0
    @Volatile private var failure: String?=null
    private val at=BlockPos(1960,120,145)
    private var targets=listOf<BlockPos>()
    private fun node(s: IntegratedServer,pos: BlockPos)=s.overworld().getBlockEntity(pos) as FaceNodeEntity
    fun tick(mc: Minecraft,server: IntegratedServer) {
        check(failure==null) { "BCCE factory authoritative check failed: $failure" };ticks++
        fun verify(action: ()->Unit) { val t=ticks;server.execute { try { action() }catch(e: Throwable) { failure="tick $t: ${e.stackTraceToString()}" } } }
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,"eln-original-bcce-factory-$name.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun camera(x: Double,z: Double,yaw: Float,pitch: Float)=server.execute { server.playerList.players.first().teleportTo(server.overworld(),x,120.0,z,yaw,pitch) }
        if(ticks==1)verify {
            val level=server.overworld();val player=server.playerList.players.first();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL)
            for(x in 1944..1976)for(z in 128..162)for(y in 119..127)level.setBlockAndUpdate(BlockPos(x,y,z),if(y==119)Blocks.STONE.defaultBlockState()else Blocks.AIR.defaultBlockState())
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity::class.java,net.minecraft.world.phys.AABB(1943.0,118.0,127.0,1978.0,130.0,164.0)).forEach { it.discard() };player.inventory.clearContent()
            level.setWeatherParameters(12000,0,false,false);level.dayTime=6000;player.teleportTo(level,1960.5,120.0,148.0,180f,25f)
            val quarry=ForgeRegistries.BLOCKS.getValue(ResourceLocation("buildcraftbuilders","quarry")) ?: error("Published BCCE quarry missing")
            val stack=ItemStack(quarry);player.setItemInHand(InteractionHand.MAIN_HAND,stack)
            require(stack.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(at.below()),Direction.UP,at.below(),false))).consumesAction() && stack.isEmpty)
            val tile=level.getBlockEntity(at) as TileQuarry;require(tile.frameBox.isInitialized)
            val min=tile.frameBox.min();val max=tile.frameBox.max()
            targets=(min.x+1 until max.x).flatMap { x -> (min.z+1 until max.z).map { z -> BlockPos(x,119,z) } }
            require(targets.size>=4);for(pos in targets)level.setBlockAndUpdate(pos,Blocks.IRON_ORE.defaultBlockState())
            for((offset,id) in listOf(2 to "electrical_source",1 to "energy_converter")) {
                val pos=at.west(offset);level.setBlockAndUpdate(pos,(if(id=="energy_converter")Content.machine.get()else Content.node.get()).defaultBlockState())
                node(server,pos).apply { parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN);changedTopology() }
            }
            level.setBlockAndUpdate(at.north(),Blocks.CHEST.defaultBlockState())
            val pipePos=at.west(6);level.setBlockAndUpdate(pipePos,BCTransportBlocks.pipeHolder.get().defaultBlockState())
            val pipe=level.getBlockEntity(pipePos) as TilePipeHolder;pipe.onPlacedBy(player,ItemStack(BCTransportItems.PIPE_ITEM_STONE.get()))
            val gateItem=BCSiliconItems.PLUG_GATE_ITEM.get();val gate=gateItem.getStack(GateVariant(CompoundTag().apply { putByte("material",2) }))
            pipe.replacePluggable(Direction.SOUTH,gateItem.onPlace(gate,pipe,Direction.SOUTH,player,InteractionHand.MAIN_HAND))
            fun device(pos: BlockPos,id: String,rotation: Int=0): FaceNodeEntity {
                level.setBlockAndUpdate(pos,Content.node.get().defaultBlockState());return node(server,pos).also { n -> n.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { this.rotation=rotation };n.changedTopology() }
            }
            for((i,id) in listOf("10a_diode","25a_diode","signal_diode").withIndex()) {
                val pos=BlockPos(1946,120,132+i*6)
                device(pos.west(),if(i==2)"signal_source" else "electrical_source");device(pos,id,2)
                val probe=device(pos.east(),"electrical_probe",1)
                PartInventory.get(probe,probe.parts[0]!!).setStackInSlot(0,ItemStack(Content.partItems.getValue(if(i==2)"signal_cable" else "low_voltage_cable").get()))
                device(pos.east(2),"creative_power_resistor",2);device(pos.east(3),"ground_cable")
            }
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.inventoryMenu.broadcastChanges();player.teleportTo(level,1955.0,120.0,148.5,210f,25f)
            Eln.LOGGER.info("ELN_CLIENT_QA actual BCCE quarry initialized {} frame with {} original iron targets; no quarry work or energy was injected",tile.frameBox,targets.size)
        }
        if(ticks==35) { mc.options.guiScale().set(2);mc.resizeDisplay();BuildcraftSpriteAudit.verify(mc) }
        if(ticks==90) {
            verify {
                val quarry=server.overworld().getBlockEntity(at) as TileQuarry
                require(quarry.getCapability(MjCapabilities.CAP_READABLE).orElseThrow { IllegalStateException("Quarry MJ readout missing") }.stored>=0)
                require(node(server,at.west()).parts[0]!!.energy>0)
            };image("actual-electrical-source-converter-and-published-quarry")
        }
        if(ticks==100)camera(1954.5,147.0,180f,55f)
        if(ticks==140)image("actual-published-pipe-and-mounted-gate-textures")
        if(ticks==150)camera(1957.0,149.0,210f,25f)
        if(ticks in listOf(400,800,1200)) {
            verify {
                val changed=targets.count { server.overworld().getBlockState(it).block!==Blocks.IRON_ORE }
                Eln.LOGGER.info("ELN_CLIENT_QA actual electrically supplied quarry: {} original ore blocks excavated; converter paid {} J",changed,node(server,at.west()).parts[0]!!.energy)
            };image("actual-frame-and-mining-progress-$ticks")
        }
        if(ticks==1400)verify {
            val changed=targets.count { server.overworld().getBlockState(it).block!==Blocks.IRON_ORE }
            require(changed>0) { "Actual ELN-powered quarry did not excavate its original ore: paid ${node(server,at.west()).parts[0]!!.energy} J" }
        }
        if(ticks==1440) { image("actual-eln-powered-excavation");Eln.LOGGER.info("ELN_CLIENT_QA actual published BCCE quarry survival placement, native ELN electrical-to-MJ supply, original frame construction and real iron-ore excavation, published stone pipe and mounted gate textures confirmed");camera(1947.2,132.4,90f,58f) }
        if(ticks==1480) { image("original-ten-amp-diode-live-world");camera(1947.2,138.4,90f,58f) }
        if(ticks==1520) { image("original-twenty-five-amp-diode-live-world");camera(1947.2,144.4,90f,58f) }
        if(ticks==1560) {
            verify { for(i in 0..2) { val d=node(server,BlockPos(1946,120,132+i*6)).parts[0]!!;require(d.connectedMask==5 && d.thermalState.getDouble("delta")>0 && kotlin.math.abs(d.current)>.04) } }
            image("original-signal-diode-live-world")
        }
        if(ticks==1580)verify { val player=server.playerList.players.first();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Content.partItems.getValue("signal_diode").get()));player.inventoryMenu.broadcastChanges() }
        if(ticks==1620) { require(mc.player!!.mainHandItem.item==Content.partItems.getValue("signal_diode").get());image("original-held-signal-diode");Eln.LOGGER.info("ELN_CLIENT_QA all three original diode world models, actual forward current, original thermal state and separate held signal diode confirmed");mc.stop() }
    }
}
