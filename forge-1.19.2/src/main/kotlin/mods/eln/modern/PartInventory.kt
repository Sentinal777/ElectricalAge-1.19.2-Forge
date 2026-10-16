package mods.eln.modern

import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraftforge.items.ItemStackHandler

/** Each face-mounted component owns its inventory; machine input/output inventory remains on its carrier. */
object PartInventory {
    fun peek(p: PartState,slot: Int): ItemStack {
        if(slot !in 0 until slots(p))return ItemStack.EMPTY
        p.inventoryHandler?.let { return it.getStackInSlot(slot) }
        val tag=p.inventoryData.getList("Items",10).filterIsInstance<net.minecraft.nbt.CompoundTag>().firstOrNull { it.getInt("Slot")==slot }
        return tag?.let(ItemStack::of) ?: ItemStack.EMPTY
    }
    fun slots(p: PartState)=if(p.legacyPassive || p.legacyBreaker)0 else p.definition.inventorySlots.orEmpty().size
    fun get(node: FaceNodeEntity,p: PartState): ItemStackHandler {
        p.inventoryHandler?.let { return it }
        var initializing=true
        val handler=object : ItemStackHandler(slots(p)) {
            override fun isItemValid(slot: Int,stack: ItemStack): Boolean = when(p.definition.inventorySlots?.getOrNull(slot)) {
                "ELECTRICAL_CABLE" -> (stack.item as? PartItem)?.definition?.behavior==Behavior.CABLE
                "VOLTAGE_CABLE" -> (stack.item as? PartItem)?.definition?.let { it.behavior==Behavior.CABLE && !it.id.endsWith("_current_cable") }==true
                "THERMAL_CABLE" -> (stack.item as? PartItem)?.definition?.behavior==Behavior.THERMAL_CABLE
                "SENSOR_CABLE" -> (stack.item as? PartItem)?.definition?.behavior in setOf(Behavior.CABLE,Behavior.THERMAL_CABLE)
                "FUEL" -> mods.eln.modern.thermal.HeatFurnaceRuntime.fuelEnergy(stack)>0
                "REGULATOR" -> (stack.item as? MaterialItem)?.definition?.kind in setOf("ON_OFF_REGULATOR","ANALOG_REGULATOR")
                "THERMAL_INSULATION","COMBUSTION_CHAMBER" -> (stack.item as? MaterialItem)?.definition?.kind==p.definition.inventorySlots!![slot]
                "FUEL_BURNER" -> (stack.item as? MaterialItem)?.definition?.kind=="FUEL_BURNER"
                "SOLAR_TRACKER" -> (stack.item as? MaterialItem)?.definition?.kind=="SOLAR_TRACKER"
                "HEATING_ELEMENT" -> (stack.item as? MaterialItem)?.definition?.kind=="HEATING_ELEMENT"
                "PRIMARY_WINDING","SECONDARY_WINDING" -> (stack.item as? MaterialItem)?.definition?.id=="copper_cable"
                "FERROMAGNETIC_CORE","CASING" -> (stack.item as? MaterialItem)?.definition?.kind==p.definition.inventorySlots!![slot]
                "CLUTCH_PLATE","CLUTCH_PIN" -> (stack.item as? MaterialItem)?.definition?.kind==p.definition.inventorySlots!![slot]
                "ELECTRICAL_ITEM" -> ElectricalItems.spec(stack)!=null
                "PORTABLE_STORE" -> (stack.item as? MaterialItem)?.definition?.kind=="PORTABLE_BATTERY"
                "FUSE" -> (stack.item as? MaterialItem)?.definition?.kind=="FUSE"
                "COAL_DUST" -> stack.`is`(net.minecraft.tags.TagKey.create(net.minecraft.core.Registry.ITEM_REGISTRY,net.minecraft.resources.ResourceLocation("forge","dusts/coal")))
                "MINING_DRILL" -> (stack.item as? MaterialItem)?.definition?.drill!=null
                "ORE_SCANNER" -> (stack.item as? MaterialItem)?.definition?.kind=="ORE_SCANNER_UPGRADE"
                "MINING_PIPE" -> (stack.item as? MaterialItem)?.definition?.kind=="MINING_PIPE"
                "LOGGER_PAPER" -> stack.item==net.minecraft.world.item.Items.PAPER
                "LOGGER_PRINT" -> (stack.item as? MaterialItem)?.definition?.kind=="DATA_LOG_PRINT"
                "FABRICATOR_OUTPUT" -> false
                "SILICON_WAFER" -> (stack.item as? MaterialItem)?.definition?.id=="silicon_wafer"
                "COPPER_PLATE" -> (stack.item as? MaterialItem)?.definition?.id=="copper_plate"
                "EGG" -> stack.item==net.minecraft.world.item.Items.EGG
                "REDSTONE" -> stack.item==net.minecraft.world.item.Items.REDSTONE
                "DIELECTRIC" -> (stack.item as? MaterialItem)?.definition?.id=="dielectric"
                "INDUCTOR_WINDING" -> (stack.item as? MaterialItem)?.definition?.id=="copper_cable"
                "ENTITY_FILTER" -> (stack.item as? MaterialItem)?.definition?.kind=="ENTITY_FILTER"
                "MACHINE_BOOSTER" -> (stack.item as? MaterialItem)?.definition?.kind=="MACHINE_BOOSTER"
                "LIGHT_BULB" -> (stack.item as? MaterialItem)?.definition?.bulb?.socket==p.definition.lamp?.socket
                else -> false
            }
            override fun getSlotLimit(slot: Int)=when(p.definition.inventorySlots?.getOrNull(slot)) {
                "LOGGER_PRINT" -> 1
                "REGULATOR","THERMAL_INSULATION","FUEL_BURNER","SOLAR_TRACKER","HEATING_ELEMENT","FERROMAGNETIC_CORE","CASING","LIGHT_BULB","CLUTCH_PLATE","CLUTCH_PIN","ELECTRICAL_ITEM","PORTABLE_STORE","ENTITY_FILTER" -> 1
                "MACHINE_BOOSTER" -> 5
                "FUSE","MINING_DRILL","ORE_SCANNER" -> 1
                "REDSTONE" -> if(p.definition.behavior==Behavior.PROCESSOR)64 else 13
                "DIELECTRIC" -> 20
                "INDUCTOR_WINDING" -> 19
                "COMBUSTION_CHAMBER" -> 8
                "PRIMARY_WINDING","SECONDARY_WINDING" -> if(p.definition.id=="variable_dc_dc_converter")4 else 64
                "ELECTRICAL_CABLE","VOLTAGE_CABLE" -> if(p.definition.behavior==Behavior.LAMP_SUPPLY)64 else 1
                else -> 64
            }
            override fun onContentsChanged(slot: Int) {
                if(!initializing && p.originalBreaker) { p.enabled=false;p.tripped=false }
                if(!initializing && p.originalPassive && p.definition.passive?.kind=="INDUCTOR")p.gridState.putBoolean("passiveReset",true)
                p.inventoryData=serializeNBT();node.changedTopology()
            }
        }
        if(!p.inventoryData.isEmpty)handler.deserializeNBT(p.inventoryData.copy().also { it.putInt("Size",slots(p)) })
        initializing=false
        p.inventoryHandler=handler;return handler
    }
    fun takeCable(node: FaceNodeEntity,p: PartState,player: Player,held: ItemStack): Boolean {
        if(p.definition.behavior !in setOf(Behavior.ENERGY_METER,Behavior.SENSOR,Behavior.THERMAL_SENSOR,Behavior.LAMP,Behavior.LAMP_SUPPLY) || slots(p)==0)return false
        val slot=if(p.definition.behavior==Behavior.LAMP)1 else 0
        val inventory=get(node,p)
        if(!inventory.isItemValid(slot,held))return false
        if(!inventory.getStackInSlot(slot).isEmpty && p.definition.behavior!=Behavior.LAMP_SUPPLY)return false
        if(node.level?.isClientSide==true)return true
        val offered=held.copy().apply { count=1 };val remaining=inventory.insertItem(slot,offered,false)
        if(remaining.isEmpty && !player.abilities.instabuild)held.shrink(1)
        return remaining.isEmpty
    }
    fun takeItem(node: FaceNodeEntity,p: PartState,player: Player,held: ItemStack): Boolean {
        if(held.isEmpty)return false
        val inventory=get(node,p)
        for(slot in 0 until inventory.slots) {
            if(!inventory.isItemValid(slot,held))continue
            val remaining=inventory.insertItem(slot,held.copy(),true)
            if(remaining.count==held.count)continue
            if(node.level?.isClientSide!=true) {
                val rest=inventory.insertItem(slot,held.copy(),false)
                if(!player.abilities.instabuild)held.shrink(held.count-rest.count)
            }
            return true
        }
        return false
    }
}

object SensorSettings {
    val directions=listOf("both","forward","reverse")
    fun range(text: String): Pair<Double,Double>? {
        val v=text.split(',').map { it.trim().toDoubleOrNull() ?: return null }
        if(v.size!=2 || v.any { !it.isFinite() || kotlin.math.abs(it)>1e12 } || kotlin.math.abs(v[0]-v[1])<1e-12)return null
        return v[0] to v[1]
    }
    fun output(physical: Double,text: String): Double {
        val (low,high)=range(text) ?: (0.0 to 5.0)
        val value=(physical-low)/(high-low)*5
        return if(value.isFinite())value.coerceIn(0.0,5.0)else 0.0
    }
}
