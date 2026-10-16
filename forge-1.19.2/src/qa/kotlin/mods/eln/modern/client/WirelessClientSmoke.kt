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

object WirelessClientSmoke {
    private var ticks=0
    @Volatile private var serverFailure: String?=null
    private val txA=BlockPos(1000,120,145);private val txB=BlockPos(1000,120,155)
    private val near=BlockPos(1016,120,150);private val far=BlockPos(1055,120,150);private val relay=BlockPos(1026,120,150)
    private val button=BlockPos(1000,120,165);private val switch=BlockPos(1000,120,175)
    private val buttonRx=BlockPos(1016,120,165);private val switchRx=BlockPos(1016,120,175)
    private fun node(server: IntegratedServer,at: BlockPos)=server.overworld().getBlockEntity(at) as FaceNodeEntity
    private fun place(server: IntegratedServer,id: String,at: BlockPos): FaceNodeEntity {
        val viewer=server.playerList.players.first();viewer.teleportTo(server.overworld(),at.x+.5,121.0,at.z+3.0,180f,45f)
        val held=ItemStack(Content.partItems.getValue(id).get());viewer.setItemInHand(InteractionHand.MAIN_HAND,held)
        require(held.useOn(UseOnContext(viewer,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(at.below()),Direction.UP,at.below(),false))).consumesAction() && held.isEmpty)
        viewer.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
        return node(server,at)
    }
    fun tick(mc: Minecraft,server: IntegratedServer) {
        check(serverFailure==null) { "Wireless authoritative server check failed: $serverFailure" }
        ticks++
        fun verifyServer(check: ()->Unit)=server.execute { try { check() } catch(failure: Throwable) { serverFailure=failure.toString() } }
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,"eln-original-$name.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun menu()=(mc.screen as DeviceScreen).menu
        fun mode()=(mc.screen as DeviceScreen).children().filterIsInstance<Button>().first { it.message.string in setOf("Max","Min","Toggle") }.onPress()
        fun open(at: BlockPos)=server.execute {
            val n=node(server,at);val viewer=server.playerList.players.first();viewer.teleportTo(server.overworld(),at.x+.5,121.0,at.z+2.5,180f,45f);n.sync()
            net.minecraftforge.network.NetworkHooks.openScreen(viewer,net.minecraft.world.SimpleMenuProvider(
                { id,inventory,_ -> DeviceMenu(id,inventory,n,0) },net.minecraft.network.chat.Component.translatable(n.parts[0]!!.definition.translationKey))) { it.writeBlockPos(at);it.writeByte(0) }
        }
        fun source(at: BlockPos,value: Double)=server.execute { val p=node(server,at).parts[0]!!;node(server,at.relative(FaceBasis.edges(p)[0])).parts[0]!!.value=value }
        fun camera(at: BlockPos)=server.execute { server.playerList.players.first().teleportTo(server.overworld(),at.x+.5,120.5,at.z+3.5,180f,32f) }
        fun use(at: BlockPos)=mc.gameMode!!.useItemOn(mc.player!!,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(at),Direction.UP,at,false))
        if(ticks==20)server.execute {
            val world=server.overworld();world.dayTime=6000
            for(x in 995..1060)for(z in 140..181)for(y in 119..129)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==119)Blocks.STONE else Blocks.AIR).defaultBlockState())
            val viewer=server.playerList.players.first();viewer.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);viewer.inventory.clearContent();viewer.inventory.selected=8
            viewer.abilities.mayfly=true;viewer.abilities.flying=true;viewer.onUpdateAbilities()
            fun device(id: String,at: BlockPos): FaceNodeEntity {
                world.setBlockAndUpdate(at,Content.node.get().defaultBlockState())
                return node(server,at).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN);it.changedTopology() }
            }
            for((id,at,ch) in listOf(Triple("wireless_signal_transmitter",txA,"Radio QA"),Triple("wireless_signal_transmitter",txB,"Radio QA"),Triple("wireless_signal_receiver",near,"Radio QA"),Triple("wireless_signal_receiver",far,"Radio QA"),Triple("wireless_signal_repeater",relay,"unused"),Triple("wireless_button",button,"Button QA"),Triple("wireless_switch",switch,"Switch QA"),Triple("wireless_signal_receiver",buttonRx,"Button QA"),Triple("wireless_signal_receiver",switchRx,"Switch QA"))) {
                val n=place(server,id,at);val p=n.parts[0]!!;p.channel=ch
                if(id=="wireless_signal_transmitter")device("signal_source",at.relative(FaceBasis.edges(p)[0])).parts[0]!!.value=if(at==txA)5.0 else 1.0
                if(id=="wireless_signal_receiver")device("signal_cable",at.relative(FaceBasis.edges(p)[0]))
                n.changedTopology()
            }
        }
        if(ticks==85)open(near)
        if(ticks==125) { require(menu().part!!.voltage>4.99 && menu().part!!.gridState.getInt("wirelessTransmitters")==2);image("wireless-receiver-max-gui");mode() }
        if(ticks==165) { require(abs(menu().part!!.voltage-1)<.01 && menu().part!!.value==1.0);image("wireless-receiver-min-gui");mode();source(txA,0.0);source(txB,0.0) }
        if(ticks==195) { require(menu().part!!.voltage<.01 && menu().part!!.value==2.0);source(txA,5.0) }
        if(ticks==230) {
            require(menu().part!!.voltage>4.99);image("wireless-receiver-toggle-latched-gui")
            server.execute { val n=node(server,near);val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology() }
        }
        if(ticks==265) { require(menu().part!!.voltage>4.99);source(txA,0.0) }
        if(ticks==290)source(txA,5.0)
        if(ticks==325) { require(menu().part!!.voltage<.01);image("wireless-receiver-toggle-second-edge-gui");mc.player!!.closeContainer() }
        if(ticks==355)open(far)
        if(ticks==395) { require(menu().part!!.voltage>4.99 && menu().part!!.active);image("wireless-repeater-remote-reception-gui");server.execute { server.overworld().removeBlock(relay,false) } }
        if(ticks==445) { require(menu().part!!.voltage<.01 && !menu().part!!.active);image("wireless-repeater-removed-gui");server.execute { place(server,"wireless_signal_repeater",relay) };mc.player!!.closeContainer() }
        if(ticks==485)open(far)
        if(ticks==520) { require(menu().part!!.voltage>4.99);image("wireless-repeater-restored-gui");mc.player!!.closeContainer();camera(relay) }
        if(ticks==555) { image("wireless-repeater-model-world");open(button) }
        if(ticks==590) {
            val field=(mc.screen as DeviceScreen).children().filterIsInstance<EditBox>().single();field.value="Button QA 2"
            (mc.screen as DeviceScreen).children().filterIsInstance<Button>().first { it.message.string=="Apply" }.onPress()
        }
        if(ticks==620) { require(menu().part!!.channel=="Button QA 2");image("wireless-button-channel-packet-gui");mc.player!!.closeContainer();server.execute { node(server,buttonRx).parts[0]!!.channel="Button QA 2";node(server,buttonRx).changedTopology() };camera(button) }
        if(ticks==650)use(button)
        if(ticks==653)verifyServer { require(node(server,buttonRx).parts[0]!!.voltage>4.9 && node(server,button).parts[0]!!.enabled) }
        if(ticks==665)verifyServer { require(node(server,buttonRx).parts[0]!!.voltage<.01 && !node(server,button).parts[0]!!.enabled) }
        if(ticks==670) { image("wireless-button-model-world");camera(switch) }
        if(ticks==705)use(switch)
        if(ticks==735) { verifyServer { require(node(server,switchRx).parts[0]!!.voltage>4.9 && node(server,switch).parts[0]!!.enabled) };image("wireless-switch-on-world");open(switch) }
        if(ticks==770) { require(menu().part!!.enabled);image("wireless-switch-channel-state-gui");mc.player!!.closeContainer();camera(switch) }
        if(ticks==805)use(switch)
        if(ticks==835) { verifyServer { require(node(server,switchRx).parts[0]!!.voltage<.01 && !node(server,switch).parts[0]!!.enabled) };image("wireless-switch-off-world");camera(txA) }
        if(ticks==875) {
            image("wireless-transmitter-model-world")
            for(d in PartDefinition.all.filter { it.wireless!=null }) {
                val model=mc.itemRenderer.getModel(ItemStack(Content.partItems.getValue(d.id).get()),mc.level,mc.player,0)
                require(!model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
                require(model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            }
            Eln.LOGGER.info("ELN_CLIENT_QA original five radio models and survival placements, actual Max/Min/Toggle GUI packets and saved history, repeated real 55-block relay reception/removal/restoration, channel edit packet, .21-second physical radio button and two actual radio switch use packets confirmed")
        }
        if(ticks==910) { Eln.LOGGER.info("ELN_CLIENT_QA original wireless controls complete");mc.stop() }
    }
}
