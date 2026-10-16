package mods.eln.modern.client

import mods.eln.Eln
import mods.eln.modern.*
import mods.eln.modern.grid.GridMultiblock
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.server.IntegratedServer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks

object RenewableClientSmoke {
    private var ticks=0
    private var windSound=false
    private var waterSound=false
    private val windPos=BlockPos(457,120,115)
    private val waterPos=BlockPos(468,120,115)
    fun tick(mc: Minecraft,server: IntegratedServer) {
        ticks++
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        for((at,isWind) in listOf(windPos to true,waterPos to false)) {
            val n=mc.level?.getBlockEntity(at) as? FaceNodeEntity
            val p=n?.parts?.get(0)
            if(n!=null && p!=null && p.definition.behavior in setOf(Behavior.WIND_TURBINE,Behavior.WATER_TURBINE) && RenewableVisuals.activeSounds(n,p)>0) {
                if(isWind)windSound=true else waterSound=true
            }
        }
        fun open(at: BlockPos)=server.execute {
            val n=server.overworld().getBlockEntity(at) as FaceNodeEntity;n.sync();val viewer=server.playerList.players.first()
            if(at==windPos)viewer.teleportTo(server.overworld(),453.0,123.0,120.0,-135f,15f)
            else viewer.teleportTo(server.overworld(),471.0,123.0,120.0,145f,30f)
            net.minecraftforge.network.NetworkHooks.openScreen(viewer,net.minecraft.world.SimpleMenuProvider(
                { id,inventory,_ -> DeviceMenu(id,inventory,n,0) },net.minecraft.network.chat.Component.translatable(n.parts[0]!!.definition.translationKey))) { it.writeBlockPos(n.blockPos);it.writeByte(0) }
        }
        if(ticks==20)server.execute {
            val world=server.overworld();world.dayTime=6000;world.setWeatherParameters(6000,0,false,false);world.setRainLevel(0f);world.setThunderLevel(0f)
            for(x in 449..477)for(z in 107..130) {
                for(y in 120..180)world.setBlockAndUpdate(BlockPos(x,y,z),Blocks.AIR.defaultBlockState())
                world.setBlockAndUpdate(BlockPos(x,119,z),Blocks.STONE.defaultBlockState())
            }
            fun device(id: String,at: BlockPos,rotation: Int=0): FaceNodeEntity {
                val d=PartDefinition.byId.getValue(id);world.setBlockAndUpdate(at,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
                return (world.getBlockEntity(at) as FaceNodeEntity).also {
                    it.parts[0]=PartState(d,Direction.DOWN).apply { this.rotation=rotation };GridMultiblock.install(it,it.parts[0]!!);it.changedTopology()
                }
            }
            device("wind_turbine",windPos,3)
            device("creative_power_resistor",windPos.west(),2).parts[0]!!.value=5.0;device("ground_cable",windPos.west(2))
            val water=device("water_turbine",waterPos)
            device("creative_power_resistor",waterPos.north(),1).parts[0]!!.value=80.0;device("ground_cable",waterPos.north(2))
            val intake=RenewableRuntime.waterPosition(water,water.parts[0]!!)
            for(x in -3..3) {
                world.setBlockAndUpdate(intake.offset(x,-1,0),Blocks.STONE.defaultBlockState())
                for(z in listOf(-1,1))world.setBlockAndUpdate(intake.offset(x,0,z),Blocks.STONE.defaultBlockState())
                world.setBlockAndUpdate(intake.offset(x,0,0),if(x in listOf(-3,3))Blocks.STONE.defaultBlockState()else Blocks.AIR.defaultBlockState())
            }
            world.setBlockAndUpdate(intake.east(2),Blocks.WATER.defaultBlockState())
            val viewer=server.playerList.players.first();viewer.setGameMode(net.minecraft.world.level.GameType.CREATIVE)
            viewer.abilities.flying=true;viewer.onUpdateAbilities();viewer.inventory.setItem(2,ItemStack.EMPTY);viewer.inventory.selected=2;viewer.inventoryMenu.broadcastChanges()
            viewer.teleportTo(world,453.0,123.0,120.0,-135f,15f)
        }
        if(ticks==100)open(windPos)
        if(ticks==160) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(p.definition.behavior==Behavior.WIND_TURBINE && p.rotation==3 && p.voltage in 45.0..59.001 && p.energy>1000 && p.gridState.getDouble("windExposure")==1.0 && windSound)
            image("eln-original-wind-turbine-gui.png");Eln.LOGGER.info("ELN_CLIENT_QA original wind curve, actual load and actual blade-triggered OpenAL sound confirmed")
        }
        if(ticks==180) { mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),450.0,122.3,115.5,-90f,12f) } }
        if(ticks==220)image("eln-original-wind-turbine-world.png")
        if(ticks==240)open(waterPos)
        if(ticks==350) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(p.definition.behavior==Behavior.WATER_TURBINE && p.voltage>5 && p.energy>20 && p.thermalState.getDouble("renewableAvailableWatts")>1 && waterSound)
            server.execute {
                val n=server.overworld().getBlockEntity(waterPos) as FaceNodeEntity
                require(RenewableRuntime.waterFactor(server.overworld(),RenewableRuntime.waterPosition(n,n.parts[0]!!))>0)
            }
            image("eln-original-water-turbine-gui.png");Eln.LOGGER.info("ELN_CLIENT_QA real flowing channel, original water filter, actual electrical load and actual wheel-triggered OpenAL sound confirmed")
        }
        if(ticks==370)mc.player!!.closeContainer()
        if(ticks==400)image("eln-original-water-turbine-world.png")
        if(ticks==420)server.execute { server.playerList.players.first().teleportTo(server.overworld(),462.0,127.0,130.0,180f,25f) }
        if(ticks==470)image("eln-original-wind-water-generators-world.png")
        fun hold(id: String)=server.execute {
            val viewer=server.playerList.players.first();viewer.inventory.setItem(2,ItemStack(Content.partItems.getValue(id).get()));viewer.inventory.selected=2;viewer.inventoryMenu.broadcastChanges()
        }
        if(ticks==480)hold("wind_turbine")
        if(ticks==520) {
            for(d in PartDefinition.all.filter { it.heldModel }) {
                val stack=ItemStack(Content.partItems.getValue(d.id).get());val model=mc.itemRenderer.getModel(stack,mc.level,mc.player,0)
                fun transformed(type: net.minecraft.client.renderer.block.model.ItemTransforms.TransformType)=net.minecraftforge.client.ForgeHooksClient.handleCameraTransforms(com.mojang.blaze3d.vertex.PoseStack(),model,type,false)
                require(!transformed(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI).isCustomRenderer) { "Inventory sprite lost for ${d.id}" }
                require(transformed(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND).isCustomRenderer) { "Held mesh missing for ${d.id}" }
            }
            image("eln-original-wind-turbine-held.png");Eln.LOGGER.info("ELN_CLIENT_QA original machine inventory sprites and separate held 3D model perspectives confirmed")
        }
        if(ticks==550)hold("large_generator")
        if(ticks==590)image("eln-original-large-generator-held.png")
        if(ticks==620)hold("shaft_motor")
        if(ticks==660)image("eln-original-shaft-motor-held.png")
        if(ticks==690) { Eln.LOGGER.info("ELN_CLIENT_QA original wind and water turbines complete");mc.stop() }
    }
}
