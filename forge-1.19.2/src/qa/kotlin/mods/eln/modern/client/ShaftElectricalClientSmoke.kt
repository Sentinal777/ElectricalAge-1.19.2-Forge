package mods.eln.modern.client

import mods.eln.Eln
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.server.IntegratedServer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks

/** Isolated original shaft machine controls, signed power lights and real sound exercise. */
object ShaftElectricalClientSmoke {
    private var ticks=0
    fun tick(mc: Minecraft,server: IntegratedServer) {
        ticks++
        fun device(id: String,x: Int,z: Int=115): FaceNodeEntity {
            val world=server.overworld();val pos=BlockPos(x,120,z);world.removeBlock(pos,false)
            val d=PartDefinition.byId.getValue(id);world.setBlockAndUpdate(pos,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
            return (world.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(d,Direction.DOWN);it.changedTopology() }
        }
        fun open(pos: BlockPos) { server.execute {
            val viewer=server.playerList.players.first();val world=server.overworld();viewer.teleportTo(world,pos.x+.5,121.8,pos.z-1.0,0f,60f)
            val node=world.getBlockEntity(pos) as FaceNodeEntity;node.sync()
            net.minecraftforge.network.NetworkHooks.openScreen(viewer,net.minecraft.world.SimpleMenuProvider(
                { id,inventory,_ -> DeviceMenu(id,inventory,node,0) },net.minecraft.network.chat.Component.translatable(node.parts[0]!!.definition.translationKey))) { it.writeBlockPos(pos);it.writeByte(0) }
        } }
        fun screenshot(name: String)=Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        if(ticks==20)server.execute {
            val world=server.overworld();world.dayTime=6000;world.setWeatherParameters(6000,0,false,false)
            for(x in 337..364)for(z in 111..122) {
                for(y in 120..180)world.setBlockAndUpdate(BlockPos(x,y,z),Blocks.AIR.defaultBlockState())
                world.setBlockAndUpdate(BlockPos(x,119,z),Blocks.STONE.defaultBlockState())
            }
            val viewer=server.playerList.players.first();viewer.setGameMode(net.minecraft.world.level.GameType.CREATIVE)
            viewer.abilities.flying=true;viewer.onUpdateAbilities();viewer.inventory.setItem(2,ItemStack.EMPTY);viewer.inventory.selected=2;viewer.inventoryMenu.broadcastChanges()
            device("shaft_motor",340);device("flywheel",340,114);device("joint",340,116);device("generator",340,117)
            device("electrical_source",341).parts[0]!!.value=3200.0
            device("creative_power_resistor",341,117).parts[0]!!.apply { rotation=2;value=10000.0 };device("ground_cable",342,117)
            val regen=device("shaft_motor",350);val wheel=device("flywheel",350,116)
            regen.parts[0]!!.shaftGroup="live-regenerating-motor";wheel.parts[0]!!.shaftGroup="live-regenerating-motor";wheel.parts[0]!!.shaftEnergy=55000.0;wheel.changedTopology()
            device("creative_power_resistor",351).parts[0]!!.apply { rotation=2;value=100000.0 };device("ground_cable",352)
            device("generator",357);device("flywheel",357,116)
            device("electrical_source",358).parts[0]!!.apply { value=3200.0;resistance=30.0 }
            viewer.teleportTo(world,340.5,121.8,114.0,0f,60f)
        }
        if(ticks==100) { mc.player!!.inventory.selected=2;open(BlockPos(340,120,115)) }
        if(ticks==160) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(p.shaftSpeed>20 && p.thermalState.getDouble("shaftElectricalWatts")<0 && p.thermalState.getDouble("shaftProducedJ")>0 && ClientAudio.activeAt(menu.node)>0)
            screenshot("eln-original-motor-driving-gui.png");Eln.LOGGER.info("ELN_CLIENT_QA original motor, winding heat, power LEDs and actual OpenAL loop confirmed")
        }
        if(ticks==180) { mc.player!!.closeContainer();open(BlockPos(340,120,117)) }
        if(ticks==240) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(p.thermalState.getDouble("shaftElectricalWatts")>0 && p.thermalState.getDouble("shaftElectricalOutputJ")>0 && ClientAudio.activeAt(menu.node)>0)
            screenshot("eln-original-generator-working-gui.png");Eln.LOGGER.info("ELN_CLIENT_QA original generator, physical electrical load, shaft loss and actual sound confirmed")
        }
        if(ticks==260) { mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),350.5,121.8,114.0,0f,60f) } }
        if(ticks==310)open(BlockPos(350,120,115))
        if(ticks==370) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(p.thermalState.getDouble("shaftElectricalWatts")>0 && p.thermalState.getDouble("shaftConsumedJ")>0 && p.current<0 && p.shaftEnergy>0)
            screenshot("eln-original-motor-regenerating-gui.png");Eln.LOGGER.info("ELN_CLIENT_QA original motor inefficient regeneration and red generating indicator confirmed")
        }
        if(ticks==390) { mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),357.5,121.8,114.0,0f,60f) } }
        if(ticks==440)open(BlockPos(357,120,115))
        if(ticks==500) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(p.thermalState.getDouble("shaftElectricalWatts")<0 && p.thermalState.getDouble("shaftProducedJ")>0 && p.current<0 && !p.tripped)
            screenshot("eln-original-generator-motoring-gui.png");Eln.LOGGER.info("ELN_CLIENT_QA original generator reverse motoring, finite input and efficiency loss confirmed")
        }
        if(ticks==520) { mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),346.0,123.5,122.0,145f,25f) } }
        if(ticks==600)screenshot("eln-original-bidirectional-shaft-yard.png")
        if(ticks==640) { Eln.LOGGER.info("ELN_CLIENT_QA original bidirectional shaft machinery complete");mc.stop() }
    }
}
