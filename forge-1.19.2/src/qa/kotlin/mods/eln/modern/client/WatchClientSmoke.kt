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
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

object WatchClientSmoke {
    private var ticks=0
    private fun pos(x: Int)=BlockPos(x,121,145)
    fun tick(mc: Minecraft,server: IntegratedServer) {
        ticks++
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun node(x: Int)=server.overworld().getBlockEntity(pos(x)) as FaceNodeEntity
        fun part(x: Int)=node(x).parts.filterNotNull().first()
        fun open(x: Int)=server.execute {
            val n=node(x);val p=part(x);n.sync();val player=server.playerList.players.first()
            player.teleportTo(server.overworld(),x+.5,120.0,148.5,180f,0f)
            net.minecraftforge.network.NetworkHooks.openScreen(player,net.minecraft.world.SimpleMenuProvider(
                { id,inventory,_ -> DeviceMenu(id,inventory,n,p.face.ordinal) },net.minecraft.network.chat.Component.translatable(p.definition.translationKey))) { it.writeBlockPos(n.blockPos);it.writeByte(p.face.ordinal) }
        }
        if(ticks==20)server.execute {
            val world=server.overworld();world.dayTime=6000
            world.gameRules.getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,server)
            for(x in 690..710)for(z in 141..151)for(y in 119..127)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==119)Blocks.STONE else Blocks.AIR).defaultBlockState())
            val player=server.playerList.players.first();player.inventory.clearContent();player.inventory.selected=0
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.abilities.mayfly=true;player.abilities.flying=true;player.onUpdateAbilities()
            for((x,id) in listOf(694 to "analog_watch",702 to "digital_watch")) {
                world.setBlockAndUpdate(pos(x).north(),Blocks.STONE.defaultBlockState());world.setBlockAndUpdate(pos(x),Content.node.get().defaultBlockState())
                val n=node(x);n.parts[Direction.NORTH.ordinal]=PartState(PartDefinition.byId.getValue(id),Direction.NORTH);n.changedTopology()
                val battery=ItemStack(Content.materialItems.getValue("portable_battery").get());ElectricalItems.setEnergy(battery,100.0)
                player.setItemInHand(InteractionHand.MAIN_HAND,battery)
                n.blockState.use(world,player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(pos(x)),Direction.SOUTH,pos(x),false));require(battery.isEmpty)
            }
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.inventoryMenu.broadcastChanges()
        }
        if(ticks==80)open(694)
        if(ticks==130) { require((mc.screen as DeviceScreen).menu.part!!.active);image("eln-original-analog-watch-battery-gui.png") }
        if(ticks==145) { mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),694.5,120.0,147.0,180f,0f) } }
        if(ticks==175)image("eln-original-analog-watch-noon-world.png")
        if(ticks==185)open(702)
        if(ticks==225) { require((mc.screen as DeviceScreen).menu.part!!.active);image("eln-original-digital-watch-battery-gui.png") }
        if(ticks==240) { mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),702.5,120.0,147.0,180f,0f) } }
        if(ticks==270)image("eln-original-digital-watch-noon-world.png")
        if(ticks==280)server.execute { server.overworld().dayTime=13500 }
        if(ticks==315)image("eln-original-digital-watch-evening-world.png")
        if(ticks==325)server.execute { for(x in listOf(694,702)) { val p=part(x);ElectricalItems.setEnergy(PartInventory.get(node(x),p).getStackInSlot(0),p.definition.power*.2) } }
        if(ticks==355) {
            val p=(mc.level!!.getBlockEntity(pos(702)) as FaceNodeEntity).parts.filterNotNull().first();require(!p.active)
            image("eln-original-digital-watch-depleted-world.png")
            server.execute { server.overworld().dayTime=18000;server.playerList.players.first().teleportTo(server.overworld(),694.5,120.0,147.0,180f,0f) }
        }
        if(ticks==390) {
            val p=(mc.level!!.getBlockEntity(pos(694)) as FaceNodeEntity).parts.filterNotNull().first();require(!p.active && WatchTime.date(p,18000)==13500L)
            image("eln-original-analog-watch-stopped-world.png");open(694)
        }
        if(ticks==430) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!;require(!p.active && ElectricalItems.energy(PartInventory.get(menu.node,p).getStackInSlot(0))==0.0)
            image("eln-original-watch-empty-battery-gui.png")
            server.execute { val p=part(694);ElectricalItems.setEnergy(PartInventory.get(node(694),p).getStackInSlot(0),20.0);node(694).changedTopology() }
        }
        if(ticks==465) { require((mc.screen as DeviceScreen).menu.part!!.active);mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),694.5,120.0,147.0,180f,0f) } }
        if(ticks==495) {
            image("eln-original-analog-watch-resumed-midnight-world.png")
            for(d in PartDefinition.all.filter { it.watch!=null }) {
                val model=mc.itemRenderer.getModel(ItemStack(Content.partItems.getValue(d.id).get()),mc.level,mc.player,0)
                require(!model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
                require(model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            }
            Eln.LOGGER.info("ELN_CLIENT_QA original analog/digital clock battery insertion, live day-time digits, actual depletion, frozen analog hands, blank digital display, battery replacement and both held/inventory models confirmed")
        }
        if(ticks==520) { server.execute { server.overworld().gameRules.getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(true,server) };Eln.LOGGER.info("ELN_CLIENT_QA original clocks complete");mc.stop() }
    }
}
