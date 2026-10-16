package mods.eln.modern

/** Only controls consumed by a device's simulation are exposed by its screen. */
object DeviceControls {
    fun expressionKey(p: PartState): String? = if(p.originalBreaker)"gui.eln.breaker_window" else expressionKey(p.definition)
    fun valueKey(p: PartState): String? = if(p.originalPassive)null else valueKey(p.definition)
    fun valueKey(d: PartDefinition): String? = if(d.contact!=null)null else when(d.behavior) {
        Behavior.TIMER -> "gui.eln.timer_duration"
        Behavior.CAPACITOR -> "gui.eln.capacitance"
        Behavior.INDUCTOR -> "gui.eln.inductance"
        Behavior.RESISTOR -> "gui.eln.resistance"
        Behavior.FLOODLIGHT -> if(d.id=="basic_floodlight")"gui.eln.horizontal_angle" else null
        Behavior.TRANSFORMER -> null
        Behavior.CURRENT_SOURCE -> "gui.eln.current"
        Behavior.HEAT_FURNACE,Behavior.FUEL_HEAT_FURNACE -> "gui.eln.furnace_gain"
        Behavior.SOLAR -> if(d.solar?.rotating==true)"gui.eln.panel_angle" else null
        Behavior.LAMP -> if(d.lamp?.minimumAngle!=d.lamp?.maximumAngle)"gui.eln.beam_angle" else null
        Behavior.VOLTAGE_SOURCE,Behavior.SIGNAL_SOURCE,Behavior.RELAY -> "gui.eln.voltage"
        else -> when(d.id) {
            "amplifier" -> "gui.eln.gain"
            "lowpass_filter" -> "gui.eln.frequency"
            "pal_chip" -> "gui.eln.truth_table"
            else -> null
        }
    }
    fun expressionKey(d: PartDefinition): String? = if(d.behavior in setOf(Behavior.SENSOR,Behavior.THERMAL_SENSOR,Behavior.AMBIENT_SENSOR))"gui.eln.display_range" else when(d.id) {
        "stone_heat_furnace","fuel_heat_furnace","electrical_furnace" -> "gui.eln.furnace_target"
        "tachometer" -> "gui.eln.display_range"
        "basic_floodlight" -> "gui.eln.tilt_spread"
        "pid_regulator" -> "gui.eln.pid_params"
        "configurable_summing_unit" -> "gui.eln.gains"
        "signal_processor" -> "gui.eln.expression"
        "digital_display" -> "gui.eln.display_range"
        else -> null
    }
    fun channel(d: PartDefinition) = d.behavior in setOf(Behavior.WIRELESS_RX,Behavior.WIRELESS_TX,Behavior.LAMP,Behavior.EMERGENCY_LAMP,Behavior.POWER_SOCKET)
    fun toggle(d: PartDefinition) = d.id!="signal_trimmer" && (d.wireless?.kind=="SOURCE" || d.behavior in setOf(Behavior.ALARM,Behavior.VOLTAGE_SOURCE,Behavior.CURRENT_SOURCE,
        Behavior.SIGNAL_SOURCE,Behavior.SWITCH,Behavior.BREAKER,Behavior.RELAY,Behavior.LOGIC,Behavior.ANALOG,
        Behavior.EMERGENCY_LAMP,Behavior.BATTERY,Behavior.SOLAR,Behavior.FURNACE,Behavior.MACERATOR,Behavior.LAMP,
        Behavior.COMPRESSOR,Behavior.PLATE_MACHINE,Behavior.MAGNETIZER,Behavior.ARC_FURNACE,Behavior.FE_CONVERTER,Behavior.SHAFT_MOTOR,
        Behavior.SHAFT_GENERATOR,Behavior.GAS_TURBINE,Behavior.STEAM_TURBINE,Behavior.RADIAL_MOTOR,Behavior.FUEL_GENERATOR,Behavior.ROLLING_SHAFT,Behavior.HEAT_FURNACE,Behavior.FUEL_HEAT_FURNACE,Behavior.BATTERY_CHARGER))
}
