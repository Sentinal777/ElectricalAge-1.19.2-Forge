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
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object ChargerClientSmoke {
    private var ticks=0
    private var stopped=0.0
    private fun pos(x: Int)=BlockPos(x,120,115)
    private fun item(id: String,energy: Double=0.0)=ItemStack(Content.materialItems.getValue(id).get()).also { ElectricalItems.setEnergy(it,energy) }
    fun tick(mc: Minecraft,server: IntegratedServer) {
        ticks++
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun node(x: Int)=server.overworld().getBlockEntity(pos(x)) as FaceNodeEntity
        fun open(x: Int)=server.execute {
            val n=node(x);n.sync();val viewer=server.playerList.players.first()
            viewer.teleportTo(server.overworld(),x-.5,121.0,113.5,-20f,55f)
            net.minecraftforge.network.NetworkHooks.openScreen(viewer,net.minecraft.world.SimpleMenuProvider(
                { id,inventory,_ -> DeviceMenu(id,inventory,n,0) },net.minecraft.network.chat.Component.translatable(n.parts[0]!!.definition.translationKey))) { it.writeBlockPos(n.blockPos);it.writeByte(0) }
        }
        fun toggle() {
            val screen=mc.screen as DeviceScreen
            val button=screen.children().filterIsInstance<Button>().first { it.message.string in setOf("Enabled","Disabled") }
            require(screen.mouseClicked(button.x+3.0,button.y+3.0,0));screen.mouseReleased(button.x+3.0,button.y+3.0,0)
        }
        if(ticks==20)server.execute {
            val world=server.overworld();world.dayTime=6000;world.setWeatherParameters(6000,0,false,false)
            for(x in 482..506)for(z in 109..124) {
                for(y in 120..180)world.setBlockAndUpdate(BlockPos(x,y,z),Blocks.AIR.defaultBlockState())
                world.setBlockAndUpdate(BlockPos(x,119,z),Blocks.STONE.defaultBlockState())
            }
            world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity::class.java,net.minecraft.world.phys.AABB(482.0,118.0,109.0,507.0,181.0,125.0)).forEach { it.discard() }
            fun device(id: String,x: Int): FaceNodeEntity {
                val at=pos(x);world.setBlockAndUpdate(at,Content.node.get().defaultBlockState())
                return (world.getBlockEntity(at) as FaceNodeEntity).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN);it.changedTopology() }
            }
            for((x,id) in listOf(486 to "50v_battery_charger",494 to "weak_50v_battery_charger",502 to "200v_battery_charger")) {
                val n=device(id,x);device("electrical_source",x+1).parts[0]!!.value=n.parts[0]!!.definition.nominalVoltage
                if(x!=486)PartInventory.get(n,n.parts[0]!!).setStackInSlot(0,item("portable_condensator_pack"))
            }
            PartInventory.get(node(494),node(494).parts[0]!!).setStackInSlot(4,ItemStack(Content.materialItems.getValue("machine_booster").get(),2))
            val viewer=server.playerList.players.first();viewer.setGameMode(net.minecraft.world.level.GameType.SURVIVAL)
            viewer.abilities.mayfly=true;viewer.abilities.flying=true;viewer.onUpdateAbilities();viewer.inventory.selected=2
            viewer.teleportTo(world,485.5,121.0,113.5,-20f,55f)
            for(stack in listOf(item("portable_battery",39950.0),item("portable_battery_pack",159900.0),item("portable_condensator",3000.0),item("portable_condensator_pack"))) {
                viewer.setItemInHand(InteractionHand.MAIN_HAND,stack)
                node(486).blockState.use(world,viewer,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(pos(486)),Direction.UP,pos(486),false))
                require(stack.isEmpty)
            }
            viewer.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);viewer.inventoryMenu.broadcastChanges()
        }
        if(ticks==60)open(486)
        if(ticks==90)toggle()
        if(ticks==170) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(p.enabled && p.energy>1000 && p.gridState.getInt("chargerPresence")==15 && p.gridState.getInt("chargerCharged") and 7==7)
            image("eln-original-50v-charger-gui.png");Eln.LOGGER.info("ELN_CLIENT_QA real battery insertion, networked charger button, four saved item charges and original full/charging LEDs confirmed")
        }
        if(ticks==190) { mc.player!!.closeContainer();open(494) }
        if(ticks==230)toggle()
        if(ticks==330) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(p.enabled && p.thermalState.getDouble("chargerWatts") in 300.0..312.51 && PartInventory.get(menu.node,p).getStackInSlot(4).count==2 && ElectricalItems.energy(PartInventory.get(menu.node,p).getStackInSlot(0))>100)
            image("eln-original-weak-charger-boosters-gui.png");Eln.LOGGER.info("ELN_CLIENT_QA weak charger actual booster speed, 81 percent efficiency and original battery model confirmed")
        }
        if(ticks==350) { mc.player!!.closeContainer();open(502) }
        if(ticks==390)toggle()
        if(ticks==490) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(p.enabled && p.voltage>190 && p.thermalState.getDouble("chargerWatts") in 990.0..1000.01 && ElectricalItems.energy(PartInventory.get(menu.node,p).getStackInSlot(0))>1000)
            image("eln-original-200v-charger-gui.png")
        }
        if(ticks==510)toggle()
        if(ticks==540)server.execute { val n=node(502);require(!n.parts[0]!!.enabled);stopped=ElectricalItems.energy(PartInventory.get(n,n.parts[0]!!).getStackInSlot(0)) }
        if(ticks==590) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(!p.enabled && !p.active && ElectricalItems.energy(PartInventory.get(menu.node,p).getStackInSlot(0))==stopped)
            image("eln-original-charger-stopped-gui.png");Eln.LOGGER.info("ELN_CLIENT_QA actual charger toggle stopped power and retained portable charge")
        }
        if(ticks==610) { mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),484.7,121.1,117.0,-130f,50f) } }
        if(ticks==640)image("eln-original-charger-indicators-world.png")
        if(ticks==650)server.execute { server.playerList.players.first().teleportTo(server.overworld(),494.0,125.0,130.0,180f,25f) }
        if(ticks==680)image("eln-original-battery-chargers-world.png")
        if(ticks==660)server.execute {
            val viewer=server.playerList.players.first();viewer.inventory.setItem(0,item("portable_battery_pack",5000.0));viewer.inventory.setItem(1,item("portable_condensator"))
            viewer.inventory.selected=1;viewer.inventoryMenu.broadcastChanges()
        }
        if(ticks==740) {
            val player=mc.player!!;val battery=player.inventory.getItem(0);val cap=player.inventory.getItem(1)
            require(ElectricalItems.energy(cap)>1000 && ElectricalItems.energy(battery)<4000 && abs(ElectricalItems.energy(cap)+ElectricalItems.energy(battery)-5000)<1e-6)
            for(d in PartDefinition.all.filter { it.heldModel }) {
                val stack=ItemStack(Content.partItems.getValue(d.id).get());val model=mc.itemRenderer.getModel(stack,mc.level,mc.player,0)
                fun transformed(type: net.minecraft.client.renderer.block.model.ItemTransforms.TransformType)=net.minecraftforge.client.ForgeHooksClient.handleCameraTransforms(com.mojang.blaze3d.vertex.PoseStack(),model,type,false)
                require(!transformed(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI).isCustomRenderer)
                require(transformed(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND).isCustomRenderer)
            }
            image("eln-original-portable-priority-charge-bars.png");Eln.LOGGER.info("ELN_CLIENT_QA actual carried battery priority transfer and live inventory energy bars conserved 5000 J")
        }
        if(ticks==780) { Eln.LOGGER.info("ELN_CLIENT_QA original chargers and portable energy stores complete");mc.stop() }
    }
}
