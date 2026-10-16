package mods.eln.modern.client

import mods.eln.Eln
import mods.eln.modern.*
import mods.eln.modern.lighting.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.server.IntegratedServer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

object PaintedPowerClientSmoke {
    private var ticks=0
    @Volatile private var serverFailure: String?=null
    private val socket=BlockPos(1144,120,145);private val other=BlockPos(1152,120,145)
    private val cable=socket.east();private val board=BlockPos(1134,120,145);private val bus=BlockPos(1170,120,151)
    private val scanner=BlockPos(1185,120,145);private val fluidScanner=BlockPos(1195,120,145)
    private fun node(server: IntegratedServer,at: BlockPos)=server.overworld().getBlockEntity(at) as FaceNodeEntity
    private fun part(server: IntegratedServer,at: BlockPos)=node(server,at).parts.filterNotNull().single()
    private fun material(id: String)=ItemStack(Content.materialItems.getValue(id).get())
    fun tick(mc: Minecraft,server: IntegratedServer) {
        check(serverFailure==null) { "Painted wiring authoritative server check failed: $serverFailure" };ticks++
        fun verify(action: ()->Unit) { val tick=ticks;server.execute { try { action() }catch(f: Throwable) { serverFailure="tick $tick: ${f.stackTraceToString()}" } } }
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,"eln-original-$name.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun camera(at: BlockPos,wall: Boolean=true)=server.execute {
            server.playerList.players.first().teleportTo(server.overworld(),at.x+.5,if(wall)120.0 else 121.0,at.z+3.0,180f,if(wall)25f else 50f)
        }
        fun hand(stack: ItemStack)=server.execute { val p=server.playerList.players.first();p.setItemInHand(InteractionHand.MAIN_HAND,stack);p.inventoryMenu.broadcastChanges() }
        fun use(at: BlockPos) {
            val n=mc.level!!.getBlockEntity(at) as FaceNodeEntity;val p=n.parts.filterNotNull().single()
            val point=PartGeometry.body(p).center.add(Vec3.atLowerCornerOf(at))
            require(mc.gameMode!!.useItemOn(mc.player!!,InteractionHand.MAIN_HAND,BlockHitResult(point,p.face.opposite,at,false)).consumesAction())
        }
        fun open(at: BlockPos)=server.execute {
            val n=node(server,at);val p=n.parts.filterNotNull().single();val face=p.face.get3DDataValue()
            val viewer=server.playerList.players.first();viewer.teleportTo(server.overworld(),at.x+.5,121.0,at.z+2.5,180f,25f);n.sync()
            net.minecraftforge.network.NetworkHooks.openScreen(viewer,net.minecraft.world.SimpleMenuProvider(
                { id,inventory,_ -> DeviceMenu(id,inventory,n,face) },net.minecraft.network.chat.Component.translatable(p.definition.translationKey))) { it.writeBlockPos(at);it.writeByte(face) }
        }
        if(ticks==20)verify {
            val world=server.overworld();world.dayTime=6000;world.setWeatherParameters(6000,0,false,false)
            for(x in 1129..1200)for(z in 139..158)for(y in 119..128)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==119)Blocks.STONE else Blocks.AIR).defaultBlockState())
            val player=server.playerList.players.first();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.inventory.clearContent();player.inventory.selected=8
            player.abilities.mayfly=true;player.abilities.flying=true;player.onUpdateAbilities()
            fun device(id: String,at: BlockPos,face: Direction=Direction.NORTH,color: Int=0,rotation: Int=0): FaceNodeEntity {
                world.setBlockAndUpdate(at.relative(face),Blocks.STONE.defaultBlockState());world.setBlockAndUpdate(at,Content.node.get().defaultBlockState())
                return node(server,at).also { it.parts[face.get3DDataValue()]=PartState(PartDefinition.byId.getValue(id),face).apply { this.rotation=rotation;gridState.putInt("paintColor",color) };it.changedTopology() }
            }
            for((at,id) in listOf(socket to "type_j_socket",other to "type_e_socket")) {
                world.setBlockAndUpdate(at.north(),Blocks.STONE.defaultBlockState());player.teleportTo(world,at.x+.5,120.0,at.z+3.0,180f,0f)
                val held=ItemStack(Content.partItems.getValue(id).get());player.setItemInHand(InteractionHand.MAIN_HAND,held)
                require(held.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(at.north()),Direction.SOUTH,at.north(),false))).consumesAction() && held.isEmpty)
            }
            val supply=device("lamp_supply",board);val bp=part(server,board)
            PartInventory.get(supply,bp).setStackInSlot(0,ItemStack(Content.partItems.getValue("low_voltage_cable").get()))
            LampChannels.write(bp,listOf(LampChannel("QA sockets","true",0)));supply.changedTopology()
            part(server,device("electrical_source",board.east()).blockPos).value=50.0
            device("low_voltage_cable",cable)
            device("low_voltage_cable",socket.east(2));part(server,device("creative_power_resistor",socket.east(3)).blockPos).value=100.0;device("ground_cable",socket.east(4))
            part(server,other).channel="QA sockets";node(server,other).changedTopology()
            part(server,device("creative_power_resistor",other.east()).blockPos).value=100.0;device("ground_cable",other.east(2))
            for(x in 1164..1170)device("signal_bus_cable",BlockPos(x,120,151),Direction.DOWN)
            device("signal_cable",BlockPos(1164,120,150),Direction.DOWN,1);part(server,device("signal_source",BlockPos(1164,120,149),Direction.DOWN).blockPos).value=1.5
            device("signal_cable",BlockPos(1167,120,150),Direction.DOWN,4);part(server,device("signal_source",BlockPos(1167,120,149),Direction.DOWN).blockPos).value=4.0
            device("signal_cable",bus.south(),Direction.DOWN,1);device("signal_cable",bus.west(2).south(),Direction.DOWN,4)
            for(at in listOf(scanner,fluidScanner)) {
                player.teleportTo(world,at.x-.5,121.0,at.z+.5,-90f,45f)
                val held=ItemStack(Content.partItems.getValue("scanner").get());player.setItemInHand(InteractionHand.MAIN_HAND,held)
                require(held.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(at.below()),Direction.UP,at.below(),false))).consumesAction() && held.isEmpty)
                require(part(server,at).rotation==0)
                device("signal_cable",at.west(),Direction.DOWN)
            }
            world.setBlockAndUpdate(scanner.east(),Blocks.CHEST.defaultBlockState())
            (world.getBlockEntity(scanner.east()) as net.minecraft.world.level.block.entity.ChestBlockEntity).apply { setItem(0,ItemStack(net.minecraft.world.item.Items.STONE,32));setItem(1,ItemStack(net.minecraft.world.item.Items.WOODEN_PICKAXE));setChanged() }
            val tankPos=fluidScanner.east();world.setBlockAndUpdate(tankPos,Content.machine.get().defaultBlockState())
            node(server,tankPos).apply { parts[0]=PartState(PartDefinition.byId.getValue("48v_fuel_generator"),Direction.DOWN);tank.setFluid(net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000));changedTopology() }
            for((i,d) in Materials.all.filter { it.kind=="BRUSH" }.sortedBy { it.variant }.withIndex())player.inventory.setItem(i,ItemStack(Content.materialItems.getValue(d.id).get()).apply { orCreateTag.putInt("life",if(i%2==0)32 else 0) })
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.inventoryMenu.broadcastChanges()
        }
        if(ticks==65)verify { server.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity::class.java,net.minecraft.world.phys.AABB(1128.0,118.0,138.0,1179.0,129.0,159.0)).forEach { it.discard() } }
        if(ticks==80)open(socket)
        if(ticks==115) {
            require((mc.screen as DeviceScreen).menu.part!!.definition.behavior==Behavior.POWER_SOCKET)
            (mc.screen as DeviceScreen).children().filterIsInstance<EditBox>().single().value="QA sockets"
            (mc.screen as DeviceScreen).children().filterIsInstance<Button>().single { it.message.string=="Apply" }.onPress()
        }
        if(ticks==150) { require((mc.screen as DeviceScreen).menu.part!!.active);image("type-j-socket-channel-current-gui");mc.player!!.closeContainer();camera(socket);hand(material("red_brush")) }
        if(ticks==180)use(socket)
        if(ticks==210) {
            verify { require(PaintedWiring.color(part(server,socket))==1 && Brushes.life(server.playerList.players.first().mainHandItem)==31 && part(server,socket.east(3)).current.let { kotlin.math.abs(it)<1e-6 }) }
            image("type-j-socket-painted-red-world");camera(cable)
        }
        if(ticks==240)use(cable)
        if(ticks==270) { verify { require(Brushes.life(server.playerList.players.first().mainHandItem)==30 && kotlin.math.abs(part(server,socket.east(3)).current)<1e-6) };hand(ItemStack(Content.wrench.get())) }
        if(ticks==300)use(cable)
        if(ticks==335) {
            verify { require(!PaintedWiring.cares(part(server,cable)) && kotlin.math.abs(part(server,socket.east(3)).current)>.49) }
            image("painted-cable-color-care-wrench-world");camera(other);hand(ItemStack.EMPTY)
        }
        if(ticks==375)image("type-e-socket-powered-world")
        if(ticks==400)open(bus)
        if(ticks==440) {
            val p=(mc.screen as DeviceScreen).menu.part!!;val v=p.gridState.getLongArray("busVoltages").map(Double::fromBits)
            require(v.size==16 && kotlin.math.abs(v[1]-1.5)<.001 && kotlin.math.abs(v[4]-4)<.001)
            image("signal-bus-sixteen-channel-gui");mc.player!!.closeContainer();camera(bus.west(2),false)
        }
        if(ticks==480) { image("signal-bus-independent-colored-wiring-world");mc.setScreen(InventoryScreen(mc.player!!)) }
        if(ticks==520) {
            image("paint-brushes-fresh-and-dry-inventory")
            for(d in Materials.all.filter { it.kind=="BRUSH" }) {
                val model=mc.itemRenderer.getModel(material(d.id),mc.level,mc.player,0)
                require(model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
                require(!model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            }
            for(id in listOf("type_j_socket","type_e_socket")) {
                val model=mc.itemRenderer.getModel(ItemStack(Content.partItems.getValue(id).get()),mc.level,mc.player,0)
                require(!model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
                require(model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            }
            Eln.LOGGER.info("ELN_CLIENT_QA original sockets survival wall placement, live supply/channel Apply packet, real colored brush/wrench packets, held wear, finite power interruption/reconnection, sixteen live bus channels and eighteen original inventory/held render routes confirmed")
        }
        if(ticks==550) { mc.player!!.closeContainer();camera(scanner,false);hand(ItemStack.EMPTY) }
        if(ticks==590) {
            val p=(mc.level!!.getBlockEntity(scanner) as FaceNodeEntity).parts[0]!!
            require(kotlin.math.abs(p.voltage-5.0*33/(27*64))<.001 && p.gridState.getInt("scannerMode")==0)
            image("stationary-scanner-total-inventory-fill-world");use(scanner)
        }
        if(ticks==630) {
            val p=(mc.level!!.getBlockEntity(scanner) as FaceNodeEntity).parts[0]!!
            require(p.gridState.getInt("scannerMode")==1 && kotlin.math.abs(p.voltage-10.0/27)<.001)
            image("stationary-scanner-occupied-slots-world");hand(ItemStack(Content.multimeter.get()))
        }
        if(ticks==660)use(scanner)
        if(ticks==685) { image("stationary-scanner-multimeter-live-reading");camera(fluidScanner,false);hand(ItemStack.EMPTY) }
        if(ticks==725) {
            val p=(mc.level!!.getBlockEntity(fluidScanner) as FaceNodeEntity).parts[0]!!
            require(kotlin.math.abs(p.voltage-2.5)<.001 && kotlin.math.abs(p.powerFactor-.5)<1e-6)
            image("stationary-scanner-real-fluid-tank-world")
            val model=mc.itemRenderer.getModel(ItemStack(Content.partItems.getValue("scanner").get()),mc.level,mc.player,0)
            require(!model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            require(model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            Eln.LOGGER.info("ELN_CLIENT_QA original stationary scanner survival placement, actual mode click packet, inventory total/slots and actual fluid capability readings, original LED mode groups, multimeter and inventory/held routes confirmed")
        }
        if(ticks==760) { Eln.LOGGER.info("ELN_CLIENT_QA painted power sockets, signal bus and stationary scanners complete");mc.stop() }
    }
}
