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
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

object ModbusClientSmoke {
    private var ticks=0
    @Volatile private var failure: String?=null
    private val at=BlockPos(1830,120,145)
    private fun node(s: IntegratedServer,pos: BlockPos)=s.overworld().getBlockEntity(pos) as FaceNodeEntity
    fun tick(mc: Minecraft,server: IntegratedServer) {
        check(failure==null) { "Modbus authoritative check failed: $failure" };ticks++
        fun verify(action: ()->Unit) { val tick=ticks;server.execute { try { action() }catch(e: Throwable) { failure="tick $tick: ${e.stackTraceToString()}" } } }
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,"eln-original-modbus-$name.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun screen()=mc.screen as ModbusScreen
        fun fields()=screen().children().filterIsInstance<EditBox>()
        fun buttons(key: String)=screen().children().filterIsInstance<Button>().filter { it.message.string==Component.translatable(key).string }
        fun button(key: String)=buttons(key).first().onPress()
        if(ticks==1)verify {
            val level=server.overworld();val player=server.playerList.players.first();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.inventory.clearContent();level.setWeatherParameters(12000,0,false,false);level.dayTime=6000
            for(x in 1823..1838)for(z in 140..151)for(y in 119..126)level.setBlockAndUpdate(BlockPos(x,y,z),if(y==119)Blocks.STONE.defaultBlockState()else Blocks.AIR.defaultBlockState())
            player.teleportTo(level,at.x+.5,120.0,at.z+3.0,180f,25f)
            val stack=ItemStack(Content.partItems.getValue("modbus_rtu").get());player.setItemInHand(InteractionHand.MAIN_HAND,stack)
            require(stack.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(at.below()),Direction.UP,at.below(),false))).consumesAction() && stack.isEmpty)
            node(server,at).apply { parts[0]!!.rotation=0;changedTopology() }
            fun device(pos: BlockPos,id: String): FaceNodeEntity { level.setBlockAndUpdate(pos,Content.node.get().defaultBlockState());return node(server,pos).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN);it.changedTopology() } }
            device(at.east(4),"wireless_signal_receiver").let { it.parts[0]!!.channel="workshop-out";it.changedTopology() }
            device(at.west(4),"wireless_switch").let { it.parts[0]!!.channel="workshop-in";it.parts[0]!!.enabled=true;it.changedTopology() }
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
        }
        if(ticks==35) { mc.options.guiScale().set(2);mc.resizeDisplay();BuildcraftSpriteAudit.verify(mc) }
        if(ticks==60)server.execute {
            val n=node(server,at);val player=server.playerList.players.first();n.sync()
            net.minecraftforge.network.NetworkHooks.openScreen(player,net.minecraft.world.SimpleMenuProvider({ id,inventory,_ -> DeviceMenu(id,inventory,n,0) },Component.translatable(n.parts[0]!!.definition.translationKey))) { it.writeBlockPos(at);it.writeByte(0) }
        }
        if(ticks==100) { fields()[0].value="42";fields()[1].value="Workshop RTU";button("gui.eln.apply");button("gui.eln.modbus_add_tx");button("gui.eln.modbus_add_rx") }
        if(ticks==145) {
            require(fields().size==6);fields()[2].value="workshop-out";fields()[3].value="100";fields()[4].value="workshop-in";fields()[5].value="200"
            buttons("gui.eln.apply")[1].onPress();buttons("gui.eln.apply")[2].onPress()
        }
        if(ticks==185)verify {
            val p=node(server,at).parts[0]!!;require(p.gridState.getInt("modbusStation")==42 && p.gridState.getString("modbusName")=="Workshop RTU" && ModbusDevices.entries(p).map { it.getInt("offset") }==listOf(100,200))
            PartModbusRegisters(p).writeWord(103,49151)
        }
        if(ticks==240) { verify { val p=node(server,at).parts[0]!!;require(node(server,at.east(4)).parts[0]!!.voltage in 3.70..3.80 && PartModbusRegisters(p).word(203,true)==65535) };image("actual-unit-name-tx-rx-channel-address-packets-and-live-reception") }
        if(ticks==255) { fields()[3].value="200";buttons("gui.eln.apply")[1].onPress() }
        if(ticks==285) { verify { require(ModbusDevices.entries(node(server,at).parts[0]!!).first().getInt("offset")==100) };image("overlapping-address-rejected-with-original-mappings-preserved");button("gui.eln.modbus_delete") }
        if(ticks==345) { verify { val p=node(server,at).parts[0]!!;require(ModbusDevices.entries(p).size==1 && node(server,at.east(4)).parts[0]!!.voltage<.001) };image("actual-delete-unregisters-radio-and-mapping");repeat(5) { button("gui.eln.modbus_add_tx") } }
        if(ticks==385) { require(fields().size==10);image("first-page-of-multiple-radio-mappings");button("gui.eln.next") }
        if(ticks==420) { require(fields().size==6);image("second-page-of-multiple-radio-mappings");mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),at.x+1.3,120.0,at.z+.5,90f,60f) } }
        if(ticks==465)image("original-open-rtu-housing-door-display-and-power-led")
        if(ticks==480)verify { val p=server.playerList.players.first();p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Content.partItems.getValue("modbus_rtu").get()));p.inventoryMenu.broadcastChanges() }
        if(ticks==520) { require(mc.player!!.mainHandItem.item==Content.partItems.getValue("modbus_rtu").get());image("original-held-rtu-example") }
        if(ticks==545) { Eln.LOGGER.info("ELN_CLIENT_QA original Modbus RTU actual survival placement, unit/name/add/configure/delete/overlap-rejection/paging GUI packets, actual wireless transmitter and receiver, original open housing/power LED/display/held model confirmed; networking remains disabled by default");mc.stop() }
    }
}
