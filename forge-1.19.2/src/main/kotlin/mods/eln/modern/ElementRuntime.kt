package mods.eln.modern

import mods.eln.Eln
import mods.eln.sim.BatteryProcess
import mods.eln.sim.BatterySlowProcess
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.ThermalLoad
import mods.eln.sim.mna.component.*
import mods.eln.solver.Equation
import mods.eln.solver.ISymbole
import mods.eln.sixnode.logicgate.*
import mods.eln.sixnode.*
import net.minecraft.core.BlockPos
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Block
import kotlin.math.*
import mods.eln.config.ForgePortConfig

/** Native world adapter around the upstream MNA component implementations. */
class ElementRuntime(val circuit: LevelCircuit, val node: FaceNodeEntity, val part: PartState) {
    var grid: mods.eln.modern.grid.GridRuntime? = null
        private set
    private var sensor: SensorRuntime?=null
    private var simpleSignal: SimpleSignalRuntime?=null
    var thermal: mods.eln.modern.thermal.ThermalRuntime?=null
        private set
    val pins = arrayOfNulls<ElectricalLoad>(4)
    private val definition = part.definition
    private val kind = definition.behavior
    private val loads = mutableListOf<ElectricalLoad>()
    private var branch: Bipole? = null
    private var source: VoltageSource? = null
    private var gateOutput: mods.eln.sim.nbt.NbtElectricalGateOutputProcess?=null
    private var currentSource: CurrentSource? = null
    var solar: SolarRuntime?=null
        private set
    private var processing: ProcessingRuntime?=null
    private var dcDc: DcDcRuntime?=null
    var lamp: mods.eln.modern.lighting.LampRuntime?=null
        private set
    var festive: mods.eln.modern.lighting.FestiveRuntime?=null
        private set
    var lampSupply: mods.eln.modern.lighting.LampSupplyRuntime?=null
        private set
    var emergencyLamp: mods.eln.modern.lighting.EmergencyLampRuntime?=null
        private set
    private var floodlight: mods.eln.modern.lighting.FloodlightRuntime?=null
    var clutch: ClutchRuntime?=null
        private set
    private var resinCollector: ResinCollectorRuntime?=null
    private var radial: RadialMotorRuntime?=null
    private var turbine: TurbineRuntime?=null
    private var shaftElectrical: ShaftElectricalRuntime?=null
    private var rolling: RollingShaftRuntime?=null
    private var fuelGenerator: FuelGeneratorRuntime?=null
    private var renewable: RenewableRuntime?=null
    private var charger: BatteryChargerRuntime?=null
    private var environment: EnvironmentSensorRuntime?=null
    private var watch: WatchRuntime?=null
    private var timer: TimerRuntime?=null
    private var alarm: AlarmRuntime?=null
    private var hub: HubRuntime?=null
    private var contact: ContactRuntime?=null
    private var passive: PassiveRuntime?=null
    private var nixie: NixieRuntime?=null
    private var wireless: WirelessRuntime?=null
    private var protection: ProtectionRuntime?=null
    private var bus: SignalBusRuntime?=null
    private var inventoryScanner: InventoryScannerRuntime?=null
    private var turret: TurretRuntime?=null
    private var dataLogger: DataLoggerRuntime?=null
    private var energyMeter: EnergyMeterRuntime?=null
    private var mqttController: MqttControllerRuntime?=null
    private var modbus: ModbusRuntime?=null
    var computerProbe: ComputerProbeRuntime?=null
        private set
    private var processor: SignalProcessorRuntime?=null
    private var vuMeter: VuMeterRuntime?=null
    var transporter: TransporterRuntime?=null
        private set
    var autoMiner: AutoMinerRuntime?=null
        private set
    private var fabricator: FabricatorRuntime?=null
    private var incubator: IncubatorRuntime?=null
    private var railPower: RailPowerRuntime?=null
    private var replicatorFeed: ReplicatorCableLoad?=null
    var powerAntenna: PowerAntennaRuntime?=null
        private set
    var powerSocket: mods.eln.modern.lighting.PowerSocketRuntime?=null
        private set
    fun portLoads(index: Int): List<ElectricalPort> = bus?.loads?.mapIndexed { channel,load -> ElectricalPort(load,channel) } ?: pins[index]?.let { listOf(ElectricalPort(it)) }.orEmpty()
    private var tachometer: TachometerRuntime?=null
    private var battery: BatteryProcess? = null
    private var batteryAging: BatterySlowProcess? = null
    private var batteryHeat = ThermalLoad()
    private var equation: Equation? = null
    private var logic: LogicFunction? = null
    private var analog: AnalogFunction? = null
    private var cookOutput = ItemStack.EMPTY
    private var cookEnergy = 10000.0
    private var lastRecipe = part.recipeId
    private var power = 0.0
    private var overloadedTicks = 0
    private var diodeHeatTransition=false
    fun connectionKind(index: Int): Int = if(kind==Behavior.ENERGY_METER)2 else if(definition.powerAntenna!=null)if(index==PowerAntennas.powerPin(part))0 else 1 else if(part.originalBreaker || kind==Behavior.FUSE_HOLDER)2 else if(part.originalPassive)if(index==0 && definition.passive!!.controlled)1 else 0 else if(definition.contact!=null)if(definition.contact.signal || definition.contact.relay && index==0)1 else 0 else if(kind==Behavior.HUB)2 else if(kind==Behavior.SENSOR)if(index==0)1 else 2 else if(kind in setOf(Behavior.TIMER,Behavior.ALARM,Behavior.ENVIRONMENT_SENSOR) || kind in setOf(Behavior.CLUTCH,Behavior.TACHOMETER,Behavior.RADIAL_MOTOR,Behavior.GAS_TURBINE,Behavior.STEAM_TURBINE) || kind==Behavior.FLOODLIGHT && index!=3 || kind==Behavior.TRANSFORMER && definition.id=="variable_dc_dc_converter" && index in setOf(1,3) || kind in setOf(Behavior.THERMAL_SENSOR,Behavior.AMBIENT_SENSOR,Behavior.HEAT_FURNACE,Behavior.FUEL_HEAT_FURNACE,Behavior.HEAT_EXCHANGER) || kind==Behavior.RELAY && index==1 || definition.id=="signal_cable" ||
        kind in setOf(Behavior.SIGNAL_SOURCE,Behavior.BUTTON,Behavior.REDSTONE_INPUT,Behavior.REDSTONE_OUTPUT,
            Behavior.DAYLIGHT_SENSOR,Behavior.LIGHT_SENSOR,Behavior.WEATHER_SENSOR,Behavior.WIRELESS_RX,Behavior.WIRELESS_TX,
            Behavior.LOGIC,Behavior.ANALOG,Behavior.PROCESSOR,Behavior.LOGGER,Behavior.MQTT_CONTROLLER,Behavior.MODBUS) ||
        kind in setOf(Behavior.NIXIE,Behavior.SIGNAL_BUS,Behavior.INVENTORY_SCANNER) || definition.id in setOf("signal_diode","digital_display","analog_vumeter","led_vumeter","multicolor_led_vumeter"))1 else 0

    private fun load(resistance: Double = part.resistance, voltage: Double = part.voltage): ElectricalLoad {
        val load = ElectricalLoad(); load.serialResistance = resistance.coerceAtLeast(1e-9)
        load.state = voltage; loads.add(load); circuit.root.addState(load)
        // A finite leakage path keeps an unpowered/floating component numerically defined.
        circuit.root.addComponent(Resistor(load,null).setResistance(1e12))
        return load
    }
    private fun add(component: Component) { circuit.root.addComponent(component) }

    fun build() {
        if(SimpleSignals.supported(part)) { simpleSignal=SimpleSignalRuntime(this);return }
        if(kind==Behavior.TUTORIAL_SIGN) { TutorialSigns.refresh(node,part);return }
        if(kind==Behavior.COMPUTER_PROBE) { computerProbe=ComputerProbeRuntime(this);return }
        if(kind==Behavior.MODBUS) { modbus=ModbusRuntime(this);return }
        if(kind==Behavior.MQTT_CONTROLLER) { mqttController=MqttControllerRuntime(this);return }
        if(kind==Behavior.PROCESSOR) { processor=SignalProcessorRuntime(this);return }
        if(definition.id in setOf("analog_vumeter","led_vumeter","multicolor_led_vumeter")) { vuMeter=VuMeterRuntime(this);return }
        if(kind==Behavior.ENERGY_METER) { energyMeter=EnergyMeterRuntime(this);return }
        if(kind==Behavior.LOGGER) { dataLogger=DataLoggerRuntime(this);return }
        if(kind==Behavior.TURRET) { turret=TurretRuntime(this);return }
        if(kind==Behavior.TELEPORTER) { transporter=TransporterRuntime(this);return }
        if(kind==Behavior.AUTO_MINER) { autoMiner=AutoMinerRuntime(this);return }
        if(kind==Behavior.FABRICATOR) { fabricator=FabricatorRuntime(this);return }
        if(kind==Behavior.INCUBATOR) { incubator=IncubatorRuntime(this);return }
        if(kind==Behavior.FESTIVE) { festive=mods.eln.modern.lighting.FestiveRuntime(this);return }
        if(kind==Behavior.OVERHEAD_LINES) { railPower=RailPowerRuntime(this);return }
        if(kind==Behavior.POWER_ANTENNA) { powerAntenna=PowerAntennaRuntime(this);return }
        if(kind==Behavior.INVENTORY_SCANNER) { inventoryScanner=InventoryScannerRuntime(this);return }
        if(kind==Behavior.SIGNAL_BUS) { bus=SignalBusRuntime(this);return }
        if(kind==Behavior.POWER_SOCKET) { powerSocket=mods.eln.modern.lighting.PowerSocketRuntime(this);return }
        if(part.originalBreaker || kind==Behavior.FUSE_HOLDER) { protection=ProtectionRuntime(this);return }
        if(definition.wireless!=null && !part.legacyWireless) { wireless=WirelessRuntime(this);return }
        if(kind==Behavior.NIXIE) { nixie=NixieRuntime(this);return }
        if(part.originalPassive) { if(definition.thermal!=null)thermal=mods.eln.modern.thermal.ThermalRuntime(this);passive=PassiveRuntime(this);return }
        if(definition.contact!=null) { if(definition.thermal!=null)thermal=mods.eln.modern.thermal.ThermalRuntime(this);contact=ContactRuntime(this);return }
        if(kind==Behavior.HUB) { hub=HubRuntime(this);return }
        if(kind==Behavior.TIMER) { timer=TimerRuntime(this);return }
        if(kind==Behavior.ALARM) { alarm=AlarmRuntime(this);return }
        if(kind==Behavior.WATCH) { watch=WatchRuntime(this);return }
        if(kind==Behavior.ENVIRONMENT_SENSOR) { environment=EnvironmentSensorRuntime(this);return }
        if(kind==Behavior.BATTERY_CHARGER) { charger=BatteryChargerRuntime(this);return }
        if(kind in setOf(Behavior.WIND_TURBINE,Behavior.WATER_TURBINE)) { renewable=RenewableRuntime(this);return }
        if(kind==Behavior.FUEL_GENERATOR) { fuelGenerator=FuelGeneratorRuntime(this);return }
        if(kind==Behavior.ROLLING_SHAFT) { rolling=RollingShaftRuntime(this);return }
        if(kind==Behavior.RESIN_COLLECTOR) { resinCollector=ResinCollectorRuntime(this);return }
        if(kind in setOf(Behavior.GAS_TURBINE,Behavior.STEAM_TURBINE)) { turbine=TurbineRuntime(this);return }
        if(kind==Behavior.RADIAL_MOTOR) { radial=RadialMotorRuntime(this);return }
        if(kind==Behavior.CLUTCH) { clutch=ClutchRuntime(this);return }
        if(kind==Behavior.TACHOMETER) { tachometer=TachometerRuntime(this);return }
        if(kind==Behavior.EMERGENCY_LAMP) { emergencyLamp=mods.eln.modern.lighting.EmergencyLampRuntime(this);return }
        if(kind==Behavior.LAMP) { lamp=mods.eln.modern.lighting.LampRuntime(this);return }
        if(kind==Behavior.FLOODLIGHT) { floodlight=mods.eln.modern.lighting.FloodlightRuntime(this);return }
        if(kind==Behavior.LAMP_SUPPLY) { lampSupply=mods.eln.modern.lighting.LampSupplyRuntime(this);return }
        if(kind==Behavior.TRANSFORMER) { dcDc=DcDcRuntime(this);return }
        if(definition.processing) { processing=ProcessingRuntime(this);return }
        if(kind==Behavior.SOLAR) { solar=SolarRuntime(this);return }
        if(kind==Behavior.GRID) { grid=mods.eln.modern.grid.GridRuntime(this);return }
        if(kind==Behavior.SENSOR) { sensor=SensorRuntime(this);return }
        if(definition.thermal!=null || kind in setOf(Behavior.THERMAL_SENSOR,Behavior.AMBIENT_SENSOR)) {
            thermal=mods.eln.modern.thermal.ThermalRuntime(this);batteryHeat=thermal!!.load
        }
        if(kind in setOf(Behavior.SHAFT_MOTOR,Behavior.SHAFT_GENERATOR)) { shaftElectrical=ShaftElectricalRuntime(this);return }
        if (kind in setOf(Behavior.THERMAL_CABLE,Behavior.THERMOMETER,Behavior.THERMAL_SENSOR,Behavior.AMBIENT_SENSOR,Behavior.PASSIVE_COOLER,Behavior.HEAT_FURNACE,Behavior.FUEL_HEAT_FURNACE,Behavior.HEAT_TURBINE,Behavior.HEAT_EXCHANGER,Behavior.FURNACE,Behavior.SHAFT,Behavior.SHAFT_HUB,
                Behavior.FLYWHEEL,Behavior.CLUTCH,Behavior.GAS_TURBINE,Behavior.STEAM_TURBINE,Behavior.ROLLING_SHAFT,Behavior.CRANK,Behavior.FIXED_SHAFT)) return
        val a = load(if(connectionKind(0)==1).05 else if(kind==Behavior.DIODE)definition.resistance else part.resistance)
        if (definition.bipolar) { pins[0]=a; pins[2]=load(resistance=if(kind==Behavior.DIODE)definition.resistance else part.resistance,voltage=part.voltageB) }
        else for (i in 0..3) pins[i]=a
        if(kind in setOf(Behavior.SHAFT_MOTOR,Behavior.SHAFT_GENERATOR,Behavior.FUEL_GENERATOR)) { pins[1]=null;pins[3]=null }
        if(kind==Behavior.ACTIVE_COOLER) { pins[1]=null;pins[3]=null }
        if(definition.id=="digital_display") {
            pins[2]=a;pins[0]=load(.05,part.pinVoltages[0]);pins[1]=load(.05,part.pinVoltages[1]);pins[3]=pins[1]
        }
        val b = pins[2].takeIf { definition.bipolar }
        when (kind) {
            Behavior.CABLE,Behavior.WIRELESS_TX,Behavior.REDSTONE_OUTPUT,Behavior.METER,Behavior.LOGGER -> Unit
            Behavior.GROUND -> { branch=Resistor(a,null).setResistance(1e-6);add(branch!!) }
            Behavior.CURRENT_SOURCE -> { currentSource=CurrentSource("current",a,null).setCurrent(part.value);branch=currentSource;add(currentSource!!) }
            Behavior.RESISTOR -> { branch=Resistor(a,b).setResistance(part.value.coerceIn(1e-6,1e12));add(branch!!) }
            Behavior.CAPACITOR -> { branch=Capacitor(a,b).apply { coulombs=part.value.coerceIn(1e-9,1000.0) };add(branch!!) }
            Behavior.INDUCTOR -> { branch=Inductor("inductor",a,b).apply { inductance=part.value.coerceIn(1e-9,1e6);currentState.state=part.inductorCurrent };add(branch!!) }
            Behavior.DIODE,Behavior.SWITCH,Behavior.BREAKER,Behavior.RELAY -> {
                branch=ResistorSwitch("contact",a,b).apply { resistance=if(kind==Behavior.DIODE)definition.value else part.resistance;state=if(kind==Behavior.DIODE)part.gridState.getBoolean("diodeOn") else part.enabled && !part.tripped }
                add(branch!!)
                if (kind==Behavior.RELAY) pins[1]=load(0.002,0.0)
            }
            Behavior.TRANSFORMER -> { branch=Transformer(a,b).apply { ratio=part.value.coerceIn(0.001,1000.0) };add(branch!!) }
            Behavior.FE_CONVERTER -> {
                if(part.value>=.5) { source=PowerSource("fe_input",a);add(source!!) }
                else { branch=Resistor(a,null).setResistance(definition.nominalVoltage.pow(2)/definition.power);add(branch!!) }
            }
            Behavior.SHAFT_GENERATOR -> {
                source=PowerSource("generator",a);add(source!!)
            }
            Behavior.LAMP,Behavior.FURNACE,Behavior.MACERATOR,Behavior.COMPRESSOR,Behavior.PLATE_MACHINE,Behavior.SHAFT_MOTOR,Behavior.ACTIVE_COOLER -> {
                branch=Resistor(a,null).setResistance(definition.nominalVoltage.pow(2)/definition.power.coerceAtLeast(0.1));add(branch!!)
            }
            else -> {
                if (kind==Behavior.LOGIC || kind==Behavior.ANALOG || kind==Behavior.PROCESSOR) {
                    for(i in 1..3)pins[i]=load(.05,part.pinVoltages[i])
                }
                val internal=if(connectionKind(0)==1)null else load(1e-6,part.value)
                if(connectionKind(0)==1) {
                    gateOutput=mods.eln.sim.nbt.NbtElectricalGateOutputProcess("output",a).apply {
                        coulombs=Eln.gateOutputCurrent*.05/ForgePortConfig.simulationSteps.get()/Eln.SVU
                        setVoltageSafe(part.voltage)
                    };add(gateOutput!!)
                } else {
                    source=VoltageSource("source",internal,b).setVoltage(part.value)
                    add(source!!)
                    branch=Resistor(internal,a).setResistance(if (kind==Behavior.BATTERY) 1e-9 else 0.002)
                    add(branch!!)
                }
                if (kind==Behavior.BATTERY) {
                    battery=BatteryProcess(internal,b,Eln.instance.batteryVoltageFunctionTable,
                        definition.maxCurrent,source!!,batteryHeat).apply {
                        // Match BatteryDescriptor's integration-based capacity calibration.
                        uNominal=definition.nominalVoltage;QNominal=1.0
                        QNominal=definition.capacity*Eln.config.getDoubleOrElse("balance.storage.batteryCapacityFactor",1.0)/energyMax
                        life=part.life;charge=part.charge
                        isRechargeable=definition.id!="single_use_battery"
                    }
                    batteryAging=object : BatterySlowProcess(battery!!,batteryHeat) {
                        override fun destroy() { part.enabled=false;part.tripped=true }
                    }.apply {
                        lifeNominalCurrent=definition.power/definition.nominalVoltage
                        lifeNominalLost=0.5/(definition.batteryHalfLife*
                            Eln.config.getDoubleOrElse("runtime.items.batteries.standardHalfLifeTicks",2.0)/2.0)
                    }
                }
                if (kind==Behavior.LOGIC) {
                    logic=when(definition.id) {
                        "not_chip" -> Not(); "and_chip" -> And(); "nand_chip" -> Nand()
                        "or_chip" -> Or(); "nor_chip" -> Nor(); "xor_chip" -> Xor(); "xnor_chip" -> XNor()
                        "schmitt_trigger_chip" -> SchmittTrigger(); "oscillator_chip" -> Oscillator()
                        "d_flip_flop_chip" -> DFlipFlop(); "jk_flip_flop_chip" -> JKFlipFlop(); "pal_chip" -> Pal()
                        else -> error("Missing logic function: ${definition.id}")
                    }.also { it.readFromNBT(part.logicState,"") }
                    (logic as? Pal)?.let { pal -> for(i in 0..7)pal.truthTable[i]=(part.value.toInt() and (1 shl i))!=0 }
                    // Original port order: opposite the output, left, then right.
                    val inputOrder=intArrayOf(2,1,3)
                    for(i in logic!!.inputCount..2)pins[inputOrder[i]]=null
                }
                if (kind==Behavior.PROCESSOR) {
                    val eq=Equation();eq.setUpDefaultOperatorAndMapper();eq.iterationLimit=4096
                    for (i in 1..3) eq.symbolList.add(object : ISymbole {
                        override fun getName() = ('A'.code+i-1).toChar().toString()
                        override fun getValue() = (pins[i]?.voltage ?: 0.0)/5.0
                    })
                    eq.preProcess(part.expression);eq.readFromNBT(part.equationState,"");equation=eq
                }
                if (kind==Behavior.ANALOG) {
                    analog=when(definition.id) {
                        "opamp" -> OpAmp(); "pid_regulator" -> PIDRegulator()
                        "voltage_controlled_sawtooth_oscillator" -> VoltageControlledSawtoothOscillator()
                        "voltage_controlled_sine_oscillator" -> VoltageControlledSineOscillator()
                        "amplifier" -> Amplifier(); "voltage_controlled_amplifier" -> VoltageControlledAmplifier()
                        "configurable_summing_unit" -> SummingUnit(); "sample_and_hold" -> SampleAndHold()
                        "lowpass_filter" -> Filter()
                        else -> error("Missing analog function: ${definition.id}")
                    }.also { if(!part.analogState.isEmpty) it.readFromNBT(part.analogState,"") }
                    val params=part.expression.split(',').mapNotNull { it.trim().toDoubleOrNull()?.takeIf(Double::isFinite) }
                    when(val fn=analog) {
                        is Amplifier -> fn.gain=part.value
                        is Filter -> fn.feedback=2*PI*part.value.coerceIn(.001,30.0)
                        is PIDRegulator -> if(params.size==3) { fn.Kp=params[0];fn.Ki=params[1];fn.Kd=params[2] }
                        is SummingUnit -> if(params.size==3)params.forEachIndexed { i,v -> fn.gains[i]=v }
                    }
                    val inputOrder=intArrayOf(2,1,3)
                    for(i in analog!!.inputCount..2)pins[inputOrder[i]]=null
                }
            }
        }
        if(kind==Behavior.CABLE && definition.id!="signal_cable")replicatorFeed=ReplicatorCableLoad(this)
    }

    fun beforeTick() {
        simpleSignal?.let { it.beforeTick();return }
        if(kind==Behavior.TUTORIAL_SIGN)return
        computerProbe?.let { it.beforeTick();return }
        modbus?.let { it.beforeTick();return }
        mqttController?.let { it.beforeTick();return }
        processor?.let { it.beforeTick();return }
        if(vuMeter!=null)return
        energyMeter?.let { it.beforeTick();return }
        if(dataLogger!=null)return
        turret?.let { it.beforeTick();return }
        transporter?.let { it.beforeTick();return }
        autoMiner?.let { it.beforeTick();return }
        fabricator?.let { it.beforeTick();return }
        incubator?.let { it.beforeTick();return }
        if(railPower!=null)return
        if(powerAntenna!=null)return
        inventoryScanner?.let { it.beforeTick();return }
        if(bus!=null || powerSocket!=null || festive!=null || incubator!=null)return
        if(protection!=null)return
        wireless?.let { it.beforeTick();return }
        if(nixie!=null)return
        passive?.let { it.beforeTick();return }
        contact?.let { it.beforeTick();return }
        if(hub!=null)return
        timer?.let { it.beforeTick();return }
        if(alarm!=null)return
        watch?.let { it.beforeTick();return }
        environment?.let { it.beforeTick();return }
        charger?.let { it.beforeTick();return }
        renewable?.let { it.beforeTick();return }
        fuelGenerator?.let { it.beforeTick();return }
        if(rolling!=null)return
        shaftElectrical?.let { thermal?.beforeTick();it.beforeTick();return }
        if(clutch!=null || radial!=null || turbine!=null || resinCollector!=null)return
        floodlight?.let { it.beforeTick();return }
        emergencyLamp?.let { it.beforeTick();return }
        lamp?.let { it.beforeTick();return }
        if(lampSupply!=null || festive!=null)return
        dcDc?.let { it.beforeTick();return }
        processing?.let { it.beforeTick();return }
        solar?.let { it.beforeTick();return }
        grid?.let { it.beforeTick();return }
        sensor?.let { it.beforeStep();return }
        thermal?.beforeTick()
        if(kind==Behavior.DIODE) {
            val contact=branch as ResistorSwitch
            val on=part.voltage-part.voltageB>0
            diodeHeatTransition=contact.state!=on;contact.state=on;part.gridState.putBoolean("diodeOn",on)
        }
        if(kind==Behavior.FURNACE)return
        updateSignalControls(.05)
        if(kind==Behavior.CRANK) {
            val speed=circuit.mechanics.speed(part)
            val input=part.crankInput-.02*speed.coerceAtLeast(1.0);part.crankInput=0.0
            if(speed<=20) { if(input>=0)circuit.mechanics.add(part,input)else circuit.mechanics.consume(part,-input);power=input.coerceAtLeast(0.0)/.05 }
            else power=0.0
        }
        if (kind in setOf(Behavior.FURNACE,Behavior.MACERATOR,Behavior.COMPRESSOR,Behavior.PLATE_MACHINE,Behavior.ROLLING_SHAFT)) {
            val input=node.inventory.getStackInSlot(0)
            var recipe="";cookOutput=ItemStack.EMPTY
            if (!input.isEmpty && kind==Behavior.FURNACE) {
                val found=circuit.level.recipeManager.getRecipeFor(RecipeType.SMELTING,SimpleContainer(input),circuit.level)
                if (found.isPresent) {
                    val r=found.get();cookOutput=r.resultItem.copy();cookEnergy=definition.power*r.cookingTime/20.0;recipe=r.id.toString()
                }
            } else if (!input.isEmpty) {
                val result=MachineRecipes.find(if(kind==Behavior.ROLLING_SHAFT)Behavior.PLATE_MACHINE else kind,input,circuit.level)
                if (result!=null) { cookOutput=result.output();cookEnergy=result.joules;recipe=result.id.toString() }
            }
            if (recipe!=lastRecipe) { part.progress=0.0;lastRecipe=recipe;part.recipeId=recipe }
            val output=node.inventory.getStackInSlot(2)
            val room=!cookOutput.isEmpty && (output.isEmpty ||
                ItemStack.isSameItemSameTags(output,cookOutput) && output.count+cookOutput.count<=output.maxStackSize)
            (branch as? Resistor)?.resistance=if (room && part.enabled) definition.nominalVoltage.pow(2)/definition.power else 1e12
            if (!room) cookOutput=ItemStack.EMPTY
        }
        if (kind==Behavior.FE_CONVERTER && part.value<.5) (branch as? Resistor)?.resistance=if (node.energyBuffer.energy<99900 && part.enabled)
            definition.nominalVoltage.pow(2)/definition.power else 1e12
    }

    fun beforeStep(dt: Double) {
        if(simpleSignal!=null)return
        if(kind==Behavior.TUTORIAL_SIGN || computerProbe!=null || modbus!=null || mqttController!=null || processor!=null || vuMeter!=null)return
        if(energyMeter!=null)return
        if(dataLogger!=null)return
        replicatorFeed?.beforeStep()
        if(turret!=null || transporter!=null || autoMiner!=null || fabricator!=null)return
        railPower?.let { it.beforeStep(dt);return }
        powerAntenna?.let { it.beforeStep(dt);return }
        inventoryScanner?.let { it.beforeStep();return }
        if(bus!=null || powerSocket!=null || festive!=null || incubator!=null)return
        protection?.let { it.beforeStep();return }
        wireless?.let { it.beforeStep(dt);return }
        if(nixie!=null)return
        if(passive!=null)return
        contact?.let { it.beforeStep();return }
        if(hub!=null)return
        timer?.let { it.beforeStep();return }
        if(alarm!=null)return
        if(watch!=null)return
        environment?.let { it.beforeStep();return }
        if(charger!=null)return
        if(renewable!=null)return
        fuelGenerator?.let { it.beforeStep(dt);return }
        if(rolling!=null)return
        shaftElectrical?.let { it.beforeStep(dt);return }
        tachometer?.let { it.beforeStep();return }
        if(grid!=null)return
        if(sensor!=null)return
        val world=circuit.level; val pos=node.blockPos
        when (kind) {
            Behavior.VOLTAGE_SOURCE -> source?.voltage=if(part.enabled) part.value else 0.0
            Behavior.SIGNAL_SOURCE -> gateOutput?.setVoltageSafe(if(part.enabled)part.value else 0.0)
            Behavior.CURRENT_SOURCE -> currentSource?.current=if(part.enabled) part.value else 0.0
            Behavior.BATTERY -> {
                source?.voltage=battery!!.computeVoltage()
                (branch as Resistor).resistance=if(part.enabled) 1e-9 else 1e12
            }
            Behavior.BUTTON -> gateOutput?.setVoltageSafe(if(part.timer>0)5.0 else 0.0)
            Behavior.REDSTONE_INPUT -> gateOutput?.setVoltageSafe(world.getBestNeighborSignal(pos)*5.0/15)
            Behavior.DAYLIGHT_SENSOR -> gateOutput?.setVoltageSafe(world.getBrightness(LightLayer.SKY,pos).toDouble()/15.0*5.0*
                max(0.0,cos(world.getSunAngle(1f).toDouble())))
            Behavior.LIGHT_SENSOR -> gateOutput?.setVoltageSafe(max(world.getBrightness(LightLayer.SKY,pos),world.getBrightness(LightLayer.BLOCK,pos))*5.0/15)
            Behavior.WEATHER_SENSOR -> gateOutput?.setVoltageSafe(if(world.isThundering)5.0 else if(world.isRaining)2.5 else 0.0)
            Behavior.WIRELESS_RX -> gateOutput?.setVoltageSafe(circuit.wireless(part,node))
            Behavior.SWITCH,Behavior.BREAKER -> (branch as ResistorSwitch).state=part.enabled && !part.tripped
            Behavior.RELAY -> (branch as ResistorSwitch).state=(pins[1]?.voltage ?: 0.0)>part.value && part.enabled
            Behavior.DIODE -> Unit
            Behavior.FE_CONVERTER -> {
                if(part.value>=.5) (source as PowerSource).apply {
                    setMaximumVoltage(definition.nominalVoltage);setMaximumCurrent(definition.power/definition.nominalVoltage)
                    power=if(part.enabled)minOf(definition.power,node.energyBuffer.energy/ForgePortConfig.fePerJoule.get()*ForgePortConfig.conversionEfficiency.get()/dt) else 0.0
                }
            }
            else -> Unit
        }
    }
    fun afterStep(dt: Double) {
        simpleSignal?.let { it.afterStep(dt);return }
        if(kind==Behavior.TUTORIAL_SIGN)return
        computerProbe?.let { it.afterStep();return }
        modbus?.let { it.afterStep();return }
        mqttController?.let { it.afterStep();return }
        processor?.let { it.afterStep();return }
        vuMeter?.let { it.afterStep();return }
        energyMeter?.let { it.afterStep(dt);return }
        dataLogger?.let { it.afterStep(dt);return }
        replicatorFeed?.afterStep(dt)
        turret?.let { it.afterStep(dt);return }
        transporter?.let { it.afterStep(dt);return }
        autoMiner?.let { it.afterStep(dt);return }
        fabricator?.let { it.afterStep(dt);return }
        incubator?.let { it.afterStep(dt);return }
        railPower?.let { it.afterStep(dt);return }
        powerAntenna?.let { it.afterStep(dt);return }
        inventoryScanner?.let { it.afterStep();return }
        bus?.let { it.afterStep();return }
        powerSocket?.let { it.afterStep(dt);return }
        protection?.let { it.afterStep(dt);return }
        wireless?.let { it.afterStep();return }
        nixie?.let { it.afterStep();return }
        passive?.let { it.afterStep(dt);return }
        contact?.let { it.afterStep(dt);return }
        hub?.let { it.afterStep(dt);return }
        timer?.let { it.afterStep();return }
        alarm?.let { it.afterStep();return }
        if(watch!=null)return
        environment?.let { it.afterStep(dt);return }
        charger?.let { it.afterStep(dt);return }
        renewable?.let { it.afterStep(dt);return }
        fuelGenerator?.let { it.afterStep(dt);return }
        if(rolling!=null)return
        shaftElectrical?.let { it.afterStep(dt);return }
        if(resinCollector!=null)return
        radial?.let { it.afterStep();return }
        turbine?.let { it.afterStep();return }
        clutch?.let { it.afterStep();return }
        tachometer?.let { it.afterStep();return }
        floodlight?.let { it.afterStep(dt);return }
        emergencyLamp?.let { it.afterStep(dt);return }
        lamp?.let { it.afterStep(dt);return }
        festive?.let { it.afterStep(dt);return }
        lampSupply?.let { it.afterStep(dt);return }
        dcDc?.let { it.afterStep(dt);return }
        processing?.let { it.afterStep(dt);return }
        solar?.let { it.afterStep(dt);return }
        grid?.let { it.afterStep(dt);return }
        sensor?.let { it.afterStep(dt);return }
        if(kind in setOf(Behavior.HEAT_TURBINE,Behavior.FURNACE)) { thermal?.afterElectricalStep(dt);return }
        if(kind in setOf(Behavior.THERMAL_CABLE,Behavior.THERMAL_SENSOR,Behavior.AMBIENT_SENSOR,Behavior.PASSIVE_COOLER,Behavior.HEAT_FURNACE,Behavior.FUEL_HEAT_FURNACE,Behavior.HEAT_EXCHANGER))return
        if(definition.mechanical && kind !in setOf(Behavior.SHAFT_MOTOR,Behavior.SHAFT_GENERATOR)) {
            part.energy+=power*dt;return
        }
        for(i in 0..3)part.pinVoltages[i]=pins[i]?.voltage ?: 0.0
        val previousVoltage=part.voltage-part.voltageB
        part.voltage=pins[0]?.voltage ?: 0.0
        if(definition.id=="digital_display")part.voltage=pins[2]?.voltage ?: 0.0
        part.voltageB=if(definition.bipolar) pins[2]?.voltage ?: 0.0 else 0.0
        part.current=when {
            branch is Capacitor -> part.value*((part.voltage-part.voltageB)-previousVoltage)/dt
            branch is Transformer -> (branch as Transformer).aCurrentState.state
            kind==Behavior.CABLE -> pins[0]?.current ?: 0.0
            gateOutput!=null -> (gateOutput!!.voltage-(pins[0]?.voltage ?: 0.0))*gateOutput!!.coulombs/dt
            source!=null -> source!!.current
            branch!=null -> branch!!.current
            else -> pins[0]?.current ?: 0.0
        }
        battery?.let { it.process(dt);part.charge=it.charge.coerceIn(0.0,1.05);it.charge=part.charge }
        if (branch is Inductor) part.inductorCurrent=(branch as Inductor).currentState.state
        if (!part.voltage.isFinite() || !part.current.isFinite()) throw IllegalStateException("Nonfinite component state at ${node.blockPos}")
        power=abs((part.voltage-part.voltageB)*part.current)
        if(kind in setOf(Behavior.FE_CONVERTER,Behavior.SHAFT_GENERATOR) && source!=null) {
            val generated=maxOf(0.0,source!!.voltage*source!!.current)
            if(kind==Behavior.FE_CONVERTER)node.energyBuffer.remove(generated*dt*ForgePortConfig.fePerJoule.get()/ForgePortConfig.conversionEfficiency.get())
            
        }
        if(kind==Behavior.FE_CONVERTER && part.value<.5)node.energyBuffer.add(power*dt*ForgePortConfig.fePerJoule.get()*ForgePortConfig.conversionEfficiency.get())
        part.energy+=power*dt
        if (part.enabled && !cookOutput.isEmpty) part.progress+=power*dt
        val heat=when(kind) {
            Behavior.CABLE,Behavior.SWITCH,Behavior.BREAKER,Behavior.RELAY -> part.current.pow(2)*part.resistance*2
            Behavior.DIODE -> if(diodeHeatTransition)0.0 else (branch as ResistorSwitch).power.coerceAtLeast(0.0)
            Behavior.RESISTOR -> power
            Behavior.BATTERY -> part.current.pow(2)*part.resistance*2
            else -> 0.0
        }
        thermal?.addHeat(heat,dt)
        if(kind==Behavior.ACTIVE_COOLER)thermal?.electricalPower(power,dt)
    }
    /** Original control functions sample at the 20 Hz device rate; MNA can use smaller integration steps. */
    private fun updateSignalControls(dt: Double) {
        when(kind) {
            Behavior.LOGIC -> {
                LogicTiming.stepSeconds=dt
                val inputs=arrayOf(2,1,3).map { i -> if(part.connectedMask and (1 shl i)==0)null else (pins[i]?.voltage ?: 0.0)/Eln.SVU }.toTypedArray()
                gateOutput?.setVoltageSafe(if(part.enabled && logic!!.process(inputs))Eln.SVU else 0.0)
            }
            Behavior.PROCESSOR -> gateOutput?.setVoltageSafe((if(part.enabled)equation?.getValue(dt) ?: 0.0 else 0.0).let { if(it.isFinite())it.coerceIn(0.0,1.0)*5 else 0.0 })
            Behavior.ANALOG -> {
                val inputs=arrayOf(2,1,3).map { i -> if(part.connectedMask and (1 shl i)==0)null else pins[i]?.voltage }.toTypedArray()
                val output=if(part.enabled)analog!!.process(inputs,dt)else 0.0
                gateOutput?.setVoltageSafe(if(output.isFinite())output.coerceIn(0.0,Eln.SVU)else 0.0)
            }
            else -> Unit
        }
    }
    fun fastStep(dt: Double) { timer?.fastStep(dt) }
    fun afterTick() {
        simpleSignal?.let { it.afterTick();return }
        if(kind==Behavior.TUTORIAL_SIGN || computerProbe!=null || modbus!=null || mqttController!=null)return
        processor?.let { it.persist();return }
        if(vuMeter!=null)return
        energyMeter?.let { it.afterTick();return }
        turret?.let { it.afterTick();return }
        transporter?.let { it.afterTick();return }
        autoMiner?.let { it.afterTick();return }
        fabricator?.let { it.afterTick();return }
        if(railPower!=null)return
        powerAntenna?.let { it.afterTick();return }
        if(inventoryScanner!=null)return
        if(bus!=null)return
        powerSocket?.let { it.afterTick();return }
        protection?.let { it.afterTick();return }
        if(wireless!=null)return
        nixie?.let { it.afterTick();return }
        passive?.let { it.afterTick();return }
        contact?.let { it.afterTick();return }
        hub?.let { it.afterTick();return }
        timer?.let { it.afterTick();return }
        alarm?.let { it.afterTick();return }
        if(watch!=null)return
        environment?.let { it.afterTick();return }
        charger?.let { it.afterTick();return }
        if(renewable!=null)return
        fuelGenerator?.let { it.afterTick();return }
        rolling?.let { it.afterTick();return }
        shaftElectrical?.let { it.afterTick();return }
        resinCollector?.let { it.afterTick();return }
        radial?.let { it.afterTick();return }
        turbine?.let { it.afterTick();return }
        if(clutch!=null)return
        tachometer?.let { it.afterTick();return }
        floodlight?.let { it.afterTick();return }
        emergencyLamp?.let { it.afterTick();return }
        lamp?.let { it.afterTick();return }
        incubator?.let { it.afterTick();return }
        if(festive!=null)return
        lampSupply?.let { it.afterTick();return }
        dcDc?.let { it.afterTick();return }
        processing?.let { it.afterTick();return }
        if(solar!=null)return
        grid?.let { it.afterTick();return }
        sensor?.let { it.afterTick();return }
        thermal?.afterTick()
        if(kind in setOf(Behavior.THERMAL_CABLE,Behavior.THERMAL_SENSOR,Behavior.AMBIENT_SENSOR,Behavior.PASSIVE_COOLER,Behavior.HEAT_FURNACE,Behavior.FUEL_HEAT_FURNACE,Behavior.HEAT_TURBINE,Behavior.HEAT_EXCHANGER,Behavior.FURNACE)) { persist();return }
        batteryAging?.process(0.05)
        battery?.let { part.life=it.life;part.charge=it.charge }
        persist()
        if(definition.id=="digital_display") {
            part.displayReading=DisplaySettings.reading(part.voltage,part.expression)
            part.displayBits=(((pins[0]?.voltage ?: 0.0)/5).toFloat()*256).toInt().coerceIn(0,255)
            part.displayBlank=((pins[1]?.voltage ?: 0.0)/5).toFloat()>=.5f
        }
        if(part.timer>0) part.timer--
        if(kind==Behavior.BREAKER && abs(part.current)>definition.maxCurrent) {
            overloadedTicks++
            if(overloadedTicks>=2) { part.tripped=true;part.enabled=false }
        } else overloadedTicks=0
        if(kind==Behavior.LOGGER && dataLogger==null && circuit.level.gameTime%10L==0L) {
            part.history.add(part.voltage);if(part.history.size>120)part.history.removeAt(0)
        }
        if(kind==Behavior.REDSTONE_OUTPUT) {
            val signal=(part.voltage/5*15).roundToInt().coerceIn(0,15)
            if(signal!=part.signal) { part.signal=signal;circuit.level.updateNeighborsAt(node.blockPos,node.blockState.block) }
        }
        if(kind==Behavior.LAMP) {
            val light=(power/definition.power*15).roundToInt().coerceIn(0,15)
            if(light!=part.signal) { part.signal=light;node.refreshLight();node.sync() }
        }
        if(kind==Behavior.FE_CONVERTER)node.transferEnergy()
        if(part.enabled && !cookOutput.isEmpty && part.progress>=cookEnergy) {
            val output=node.inventory.getStackInSlot(2)
            if(output.isEmpty) node.inventory.setStackInSlot(2,cookOutput.copy()) else { output.grow(cookOutput.count);node.inventory.setStackInSlot(2,output) }
            node.inventory.extractItem(0,1,false);part.progress-=cookEnergy;node.setChanged()
        }
        val wasActive=part.active
        part.active=when(kind) {
            Behavior.RELAY -> (branch as ResistorSwitch).state
            Behavior.SWITCH,Behavior.BREAKER -> part.enabled && !part.tripped
            Behavior.BUTTON -> part.timer>0
            Behavior.FURNACE,Behavior.MACERATOR,Behavior.COMPRESSOR,Behavior.PLATE_MACHINE,Behavior.ROLLING_SHAFT -> part.enabled && !cookOutput.isEmpty && power>1.0
            else -> part.enabled && power>1.0
        }
        part.powerFactor=if(part.active)(power/definition.power.coerceAtLeast(.1)).coerceIn(0.0,4.0) else 0.0
        part.processFraction=(part.progress/cookEnergy.coerceAtLeast(.1)).coerceIn(0.0,1.0)
        if(wasActive!=part.active)node.sync()
        var cableFault=false
        if(kind==Behavior.CABLE) {
            val overflow=abs(part.voltage)-definition.nominalVoltage*1.3
            if(overflow<=0 || part.wasOvervoltage)part.voltageFault=(part.voltageFault+overflow*.05).coerceAtLeast(0.0)
            part.wasOvervoltage=overflow>0
            cableFault=(part.voltageFault>definition.nominalVoltage*.25 && ForgePortConfig.cableVoltageProtection.get() &&
                Eln.config.getBooleanOrElse("simulation.watchdog.destruction.voltage",true))
        }
        if(cableFault) {
            node.parts[part.face.get3DDataValue()]=null
            node.changedTopology()
            if(node.parts.all { it==null }) circuit.level.removeBlock(node.blockPos,false)
        }
    }
    fun persist() {
        processor?.persist()
        turret?.persist()
        powerAntenna?.persist()
        bus?.persist()
        protection?.persist()
        passive?.persist()
        contact?.persist()
        timer?.persist();alarm?.persist()
        environment?.persist()
        charger?.persist()
        renewable?.persist()
        radial?.persist()
        turbine?.persist()
        lamp?.persist()
        thermal?.persist()
        equation?.writeToNBT(part.equationState,"")
        logic?.writeToNBT(part.logicState,"")
        analog?.writeToNBT(part.analogState,"")
    }
}
