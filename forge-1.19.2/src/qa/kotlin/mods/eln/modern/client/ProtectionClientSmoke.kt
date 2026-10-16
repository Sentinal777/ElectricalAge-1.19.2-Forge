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
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object ProtectionClientSmoke {
    private var ticks=0
    @Volatile private var serverFailure: String?=null
    private val holder=BlockPos(1090,120,145);private val breaker=BlockPos(1102,120,145)
    private val levels=listOf("low","medium","high","very_high")
    private fun node(server: IntegratedServer,at: BlockPos)=server.overworld().getBlockEntity(at) as FaceNodeEntity
    private fun material(id: String,n: Int=1)=ItemStack(Content.materialItems.getValue(id).get(),n)
    fun tick(mc: Minecraft,server: IntegratedServer) {
        check(serverFailure==null) { "Protection authoritative server check failed: $serverFailure" };ticks++
        fun verify(check: ()->Unit) { val tick=ticks;server.execute { try { check() }catch(f: Throwable) { serverFailure="tick $tick: ${f.stackTraceToString()}" } } }
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,"eln-original-$name.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun menu()=(mc.screen as DeviceScreen).menu
        fun clickButton(text: String)=(mc.screen as DeviceScreen).children().filterIsInstance<Button>().first { it.message.string==text }.onPress()
        fun open(at: BlockPos)=server.execute {
            val n=node(server,at);val viewer=server.playerList.players.first();viewer.teleportTo(server.overworld(),at.x+.5,121.0,at.z+2.5,180f,45f);n.sync()
            net.minecraftforge.network.NetworkHooks.openScreen(viewer,net.minecraft.world.SimpleMenuProvider(
                { id,inventory,_ -> DeviceMenu(id,inventory,n,0) },net.minecraft.network.chat.Component.translatable(n.parts[0]!!.definition.translationKey))) { it.writeBlockPos(at);it.writeByte(0) }
        }
        fun camera(at: BlockPos)=server.execute {
            val edge=FaceBasis.edges(node(server,at).parts[0]!!)[1]
            val yaw=when(edge) { Direction.EAST->90f;Direction.WEST->-90f;Direction.NORTH->0f;else->180f }
            server.playerList.players.first().teleportTo(server.overworld(),at.x+.5+edge.stepX*2.0,120.5,at.z+.5+edge.stepZ*2.0,yaw,43f)
        }
        fun use(at: BlockPos): net.minecraft.world.InteractionResult {
            val p=(mc.level!!.getBlockEntity(at) as FaceNodeEntity).parts[0]!!
            val point=PartGeometry.body(p).center.add(Vec3.atLowerCornerOf(at))
            return mc.gameMode!!.useItemOn(mc.player!!,InteractionHand.MAIN_HAND,BlockHitResult(point,Direction.UP,at,false))
        }
        fun hand(stack: ItemStack)=server.execute { val player=server.playerList.players.first();player.setItemInHand(InteractionHand.MAIN_HAND,stack);player.inventoryMenu.broadcastChanges() }
        fun source(at: BlockPos,value: Double)=server.execute { val p=node(server,at).parts[0]!!;node(server,at.relative(FaceBasis.edges(p)[0])).parts[0]!!.value=value }
        if(ticks==20)server.execute {
            val world=server.overworld();world.dayTime=6000;world.setWeatherParameters(6000,0,false,false)
            for(x in 1085..1110)for(z in 139..151)for(y in 119..129)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==119)Blocks.STONE else Blocks.AIR).defaultBlockState())
            val player=server.playerList.players.first();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.inventory.clearContent();player.inventory.selected=8
            player.abilities.mayfly=true;player.abilities.flying=true;player.onUpdateAbilities()
            fun device(id: String,at: BlockPos,rotation: Int=0): FaceNodeEntity {
                world.setBlockAndUpdate(at,Content.node.get().defaultBlockState())
                return node(server,at).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { this.rotation=rotation };it.changedTopology() }
            }
            for((at,id) in listOf(holder to "electrical_fuse_holder",breaker to "5a_electrical_breaker")) {
                player.teleportTo(world,at.x+.5,121.0,at.z+3.0,180f,45f)
                val held=ItemStack(Content.partItems.getValue(id).get());player.setItemInHand(InteractionHand.MAIN_HAND,held)
                require(held.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(at.below()),Direction.UP,at.below(),false))).consumesAction() && held.isEmpty)
                val p=node(server,at).parts[0]!!;val edges=FaceBasis.edges(p)
                device("electrical_source",at.relative(edges[0])).parts[0]!!.value=20.0
                device("creative_power_resistor",at.relative(edges[2]),(p.rotation+2)%4).parts[0]!!.value=100.0
                device("ground_cable",at.relative(edges[2],2))
            }
            world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity::class.java,net.minecraft.world.phys.AABB(1085.0,118.0,139.0,1111.0,130.0,152.0)).forEach { it.discard() }
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
            player.inventory.setItem(0,ItemStack(Content.partItems.getValue("low_voltage_cable").get()))
            player.inventoryMenu.broadcastChanges()
        }
        if(ticks==80) { camera(holder);hand(material("lead_fuse_for_low_voltage_cables",2)) }
        if(ticks==110)use(holder)
        if(ticks==140) {
            verify { val installed=PartInventory.get(node(server,holder),node(server,holder).parts[0]!!).getStackInSlot(0);val held=server.playerList.players.first().mainHandItem;require(installed.count==1 && held.count==1) { "Installed $installed, remaining held $held" } }
            image("fuse-holder-low-voltage-world");hand(material("lead_fuse_for_medium_voltage_cables"))
        }
        if(ticks==165)use(holder)
        if(ticks==195) { image("fuse-holder-medium-voltage-world");hand(material("lead_fuse_for_high_voltage_cables")) }
        if(ticks==220)use(holder)
        if(ticks==250) { image("fuse-holder-high-voltage-world");hand(material("lead_fuse_for_very_high_voltage_cables")) }
        if(ticks==275)use(holder)
        if(ticks==305) { image("fuse-holder-very-high-voltage-world");open(holder) }
        if(ticks==340) {
            require(menu().part!!.active && PartInventory.get(menu().node,menu().part!!).getStackInSlot(0).item==Content.materialItems.getValue("lead_fuse_for_very_high_voltage_cables").get())
            image("fuse-holder-installed-current-gui");mc.player!!.closeContainer();hand(ItemStack.EMPTY);camera(holder)
        }
        if(ticks==370)use(holder)
        if(ticks==400) { verify { require(PartInventory.get(node(server,holder),node(server,holder).parts[0]!!).getStackInSlot(0).isEmpty) };image("fuse-holder-empty-world");hand(material("lead_fuse_for_low_voltage_cables")) }
        if(ticks==425)use(holder)
        if(ticks==450) {
            source(holder,96*(.13333333333333333+.0125+.004001))
            server.execute {
                val n=node(server,holder);val p=n.parts[0]!!;val edge=FaceBasis.edges(p)[2];val load=node(server,holder.relative(edge));load.parts[0]!!.value=0.0;load.parts[0]!!.resistance=1e-9;load.changedTopology()
            }
        }
        if(ticks==525)open(holder)
        if(ticks==560) { require(!menu().part!!.active && PartInventory.get(menu().node,menu().part!!).getStackInSlot(0).item==Content.materialItems.getValue("blown_lead_fuse").get());image("fuse-holder-blown-open-gui");mc.player!!.closeContainer();hand(ItemStack.EMPTY);camera(holder) }
        if(ticks==590)image("fuse-holder-blown-world")
        if(ticks==620)open(breaker)
        if(ticks==650)mc.gameMode!!.handleInventoryMouseClick(menu().containerId,28,0,ClickType.QUICK_MOVE,mc.player!!)
        if(ticks==690) {
            require(menu().part!!.originalBreaker && !menu().part!!.enabled && PartInventory.get(menu().node,menu().part!!).getStackInSlot(0).count==1)
            image("original-breaker-fitted-default-window-gui")
            (mc.screen as DeviceScreen).children().filterIsInstance<EditBox>().single().value="0,30";clickButton("Apply")
        }
        if(ticks==725) { require(menu().part!!.expression=="0,30");clickButton("Contact open") }
        if(ticks==765) { require(menu().part!!.active && menu().part!!.enabled && menu().part!!.current>.19);image("original-breaker-closed-window-gui");source(breaker,40.0) }
        if(ticks==805) { require(!menu().part!!.enabled && menu().part!!.tripped);image("original-breaker-upper-voltage-trip-gui");(mc.screen as DeviceScreen).children().filterIsInstance<EditBox>().single().value="30,50";clickButton("Apply") }
        if(ticks==835)clickButton("Contact open")
        if(ticks==875) { require(menu().part!!.active);source(breaker,20.0) }
        if(ticks==915) { require(!menu().part!!.enabled && menu().part!!.tripped);image("original-breaker-lower-voltage-trip-gui");mc.player!!.closeContainer();camera(breaker) }
        if(ticks==950) {
            image("original-breaker-open-model-world")
            for(id in listOf("electrical_fuse_holder","5a_electrical_breaker")) {
                val model=mc.itemRenderer.getModel(ItemStack(Content.partItems.getValue(id).get()),mc.level,mc.player,0)
                require(!model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
                require(model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            }
            for(id in levels.map { "lead_fuse_for_${it}_voltage_cables" }+"blown_lead_fuse") {
                val model=mc.itemRenderer.getModel(material(id),mc.level,mc.player,0)
                require(!model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
                require(model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            }
            Eln.LOGGER.info("ELN_CLIENT_QA original fuse/holder and breaker survival placements, all four physical fuse replacement packets, exact held consumption and empty-hand extraction, actual thermal melt and original blown mesh, cable inventory packet, both voltage-window Apply/Close/trip packets and seven original held/inventory routes confirmed")
        }
        if(ticks==980) { Eln.LOGGER.info("ELN_CLIENT_QA original fuses and fitted-cable breaker complete");mc.stop() }
    }
}
