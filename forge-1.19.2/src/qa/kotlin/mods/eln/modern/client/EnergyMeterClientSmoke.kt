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
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object EnergyMeterClientSmoke {
    private var ticks=0
    @Volatile private var failure: String?=null
    private val at=BlockPos(1690,120,145)
    private var parked=0.0
    private fun node(s: IntegratedServer,pos: BlockPos)=s.overworld().getBlockEntity(pos) as FaceNodeEntity
    private fun meter(s: IntegratedServer,advanced: Boolean)=node(s,if(advanced)at.east(5)else at)
    fun tick(mc: Minecraft,server: IntegratedServer) {
        check(failure==null) { "Energy meter authoritative check failed: $failure" };ticks++
        fun verify(action: ()->Unit) { val tick=ticks;server.execute { try { action() }catch(t: Throwable) { failure="tick $tick: ${t.stackTraceToString()}" } } }
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,"eln-original-energy-meter-$name.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun screen()=mc.screen as EnergyMeterScreen
        fun fields()=screen().children().filterIsInstance<EditBox>()
        fun button(text: String)=screen().children().filterIsInstance<Button>().first { it.message.string==text }.onPress()
        fun open(advanced: Boolean)=server.execute {
            val n=meter(server,advanced);val player=server.playerList.players.first();player.teleportTo(server.overworld(),n.blockPos.x+.5,120.0,148.5,180f,15f);n.sync()
            net.minecraftforge.network.NetworkHooks.openScreen(player,net.minecraft.world.SimpleMenuProvider({ id,inventory,_ -> DeviceMenu(id,inventory,n,0) },net.minecraft.network.chat.Component.translatable(n.parts[0]!!.definition.translationKey))) { it.writeBlockPos(n.blockPos);it.writeByte(0) }
        }
        fun camera(x: Double,z: Double,yaw: Float)=server.execute { server.playerList.players.first().teleportTo(server.overworld(),x,120.0,z,yaw,20f) }
        if(ticks==20)verify {
            val world=server.overworld();world.dayTime=6000;world.setWeatherParameters(12000,0,false,false)
            for(x in 1683..1704)for(z in 138..153)for(y in 119..127)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==119)Blocks.STONE else Blocks.AIR).defaultBlockState())
            val player=server.playerList.players.first();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.inventory.clearContent();player.inventory.selected=8
            player.abilities.mayfly=true;player.abilities.flying=true;player.onUpdateAbilities();player.teleportTo(world,1690.5,120.0,148.5,0f,15f)
            fun device(pos: BlockPos,id: String): FaceNodeEntity {
                world.setBlockAndUpdate(pos,Content.node.get().defaultBlockState())
                return node(server,pos).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN);it.changedTopology() }
            }
            for((i,id) in listOf("energy_meter","advanced_energy_meter").withIndex()) {
                val pos=at.east(i*5);val stack=ItemStack(Content.partItems.getValue(id).get());player.setItemInHand(InteractionHand.MAIN_HAND,stack)
                require(stack.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false))).consumesAction() && stack.isEmpty)
                meter(server,i==1).apply { parts[0]!!.rotation=0;changedTopology() }
                device(pos.north(),"electrical_source").parts[0]!!.value=50.0
                device(pos.south(),"creative_power_resistor").apply { parts[0]!!.apply { rotation=1;value=100.0 };changedTopology() }
                device(pos.south(2),"ground_cable")
            }
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.inventory.setItem(9,ItemStack(Content.partItems.getValue("low_voltage_cable").get(),2));player.inventoryMenu.broadcastChanges()
        }
        if(ticks==35) { mc.options.guiScale().set(1);mc.resizeDisplay() }
        if(ticks==60) { BuildcraftSpriteAudit.verify(mc);open(false) }
        if(ticks==100)mc.gameMode!!.handleInventoryMouseClick(screen().menu.containerId,1,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,mc.player!!)
        if(ticks==130) { require(screen().menu.slots[0].item.count==1);button("Off") }
        if(ticks==165) {
            verify { val p=meter(server,false).parts[0]!!;require(p.enabled && EnergyMeters.counter(p)>20 && p.current in .49.. .501) }
            image("actual-basic-fitted-cable-switch-and-signed-counter-gui");mc.player!!.closeContainer()
        }
        if(ticks==185)open(true)
        if(ticks==220)mc.gameMode!!.handleInventoryMouseClick(screen().menu.containerId,1,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,mc.player!!)
        if(ticks==250)button("Off")
        if(ticks==280)button("Counter")
        if(ticks==290) { fields()[1].value="0.005";button("Set counter (kJ)") }
        if(ticks==325)verify {
            val n=meter(server,true);val p=n.parts[0]!!;require(EnergyMeters.prepaid(p) && EnergyMeters.counter(p) in -2.0..0.0)
            parked=node(server,n.blockPos.south()).parts[0]!!.energy
        }
        if(ticks==350) {
            verify { val n=meter(server,true);require(abs(node(server,n.blockPos.south()).parts[0]!!.energy-parked)<1e-5) }
            image("actual-exhausted-prepay-stops-real-power-gui")
        }
        if(ticks==360) { fields()[1].value="20";button("Set counter (kJ)") }
        if(ticks==400) { verify { val n=meter(server,true);require(node(server,n.blockPos.south()).parts[0]!!.energy>parked+20) };button("kJ");button("H") }
        if(ticks==415) { fields()[0].value="native-password";button("Set password") }
        if(ticks==440) {
            verify { val p=meter(server,true).parts[0]!!;require(EnergyMeterControls.authenticate(p,"native-password") && !EnergyMeterControls.authenticate(p,"wrong") && p.gridState.getInt("meterEnergyUnit")==2 && p.gridState.getInt("meterTimeUnit")==1) }
            image("actual-refill-mj-day-wheels-and-password-gui");mc.player!!.closeContainer()
        }
        if(ticks==455)open(true)
        if(ticks==480) {
            require(!screen().children().filterIsInstance<Button>().first { it.message.string=="On" }.active)
            fields()[0].value="wrong";button("Unlock")
        }
        if(ticks==500) { require(!screen().children().filterIsInstance<Button>().first { it.message.string=="On" }.active);fields()[0].value="native-password";button("Unlock") }
        if(ticks==530) { require(screen().children().filterIsInstance<Button>().first { it.message.string=="On" }.active);image("actual-password-unlock-controls-gui");button("On") }
        if(ticks==565) { verify { require(!meter(server,true).parts[0]!!.enabled) };mc.player!!.closeContainer();camera(1686.5,145.5,-90f) }
        if(ticks==590)image("original-basic-counter-wheels-west")
        if(ticks==600)camera(1693.5,145.5,90f)
        if(ticks==625)image("original-basic-counter-wheels-east")
        if(ticks==635)camera(1690.5,148.5,180f)
        if(ticks==660)image("original-basic-counter-wheels-south")
        if(ticks==670)camera(1690.5,141.5,0f)
        if(ticks==695)image("original-basic-counter-wheels-north")
        if(ticks==705)camera(1691.5,145.5,-90f)
        if(ticks==730)image("original-advanced-energy-time-wheels-world")
        if(ticks==740)server.execute { val player=server.playerList.players.first();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Content.partItems.getValue("advanced_energy_meter").get()));player.inventoryMenu.broadcastChanges() }
        if(ticks==765)image("original-held-advanced-meter-example-wheels")
        if(ticks==790) {
            Eln.LOGGER.info("ELN_CLIENT_QA original energy meters actual survival placement and cable quick-move, real conduction and signed counters, prepay five-joule cutoff and refill, MJ/day controls, password set/reopen/reject/unlock and shutdown packets, original number-wheel meshes and held model confirmed")
            mc.stop()
        }
    }
}
