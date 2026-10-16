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

object PortableToolClientSmoke {
    private var ticks=0
    private val quarry=BlockPos(556,121,115)
    private val tree=BlockPos(564,120,115)
    fun tick(mc: Minecraft,server: IntegratedServer) {
        ticks++
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun player()=server.playerList.players.first()
        if(ticks==20)server.execute {
            val world=server.overworld();world.dayTime=6000
            for(x in 549..572)for(z in 109..123)for(y in 119..145)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==119)Blocks.STONE else Blocks.AIR).defaultBlockState())
            for(dx in -1..1)for(dy in -1..0)for(dz in -1..1)world.setBlockAndUpdate(quarry.offset(dx,dy,dz),Blocks.COBBLESTONE.defaultBlockState())
            val p=player();p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.inventory.clearContent()
            val drill=ItemStack(Content.materialItems.getValue("portable_electrical_mining_drill").get());ElectricalItems.setEnergy(drill,3600.0)
            p.inventory.setItem(0,drill);p.inventory.selected=0;p.abilities.mayfly=true;p.abilities.flying=true;p.onUpdateAbilities()
            p.teleportTo(world,556.5,120.0,111.5,0f,5f);p.inventoryMenu.broadcastChanges()
        }
        if(ticks==70) {
            mc.player!!.inventory.selected=0
            require(mc.player!!.mainHandItem.getDestroySpeed(Blocks.COBBLESTONE.defaultBlockState())==22f)
            image("eln-original-portable-drill-before.png")
            mc.player!!.swing(InteractionHand.MAIN_HAND);mc.gameMode!!.startDestroyBlock(quarry,Direction.NORTH)
        }
        if(ticks in 71..80)mc.gameMode!!.continueDestroyBlock(quarry,Direction.NORTH)
        if(ticks==100) {
            server.execute {
                for(dx in -1..1)for(dy in -1..0)for(dz in -1..1)require(server.overworld().getBlockState(quarry.offset(dx,dy,dz)).isAir)
                require(ElectricalItems.energy(player().mainHandItem)==0.0)
                Eln.LOGGER.info("ELN_CLIENT_QA actual client mining packets excavated the original 18-block drill volume for exactly 3600 J")
            }
            image("eln-original-portable-drill-excavated.png")
            mc.gameMode!!.useItem(mc.player!!,InteractionHand.MAIN_HAND)
        }
        if(ticks==130) {
            require(mc.player!!.mainHandItem.orCreateTag.getBoolean("conservative"))
            server.execute {
                val p=player();val axe=ItemStack(Content.materialItems.getValue("portable_electrical_axe").get());ElectricalItems.setEnergy(axe,4000.0)
                p.inventory.setItem(0,axe);p.teleportTo(server.overworld(),564.5,120.0,111.5,0f,5f);p.inventoryMenu.broadcastChanges()
                for(dy in 0..11)server.overworld().setBlockAndUpdate(tree.above(dy),Blocks.OAK_LOG.defaultBlockState())
                for(dx in -2..2)for(dz in -2..2)server.overworld().setBlockAndUpdate(tree.offset(dx,12,dz),Blocks.OAK_LEAVES.defaultBlockState())
            }
        }
        if(ticks==175) {
            image("eln-original-portable-axe-before.png")
            mc.player!!.swing(InteractionHand.MAIN_HAND);mc.gameMode!!.startDestroyBlock(tree,Direction.NORTH)
        }
        if(ticks in 176..185)mc.gameMode!!.continueDestroyBlock(tree,Direction.NORTH)
        if(ticks==220) {
            server.execute {
                require((0..11).all { server.overworld().getBlockState(tree.above(it)).isAir })
                require(ElectricalItems.energy(player().mainHandItem)==1600.0)
                Eln.LOGGER.info("ELN_CLIENT_QA actual client axe packets felled twelve logs gradually for exactly 2400 J")
            }
            image("eln-original-portable-axe-felled.png");mc.gameMode!!.useItem(mc.player!!,InteractionHand.MAIN_HAND)
        }
        if(ticks==245) {
            require(!PortableTools.capitation(mc.player!!.mainHandItem))
            server.execute {
                val p=player();val battery=ItemStack(Content.materialItems.getValue("portable_battery_pack").get());ElectricalItems.setEnergy(battery,5000.0)
                p.inventory.setItem(1,battery);p.inventoryMenu.broadcastChanges()
            }
            mc.player!!.swing(InteractionHand.MAIN_HAND)
        }
        if(ticks==270)server.execute { require(ElectricalItems.energy(player().mainHandItem)==1600.0) { "Swinging axe received charge during its two-second exclusion" } }
        if(ticks==320)server.execute {
            val p=player();val tool=ElectricalItems.energy(p.mainHandItem);val donor=ElectricalItems.energy(p.inventory.getItem(1))
            require(tool>1600 && kotlin.math.abs(tool+donor-6600)<1e-8)
            Eln.LOGGER.info("ELN_CLIENT_QA actual swinging tool charging exclusion and delayed finite recharge confirmed")
        }
        if(ticks==345) { Eln.LOGGER.info("ELN_CLIENT_QA original powered drill and axe complete");mc.stop() }
    }
}
