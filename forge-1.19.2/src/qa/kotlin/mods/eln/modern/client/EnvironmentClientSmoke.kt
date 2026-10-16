package mods.eln.modern.client

import mods.eln.Eln
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.server.IntegratedServer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.animal.Cow
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object EnvironmentClientSmoke {
    private var ticks=0
    private var animal: Cow?=null
    private fun pos(x: Int)=BlockPos(x,120,115)
    fun tick(mc: Minecraft,server: IntegratedServer) {
        ticks++
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun node(x: Int)=server.overworld().getBlockEntity(if(x==674)pos(x).above() else pos(x)) as FaceNodeEntity
        fun part(x: Int)=node(x).parts.filterNotNull().first()
        fun open(x: Int)=server.execute {
            val n=node(x);val p=part(x);n.sync();val viewer=server.playerList.players.first()
            viewer.teleportTo(server.overworld(),x-1.5,121.0,113.5,-20f,50f)
            net.minecraftforge.network.NetworkHooks.openScreen(viewer,net.minecraft.world.SimpleMenuProvider(
                { id,inventory,_ -> DeviceMenu(id,inventory,n,p.face.ordinal) },net.minecraft.network.chat.Component.translatable(p.definition.translationKey))) { it.writeBlockPos(n.blockPos);it.writeByte(p.face.ordinal) }
        }
        if(ticks==20)server.execute {
            val world=server.overworld();world.dayTime=6000;world.setWeatherParameters(6000,0,false,false)
            for(x in 638..680)for(z in 109..124)for(y in 119..138)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==119)Blocks.STONE else Blocks.AIR).defaultBlockState())
            world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity::class.java,net.minecraft.world.phys.AABB(638.0,119.0,109.0,681.0,139.0,125.0)).forEach { it.discard() }
            fun create(id: String,at: BlockPos,face: Direction=Direction.DOWN): FaceNodeEntity {
                world.setBlockAndUpdate(at.relative(face),Blocks.STONE.defaultBlockState());world.setBlockAndUpdate(at,Content.node.get().defaultBlockState())
                return (world.getBlockEntity(at) as FaceNodeEntity).also { it.parts[face.ordinal]=PartState(PartDefinition.byId.getValue(id),face);it.changedTopology() }
            }
            for((x,id) in listOf(642 to "humidity_sensor",650 to "electrical_entity_sensor",658 to "electrical_fire_detector",666 to "electrical_fire_buzzer")) {
                create(id,pos(x));if(x!=666)create("signal_cable",pos(x).south())
            }
            create("electrical_anemometer_sensor",pos(674).above(),Direction.NORTH);create("signal_cable",pos(674),Direction.NORTH)
            val viewer=server.playerList.players.first();viewer.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);viewer.inventory.clearContent();viewer.inventory.selected=0
            for(slot in listOf(net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET))viewer.setItemSlot(slot,ItemStack.EMPTY)
            viewer.abilities.mayfly=true;viewer.abilities.flying=true;viewer.onUpdateAbilities()
            for((x,stack) in listOf(650 to ItemStack(Content.materialItems.getValue("animal_filter").get()),666 to ItemStack(Content.materialItems.getValue("portable_battery").get()).also { ElectricalItems.setEnergy(it,3.0) })) {
                viewer.setItemInHand(InteractionHand.MAIN_HAND,stack)
                node(x).blockState.use(world,viewer,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(pos(x)),Direction.UP,pos(x),false));require(stack.isEmpty)
            }
            animal=EntityType.COW.create(world)!!.also { it.isNoAi=true;it.setNoGravity(true);it.moveTo(653.5,120.0,115.5,0f,0f);world.addFreshEntity(it) }
            for(x in listOf(658,666)) { val fire=pos(x).south(3);world.setBlockAndUpdate(fire.below(),Blocks.NETHERRACK.defaultBlockState());world.setBlockAndUpdate(fire,Blocks.FIRE.defaultBlockState()) }
            viewer.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);viewer.inventoryMenu.broadcastChanges()
        }
        if(ticks in 21..569)server.execute { animal?.setPos(653.5+(if(server.tickCount%8<4).5 else -.5),120.0,115.5) }
        if(ticks==80)open(642)
        if(ticks==140) {
            val p=(mc.screen as DeviceScreen).menu.part!!
            require(p.voltage in .1..5.0 && p.thermalState.getDouble("envMeasured")>0)
            image("eln-original-humidity-sensor-gui.png")
        }
        if(ticks==160) { mc.player!!.closeContainer();open(650) }
        if(ticks==220) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!
            require(p.active && p.voltage>3 && PartInventory.get(menu.node,p).getStackInSlot(0).item==Content.materialItems.getValue("animal_filter").get())
            image("eln-original-motion-sensor-filter-gui.png")
        }
        if(ticks==240) { mc.player!!.closeContainer();open(658) }
        if(ticks==300) {
            val p=(mc.screen as DeviceScreen).menu.part!!;require(p.active && p.voltage>4.8 && p.thermalState.getBoolean("envFire"))
            image("eln-original-wired-fire-detector-gui.png")
        }
        if(ticks==320) { mc.player!!.closeContainer();open(666) }
        if(ticks==380) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!;val energy=ElectricalItems.energy(PartInventory.get(menu.node,p).getStackInSlot(0))
            val audio=ClientAudio.activeAt(menu.node)
            require(p.active && p.thermalState.getBoolean("envPowered") && energy>0 && audio>0) { "Buzzer active=${p.active}, powered=${p.thermalState.getBoolean("envPowered")}, energy=$energy, spent=${p.energy}, audio=$audio" }
            server.execute { val real=part(666);require(abs(real.energy+ElectricalItems.energy(PartInventory.get(node(666),real).getStackInSlot(0))-3)<1e-8) }
            image("eln-original-battery-fire-buzzer-running-gui.png")
            Eln.LOGGER.info("ELN_CLIENT_QA actual sensor/filter/battery insertion, climate and moving animal outputs, real fire sight and original fire alarm playing through OpenAL confirmed")
        }
        if(ticks==400) { mc.player!!.closeContainer();open(674) }
        if(ticks==460) {
            val p=(mc.screen as DeviceScreen).menu.part!!
            require(p.voltage>4.9 && p.thermalState.getDouble("envMeasured")>=100)
            image("eln-original-wall-anemometer-gui.png")
        }
        if(ticks==480) { mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),674.5,122.3,118.5,180f,12f) } }
        if(ticks==500)image("eln-original-wall-anemometer-world.png")
        if(ticks==510)open(666)
        if(ticks==545) {
            val menu=(mc.screen as DeviceScreen).menu;val p=menu.part!!;val energy=ElectricalItems.energy(PartInventory.get(menu.node,p).getStackInSlot(0))
            require(!p.active && !p.thermalState.getBoolean("envPowered") && energy in 0.0.. .027778 && abs(p.energy+energy-3)<1e-8 && ClientAudio.activeAt(menu.node)==0)
            image("eln-original-battery-fire-buzzer-empty-gui.png")
            Eln.LOGGER.info("ELN_CLIENT_QA original battery buzzer exhausted its actual three-joule store, retained only its original reserve and stopped real audio without creating energy")
        }
        if(ticks==550) { mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),648.4,121.1,117.4,-140f,45f) } }
        if(ticks==565)image("eln-original-animal-motion-blue-halo-world.png")
        if(ticks==650) {
            val sensor=(mc.level!!.getBlockEntity(pos(650)) as FaceNodeEntity).parts.filterNotNull().first()
            require(!sensor.active && sensor.voltage<.1)
            image("eln-original-stationary-animal-sensor-off-world.png")
            for(d in PartDefinition.all.filter { it.environment!=null }) {
                val stack=ItemStack(Content.partItems.getValue(d.id).get());val model=mc.itemRenderer.getModel(stack,mc.level,mc.player,0)
                require(!model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
                require(model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            }
            Eln.LOGGER.info("ELN_CLIENT_QA all five original environmental held/inventory models and actual stationary motion shutdown confirmed")
        }
        if(ticks==680) { server.execute { animal?.discard() };Eln.LOGGER.info("ELN_CLIENT_QA original environmental sensors complete");mc.stop() }
    }
}
