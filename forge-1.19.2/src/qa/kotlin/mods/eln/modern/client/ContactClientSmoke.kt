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

object ContactClientSmoke {
    private var ticks=0
    private fun pos(x: Int,z: Int)=BlockPos(x,120,z)
    fun tick(mc: Minecraft,server: IntegratedServer) {
        ticks++
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun node(x: Int,z: Int)=server.overworld().getBlockEntity(pos(x,z)) as FaceNodeEntity
        fun gate(x: Int,z: Int)=(server.overworld().getBlockEntity(pos(x,z).relative(FaceBasis.edges(node(x,z).parts[0]!!)[0])) as FaceNodeEntity).parts[0]!!
        fun menu()=(mc.screen as DeviceScreen).menu
        fun button()=(mc.screen as DeviceScreen).children().filterIsInstance<Button>().first().onPress()
        fun open(x: Int,z: Int)=server.execute {
            val n=node(x,z);n.sync();val viewer=server.playerList.players.first();viewer.teleportTo(server.overworld(),x-.5,121.0,z+2.5,180f,50f)
            net.minecraftforge.network.NetworkHooks.openScreen(viewer,net.minecraft.world.SimpleMenuProvider(
                { id,inventory,_ -> DeviceMenu(id,inventory,n,0) },net.minecraft.network.chat.Component.translatable(n.parts[0]!!.definition.translationKey))) { it.writeBlockPos(n.blockPos);it.writeByte(0) }
        }
        fun use(x: Int,z: Int)=mc.gameMode!!.useItemOn(mc.player!!,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(pos(x,z)),Direction.UP,pos(x,z),false))
        if(ticks==20)server.execute {
            val world=server.overworld();world.dayTime=6000
            for(x in 795..838)for(z in 140..171)for(y in 119..129)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==119)Blocks.STONE else Blocks.AIR).defaultBlockState())
            val viewer=server.playerList.players.first();viewer.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);viewer.inventory.clearContent();viewer.inventory.selected=8
            viewer.abilities.mayfly=true;viewer.abilities.flying=true;viewer.onUpdateAbilities()
            fun device(id: String,at: BlockPos,rotation: Int=0,face: Direction=Direction.DOWN): FaceNodeEntity {
                world.setBlockAndUpdate(at.relative(face),Blocks.STONE.defaultBlockState());world.setBlockAndUpdate(at,Content.node.get().defaultBlockState())
                return (world.getBlockEntity(at) as FaceNodeEntity).also { it.parts[face.ordinal]=PartState(PartDefinition.byId.getValue(id),face).apply { this.rotation=rotation };it.changedTopology() }
            }
            val rows=listOf(
                145 to listOf("low_voltage_switch","medium_voltage_switch","high_voltage_switch","very_high_voltage_switch"),
                155 to listOf("low_voltage_relay","medium_voltage_relay","high_voltage_relay","very_high_voltage_relay","signal_relay"),
                165 to listOf("low_current_relay","medium_current_relay","high_current_relay"))
            for((z,ids) in rows)for((i,id) in ids.withIndex()) {
                val x=800+i*8;val at=pos(x,z);viewer.teleportTo(world,x+.5,121.0,z+3.0,180f,50f)
                val held=ItemStack(Content.partItems.getValue(id).get());viewer.setItemInHand(InteractionHand.MAIN_HAND,held)
                require(held.useOn(UseOnContext(viewer,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(at.below()),Direction.UP,at.below(),false))).consumesAction() && held.isEmpty)
                val p=node(x,z).parts[0]!!;val d=p.definition;val spec=d.contact!!;val edges=FaceBasis.edges(p)
                val power=if(spec.currentThermal)50.0 else d.nominalVoltage
                val a=if(spec.relay)1 else 0;val b=if(spec.relay)3 else 2
                device(if(spec.signal)"signal_source" else "electrical_source",at.relative(edges[a])).parts[0]!!.value=power
                if(spec.signal)device("signal_cable",at.relative(edges[b])) else {
                    val r=device("creative_power_resistor",at.relative(edges[b]),(p.rotation+a)%4).parts[0]!!;r.value=if(spec.currentThermal)100.0 else power*power/50
                    device("ground_cable",at.relative(edges[b],2))
                }
                if(spec.relay)device("signal_source",at.relative(edges[0]))
            }
            device("standard_alarm",BlockPos(832,121,168),rotation=1,face=Direction.NORTH).parts[Direction.NORTH.ordinal]!!.enabled=false
            device("signal_source",BlockPos(832,120,168),face=Direction.NORTH)
            device("electrical_anemometer_sensor",BlockPos(836,121,168),face=Direction.NORTH)
            viewer.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);viewer.inventoryMenu.broadcastChanges()
            viewer.teleportTo(world,800.5,121.0,147.5,180f,50f)
        }
        if(ticks==110)use(800,145)
        if(ticks==150) {
            require((mc.level!!.getBlockEntity(pos(800,145)) as FaceNodeEntity).parts[0]!!.active)
            image("eln-original-low-voltage-switch-active-world.png");open(800,145)
        }
        if(ticks==190) { require(menu().part!!.active && menu().part!!.current>.9);image("eln-original-low-voltage-switch-gui.png");button() }
        if(ticks==220) { require(!menu().part!!.active);image("eln-original-low-voltage-switch-open-gui.png");mc.player!!.closeContainer();open(800,155) }
        if(ticks==260) { require(menu().part!!.active && !menu().part!!.enabled && menu().part!!.connectedMask==11);image("eln-original-low-voltage-relay-normally-open-gui.png");button() }
        if(ticks==290) {
            require(!menu().part!!.active && menu().part!!.enabled);image("eln-original-low-voltage-relay-high-input-nc-gui.png")
            server.execute { gate(800,155).value=0.0 }
        }
        if(ticks==325) { require(menu().part!!.active && menu().part!!.enabled);image("eln-original-low-voltage-relay-low-input-nc-gui.png");mc.player!!.closeContainer();open(832,155) }
        if(ticks==365) { require(menu().part!!.active && menu().part!!.pinVoltages[3]>4.99);image("eln-original-signal-relay-gui.png");button() }
        if(ticks==395) { require(!menu().part!!.active && menu().part!!.pinVoltages[3]<.001);image("eln-original-signal-relay-open-gui.png");mc.player!!.closeContainer();open(800,165) }
        if(ticks==435) { require(menu().part!!.active && menu().part!!.current in .48.. .51 && menu().part!!.thermalState.getDouble("capacity")>0);image("eln-original-low-current-relay-gui.png");mc.player!!.closeContainer();open(808,165) }
        if(ticks==475) { require(menu().part!!.active && menu().part!!.definition.maxCurrent==20.0);image("eln-original-medium-current-relay-gui.png");mc.player!!.closeContainer();open(816,165) }
        if(ticks==515) {
            require(menu().part!!.active && menu().part!!.definition.maxCurrent==100.0);image("eln-original-high-current-relay-gui.png");mc.player!!.closeContainer()
            server.execute { server.playerList.players.first().teleportTo(server.overworld(),824.5,121.0,147.5,180f,45f) }
        }
        if(ticks==550)use(824,145)
        if(ticks==585) {
            require((mc.level!!.getBlockEntity(pos(824,145)) as FaceNodeEntity).parts[0]!!.active)
            image("eln-original-very-high-voltage-switch-active-world.png")
            server.execute { server.playerList.players.first().teleportTo(server.overworld(),816.5,128.0,177.0,180f,35f) }
        }
        if(ticks==620) { image("eln-original-twelve-contacts-world.png");server.execute { server.playerList.players.first().teleportTo(server.overworld(),834.4,122.2,171.0,180f,8f) } }
        if(ticks in listOf(655,662,670))image("eln-original-synchronized-beacon-anemometer-motion-$ticks.png")
        if(ticks==695) {
            val definitions=PartDefinition.all.filter { it.contact!=null };require(definitions.size==12)
            for(d in definitions) {
                val model=mc.itemRenderer.getModel(ItemStack(Content.partItems.getValue(d.id).get()),mc.level,mc.player,0)
                require(!model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
                require(model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            }
            Eln.LOGGER.info("ELN_CLIENT_QA all twelve original contact survival placements and held/inventory routes, actual manual-switch interaction packets, separate relay terminals, normally-open/closed GUI packets, signal isolation and three current-relay live thermal screens confirmed")
        }
        if(ticks==720) { Eln.LOGGER.info("ELN_CLIENT_QA original switches and relays complete");mc.stop() }
    }
}
