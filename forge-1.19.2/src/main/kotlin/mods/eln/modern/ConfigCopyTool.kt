package mods.eln.modern

import mods.eln.modern.lighting.LampChannel
import mods.eln.modern.lighting.LampChannels
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraftforge.registries.ForgeRegistries

/** Only source-supported configuration and item types/counts enter the clipboard, never runtime energy. */
object ConfigCopyTool {
    private fun hardware(p: PartState): Map<Int,String> {
        val types=p.definition.inventorySlots.orEmpty()
        return types.mapIndexedNotNull { slot,type ->
            val key=when {
                p.definition.behavior==Behavior.PROCESSOR && type=="REDSTONE" -> "redstone"
                p.definition.behavior==Behavior.TURRET -> "filter"
                p.definition.behavior==Behavior.FLOODLIGHT -> "lamp${slot+1}"
                p.definition.behavior==Behavior.LAMP && type=="LIGHT_BULB" -> "lamp"
                p.definition.behavior==Behavior.TRANSFORMER -> when(type) { "PRIMARY_WINDING"->"primary";"SECONDARY_WINDING"->"secondary";"FERROMAGNETIC_CORE"->"core";else->null }
                p.originalPassive && type=="REDSTONE" -> "capRedstone"
                p.originalPassive && type=="DIELECTRIC" -> "capDielectric"
                p.originalPassive && type=="INDUCTOR_WINDING" -> "indCable"
                p.originalPassive && type=="FERROMAGNETIC_CORE" -> "indCore"
                p.definition.processing && type=="MACHINE_BOOSTER" -> "booster"
                p.definition.behavior in setOf(Behavior.SENSOR,Behavior.THERMAL_SENSOR,Behavior.LAMP,Behavior.LAMP_SUPPLY) && type in setOf("VOLTAGE_CABLE","SENSOR_CABLE","ELECTRICAL_CABLE","THERMAL_CABLE") -> "cable"
                else -> null
            }
            key?.let { slot to it }
        }.toMap()
    }
    private fun rangePart(p: PartState)=p.definition.behavior in setOf(Behavior.SENSOR,Behavior.THERMAL_SENSOR,Behavior.AMBIENT_SENSOR,Behavior.TACHOMETER) || p.definition.id=="digital_display"
    private fun supported(p: PartState)=hardware(p).isNotEmpty() || rangePart(p) || p.definition.wireless!=null || p.definition.behavior in setOf(Behavior.LOGGER,Behavior.VOLTAGE_SOURCE,Behavior.CURRENT_SOURCE,Behavior.LAMP_SUPPLY,Behavior.POWER_SOCKET) || p.definition.behavior==Behavior.ANALOG || p.definition.contact?.relay==true
    fun copy(node: FaceNodeEntity,p: PartState,clipboard: CompoundTag): Boolean {
        if(!supported(p))return false
        clipboard.putString("source",p.definition.translationKey)
        if(p.definition.behavior==Behavior.PROCESSOR)clipboard.putString("expression",p.expression)
        if(p.definition.behavior==Behavior.LOGGER) {
            DataLoggers.initialize(p);clipboard.putDouble("min",p.gridState.getDouble("loggerMin"));clipboard.putDouble("max",p.gridState.getDouble("loggerMax"));clipboard.putDouble("period",DataLoggers.period(p));clipboard.putInt("unit",p.gridState.getInt("loggerUnit"))
        }
        if(rangePart(p))SensorSettings.range(p.expression)?.let { (a,b) -> clipboard.putDouble("min",a);clipboard.putDouble("max",b) }
        if(p.definition.behavior==Behavior.SENSOR) { clipboard.putInt("unit",when(p.value.toInt()) { 0->2;1->1;else->0 });clipboard.putString("dir",p.channel) }
        if(p.definition.behavior==Behavior.THERMAL_SENSOR)clipboard.putInt("unit",if(p.value.toInt()==0)2 else 3)
        if(p.definition.behavior==Behavior.TACHOMETER)clipboard.putInt("unit",6)
        when(p.definition.behavior) {
            Behavior.VOLTAGE_SOURCE -> clipboard.putDouble("voltage",p.value)
            Behavior.CURRENT_SOURCE -> clipboard.putDouble("current",p.value)
            Behavior.TURRET -> { clipboard.putDouble("chargePower",p.value);clipboard.putBoolean("filterInvert",p.enabled) }
            Behavior.LAMP_SUPPLY -> {
                val channels=LampChannels.read(p)
                strings(clipboard,"powerChannels",channels.map { it.power });strings(clipboard,"wirelessChannels",channels.map { it.signal });clipboard.putIntArray("aggregators",channels.map { it.mode })
            }
            Behavior.LAMP,Behavior.POWER_SOCKET -> strings(clipboard,"powerChannels",listOf(p.channel))
            else -> Unit
        }
        if(p.definition.wireless!=null)strings(clipboard,"wirelessChannels",listOf(p.channel))
        if(p.definition.contact?.relay==true)clipboard.putBoolean("nc",p.enabled)
        if(p.definition.behavior==Behavior.ANALOG)when(p.definition.id) {
            "pid_regulator" -> clipboard.putString("pid",p.expression)
            "configurable_summing_unit" -> clipboard.putString("gains",p.expression)
            "amplifier" -> clipboard.putDouble("gain",p.value)
            "lowpass_filter" -> clipboard.putDouble("cutoff",p.value)
        }
        for((slot,key) in hardware(p)) {
            val stack=PartInventory.peek(p,slot)
            clipboard.put("hardware_$key",CompoundTag().also { t -> if(!stack.isEmpty)t.putString("item",ForgeRegistries.ITEMS.getKey(stack.item).toString());t.putInt("count",stack.count) })
        }
        return true
    }
    private fun strings(t: CompoundTag,key: String,values: List<String>) { t.put(key,ListTag().also { list -> values.forEach { list.add(StringTag.valueOf(it)) } }) }
    private fun string(t: CompoundTag,key: String)=t.getList(key,8).takeIf { !it.isEmpty() }?.getString(0)
    fun apply(node: FaceNodeEntity,p: PartState,clipboard: CompoundTag,player: Player): Boolean {
        if(!supported(p) || !clipboard.contains("source") || node.isRemoved || node.parts[p.face.get3DDataValue()]!==p || !player.level.mayInteract(player,node.blockPos))return false
        fun number(key: String)=clipboard.getDouble(key).takeIf { clipboard.contains(key,99) && it.isFinite() }
        if(p.definition.behavior==Behavior.PROCESSOR && clipboard.contains("expression",8))SignalProcessors.configure(node,p,clipboard.getString("expression").take(256))
        if(p.definition.behavior==Behavior.LOGGER)DataLoggers.configure(node,p,number("period") ?: DataLoggers.period(p),number("min") ?: p.gridState.getDouble("loggerMin"),number("max") ?: p.gridState.getDouble("loggerMax"),if(clipboard.contains("unit"))clipboard.getInt("unit")else p.gridState.getInt("loggerUnit"),p.gridState.getBoolean("loggerZero"))
        if(rangePart(p)) {
            val low=number("min");val high=number("max")
            if(low!=null && high!=null && SensorSettings.range("$low,$high")!=null)p.expression="$low,$high"
        }
        if(p.definition.behavior in setOf(Behavior.SENSOR,Behavior.THERMAL_SENSOR) && clipboard.contains("unit")) {
            p.value=if(p.definition.id=="voltage_probe")2.0 else if(p.definition.id=="temperature_probe")1.0 else if(p.definition.behavior==Behavior.SENSOR)when(clipboard.getInt("unit")) { 0->2.0;1->1.0;2->0.0;else->p.value }else when(clipboard.getInt("unit")) { 2->0.0;3->1.0;else->p.value }
            if(p.definition.behavior==Behavior.SENSOR && clipboard.getString("dir") in SensorSettings.directions)p.channel=clipboard.getString("dir")
        }
        when(p.definition.behavior) {
            Behavior.VOLTAGE_SOURCE -> number("voltage")?.let { p.value=it.coerceIn(-120000.0,120000.0) }
            Behavior.CURRENT_SOURCE -> number("current")?.let { p.value=it.coerceIn(-120000.0,120000.0) }
            Behavior.TURRET -> Turrets.configure(node,p,number("chargePower")?.coerceIn(100.0,5000.0) ?: p.value,if(clipboard.contains("filterInvert"))clipboard.getBoolean("filterInvert")else p.enabled)
            Behavior.LAMP_SUPPLY -> {
                val power=clipboard.getList("powerChannels",8);val signal=clipboard.getList("wirelessChannels",8);val modes=clipboard.getIntArray("aggregators")
                LampChannels.write(p,LampChannels.read(p).mapIndexed { i,c -> LampChannel(if(i<power.size)power.getString(i)else c.power,if(i<signal.size)signal.getString(i)else c.signal,modes.getOrNull(i)?.coerceIn(0,2) ?: c.mode) })
            }
            Behavior.LAMP,Behavior.POWER_SOCKET -> string(clipboard,"powerChannels")?.let { p.channel=it.take(64) }
            else -> Unit
        }
        if(p.definition.wireless!=null)string(clipboard,"wirelessChannels")?.let { p.channel=it.take(64);p.gridState.putBoolean("wirelessRescan",true) }
        if(p.definition.contact?.relay==true && clipboard.contains("nc"))p.enabled=clipboard.getBoolean("nc")
        if(p.definition.behavior==Behavior.ANALOG)when(p.definition.id) {
            "pid_regulator","configurable_summing_unit" -> {
                val text=clipboard.getString(if(p.definition.id=="pid_regulator")"pid" else "gains")
                val values=text.split(',').mapNotNull { it.toDoubleOrNull()?.takeIf(Double::isFinite) }
                if(values.size==3 && text.length<=256)p.expression=text
            }
            "amplifier" -> number("gain")?.let { p.value=it.coerceIn(-120000.0,120000.0) }
            "lowpass_filter" -> number("cutoff")?.let { p.value=it.coerceIn(.001,30.0) }
        }
        for((slot,key) in hardware(p))if(clipboard.contains("hardware_$key",10))moveHardware(node,p,slot,clipboard.getCompound("hardware_$key"),player)
        player.inventory.setChanged();player.inventoryMenu.broadcastChanges();node.changedTopology();return true
    }
    /** Keep actual NBT on returned/installed hardware. A clipboard cannot mint a charged or unused item. */
    private fun moveHardware(node: FaceNodeEntity,p: PartState,slot: Int,wanted: CompoundTag,player: Player) {
        val inv=PartInventory.get(node,p)
        val item=ResourceLocation.tryParse(wanted.getString("item"))?.let(ForgeRegistries.ITEMS::getValue)
        val desired=if(item==null)0 else wanted.getInt("count").coerceIn(0,minOf(inv.getSlotLimit(slot),item.maxStackSize))
        if(desired>0 && !inv.isItemValid(slot,ItemStack(item!!)))return
        val current=inv.getStackInSlot(slot)
        if(player.abilities.instabuild) { inv.setStackInSlot(slot,if(desired==0)ItemStack.EMPTY else ItemStack(item!!,desired));return }
        if(!current.isEmpty && (current.item!==item || current.count>desired)) {
            val back=inv.extractItem(slot,if(current.item!==item)current.count else current.count-desired,false)
            if(!player.inventory.add(back) && !back.isEmpty)player.drop(back,false)
        }
        var needed=desired-inv.getStackInSlot(slot).count
        for(i in 0 until player.inventory.containerSize) {
            if(needed<=0)break
            val available=player.inventory.getItem(i)
            if(available.isEmpty || available.item!==item)continue
            val offered=available.copy().apply { count=minOf(available.count,needed) }
            val remainder=inv.insertItem(slot,offered,false)
            val moved=offered.count-remainder.count
            available.shrink(moved);needed-=moved
        }
    }
}
