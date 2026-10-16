package mods.eln.modern

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

data class BulbSpec(val type: String,val socket: String,val nominalLight: Double,val hours: Double,val growRate: Double=0.0,val range: Int=0)
data class ClutchPlateSpec(val maximumStatic: Double,val minimumStatic: Double,val maximumDynamic: Double,val minimumDynamic: Double,val wearSpeed: Double,val explodes: Boolean=false)
data class DrillSpec(val operationTime: Double,val operationEnergy: Double)
data class MaterialDefinition(val id: String, val name: String, val legacyId: Int, val icon: String,
    val kind: String="MATERIAL",val value: Double=0.0,val maximumTemperature: Double=0.0,val variant: Int=-1,val nominalVoltage: Double=0.0,val bulb: BulbSpec?=null,val clutchPlate: ClutchPlateSpec?=null,val electrical: ElectricalItemSpec?=null,val drill: DrillSpec?=null,val armor: ArmorSpec?=null,val fuseCable: String?=null,val hidden: Boolean=false)
class MaterialItem(val definition: MaterialDefinition,properties: net.minecraft.world.item.Item.Properties): net.minecraft.world.item.Item(properties) {
    override fun shouldCauseReequipAnimation(oldStack: net.minecraft.world.item.ItemStack,newStack: net.minecraft.world.item.ItemStack,slotChanged: Boolean)=
        if(definition.electrical!=null)slotChanged || oldStack.item!=newStack.item else super.shouldCauseReequipAnimation(oldStack,newStack,slotChanged)
    override fun shouldCauseBlockBreakReset(oldStack: net.minecraft.world.item.ItemStack,newStack: net.minecraft.world.item.ItemStack)=
        if(definition.electrical!=null)oldStack.item!=newStack.item else super.shouldCauseBlockBreakReset(oldStack,newStack)
    override fun initializeClient(consumer: java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions>) {
        consumer.accept(object : net.minecraftforge.client.extensions.common.IClientItemExtensions {
            override fun getCustomRenderer()=when(this@MaterialItem.definition.kind) {
                "DATA_LOG_PRINT" -> mods.eln.modern.client.DataLogPrintRenderer.instance
                "FUSE" -> mods.eln.modern.client.FuseItemRenderer.instance
                "BRUSH" -> mods.eln.modern.client.BrushItemRenderer.instance
                else -> mods.eln.modern.client.ScannerItemRenderer.instance
            }
        })
    }
    override fun getName(stack: net.minecraft.world.item.ItemStack): net.minecraft.network.chat.Component = if(definition.kind=="BRUSH" && definition.variant==15 && Brushes.life(stack)==0 && !ClientNodeHooks.creative())net.minecraft.network.chat.Component.translatable("item.eln.empty_white_brush")else super.getName(stack)
    override fun onBlockStartBreak(stack: net.minecraft.world.item.ItemStack,pos: net.minecraft.core.BlockPos,player: net.minecraft.world.entity.player.Player): Boolean {
        if(!player.level.isClientSide)PortableScanner.strike(stack)
        return false
    }
    override fun onDroppedByPlayer(stack: net.minecraft.world.item.ItemStack,player: net.minecraft.world.entity.player.Player): Boolean { PortableScanner.dropped(stack);return true }
    override fun getDestroySpeed(stack: net.minecraft.world.item.ItemStack,state: net.minecraft.world.level.block.state.BlockState)=if(definition.electrical?.tool!=null)PortableTools.speed(stack,state)else super.getDestroySpeed(stack,state)
    override fun isCorrectToolForDrops(state: net.minecraft.world.level.block.state.BlockState)=definition.electrical?.tool!=null || super.isCorrectToolForDrops(state)
    override fun isCorrectToolForDrops(stack: net.minecraft.world.item.ItemStack,state: net.minecraft.world.level.block.state.BlockState)=definition.electrical?.tool!=null || super.isCorrectToolForDrops(stack,state)
    override fun mineBlock(stack: net.minecraft.world.item.ItemStack,level: net.minecraft.world.level.Level,state: net.minecraft.world.level.block.state.BlockState,pos: net.minecraft.core.BlockPos,entity: net.minecraft.world.entity.LivingEntity)=if(definition.electrical?.tool!=null)PortableTools.mined(stack,level,state,pos,entity)else super.mineBlock(stack,level,state,pos,entity)
    override fun onEntitySwing(stack: net.minecraft.world.item.ItemStack,entity: net.minecraft.world.entity.LivingEntity): Boolean { PortableTools.swing(stack,entity);return false }
    override fun use(level: net.minecraft.world.level.Level,player: net.minecraft.world.entity.player.Player,hand: net.minecraft.world.InteractionHand): net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> {
        val stack=player.getItemInHand(hand)
        if(definition.electrical?.scanner!=null) {
            if(!level.isClientSide) { PortableScanner.toggle(stack);player.displayClientMessage(net.minecraft.network.chat.Component.translatable(PortableScanner.modeKey(stack)),true) }
            return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack,level.isClientSide)
        }
        if(definition.electrical?.tool!=null) {
            if(!level.isClientSide)player.displayClientMessage(net.minecraft.network.chat.Component.translatable(PortableTools.toggle(stack)),true)
            return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack,level.isClientSide)
        }
        if(definition.electrical?.flashlight==null)return super.use(level,player,hand)
        if(!level.isClientSide && ElectricalItems.energy(stack)>0) {
            val state=(PortableLights.state(stack)+1)%3;stack.orCreateTag.putInt("LightState",state)
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(PortableLights.modeKey(state)),true)
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack,level.isClientSide)
    }
    override fun inventoryTick(stack: net.minecraft.world.item.ItemStack,level: net.minecraft.world.level.Level,entity: net.minecraft.world.entity.Entity,slot: Int,selected: Boolean) {
        if(level is net.minecraft.server.level.ServerLevel && definition.electrical?.flashlight!=null)PortableLights.project(stack,level,entity)
        if(level is net.minecraft.server.level.ServerLevel && definition.electrical?.scanner!=null)PortableScanner.tick(stack)
    }
    override fun isBarVisible(stack: net.minecraft.world.item.ItemStack)=definition.electrical!=null
    override fun getBarWidth(stack: net.minecraft.world.item.ItemStack)=definition.electrical?.let { (13*ElectricalItems.energy(stack)/it.capacity).toInt().coerceIn(0,13) } ?: 0
    override fun getBarColor(stack: net.minecraft.world.item.ItemStack)=definition.electrical?.let { net.minecraft.util.Mth.hsvToRgb((ElectricalItems.energy(stack)/it.capacity/3).toFloat(),1f,1f) } ?: 0
    override fun useOn(context: net.minecraft.world.item.context.UseOnContext): net.minecraft.world.InteractionResult {
        if(definition.kind in PortableInstruments.kinds)return PortableInstruments.use(context)
        if(definition.bulb==null)return net.minecraft.world.InteractionResult.PASS
        val player=context.player ?: return net.minecraft.world.InteractionResult.PASS
        val node=(context.level.getBlockEntity(context.clickedPos) as? FaceNodeEntity) ?: (context.level.getBlockEntity(context.clickedPos) as? mods.eln.modern.grid.GridGhostEntity)?.root() ?: return net.minecraft.world.InteractionResult.PASS
        val p=node.partAt(context.clickLocation,player.eyePosition) ?: return net.minecraft.world.InteractionResult.PASS
        if(p.definition.behavior !in setOf(Behavior.LAMP,Behavior.FLOODLIGHT) || !context.level.mayInteract(player,node.blockPos))return net.minecraft.world.InteractionResult.PASS
        val inventory=PartInventory.get(node,p)
        val slot=(0 until inventory.slots).firstOrNull { inventory.getStackInSlot(it).isEmpty && inventory.isItemValid(it,context.itemInHand) } ?: return net.minecraft.world.InteractionResult.PASS
        if(!inventory.isItemValid(slot,context.itemInHand))return net.minecraft.world.InteractionResult.PASS
        if(!context.level.isClientSide) {
            inventory.insertItem(slot,context.itemInHand.copy().apply { count=1 },false)
            if(!player.abilities.instabuild)context.itemInHand.shrink(1)
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(context.level.isClientSide)
    }
    override fun appendHoverText(stack: net.minecraft.world.item.ItemStack,level: net.minecraft.world.level.Level?,lines: MutableList<net.minecraft.network.chat.Component>,flag: net.minecraft.world.item.TooltipFlag) {
        if(definition.kind=="BRUSH")lines.add(net.minecraft.network.chat.Component.translatable("tooltip.eln.brush_uses",if(ClientNodeHooks.creative())net.minecraft.network.chat.Component.translatable("tooltip.eln.brush_infinite")else Brushes.life(stack)))
        if(definition.kind=="FUSE")lines.add(net.minecraft.network.chat.Component.translatable("tooltip.eln.fuse"))
        if(definition.id in setOf("overheating_protection","overvoltage_protection"))lines.add(net.minecraft.network.chat.Component.translatable("tooltip.eln.protection_resource"))
        definition.drill?.let {
            lines.add(net.minecraft.network.chat.Component.translatable("tooltip.eln.drill_operation",it.operationTime,it.operationEnergy.toInt(),(it.operationEnergy/it.operationTime).toInt()))
        }
        definition.electrical?.let {
            fun number(value: Double)=String.format(java.util.Locale.ROOT,"%.0f",value)
            lines.add(net.minecraft.network.chat.Component.translatable("tooltip.eln.portable_energy",number(ElectricalItems.energy(stack)),number(it.capacity)))
            if(it.scanner!=null) {
                lines.add(net.minecraft.network.chat.Component.translatable("tooltip.eln.scanner_power",number(it.chargePower),number(it.scanner.watts)))
                lines.add(net.minecraft.network.chat.Component.translatable(PortableScanner.modeKey(stack)))
                lines.add(net.minecraft.network.chat.Component.translatable("tooltip.eln.scanner_care"))
            } else if(it.flashlight==null)lines.add(net.minecraft.network.chat.Component.translatable("tooltip.eln.portable_rates",number(it.chargePower),number(it.dischargePower)))
            else {
                lines.add(net.minecraft.network.chat.Component.translatable("tooltip.eln.flashlight_power",number(it.chargePower),number(it.flashlight.wattsLow),number(it.flashlight.wattsHigh)))
                lines.add(net.minecraft.network.chat.Component.translatable(PortableLights.modeKey(PortableLights.state(stack))))
            }
            if(it.tool!=null)lines.add(net.minecraft.network.chat.Component.translatable(if(it.tool.type=="AXE")"tooltip.eln.electric_axe" else "tooltip.eln.electric_drill"))
        }
        definition.clutchPlate?.let {
            lines.add(net.minecraft.network.chat.Component.translatable("tooltip.eln.clutch_wear",String.format(java.util.Locale.ROOT,"%.1f",(1-(stack.tag?.getDouble("wear") ?: 0.0))*100)))
        }
        definition.bulb?.let { bulb ->
            lines.add(net.minecraft.network.chat.Component.translatable("tooltip.eln.bulb",definition.nominalVoltage.toInt(),definition.value.toInt(),if(stack.tag?.contains("life")==true)String.format(java.util.Locale.ROOT,"%.2f",stack.tag!!.getDouble("life"))else bulb.hours.toString()))
        }
    }
}
object Materials {
    val all: List<MaterialDefinition> by lazy {
        val input=Materials::class.java.getResourceAsStream("/assets/eln/materials.json") ?: error("Missing material definitions")
        InputStreamReader(input).use { Gson().fromJson(it,object : TypeToken<List<MaterialDefinition>>() {}.type) }
    }
}
