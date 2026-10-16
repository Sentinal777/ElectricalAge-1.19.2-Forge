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
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/** Actual client packets, original model routes and finite fabrication work; development only. */
object FabricatorClientSmoke {
    private var ticks=0
    @Volatile private var failure: String?=null
    private val at=BlockPos(1440,120,145)
    private fun node(s: IntegratedServer)=s.overworld().getBlockEntity(at) as FaceNodeEntity
    private fun part(s: IntegratedServer)=node(s).parts[0]!!
    fun tick(mc: Minecraft,server: IntegratedServer) {
        check(failure==null) { "Fabricator authoritative check failed: $failure" };ticks++
        fun verify(action: ()->Unit) { val tick=ticks;server.execute { try { action() }catch(t: Throwable) { failure="tick $tick: ${t.stackTraceToString()}" } } }
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,"eln-original-fabricator-$name.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun menu()=(mc.screen as DeviceScreen).menu
        fun buttons()=(mc.screen as DeviceScreen).children().filterIsInstance<Button>()
        fun open()=server.execute {
            val n=node(server);val viewer=server.playerList.players.first();n.sync()
            net.minecraftforge.network.NetworkHooks.openScreen(viewer,net.minecraft.world.SimpleMenuProvider({ id,inventory,_ -> DeviceMenu(id,inventory,n,0) },net.minecraft.network.chat.Component.translatable(part(server).definition.translationKey))) { it.writeBlockPos(at);it.writeByte(0) }
        }
        fun inputs()=server.execute {
            val player=server.playerList.players.first()
            player.inventory.setItem(9,ItemStack(Content.materialItems.getValue("silicon_wafer").get()))
            player.inventory.setItem(10,ItemStack(Content.materialItems.getValue("copper_plate").get()))
            player.containerMenu.broadcastChanges()
        }
        fun insert() { for(slot in 3..4)mc.gameMode!!.handleInventoryMouseClick(menu().containerId,slot,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,mc.player!!) }
        if(ticks==20)verify {
            val world=server.overworld();world.dayTime=6000
            for(x in 1436..1444)for(z in 140..152)for(y in 119..125)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==119)Blocks.STONE else Blocks.AIR).defaultBlockState())
            world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity::class.java,net.minecraft.world.phys.AABB(at).inflate(12.0)).forEach { it.discard() }
            val viewer=server.playerList.players.first();viewer.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);viewer.inventory.clearContent();viewer.inventory.selected=8
            viewer.teleportTo(world,1440.5,120.0,148.0,180f,20f);viewer.abilities.mayfly=true;viewer.abilities.flying=true;viewer.onUpdateAbilities()
            val held=ItemStack(Content.partItems.getValue("fabricator").get());viewer.setItemInHand(InteractionHand.MAIN_HAND,held)
            require(held.useOn(UseOnContext(viewer,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(at.below()),Direction.UP,at.below(),false))).consumesAction() && held.isEmpty)
            world.setBlockAndUpdate(at.north(),Content.node.get().defaultBlockState())
            (world.getBlockEntity(at.north()) as FaceNodeEntity).apply { parts[0]=PartState(PartDefinition.byId.getValue("electrical_source"),Direction.DOWN).apply { value=200.0 };changedTopology() }
            viewer.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);viewer.inventoryMenu.broadcastChanges()
        }
        if(ticks==60) { BuildcraftSpriteAudit.verify(mc);open() }
        if(ticks in 90..230 && (ticks-90)%10==0) {
            val index=(ticks-90)/10;require(buttons().size==15);buttons()[index].onPress()
        }
        if(ticks in 99..239 && (ticks-99)%10==0) {
            val index=(ticks-99)/10
            require(menu().part!!.value.toInt()==index) { "Operation $index did not survive actual GUI packet" }
            verify { require(part(server).value.toInt()==index && part(server).progress==0.0) }
        }
        if(ticks==245) { image("all-fifteen-original-operation-buttons");buttons()[3].onPress();inputs();verify { part(server).gridState.putLong("fabricatorRandom",2L) } }
        if(ticks==265)insert()
        if(ticks==300) { require(menu().part!!.active && menu().part!!.progress>1000);image("paid-alu-work-gui");mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),1441.5,120.2,147.3,156f,20f) } }
        if(ticks==330)image("original-moving-etcher-world")
        if(ticks==370) {
            verify {
                val p=part(server);val inv=PartInventory.get(node(server),p)
                require(p.gridState.getInt("fabricatorFailedBatches")==1 && p.gridState.getDouble("fabricatorBatchWorkJ")==4000.0 && inv.getStackInSlot(0).isEmpty && inv.getStackInSlot(1).isEmpty && inv.getStackInSlot(2).isEmpty)
            };open()
        }
        if(ticks==400) { buttons()[0].onPress();inputs() }
        if(ticks==420)insert()
        if(ticks==520) {
            verify {
                val p=part(server);val inv=PartInventory.get(node(server),p)
                require(inv.getStackInSlot(0).count==16 && p.gridState.getInt("fabricatorBatches")==2 && p.gridState.getDouble("fabricatorBatchWorkJ")==8000.0)
                val counted=p.progress+p.gridState.getDouble("fabricatorBatchWorkJ")+p.gridState.getDouble("fabricatorDiscardedJ")+p.gridState.getDouble("fabricatorIdleLossJ")
                require(abs(p.energy-counted)<1e-5)
            }
            image("original-sixteen-transistor-output-gui")
            mc.gameMode!!.handleInventoryMouseClick(menu().containerId,0,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,mc.player!!)
        }
        if(ticks==550) {
            require(menu().slots[0].item.isEmpty)
            verify { require(server.playerList.players.first().inventory.items.filter { it.item===Content.materialItems.getValue("transistor").get() }.sumOf { it.count }==16) }
            mc.player!!.closeContainer()
            server.execute { val p=server.playerList.players.first();p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Content.partItems.getValue("fabricator").get()));p.inventoryMenu.broadcastChanges() }
        }
        if(ticks==580) {
            image("original-held-mesh")
            val model=mc.itemRenderer.getModel(ItemStack(Content.partItems.getValue("fabricator").get()),mc.level,mc.player,0)
            for(type in listOf(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI,net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND))require(model.applyTransform(type,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            Eln.LOGGER.info("ELN_CLIENT_QA original Fabricator survival placement, all fifteen real operation packets, quick-move wafer/copper input and sixteen-transistor output, paid failed ALU batch, conserved actual resistor work, original 3D GUI/held/world routes and moving etchers confirmed")
        }
        if(ticks==600)mc.stop()
    }
}
