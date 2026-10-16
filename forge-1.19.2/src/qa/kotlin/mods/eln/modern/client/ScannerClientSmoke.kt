package mods.eln.modern.client

import mods.eln.Eln
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.renderer.block.model.ItemTransforms
import net.minecraft.client.server.IntegratedServer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks

object ScannerClientSmoke {
    private var ticks=0
    private var idleEnergy=0.0
    private val flower=BlockPos(620,120,117)
    fun tick(mc: Minecraft,server: IntegratedServer) {
        ticks++
        fun p()=server.playerList.players.first()
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun click() { mc.player!!.inventory.selected=0;mc.gameMode!!.useItem(mc.player!!,InteractionHand.MAIN_HAND) }
        if(ticks==20)server.execute {
            val world=server.overworld();world.dayTime=6000
            for(x in 612..633)for(z in 109..127)for(y in 119..126)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==119)Blocks.STONE else Blocks.AIR).defaultBlockState())
            for(x in 618..622)for(y in 120..123)world.setBlockAndUpdate(BlockPos(x,y,118),Blocks.STONE.defaultBlockState())
            for(x in 619..621)for(y in 120..122)world.setBlockAndUpdate(BlockPos(x,y,119),Blocks.DIAMOND_ORE.defaultBlockState())
            val p=p();p.inventory.clearContent();p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL)
            for(slot in net.minecraft.world.entity.EquipmentSlot.values())if(slot.type==net.minecraft.world.entity.EquipmentSlot.Type.ARMOR)p.setItemSlot(slot,ItemStack.EMPTY)
            p.inventory.selected=0;p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Content.materialItems.getValue("x_ray_scanner").get()))
            p.abilities.mayfly=true;p.abilities.flying=true;p.onUpdateAbilities();p.teleportTo(world,620.5,120.0,115.5,0f,0f);p.inventoryMenu.broadcastChanges()
        }
        if(ticks==55)mc.player!!.inventory.selected=0
        if(ticks==70) { require(PortableScanner.state(mc.player!!.mainHandItem)==PortableScanner.BOOT);image("eln-original-xray-scanner-boot.png") }
        if(ticks==140) {
            val stack=mc.player!!.mainHandItem
            require(PortableScanner.state(stack)==PortableScanner.RUN)
            val model=mc.itemRenderer.getModel(stack,mc.level,mc.player,0)
            require(!model.applyTransform(ItemTransforms.TransformType.GUI,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            require(model.applyTransform(ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            val pixels=ScannerImage.sample(mc.level!!,mc.player!!.eyePosition,0.0,0.0,5.0)
            require(pixels[10][16].green>.9)
            image("eln-original-xray-scanner-hidden-ore.png")
            Eln.LOGGER.info("ELN_CLIENT_QA actual scanner boot, original 3D held model, flat inventory sprite and hidden diamond ray integration confirmed")
        }
        if(ticks==165)server.execute { p().teleportTo(server.overworld(),620.5,120.0,115.5,180f,0f) }
        if(ticks==200) {
            require(ScannerImage.sample(mc.level!!,mc.player!!.eyePosition,mc.player!!.yRot.toDouble(),0.0,5.0)[10][16].green==0.0)
            image("eln-original-xray-scanner-empty-direction.png")
        }
        if(ticks==215)server.execute { p().teleportTo(server.overworld(),620.5,120.0,115.5,0f,0f) }
        if(ticks==235)click()
        if(ticks==245) { require(PortableScanner.state(mc.player!!.mainHandItem)==PortableScanner.STOP);image("eln-original-xray-scanner-shutdown.png") }
        if(ticks==275) {
            require(PortableScanner.state(mc.player!!.mainHandItem)==PortableScanner.IDLE)
            image("eln-original-xray-scanner-off.png");server.execute { idleEnergy=ElectricalItems.energy(p().mainHandItem) }
        }
        if(ticks==315) {
            server.execute { require(ElectricalItems.energy(p().mainHandItem)==idleEnergy);Eln.LOGGER.info("ELN_CLIENT_QA actual scanner use packets completed one-second shutdown and stopped finite operating consumption") }
            click()
        }
        for(index in 0 until 12) {
            if(ticks==440+index*8)server.execute {
                server.overworld().setBlockAndUpdate(flower.below(),Blocks.DIRT.defaultBlockState())
                server.overworld().setBlockAndUpdate(flower,Blocks.DANDELION.defaultBlockState())
            }
            if(ticks==444+index*8) { mc.player!!.swing(InteractionHand.MAIN_HAND);mc.gameMode!!.startDestroyBlock(flower,Direction.NORTH) }
        }
        if(ticks==470 || ticks==494 || ticks==518)image("eln-original-xray-scanner-damage-${(ticks-446)/24}.png")
        if(ticks==555) {
            val stack=mc.player!!.mainHandItem
            require(PortableScanner.damage(stack)==12 && PortableScanner.state(stack)==PortableScanner.IDLE) { "Scanner damage=${PortableScanner.damage(stack)}, state=${PortableScanner.state(stack)}" }
            image("eln-original-xray-scanner-broken.png")
            Eln.LOGGER.info("ELN_CLIENT_QA twelve actual client block breaks produced original scanner damage stages and stopped the broken scanner")
            click()
        }
        if(ticks==580) { require(PortableScanner.state(mc.player!!.mainHandItem)==PortableScanner.IDLE);Eln.LOGGER.info("ELN_CLIENT_QA original X-Ray Scanner complete");mc.stop() }
    }
}
