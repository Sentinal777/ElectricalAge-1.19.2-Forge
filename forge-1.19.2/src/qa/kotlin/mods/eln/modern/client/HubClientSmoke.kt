package mods.eln.modern.client

import mods.eln.Eln
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
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

object HubClientSmoke {
    private var ticks=0
    private val at=BlockPos(764,121,145)
    private val cables=listOf("low_voltage_cable","medium_voltage_cable","high_voltage_cable","very_high_voltage_cable")
    fun tick(mc: Minecraft,server: IntegratedServer) {
        ticks++
        fun node()=server.overworld().getBlockEntity(at) as FaceNodeEntity
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun menu()=(mc.screen as DeviceScreen).menu
        fun move(slot: Int)=mc.gameMode!!.handleInventoryMouseClick(menu().containerId,slot,0,ClickType.QUICK_MOVE,mc.player!!)
        fun link(index: Int) {
            val (x,y)=listOf(97 to 95,77 to 50,77 to 95,97 to 50,61 to 73,87 to 47)[index]
            val sx=(mc.window.guiScaledWidth-176)/2+x;val sy=(mc.window.guiScaledHeight-226)/2+y
            require((mc.screen as DeviceScreen).mouseClicked(sx.toDouble(),sy.toDouble(),0));(mc.screen as DeviceScreen).mouseReleased(sx.toDouble(),sy.toDouble(),0)
        }
        if(ticks==20)server.execute {
            val world=server.overworld();world.dayTime=6000
            for(x in 757..771)for(z in 138..152)for(y in 119..130)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==119 || y==120)Blocks.STONE else Blocks.AIR).defaultBlockState())
            val viewer=server.playerList.players.first();viewer.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);viewer.inventory.clearContent();viewer.inventory.selected=8
            viewer.abilities.mayfly=true;viewer.abilities.flying=true;viewer.onUpdateAbilities();viewer.teleportTo(world,764.5,123.0,149.0,180f,50f)
            val held=ItemStack(Content.partItems.getValue("hub").get());viewer.setItemInHand(InteractionHand.MAIN_HAND,held)
            require(held.useOn(UseOnContext(viewer,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(at.below()),Direction.UP,at.below(),false))).consumesAction() && held.isEmpty)
            val p=node().parts[0]!!;val edges=FaceBasis.edges(p)
            fun device(id: String,pos: BlockPos,rotation: Int=p.rotation): FaceNodeEntity {
                world.setBlockAndUpdate(pos,Content.node.get().defaultBlockState())
                return (world.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { this.rotation=rotation };it.changedTopology() }
            }
            device("electrical_source",at.relative(edges[0])).parts[0]!!.value=10.0
            device("electrical_source",at.relative(edges[1])).parts[0]!!.value=40.0
            device("creative_power_resistor",at.relative(edges[2]));device("ground_cable",at.relative(edges[2],2))
            device("creative_power_resistor",at.relative(edges[3]),(p.rotation+1)%4);device("ground_cable",at.relative(edges[3],2))
            cables.forEachIndexed { i,id -> viewer.inventory.setItem(i,ItemStack(Content.partItems.getValue(id).get())) }
            viewer.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);viewer.inventoryMenu.broadcastChanges()
        }
        if(ticks==80)server.execute {
            val n=node();n.sync();val viewer=server.playerList.players.first();viewer.teleportTo(server.overworld(),764.5,123.0,147.5,180f,55f)
            net.minecraftforge.network.NetworkHooks.openScreen(viewer,net.minecraft.world.SimpleMenuProvider(
                { id,inventory,_ -> DeviceMenu(id,inventory,n,0) },net.minecraft.network.chat.Component.translatable(n.parts[0]!!.definition.translationKey))) { it.writeBlockPos(at);it.writeByte(0) }
        }
        for(i in 0..3)if(ticks==110+i*10)move(31+i)
        if(ticks==180) {
            val m=menu();val p=m.part!!;val inv=PartInventory.get(m.node,p)
            require(cables.indices.all { inv.getStackInSlot(it).item==Content.partItems.getValue(cables[it]).get() && inv.getStackInSlot(it).count==1 })
            require(p.value==48.0 && p.connectedMask==15 && abs(p.pinVoltages[0]-10)<.01 && abs(p.pinVoltages[1]-40)<.01 && abs(p.pinVoltages[2]-10)<.1 && abs(p.pinVoltages[3]-40)<.1)
            image("eln-original-hub-independent-crossings-gui.png");link(0)
        }
        if(ticks==210) { require(menu().part!!.value==49.0);image("eln-original-hub-corner-connected-gui.png");link(4) }
        if(ticks==240) { require(menu().part!!.value==33.0);image("eln-original-hub-one-crossing-open-gui.png");link(2) }
        if(ticks==270) { require(menu().part!!.value==37.0);link(1) }
        if(ticks==300) { require(menu().part!!.value==39.0);link(3) }
        if(ticks==330) { require(menu().part!!.value==47.0);link(5) }
        if(ticks==360) {
            val p=menu().part!!;require(p.value==15.0)
            image("eln-original-hub-all-corner-links-gui.png")
            Network.channel.sendToServer(ConfigureDevice(at,0,p.value,p.resistance,p.enabled,"99",p.expression))
            server.execute { val n=node();val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology() }
        }
        if(ticks==395) { require(menu().part!!.value==15.0 && menu().part!!.connectedMask==15);move(0) }
        if(ticks==425) {
            require(PartInventory.get(menu().node,menu().part!!).getStackInSlot(0).isEmpty && menu().part!!.connectedMask==14)
            image("eln-original-hub-extracted-cable-gui.png")
            val slot=menu().slots.indices.first { it>=4 && menu().slots[it].item.item==Content.partItems.getValue(cables[0]).get() };move(slot)
        }
        if(ticks==460) {
            require(menu().part!!.connectedMask==15)
            server.execute {
                val n=node();val p=n.parts[0]!!;val inv=PartInventory.get(n,p);val viewer=server.playerList.players.first()
                for(id in cables) {
                    val item=Content.partItems.getValue(id).get()
                    val count=(0 until inv.slots).sumOf { if(inv.getStackInSlot(it).item==item)inv.getStackInSlot(it).count else 0 }+viewer.inventory.items.sumOf { if(it.item==item)it.count else 0 }
                    require(count==1)
                }
            }
            mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),764.5,124.0,149.0,180f,45f) }
        }
        if(ticks==495) {
            image("eln-original-hub-configured-links-world.png")
            Eln.LOGGER.info("ELN_CLIENT_QA original hub actual survival placement, four cable inventory packets, independent 10/40 V crossings, all six clickable source links, secure invalid-link rejection, saved node reload and exact cable extraction/reinsertion conservation confirmed")
        }
        if(ticks==520) { Eln.LOGGER.info("ELN_CLIENT_QA original distribution hub complete");mc.stop() }
    }
}
