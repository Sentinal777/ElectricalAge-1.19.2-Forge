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
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object SignalDeviceClientSmoke {
    private var ticks=0
    @Volatile private var failure: String?=null
    private val at=BlockPos(1740,120,145)
    private val ids=listOf("analog_vumeter","led_vumeter","multicolor_led_vumeter")
    private fun node(s: IntegratedServer,pos: BlockPos)=s.overworld().getBlockEntity(pos) as FaceNodeEntity
    fun tick(mc: Minecraft,server: IntegratedServer) {
        check(failure==null) { "Signal-device authoritative check failed: $failure" };ticks++
        fun verify(action: ()->Unit) { val t=ticks;server.execute { try { action() }catch(e: Throwable) { failure="tick $t: ${e.stackTraceToString()}" } } }
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,"eln-original-signal-devices-$name.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun screen()=mc.screen as SignalProcessorScreen
        fun configure(text: String) { screen().children().filterIsInstance<EditBox>().single().value=text;screen().children().filterIsInstance<Button>().single().onPress() }
        fun camera(x: Double,z: Double,yaw: Float,pitch: Float=28f)=server.execute { server.playerList.players.first().teleportTo(server.overworld(),x,120.0,z,yaw,pitch) }
        if(ticks==1)verify {
            val level=server.overworld();val player=server.playerList.players.first();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.inventory.clearContent();player.teleportTo(level,1740.5,120.0,148.0,180f,25f)
            level.setWeatherParameters(12000,0,false,false);level.dayTime=6000
            for(x in 1735..1762)for(z in 140..151)for(y in 119..126)level.setBlockAndUpdate(BlockPos(x,y,z),if(y==119)Blocks.STONE.defaultBlockState()else Blocks.AIR.defaultBlockState())
            fun device(pos: BlockPos,id: String,rotation: Int=0,value: Double?=null): FaceNodeEntity {
                level.setBlockAndUpdate(pos,Content.node.get().defaultBlockState())
                return node(server,pos).also { n -> n.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { this.rotation=rotation;value?.let { this.value=it } };n.changedTopology() }
            }
            for((i,id) in (listOf("signal_processor")+ids).withIndex()) {
                val pos=if(i==0)at else at.east(5+(i-1)*4)
                player.teleportTo(level,pos.x+.5,120.0,pos.z+2.5,180f,25f)
                val stack=ItemStack(Content.partItems.getValue(id).get());player.setItemInHand(InteractionHand.MAIN_HAND,stack)
                require(stack.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false))).consumesAction() && stack.isEmpty)
                node(server,pos).apply { parts[0]!!.rotation=0;changedTopology() }
                if(i>0)device(pos.north(),"signal_source",value=4.0)
                if(i>0)device(pos.east(),"signal_source",value=1.0)
            }
            device(at.east(),"signal_source",value=2.5);device(at.south(),"signal_source",value=5.0);device(at.west(),"signal_source",value=1.25)
            device(at.north(),"electrical_probe").let { probe -> PartInventory.get(probe,probe.parts[0]!!).setStackInSlot(0,ItemStack(Content.partItems.getValue("signal_cable").get())) }
            device(at.north(2),"creative_power_resistor",rotation=1,value=10000.0);device(at.north(3),"ground_cable")
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.inventory.setItem(9,ItemStack(Items.REDSTONE,2));player.inventoryMenu.broadcastChanges()
        }
        if(ticks==35) { mc.options.guiScale().set(2);mc.resizeDisplay();BuildcraftSpriteAudit.verify(mc) }
        if(ticks==55)server.execute {
            val n=node(server,at);val player=server.playerList.players.first();player.teleportTo(server.overworld(),1740.5,120.0,148.0,180f,25f);n.sync()
            net.minecraftforge.network.NetworkHooks.openScreen(player,net.minecraft.world.SimpleMenuProvider({ id,inventory,_ -> DeviceMenu(id,inventory,n,0) },net.minecraft.network.chat.Component.translatable(n.parts[0]!!.definition.translationKey))) { it.writeBlockPos(at);it.writeByte(0) }
        }
        if(ticks==100)configure("A+B*C")
        if(ticks==135) { verify { val p=node(server,at).parts[0]!!;require(!p.gridState.getBoolean("processorReady") && abs(p.voltage)<.001 && p.gridState.getInt("processorRedstone")==2) };image("actual-equation-needs-two-real-redstone") }
        if(ticks==145)mc.gameMode!!.handleInventoryMouseClick(screen().menu.containerId,1,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,mc.player!!)
        if(ticks==185) { verify { val n=node(server,at);val p=n.parts[0]!!;require(PartInventory.peek(p,0).count==2 && p.voltage in 3.7..3.8 && node(server,at.north(2)).parts[0]!!.current in .00036.. .00039) };image("actual-redstone-quickmove-and-independent-inputs-output");configure("beep(A,1)") }
        if(ticks==225) { verify { require(node(server,at).parts[0]!!.gridState.getBoolean("processorBeep")) };require(ClientAudio.activeAt(screen().menu.node)>0);image("actual-original-beep-expression-and-hardware-gui");configure("bad(") }
        if(ticks==260) { verify { val p=node(server,at).parts[0]!!;require(!p.gridState.getBoolean("processorValid") && abs(p.voltage)<.001 && !p.gridState.getBoolean("processorBeep")) };image("actual-invalid-equation-stops-signal-and-sound");mc.player!!.closeContainer();camera(1743.5,149.0,210f,35f) }
        if(ticks==300) {
            verify { for(i in ids.indices) { val p=node(server,at.east(5+i*4)).parts[0]!!;require(p.connectedMask==8 && p.voltage in 3.99..4.01) } }
            image("original-analog-green-led-and-rgb-at-four-volts-world")
        }
        if(ticks==310)camera(1744.0,145.5,-90f,42f)
        if(ticks==340)image("original-moving-analog-pointer-close-world")
        if(ticks==350)camera(1748.0,145.5,-90f,42f)
        if(ticks==380) { image("original-green-led-world");verify { for(i in ids.indices)node(server,at.east(5+i*4).north()).apply { parts[0]!!.value=0.0;changedTopology() } } }
        if(ticks==425) { verify { require(abs(node(server,at.east(9)).parts[0]!!.voltage)<.001) };image("original-red-led-at-zero-volts-world");camera(1752.0,145.5,-90f,42f) }
        if(ticks==465) { image("original-rgb-grey-at-zero-world");verify { for(i in ids.indices)node(server,at.east(5+i*4).north()).apply { parts[0]!!.value=2.5;changedTopology() } } }
        if(ticks==535) { image("original-rgb-cyan-midscale-world");camera(1741.2,145.5,90f,55f) }
        if(ticks==575)image("original-processor-near-player-open-door-and-leds-world")
        if(ticks==585)camera(1740.5,144.7,0f,60f)
        if(ticks==620)image("original-processor-open-door-north-world")
        if(ticks==630)verify { val player=server.playerList.players.first();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Content.partItems.getValue("signal_processor").get()));player.inventoryMenu.broadcastChanges() }
        if(ticks==665) { require(mc.player!!.mainHandItem.item==Content.partItems.getValue("signal_processor").get());image("original-held-processor-open-door-example") }
        if(ticks==690) { Eln.LOGGER.info("ELN_CLIENT_QA original signal processor actual survival placement, real redstone quickmove and budget, independent used inputs and loaded output, valid/invalid/beep GUI packets and actual loop, original processor door/LEDs and all three one-input vuMeter models and live colors confirmed");mc.stop() }
    }
}
