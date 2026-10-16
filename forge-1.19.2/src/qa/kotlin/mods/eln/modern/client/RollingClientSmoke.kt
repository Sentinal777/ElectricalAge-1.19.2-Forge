package mods.eln.modern.client

import mods.eln.Eln
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.server.IntegratedServer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks

object RollingClientSmoke {
    private var ticks=0
    private var blockedEnergy=0.0
    fun tick(mc: Minecraft,server: IntegratedServer) {
        ticks++
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        if(ticks==20)server.execute {
            val world=server.overworld();world.dayTime=6000;world.setWeatherParameters(6000,0,false,false)
            for(x in 367..378)for(z in 111..122) {
                for(y in 120..180)world.setBlockAndUpdate(BlockPos(x,y,z),Blocks.AIR.defaultBlockState())
                world.setBlockAndUpdate(BlockPos(x,119,z),Blocks.STONE.defaultBlockState())
            }
            fun device(id: String,z: Int): FaceNodeEntity {
                val pos=BlockPos(370,120,z);world.setBlockAndUpdate(pos,Content.machine.get().defaultBlockState())
                return (world.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { shaftGroup="live-rolling-primer" };it.changedTopology() }
            }
            val roller=device("rolling_shaft",115);val a=device("flywheel",116);device("flywheel",117)
            a.parts[0]!!.shaftEnergy=100000.0;a.changedTopology();roller.inventory.setStackInSlot(0,ItemStack(Items.IRON_INGOT,6))
            val viewer=server.playerList.players.first();viewer.setGameMode(net.minecraft.world.level.GameType.CREATIVE)
            viewer.abilities.flying=true;viewer.onUpdateAbilities();viewer.inventory.setItem(2,ItemStack.EMPTY);viewer.inventory.selected=2;viewer.inventoryMenu.broadcastChanges()
            viewer.teleportTo(world,370.5,121.8,114.0,0f,60f)
        }
        if(ticks==90)server.execute {
            val viewer=server.playerList.players.first();val node=server.overworld().getBlockEntity(BlockPos(370,120,115)) as FaceNodeEntity;node.sync()
            net.minecraftforge.network.NetworkHooks.openScreen(viewer,net.minecraft.world.SimpleMenuProvider(
                { id,inventory,_ -> DeviceMenu(id,inventory,node,0) },net.minecraft.network.chat.Component.translatable(node.parts[0]!!.definition.translationKey))) { it.writeBlockPos(node.blockPos);it.writeByte(0) }
        }
        if(ticks==150) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(menu.node.inventory.getStackInSlot(2).count>=2 && p.energy>20000 && p.active && p.thermalState.getDouble("rollingWatts")==4000.0 && ClientAudio.activeAt(menu.node)>0)
            image("eln-original-rolling-shaft-working-gui.png");Eln.LOGGER.info("ELN_CLIENT_QA original rolling inventory, finite 4000 W work, moving drums and actual sound confirmed")
        }
        if(ticks==180)server.execute {
            val n=server.overworld().getBlockEntity(BlockPos(370,120,115)) as FaceNodeEntity
            require(n.inventory.getStackInSlot(0).count>0)
            n.inventory.setStackInSlot(2,ItemStack(Content.materialItems.getValue("iron_plate").get(),64));blockedEnergy=n.parts[0]!!.energy
        }
        if(ticks==240) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(p.energy==blockedEnergy && menu.node.inventory.getStackInSlot(0).count>0 && !p.active && ClientAudio.activeAt(menu.node)>0)
            image("eln-original-rolling-shaft-blocked-gui.png");Eln.LOGGER.info("ELN_CLIENT_QA blocked rolling output consumed no process energy while its real shaft kept spinning")
        }
        if(ticks==260)server.execute {
            val n=server.overworld().getBlockEntity(BlockPos(370,120,115)) as FaceNodeEntity
            n.inventory.setStackInSlot(2,ItemStack.EMPTY);n.inventory.setStackInSlot(0,ItemStack(Content.materialItems.getValue("silicon_ingot").get(),4))
        }
        if(ticks==340) {
            val menu=(mc.screen as DeviceScreen).menu
            require(menu.node.inventory.getStackInSlot(0).isEmpty && menu.node.inventory.getStackInSlot(2).`is`(Content.materialItems.getValue("silicon_plate").get()) && menu.node.inventory.getStackInSlot(2).count==1)
            image("eln-original-rolling-silicon-recipe-gui.png");Eln.LOGGER.info("ELN_CLIENT_QA rolling four original silicon ingots produced one real plate")
        }
        if(ticks==380) { mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),374.0,123.0,121.0,145f,25f) } }
        if(ticks==440)image("eln-original-rolling-shaft-world.png")
        if(ticks==480) { Eln.LOGGER.info("ELN_CLIENT_QA original rolling shaft machine complete");mc.stop() }
    }
}
