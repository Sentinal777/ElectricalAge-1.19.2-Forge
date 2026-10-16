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

object SimpleSignalClientSmoke {
    private var ticks=0
    @Volatile private var failure: String?=null
    private val at=BlockPos(1910,120,145)
    private fun node(s: IntegratedServer,pos: BlockPos)=s.overworld().getBlockEntity(pos) as FaceNodeEntity
    fun tick(mc: Minecraft,server: IntegratedServer) {
        check(failure==null) { "Simple-signal authoritative check failed: $failure" };ticks++
        fun verify(action: ()->Unit) { val t=ticks;server.execute { try { action() }catch(e: Throwable) { failure="tick $t: ${e.stackTraceToString()}" } } }
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,"eln-original-simple-signal-$name.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun camera(x: Double,z: Double,yaw: Float,pitch: Float)=server.execute { server.playerList.players.first().teleportTo(server.overworld(),x,120.0,z,yaw,pitch) }
        fun activate(pos: BlockPos)=verify {
            val n=node(server,pos);val player=server.playerList.players.first();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
            require((n.blockState.block as NodeBlock).use(n.blockState,server.overworld(),pos,player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)).consumesAction())
        }
        if(ticks==1)verify {
            val level=server.overworld();val player=server.playerList.players.first();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL)
            for(x in 1905..1930)for(z in 139..153)for(y in 119..126)level.setBlockAndUpdate(BlockPos(x,y,z),if(y==119)Blocks.STONE.defaultBlockState()else Blocks.AIR.defaultBlockState())
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity::class.java,net.minecraft.world.phys.AABB(1904.0,118.0,138.0,1932.0,128.0,155.0)).forEach { it.discard() };player.inventory.clearContent()
            level.setWeatherParameters(12000,0,false,false);level.setRainLevel(0f);level.setThunderLevel(0f);level.dayTime=6000
            for((i,id) in listOf("signal_trimmer","signal_switch","signal_button","electrical_daylight_sensor").withIndex()) {
                val pos=at.east(i*5);player.teleportTo(level,pos.x+.5,120.0,pos.z+3.0,180f,25f)
                val stack=ItemStack(Content.partItems.getValue(id).get());player.setItemInHand(InteractionHand.MAIN_HAND,stack)
                require(stack.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false))).consumesAction() && stack.isEmpty)
                node(server,pos).apply { parts[0]!!.rotation=0;changedTopology() }
            }
            fun device(pos: BlockPos,id: String,rotation: Int=0,value: Double?=null): FaceNodeEntity {
                level.setBlockAndUpdate(pos,Content.node.get().defaultBlockState());return node(server,pos).also { n -> n.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { this.rotation=rotation;value?.let { this.value=it } };n.changedTopology() }
            }
            val probe=device(at.east(),"electrical_probe",1);PartInventory.get(probe,probe.parts[0]!!).setStackInSlot(0,ItemStack(Content.partItems.getValue("signal_cable").get()))
            device(at.east(2),"creative_power_resistor",2,10000.0);device(at.east(3),"ground_cable")
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.inventoryMenu.broadcastChanges();player.teleportTo(level,at.x+.5,120.0,at.z+2.0,180f,35f)
        }
        if(ticks==35) { mc.options.guiScale().set(2);mc.resizeDisplay();BuildcraftSpriteAudit.verify(mc) }
        if(ticks==55)activate(at)
        if(ticks==100) {
            val screen=mc.screen as DeviceScreen
            require(screen.children().filterIsInstance<EditBox>().size==1 && screen.children().filterIsInstance<Button>().size==1)
            screen.children().filterIsInstance<EditBox>().single().value="3.75";screen.children().filterIsInstance<Button>().single().onPress()
        }
        if(ticks==135) {
            verify { require(node(server,at).parts[0]!!.value==3.75 && abs(node(server,at.east(2)).parts[0]!!.current) in .00036.. .00039) }
            image("actual-secure-trimmer-gui-and-loaded-output")
            val screen=mc.screen as DeviceScreen;screen.children().filterIsInstance<EditBox>().single().value="6";screen.children().filterIsInstance<Button>().single().onPress()
        }
        if(ticks==165) { verify { require(node(server,at).parts[0]!!.value==3.75) };image("invalid-trimmer-range-preserves-real-setting");mc.player!!.closeContainer();camera(at.x+1.2,at.z+.4,90f,60f) }
        if(ticks==205) { image("original-trimmer-pointer-and-single-terminal-world");camera(at.x+6.2,at.z+.4,90f,60f) }
        if(ticks==235)image("original-red-switch-led-and-halo-world")
        if(ticks==240)activate(at.east(5))
        if(ticks==275) { verify { require(node(server,at.east(5)).parts[0]!!.enabled && node(server,at.east(5)).parts[0]!!.voltage>4.99) };image("original-green-switch-led-and-depressed-button-world");camera(at.x+11.2,at.z+.4,90f,60f) }
        if(ticks==300)activate(at.east(10))
        if(ticks==302)image("original-momentary-button-actual-pulse")
        if(ticks==320) { verify { require(!node(server,at.east(10)).parts[0]!!.enabled && abs(node(server,at.east(10)).parts[0]!!.voltage)<.001) };image("original-button-released-after-point-two-one-seconds") }
        if(ticks==335)verify { val player=server.playerList.players.first();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Content.partItems.getValue("signal_trimmer").get()));player.inventoryMenu.broadcastChanges() }
        if(ticks==370) { require(mc.player!!.mainHandItem.item==Content.partItems.getValue("signal_trimmer").get());image("original-held-trimmer-off-position") }
        if(ticks==400) { Eln.LOGGER.info("ELN_CLIENT_QA original simple controls actual survival placement, secure trimmer GUI and invalid range rejection, real loaded output, switch red/green LED and translated button/halo, actual momentary pulse and original held trimmer confirmed");mc.stop() }
    }
}
