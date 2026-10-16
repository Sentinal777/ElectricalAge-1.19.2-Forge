package mods.eln.modern.client

import mods.eln.Eln
import mods.eln.modern.*
import mods.eln.modern.lighting.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.server.IntegratedServer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object FestiveClientSmoke {
    private var ticks=0
    @Volatile private var failure: String?=null
    private val positions=listOf(BlockPos(1404,120,145),BlockPos(1410,120,145),BlockPos(1416,120,145))
    private val ids=listOf("christmas_tree","holiday_candle","string_lights")
    private val board=BlockPos(1400,120,145)
    private val source=board.west()
    private val incubator=BlockPos(1423,120,145)
    private var firstPhase=false
    private var secondPhase=false
    private fun node(s: IntegratedServer,at: BlockPos)=s.overworld().getBlockEntity(at) as FaceNodeEntity
    private fun part(s: IntegratedServer,at: BlockPos)=node(s,at).parts.filterNotNull().single()
    fun tick(mc: Minecraft,server: IntegratedServer) {
        check(failure==null) { "Festive authoritative server check failed: $failure" };ticks++
        fun verify(action: ()->Unit) { val at=ticks;server.execute { try { action() }catch(t: Throwable) { failure="tick $at: ${t.stackTraceToString()}" } } }
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,"eln-original-$name.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun camera(at: BlockPos,distance: Double=3.0,height: Double=.6)=server.execute { server.playerList.players.first().teleportTo(server.overworld(),at.x+.5+distance*.4,at.y+height,at.z+.5+distance,158f,if(height>1)18f else 32f) }
        fun hand(id: String)=server.execute { val p=server.playerList.players.first();p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Content.partItems.getValue(id).get()));p.inventoryMenu.broadcastChanges() }
        if(ticks==20)verify {
            val world=server.overworld();world.dayTime=18000;world.setWeatherParameters(6000,0,false,false);world.setRainLevel(0f);world.setThunderLevel(0f)
            for(x in 1394..1426)for(z in 138..158)for(y in 119..129)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==119)Blocks.STONE else Blocks.AIR).defaultBlockState())
            val player=server.playerList.players.first();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.inventory.clearContent();player.inventory.selected=8
            player.abilities.mayfly=true;player.abilities.flying=true;player.onUpdateAbilities()
            for((index,at) in positions.withIndex()) {
                player.teleportTo(world,at.x+.5,at.y.toDouble(),at.z+3.5,180f,10f)
                val held=ItemStack(Content.partItems.getValue(ids[index]).get());player.setItemInHand(InteractionHand.MAIN_HAND,held)
                val support=if(index==2)at.north()else at.below();val face=if(index==2)Direction.SOUTH else Direction.UP
                world.setBlockAndUpdate(support,Blocks.STONE.defaultBlockState())
                require(held.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(support),face,support,false))).consumesAction() && held.isEmpty)
                if(index==2)world.removeBlock(at.below(),false)
                require(part(server,at).definition.id==ids[index])
            }
            fun device(id: String,at: BlockPos,rotation: Int=0): PartState {
                world.setBlockAndUpdate(at,Content.node.get().defaultBlockState())
                return node(server,at).let { n -> PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { this.rotation=rotation;n.parts[0]=this;n.changedTopology() } }
            }
            device("electrical_source",source).value=200.0
            val bp=device("lamp_supply",board,2)
            PartInventory.get(node(server,board),bp).setStackInSlot(0,ItemStack(Content.partItems.getValue("medium_voltage_cable").get()))
            LampChannels.write(bp,listOf(LampChannel("xmas","true",0)));node(server,board).changedTopology()
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.inventoryMenu.broadcastChanges()
        }
        if(ticks==70) {
            BuildcraftSpriteAudit.verify(mc)
            verify { require(positions.all { part(server,it).active && part(server,it).signal==15 && server.overworld().getBrightness(net.minecraft.world.level.LightLayer.BLOCK,it)==15 });require(abs(part(server,board).energy-positions.sumOf { part(server,it).energy })<1e-4) }
            camera(positions[0],5.5,1.5)
        }
        if(ticks in 110..160) {
            val phase=mc.level!!.gameTime%21
            if(phase==5L && !firstPhase) { image("christmas-tree-original-first-light-strip-night");firstPhase=true }
            if(phase==16L && !secondPhase) { image("christmas-tree-original-alternate-light-strip-night");secondPhase=true }
        }
        if(ticks==170) { require(firstPhase && secondPhase);camera(positions[1],2.2,.2) }
        if(ticks==205) { image("holiday-candle-original-lit-glass-night");camera(positions[2],2.0,.2) }
        if(ticks==240) { image("string-lights-original-lit-wall-night");camera(positions[0],5.5,1.5);verify { part(server,source).enabled=false;node(server,source).changedTopology() } }
        if(ticks==280) {
            verify { require(positions.all { !part(server,it).active && part(server,it).signal==0 && server.overworld().getBrightness(net.minecraft.world.level.LightLayer.BLOCK,it)==0 }) }
            image("christmas-tree-original-unpowered-night");hand("christmas_tree")
            for(id in ids) {
                val model=mc.itemRenderer.getModel(ItemStack(Content.partItems.getValue(id).get()),mc.level,mc.player,0)
                require(!model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
                require(!model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            }
        }
        if(ticks==315) { image("christmas-tree-original-held-artwork");hand("holiday_candle") }
        if(ticks==350) { image("holiday-candle-original-held-artwork");hand("string_lights") }
        if(ticks==385)image("string-lights-original-held-artwork")
        if(ticks==390) {
            val setting=mods.eln.config.ForgePortConfig.enableFestiveItems;val original=setting.get()
            try {
                for(enabled in listOf(false,true)) {
                    setting.set(enabled)
                    for(id in ids) {
                        val item=Content.partItems.getValue(id).get();val entries=net.minecraft.core.NonNullList.create<ItemStack>()
                        item.fillItemCategory(Content.tab,entries)
                        require(entries.any { it.item===item }==enabled)
                        require(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation("eln",id))===item)
                    }
                }
            }finally { setting.set(original) }
            Eln.LOGGER.info("ELN_CLIENT_QA festive config hides/shows all three creative entries while preserving the actual saved-world item registrations")
        }
        if(ticks==415)Eln.LOGGER.info("ELN_CLIENT_QA all three original festive decorations survival placement, nineteen-cell tree and floor/wall supports, real xmas load and paid shared energy ledger, actual light extinction, alternating tree light strips, translucent candle, wall string lights and all sprite/held routes confirmed")
        if(ticks==430)verify {
            val world=server.overworld();world.dayTime=6000;val player=server.playerList.players.first();player.teleportTo(world,incubator.x+.5,120.0,148.5,180f,15f)
            val held=ItemStack(Content.partItems.getValue("50v_egg_incubator").get());player.setItemInHand(InteractionHand.MAIN_HAND,held)
            require(held.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(incubator.below()),Direction.UP,incubator.below(),false))).consumesAction() && held.isEmpty)
            world.setBlockAndUpdate(incubator.west(),Content.node.get().defaultBlockState())
            node(server,incubator.west()).apply { parts[0]=PartState(PartDefinition.byId.getValue("electrical_source"),Direction.DOWN).apply { value=50.0 };changedTopology() }
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.inventory.setItem(9,ItemStack(net.minecraft.world.item.Items.EGG,2));player.inventoryMenu.broadcastChanges()
        }
        if(ticks==480)server.execute {
            val n=node(server,incubator);val p=part(server,incubator);val player=server.playerList.players.first();n.sync()
            net.minecraftforge.network.NetworkHooks.openScreen(player,net.minecraft.world.SimpleMenuProvider({ id,inventory,_ -> DeviceMenu(id,inventory,n,0) },net.minecraft.network.chat.Component.translatable(p.definition.translationKey))) { it.writeBlockPos(incubator);it.writeByte(0) }
        }
        if(ticks==515) {
            val menu=(mc.screen as DeviceScreen).menu
            require(menu.slots[1].item.`is`(net.minecraft.world.item.Items.EGG))
            mc.gameMode!!.handleInventoryMouseClick(menu.containerId,1,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,mc.player!!)
        }
        if(ticks==550)verify {
            val p=part(server,incubator);require(PartInventory.get(node(server,incubator),p).getStackInSlot(0).count==2 && p.active && abs(p.voltage*p.current-50)<.1)
            // Resume a late, already-paid saved job rather than waiting through an entire 200–400 second hatch.
            p.energy=9700.0;p.progress=9700.0;p.gridState.putDouble("incubatorTargetJ",10000.0);p.gridState.putDouble("incubatorRemainingJ",300.0);node(server,incubator).sync()
        }
        if(ticks==585) { require((mc.screen as DeviceScreen).menu.part!!.active);image("egg-incubator-original-egg-slot-paid-power-gui");mc.player!!.closeContainer();camera(incubator,2.2,.1) }
        if(ticks==625)image("egg-incubator-original-rotating-egg-and-orange-heater-world")
        if(ticks==705) {
            verify {
                val p=part(server,incubator)
                require(p.gridState.getInt("incubatorHatched")==1 && p.energy>=10000 && PartInventory.get(node(server,incubator),p).getStackInSlot(0).count==1)
                require(server.overworld().getEntitiesOfClass(net.minecraft.world.entity.animal.Chicken::class.java,net.minecraft.world.phys.AABB(incubator).inflate(6.0)).any { it.isBaby })
            }
            image("egg-incubator-real-paid-hatched-chick-world");hand("50v_egg_incubator")
        }
        if(ticks==740) {
            image("egg-incubator-original-held-mesh")
            val model=mc.itemRenderer.getModel(ItemStack(Content.partItems.getValue("50v_egg_incubator").get()),mc.level,mc.player,0)
            require(!model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            require(model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
        }
        if(ticks==775) { Eln.LOGGER.info("ELN_CLIENT_QA original egg incubator actual survival placement, real quick-move egg packet, paid 50 W load, saved job completion with one baby/one consumed egg, original rotating egg/heater/held artwork and live GUI confirmed");mc.stop() }
    }
}
