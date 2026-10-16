package mods.eln.modern

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.minecraft.core.Direction
import java.io.InputStreamReader

enum class Behavior {
    CABLE, GROUND, VOLTAGE_SOURCE, CURRENT_SOURCE, RESISTOR, CAPACITOR, INDUCTOR, DIODE,
    SWITCH, BREAKER, RELAY, METER, THERMAL_CABLE, THERMOMETER, LAMP,
    REDSTONE_INPUT, REDSTONE_OUTPUT, DAYLIGHT_SENSOR, LIGHT_SENSOR, WEATHER_SENSOR,
    SIGNAL_SOURCE, BUTTON, WIRELESS_RX, WIRELESS_TX, LOGIC, ANALOG, PROCESSOR, LOGGER,
    SOLAR, BATTERY, TRANSFORMER, FURNACE, MACERATOR, COMPRESSOR, PLATE_MACHINE, FE_CONVERTER,
    SHAFT, SHAFT_HUB, FLYWHEEL, CLUTCH, SHAFT_MOTOR, SHAFT_GENERATOR, GAS_TURBINE, STEAM_TURBINE,
    FUEL_GENERATOR, WIND_TURBINE, WATER_TURBINE, BATTERY_CHARGER, ROLLING_SHAFT, GRID, SENSOR, THERMAL_SENSOR, AMBIENT_SENSOR,
    PASSIVE_COOLER, ACTIVE_COOLER, HEAT_FURNACE, HEAT_TURBINE, FUEL_HEAT_FURNACE, HEAT_EXCHANGER, CRANK, MAGNETIZER, ARC_FURNACE, LAMP_SUPPLY, EMERGENCY_LAMP, FLOODLIGHT, TACHOMETER, FIXED_SHAFT, RADIAL_MOTOR, RESIN_COLLECTOR, ENVIRONMENT_SENSOR, WATCH, TIMER, ALARM, HUB, NIXIE, WIRELESS_REPEATER, FUSE_HOLDER, POWER_SOCKET, SIGNAL_BUS, INVENTORY_SCANNER, POWER_ANTENNA, OVERHEAD_LINES, FESTIVE, INCUBATOR, FABRICATOR, AUTO_MINER, TELEPORTER, TURRET, ENERGY_METER, MQTT_CONTROLLER, MODBUS, TUTORIAL_SIGN, COMPUTER_PROBE
}

data class VisualSpec(val scale: Double = 1.0, val bounds: List<Double> = listOf(.25,0.0,.25,.75,.25,.75),
    val cableTexture: String = "sprites/cable.png", val cableWidth: Double = .121875,
    val cableHeight: Double = .059375, val floorOnly: Boolean = false, val cubeTexture: String = "",
    val modelOffset: List<Double>? = null, val modelYaw: Double = 0.0,val modelLift: Double=0.0)

data class GridSpec(val kind: String, val range: Double, val voltageLimit: Double,
    val cable: String = "high_voltage_cable", val ghosts: List<List<Int>> = emptyList(),
    val anchors: Map<String,List<Double>> = emptyMap())

data class ThermalSpec(val rs: Double, val rp: Double, val capacity: Double,
    val maximum: Double=130.0, val minimum: Double=-100.0, val slow: Boolean=false)
data class HeatTurbineSpec(val nominalDelta: Double,val wattsPerVolt: Double)
data class SolarSpec(val minimumAngle: Double,val maximumAngle: Double,val offset: List<Int>,val ground: List<Int>?=null) {
    val rotating get()=minimumAngle!=maximumAngle
}
data class ShaftPortSpec(val offset: List<Int>,val side: Int)

data class ShaftElectricalSpec(val polarized: Boolean=false)

data class EmergencySpec(val chargePower: Double,val consumption: Double,val lightLevel: Int)
data class LampSpec(val range: Int=0,val minimumAngle: Double=0.0,val maximumAngle: Double=0.0,val chainLength: Int=0,
    val swing: Boolean=false,val paintable: Boolean=false,val allowedFaces: List<Int>?=null,val socket: String="DOUILLE",
    val sidewaysConnections: Boolean=true,val onOffModel: Boolean=true)

data class PartDefinition(
    val id: String, val name: String, val family: String, val legacyId: Int,
    val behavior: Behavior, val model: String = "", val icon: String = "",
    val modelGroups: List<String> = emptyList(),
    val visual: VisualSpec = VisualSpec(),
    val value: Double = 0.0, val resistance: Double = 0.001,
    val nominalVoltage: Double = 50.0, val maxCurrent: Double = 20.0,
    val power: Double = 1000.0, val capacity: Double = 480000.0,
    val initialCharge: Double = 0.5, val batteryHalfLife: Double = 2.0,
    val color: Int = 0xB36B32, val shaftMass: Double = 5.0, val grid: GridSpec? = null,
    val inventorySlots: List<String>? = null, val thermal: ThermalSpec? = null,val heatTurbine: HeatTurbineSpec?=null,
    val tankCapacity: Int=2000,val customItemRendering: Boolean=false,val heldModel: Boolean=false,val solar: SolarSpec?=null,
    val powerPorts: List<ShaftPortSpec>?=null,val shaftPorts: List<ShaftPortSpec>?=null,val shaftElectrical: ShaftElectricalSpec?=null,val ghosts: List<List<Int>>?=null,val lamp: LampSpec?=null,val emergency: EmergencySpec?=null,val environment: EnvironmentSpec?=null,val watch: WatchSpec?=null,val alarm: AlarmSpec?=null,val contact: ContactSpec?=null,val passive: PassiveSpec?=null,val wireless: WirelessSpec?=null,val originalBreaker: Boolean=false,val powerAntenna: PowerAntennaSpec?=null
) {
    val machine get() = family != "six"
    val reservedCells get() = grid?.ghosts ?: ghosts.orEmpty()
    val multiblock get() = reservedCells.isNotEmpty()
    val processing get() = behavior in setOf(Behavior.MACERATOR,Behavior.COMPRESSOR,Behavior.PLATE_MACHINE,Behavior.MAGNETIZER,Behavior.ARC_FURNACE)
    val translationKey get() = "item.eln.$id"
    val mechanical get() = behavior in setOf(Behavior.SHAFT,Behavior.SHAFT_HUB,Behavior.FLYWHEEL,Behavior.CLUTCH,
        Behavior.SHAFT_MOTOR,Behavior.SHAFT_GENERATOR,Behavior.GAS_TURBINE,Behavior.STEAM_TURBINE,Behavior.ROLLING_SHAFT,Behavior.CRANK,Behavior.TACHOMETER,Behavior.FIXED_SHAFT,Behavior.RADIAL_MOTOR)
    val fluidMachine get() = behavior in setOf(Behavior.GAS_TURBINE,Behavior.STEAM_TURBINE,Behavior.FUEL_GENERATOR,Behavior.FUEL_HEAT_FURNACE,Behavior.HEAT_EXCHANGER,Behavior.RADIAL_MOTOR)
    val bipolar get() = behavior in setOf(Behavior.RESISTOR, Behavior.CAPACITOR, Behavior.INDUCTOR,
        Behavior.DIODE, Behavior.SWITCH, Behavior.BREAKER, Behavior.RELAY, Behavior.TRANSFORMER,
        Behavior.SOLAR, Behavior.BATTERY) || shaftElectrical?.polarized==true
    companion object {
        val all: List<PartDefinition> by lazy {
            val stream = PartDefinition::class.java.getResourceAsStream("/assets/eln/parts.json")
                ?: error("Missing Electrical Age part definitions")
            val list: List<PartDefinition> = InputStreamReader(stream).use {
                Gson().fromJson(it, object : TypeToken<List<PartDefinition>>() {}.type)
            }
            require(list.map { it.id }.toSet().size == list.size) { "Duplicate modern item ID" }
            require(list.map { it.family to it.legacyId }.toSet().size == list.size) { "Duplicate legacy item ID" }
            require(list.all { it.id.matches(Regex("[a-z0-9_]+")) })
            list
        }
        val byId by lazy { all.associateBy { it.id } }
    }
}

/** Four edge directions in a face's plane. The index is preserved through rotation. */
object FaceBasis {
    fun edges(part: PartState) = edges(part.face,part.rotation,part.orientationVersion)
    fun edges(face: Direction, rotation: Int, version: Int = 2): List<Direction> {
        val base = if(version<2) when(face.axis) {
            Direction.Axis.Y -> listOf(Direction.EAST,Direction.SOUTH,Direction.WEST,Direction.NORTH)
            Direction.Axis.X -> listOf(Direction.SOUTH,Direction.UP,Direction.NORTH,Direction.DOWN)
            Direction.Axis.Z -> listOf(Direction.EAST,Direction.UP,Direction.WEST,Direction.DOWN)
        } else when(face) {
            Direction.DOWN -> listOf(Direction.EAST,Direction.SOUTH,Direction.WEST,Direction.NORTH)
            Direction.UP -> listOf(Direction.EAST,Direction.NORTH,Direction.WEST,Direction.SOUTH)
            Direction.WEST -> listOf(Direction.SOUTH,Direction.UP,Direction.NORTH,Direction.DOWN)
            Direction.EAST -> listOf(Direction.SOUTH,Direction.DOWN,Direction.NORTH,Direction.UP)
            Direction.NORTH -> listOf(Direction.EAST,Direction.DOWN,Direction.WEST,Direction.UP)
            Direction.SOUTH -> listOf(Direction.EAST,Direction.UP,Direction.WEST,Direction.DOWN)
        }
        return List(4) { base[Math.floorMod(it + rotation, 4)] }
    }
    fun placementRotation(face: Direction, look: Direction, machine: Boolean): Int {
        val target=if(machine) look.counterClockWise else when(face) {
            Direction.DOWN -> look
            Direction.UP -> look.opposite
            else -> Direction.UP
        }
        return edges(face,0).indexOf(target).coerceAtLeast(0)
    }
}
