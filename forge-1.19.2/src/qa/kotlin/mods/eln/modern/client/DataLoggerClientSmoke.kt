package mods.eln.modern.client

import mods.eln.Eln
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.server.IntegratedServer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

object DataLoggerClientSmoke {
    private var ticks=0
    @Volatile private var failure: String?=null
    private val at=BlockPos(1645,120,145)
    private var snapshot: net.minecraft.nbt.CompoundTag?=null
    private var paused: ByteArray?=null
    private fun position(index: Int)=at.east(index*5).let { if(index==2)it.above()else it }
    private fun node(s: IntegratedServer,p: BlockPos=at)=s.overworld().getBlockEntity(p) as FaceNodeEntity
    fun tick(mc: Minecraft,server: IntegratedServer) {
        check(failure==null) { "Data logger authoritative check failed: $failure" };ticks++
        fun verify(action: ()->Unit) { val tick=ticks;server.execute { try { action() }catch(t: Throwable) { failure="tick $tick: ${t.stackTraceToString()}" } } }
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,"eln-original-data-logger-$name.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun screen()=mc.screen as DataLoggerScreen
        fun button(text: String)=screen().children().filterIsInstance<Button>().first { it.visible && it.message.string==text }.onPress()
        fun signal(value: Double)=verify { for(i in 0..2) { val pos=position(i);val p=node(server,pos).parts.filterNotNull().first();node(server,pos.relative(FaceBasis.edges(p)[1])).parts.filterNotNull().first().value=value } }
        if(ticks==20)verify {
            val world=server.overworld();world.dayTime=6000;world.setWeatherParameters(12000,0,false,false)
            for(x in 1639..1662)for(z in 137..153)for(y in 119..127)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==119)Blocks.STONE else Blocks.AIR).defaultBlockState())
            val player=server.playerList.players.first();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.inventory.clearContent();player.inventory.selected=8
            player.abilities.mayfly=true;player.abilities.flying=true;player.onUpdateAbilities();player.teleportTo(world,1645.5,121.0,149.0,180f,15f)
            for((i,id) in listOf("data_logger","modern_data_logger","industrial_data_logger").withIndex()) {
                val pos=position(i);val item=ItemStack(Content.partItems.getValue(id).get());player.setItemInHand(InteractionHand.MAIN_HAND,item)
                val support=if(i==2)pos.north()else pos.below();val side=if(i==2)Direction.SOUTH else Direction.UP
                world.setBlockAndUpdate(support,Blocks.STONE.defaultBlockState())
                require(item.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(support),side,support,false))).consumesAction() && item.isEmpty)
                val p=node(server,pos).parts.filterNotNull().first();if(i!=2)p.rotation=1;node(server,pos).changedTopology()
                val power=pos.relative(FaceBasis.edges(p)[1]);world.setBlockAndUpdate(power.relative(p.face),Blocks.STONE.defaultBlockState());world.setBlockAndUpdate(power,Content.node.get().defaultBlockState())
                node(server,power).apply { parts[p.face.get3DDataValue()]=PartState(PartDefinition.byId.getValue("signal_source"),p.face).apply { value=5.0 };changedTopology() }
                DataLoggers.configure(node(server,pos),p,.05,-50.0,50.0,0,true)
            }
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.inventory.setItem(9,ItemStack(Items.PAPER,2));player.inventoryMenu.broadcastChanges()
        }
        if(ticks==35) { mc.options.guiScale().set(1);mc.resizeDisplay() }
        if(ticks==60)BuildcraftSpriteAudit.verify(mc)
        if(ticks==70)server.execute {
            val player=server.playerList.players.first();val n=node(server);n.sync()
            net.minecraftforge.network.NetworkHooks.openScreen(player,net.minecraft.world.SimpleMenuProvider({ id,inventory,_ -> DeviceMenu(id,inventory,n,0) },net.minecraft.network.chat.Component.translatable(n.parts[0]!!.definition.translationKey))) { it.writeBlockPos(at);it.writeByte(0) }
        }
        if(ticks==100)mc.gameMode!!.handleInventoryMouseClick(screen().menu.containerId,2,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,mc.player!!)
        if(ticks==120) { require(screen().menu.slots[0].item.count==2);button("Configuration") }
        if(ticks==140) {
            val fields=screen().children().filterIsInstance<EditBox>();require(fields.size==3)
            fields[0].value="-50";fields[1].value="50";fields[2].value="0.05";button("Apply")
        }
        for((i,label) in listOf("V","A","W","°C","%","J","No unit","°F","% RH").withIndex())if(ticks==150+i*4)button(label)
        if(ticks==194) { require(screen().menu.part!!.gridState.getInt("loggerUnit")==8);image("actual-nine-unit-range-period-controls");button("V") }
        if(ticks==205)button("Back to display")
        if(ticks==220)signal(0.0)
        if(ticks==260)signal(2.5)
        if(ticks==300)signal(5.0)
        if(ticks==350)button("Running")
        if(ticks==370)verify { val p=node(server).parts[0]!!;require(p.gridState.getBoolean("loggerPaused"));paused=DataLoggers.samples(p) }
        if(ticks==390) { image("actual-averaged-live-graph-paused-with-paper");button("Print") }
        if(ticks==410) {
            val result=screen().menu.slots[1].item;require(result.item==Content.materialItems.getValue("data_logger_print").get() && screen().menu.slots[0].item.count==1 && result.tag!!.getByteArray("log").size>30)
            snapshot=result.tag!!.copy();image("actual-consumed-paper-and-printed-graph-item")
            mc.gameMode!!.handleInventoryMouseClick(screen().menu.containerId,1,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,mc.player!!)
        }
        if(ticks==430) { button("Paused");signal(0.0) }
        if(ticks==460) {
            verify {
                val p=node(server).parts[0]!!;val player=server.playerList.players.first();val print=(0 until player.inventory.containerSize).map { player.inventory.getItem(it) }.first { it.item==Content.materialItems.getValue("data_logger_print").get() }
                require(print.tag==snapshot && !DataLoggers.samples(p).contentEquals(paused!!) && player.mainHandItem===print)
                for(i in 0..2) { val other=node(server,position(i)).parts.filterNotNull().first();require(DataLoggers.samples(other).size==256 && DataLoggers.samples(other).first()==(-128).toByte()) { "Monitor $i did not record its actual wall/floor input: ${other.save()}" } }
                player.inventoryMenu.broadcastChanges()
            };mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),1647.5,120.0,148.5,145f,13f) }
        }
        if(ticks==490) {
            require(mc.itemRenderer.getModel(mc.player!!.mainHandItem,mc.level,mc.player,0).isCustomRenderer)
            image("actual-original-printed-graph-held-and-world-monitor")
            server.execute { val player=server.playerList.players.first();val printed=player.mainHandItem;player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.inventory.setItem(10,printed);player.inventoryMenu.broadcastChanges();player.teleportTo(server.overworld(),1650.5,122.5,154.5,180f,12f) }
        }
        if(ticks==520)image("three-original-monitor-models-and-live-world-plots")
        if(ticks==530)server.execute { server.playerList.players.first().teleportTo(server.overworld(),1650.5,120.0,148.3,180f,20f) }
        if(ticks==555)image("actual-front-modern-monitor-live-plot-closeup")
        if(ticks==565)server.execute { server.playerList.players.first().teleportTo(server.overworld(),1655.5,120.3,148.3,180f,8f) }
        if(ticks==590)image("actual-wall-industrial-monitor-live-plot-closeup")
        if(ticks==610) {
            Eln.LOGGER.info("ELN_CLIENT_QA original data loggers actual survival placement, source rear input, real paper quick move, all nine unit/range/period packets, pause and immutable one-paper print, actual held/inventory graph and original three live world monitor models confirmed")
            mc.stop()
        }
    }
}
