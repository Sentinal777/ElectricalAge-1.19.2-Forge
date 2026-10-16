package mods.eln.modern.client

import mods.eln.Eln
import mods.eln.modern.*
import mods.eln.modern.grid.GridMultiblock
import mods.eln.modern.thermal.SteamFluids
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.server.IntegratedServer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction
import net.minecraftforge.registries.ForgeRegistries

object LargeShaftClientSmoke {
    private var ticks=0
    fun tick(mc: Minecraft,server: IntegratedServer) {
        ticks++
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun open(pos: BlockPos) { server.execute {
            val viewer=server.playerList.players.first();val world=server.overworld();viewer.teleportTo(world,pos.x+3.0,pos.y+2.8,pos.z-3.0,35f,34f)
            val node=world.getBlockEntity(pos) as FaceNodeEntity;node.sync()
            net.minecraftforge.network.NetworkHooks.openScreen(viewer,net.minecraft.world.SimpleMenuProvider(
                { id,inventory,_ -> DeviceMenu(id,inventory,node,0) },net.minecraft.network.chat.Component.translatable(node.parts[0]!!.definition.translationKey))) { it.writeBlockPos(pos);it.writeByte(0) }
        } }
        if(ticks==20)server.execute {
            val world=server.overworld();world.dayTime=6000;world.setWeatherParameters(6000,0,false,false)
            for(x in 383..416)for(z in 110..137) {
                for(y in 120..180)world.setBlockAndUpdate(BlockPos(x,y,z),Blocks.AIR.defaultBlockState())
                world.setBlockAndUpdate(BlockPos(x,119,z),Blocks.STONE.defaultBlockState())
            }
            fun device(id: String,x: Int,y: Int=120,z: Int=115,rotation: Int=0): FaceNodeEntity {
                val pos=BlockPos(x,y,z);val p=PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { this.rotation=rotation }
                if(world.isEmptyBlock(pos.below()))world.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
                world.setBlockAndUpdate(pos,(if(p.definition.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
                return (world.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=p;GridMultiblock.install(it,p);it.changedTopology() }
            }
            device("large_shaft_motor",386);device("large_generator",386,z=119)
            device("electrical_source",387).parts[0]!!.value=12800.0
            device("joint",386,121,117)
            device("creative_power_resistor",387,z=119,rotation=2).parts[0]!!.value=10000.0
            device("ground_cable",388,z=119)
            val steam=device("large_steam_turbine",395);val steamWheel=device("flywheel",395,121,117)
            device("signal_source",396).parts[0]!!.value=.05
            for(n in listOf(steam,steamWheel))n.parts[0]!!.shaftGroup="live-large-steam-primer"
            steamWheel.parts[0]!!.shaftEnergy=185000.0;steamWheel.changedTopology()
            steam.tank.fill(FluidStack(SteamFluids.steam.get(),194400),FluidAction.EXECUTE)
            val gas=device("large_gas_turbine",403);val gasWheel=device("flywheel",403,121,117)
            device("signal_source",404).parts[0]!!.value=.025
            for(n in listOf(gas,gasWheel))n.parts[0]!!.shaftGroup="live-large-gas-primer"
            gasWheel.parts[0]!!.shaftEnergy=185000.0;gasWheel.changedTopology()
            val fuel=ForgeRegistries.FLUIDS.values.first { val id=ForgeRegistries.FLUIDS.getKey(it)!!;id.namespace.startsWith("buildcraft") && id.path.startsWith("fuel_light") }
            gas.tank.fill(FluidStack(fuel,108),FluidAction.EXECUTE)
            val viewer=server.playerList.players.first();viewer.setGameMode(net.minecraft.world.level.GameType.CREATIVE)
            viewer.abilities.flying=true;viewer.onUpdateAbilities();viewer.inventory.setItem(2,ItemStack.EMPTY);viewer.inventory.selected=2;viewer.inventoryMenu.broadcastChanges()
            viewer.teleportTo(world,389.0,122.8,112.0,35f,34f)
        }
        if(ticks==100) { mc.player!!.inventory.selected=2;open(BlockPos(386,120,115)) }
        if(ticks==160) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(p.definition.visual.scale==3.0 && p.shaftSpeed>100 && p.voltage>12000 && p.thermalState.getDouble("shaftProducedJ")>0 && ClientAudio.activeAt(menu.node)>0)
            image("eln-original-large-motor-gui.png");Eln.LOGGER.info("ELN_CLIENT_QA original large motor, 12.8kV input, raised ghost axle and actual sound confirmed")
        }
        if(ticks==180) { mc.player!!.closeContainer();open(BlockPos(386,120,119)) }
        if(ticks==240) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(p.shaftSpeed>100 && p.voltage>10000 && p.thermalState.getDouble("shaftElectricalOutputJ")>1000 && ClientAudio.activeAt(menu.node)>0)
            image("eln-original-large-generator-gui.png");Eln.LOGGER.info("ELN_CLIENT_QA actual large motor-to-raised-joint-to-large-generator electrical plant confirmed")
        }
        if(ticks==260) { mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),398.0,122.8,112.0,35f,34f) } }
        if(ticks==310)open(BlockPos(395,120,115))
        if(ticks==370) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(menu.node.tank.fluidAmount<194400 && p.energy>0 && p.thermalState.getDouble("turbineRate")>0 && p.shaftSpeed>195 && p.shaftSpeed<250 && !p.tripped)
            image("eln-original-large-steam-turbine-gui.png");Eln.LOGGER.info("ELN_CLIENT_QA original scaled steam tank, governor and finite large rotor confirmed")
        }
        if(ticks==390) { mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),406.0,122.8,112.0,35f,34f) } }
        if(ticks==440)open(BlockPos(403,120,115))
        if(ticks==500) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(menu.node.tank.fluidAmount<108 && p.energy>0 && p.shaftSpeed>190 && p.shaftSpeed<250 && !p.tripped)
            image("eln-original-large-gas-turbine-gui.png");Eln.LOGGER.info("ELN_CLIENT_QA original 27-times gas turbine intake with actual BuildCraft fuel confirmed")
        }
        if(ticks==520) { mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),407.0,128.0,131.0,140f,20f) } }
        if(ticks==600) {
            image("eln-original-large-shaft-machinery-world.png")
            server.execute { Eln.LOGGER.info("ELN_CLIENT_QA large machinery integrated-server average tick {} ms",server.averageTickTime) }
        }
        if(ticks==640) { Eln.LOGGER.info("ELN_CLIENT_QA original large shaft machinery complete");mc.stop() }
    }
}
