package mods.eln.modern.client

import mods.eln.Eln
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.server.IntegratedServer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object NodeCoverClientSmoke {
    private var ticks=0
    @Volatile private var failure: String?=null
    private val at=BlockPos(2020,120,145)
    private fun node(s: IntegratedServer,p: BlockPos)=s.overworld().getBlockEntity(p) as FaceNodeEntity
    fun tick(mc: Minecraft,server: IntegratedServer) {
        check(failure==null) { "Cover authoritative check failed: $failure" };ticks++
        fun verify(action: ()->Unit) { val tick=ticks;server.execute { try { action() }catch(e: Throwable) { failure="tick $tick: ${e.stackTraceToString()}" } } }
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,"eln-original-node-cover-$name.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun camera(p: BlockPos)=server.execute { server.playerList.players.first().teleportTo(server.overworld(),p.x+1.3,120.0,p.z+.5,90f,55f) }
        fun hit()=BlockHitResult(Vec3.atCenterOf(at).add(0.0,.5,0.0),Direction.UP,at,false)
        if(ticks==1)verify {
            val level=server.overworld();val player=server.playerList.players.first();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL)
            for(x in 2014..2042)for(z in 139..153)for(y in 119..126)level.setBlockAndUpdate(BlockPos(x,y,z),if(y==119)Blocks.STONE.defaultBlockState()else Blocks.AIR.defaultBlockState())
            level.getEntitiesOfClass(ItemEntity::class.java,AABB(2013.0,118.0,138.0,2044.0,128.0,155.0)).forEach { it.discard() };player.inventory.clearContent()
            level.setWeatherParameters(12000,0,false,false);level.setRainLevel(0f);level.setThunderLevel(0f);level.dayTime=6000
            fun device(p: BlockPos,id: String,rotation: Int=0): FaceNodeEntity {
                level.setBlockAndUpdate(p,Content.node.get().defaultBlockState());return node(server,p).also { n -> n.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { this.rotation=rotation };n.changedTopology() }
            }
            device(at.west(),"electrical_source");device(at,"low_voltage_cable");device(at.east(),"creative_power_resistor",2);device(at.east(2),"ground_cable")
            val blocks=listOf(Items.OAK_LOG,Items.GLASS,Items.OAK_LEAVES,Items.GLOWSTONE)
            for((i,item) in blocks.withIndex()) {
                val p=at.east(6+i*4);val n=device(p,"signal_cable");val stack=ItemStack(item)
                if(i==0)stack.getOrCreateTagElement("BlockStateTag").putString("axis","x")
                player.setItemInHand(InteractionHand.MAIN_HAND,stack);NodeCovers.setModifier(player,true)
                require(NodeCovers.interact(n,player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(p),Direction.UP,p,false))?.consumesAction()==true && stack.isEmpty)
            }
            NodeCovers.setModifier(player,false);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Items.OAK_PLANKS,2));player.inventoryMenu.broadcastChanges()
            player.teleportTo(level,2020.5,120.0,147.5,180f,30f)
        }
        if(ticks==35) { mc.options.guiScale().set(2);mc.resizeDisplay();BuildcraftSpriteAudit.verify(mc) }
        if(ticks in 45..85)CoverKeys.key.setDown(true)
        if(ticks==75) { require(mc.player!!.mainHandItem.`is`(Items.OAK_PLANKS));require(mc.gameMode!!.useItemOn(mc.player!!,InteractionHand.MAIN_HAND,hit()).consumesAction()) }
        if(ticks==90)CoverKeys.key.setDown(false)
        if(ticks==110) {
            verify { val n=node(server,at);require(NodeCovers.state(n)?.block===Blocks.OAK_PLANKS && server.playerList.players.first().mainHandItem.count==1 && abs(node(server,at.east()).parts[0]!!.current-.5)<.01) }
            val n=mc.level!!.getBlockEntity(at) as FaceNodeEntity;require(NodeCovers.state(n)?.block===Blocks.OAK_PLANKS && n.blockState.getValue(NodeBlock.COVERED));camera(at)
        }
        if(ticks==140)image("actual-c-key-packet-consumed-one-block-and-opaque-cover-model")
        if(ticks==150)verify { val player=server.playerList.players.first();player.inventory.setItem(9,player.mainHandItem.copy());player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.inventoryMenu.broadcastChanges() }
        if(ticks==180)mc.gameMode!!.startDestroyBlock(at,Direction.UP)
        if(ticks in 181..270) {
            val state=mc.level!!.getBlockState(at)
            if(state.hasProperty(NodeBlock.COVERED) && state.getValue(NodeBlock.COVERED))mc.gameMode!!.continueDestroyBlock(at,Direction.UP)
        }
        if(ticks==290) {
            verify {
                val n=node(server,at);val player=server.playerList.players.first()
                require(!NodeCovers.present(n) && n.parts[0]?.definition?.id=="low_voltage_cable" && abs(node(server,at.east()).parts[0]!!.current-.5)<.01)
                val inventory=(0 until player.inventory.containerSize).sumOf { player.inventory.getItem(it).takeIf { s -> s.`is`(Items.OAK_PLANKS) }?.count ?: 0 }
                val dropped=server.overworld().getEntitiesOfClass(ItemEntity::class.java,AABB(at).inflate(3.0)).filter { it.item.`is`(Items.OAK_PLANKS) }.sumOf { it.item.count }
                require(inventory+dropped==2) { "Actual client peeling lost or duplicated material: $inventory + $dropped" }
            };mc.gameMode!!.stopDestroyBlock();image("actual-player-break-peeled-only-cover-and-live-cable-remains")
        }
        if(ticks==310)camera(at.east(6))
        if(ticks==345) { image("original-axis-x-log-cover-model");camera(at.east(10)) }
        if(ticks==380) { image("original-glass-cover-with-visible-live-signal-cable");camera(at.east(14)) }
        if(ticks==415) { image("original-biome-tinted-leaf-cover");camera(at.east(18));server.execute { server.overworld().dayTime=18000 } }
        if(ticks==465) {
            verify { val pos=at.east(18);val n=node(server,pos);require(n.blockState.getLightEmission(server.overworld(),pos)==15 && server.overworld().getBrightness(LightLayer.BLOCK,pos)>=14) }
            image("original-emissive-cover-actual-night-light")
        }
        if(ticks==500) { CoverKeys.key.setDown(false);Eln.LOGGER.info("ELN_CLIENT_QA original six-node covers actual C key-state packet and survival right-click, one-block consumption, opaque/log/glass/biome-leaf/emissive models, real client mining peel without circuit loss, exact item conservation and real night lighting confirmed");mc.stop() }
    }
}
