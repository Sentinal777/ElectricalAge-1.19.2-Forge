package mods.eln.modern

import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag

/** Persistent component state. Solver objects remain transient and are rebuilt after chunk load. */
class PartState(val definition: PartDefinition, val face: Direction) {
    var rotation = 0
    // Preserve saved preview components' editable values and original 0/2 terminals.
    var legacyPassive = false
    var legacyWireless = false
    var legacyBreaker = false
    var legacySimpleSignal = false
    val originalBreaker get()=definition.originalBreaker && !legacyBreaker
    val originalPassive get() = definition.passive!=null && !legacyPassive
    // Only physical placement/rotation can introduce a new unsafe shaft merge; never serialize this flag.
    var transporterInitialized=false
    var shaftJoinPending=false
    // Old test worlds retain their original pin directions; new placements use an unmirrored frame.
    var orientationVersion = 2
    var value = definition.value
    var resistance = definition.resistance
    var enabled = definition.id=="mqtt_energy_meter" || definition.id !in setOf("signal_switch","signal_button") && !definition.originalBreaker && definition.contact==null && definition.wireless?.kind!="SOURCE" && definition.behavior !in setOf(Behavior.ENERGY_METER,Behavior.TURRET,Behavior.TELEPORTER,Behavior.BATTERY_CHARGER,Behavior.FUEL_GENERATOR,Behavior.HEAT_FURNACE,Behavior.FUEL_HEAT_FURNACE,Behavior.FURNACE,Behavior.LAMP,Behavior.EMERGENCY_LAMP)
    var channel = if(definition.behavior==Behavior.TELEPORTER)"Unnamed" else if(definition.wireless!=null)"Default channel" else if(definition.behavior==Behavior.SENSOR)"both" else if(definition.behavior==Behavior.FURNACE)"automatic" else if(definition.behavior in setOf(Behavior.LAMP,Behavior.EMERGENCY_LAMP,Behavior.POWER_SOCKET))"Default channel" else "default"
    var expression = when(definition.id) {
        "signal_processor" -> ""
        "experimental_transporter" -> "Unnamed"
        "5a_electrical_breaker" -> "0,5"
        "tachometer" -> "0,2500"
        "basic_floodlight","motorized_floodlight" -> "0,0"
        "pid_regulator" -> "1,0,0"
        "configurable_summing_unit" -> "1,1,1"
        "digital_display" -> "0,1000"
        "voltage_probe","electrical_probe","thermal_probe","temperature_probe" -> "0,5"
        "thermometer_sensor" -> "-40,50"
        "stone_heat_furnace","fuel_heat_furnace","electrical_furnace" -> "0"
        else -> "A"
    }
    var voltage = 0.0
    var voltageB = 0.0
    var current = 0.0
    var temperature = 20.0
    var voltageFault=0.0
    var wasOvervoltage=false
    var energy = 0.0
    var charge = definition.initialCharge
    var life = 1.0
    var inductorCurrent = 0.0
    var progress = 0.0
    var shaftEnergy = 0.0
    var shaftSpeed = 0.0
    var shaftGroup = ""
    var fuelEnergy = 0.0
    // Like the original crank, pending human input is transient until the next device tick.
    var crankInput = 0.0
    var solarAngle=Math.PI/2
    var recipeId = ""
    var tripped = false
    var timer = 0
    var signal = 0
    var displayReading=0
    var displayBits=0
    var displayBlank=false
    var displayColor=if(definition.lamp!=null || definition.behavior==Behavior.LOGGER)0xFFFFFF else 0xF20000
    var active = false
    var processFraction = 0.0
    var powerFactor = 0.0
    var history = mutableListOf<Double>()
    var equationState = CompoundTag()
    var logicState = CompoundTag()
    var analogState = CompoundTag()
    val sensedEntityPositions=hashMapOf<java.util.UUID,net.minecraft.world.phys.Vec3>()
    var gridId=java.util.UUID.randomUUID().toString()
    var gridAngle=0.0
    var gridState=CompoundTag()
    var inventoryData=CompoundTag()
    var thermalState=CompoundTag()
    var thermalPower=0.0
    var inventoryHandler: net.minecraftforge.items.ItemStackHandler?=null
    var connectedMask = 0
    var pinVoltages = DoubleArray(4)

    fun save(): CompoundTag = CompoundTag().also { t ->
        t.putInt("componentDataVersion",8);t.putBoolean("legacyPassive",legacyPassive);t.putBoolean("legacyWireless",legacyWireless);t.putBoolean("legacyBreaker",legacyBreaker);t.putBoolean("legacySimpleSignal",legacySimpleSignal)
        t.putString("id", definition.id); t.putString("family", definition.family)
        t.putInt("legacyId", definition.legacyId); t.putInt("face", face.get3DDataValue())
        t.putInt("rotation", rotation); t.putDouble("value", value); t.putDouble("resistance", resistance)
        t.putInt("orientationVersion",orientationVersion)
        t.putBoolean("enabled", enabled); t.putString("channel", channel); t.putString("expression", expression)
        t.putDouble("voltage", voltage); t.putDouble("voltageB", voltageB); t.putDouble("current", current)
        t.putDouble("temperature", temperature); t.putDouble("energy", energy); t.putDouble("charge", charge)
        t.putDouble("voltageFault",voltageFault);t.putBoolean("wasOvervoltage",wasOvervoltage)
        t.putDouble("life", life); t.putInt("connectedMask", connectedMask)
        t.putDouble("inductorCurrent", inductorCurrent); t.putDouble("progress", progress)
        t.putDouble("shaftEnergy",shaftEnergy);t.putDouble("shaftSpeed",shaftSpeed);t.putDouble("fuelEnergy",fuelEnergy)
        t.putString("shaftGroup",shaftGroup)
        t.putDouble("solarAngle",solarAngle)
        t.putString("recipeId", recipeId)
        t.putBoolean("tripped", tripped); t.putInt("timer", timer); t.putInt("signal", signal)
        t.putInt("displayReading",displayReading);t.putInt("displayBits",displayBits);t.putBoolean("displayBlank",displayBlank);t.putInt("displayColor",displayColor)
        t.putBoolean("active",active);t.putDouble("processFraction",processFraction);t.putDouble("powerFactor",powerFactor)
        t.putLongArray("history", history.map { java.lang.Double.doubleToRawLongBits(it) })
        t.put("equation", equationState.copy())
        t.put("logic", logicState.copy())
        t.put("analog", analogState.copy())
        t.putString("gridId",gridId);t.putDouble("gridAngle",gridAngle);t.put("gridState",gridState.copy())
        t.put("componentInventory",inventoryHandler?.serializeNBT() ?: inventoryData.copy())
        t.put("thermalState",thermalState.copy());t.putDouble("thermalPower",thermalPower)
        t.putLongArray("pinVoltages",pinVoltages.map(java.lang.Double::doubleToRawLongBits))
    }
    companion object {
        fun load(t: CompoundTag): PartState? {
            val def = PartDefinition.byId[t.getString("id")] ?: return null
            val index = t.getInt("face")
            if (index !in 0..5) return null
            fun number(key: String, fallback: Double, low: Double = -1e12, high: Double = 1e12): Double {
                val n = if (t.contains(key)) t.getDouble(key) else fallback
                return if (n.isFinite()) n.coerceIn(low, high) else fallback
            }
            return PartState(def, Direction.from3DDataValue(index)).also { p ->
                p.legacyPassive=def.passive!=null && (t.getBoolean("legacyPassive") || t.getInt("componentDataVersion")<5)
                p.legacyWireless=def.wireless!=null && (t.getBoolean("legacyWireless") || t.getInt("componentDataVersion")<6)
                p.legacyBreaker=def.originalBreaker && (t.getBoolean("legacyBreaker") || t.getInt("componentDataVersion")<7)
                p.legacySimpleSignal=SimpleSignals.supported(p) && (t.getBoolean("legacySimpleSignal") || t.getInt("componentDataVersion")<8)
                p.rotation = Math.floorMod(t.getInt("rotation"), 4)
                p.orientationVersion = if(t.getInt("orientationVersion")>=2)2 else 1
                p.value = number("value", def.value)
                p.resistance = number("resistance", def.resistance, 1e-9, 1e12)
                if(def.behavior==Behavior.CABLE && t.getInt("componentDataVersion")<3) {
                    val old=mapOf("signal_cable" to .002,"low_voltage_cable" to .0001,"medium_voltage_cable" to .00005,
                        "high_voltage_cable" to .000015625,"very_high_voltage_cable" to .000001953125,"creative_cable" to 1e-9,
                        "low_current_cable" to .02,"medium_current_cable" to .005,"high_current_cable" to .001)[def.id]
                    if(old!=null && kotlin.math.abs(p.resistance-old)<old*1e-8)p.resistance=def.resistance
                }
                if(t.getInt("componentDataVersion")<4 && def.behavior in setOf(Behavior.BATTERY,Behavior.SOLAR)) {
                    val previous=when(def.id) {
                        "cost_oriented_battery","life_oriented_battery" -> .01152
                        "capacity_oriented_battery" -> .02304
                        "voltage_oriented_battery" -> .18432
                        "current_oriented_battery" -> .01152
                        "single_use_battery" -> .00576
                        "experimental_battery" -> .144
                        else -> .028125
                    }
                    if(kotlin.math.abs(p.resistance-previous)<previous*1e-8)p.resistance=def.resistance
                }
                p.enabled = !t.contains("enabled") || t.getBoolean("enabled")
                p.channel = t.getString("channel").take(64)
                p.expression = t.getString("expression").take(256).let { if(def.behavior==Behavior.PROCESSOR)it else it.ifBlank { "A" } }
                if(def.id=="digital_display" && DisplaySettings.range(p.expression)==null)p.expression="0,1000"
                p.voltage = number("voltage", 0.0); p.voltageB = number("voltageB", 0.0)
                p.current = number("current", 0.0); p.temperature = number("temperature", 20.0, -273.15, 1e6)
                p.voltageFault=number("voltageFault",0.0,0.0,1e6);p.wasOvervoltage=t.getBoolean("wasOvervoltage")
                p.energy = number("energy", 0.0, 0.0); p.charge = number("charge", def.initialCharge, 0.0, 1.05)
                p.life = number("life", 1.0, 0.1, 1.0); p.connectedMask = t.getInt("connectedMask") and 15
                p.inductorCurrent = number("inductorCurrent", 0.0)
                p.progress = number("progress", 0.0, 0.0)
                p.shaftEnergy=number("shaftEnergy",0.0,0.0);p.shaftSpeed=number("shaftSpeed",0.0,0.0)
                p.shaftGroup=t.getString("shaftGroup").take(128)
                p.solarAngle=number("solarAngle",Math.PI/2,def.solar?.minimumAngle ?: 0.0,def.solar?.maximumAngle ?: Math.PI)
                p.fuelEnergy=number("fuelEnergy",0.0,0.0)
                p.recipeId = t.getString("recipeId")
                p.tripped = t.getBoolean("tripped"); p.timer = t.getInt("timer").coerceIn(0, 12000)
                p.signal = t.getInt("signal").coerceIn(0, 15)
                p.displayReading=t.getInt("displayReading").coerceIn(0,9999);p.displayBits=t.getInt("displayBits") and 255
                p.displayBlank=t.getBoolean("displayBlank");p.displayColor=if(t.contains("displayColor"))t.getInt("displayColor") and 0xFFFFFF else if(def.lamp!=null || def.behavior==Behavior.LOGGER)0xFFFFFF else 0xF20000
                p.active=t.getBoolean("active");p.processFraction=number("processFraction",0.0,0.0,1.0)
                p.powerFactor=number("powerFactor",0.0,0.0,4.0)
                p.history = t.getLongArray("history").takeLast(if(def.behavior==Behavior.LOGGER)256 else 120).map { java.lang.Double.longBitsToDouble(it) }
                    .filter { it.isFinite() }.toMutableList()
                p.equationState = t.getCompound("equation").copy()
                p.logicState = t.getCompound("logic").copy()
                p.analogState = t.getCompound("analog").copy()
                if(t.getString("gridId").matches(Regex("[a-fA-F0-9-]{36}")))p.gridId=t.getString("gridId")
                p.gridAngle=number("gridAngle",0.0,-360.0,360.0);p.gridState=t.getCompound("gridState").copy()
                p.inventoryData=t.getCompound("componentInventory").copy()
                p.thermalState=t.getCompound("thermalState").copy();p.thermalPower=number("thermalPower",0.0)
                if(def.behavior in setOf(Behavior.THERMAL_SENSOR,Behavior.AMBIENT_SENSOR)) {
                    if(SensorSettings.range(p.expression)==null)p.expression=if(def.behavior==Behavior.AMBIENT_SENSOR)"-40,50" else "0,5"
                    p.value=if(def.id=="temperature_probe")1.0 else p.value.toInt().coerceIn(0,1).toDouble()
                }
                if(def.behavior in setOf(Behavior.HEAT_FURNACE,Behavior.FUEL_HEAT_FURNACE)) {
                    p.value=p.value.coerceIn(if(def.behavior==Behavior.HEAT_FURNACE).1 else 0.0,1.0)
                    if(mods.eln.modern.thermal.HeatFurnaceRuntime.target(p.expression)==null)p.expression="0"
                    if(p.channel!="external")p.channel="internal"
                }
                if(def.behavior==Behavior.SENSOR) {
                    if(SensorSettings.range(p.expression)==null)p.expression="0,5"
                    p.value=if(def.id=="voltage_probe")2.0 else p.value.toInt().coerceIn(0,2).toDouble()
                    if(p.channel !in SensorSettings.directions)p.channel="both"
                }
                val pins=t.getLongArray("pinVoltages")
                p.pinVoltages=DoubleArray(4) { i -> pins.getOrNull(i)?.let(java.lang.Double::longBitsToDouble)?.takeIf { it.isFinite() } ?: 0.0 }
            }
        }
    }
}
