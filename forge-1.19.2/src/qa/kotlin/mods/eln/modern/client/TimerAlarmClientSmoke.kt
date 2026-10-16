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
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

object TimerAlarmClientSmoke {
    private var ticks=0
    private val heard=hashSetOf<Int>()
    private fun pos(x: Int)=BlockPos(x,121,145)
    fun tick(mc: Minecraft,server: IntegratedServer) {
        ticks++
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun node(x: Int)=server.overworld().getBlockEntity(pos(x)) as FaceNodeEntity
        fun part(x: Int)=node(x).parts.filterNotNull().first()
        fun source(x: Int)=server.overworld().getBlockEntity(pos(x).relative(FaceBasis.edges(part(x))[0])) as FaceNodeEntity
        fun open(x: Int)=server.execute {
            val n=node(x);val p=part(x);n.sync();val viewer=server.playerList.players.first();viewer.teleportTo(server.overworld(),x+.5,120.0,148.0,180f,0f)
            net.minecraftforge.network.NetworkHooks.openScreen(viewer,net.minecraft.world.SimpleMenuProvider(
                { id,inventory,_ -> DeviceMenu(id,inventory,n,p.face.ordinal) },net.minecraft.network.chat.Component.translatable(p.definition.translationKey))) { it.writeBlockPos(n.blockPos);it.writeByte(p.face.ordinal) }
        }
        fun press(key: String) {
            val label=net.minecraft.network.chat.Component.translatable(key).string
            (mc.screen as DeviceScreen).children().filterIsInstance<Button>().first { it.message.string==label }.onPress()
        }
        for(x in listOf(734,744)) {
            val clientNode=mc.level?.getBlockEntity(pos(x)) as? FaceNodeEntity
            if(clientNode!=null && ClientAudio.activeAt(clientNode)>0)heard.add(x)
        }
        if(ticks==20)server.execute {
            val world=server.overworld();world.dayTime=6000
            for(x in 718..750)for(z in 140..151)for(y in 119..128)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==119)Blocks.STONE else Blocks.AIR).defaultBlockState())
            val viewer=server.playerList.players.first();viewer.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);viewer.inventory.clearContent();viewer.inventory.selected=0
            viewer.abilities.mayfly=true;viewer.abilities.flying=true;viewer.onUpdateAbilities()
            fun signal(at: BlockPos,id: String) {
                world.setBlockAndUpdate(at.north(),Blocks.STONE.defaultBlockState());world.setBlockAndUpdate(at,Content.node.get().defaultBlockState())
                val n=world.getBlockEntity(at) as FaceNodeEntity;n.parts[Direction.NORTH.ordinal]=PartState(PartDefinition.byId.getValue(id),Direction.NORTH);n.changedTopology()
            }
            for((x,id) in listOf(724 to "electrical_timer",734 to "nuclear_alarm",744 to "standard_alarm")) {
                val support=pos(x).north();world.setBlockAndUpdate(support,Blocks.STONE.defaultBlockState());viewer.teleportTo(world,x+.5,120.0,148.0,180f,0f)
                val held=ItemStack(Content.partItems.getValue(id).get());viewer.setItemInHand(InteractionHand.MAIN_HAND,held)
                require(held.useOn(UseOnContext(viewer,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(support),Direction.SOUTH,support,false))).consumesAction() && held.isEmpty)
                val p=part(x);signal(pos(x).relative(FaceBasis.edges(p)[0]),"signal_source")
                if(x==724)signal(pos(x).relative(FaceBasis.edges(p)[2]),"signal_cable")
            }
            viewer.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);viewer.inventoryMenu.broadcastChanges()
        }
        if(ticks==80)open(724)
        if(ticks==120) {
            val screen=mc.screen as DeviceScreen;val p=screen.menu.part!!;require(p.active && p.thermalState.getBoolean("timerHigh") && p.voltage>4.9)
            image("eln-original-timer-triggered-gui.png");screen.children().filterIsInstance<EditBox>().first().value="3.0";press("gui.eln.apply")
        }
        if(ticks==145) { require((mc.screen as DeviceScreen).menu.part!!.value==3.0);server.execute { source(724).parts.filterNotNull().first().value=0.0 } }
        if(ticks==175) {
            val p=(mc.screen as DeviceScreen).menu.part!!;require(p.active && !p.thermalState.getBoolean("timerHigh") && p.thermalState.getDouble("timerLeft") in 1.3..2.0)
            image("eln-original-timer-countdown-gui.png");press("gui.eln.timer_reset")
        }
        if(ticks==205) { require(!(mc.screen as DeviceScreen).menu.part!!.active);press("gui.eln.timer_set") }
        if(ticks==230) { require((mc.screen as DeviceScreen).menu.part!!.active);image("eln-original-timer-manual-set-gui.png");mc.player!!.closeContainer();open(734) }
        if(ticks==280) {
            val menu=(mc.screen as DeviceScreen).menu;require(menu.part!!.active && heard.contains(734) && mc.level!!.getBrightness(LightLayer.BLOCK,pos(734))==7)
            image("eln-original-nuclear-alarm-gui.png");press("gui.eln.alarm_sound_on")
        }
        if(ticks==310) {
            val menu=(mc.screen as DeviceScreen).menu;require(!menu.part!!.enabled && menu.part!!.active && ClientAudio.activeAt(menu.node)==0)
            image("eln-original-nuclear-alarm-muted-gui.png");mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),734.5,120.0,147.0,180f,0f) }
        }
        if(ticks==345)image("eln-original-nuclear-alarm-muted-beacon-world.png")
        if(ticks==355)open(744)
        if(ticks==400) {
            val menu=(mc.screen as DeviceScreen).menu;require(menu.part!!.active && heard.contains(744))
            image("eln-original-standard-alarm-gui.png");press("gui.eln.alarm_sound_on")
        }
        if(ticks==430) {
            val menu=(mc.screen as DeviceScreen).menu;require(menu.part!!.active && !menu.part!!.enabled && ClientAudio.activeAt(menu.node)==0)
            image("eln-original-standard-alarm-muted-gui.png");mc.player!!.closeContainer()
            server.execute { source(734).parts.filterNotNull().first().value=0.0;source(744).parts.filterNotNull().first().value=0.0;server.playerList.players.first().teleportTo(server.overworld(),744.5,120.0,147.0,180f,0f) }
        }
        if(ticks==480) {
            val n=mc.level!!.getBlockEntity(pos(744)) as FaceNodeEntity
            require(!n.parts.filterNotNull().first().active && mc.level!!.getBrightness(LightLayer.BLOCK,pos(744))==0 && ClientAudio.activeAt(n)==0)
            image("eln-original-standard-alarm-off-world.png");open(744)
        }
        if(ticks==510) { press("gui.eln.alarm_sound_off");heard.remove(744);server.execute { source(744).parts.filterNotNull().first().value=5.0 } }
        if(ticks==565) {
            require((mc.screen as DeviceScreen).menu.part!!.active && (mc.screen as DeviceScreen).menu.part!!.enabled && heard.contains(744))
            image("eln-original-standard-alarm-resumed-gui.png");mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),744.5,120.0,147.0,180f,0f) }
        }
        if(ticks==590) { image("eln-original-standard-alarm-active-beacon-world.png");open(724) }
        if(ticks==630) { require(!(mc.screen as DeviceScreen).menu.part!!.active);image("eln-original-timer-expired-gui.png");mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),724.5,120.0,147.0,180f,0f) } }
        if(ticks==660) {
            image("eln-original-timer-expired-world.png")
            for(d in PartDefinition.all.filter { it.behavior in setOf(Behavior.TIMER,Behavior.ALARM) }) {
                val model=mc.itemRenderer.getModel(ItemStack(Content.partItems.getValue(d.id).get()),mc.level,mc.player,0)
                require(!model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
                require(model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            }
            Eln.LOGGER.info("ELN_CLIENT_QA actual timer/alarm survival placements, secure interval/apply/set/reset/mute GUI packets, countdown, both original OpenAL alarm clips, independent muted beacon, real block-light shutdown and resumed audio confirmed")
        }
        if(ticks==690) { Eln.LOGGER.info("ELN_CLIENT_QA original timer and alarms complete");mc.stop() }
    }
}
