package mods.eln.modern.client

import mods.eln.Eln
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.server.IntegratedServer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction
import net.minecraftforge.registries.ForgeRegistries
import kotlin.math.abs

object FuelGeneratorClientSmoke {
    private var ticks=0
    private var stoppedFuel=0.0
    private fun pos(x: Int)=BlockPos(x,120,115)
    fun tick(mc: Minecraft,server: IntegratedServer) {
        ticks++
        fun node(x: Int)=server.overworld().getBlockEntity(pos(x)) as FaceNodeEntity
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun open(x: Int)=server.execute {
            val n=node(x);n.sync();val viewer=server.playerList.players.first()
            viewer.teleportTo(server.overworld(),x+2.5,122.0,112.0,35f,30f)
            net.minecraftforge.network.NetworkHooks.openScreen(viewer,net.minecraft.world.SimpleMenuProvider(
                { id,inventory,_ -> DeviceMenu(id,inventory,n,0) },net.minecraft.network.chat.Component.translatable(n.parts[0]!!.definition.translationKey))) { it.writeBlockPos(n.blockPos);it.writeByte(0) }
        }
        fun click(x: Int) {
            val n=node(x);val viewer=server.playerList.players.first();viewer.setShiftKeyDown(false)
            viewer.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
            n.blockState.use(server.overworld(),viewer,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(n.blockPos),Direction.UP,n.blockPos,false))
        }
        if(ticks==20)server.execute {
            val world=server.overworld();world.dayTime=6000;world.setWeatherParameters(6000,0,false,false)
            for(x in 424..445)for(z in 109..124) {
                for(y in 120..180)world.setBlockAndUpdate(BlockPos(x,y,z),Blocks.AIR.defaultBlockState())
                world.setBlockAndUpdate(BlockPos(x,119,z),Blocks.STONE.defaultBlockState())
            }
            val fuel=ForgeRegistries.FLUIDS.values.first { val id=ForgeRegistries.FLUIDS.getKey(it)!!;id.namespace.startsWith("buildcraft") && id.path.startsWith("fuel_light") }
            fun device(id: String,x: Int): FaceNodeEntity {
                val d=PartDefinition.byId.getValue(id);val at=pos(x)
                world.setBlockAndUpdate(at,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
                return (world.getBlockEntity(at) as FaceNodeEntity).also { it.parts[0]=PartState(d,Direction.DOWN);it.changedTopology() }
            }
            for((x,id) in listOf(430 to "48v_fuel_generator",438 to "240v_fuel_generator")) {
                val n=device(id,x);val p=n.parts[0]!!;require(!p.enabled)
                require(n.tank.fill(FluidStack(fuel,1000),FluidAction.EXECUTE)==1000)
                device("creative_power_resistor",x+1).parts[0]!!.apply { rotation=2;value=p.definition.nominalVoltage*p.definition.nominalVoltage/p.definition.power }
                device("ground_cable",x+2)
            }
            val viewer=server.playerList.players.first();viewer.setGameMode(net.minecraft.world.level.GameType.CREATIVE)
            viewer.abilities.flying=true;viewer.onUpdateAbilities();viewer.inventory.setItem(2,ItemStack.EMPTY);viewer.inventory.selected=2;viewer.inventoryMenu.broadcastChanges()
            viewer.teleportTo(world,432.5,122.0,112.0,35f,30f)
        }
        if(ticks==60)server.execute { click(430);click(438) }
        if(ticks==100)open(430)
        if(ticks==160) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(p.definition.id=="48v_fuel_generator" && p.enabled && p.active && p.voltage in 49.0..53.0 && abs(p.thermalState.getDouble("fuelGeneratorWatts")-1200)<1 && ClientAudio.activeAt(menu.node)>0)
            image("eln-original-50v-fuel-generator-gui.png");Eln.LOGGER.info("ELN_CLIENT_QA original 1200 W gasoline generator, real BuildCraft fuel, controls and sound confirmed")
        }
        if(ticks==180) { mc.player!!.closeContainer();open(438) }
        if(ticks==250) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(p.definition.id=="240v_fuel_generator" && p.enabled && p.voltage in 198.0..206.0 && abs(p.thermalState.getDouble("fuelGeneratorWatts")-6000)<1 && ClientAudio.activeAt(menu.node)>0)
            image("eln-original-200v-fuel-generator-gui.png");Eln.LOGGER.info("ELN_CLIENT_QA original 6000 W gasoline generator and original voltage-driven sound confirmed")
        }
        if(ticks==270)server.execute { click(430);stoppedFuel=node(430).parts[0]!!.fuelEnergy }
        if(ticks==290) { mc.player!!.closeContainer();open(430) }
        if(ticks==350) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(!p.enabled && !p.active && p.fuelEnergy==stoppedFuel && ClientAudio.activeAt(menu.node)==0)
            image("eln-original-fuel-generator-stopped-gui.png");Eln.LOGGER.info("ELN_CLIENT_QA right-click shutdown stopped actual fuel consumption and OpenAL loop")
            server.execute {
                for(x in listOf(430,438)) {
                    val n=node(x);val q=n.parts[0]!!;val perMb=q.definition.power*1200/2000
                    require(abs(n.tank.fluidAmount*perMb+q.fuelEnergy+q.thermalState.getDouble("fuelGeneratorInputJ")-1000*perMb)<1e-4)
                }
            }
        }
        if(ticks==380) { mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),435.0,125.0,127.0,175f,20f) } }
        if(ticks==420)image("eln-original-fuel-generators-world.png")
        if(ticks==450) { Eln.LOGGER.info("ELN_CLIENT_QA original fuel generators complete");mc.stop() }
    }
}
