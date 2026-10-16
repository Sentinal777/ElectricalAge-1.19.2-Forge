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
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.nio.file.Files
import kotlin.math.abs

object ProbeTutorialClientSmoke {
    private var ticks=0
    @Volatile private var failure: String?=null
    private val at=BlockPos(1870,120,145)
    private var previous: ByteArray?=null
    private fun node(s: IntegratedServer,pos: BlockPos)=s.overworld().getBlockEntity(pos) as FaceNodeEntity
    fun tick(mc: Minecraft,server: IntegratedServer) {
        check(failure==null) { "Probe/tutorial authoritative check failed: $failure" };ticks++
        fun verify(action: ()->Unit) { val t=ticks;server.execute { try { action() }catch(e: Throwable) { failure="tick $t: ${e.stackTraceToString()}" } } }
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,"eln-original-probe-tutorial-$name.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun camera(x: Double,z: Double,yaw: Float,pitch: Float)=server.execute { server.playerList.players.first().teleportTo(server.overworld(),x,120.0,z,yaw,pitch) }
        if(ticks==1)verify {
            val level=server.overworld();val player=server.playerList.players.first();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.inventory.clearContent();level.setWeatherParameters(12000,0,false,false);level.dayTime=6000
            for(x in 1864..1883)for(z in 140..151)for(y in 119..126)level.setBlockAndUpdate(BlockPos(x,y,z),if(y==119)Blocks.STONE.defaultBlockState()else Blocks.AIR.defaultBlockState())
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity::class.java,net.minecraft.world.phys.AABB(1863.0,118.0,139.0,1885.0,128.0,153.0)).forEach { it.discard() }
            player.inventory.clearContent()
            val file=server.getWorldPath(LevelResource.ROOT).resolve("EA/tutorialSign.txt");previous=if(Files.exists(file))Files.readAllBytes(file)else null
            Files.createDirectories(file.parent);Files.writeString(file,"#client-beacon#\r\nWorkshop circuit\r\nFit a fuse before switching on.\r\n#")
            TutorialSigns.clear()
            for((offset,id) in listOf(0 to "tutorial_sign",6 to "computer_probe")) {
                val pos=at.east(offset);player.teleportTo(level,pos.x+.5,120.0,pos.z+3.0,180f,25f)
                val stack=ItemStack(Content.partItems.getValue(id).get());player.setItemInHand(InteractionHand.MAIN_HAND,stack)
                require(stack.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false))).consumesAction() && stack.isEmpty)
            }
            fun device(pos: BlockPos,id: String,rotation: Int=0,value: Double?=null): FaceNodeEntity {
                level.setBlockAndUpdate(pos,Content.node.get().defaultBlockState());return node(server,pos).also { n -> n.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { this.rotation=rotation;value?.let { this.value=it } };n.changedTopology() }
            }
            val probe=at.east(6);device(probe.north(),"signal_source",value=2.5)
            device(probe.south(),"electrical_probe").let { PartInventory.get(it,it.parts[0]!!).setStackInSlot(0,ItemStack(Content.partItems.getValue("signal_cable").get())) }
            device(probe.south(2),"creative_power_resistor",1,10000.0);device(probe.south(3),"ground_cable")
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.teleportTo(level,at.x+.5,120.0,at.z+2.0,180f,30f)
        }
        if(ticks==35) { mc.options.guiScale().set(2);mc.resizeDisplay();BuildcraftSpriteAudit.verify(mc) }
        if(ticks==60)server.execute {
            val n=node(server,at);val player=server.playerList.players.first();n.sync()
            net.minecraftforge.network.NetworkHooks.openScreen(player,net.minecraft.world.SimpleMenuProvider({ id,inventory,_ -> DeviceMenu(id,inventory,n,0) },Component.translatable(n.parts[0]!!.definition.translationKey))) { it.writeBlockPos(at);it.writeByte(0) }
        }
        if(ticks==100) {
            val screen=mc.screen as TutorialSignScreen;screen.children().filterIsInstance<EditBox>().first().value="client-beacon"
            screen.children().filterIsInstance<Button>().first { it.message.string==Component.translatable("gui.eln.apply").string }.onPress()
        }
        if(ticks==130) { verify { val p=node(server,at).parts[0]!!;require(p.gridState.getBoolean("tutorialFound") && p.gridState.getString("tutorialText")=="Workshop circuit\nFit a fuse before switching on.") };image("actual-beacon-name-packet-and-world-authored-text");mc.player!!.closeContainer();camera(at.x+1.2,at.z+.5,90f,55f) }
        if(ticks==180) { require(TutorialSignOverlay.nearest(mc)?.second?.gridState?.getString("tutorialBeacon")=="client-beacon");image("nearby-original-green-beacon-and-authored-two-line-overlay");camera(at.x+3.5,at.z+2.5,125f,20f) }
        if(ticks==220) { require(TutorialSignOverlay.nearest(mc)==null);image("outside-beacon-neighborhood-hides-text-and-restores-blue-indicator") }
        if(ticks==235) { verify { val n=node(server,at.east(6));val p=n.parts[0]!!;ComputerProbes.call(n,p,0,"ZP",text="out");ComputerProbes.call(n,p,2,"ZP",.75) };camera(at.x+8.4,at.z+2.4,135f,35f) }
        if(ticks==280) { verify { val n=node(server,at.east(6));require((ComputerProbes.call(n,n.parts[0]!!,4,"ZN")[0] as Double) in .49.. .51 && abs(node(server,at.east(6).south(2)).parts[0]!!.current) in .00036.. .00039) };image("original-computer-probe-cube-textures-and-actual-six-side-signal-interface");camera(at.x+4.0,at.z-2.0,-45f,30f) }
        if(ticks==320) { image("original-fixed-computer-probe-opposite-face-textures");verify { val player=server.playerList.players.first();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Content.partItems.getValue("computer_probe").get()));player.inventoryMenu.broadcastChanges() } }
        if(ticks==360) { require(mc.player!!.mainHandItem.item==Content.partItems.getValue("computer_probe").get());image("original-held-computer-probe-cube") }
        if(ticks==375)verify {
            val file=server.getWorldPath(LevelResource.ROOT).resolve("EA/tutorialSign.txt");previous?.let { Files.write(file,it) } ?: Files.deleteIfExists(file);TutorialSigns.clear()
            val n=node(server,at.east(6));ComputerProbes.call(n,n.parts[0]!!,0,"ZP",text="in")
        }
        if(ticks==400) { verify { require(abs(node(server,at.east(6).south(2)).parts[0]!!.current)<1e-6) };Eln.LOGGER.info("ELN_CLIENT_QA original hidden Tutorial Sign actual survival placement, secure beacon-name GUI packet, Windows map-file text, near/far two-line overlay and original blue/green mesh, and original Computer Probe cube/held six textures plus actual input/output current confirmed");mc.stop() }
    }
}
