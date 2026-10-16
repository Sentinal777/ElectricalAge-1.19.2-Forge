package mods.eln.modern.client

import mods.eln.Eln
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.Button
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

object AutoMinerClientSmoke {
    private var ticks=0
    @Volatile private var failure: String?=null
    private val at=BlockPos(1480,120,145)
    private fun node(s: IntegratedServer)=s.overworld().getBlockEntity(at) as FaceNodeEntity
    private fun part(s: IntegratedServer)=node(s).parts[0]!!
    private fun source(s: IntegratedServer): FaceNodeEntity {
        val port=RemotePowerPorts.positions(node(s),part(s)).first()
        return s.overworld().getBlockEntity(port.position.relative(port.direction)) as FaceNodeEntity
    }
    fun tick(mc: Minecraft,server: IntegratedServer) {
        check(failure==null) { "Auto Miner authoritative check failed: $failure" };ticks++
        fun verify(action: ()->Unit) { val tick=ticks;server.execute { try { action() }catch(t: Throwable) { failure="tick $tick: ${t.stackTraceToString()}" } } }
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,"eln-original-auto-miner-$name.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun menu()=(mc.screen as DeviceScreen).menu
        fun button()=(mc.screen as DeviceScreen).children().filterIsInstance<Button>().first().onPress()
        fun open()=server.execute {
            val n=node(server);n.sync();val player=server.playerList.players.first()
            player.teleportTo(server.overworld(),1481.5,120.7,148.5,160f,15f)
            net.minecraftforge.network.NetworkHooks.openScreen(player,net.minecraft.world.SimpleMenuProvider({ id,inventory,_ -> DeviceMenu(id,inventory,n,0) },net.minecraft.network.chat.Component.translatable(part(server).definition.translationKey))) { it.writeBlockPos(at);it.writeByte(0) }
        }
        fun move(slot: Int)=mc.gameMode!!.handleInventoryMouseClick(menu().containerId,slot,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,mc.player!!)
        if(ticks==20)verify {
            val world=server.overworld();world.dayTime=6000
            for(x in 1472..1488)for(z in 137..155)for(y in 110..129)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==110)Blocks.STONE else Blocks.AIR).defaultBlockState())
            world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity::class.java,net.minecraft.world.phys.AABB(at).inflate(14.0)).forEach { it.discard() }
            val player=server.playerList.players.first();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.inventory.clearContent();player.inventory.selected=8
            player.teleportTo(world,1480.5,121.0,149.0,180f,15f);player.abilities.mayfly=true;player.abilities.flying=true;player.onUpdateAbilities()
            world.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState())
            val held=ItemStack(Content.partItems.getValue("auto_miner").get());player.setItemInHand(InteractionHand.MAIN_HAND,held)
            require(held.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(at.below()),Direction.UP,at.below(),false))).consumesAction() && held.isEmpty)
            val n=node(server);val p=part(server)
            world.setBlockAndUpdate(AutoMiners.output(n,p),Blocks.CHEST.defaultBlockState())
            val port=RemotePowerPorts.positions(n,p).first();val power=port.position.relative(port.direction)
            world.setBlockAndUpdate(power.below(),Blocks.STONE.defaultBlockState());world.setBlockAndUpdate(power,Content.node.get().defaultBlockState())
            source(server).apply { parts[0]=PartState(PartDefinition.byId.getValue("electrical_source"),Direction.DOWN).apply { value=800.0 };changedTopology() }
            world.setBlockAndUpdate(at.below(3).east(3),Blocks.IRON_ORE.defaultBlockState())
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
            player.inventory.setItem(9,ItemStack(Content.materialItems.getValue("turbo_electrical_drill").get()))
            player.inventory.setItem(10,ItemStack(Content.materialItems.getValue("mining_pipe").get(),6));player.inventoryMenu.broadcastChanges()
        }
        if(ticks==70) { BuildcraftSpriteAudit.verify(mc);open() }
        if(ticks==100) { require(menu().componentSlots==3);button() }
        if(ticks==120) { require(menu().part!!.value==1.0);image("silk-touch-original-hardware-gui");button() }
        if(ticks==140) { require(menu().part!!.value==0.0);move(3);move(4) }
        if(ticks==180) { require(menu().part!!.active);image("actual-paid-drilling-gui");mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),1483.8,121.5,150.2,145f,22f) } }
        if(ticks==240) {
            image("original-working-body-pipes-and-display")
            server.execute {
                val edges=FaceBasis.edges(part(server));val u=edges[0];val v=edges[1]
                val panel=Vec3.atCenterOf(at).add(u.stepX*.2925, .2925,u.stepZ*.2925).add(v.stepX*-1.57031,0.0,v.stepZ*-1.57031)
                server.playerList.players.first().teleportTo(server.overworld(),panel.x-v.stepX*2, panel.y-1.47,panel.z-v.stepZ*2,v.toYRot(),5f)
            }
        }
        if(ticks==265)image("original-scanner-and-operating-log-closeup")
        if(ticks==280)verify {
            val n=node(server);val p=part(server);val inv=PartInventory.get(n,p)
            require(p.gridState.getInt("autoMinerDrilled")==2 && AutoMiners.depth(p)>=3 && inv.getStackInSlot(2).count+AutoMiners.depth(p)==6)
            val chest=server.overworld().getBlockEntity(AutoMiners.output(n,p)) as net.minecraft.world.level.block.entity.ChestBlockEntity
            require((0 until chest.containerSize).sumOf { if(chest.getItem(it).`is`(Items.COBBLESTONE))chest.getItem(it).count else 0 }==1)
            require((0 until chest.containerSize).sumOf { if(chest.getItem(it).`is`(Items.RAW_IRON))chest.getItem(it).count else 0 }==1)
            source(server).parts[0]!!.enabled=false;source(server).changedTopology()
        }
        if(ticks==310)open()
        if(ticks==340) { require(!menu().part!!.active);image("saved-depth-and-real-rear-chest-output");move(0);verify { source(server).parts[0]!!.enabled=true;source(server).changedTopology() } }
        if(ticks==510)verify {
            val p=part(server);require(AutoMiners.depth(p)==0 && PartInventory.get(node(server),p).getStackInSlot(2).count==6)
            val accounted=p.progress+p.gridState.getDouble("autoMinerSpentJ")+p.gridState.getDouble("autoMinerDiscardedJ")+p.gridState.getDouble("autoMinerIdleLossJ")
            require(abs(p.energy-accounted)<1e-4)
        }
        if(ticks==535) {
            require(menu().part!!.gridState.getInt(AutoMiners.DEPTH)==0);image("all-six-original-pipes-retracted-on-drill-removal");mc.player!!.closeContainer()
            server.execute { val player=server.playerList.players.first();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Content.partItems.getValue("auto_miner").get()));player.inventoryMenu.broadcastChanges() }
        }
        if(ticks==570) {
            image("original-held-artwork")
            val model=mc.itemRenderer.getModel(ItemStack(Content.partItems.getValue("auto_miner").get()),mc.level,mc.player,0)
            require(!model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            require(model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            Eln.LOGGER.info("ELN_CLIENT_QA original Auto Miner survival placement, raised original power terminals, actual silk-touch control and drill/pipe inventory packets, finite paid excavation and ore drops into real rear chest, complete paid pipe retraction, original model and held/sprite routes confirmed")
        }
        if(ticks==590)mc.stop()
    }
}
