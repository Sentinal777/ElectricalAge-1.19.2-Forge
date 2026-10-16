package mods.eln.modern.client

import mods.eln.Eln
import mods.eln.modern.*
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

/** Isolated client exercise for actual turbine controls/audio and the six-sided hub model. */
object TurbineClientSmoke {
    private var ticks=0
    fun tick(mc: Minecraft,server: IntegratedServer) {
        ticks++
        fun device(id: String,x: Int,y: Int=120,z: Int=115): FaceNodeEntity {
            val world=server.overworld();val pos=BlockPos(x,y,z)
            world.removeBlock(pos,false)
            if(world.isEmptyBlock(pos.below()))world.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
            val d=PartDefinition.byId.getValue(id)
            world.setBlockAndUpdate(pos,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
            return (world.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(d,Direction.DOWN);it.changedTopology() }
        }
        fun open(pos: BlockPos) { server.execute {
            val viewer=server.playerList.players.first();val world=server.overworld()
            viewer.teleportTo(world,pos.x+.5,pos.y+1.8,pos.z-1.0,0f,60f)
            val node=world.getBlockEntity(pos) as FaceNodeEntity;node.sync()
            net.minecraftforge.network.NetworkHooks.openScreen(viewer,net.minecraft.world.SimpleMenuProvider(
                { id,inventory,_ -> DeviceMenu(id,inventory,node,0) },net.minecraft.network.chat.Component.translatable(node.parts[0]!!.definition.translationKey))) { it.writeBlockPos(pos);it.writeByte(0) }
        } }
        if(ticks==20)server.execute {
            val world=server.overworld();world.dayTime=6000
            for(x in 307..330)for(z in 111..120) {
                for(y in 120..180)world.setBlockAndUpdate(BlockPos(x,y,z),Blocks.AIR.defaultBlockState())
                world.setBlockAndUpdate(BlockPos(x,119,z),Blocks.STONE.defaultBlockState())
            }
            val viewer=server.playerList.players.first();viewer.setGameMode(net.minecraft.world.level.GameType.CREATIVE)
            viewer.abilities.flying=true;viewer.onUpdateAbilities();viewer.inventory.setItem(2,ItemStack.EMPTY);viewer.inventory.selected=2;viewer.inventoryMenu.broadcastChanges()
            val steam=device("steam_turbine",310);device("flywheel",310,z=116)
            device("signal_source",311).parts[0]!!.value=2.5
            steam.tank.fill(FluidStack(SteamFluids.steam.get(),7200),FluidAction.EXECUTE)
            val gas=device("gas_turbine",316);val fly=device("flywheel",316,z=116)
            gas.parts[0]!!.shaftGroup="live-gas-primer";fly.parts[0]!!.shaftGroup="live-gas-primer";fly.parts[0]!!.shaftEnergy=50000.0;fly.changedTopology()
            val fuel=ForgeRegistries.FLUIDS.values.first { val id=ForgeRegistries.FLUIDS.getKey(it)!!;id.namespace.startsWith("buildcraft") && id.path.startsWith("fuel_light") }
            gas.tank.fill(FluidStack(fuel,4),FluidAction.EXECUTE)
            val lower=device("joint_hub",322);val upper=device("joint_hub",322,121);val wheel=device("flywheel",322,121,116)
            for(node in listOf(lower,upper,wheel))node.parts[0]!!.shaftGroup="live-vertical-hub"
            lower.parts[0]!!.shaftEnergy=5000.0;lower.changedTopology()
            viewer.teleportTo(world,310.5,121.8,114.0,0f,60f)
        }
        if(ticks==90) { mc.player!!.inventory.selected=2;open(BlockPos(310,120,115)) }
        if(ticks==150) {
            val menu=(mc.screen as? DeviceScreen ?: error("Live steam controls did not remain open")).menu;val p=menu.part!!
            require(p.energy>0 && p.thermalState.getDouble("turbineChamberHeat")>0 && p.thermalState.getDouble("turbineRate")>0 && ClientAudio.activeAt(menu.node)>0) { "Steam intake, finite delayed work or actual sound failed" }
            Screenshot.grab(mc.gameDirectory,"eln-original-steam-turbine-intake-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
            Eln.LOGGER.info("ELN_CLIENT_QA original steam throttle, saved finite intake and actual OpenAL loop confirmed")
        }
        if(ticks==180) { mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),316.5,121.8,114.0,0f,60f) } }
        if(ticks==240)open(BlockPos(316,120,115))
        if(ticks==300) {
            val menu=(mc.screen as? DeviceScreen ?: error("Live gas controls did not remain open")).menu;val p=menu.part!!
            require(menu.node.tank.isEmpty && p.energy>0 && p.thermalState.getDouble("turbineChamberHeat")>0 && p.shaftSpeed>175 && !p.tripped && ClientAudio.activeAt(menu.node)>0) { "Primed gas turbine lost finite BuildCraft fuel, work or original sound" }
            Screenshot.grab(mc.gameDirectory,"eln-original-gas-turbine-intake-gui.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
            Eln.LOGGER.info("ELN_CLIENT_QA primed gas turbine, original four-mB tank, finite fuel and actual sound confirmed")
        }
        if(ticks==340) { mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),326.5,123.0,119.0,135f,25f) } }
        if(ticks==400)server.execute {
            val lower=(server.overworld().getBlockEntity(BlockPos(322,120,115)) as FaceNodeEntity).parts[0]!!
            val upper=(server.overworld().getBlockEntity(BlockPos(322,121,115)) as FaceNodeEntity).parts[0]!!
            require(lower.gridState.getInt("shaftConnectedSides")== (1 shl Direction.UP.get3DDataValue()) && upper.shaftSpeed>0)
            Eln.LOGGER.info("ELN_CLIENT_QA actual vertical shaft path and original connected hub meshes confirmed")
        }
        if(ticks==420)Screenshot.grab(mc.gameDirectory,"eln-original-vertical-shaft-hubs.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        if(ticks==460) { Eln.LOGGER.info("ELN_CLIENT_QA original gas/steam turbines and six-sided hubs complete");mc.stop() }
    }
}
