package mods.eln.modern.client

import mods.eln.Eln
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.server.IntegratedServer
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import kotlin.math.abs

object ArmorClientSmoke {
    private var ticks=0
    private val names=listOf("helmet","chestplate","leggings","boots")
    private val slots=listOf(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)
    fun tick(mc: Minecraft,server: IntegratedServer) {
        ticks++
        fun p()=server.playerList.players.first()
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun give(prefix: String)=server.execute {
            val p=p();p.inventory.clearContent()
            for(i in names.indices) {
                p.setItemSlot(slots[i],ItemStack.EMPTY)
                val stack=ItemStack(Content.materialItems.getValue("${prefix}_${names[i]}").get());ElectricalItems.setEnergy(stack,8000.0)
                p.inventory.setItem(i,stack)
            }
            p.inventoryMenu.broadcastChanges()
        }
        fun equip(index: Int) { mc.player!!.inventory.selected=index;mc.gameMode!!.useItem(mc.player!!,InteractionHand.MAIN_HAND) }
        if(ticks==20) {
            server.execute {
                val world=server.overworld();world.dayTime=6000
                world.gameRules.getRule(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION).set(false,server)
                for(x in 588..605)for(z in 109..124)for(y in 119..125)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==119)Blocks.STONE else Blocks.AIR).defaultBlockState())
                val p=p();p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.health=20f;p.foodData.foodLevel=20
                p.teleportTo(world,596.5,120.0,115.5,0f,0f)
            }
            give("copper")
        }
        for(i in names.indices)if(ticks==60+i*20)equip(i)
        if(ticks==160) {
            require(mc.player!!.armorSlots.all { it.item is PortArmorItem } && mc.player!!.armorValue==15)
            mc.setScreen(InventoryScreen(mc.player!!))
        }
        if(ticks==190) { image("eln-original-copper-armor-inventory.png");Eln.LOGGER.info("ELN_CLIENT_QA four original copper armor pieces equipped through actual client item-use packets") }
        if(ticks==210) { mc.player!!.closeContainer();give("e_coal") }
        for(i in names.indices)if(ticks==240+i*20)equip(i)
        if(ticks==340) {
            require(mc.player!!.armorSlots.sumOf { ArmorRuntime.display(it) }==20 && mc.player!!.armorValue==0)
            image("eln-original-ecoal-full-shield-hud.png");mc.setScreen(InventoryScreen(mc.player!!))
        }
        if(ticks==370)image("eln-original-ecoal-armor-inventory.png")
        if(ticks==390) {
            mc.player!!.closeContainer()
            server.execute { val p=p();p.invulnerableTime=0;p.hurt(net.minecraft.world.damagesource.DamageSource.GENERIC,10f) }
        }
        if(ticks==420)server.execute {
            require(p().health==20f && p().armorSlots.sumOf(ElectricalItems::energy)==27500.0)
            Eln.LOGGER.info("ELN_CLIENT_QA actual player E-Coal shield absorbed ten incoming damage for original 4500 J item damage charge")
            p().armorSlots.forEach { ElectricalItems.setEnergy(it,0.0) };p().invulnerableTime=0;p().hurt(net.minecraft.world.damagesource.DamageSource.GENERIC,2f)
        }
        if(ticks==450) {
            require(mc.player!!.health==18f && mc.player!!.armorSlots.sumOf { ArmorRuntime.display(it) }==0) { "Empty shield: client health=${mc.player!!.health}, display=${mc.player!!.armorSlots.sumOf { ArmorRuntime.display(it) }}, server health=${p().health}" }
            image("eln-original-ecoal-empty-shield-hud.png")
            server.execute {
                val battery=ItemStack(Content.materialItems.getValue("portable_battery_pack").get());ElectricalItems.setEnergy(battery,5000.0)
                p().inventory.setItem(4,battery);p().inventoryMenu.broadcastChanges()
            }
        }
        if(ticks==510)server.execute {
            val armor=p().armorSlots.sumOf(ElectricalItems::energy);val donor=ElectricalItems.energy(p().inventory.getItem(4))
            require(armor in 2500.0..3500.0 && abs(armor+donor-5000)<1e-8)
            Eln.LOGGER.info("ELN_CLIENT_QA actually worn E-Coal armor received finite rate-limited inventory priority charge")
            server.overworld().gameRules.getRule(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION).set(true,server)
        }
        if(ticks==535) { image("eln-original-ecoal-recharging-shield-hud.png");Eln.LOGGER.info("ELN_CLIENT_QA original copper and electrical armor complete");mc.stop() }
    }
}
