package mods.eln.modern.client

import mods.eln.Eln
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.server.IntegratedServer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RailBlock
import net.minecraft.world.level.block.state.properties.RailShape
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object RailClientSmoke {
    private var ticks=0
    @Volatile private var failure: String?=null
    private val rail=BlockPos(1360,120,145)
    private val wires=(1352..1368).map { BlockPos(it,123,145) }
    private var startX=0.0
    private var startMotor=0.0
    private fun cart(server: IntegratedServer)=server.overworld().getEntitiesOfClass(ElectricMinecartEntity::class.java,AABB(1335.0,118.0,140.0,1395.0,126.0,152.0)).single()
    private fun clientCart(mc: Minecraft)=mc.level!!.getEntitiesOfClass(ElectricMinecartEntity::class.java,AABB(1335.0,118.0,140.0,1395.0,126.0,152.0)).single()
    fun tick(mc: Minecraft,server: IntegratedServer) {
        check(failure==null) { "Rail authoritative server check failed: $failure" };ticks++
        fun verify(action: ()->Unit) { val at=ticks;server.execute { try { action() }catch(t: Throwable) { failure="tick $at: ${t.stackTraceToString()}" } } }
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,"eln-original-$name.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun hand(stack: ItemStack)=server.execute { val player=server.playerList.players.first();player.setItemInHand(InteractionHand.MAIN_HAND,stack);player.inventoryMenu.broadcastChanges() }
        fun camera()=server.execute { server.playerList.players.first().teleportTo(server.overworld(),1360.5,121.0,150.5,180f,10f) }
        if(ticks==20)verify {
            val world=server.overworld();world.dayTime=6000;world.setWeatherParameters(6000,0,false,false)
            for(old in world.getEntitiesOfClass(ElectricMinecartEntity::class.java,AABB(1335.0,115.0,138.0,1396.0,132.0,154.0)))old.discard()
            for(x in 1339..1390)for(z in 139..152)for(y in 119..130)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==119)Blocks.STONE else Blocks.AIR).defaultBlockState())
            for(x in 1343..1383)world.setBlockAndUpdate(BlockPos(x,120,145),Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE,RailShape.EAST_WEST))
            val player=server.playerList.players.first();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.inventory.clearContent();player.inventory.selected=8
            player.abilities.mayfly=true;player.abilities.flying=true;player.onUpdateAbilities()
            world.setBlockAndUpdate(wires.first().west(),Blocks.STONE.defaultBlockState());world.setBlockAndUpdate(wires.last().east(),Blocks.STONE.defaultBlockState())
            for(at in wires) {
                world.setBlockAndUpdate(at,Content.machine.get().defaultBlockState())
                (world.getBlockEntity(at) as FaceNodeEntity).apply { parts[0]=PartState(PartDefinition.byId.getValue("overhead_lines"),Direction.DOWN);changedTopology() }
            }
            val source=rail.above(3).south();world.setBlockAndUpdate(source.below(),Blocks.STONE.defaultBlockState());world.setBlockAndUpdate(source,Content.node.get().defaultBlockState())
            (world.getBlockEntity(source) as FaceNodeEntity).apply { parts[0]=PartState(PartDefinition.byId.getValue("electrical_source"),Direction.DOWN).apply { value=800.0 };changedTopology() }
            player.teleportTo(world,1361.5,121.0,147.5,155f,25f);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Content.materialItems.getValue("electric_minecart").get()).apply { setHoverName(net.minecraft.network.chat.Component.literal("Electrical Age rail QA")) });player.inventoryMenu.broadcastChanges()
        }
        if(ticks==65)verify { server.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity::class.java,AABB(1338.0,118.0,138.0,1391.0,132.0,154.0)).forEach { it.discard() } }
        if(ticks==90)require(mc.gameMode!!.useItemOn(mc.player!!,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(rail),Direction.UP,rail,false)).consumesAction())
        if(ticks==130) {
            verify { require(server.playerList.players.first().mainHandItem.isEmpty && cart(server).energyJ>0 && cart(server).customName?.string=="Electrical Age rail QA") }
            camera()
        }
        if(ticks==170) { require(clientCart(mc).displayedEnergy>5000);image("electric-minecart-real-overhead-charging-world") }
        if(ticks==195)server.execute { val player=server.playerList.players.first();player.teleportTo(server.overworld(),1361.5,121.0,147.5,155f,25f) }
        if(ticks==225)require(mc.gameMode!!.interact(mc.player!!,clientCart(mc),InteractionHand.MAIN_HAND).consumesAction())
        if(ticks==265) {
            verify { val c=cart(server);require(server.playerList.players.first().vehicle===c);startX=c.x;startMotor=c.motorJ }
            hand(ItemStack(Content.multimeter.get()))
        }
        if(ticks==295) {
            require(mc.player!!.vehicle is ElectricMinecartEntity)
            mc.gameMode!!.useItem(mc.player!!,InteractionHand.MAIN_HAND)
        }
        if(ticks==320) {
            image("electric-minecart-ridden-live-energy-meter")
            mc.options.cameraType=net.minecraft.client.CameraType.THIRD_PERSON_BACK
            mc.player!!.yRot=-90f;mc.player!!.xRot=5f;mc.options.keyUp.isDown=true
        }
        if(ticks==350)mc.options.keyUp.isDown=false
        if(ticks==380) {
            verify {
                val c=cart(server);require(c.x>startX+3 && c.motorJ>startMotor && c.energyJ>=0)
                val parts=wires.map { (server.overworld().getBlockEntity(it) as FaceNodeEntity).parts[0]!! }
                val supplied=parts.sumOf { it.energy-it.gridState.getDouble("railDissipatedJ") }
                require(abs(supplied-c.energyJ-RailPower.pending(c)-c.motorJ)<1e-4)
                Eln.LOGGER.info("ELN_CLIENT_QA real moving/ridden electric cart stored={} J motor={} J paid={} J averageTick={} ms",c.energyJ,c.motorJ,supplied,server.averageTickTime)
            }
            image("electric-minecart-moving-on-real-rails-third-person")
        }
        if(ticks==410) {
            mc.options.keyUp.isDown=false
            server.execute { val player=server.playerList.players.first();player.stopRiding();cart(server).deltaMovement=Vec3.ZERO }
        }
        if(ticks==445) {
            mc.options.cameraType=net.minecraft.client.CameraType.FIRST_PERSON
            server.execute { server.playerList.players.first().teleportTo(server.overworld(),1352.5,122.0,149.5,180f,12f) }
            hand(ItemStack(Content.partItems.getValue("overhead_lines").get()))
        }
        if(ticks==480) {
            image("overhead-lines-original-gantry-wire-and-inventory-artwork")
            val model=mc.itemRenderer.getModel(ItemStack(Content.partItems.getValue("overhead_lines").get()),mc.level,mc.player,0)
            require(!model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            require(mc.entityRenderDispatcher.getRenderer(clientCart(mc)) is net.minecraft.client.renderer.entity.MinecartRenderer<*>)
            Eln.LOGGER.info("ELN_CLIENT_QA electric cart actual survival rail-placement packet, consumed named item, original iron-load minecart renderer and overhead gantry/cable artwork, real charging, actual riding and mounted meter packet, normal movement input, finite motor work and complete paid energy ledger confirmed")
        }
        if(ticks==510) { Eln.LOGGER.info("ELN_CLIENT_QA original electric railway complete");mc.stop() }
    }
}
