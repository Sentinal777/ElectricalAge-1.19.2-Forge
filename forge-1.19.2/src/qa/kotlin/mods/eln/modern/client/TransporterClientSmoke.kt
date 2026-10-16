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
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

object TransporterClientSmoke {
    private var ticks=0
    @Volatile private var failure: String?=null
    private val departure=BlockPos(1520,120,145)
    private val arrival=departure.east(12)
    private var passengerItem: java.util.UUID?=null
    private fun node(s: IntegratedServer,at: BlockPos)=s.overworld().getBlockEntity(at) as FaceNodeEntity
    private fun part(s: IntegratedServer,at: BlockPos)=node(s,at).parts[0]!!
    fun tick(mc: Minecraft,server: IntegratedServer) {
        check(failure==null) { "Transporter authoritative check failed: $failure" };ticks++
        fun verify(action: ()->Unit) { val tick=ticks;server.execute { try { action() }catch(t: Throwable) { failure="tick $tick: ${t.stackTraceToString()}" } } }
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,"eln-original-transporter-$name.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun screen()=mc.screen as DeviceScreen
        fun fields()=screen().children().filterIsInstance<EditBox>()
        fun button(label: String)=screen().children().filterIsInstance<Button>().first { it.message.string==label }.onPress()
        fun open(at: BlockPos)=server.execute {
            val n=node(server,at);val p=part(server,at);val player=server.playerList.players.first();n.sync()
            player.teleportTo(server.overworld(),at.x+2.0,121.0,at.z+5.0,160f,10f)
            net.minecraftforge.network.NetworkHooks.openScreen(player,net.minecraft.world.SimpleMenuProvider({ id,inventory,_ -> DeviceMenu(id,inventory,n,0) },net.minecraft.network.chat.Component.translatable(p.definition.translationKey))) { it.writeBlockPos(at);it.writeByte(0) }
        }
        if(ticks==20)verify {
            val world=server.overworld();world.dayTime=6000
            for(x in 1512..1538)for(z in 140..155)for(y in 119..128)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==119)Blocks.STONE else Blocks.AIR).defaultBlockState())
            world.getEntitiesOfClass(ItemEntity::class.java,net.minecraft.world.phys.AABB(departure,arrival).inflate(12.0)).forEach { it.discard() }
            val player=server.playerList.players.first();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.inventory.clearContent();player.inventory.selected=8
            player.abilities.mayfly=true;player.abilities.flying=true;player.onUpdateAbilities()
            for(at in listOf(departure,arrival)) {
                player.teleportTo(world,at.x+.5,121.0,at.z+6.0,180f,15f)
                val held=ItemStack(Content.partItems.getValue("experimental_transporter").get());player.setItemInHand(InteractionHand.MAIN_HAND,held)
                require(held.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(at.below()),Direction.UP,at.below(),false))).consumesAction() && held.isEmpty)
                val n=node(server,at);val p=part(server,at);val port=RemotePowerPorts.positions(n,p).first();val power=port.position.relative(port.direction)
                world.setBlockAndUpdate(power.below(),Blocks.STONE.defaultBlockState());world.setBlockAndUpdate(power,Content.node.get().defaultBlockState())
                (world.getBlockEntity(power) as FaceNodeEntity).apply { parts[0]=PartState(PartDefinition.byId.getValue("electrical_source"),Direction.DOWN).apply { value=800.0 };changedTopology() }
            }
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.inventoryMenu.broadcastChanges()
        }
        if(ticks==65) { BuildcraftSpriteAudit.verify(mc);open(arrival) }
        if(ticks==100) { require(fields().size==3);fields()[0].value="Arrival";fields()[1].value="Departure";fields()[2].value="20000";button("Apply") }
        if(ticks==125) { require(screen().menu.part!!.expression=="Arrival" && screen().menu.part!!.value==20000.0);image("named-destination-control-packets");mc.player!!.closeContainer();open(departure) }
        if(ticks==160) { fields()[0].value="Departure";fields()[1].value="Arrival";fields()[2].value="20000";button("Apply") }
        if(ticks==185) {
            require(screen().menu.part!!.expression=="Departure" && screen().menu.part!!.channel=="Arrival")
            image("original-name-target-power-gui");button("Start")
        }
        if(ticks==210)verify {
            val n=node(server,departure);val p=part(server,departure);require(Transporters.phase(p)==Transporters.START)
            val at=Transporters.chamber(n,p);val player=server.playerList.players.first()
            player.teleportTo(server.overworld(),at.x+.5,at.y+.08,at.z+.5,180f,0f);player.deltaMovement=Vec3.ZERO
            val item=ItemEntity(server.overworld(),at.x+.65,at.y+.6,at.z+.65,ItemStack(Items.DIAMOND)).apply { setNoGravity(true);setNeverPickUp();deltaMovement=Vec3.ZERO }
            require(server.overworld().addFreshEntity(item));passengerItem=item.uuid
        }
        if(ticks==245) { require(Transporters.phase(screen().menu.part!!)==Transporters.START);image("reserved-pair-waits-for-original-occupant-delay") }
        if(ticks==285) { require(Transporters.closed(screen().menu.part!!));image("original-closed-doors-gui") }
        if(ticks==350) { require(screen().menu.part!!.active && screen().menu.part!!.progress>0);image("actual-paid-20kw-transport-charge-gui") }
        if(ticks==440)verify {
            val p=part(server,departure);val destination=node(server,arrival);val dp=part(server,arrival);val player=server.playerList.players.first()
            val item=server.overworld().getEntity(passengerItem!!) as? ItemEntity
            val distance=sqrt(Transporters.chamber(node(server,departure),p).distSqr(Transporters.chamber(destination,dp)))
            val expected=55000*(1+sqrt(distance/250))
            require(p.gridState.getInt("transporterTrips")==1 && p.gridState.getInt("transporterMoved")==2 && p.gridState.getDouble("transporterSpentJ")>=expected) { "Actual paid player/item trip mismatch: expected=$expected, state=${p.save()}, player=${player.position()}, item=${item?.position()}" }
            require(Transporters.area(destination,dp).contains(player.position()) && item!=null && Transporters.area(destination,dp).contains(item.position()) && item.item.count==1) { "Paid player/item arrival positions differ: player=${player.position()}, item=${item?.position()}, chamber=${Transporters.area(destination,dp)}" }
            val accounted=p.progress+p.gridState.getDouble("transporterSpentJ")+p.gridState.getDouble("transporterDiscardedJ")+p.gridState.getDouble("transporterIdleLossJ")
            require(abs(p.energy-accounted)<1e-4)
        }
        if(ticks==470) { image("real-player-and-item-arrival");mc.player!!.closeContainer() }
        if(ticks==490)verify {
            require(Transporters.phase(part(server,departure))==Transporters.IDLE && Transporters.phase(part(server,arrival))==Transporters.IDLE)
            server.playerList.players.first().teleportTo(server.overworld(),arrival.x+3.4,121.8,arrival.z+6.2,152f,13f)
        }
        if(ticks==530) { image("original-open-door-gyro-and-status-world");server.execute { val p=server.playerList.players.first();p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Content.partItems.getValue("experimental_transporter").get()));p.inventoryMenu.broadcastChanges() } }
        if(ticks==565) {
            image("original-held-mesh")
            val model=mc.itemRenderer.getModel(ItemStack(Content.partItems.getValue("experimental_transporter").get()),mc.level,mc.player,0)
            require(!model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            require(model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            Eln.LOGGER.info("ELN_CLIENT_QA original transporter actual survival placement, real name/destination/power/Start packets, paired reservation and physical doors, paid player-plus-item arrival, original gyro/glow/status model and held/sprite routes confirmed")
        }
        if(ticks==590)mc.stop()
    }
}
