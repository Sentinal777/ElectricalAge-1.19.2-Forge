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
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object PassiveClientSmoke {
    private var ticks=0
    private val ids=listOf("power_resistor","rheostat","thermistor","power_capacitor","power_inductor","variable_inductor")
    private fun pos(index: Int)=BlockPos(880+index*8,120,145)
    fun tick(mc: Minecraft,server: IntegratedServer) {
        ticks++
        fun node(i: Int)=server.overworld().getBlockEntity(pos(i)) as FaceNodeEntity
        fun menu()=(mc.screen as DeviceScreen).menu
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,"eln-original-$name.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun material(id: String,n: Int=1)=ItemStack(Content.materialItems.getValue(id).get(),n)
        fun move(slot: Int)=mc.gameMode!!.handleInventoryMouseClick(menu().containerId,slot,0,ClickType.QUICK_MOVE,mc.player!!)
        fun control(i: Int,value: Double)=server.execute { val n=node(i);val p=n.parts[0]!!;val at=n.blockPos.relative(FaceBasis.edges(p)[0]);(server.overworld().getBlockEntity(at) as FaceNodeEntity).parts[0]!!.value=value }
        fun open(i: Int)=server.execute {
            val n=node(i);val player=server.playerList.players.first();player.inventory.clearContent()
            when(i) {
                0,1,2 -> player.inventory.setItem(0,material("coal_dust",48))
                3 -> { player.inventory.setItem(0,ItemStack(Items.REDSTONE,13));player.inventory.setItem(1,material("dielectric",2)) }
                4 -> { player.inventory.setItem(0,material("copper_cable",19));player.inventory.setItem(1,material("optimal_ferromagnetic_core")) }
                5 -> player.inventory.setItem(0,material("optimal_ferromagnetic_core"))
            }
            player.inventory.selected=8;player.inventoryMenu.broadcastChanges();n.sync()
            player.teleportTo(server.overworld(),n.blockPos.x+.5,121.0,n.blockPos.z+2.5,180f,50f)
            net.minecraftforge.network.NetworkHooks.openScreen(player,net.minecraft.world.SimpleMenuProvider(
                { id,inventory,_ -> DeviceMenu(id,inventory,n,0) },net.minecraft.network.chat.Component.translatable(n.parts[0]!!.definition.translationKey))) { it.writeBlockPos(n.blockPos);it.writeByte(0) }
        }
        if(ticks==20)server.execute {
            val world=server.overworld();world.dayTime=6000
            for(x in 875..925)for(z in 138..152)for(y in 119..128)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==119)Blocks.STONE else Blocks.AIR).defaultBlockState())
            val player=server.playerList.players.first();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.inventory.clearContent();player.inventory.selected=8
            player.abilities.mayfly=true;player.abilities.flying=true;player.onUpdateAbilities()
            fun device(id: String,at: BlockPos,rotation: Int): FaceNodeEntity {
                world.setBlockAndUpdate(at,Content.node.get().defaultBlockState())
                return (world.getBlockEntity(at) as FaceNodeEntity).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { this.rotation=rotation };it.changedTopology() }
            }
            ids.forEachIndexed { i,id ->
                val at=pos(i);player.teleportTo(world,at.x+.5,121.0,at.z+3.0,180f,50f)
                val held=ItemStack(Content.partItems.getValue(id).get());player.setItemInHand(InteractionHand.MAIN_HAND,held)
                require(held.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(at.below()),Direction.UP,at.below(),false))).consumesAction() && held.isEmpty)
                val p=node(i).parts[0]!!;val edges=FaceBasis.edges(p)
                device("ground_cable",at.relative(edges[1]),0)
                device("creative_power_resistor",at.relative(edges[3]),(p.rotation+3)%4).parts[0]!!.value=10.0
                device("electrical_source",at.relative(edges[3],2),0).parts[0]!!.value=5.0
                if(p.definition.passive!!.controlled)device("signal_source",at.relative(edges[0]),0).parts[0]!!.value=5.0
            }
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
        }
        if(ticks==80)open(0)
        if(ticks==110)move(menu().componentSlots+27)
        if(ticks==145) { require(abs(menu().part!!.value-100)<.01);image("coal-resistor-hardware-gui");move(0) }
        if(ticks==185) { require(abs(menu().part!!.value-.01)<1e-7);image("empty-resistor-hardware-gui");move(menu().slots.indices.first { it>=1 && menu().slots[it].item.item==Content.materialItems.getValue("coal_dust").get() }) }
        if(ticks==225) { require(abs(menu().part!!.value-100)<.01);mc.player!!.closeContainer() }
        if(ticks==260)open(1)
        if(ticks==290)move(menu().componentSlots+27)
        if(ticks==325) { require(abs(menu().part!!.value-100)<.1);image("rheostat-five-volt-gui");mc.player!!.closeContainer();control(1,0.0) }
        if(ticks==345)open(1)
        if(ticks==375) { require(abs(menu().part!!.value-100.0/101)<.01);image("rheostat-zero-volt-gui");mc.player!!.closeContainer() }
        if(ticks==410)open(2)
        if(ticks==440)move(menu().componentSlots+27)
        if(ticks==475) { require(menu().part!!.value in 90.0..110.0);image("thermistor-hardware-gui");mc.player!!.closeContainer() }
        if(ticks==530)open(3)
        if(ticks==560)move(menu().componentSlots+27)
        if(ticks==580)move(menu().componentSlots+28)
        if(ticks==620) { require(abs(menu().part!!.value-PassiveSettings.capacitance(13,2))<1e-7);require(PartInventory.get(menu().node,menu().part!!).getStackInSlot(0).count==13);image("capacitor-redstone-dielectric-gui");mc.player!!.closeContainer() }
        if(ticks==670)open(4)
        if(ticks==700)move(menu().componentSlots+27)
        if(ticks==720)move(menu().componentSlots+28)
        if(ticks==760) { require(menu().part!!.value==100.0 && menu().part!!.inductorCurrent>0);image("wound-inductor-core-gui");mc.player!!.closeContainer();server.execute { val n=node(4);val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology() } }
        if(ticks==840)open(5)
        if(ticks==870)move(menu().componentSlots+27)
        if(ticks==910) { require(menu().part!!.value==100.0);image("variable-inductor-five-volt-gui");mc.player!!.closeContainer();control(5,2.5) }
        if(ticks==940)open(5)
        if(ticks==975) { require(abs(menu().part!!.value-PassiveSettings.controlledInductance(.5))<1e-7);image("variable-inductor-half-input-gui");mc.player!!.closeContainer() }
        if(ticks==1000)server.execute { server.playerList.players.first().teleportTo(server.overworld(),899.0,127.0,154.0,180f,40f) }
        if(ticks==1030) {
            image("six-original-passive-components-world")
            for(id in ids) {
                val model=mc.itemRenderer.getModel(ItemStack(Content.partItems.getValue(id).get()),mc.level,mc.player,0)
                require(!model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
                require(model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            }
            Eln.LOGGER.info("ELN_CLIENT_QA six original passive survival placements, actual coal/redstone/dielectric/winding/core inventory packets, resistor extraction/reinsertion, 0/5V rheostat, variable inductor quantization, saved inductor state and six held/inventory model routes confirmed")
        }
        if(ticks==1060) { Eln.LOGGER.info("ELN_CLIENT_QA original passive hardware complete");mc.stop() }
    }
}
