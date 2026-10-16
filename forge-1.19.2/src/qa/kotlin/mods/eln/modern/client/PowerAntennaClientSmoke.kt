package mods.eln.modern.client

import mods.eln.Eln
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.server.IntegratedServer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.pow

object PowerAntennaClientSmoke {
    private var ticks=0
    @Volatile private var failure: String?=null
    private val txs=listOf(1250,1266,1282).map { BlockPos(it,120,145) }
    private val tiers=listOf("low","medium","high")
    private fun node(s: IntegratedServer,at: BlockPos)=s.overworld().getBlockEntity(at) as FaceNodeEntity
    private fun part(s: IntegratedServer,at: BlockPos)=node(s,at).parts.filterNotNull().single()
    fun tick(mc: Minecraft,server: IntegratedServer) {
        check(failure==null) { "Power antenna authoritative server check failed: $failure" };ticks++
        fun verify(action: ()->Unit) { val at=ticks;server.execute { try { action() }catch(t: Throwable) { failure="tick $at: ${t.stackTraceToString()}" } } }
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,"eln-original-$name.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun camera(at: BlockPos,receiver: Boolean=false)=server.execute { server.playerList.players.first().teleportTo(server.overworld(),at.x+2.5,120.0,at.z+.5+(if(receiver)-2.0 else 2.0),if(receiver)45f else 135f,22f) }
        fun open(at: BlockPos)=server.execute {
            val n=node(server,at);val p=part(server,at);val face=p.face.get3DDataValue();val player=server.playerList.players.first()
            player.teleportTo(server.overworld(),at.x+2.5,120.0,at.z+2.5,135f,22f);n.sync()
            net.minecraftforge.network.NetworkHooks.openScreen(player,net.minecraft.world.SimpleMenuProvider({ id,inventory,_ -> DeviceMenu(id,inventory,n,face) },net.minecraft.network.chat.Component.translatable(p.definition.translationKey))) { it.writeBlockPos(at);it.writeByte(face) }
        }
        fun hand(item: net.minecraft.world.item.Item)=server.execute { val p=server.playerList.players.first();p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(item));p.inventoryMenu.broadcastChanges() }
        fun use(at: BlockPos) {
            val n=mc.level!!.getBlockEntity(at) as FaceNodeEntity;val p=n.parts.filterNotNull().single();val hit=PartGeometry.body(p).center.add(Vec3.atLowerCornerOf(at))
            require(mc.gameMode!!.useItemOn(mc.player!!,InteractionHand.MAIN_HAND,BlockHitResult(hit,p.face.opposite,at,false)).consumesAction())
        }
        fun apply(value: String) {
            val screen=mc.screen as DeviceScreen;screen.children().filterIsInstance<EditBox>().single().value=value
            screen.children().filterIsInstance<Button>().single { it.message.string=="Apply" }.onPress()
        }
        if(ticks==20)verify {
            val world=server.overworld();world.dayTime=6000;world.setWeatherParameters(6000,0,false,false);world.setRainLevel(0f);world.setThunderLevel(0f)
            for(x in 1244..1294)for(z in 138..162)for(y in 118..132)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==118)Blocks.STONE else Blocks.AIR).defaultBlockState())
            val player=server.playerList.players.first();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.inventory.clearContent();player.inventory.selected=8
            player.abilities.mayfly=true;player.abilities.flying=true;player.onUpdateAbilities()
            fun device(id: String,at: BlockPos,face: Direction,rotation: Int=0): FaceNodeEntity {
                world.setBlockAndUpdate(at.relative(face),Blocks.STONE.defaultBlockState());world.setBlockAndUpdate(at,Content.node.get().defaultBlockState())
                return node(server,at).also { it.parts[face.get3DDataValue()]=PartState(PartDefinition.byId.getValue(id),face).apply { this.rotation=rotation };it.changedTopology() }
            }
            for((i,tx) in txs.withIndex()) {
                val rx=tx.south(10)
                for((at,face,id) in listOf(Triple(tx,Direction.NORTH,tiers[i]+"_power_transmitter_antenna"),Triple(rx,Direction.SOUTH,tiers[i]+"_power_receiver_antenna"))) {
                    world.setBlockAndUpdate(at.relative(face),Blocks.STONE.defaultBlockState());player.teleportTo(world,at.x+2.5,120.0,at.z+2.5,135f,20f)
                    val held=ItemStack(Content.partItems.getValue(id).get());player.setItemInHand(InteractionHand.MAIN_HAND,held)
                    require(held.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(at.relative(face)),face.opposite,at.relative(face),false))).consumesAction() && held.isEmpty)
                    require(part(server,at).face==face && part(server,at).rotation==PowerAntennas.initialRotation(face))
                }
                val tp=part(server,tx);val rp=part(server,rx)
                device("electrical_source",tx.below(),tp.face).parts[2]!!.value=tp.definition.nominalVoltage
                device("signal_source",tx.west(),tp.face).parts[2]!!.value=5.0
                device("signal_source",rx.west(),rp.face).parts[3]!!.value=3.25
                val load=device("creative_power_resistor",rx.east(),rp.face,rp.rotation).parts[3]!!;load.value=rp.definition.nominalVoltage.pow(2)/(rp.definition.power*.8)
                device("ground_cable",rx.east(2),rp.face)
            }
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.inventoryMenu.broadcastChanges()
        }
        if(ticks==65)verify { server.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity::class.java,net.minecraft.world.phys.AABB(1243.0,117.0,137.0,1296.0,134.0,164.0)).forEach { it.discard() } }
        if(ticks==80) {
            verify { for(tx in txs) { val p=part(server,tx);val r=part(server,tx.south(10));require(p.gridState.getBoolean("beamConnected") && r.active && r.energy>0 && r.energy<p.energy && abs(p.pinVoltages[0]-3.25)<.001) } }
            camera(txs[0])
        }
        if(ticks==115) { image("low-power-transmitter-original-model-world");camera(txs[0].south(10),true) }
        if(ticks==150) { image("low-power-receiver-original-model-world");open(txs[0]) }
        if(ticks==185) { require((mc.screen as DeviceScreen).menu.part!!.current>4.9);image("low-power-transmitter-input-efficiency-gui");mc.player!!.closeContainer();open(txs[0].west()) }
        if(ticks==220)apply("1.25")
        if(ticks==260) { mc.player!!.closeContainer();open(txs[0]) }
        if(ticks==295) {
            val p=(mc.screen as DeviceScreen).menu.part!!;require(p.voltage*p.current in 61.0..64.0)
            image("power-transmitter-real-quarter-command-gui");mc.player!!.closeContainer();open(txs[0].south(10).west())
        }
        if(ticks==330)apply("1.75")
        if(ticks==370) { mc.player!!.closeContainer();camera(txs[0]);hand(Content.wrench.get()) }
        if(ticks==400) {
            verify { require(abs(part(server,txs[0]).pinVoltages[0]-1.75)<.001) }
            use(txs[0])
        }
        if(ticks==435) {
            verify { require(part(server,txs[0]).rotation==1 && abs(part(server,txs[0]).current)<1e-6 && !part(server,txs[0].south(10)).active) }
            image("power-antenna-wrench-rotated-disconnected-ports-world");use(txs[0])
        }
        if(ticks==465 || ticks==495)use(txs[0])
        if(ticks==530) {
            verify { require(part(server,txs[0]).rotation==0 && part(server,txs[0].south(10)).active && part(server,txs[0]).voltage*part(server,txs[0]).current in 61.0..64.0) }
            camera(txs[1]);server.execute { server.playerList.players.first().setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY) }
        }
        if(ticks==565) { image("medium-power-transmitter-original-model-world");camera(txs[1].south(10),true) }
        if(ticks==600) { image("medium-power-receiver-original-model-world");camera(txs[2]) }
        if(ticks==635) { image("high-power-transmitter-original-model-world");open(txs[2].south(10)) }
        if(ticks==670) {
            val p=(mc.screen as DeviceScreen).menu.part!!;require(p.active && p.gridState.getDouble("beamOutputWatts")>1800)
            image("high-power-receiver-real-output-gui");mc.player!!.closeContainer();camera(txs[2].south(10),true)
        }
        if(ticks==705) {
            image("high-power-receiver-original-model-world")
            for(tier in tiers)for(kind in listOf("transmitter","receiver")) {
                val model=mc.itemRenderer.getModel(ItemStack(Content.partItems.getValue(tier+"_power_"+kind+"_antenna").get()),mc.level,mc.player,0)
                require(!model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
                require(model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            }
            Eln.LOGGER.info("ELN_CLIENT_QA all six power antennas survival wall placement, original rear ports and meshes, real finite input/output, independent receiver feedback packet, quarter-command Apply packet, four actual wrench rotation packets, interruption/reconnection, efficiency/output screens and all six held/inventory routes confirmed")
            hand(Content.partItems.getValue("low_power_transmitter_antenna").get())
        }
        if(ticks==740)image("power-antenna-original-held-mesh")
        if(ticks==770) { Eln.LOGGER.info("ELN_CLIENT_QA original power antennas complete");mc.stop() }
    }
}
