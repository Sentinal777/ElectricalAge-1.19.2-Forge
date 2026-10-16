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

object RheostatNixieClientSmoke {
    private var ticks=0
    private val rheostat=BlockPos(950,120,145);private val nixie=BlockPos(962,120,145)
    fun tick(mc: Minecraft,server: IntegratedServer) {
        ticks++
        fun node(at: BlockPos)=server.overworld().getBlockEntity(at) as FaceNodeEntity
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,"eln-original-$name.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun gate(at: BlockPos,index: Int,value: Double)=server.execute { val p=node(at).parts[0]!!;(server.overworld().getBlockEntity(at.relative(FaceBasis.edges(p)[index])) as FaceNodeEntity).parts[0]!!.value=value }
        fun menu()=(mc.screen as DeviceScreen).menu
        fun open(at: BlockPos)=server.execute {
            val n=node(at);val viewer=server.playerList.players.first();viewer.teleportTo(server.overworld(),at.x+.5,121.0,at.z+2.5,180f,45f);n.sync()
            net.minecraftforge.network.NetworkHooks.openScreen(viewer,net.minecraft.world.SimpleMenuProvider(
                { id,inventory,_ -> DeviceMenu(id,inventory,n,0) },net.minecraft.network.chat.Component.translatable(n.parts[0]!!.definition.translationKey))) { it.writeBlockPos(at);it.writeByte(0) }
        }
        fun camera(at: BlockPos)=server.execute { server.playerList.players.first().teleportTo(server.overworld(),at.x+.5,120.5,at.z+3.5,180f,32f) }
        fun nixiePart()=(mc.level!!.getBlockEntity(nixie) as FaceNodeEntity).parts[0]!!
        if(ticks==20)server.execute {
            val world=server.overworld();world.dayTime=6000
            for(x in 945..967)for(z in 138..152)for(y in 119..129)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==119)Blocks.STONE else Blocks.AIR).defaultBlockState())
            val viewer=server.playerList.players.first();viewer.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);viewer.inventory.clearContent();viewer.inventory.selected=8
            viewer.abilities.mayfly=true;viewer.abilities.flying=true;viewer.onUpdateAbilities()
            fun device(id: String,at: BlockPos,rotation: Int=0): FaceNodeEntity {
                val d=PartDefinition.byId.getValue(id);world.setBlockAndUpdate(at,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
                return node(at).also { it.parts[0]=PartState(d,Direction.DOWN).apply { this.rotation=rotation };it.changedTopology() }
            }
            for((at,id) in listOf(rheostat to "large_rheostat",nixie to "nixie_tube")) {
                viewer.teleportTo(world,at.x+.5,121.0,at.z+3.0,180f,45f)
                val held=ItemStack(Content.partItems.getValue(id).get());viewer.setItemInHand(InteractionHand.MAIN_HAND,held)
                require(held.useOn(UseOnContext(viewer,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(at.below()),Direction.UP,at.below(),false))).consumesAction() && held.isEmpty)
                val p=node(at).parts[0]!!;val edges=FaceBasis.edges(p)
                if(id=="large_rheostat") {
                    device("signal_source",at.relative(edges[0])).parts[0]!!.value=5.0
                    device("electrical_source",at.relative(edges[3])).parts[0]!!.value=20.0
                    device("ground_cable",at.relative(edges[1]));device("copper_thermal_cable",at.relative(edges[2]))
                }else {
                    device("signal_source",at.relative(edges[2])).parts[0]!!.value=3.7
                    device("signal_source",at.relative(edges[0])).parts[0]!!.value=2.6
                    device("signal_source",at.relative(edges[1])).parts[0]!!.value=0.0
                    device("signal_cable",at.relative(edges[3]))
                }
            }
            viewer.inventory.setItem(0,ItemStack(Content.materialItems.getValue("coal_dust").get(),24));viewer.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);viewer.inventoryMenu.broadcastChanges()
        }
        if(ticks==80)open(rheostat)
        if(ticks==110)mc.gameMode!!.handleInventoryMouseClick(menu().containerId,28,0,ClickType.QUICK_MOVE,mc.player!!)
        if(ticks==150) { require(abs(menu().part!!.value-100)<.01);image("large-rheostat-coal-hardware-gui");mc.player!!.closeContainer();camera(rheostat) }
        if(ticks==195) { image("large-rheostat-full-control-world");gate(rheostat,0,0.0) }
        if(ticks==300) {
            image("large-rheostat-minimum-control-world")
            server.execute { val n=node(rheostat);n.parts[0]!!.thermalState.putDouble("delta",500.0);val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology() }
        }
        if(ticks==350)open(rheostat)
        if(ticks==390) { require(menu().part!!.value in .98..1.01 && menu().part!!.temperature>100);image("large-rheostat-hot-thermal-gui");mc.player!!.closeContainer();camera(rheostat) }
        if(ticks==420)image("large-rheostat-warm-body-world")
        if(ticks==450)open(nixie)
        if(ticks==485) { require(menu().part!!.displayReading==7 && menu().part!!.displayBits==2 && !menu().part!!.displayBlank);image("nixie-independent-inputs-gui");mc.player!!.closeContainer();camera(nixie) }
        if(ticks==525) { image("nixie-digit-seven-upper-point-world");gate(nixie,2,0.0);gate(nixie,0,0.0) }
        if(ticks==565) { require(nixiePart().displayReading==0 && nixiePart().displayBits==0);image("nixie-digit-zero-world");gate(nixie,2,2.25);gate(nixie,0,1.3) }
        if(ticks==605) { require(nixiePart().displayReading==4 && nixiePart().displayBits==1);image("nixie-digit-four-lower-point-world");gate(nixie,2,5.0);gate(nixie,0,5.0) }
        if(ticks==645) { require(nixiePart().displayReading==9 && nixiePart().displayBits==3);image("nixie-digit-nine-both-points-world");gate(nixie,1,5.0) }
        if(ticks==685) { require(nixiePart().displayBlank);image("nixie-blank-glass-world");gate(nixie,1,0.0);server.execute { val n=node(nixie);val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology() } }
        if(ticks==730) {
            require(!nixiePart().displayBlank && nixiePart().displayReading==9 && nixiePart().displayBits==3);image("nixie-resumed-after-reload-world")
            for(id in listOf("large_rheostat","nixie_tube")) {
                val model=mc.itemRenderer.getModel(ItemStack(Content.partItems.getValue(id).get()),mc.level,mc.player,0)
                require(!model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
                require(model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            }
            Eln.LOGGER.info("ELN_CLIENT_QA original large rheostat survival placement, actual coal inventory packet, independent gate/power/thermal ports, source wiper and hot body plus Nixie independent digit/blank/point inputs, original atlas/glass and saved display/model routes confirmed")
        }
        if(ticks==760) { Eln.LOGGER.info("ELN_CLIENT_QA large rheostat and Nixie tube complete");mc.stop() }
    }
}
