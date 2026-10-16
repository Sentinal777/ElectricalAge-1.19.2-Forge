package mods.eln.modern.client

import mods.eln.Eln
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

/** Isolated developer QA only; excluded from normal builds and the release JAR. */
@Mod.EventBusSubscriber(modid="eln",value=[Dist.CLIENT])
object ClientSmoke {
    private var started=false
    private var prepared=false
    private var ticks=0
    @JvmStatic @SubscribeEvent fun tick(event: TickEvent.ClientTickEvent) {
        if(!java.lang.Boolean.getBoolean("eln.clientSmoke") || event.phase!=TickEvent.Phase.END)return
        val mc=Minecraft.getInstance()
        if(mc.options.tutorialStep!=net.minecraft.client.tutorial.TutorialSteps.NONE)mc.tutorial.setStep(net.minecraft.client.tutorial.TutorialSteps.NONE)
        if(!started && mc.screen is TitleScreen) {
            started=true;Eln.LOGGER.info("ELN_CLIENT_QA opening isolated ELN-QA world")
            mc.window.setTitle("ELN isolated QA - Forge 43.5.0")
            mc.options.pauseOnLostFocus=false
            mc.createWorldOpenFlows().loadLevel(mc.screen!!,"ELN-QA")
        }
        val prompt=mc.screen
        if(started && prompt is net.minecraft.client.gui.screens.ConfirmScreen && prompt.title.string.contains("Experimental Settings")) {
            prompt.children().filterIsInstance<net.minecraft.client.gui.components.Button>()
                .firstOrNull { it.message.string=="Proceed" }?.onPress()
        }
        val player=mc.player ?: return
        val server=mc.singleplayerServer ?: return
        if(java.lang.Boolean.getBoolean("eln.turbineOnly")) { TurbineClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.shaftElectricalOnly")) { ShaftElectricalClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.rollingOnly")) { RollingClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.largeShaftOnly")) { LargeShaftClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.fuelGeneratorOnly")) { FuelGeneratorClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.renewableOnly")) { RenewableClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.chargerOnly")) { ChargerClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.portableLightOnly")) { PortableLightClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.portableToolOnly")) { PortableToolClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.armorOnly")) { ArmorClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.scannerOnly")) { ScannerClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.environmentOnly")) { EnvironmentClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.watchOnly")) { WatchClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.timerAlarmOnly")) { TimerAlarmClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.hubOnly")) { HubClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.contactOnly")) { ContactClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.passiveOnly")) { PassiveClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.rheostatNixieOnly")) { RheostatNixieClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.wirelessOnly")) { WirelessClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.protectionOnly")) { ProtectionClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.paintedPowerOnly")) { PaintedPowerClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.powerAntennaOnly")) { PowerAntennaClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.railOnly")) { RailClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.festiveOnly")) { FestiveClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.fabricatorOnly")) { FabricatorClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.autoMinerOnly")) { AutoMinerClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.transporterOnly")) { TransporterClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.turretOnly")) { TurretClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.replicatorToolsOnly")) { ReplicatorToolsClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.loggerOnly")) { DataLoggerClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.energyMeterOnly")) { EnergyMeterClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.signalDevicesOnly")) { SignalDeviceClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.mqttOnly")) { MqttClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.modbusOnly")) { ModbusClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.probeTutorialOnly")) { ProbeTutorialClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.simpleSignalOnly")) { SimpleSignalClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.bcceFactoryOnly")) { BcceFactoryClientSmoke.tick(mc,server);return }
        if(java.lang.Boolean.getBoolean("eln.nodeCoversOnly")) { NodeCoverClientSmoke.tick(mc,server);return }
        if(!prepared) {
            prepared=true
            server.execute {
                val world=server.overworld();world.dayTime=6000
                PartDefinition.all.forEachIndexed { index,definition ->
                    val x=index%14*3;val z=index/14*3
                    world.setBlockAndUpdate(BlockPos(x,119,z),Blocks.STONE.defaultBlockState())
                    val pos=BlockPos(x,120,z)
                    world.setBlockAndUpdate(pos,(if(definition.machine)Content.machine.get() else Content.node.get()).defaultBlockState())
                    val entity=world.getBlockEntity(pos) as FaceNodeEntity
                    val face=if(definition.behavior==Behavior.RESIN_COLLECTOR)Direction.WEST else Direction.from3DDataValue(definition.lamp?.allowedFaces?.firstOrNull() ?: 0)
                    world.setBlockAndUpdate(pos.relative(face),(if(definition.behavior==Behavior.RESIN_COLLECTOR)Blocks.OAK_LOG else Blocks.STONE).defaultBlockState())
                    entity.parts[face.get3DDataValue()]=PartState(definition,face).apply { if(definition.behavior==Behavior.RESIN_COLLECTOR)rotation=FaceBasis.edges(face,0).indexOf(Direction.DOWN) };entity.changedTopology()
                }
                val viewer=server.playerList.players.first()
                viewer.setGameMode(net.minecraft.world.level.GameType.CREATIVE)
                viewer.abilities.flying=true;viewer.onUpdateAbilities()
                viewer.teleportTo(world,18.0,122.0,-8.0,0f,25f)
                Eln.LOGGER.info("ELN_CLIENT_QA placed {} component variants",PartDefinition.all.size)
            }
        }
        ticks++
        if(ticks==60) {
            if(java.lang.Boolean.getBoolean("eln.bootstrapOnly"))ticks=5159
            else if(java.lang.Boolean.getBoolean("eln.clutchOnly"))ticks=4559
            else if(java.lang.Boolean.getBoolean("eln.floodlightOnly"))ticks=3969
            else if(java.lang.Boolean.getBoolean("eln.lightingOnly"))ticks=3169
            else if(java.lang.Boolean.getBoolean("eln.dcDcOnly"))ticks=2609
            else if(java.lang.Boolean.getBoolean("eln.equipmentOnly"))ticks=1889
            else if(java.lang.Boolean.getBoolean("eln.steamOnly"))ticks=1399
            else if(java.lang.Boolean.getBoolean("eln.thermalOnly"))ticks=979
            else if(java.lang.Boolean.getBoolean("eln.probeOnly"))ticks=739
        }
        if(ticks==120) {
            mc.setScreen(null)
            PartDefinition.all.filter { it.model.isNotEmpty() }.forEach { requireNotNull(LegacyMeshes.model(it.model)) { "Missing model ${it.id}" } }
            Screenshot.grab(mc.gameDirectory,"eln-gallery.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        }
        if(ticks==180)server.execute { server.playerList.players.first().teleportTo(server.overworld(),12.0,122.0,16.0,0f,25f) }
        if(ticks==240)Screenshot.grab(mc.gameDirectory,"eln-mechanical-gallery.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        if(ticks==280) {
            val i=PartDefinition.all.indexOfFirst { it.id=="energy_converter" }
            val pos=BlockPos(i%14*3,120,i/14*3)
            open(server,pos)
        }
        if(ticks==310)(mc.screen as DeviceScreen).children().filterIsInstance<net.minecraft.client.gui.components.Button>()
            .first { it.message.string.contains("Electrical") }.onPress()
        if(ticks==335) {
            require((mc.screen as DeviceScreen).menu.part!!.value==1.0) { "Server did not confirm converter direction" }
            Eln.LOGGER.info("ELN_CLIENT_QA server confirmed converter GUI direction")
        }
        if(ticks==340)Screenshot.grab(mc.gameDirectory,"eln-converter-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        if(ticks==350) {
            val i=PartDefinition.all.indexOfFirst { it.id=="digital_display" }
            val pos=BlockPos(i%14*3,120,i/14*3)
            open(server,pos)
        }
        if(ticks==370) {
            val screen=mc.screen as DeviceScreen
            screen.children().filterIsInstance<net.minecraft.client.gui.components.EditBox>().first().value="1000,2000"
            screen.children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="Apply" }.onPress()
        }
        if(ticks==385) {
            require((mc.screen as DeviceScreen).menu.part!!.expression=="1000,2000") { "Server did not confirm display range" }
            Eln.LOGGER.info("ELN_CLIENT_QA server confirmed display GUI range")
        }
        if(ticks==390)Screenshot.grab(mc.gameDirectory,"eln-display-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        if(ticks==410) {
            mc.setScreen(null)
            server.execute {
                val world=server.overworld()
                fun device(id: String,x: Int,z: Int,rotation: Int=0): FaceNodeEntity {
                    world.setBlockAndUpdate(BlockPos(x,119,z),Blocks.STONE.defaultBlockState())
                    val pos=BlockPos(x,120,z);val d=PartDefinition.byId.getValue(id)
                    (world.getBlockEntity(pos) as? FaceNodeEntity)?.let { old ->
                        mods.eln.modern.grid.GridNetwork.removeAt(world,pos,false)
                        old.parts.filterNotNull().forEach { mods.eln.modern.grid.GridMultiblock.remove(old,it) }
                    }
                    world.setBlockAndUpdate(pos,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
                    val n=world.getBlockEntity(pos) as FaceNodeEntity
                    n.parts[0]=PartState(d,Direction.DOWN).apply { this.rotation=rotation };n.changedTopology()
                    mods.eln.modern.grid.GridMultiblock.install(n,n.parts[0]!!);return n
                }
                device("electrical_source",87,80).parts[0]!!.value=200.0
                val direct=device("direct_utility_pole",88,80);val pole=device("utility_pole",100,80)
                val transformer=device("grid_dc_dc_converter",107,80);val ground=device("utility_pole_w_dc_dc_converter",114,80)
                device("creative_power_resistor",115,80,2);device("ground_cable",116,80)
                val tower=device("transmission_tower",100,104);val isolator=device("grid_switch",112,104);val end=device("direct_utility_pole",124,104)
                device("creative_power_resistor",125,104,2);device("ground_cable",126,104)
                fun wire(a: FaceNodeEntity,b: FaceNodeEntity,side: Direction=Direction.NORTH) {
                    mods.eln.modern.grid.GridNetwork.link(world,a,side,b,Direction.NORTH)
                }
                wire(direct,pole);wire(pole,transformer);wire(transformer,ground,Direction.SOUTH)
                wire(pole,tower);wire(tower,isolator);wire(isolator,end,Direction.SOUTH)
                server.playerList.players.first().teleportTo(world,108.0,130.0,60.0,0f,20f)
                Eln.LOGGER.info("ELN_CLIENT_QA placed and connected all six original grid devices")
            }
        }
        if(ticks==570) {
            require(mc.level!!.getBlockEntity(BlockPos(115,120,80)) is FaceNodeEntity)
            Eln.LOGGER.info("ELN_CLIENT_QA grid scene loaded")
            Screenshot.grab(mc.gameDirectory,"eln-grid-yard.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
            server.execute { server.playerList.players.first().teleportTo(server.overworld(),118.0,124.0,112.0,145f,15f) }
        }
        if(ticks==700)Screenshot.grab(mc.gameDirectory,"eln-grid-switch.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        if(ticks==740)server.execute {
            val world=server.overworld()
            fun device(id: String,z: Int,rotation: Int=0) {
                val d=PartDefinition.byId.getValue(id);val pos=BlockPos(140,120,z)
                world.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
                world.setBlockAndUpdate(pos,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
                val n=world.getBlockEntity(pos) as FaceNodeEntity;n.parts[0]=PartState(d,Direction.DOWN).apply { this.rotation=rotation };n.changedTopology()
            }
            device("electrical_source",85);device("low_voltage_cable",84);device("electrical_probe",83)
            device("creative_power_resistor",82,3);device("ground_cable",81)
            val viewer=server.playerList.players.first()
            viewer.inventory.setItem(0,net.minecraft.world.item.ItemStack(Content.partItems.getValue("low_voltage_cable").get(),4))
            viewer.inventoryMenu.broadcastChanges()
            viewer.teleportTo(world,140.5,121.8,82.0,0f,25f)
        }
        if(ticks==760)open(server,BlockPos(140,120,83))
        if(ticks==772) {
            Eln.LOGGER.info("ELN_CLIENT_QA probe screen={} menu={} slots={}",mc.screen?.javaClass?.simpleName,player.containerMenu.javaClass.simpleName,player.containerMenu.slots.size)
            server.execute { val v=server.playerList.players.first();Eln.LOGGER.info("ELN_CLIENT_QA server menu={} valid={} position={}",v.containerMenu.javaClass.simpleName,v.containerMenu.stillValid(v),v.position()) }
        }
        if(ticks==780) {
            val screen=mc.screen as DeviceScreen
            mc.gameMode!!.handleInventoryMouseClick(screen.menu.containerId,screen.menu.slots.size-9,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,player)
        }
        if(ticks==815) {
            val menu=(mc.screen as DeviceScreen).menu
            require(PartInventory.get(menu.node,menu.part!!).getStackInSlot(0).count==4) { "Server did not confirm component inventory transfer" }
            require(menu.part!!.voltage>49.9) { "Inserted cable did not connect probe" }
            Eln.LOGGER.info("ELN_CLIENT_QA server confirmed probe cable inventory transaction")
            (mc.screen as DeviceScreen).children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="Voltage" }.onPress()
        }
        if(ticks==850) {
            require((mc.screen as DeviceScreen).menu.part!!.value==0.0)
            (mc.screen as DeviceScreen).children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="Power" }.onPress()
        }
        if(ticks==875) {
            require((mc.screen as DeviceScreen).menu.part!!.value==1.0)
            val screen=mc.screen as DeviceScreen
            screen.children().filterIsInstance<net.minecraft.client.gui.components.EditBox>().first().value="0,1"
            screen.children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="Apply" }.onPress()
        }
        if(ticks==910) {
            val p=(mc.screen as DeviceScreen).menu.part!!
            require(p.expression=="0,1" && kotlin.math.abs(p.pinVoltages[0]-2.5)<.01) { "Probe GUI configuration did not affect the real circuit" }
            (mc.screen as DeviceScreen).children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="Absolute" }.onPress()
        }
        if(ticks==935) {
            require((mc.screen as DeviceScreen).menu.part!!.channel=="forward")
            Eln.LOGGER.info("ELN_CLIENT_QA server confirmed probe current/range/direction controls")
            Screenshot.grab(mc.gameDirectory,"eln-probe-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        }
        if(ticks==960 && java.lang.Boolean.getBoolean("eln.probeOnly") && !java.lang.Boolean.getBoolean("eln.thermalOnly")) { Eln.LOGGER.info("ELN_CLIENT_QA probe checks complete");mc.stop() }
        if(ticks==980)server.execute {
            val world=server.overworld()
            for((id,z,rotation) in listOf(Triple("stone_heat_furnace",83,3),Triple("copper_thermal_cable",84,0),Triple("small_passive_thermal_dissipator",85,0))) {
                val d=PartDefinition.byId.getValue(id);val pos=BlockPos(140,120,z)
                world.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
                world.setBlockAndUpdate(pos,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
                val n=world.getBlockEntity(pos) as FaceNodeEntity;n.parts[0]=PartState(d,Direction.DOWN).apply { this.rotation=rotation };n.changedTopology()
            }
            val viewer=server.playerList.players.first()
            viewer.inventory.setItem(0,net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL,8))
            for((index,id,count) in listOf(Triple(1,"analogic_regulator",1),Triple(2,"thermal_insulation",1),Triple(3,"combustion_chamber",8)))
                viewer.inventory.setItem(index,net.minecraft.world.item.ItemStack(Content.materialItems.getValue(id).get(),count))
            viewer.inventoryMenu.broadcastChanges();viewer.teleportTo(world,140.5,121.8,82.0,0f,25f)
        }
        if(ticks==1020)open(server,BlockPos(140,120,83))
        if(ticks in listOf(1040,1060,1080,1100)) {
            val menu=(mc.screen as DeviceScreen).menu
            mc.gameMode!!.handleInventoryMouseClick(menu.containerId,menu.slots.size-9+(ticks-1040)/20,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,player)
        }
        if(ticks==1120) {
            val screen=mc.screen as DeviceScreen;val p=screen.menu.part!!;val inventory=PartInventory.get(screen.menu.node,p)
            require(inventory.getStackInSlot(0).count==8 && inventory.getStackInSlot(1).count==1 && inventory.getStackInSlot(2).count==1 && inventory.getStackInSlot(3).count==8) { "Heat furnace GUI lost an inventory transaction" }
            require(!p.enabled && p.fuelEnergy==0.0)
            val fields=screen.children().filterIsInstance<net.minecraft.client.gui.components.EditBox>()
            fields[0].value="0.5";fields[1].value="200"
            screen.children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="Apply" }.onPress()
            Eln.LOGGER.info("ELN_CLIENT_QA heat furnace fuel and three upgrade slots confirmed by server")
        }
        if(ticks==1140) {
            val screen=mc.screen as DeviceScreen;require(screen.menu.part!!.expression=="200")
            screen.children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="Add fuel" }.onPress()
        }
        if(ticks==1180) {
            val screen=mc.screen as DeviceScreen;val p=screen.menu.part!!
            require(p.enabled && p.fuelEnergy>0 && p.thermalState.getDouble("delta")>10) { "Heat furnace GUI failed to start actual combustion" }
            screen.children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="Internal" }.onPress()
        }
        if(ticks==1210) {
            val screen=mc.screen as DeviceScreen;val p=screen.menu.part!!
            require(p.channel=="external" && !p.enabled) { "External furnace control did not reach the server" }
            screen.children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="External" }.onPress()
        }
        if(ticks==1240) {
            val screen=mc.screen as DeviceScreen;val p=screen.menu.part!!
            require(p.channel=="internal" && p.expression=="200" && kotlin.math.abs(p.thermalState.getDouble("rp")-.39)<1e-8)
            Eln.LOGGER.info("ELN_CLIENT_QA real furnace fuel, temperature target, insulation, combustion chambers and external control confirmed")
            Screenshot.grab(mc.gameDirectory,"eln-heat-furnace-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        }
        if(ticks==1280) { player.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),140.5,122.0,90.0,180f,15f) } }
        if(ticks==1370)Screenshot.grab(mc.gameDirectory,"eln-thermal-yard.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        if(ticks==1400 && java.lang.Boolean.getBoolean("eln.thermalOnly")) { Eln.LOGGER.info("ELN_CLIENT_QA thermal checks complete");mc.stop() }
        if(ticks==1440)server.execute {
            val world=server.overworld()
            fun device(id: String,x: Int,rotation: Int=0): FaceNodeEntity {
                val d=PartDefinition.byId.getValue(id);val pos=BlockPos(x,120,83)
                world.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
                world.setBlockAndUpdate(pos,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
                return (world.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(d,Direction.DOWN).apply { this.rotation=rotation };it.changedTopology() }
            }
            val heater=device("fuel_heat_furnace",148,2);device("copper_thermal_cable",149)
            val exchanger=device("thermal_heat_exchanger",150);device("signal_source",151)
            exchanger.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER,Direction.DOWN).orElseThrow { IllegalStateException("No exchanger input") }
                .fill(net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000),net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)
            val fuel=net.minecraftforge.registries.ForgeRegistries.FLUIDS.values.firstOrNull { FuelFluids.accepts(Behavior.FUEL_HEAT_FURNACE,it) }
            if(fuel!=null)heater.tank.fill(net.minecraftforge.fluids.FluidStack(fuel,25),net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)
            else { exchanger.parts[0]!!.thermalState.putDouble("delta",200.0);exchanger.changedTopology();Eln.LOGGER.info("ELN_CLIENT_QA liquid heater render/control only: no registered liquid fuel provider") }
            val viewer=server.playerList.players.first()
            viewer.inventory.setItem(0,net.minecraft.world.item.ItemStack(Content.materialItems.getValue("big_fuel_burner").get()))
            viewer.inventory.setItem(1,net.minecraft.world.item.ItemStack(Content.materialItems.getValue("analogic_regulator").get()))
            viewer.inventory.setItem(2,net.minecraft.world.item.ItemStack(Content.partItems.getValue("thermal_heat_exchanger").get()))
            viewer.inventoryMenu.broadcastChanges();viewer.teleportTo(world,148.5,121.8,82.0,0f,25f)
        }
        if(ticks==1480)open(server,BlockPos(148,120,83))
        if(ticks==1500 || ticks==1520) {
            val menu=(mc.screen as DeviceScreen).menu
            mc.gameMode!!.handleInventoryMouseClick(menu.containerId,menu.slots.size-9+(ticks-1500)/20,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,player)
        }
        if(ticks==1540) {
            val screen=mc.screen as DeviceScreen;val p=screen.menu.part!!;val inv=PartInventory.get(screen.menu.node,p)
            require(inv.getStackInSlot(0).count==1 && inv.getStackInSlot(1).count==1)
            val fields=screen.children().filterIsInstance<net.minecraft.client.gui.components.EditBox>();fields[0].value="0.5";fields[1].value="180"
            screen.children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="Apply" }.onPress()
        }
        if(ticks==1560) {
            val screen=mc.screen as DeviceScreen;require(screen.menu.part!!.expression=="180")
            screen.children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="Disabled" }.onPress()
        }
        if(ticks==1620) {
            val screen=mc.screen as DeviceScreen;val p=screen.menu.part!!
            require(p.enabled && p.thermalState.getInt("burnerType")==0)
            if(IntegrationSupport.buildCraft())require(p.energy>0 && p.thermalState.getDouble("delta")>20) { "Liquid heater GUI failed actual fuel combustion" }
            Eln.LOGGER.info("ELN_CLIENT_QA liquid heater burner/regulator inventory and server temperature/duty controls confirmed")
            Screenshot.grab(mc.gameDirectory,"eln-liquid-heat-furnace-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        }
        if(ticks==1660) { player.closeContainer();open(server,BlockPos(150,120,83)) }
        if(ticks==1700) {
            val menu=(mc.screen as DeviceScreen).menu
            require(menu.node.exchanger.input.fluidAmount<1000 && menu.node.exchanger.output.fluidAmount>0) { "Steam production failed to synchronize to the real client" }
            require(menu.part!!.thermalState.getDouble("convertedHeatJ")<0)
            Eln.LOGGER.info("ELN_CLIENT_QA steam conversion and sided input/output levels confirmed")
            Screenshot.grab(mc.gameDirectory,"eln-heat-exchanger-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        }
        if(ticks==1740) { player.closeContainer();player.inventory.selected=2;server.execute { val v=server.playerList.players.first();v.inventory.selected=2;v.teleportTo(server.overworld(),150.0,122.0,88.0,180f,15f) } }
        if(ticks==1840)Screenshot.grab(mc.gameDirectory,"eln-steam-yard-and-item.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        if(ticks==1880 && java.lang.Boolean.getBoolean("eln.steamOnly")) { Eln.LOGGER.info("ELN_CLIENT_QA rendering and integrated-server steam gallery complete");mc.stop() }
        if(ticks==1920)server.execute {
            val world=server.overworld();world.setDayTime(6000);world.setWeatherParameters(6000,0,false,false)
            fun device(id: String,x: Int,rotation: Int=0): FaceNodeEntity {
                val pos=BlockPos(x,120,83);world.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
                val d=PartDefinition.byId.getValue(id);world.setBlockAndUpdate(pos,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
                return (world.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(d,Direction.DOWN).apply { this.rotation=rotation };it.changedTopology() }
            }
            device("electrical_source",171).parts[0]!!.value=800.0;device("electrical_furnace",172,1)
            device("ground_cable",178);device("small_rotating_solar_panel",179);device("creative_power_resistor",180,2).parts[0]!!.value=5.0;device("ground_cable",181)
            val viewer=server.playerList.players.first()
            for((slot,id) in listOf("800v_tungsten_heating_element","thermal_insulation","on_off_regulator_1_percent").withIndex())viewer.inventory.setItem(slot,net.minecraft.world.item.ItemStack(Content.materialItems.getValue(id).get()))
            viewer.inventory.setItem(3,net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_IRON,4))
            viewer.inventory.setItem(4,net.minecraft.world.item.ItemStack(Content.materialItems.getValue("solar_tracker").get()))
            viewer.inventory.setItem(5,net.minecraft.world.item.ItemStack(Content.partItems.getValue("thermal_heat_exchanger").get()))
            viewer.inventoryMenu.broadcastChanges();viewer.teleportTo(world,172.5,121.8,82.0,0f,25f)
        }
        if(ticks==1960)open(server,BlockPos(172,120,83))
        if(ticks in setOf(1980,2000,2020,2040)) {
            val menu=(mc.screen as DeviceScreen).menu
            mc.gameMode!!.handleInventoryMouseClick(menu.containerId,menu.slots.size-9+(ticks-1980)/20,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,player)
        }
        if(ticks==2060) {
            val screen=mc.screen as DeviceScreen;val p=screen.menu.part!!;val inv=PartInventory.get(screen.menu.node,p)
            require((0..2).all { !inv.getStackInSlot(it).isEmpty } && screen.menu.node.inventory.getStackInSlot(0).count==4) { "Electrical furnace component or input shift transfer failed" }
            screen.children().filterIsInstance<net.minecraft.client.gui.components.EditBox>().single().value="200"
            screen.children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="Apply" }.onPress()
        }
        if(ticks==2160) {
            val screen=mc.screen as DeviceScreen;val p=screen.menu.part!!
            require(p.expression=="200" && p.energy>4000 && p.thermalState.getDouble("delta") in 170.0..215.0) { "Real electrical furnace failed heating/regulation: ${p.thermalState}" }
            screen.children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="Automatic" }.onPress()
        }
        if(ticks==2200) {
            val screen=mc.screen as DeviceScreen;require(screen.menu.part!!.channel=="manual")
            screen.children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="Enabled" }.onPress()
        }
        if(ticks==2240) {
            val screen=mc.screen as DeviceScreen;val p=screen.menu.part!!
            require(!p.enabled && p.thermalState.getDouble("heatingWatts")<1e-5)
            Eln.LOGGER.info("ELN_CLIENT_QA electrical furnace coil/insulation/regulator/input inventory and real temperature/automatic/manual controls confirmed")
            Screenshot.grab(mc.gameDirectory,"eln-electrical-furnace-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        }
        if(ticks==2280) { player.closeContainer();open(server,BlockPos(179,120,83)) }
        if(ticks==2320) {
            val menu=(mc.screen as DeviceScreen).menu
            mc.gameMode!!.handleInventoryMouseClick(menu.containerId,menu.slots.size-9+4,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,player)
        }
        if(ticks==2360) {
            val screen=mc.screen as DeviceScreen;val p=screen.menu.part!!
            require(!PartInventory.get(screen.menu.node,p).getStackInSlot(0).isEmpty && !screen.children().filterIsInstance<net.minecraft.client.gui.components.EditBox>().single().active)
            Screenshot.grab(mc.gameDirectory,"eln-solar-tracker-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
            mc.gameMode!!.handleInventoryMouseClick(screen.menu.containerId,0,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,player)
        }
        if(ticks==2400) {
            val screen=mc.screen as DeviceScreen
            val field=screen.children().filterIsInstance<net.minecraft.client.gui.components.EditBox>().single();require(field.active);field.value="30"
            screen.children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="Apply" }.onPress()
        }
        if(ticks==2440) {
            val p=(mc.screen as DeviceScreen).menu.part!!;require(kotlin.math.abs(p.solarAngle-2*Math.PI/3)<1e-7)
            Eln.LOGGER.info("ELN_CLIENT_QA solar tracker insertion/removal and real manual angle controls confirmed")
            Screenshot.grab(mc.gameDirectory,"eln-solar-manual-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        }
        if(ticks==2480) { player.closeContainer();player.inventory.selected=5;server.execute { val v=server.playerList.players.first();v.inventory.selected=5;v.teleportTo(server.overworld(),175.0,122.0,88.0,180f,15f) } }
        if(ticks==2560)Screenshot.grab(mc.gameDirectory,"eln-original-equipment-yard-and-item.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        if(ticks==2600 && java.lang.Boolean.getBoolean("eln.equipmentOnly")) { Eln.LOGGER.info("ELN_CLIENT_QA rendering and integrated-server original equipment gallery complete");mc.stop() }
        if(ticks==2640)server.execute {
            val world=server.overworld()
            fun device(id: String,x: Int,z: Int=83,rotation: Int=0): FaceNodeEntity {
                val pos=BlockPos(x,120,z);world.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
                val d=PartDefinition.byId.getValue(id);world.setBlockAndUpdate(pos,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
                return (world.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(d,Direction.DOWN).apply { this.rotation=rotation };it.changedTopology() }
            }
            device("electrical_source",187);device("dc_dc_converter",188);device("creative_power_resistor",189,rotation=2);device("ground_cable",190)
            device("electrical_source",195);val variable=device("variable_dc_dc_converter",196);device("creative_power_resistor",197,rotation=2);device("ground_cable",198)
            device("signal_source",196,82).parts[0]!!.value=2.5
            val inv=PartInventory.get(variable,variable.parts[0]!!)
            for(slot in 0..1)inv.setStackInSlot(slot,net.minecraft.world.item.ItemStack(Content.materialItems.getValue("copper_cable").get(),4))
            inv.setStackInSlot(2,net.minecraft.world.item.ItemStack(Content.materialItems.getValue("optimal_ferromagnetic_core").get()))
            inv.setStackInSlot(3,net.minecraft.world.item.ItemStack(Content.materialItems.getValue("casing").get()))
            val viewer=server.playerList.players.first()
            viewer.inventory.setItem(0,net.minecraft.world.item.ItemStack(Content.materialItems.getValue("copper_cable").get()))
            viewer.inventory.setItem(1,net.minecraft.world.item.ItemStack(Content.materialItems.getValue("copper_cable").get(),4))
            viewer.inventory.setItem(2,net.minecraft.world.item.ItemStack(Content.materialItems.getValue("optimal_ferromagnetic_core").get()))
            viewer.inventory.setItem(3,net.minecraft.world.item.ItemStack(Content.materialItems.getValue("casing").get()))
            viewer.inventoryMenu.broadcastChanges();viewer.teleportTo(world,188.5,121.0,82.0,0f,25f)
        }
        if(ticks==2680)open(server,BlockPos(188,120,83))
        if(ticks in setOf(2700,2740,2760)) {
            val menu=(mc.screen as DeviceScreen).menu;val slot=if(ticks==2700)0 else if(ticks==2740)2 else 3
            mc.gameMode!!.handleInventoryMouseClick(menu.containerId,menu.slots.size-9+slot,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,player)
        }
        if(ticks==2720) {
            val menu=(mc.screen as DeviceScreen).menu
            mc.gameMode!!.handleInventoryMouseClick(menu.containerId,menu.slots.size-9+1,0,net.minecraft.world.inventory.ClickType.PICKUP,player)
            mc.gameMode!!.handleInventoryMouseClick(menu.containerId,1,0,net.minecraft.world.inventory.ClickType.PICKUP,player)
        }
        if(ticks==2820) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!;val inv=PartInventory.get(menu.node,p)
            require(inv.getStackInSlot(0).count==1 && inv.getStackInSlot(1).count==4 && !inv.getStackInSlot(2).isEmpty && !inv.getStackInSlot(3).isEmpty)
            require(p.value==4.0 && p.voltageB in 198.0..200.0) { "GUI hardware did not drive real converter: ${p.voltageB} V" }
            require(ClientAudio.activeAt(menu.node)>0) { "Loaded transformer failed to start its original native sound loop" }
            Eln.LOGGER.info("ELN_CLIENT_QA DC-DC winding/core/casing inventory, original mesh and sound engine loop confirmed")
            Screenshot.grab(mc.gameDirectory,"eln-dc-dc-converter-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        }
        if(ticks==2860) { player.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),188.5,121.0,81.0,0f,15f) } }
        if(ticks==2940)Screenshot.grab(mc.gameDirectory,"eln-dc-dc-original-windings-and-casing.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        if(ticks==2980)open(server,BlockPos(196,120,83))
        if(ticks==3040) {
            val menu=(mc.screen as DeviceScreen).menu;require(menu.part!!.value in .49.. .51 && menu.part!!.voltageB in 24.0..25.1)
            Screenshot.grab(mc.gameDirectory,"eln-variable-dc-dc-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
            server.execute { val node=server.overworld().getBlockEntity(BlockPos(196,120,82)) as FaceNodeEntity;node.parts[0]!!.value=5.0;node.sync() }
        }
        if(ticks==3080) {
            val menu=(mc.screen as DeviceScreen).menu;require(kotlin.math.abs(menu.part!!.value-.1)<.001 && menu.part!!.voltageB in 4.8..5.1)
            Eln.LOGGER.info("ELN_CLIENT_QA variable converter separate signal control confirmed")
            mc.gameMode!!.handleInventoryMouseClick(menu.containerId,2,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,player)
        }
        if(ticks==3120) {
            val menu=(mc.screen as DeviceScreen).menu;require(!menu.part!!.gridState.getBoolean("populated"))
            Eln.LOGGER.info("ELN_CLIENT_QA converter core removal stopped real output")
        }
        if(ticks==3160 && java.lang.Boolean.getBoolean("eln.dcDcOnly")) { player.closeContainer();Eln.LOGGER.info("ELN_CLIENT_QA rendering, original converter hardware and audio checks complete");mc.stop() }
        if(ticks==3200)server.execute {
            val world=server.overworld();world.setDayTime(18000)
            fun device(id: String,x: Int,y: Int=120,rotation: Int=0,face: Direction=Direction.DOWN): FaceNodeEntity {
                val pos=BlockPos(x,y,83);world.setBlockAndUpdate(pos.relative(face),Blocks.STONE.defaultBlockState())
                val d=PartDefinition.byId.getValue(id);world.setBlockAndUpdate(pos,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
                return (world.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[face.get3DDataValue()]=PartState(d,face).apply { this.rotation=rotation };it.changedTopology() }
            }
            fun lamp(id: String,x: Int,y: Int=120,face: Direction=Direction.DOWN) {
                val n=device(id,x,y,face=face);val p=n.parts[face.get3DDataValue()]!!;p.channel="room"
                val inv=PartInventory.get(n,p)
                inv.setStackInSlot(0,net.minecraft.world.item.ItemStack(Content.materialItems.getValue("50v_led_bulb").get()))
                inv.setStackInSlot(1,net.minecraft.world.item.ItemStack(Content.partItems.getValue("low_voltage_cable").get()))
                if(p.definition.multiblock)mods.eln.modern.grid.GridMultiblock.install(n,p)
            }
            device("electrical_source",207);device("lamp_supply",208,rotation=2);device("classic_lamp_socket",212)
            lamp("long_suspended_lamp_socket",215,122,Direction.UP);lamp("suspended_lamp_socket_no_swing",218,122,Direction.UP)
            lamp("fluorescent_lamp_socket",221);lamp("robust_lamp_socket",224);lamp("street_light",227)
            val viewer=server.playerList.players.first()
            viewer.inventory.setItem(0,net.minecraft.world.item.ItemStack(Content.partItems.getValue("low_voltage_cable").get(),2))
            viewer.inventory.setItem(1,net.minecraft.world.item.ItemStack(Content.materialItems.getValue("50v_led_bulb").get()))
            viewer.inventory.setItem(2,net.minecraft.world.item.ItemStack(Content.partItems.getValue("low_voltage_cable").get()))
            viewer.inventoryMenu.broadcastChanges();viewer.teleportTo(world,208.5,121.8,82.0,0f,25f)
        }
        if(ticks==3240)open(server,BlockPos(208,120,83))
        if(ticks==3260) {
            val menu=(mc.screen as DeviceScreen).menu
            mc.gameMode!!.handleInventoryMouseClick(menu.containerId,menu.slots.size-9,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,player)
        }
        if(ticks==3280) {
            val screen=mc.screen as DeviceScreen;val fields=screen.children().filterIsInstance<net.minecraft.client.gui.components.EditBox>()
            require(fields.size==6);fields[0].value="room";fields[1].value="true";fields[2].value="room-b";fields[3].value="false";fields[4].value="room-c";fields[5].value="switch"
            screen.children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="Apply" }.onPress()
        }
        if(ticks==3340) {
            val menu=(mc.screen as DeviceScreen).menu
            require(mods.eln.modern.lighting.LampChannels.read(menu.part!!)[0].power=="room" && menu.part!!.current>1.4) { "Real lamp channels failed to synchronize or power the lighting yard" }
            Eln.LOGGER.info("ELN_CLIENT_QA lamp supply three-channel editor, cable inventory and real aggregate power confirmed")
            Screenshot.grab(mc.gameDirectory,"eln-lamp-supply-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        }
        if(ticks==3380) { player.closeContainer();open(server,BlockPos(212,120,83)) }
        if(ticks==3400 || ticks==3420) {
            val menu=(mc.screen as DeviceScreen).menu
            mc.gameMode!!.handleInventoryMouseClick(menu.containerId,menu.slots.size-9+1+(ticks-3400)/20,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,player)
        }
        if(ticks==3440) {
            val screen=mc.screen as DeviceScreen
            screen.children().filterIsInstance<net.minecraft.client.gui.components.EditBox>().single().value="room"
            screen.children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="Apply" }.onPress()
        }
        if(ticks==3480) {
            val menu=(mc.screen as DeviceScreen).menu;require(menu.part!!.signal==14 && menu.part!!.current>.29 && !menu.part!!.enabled)
            Screenshot.grab(mc.gameDirectory,"eln-lamp-bulb-and-cable-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
            (mc.screen as DeviceScreen).children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="Supply" }.onPress()
        }
        if(ticks==3520) {
            val screen=mc.screen as DeviceScreen;require(screen.menu.part!!.enabled && screen.menu.part!!.signal==0)
            screen.children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="Wired" }.onPress()
        }
        if(ticks==3560) {
            val menu=(mc.screen as DeviceScreen).menu;require(menu.part!!.signal==14)
            mc.gameMode!!.handleInventoryMouseClick(menu.containerId,0,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,player)
        }
        if(ticks==3600) {
            val menu=(mc.screen as DeviceScreen).menu;require(menu.part!!.signal==0)
            val slot=menu.slots.indexOfFirst { (it.item.item as? MaterialItem)?.definition?.bulb!=null }
            require(slot>=2);mc.gameMode!!.handleInventoryMouseClick(menu.containerId,slot,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,player)
        }
        if(ticks==3640) {
            val menu=(mc.screen as DeviceScreen).menu;require(menu.part!!.signal==14 && PartInventory.get(menu.node,menu.part!!).getStackInSlot(0).tag!!.getDouble("life")<512)
            Eln.LOGGER.info("ELN_CLIENT_QA real lamp bulb/cable inventory, wired/supply mode, channel controls and saved bulb lifetime confirmed")
        }
        if(ticks==3680) { player.closeContainer();open(server,BlockPos(208,120,83)) }
        if(ticks==3700 || ticks==3780) {
            val screen=mc.screen as DeviceScreen;screen.children().filterIsInstance<net.minecraft.client.gui.components.EditBox>()[1].value=if(ticks==3700)"false" else "true"
            screen.children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="Apply" }.onPress()
        }
        if(ticks==3760) {
            val menu=(mc.screen as DeviceScreen).menu;require(menu.part!!.current<1e-5)
            Screenshot.grab(mc.gameDirectory,"eln-lamp-supply-off-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        }
        if(ticks==3820) { player.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),219.0,122.0,89.0,180f,12f) } }
        if(ticks==3920)Screenshot.grab(mc.gameDirectory,"eln-original-lighting-night-yard.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        if(ticks==3960 && java.lang.Boolean.getBoolean("eln.lightingOnly")) { Eln.LOGGER.info("ELN_CLIENT_QA original lighting meshes, projected light and controls complete");mc.stop() }
        if(ticks==4000)server.execute {
            val world=server.overworld();world.setDayTime(18000)
            fun device(id: String,x: Int,z: Int=94): FaceNodeEntity {
                val pos=BlockPos(x,120,z);world.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
                val d=PartDefinition.byId.getValue(id);world.setBlockAndUpdate(pos,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
                return (world.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(d,Direction.DOWN);it.changedTopology() }
            }
            device("electrical_source",232,93).parts[0]!!.value=200.0;device("basic_floodlight",232)
            device("electrical_source",236);device("50v_emergency_lamp",237)
            val motor=device("motorized_floodlight",243);val mp=motor.parts[0]!!
            PartInventory.get(motor,mp).setStackInSlot(0,net.minecraft.world.item.ItemStack(Content.materialItems.getValue("200v_halogen_bulb").get()))
            device("electrical_source",243,93).parts[0]!!.value=200.0
            device("signal_source",244).parts[0]!!.value=1.0;device("signal_source",242).parts[0]!!.value=1.0
            val viewer=server.playerList.players.first()
            viewer.inventory.setItem(0,net.minecraft.world.item.ItemStack(Content.materialItems.getValue("200v_halogen_bulb").get()))
            viewer.inventory.setItem(1,net.minecraft.world.item.ItemStack(Content.materialItems.getValue("200v_halogen_bulb").get()))
            viewer.inventory.selected=2;viewer.inventoryMenu.broadcastChanges();viewer.teleportTo(world,232.5,121.8,93.0,0f,25f)
        }
        if(ticks==4040)open(server,BlockPos(232,120,94))
        if(ticks==4060 || ticks==4080) {
            val menu=(mc.screen as DeviceScreen).menu
            mc.gameMode!!.handleInventoryMouseClick(menu.containerId,menu.slots.size-9+(ticks-4060)/20,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,player)
        }
        if(ticks==4120) {
            val screen=mc.screen as DeviceScreen;require(screen.menu.part!!.signal==15 && screen.menu.part!!.thermalState.getDouble("lampWatts")>990)
            val sliders=screen.children().filterIsInstance<FloodlightSlider>();require(sliders.size==3)
            sliders[0].setDegrees(90.0);sliders[1].setDegrees(30.0);sliders[2].setDegrees(25.0)
            screen.children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="Apply" }.onPress()
        }
        if(ticks==4180) {
            val menu=(mc.screen as DeviceScreen).menu;require(menu.part!!.value==90.0 && menu.part!!.expression=="30.0,25.0")
            Eln.LOGGER.info("ELN_CLIENT_QA real two-bulb floodlight inventory, finite bulb life, circuit power and aiming controls confirmed")
            Screenshot.grab(mc.gameDirectory,"eln-floodlight-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        }
        if(ticks==4200) { player.closeContainer();open(server,BlockPos(237,120,94)) }
        if(ticks==4240) {
            val screen=mc.screen as DeviceScreen;require(screen.menu.part!!.signal==6 && !screen.menu.part!!.enabled && screen.menu.part!!.charge<.5)
            screen.children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="Supply" }.onPress()
        }
        if(ticks==4300) {
            val menu=(mc.screen as DeviceScreen).menu;require(menu.part!!.enabled && menu.part!!.signal==0 && menu.part!!.current>.19)
            Screenshot.grab(mc.gameDirectory,"eln-emergency-charging-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
            server.execute { val n=server.overworld().getBlockEntity(BlockPos(236,120,94)) as FaceNodeEntity;n.parts[0]!!.enabled=false;n.changedTopology() }
        }
        if(ticks==4360) {
            val menu=(mc.screen as DeviceScreen).menu;require(menu.part!!.signal==6 && menu.part!!.current<1e-5)
            Eln.LOGGER.info("ELN_CLIENT_QA emergency lamp charged from a real cable and automatically entered finite backup lighting after a power outage")
            Screenshot.grab(mc.gameDirectory,"eln-emergency-outage-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        }
        if(ticks==4400) { player.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),238.0,122.0,100.0,180f,10f) };mc.player!!.inventory.selected=2 }
        if(ticks==4480)Screenshot.grab(mc.gameDirectory,"eln-floodlight-and-emergency-night-yard.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        if(ticks==4520 && java.lang.Boolean.getBoolean("eln.floodlightOnly")) { Eln.LOGGER.info("ELN_CLIENT_QA floodlight/emergency hardware, original meshes, real controls and lighting complete");mc.stop() }
        if(ticks==4600)server.execute {
            val world=server.overworld();world.setDayTime(18000)
            fun device(id: String,x: Int,z: Int=105): FaceNodeEntity {
                val pos=BlockPos(x,120,z);world.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
                val d=PartDefinition.byId.getValue(id);world.setBlockAndUpdate(pos,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
                return (world.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(d,Direction.DOWN).apply { rotation=1 };it.changedTopology() }
            }
            device("flywheel",252).parts[0]!!.apply { shaftEnergy=.5*definition.shaftMass*.05*80*80;shaftGroup="qa-clutch-left" }
            device("clutch",253).parts[0]!!.apply { gridState.putDouble("clutchEnergy1",.5*definition.shaftMass*.05*80*80);gridState.putString("clutchGroup1","qa-clutch-left");gridState.putString("clutchGroup3","qa-clutch-right") }
            device("flywheel",254).parts[0]!!.shaftGroup="qa-clutch-right"
            device("signal_source",253,104).parts[0]!!.value=0.0
            val viewer=server.playerList.players.first()
            viewer.inventory.setItem(0,net.minecraft.world.item.ItemStack(Content.materialItems.getValue("iron_clutch_plate").get()))
            viewer.inventory.setItem(1,net.minecraft.world.item.ItemStack(Content.materialItems.getValue("clutch_pin").get()))
            viewer.inventory.setItem(2,net.minecraft.world.item.ItemStack.EMPTY);viewer.inventory.selected=2;viewer.inventoryMenu.broadcastChanges();viewer.teleportTo(world,253.5,121.8,104.0,0f,25f)
        }
        if(ticks==4630)open(server,BlockPos(253,120,105))
        if(ticks==4650 || ticks==4740) {
            val menu=(mc.screen as DeviceScreen).menu
            mc.gameMode!!.handleInventoryMouseClick(menu.containerId,menu.slots.size-9+(if(ticks==4650)0 else 1),0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,player)
        }
        if(ticks==4670) {
            val menu=(mc.screen as DeviceScreen).menu
            require(menu.part!!.gridState.getDouble("clutchSpeed1")>70 && menu.part!!.gridState.getDouble("clutchSpeed3")==0.0 && !PartInventory.get(menu.node,menu.part!!).getStackInSlot(0).isEmpty)
            server.execute { (server.overworld().getBlockEntity(BlockPos(253,120,104)) as FaceNodeEntity).parts[0]!!.value=5.0 }
        }
        if(ticks==4730) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(p.gridState.getDouble("clutchSpeed3")>30 && kotlin.math.abs(p.gridState.getDouble("clutchSpeed1")-p.gridState.getDouble("clutchSpeed3"))<.001 && !p.gridState.getBoolean("clutchSlipping"))
            require(PartInventory.get(menu.node,p).getStackInSlot(0).tag!!.getDouble("wear")>0)
            Eln.LOGGER.info("ELN_CLIENT_QA clutch real inventory, separate shaft speeds, actual gate control and wearing friction confirmed")
            Screenshot.grab(mc.gameDirectory,"eln-clutch-plate-and-speeds-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        }
        if(ticks==4770)server.execute {
            val n=server.overworld().getBlockEntity(BlockPos(253,120,105)) as FaceNodeEntity
            require(!PartInventory.get(n,n.parts[0]!!).getStackInSlot(1).isEmpty);val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology()
        }
        if(ticks==4830)server.execute {
            (server.overworld().getBlockEntity(BlockPos(253,120,104)) as FaceNodeEntity).parts[0]!!.value=0.0
            val left=server.overworld().getBlockEntity(BlockPos(252,120,105)) as FaceNodeEntity
            left.parts[0]!!.shaftEnergy+=3000;left.changedTopology()
        }
        if(ticks==4880) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(p.gridState.getDouble("clutchSpeed1")-p.gridState.getDouble("clutchSpeed3")>10 && p.voltage<1e-5)
            Screenshot.grab(mc.gameDirectory,"eln-clutch-separated-speeds-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        }
        if(ticks==4930)server.execute { (server.overworld().getBlockEntity(BlockPos(253,120,104)) as FaceNodeEntity).parts[0]!!.value=.1 }
        if(ticks==4940) {
            val menu=(mc.screen as DeviceScreen).menu;require(menu.part!!.active && ClientAudio.activeAt(menu.node)>0) { "Actual OpenAL clutch friction loop did not play" }
            Eln.LOGGER.info("ELN_CLIENT_QA actual clutch slip sound confirmed through the active OpenAL engine")
        }
        if(ticks==5020) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(!p.gridState.getBoolean("clutchSlipping") && kotlin.math.abs(p.gridState.getDouble("clutchSpeed1")-p.gridState.getDouble("clutchSpeed3"))<.001)
            Eln.LOGGER.info("ELN_CLIENT_QA clutch pin, saved two-network state, disengagement and recoupling confirmed")
            Screenshot.grab(mc.gameDirectory,"eln-clutch-pin-locked-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        }
        if(ticks==5040) { player.closeContainer();mc.player!!.inventory.selected=2;server.execute { server.playerList.players.first().teleportTo(server.overworld(),253.0,122.0,110.0,180f,10f) } }
        if(ticks==5120)Screenshot.grab(mc.gameDirectory,"eln-original-clutch-night-yard.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        if(ticks==5140 && java.lang.Boolean.getBoolean("eln.clutchOnly")) { Eln.LOGGER.info("ELN_CLIENT_QA clutch original hardware, controls, sound and independent shaft rendering complete");mc.stop() }
        if(ticks==5200)server.execute {
            val world=server.overworld();world.setDayTime(6000)
            fun device(id: String,x: Int,z: Int=115): FaceNodeEntity {
                val pos=BlockPos(x,120,z);world.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
                val d=PartDefinition.byId.getValue(id);world.setBlockAndUpdate(pos,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
                return (world.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(d,Direction.DOWN);it.changedTopology() }
            }
            val old=world.getBlockEntity(BlockPos(280,120,115)) as? FaceNodeEntity
            old?.parts?.get(0)?.let { mods.eln.modern.grid.GridMultiblock.remove(old,it) }
            world.removeBlock(BlockPos(280,120,115),false)
            val radial=device("radial_motor",280);val rp=radial.parts[0]!!
            mods.eln.modern.grid.GridMultiblock.cells(radial.blockPos,rp).forEach { world.setBlockAndUpdate(it,Blocks.AIR.defaultBlockState()) }
            mods.eln.modern.grid.GridMultiblock.install(radial,rp)
            device("flywheel",280,116);device("signal_source",281).parts[0]!!.value=.25
            val fuel=net.minecraftforge.registries.ForgeRegistries.FLUIDS.values.first { val id=net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(it)!!;id.namespace.startsWith("buildcraft") && id.path.startsWith("fuel_light") }
            radial.tank.fill(net.minecraftforge.fluids.FluidStack(fuel,40),net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)
            val viewer=server.playerList.players.first();viewer.inventory.setItem(2,net.minecraft.world.item.ItemStack.EMPTY);viewer.inventory.selected=2;viewer.inventoryMenu.broadcastChanges()
            viewer.teleportTo(world,280.5,122.8,117.0,180f,25f)
        }
        if(ticks==5260) { mc.player!!.inventory.selected=2;open(server,BlockPos(280,120,115)) }
        if(ticks==5300 && mc.screen !is DeviceScreen)server.execute {
            val viewer=server.playerList.players.first();val node=server.overworld().getBlockEntity(BlockPos(280,120,115)) as? FaceNodeEntity
            Eln.LOGGER.info("ELN_CLIENT_QA radial screen diagnostic menu={} valid={} node={} player={}",viewer.containerMenu.javaClass.simpleName,viewer.containerMenu.stillValid(viewer),node?.parts?.get(0)?.save(),viewer.position())
        }
        if(ticks==5320) {
            val menu=(mc.screen as? DeviceScreen ?: error("Radial screen closed before the live controls check")).menu;val p=menu.part!!
            Eln.LOGGER.info("ELN_CLIENT_QA radial actual state tank={} audio={} part={}",menu.node.tank.fluidAmount,ClientAudio.activeAt(menu.node),p.save())
            require(menu.node.tank.fluidAmount<40 && p.energy>0 && p.thermalState.getDouble("radialRate")>0 && ClientAudio.activeAt(menu.node)>0) { "Live radial engine failed its actual fuel, inertia or OpenAL output" }
            Eln.LOGGER.info("ELN_CLIENT_QA actual radial fuel, throttle, intake inertia and active OpenAL engine sound confirmed")
            Screenshot.grab(mc.gameDirectory,"eln-radial-fuel-throttle-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        }
        if(ticks==5360)(mc.screen as DeviceScreen).children().filterIsInstance<net.minecraft.client.gui.components.Button>().first { it.message.string=="Enabled" }.onPress()
        if(ticks==5400) {
            val menu=(mc.screen as DeviceScreen).menu;require(!menu.part!!.enabled)
            player.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),285.0,122.0,120.0,140f,12f) }
        }
        if(ticks==5480)Screenshot.grab(mc.gameDirectory,"eln-original-radial-engine-body.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        if(ticks==5520)server.execute {
            val world=server.overworld();val trunk=BlockPos(294,119,115)
            for(y in 119..123) { world.setBlockAndUpdate(trunk.atY(y),Blocks.OAK_LOG.defaultBlockState());world.setBlockAndUpdate(trunk.east().atY(y),Blocks.AIR.defaultBlockState()) }
            world.setBlockAndUpdate(trunk.east().atY(122),Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.PERSISTENT,true))
            val viewer=server.playerList.players.first();viewer.setGameMode(net.minecraft.world.level.GameType.SURVIVAL)
            viewer.abilities.mayfly=true;viewer.abilities.flying=true;viewer.abilities.invulnerable=true;viewer.onUpdateAbilities()
            viewer.inventory.setItem(0,net.minecraft.world.item.ItemStack(Content.partItems.getValue("tree_resin_collector").get(),2));viewer.inventory.selected=0;viewer.inventoryMenu.broadcastChanges()
            viewer.teleportTo(world,296.5,121.0,117.0,135f,40f)
        }
        if(ticks==5560) {
            mc.player!!.inventory.selected=0
            val pos=BlockPos(294,120,115)
            mc.gameMode!!.useItemOn(player,net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos).add(.5,0.0,0.0),Direction.EAST,pos,false))
        }
        if(ticks==5620)server.execute {
            val n=server.overworld().getBlockEntity(BlockPos(295,120,115)) as? FaceNodeEntity ?: error("Real collector item did not place a carrier")
            require(n.parts[4]?.definition?.behavior==Behavior.RESIN_COLLECTOR && server.playerList.players.first().inventory.getItem(0).count==1)
            n.parts[4]!!.thermalState.putDouble("resinElapsed",1000.0);n.sync()
            Eln.LOGGER.info("ELN_CLIENT_QA real survival collector placement consumed one crafted item on the side of its tree")
        }
        if(ticks==5640) {
            mc.player!!.inventory.selected=3
            server.execute { val viewer=server.playerList.players.first();viewer.inventory.setItem(3,net.minecraft.world.item.ItemStack.EMPTY);viewer.inventory.selected=3;viewer.inventoryMenu.broadcastChanges() }
        }
        if(ticks==5680) {
            val pos=BlockPos(295,120,115)
            mc.gameMode!!.useItemOn(player,net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.EAST,pos,false))
        }
        if(ticks==5720)server.execute {
            val world=server.overworld();val pos=BlockPos(295,120,115);val n=world.getBlockEntity(pos) as FaceNodeEntity
            val count=world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity::class.java,net.minecraft.world.phys.AABB(pos).inflate(4.0)).filter { it.item.item==Content.materialItems.getValue("tree_resin").get() }.sumOf { it.item.count }+server.playerList.players.first().inventory.items.filter { it.item==Content.materialItems.getValue("tree_resin").get() }.sumOf { it.count }
            require(count==2 && n.parts[4]!!.thermalState.getDouble("resinElapsed")<3.0) { "Real survival right-click failed to harvest the finite two-item resin pot" }
            Eln.LOGGER.info("ELN_CLIENT_QA real survival harvest dropped two resin items and reset its finite pot")
        }
        if(ticks==5760)Screenshot.grab(mc.gameDirectory,"eln-survival-tree-resin-collector.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        if(ticks==5800) { Eln.LOGGER.info("ELN_CLIENT_QA radial engine and original survival resin bootstrap complete");mc.stop() }
    }
    private fun open(server: net.minecraft.client.server.IntegratedServer,pos: BlockPos) {
        server.execute {
            val viewer=server.playerList.players.first();val world=server.overworld()
            val node=world.getBlockEntity(pos) as FaceNodeEntity
            if(node.parts[0]?.definition?.behavior==Behavior.RADIAL_MOTOR)viewer.teleportTo(world,pos.x+.5,pos.y+2.8,pos.z+2.0,180f,25f)
            else viewer.teleportTo(world,pos.x+.5,pos.y+1.8,pos.z-1.0,0f,25f)
            node.sync()
            net.minecraftforge.network.NetworkHooks.openScreen(viewer,net.minecraft.world.SimpleMenuProvider(
                { id,inventory,_ -> DeviceMenu(id,inventory,node,0) },net.minecraft.network.chat.Component.translatable(node.parts[0]!!.definition.translationKey))) {
                it.writeBlockPos(pos);it.writeByte(0)
            }
            Eln.LOGGER.info("ELN_CLIENT_QA server opened {} for {} valid={}",viewer.containerMenu.javaClass.simpleName,node.parts[0]!!.definition.id,viewer.containerMenu.stillValid(viewer))
        }
    }
}
