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
import kotlin.math.abs

object MqttClientSmoke {
    private var ticks=0
    @Volatile private var failure: String?=null
    private val at=BlockPos(1790,120,145)
    private var parked=0.0
    private fun node(s: IntegratedServer,pos: BlockPos)=s.overworld().getBlockEntity(pos) as FaceNodeEntity
    fun tick(mc: Minecraft,server: IntegratedServer) {
        check(failure==null) { "MQTT authoritative check failed: $failure" };ticks++
        fun verify(action: ()->Unit) { val t=ticks;server.execute { try { action() }catch(e: Throwable) { failure="tick $t: ${e.stackTraceToString()}" } } }
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,"eln-original-mqtt-$name.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun screen()=mc.screen as MqttScreen
        fun fields()=screen().children().filterIsInstance<EditBox>()
        fun button(key: String)=screen().children().filterIsInstance<Button>().first { it.message.string==Component.translatable(key).string }.onPress()
        fun mode(port: String)=screen().children().filterIsInstance<Button>().first { it.message.string.startsWith("$port: ") }.onPress()
        fun open(meter: Boolean)=server.execute {
            val pos=if(meter)at.east(6)else at;val n=node(server,pos);val player=server.playerList.players.first();player.teleportTo(server.overworld(),pos.x+.5,120.0,pos.z+3.0,180f,20f);n.sync()
            net.minecraftforge.network.NetworkHooks.openScreen(player,net.minecraft.world.SimpleMenuProvider({ id,inventory,_ -> DeviceMenu(id,inventory,n,0) },Component.translatable(n.parts[0]!!.definition.translationKey))) { it.writeBlockPos(pos);it.writeByte(0) }
        }
        fun camera(x: Double,z: Double,yaw: Float,pitch: Float=35f)=server.execute { server.playerList.players.first().teleportTo(server.overworld(),x,120.0,z,yaw,pitch) }
        if(ticks==1)verify {
            val level=server.overworld();val player=server.playerList.players.first();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.inventory.clearContent();level.setWeatherParameters(12000,0,false,false);level.dayTime=6000
            for(x in 1785..1806)for(z in 140..151)for(y in 119..126)level.setBlockAndUpdate(BlockPos(x,y,z),if(y==119)Blocks.STONE.defaultBlockState()else Blocks.AIR.defaultBlockState())
            fun device(pos: BlockPos,id: String,rotation: Int=0,value: Double?=null): FaceNodeEntity {
                level.setBlockAndUpdate(pos,Content.node.get().defaultBlockState())
                return node(server,pos).also { n -> n.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { this.rotation=rotation;value?.let { this.value=it } };n.changedTopology() }
            }
            for((i,id) in listOf("mqtt_signal_controller","mqtt_energy_meter").withIndex()) {
                val pos=at.east(i*6);player.teleportTo(level,pos.x+.5,120.0,pos.z+3.0,180f,25f)
                val stack=ItemStack(Content.partItems.getValue(id).get());player.setItemInHand(InteractionHand.MAIN_HAND,stack)
                require(stack.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false))).consumesAction() && stack.isEmpty)
                node(server,pos).apply { parts[0]!!.rotation=0;changedTopology() }
            }
            device(at.east(),"signal_source",value=2.5)
            device(at.south(),"electrical_probe").let { PartInventory.get(it,it.parts[0]!!).setStackInSlot(0,ItemStack(Content.partItems.getValue("signal_cable").get())) }
            device(at.south(2),"creative_power_resistor",1,10000.0);device(at.south(3),"ground_cable")
            MqttDevices.setDesired(node(server,at).parts[0]!!,1,3.75)
            device(at.east(6).north(),"electrical_source",value=50.0);device(at.east(6).south(),"creative_power_resistor",1,100.0);device(at.east(6).south(2),"ground_cable")
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.inventory.setItem(9,ItemStack(Content.partItems.getValue("low_current_cable").get(),2));player.inventoryMenu.broadcastChanges()
        }
        if(ticks==35) { mc.options.guiScale().set(2);mc.resizeDisplay();BuildcraftSpriteAudit.verify(mc) }
        if(ticks==60)open(false)
        if(ticks==100) { require(fields()[2].value.matches(Regex("\\d{6}")));fields()[0].value="Workshop controller";fields()[1].value="qa-unused";button("gui.eln.apply");mode("A");mode("B") }
        if(ticks==135)mode("B")
        if(ticks==175) { verify { val p=node(server,at).parts[0]!!;require(p.gridState.getString("mqttName")=="Workshop controller" && p.gridState.getString("mqttServer")=="qa-unused" && p.pinVoltages[0] in 2.49..2.51 && p.pinVoltages[1] in 3.70..3.80 && abs(node(server,at.south(2)).parts[0]!!.current) in .00036.. .00039) };image("actual-controller-name-server-and-read-write-mode-packets");mode("B") }
        if(ticks==210) { verify { val p=node(server,at).parts[0]!!;require(abs(p.pinVoltages[1])<.001 && abs(node(server,at.south(2)).parts[0]!!.current)<1e-6) };image("actual-disabled-output-stops-real-signal-load");mc.player!!.closeContainer();open(true) }
        if(ticks==250)mc.gameMode!!.handleInventoryMouseClick(screen().menu.containerId,1,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,mc.player!!)
        if(ticks==290) { verify { val p=node(server,at.east(6)).parts[0]!!;require(p.enabled && p.current in .49.. .501 && EnergyMeters.counter(p)>20 && PartInventory.peek(p,0).item==Content.partItems.getValue("low_current_cable").get()) };fields()[0].value="Workshop meter";fields()[1].value="qa-unused";button("gui.eln.apply");image("actual-current-cable-quickmove-and-enabled-counter") }
        if(ticks==320)button("gui.eln.mqtt_reset")
        if(ticks==340) { verify { val p=node(server,at.east(6)).parts[0]!!;require(EnergyMeters.counter(p) in 0.0..40.0 && EnergyMeters.time(p)<90) };button("gui.eln.enabled") }
        if(ticks==370)verify { val p=node(server,at.east(6)).parts[0]!!;require(!p.enabled && abs(p.current)<1e-6);parked=node(server,at.east(6).south()).parts[0]!!.energy }
        if(ticks==405) { verify { require(abs(node(server,at.east(6).south()).parts[0]!!.energy-parked)<1e-5) };image("actual-meter-switch-stops-real-power-and-counter");button("gui.eln.disabled") }
        if(ticks==440) { verify { require(node(server,at.east(6).south()).parts[0]!!.energy>parked+20) };screen().children().filterIsInstance<Button>().first { it.message.string=="kJ" }.onPress();screen().children().filterIsInstance<Button>().first { it.message.string=="H" }.onPress() }
        if(ticks==470) { verify { val p=node(server,at.east(6)).parts[0]!!;require(p.gridState.getInt("meterEnergyUnit")==2 && p.gridState.getInt("meterTimeUnit")==1) };image("actual-mqtt-meter-name-id-mj-day-controls");mc.player!!.closeContainer();camera(1791.2,145.5,90f,55f) }
        if(ticks==510)image("original-mqtt-controller-open-housing-and-status-leds-world")
        if(ticks==520)camera(1793.5,145.5,-90f,35f)
        if(ticks==555)image("original-mqtt-meter-number-wheels-and-current-cable-world")
        if(ticks==565)verify { val p=server.playerList.players.first();p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Content.partItems.getValue("mqtt_energy_meter").get()));p.inventoryMenu.broadcastChanges() }
        if(ticks==600) { require(mc.player!!.mainHandItem.item==Content.partItems.getValue("mqtt_energy_meter").get());image("original-held-mqtt-meter-example") }
        if(ticks==620) { Eln.LOGGER.info("ELN_CLIENT_QA original MQTT devices actual survival placement, real name/server/readonly-ID and independent read-write-disable GUI packets, real signal load, current-cable quickmove, counter/reset/off-on/units and original world/held models confirmed with networking disabled by default");mc.stop() }
    }
}
