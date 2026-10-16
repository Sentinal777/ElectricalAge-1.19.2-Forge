package mods.eln.modern.client

import mods.eln.Eln
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.server.IntegratedServer
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Blocks
import kotlin.math.abs

object PortableLightClientSmoke {
    private var ticks=0
    private var before=0.0
    private val source=BlockPos(533,121,115)
    private fun item(id: String,joules: Double)=ItemStack(Content.materialItems.getValue(id).get()).also { ElectricalItems.setEnergy(it,joules) }
    fun tick(mc: Minecraft,server: IntegratedServer) {
        ticks++
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun click() {
            mc.player!!.inventory.selected=0
            require(mc.player!!.mainHandItem.item==Content.materialItems.getValue("improved_flashlight").get()) { "QA click selected ${mc.player!!.mainHandItem}" }
            mc.gameMode!!.useItem(mc.player!!,InteractionHand.MAIN_HAND)
        }
        if(ticks==20)server.execute {
            val world=server.overworld();world.dayTime=18000
            for(x in 526..543)for(z in 109..122)for(y in 119..125) {
                val shell=x in setOf(526,543) || z in setOf(109,122) || y in setOf(119,125)
                world.setBlockAndUpdate(BlockPos(x,y,z),(if(shell)Blocks.STONE else Blocks.AIR).defaultBlockState())
            }
            val p=server.playerList.players.first();p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL)
            p.inventory.clearContent();p.inventory.selected=0;p.setItemInHand(InteractionHand.MAIN_HAND,item("improved_flashlight",12000.0))
            p.abilities.mayfly=true;p.abilities.flying=true;p.onUpdateAbilities();p.teleportTo(world,530.5,120.0,115.5,-90f,0f)
            p.inventoryMenu.broadcastChanges()
        }
        if(ticks==75) {
            require(server.overworld().getBrightness(LightLayer.BLOCK,source)==0)
            image("eln-original-flashlight-off-dark.png");click()
        }
        if(ticks==105)server.execute { before=ElectricalItems.energy(server.playerList.players.first().mainHandItem) }
        if(ticks==165) {
            val stack=mc.player!!.mainHandItem
            require(PortableLights.state(stack)==1) { "Normal flashlight state=${PortableLights.state(stack)}, client=$stack, server=${server.playerList.players.first().mainHandItem}" }
            require(mc.itemRenderer.getModel(stack,mc.level,mc.player,0).particleIcon.name.path.endsWith("improvedflashlighton"))
            require(server.overworld().getBrightness(LightLayer.BLOCK,BlockPos(532,121,115))==15)
            server.execute {
                val after=ElectricalItems.energy(server.playerList.players.first().mainHandItem)
                require(before-after in 50.0..70.0)
                Eln.LOGGER.info("ELN_CLIENT_QA actual carried flashlight normal mode, original dynamic sprite and finite 20 W consumption confirmed")
            }
            image("eln-original-flashlight-normal-lit.png");click()
        }
        if(ticks==205) {
            val stack=mc.player!!.mainHandItem
            require(PortableLights.state(stack)==2 && mc.itemRenderer.getModel(stack,mc.level,mc.player,0).particleIcon.name.path.endsWith("improvedflashlightboosted"))
            require(server.overworld().getBlockState(source).block==Content.lightSpot.get() && server.overworld().getBrightness(LightLayer.BLOCK,source)==15)
            image("eln-original-flashlight-boosted-lit.png")
            server.execute {
                val p=server.playerList.players.first();p.inventory.setItem(1,p.mainHandItem);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.inventoryMenu.broadcastChanges()
            }
        }
        if(ticks==245) {
            require(mc.player!!.mainHandItem.isEmpty && server.overworld().getBlockState(source).block==Content.lightSpot.get())
            image("eln-original-flashlight-carried-unselected.png")
            server.execute {
                val p=server.playerList.players.first();p.setItemInHand(InteractionHand.MAIN_HAND,p.inventory.getItem(1));p.inventory.setItem(1,ItemStack.EMPTY);p.inventoryMenu.broadcastChanges()
            }
        }
        if(ticks==275)click()
        if(ticks==315) {
            require(PortableLights.state(mc.player!!.mainHandItem)==0 && server.overworld().getBrightness(LightLayer.BLOCK,source)==0)
            image("eln-original-flashlight-off-expired.png")
            server.execute {
                val p=server.playerList.players.first();p.inventory.setItem(0,item("small_flashlight",0.0));p.inventory.setItem(1,item("portable_battery_pack",5000.0));p.inventoryMenu.broadcastChanges()
            }
        }
        if(ticks==375) {
            server.execute {
                val p=server.playerList.players.first();val lamp=ElectricalItems.energy(p.inventory.getItem(0));val battery=ElectricalItems.energy(p.inventory.getItem(1))
                require(lamp in 250.0..350.0 && abs(lamp+battery-5000)<1e-8)
                Eln.LOGGER.info("ELN_CLIENT_QA original small flashlight actual player inventory charge limit and finite 5000 J ledger confirmed")
            }
            val small=mc.player!!.mainHandItem
            for((state,suffix) in listOf(0 to "off",1 to "on",2 to "boosted")) {
                small.orCreateTag.putInt("LightState",state)
                require(mc.itemRenderer.getModel(small,mc.level,mc.player,0).particleIcon.name.path.endsWith("smallflashlight$suffix"))
            }
            small.orCreateTag.putInt("LightState",0)
        }
        if(ticks==395) {
            Eln.LOGGER.info("ELN_CLIENT_QA original powered portable lights complete")
            mc.stop()
        }
    }
}
