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
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.animal.Cow
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object TurretClientSmoke {
    private var ticks=0
    @Volatile private var failure: String?=null
    private val at=BlockPos(1570,120,145)
    private var cowId: java.util.UUID?=null
    private var sparedShots=0
    private var photographedShot=false
    private fun node(s: IntegratedServer)=s.overworld().getBlockEntity(at) as FaceNodeEntity
    private fun part(s: IntegratedServer)=node(s).parts[0]!!
    private fun source(s: IntegratedServer): FaceNodeEntity=(s.overworld().getBlockEntity(at.relative(FaceBasis.edges(part(s))[1])) as FaceNodeEntity)
    fun tick(mc: Minecraft,server: IntegratedServer) {
        check(failure==null) { "Turret authoritative check failed: $failure" };ticks++
        fun verify(action: ()->Unit) { val tick=ticks;server.execute { try { action() }catch(t: Throwable) { failure="tick $tick: ${t.stackTraceToString()}" } } }
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,"eln-original-turret-$name.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun screen()=mc.screen as DeviceScreen
        fun field()=screen().children().filterIsInstance<EditBox>().single()
        fun button(text: String)=screen().children().filterIsInstance<Button>().first { it.message.string==text }.onPress()
        fun open()=server.execute {
            val n=node(server);val p=part(server);val player=server.playerList.players.first();val back=FaceBasis.edges(p)[1];n.sync()
            player.teleportTo(server.overworld(),at.x+.5+back.stepX*3,121.0,at.z+.5+back.stepZ*3,back.opposite.toYRot(),15f)
            net.minecraftforge.network.NetworkHooks.openScreen(player,net.minecraft.world.SimpleMenuProvider({ id,inventory,_ -> DeviceMenu(id,inventory,n,0) },net.minecraft.network.chat.Component.translatable(p.definition.translationKey))) { it.writeBlockPos(at);it.writeByte(0) }
        }
        if(ticks==20)verify {
            val world=server.overworld();world.dayTime=6000
            for(x in 1560..1583)for(z in 132..156)for(y in 119..129)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==119)Blocks.STONE else Blocks.AIR).defaultBlockState())
            world.getEntities(null,net.minecraft.world.phys.AABB(at).inflate(16.0)).filter { it is Cow || it is net.minecraft.world.entity.item.ItemEntity }.forEach { it.discard() }
            val player=server.playerList.players.first();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.inventory.clearContent();player.inventory.selected=8
            player.abilities.mayfly=true;player.abilities.flying=true;player.onUpdateAbilities();player.teleportTo(world,1570.5,121.0,149.0,180f,15f)
            val held=ItemStack(Content.partItems.getValue("800v_defence_turret").get());player.setItemInHand(InteractionHand.MAIN_HAND,held)
            require(held.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(at.below()),Direction.UP,at.below(),false))).consumesAction() && held.isEmpty)
            val p=part(server);val power=at.relative(FaceBasis.edges(p)[1]);world.setBlockAndUpdate(power,Content.node.get().defaultBlockState())
            source(server).apply { parts[0]=PartState(PartDefinition.byId.getValue("electrical_source"),Direction.DOWN).apply { value=800.0 };changedTopology() }
            val target=at.relative(FaceBasis.edges(p)[3],6)
            val cow=EntityType.COW.create(world)!!.apply {
                setNoAi(true);setNoGravity(true);getAttribute(Attributes.MAX_HEALTH)!!.baseValue=100.0;health=100f
                setPos(target.x+.5,120.0,target.z+.5);deltaMovement=Vec3.ZERO
            }
            require(world.addFreshEntity(cow));cowId=cow.uuid
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.inventory.setItem(9,ItemStack(Content.materialItems.getValue("animal_filter").get()));player.inventoryMenu.broadcastChanges()
        }
        if(ticks==65) { BuildcraftSpriteAudit.verify(mc);open() }
        if(ticks==100)mc.gameMode!!.handleInventoryMouseClick(screen().menu.containerId,1,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,mc.player!!)
        if(ticks==140) { require(screen().menu.slots[0].item.item===Content.materialItems.getValue("animal_filter").get());field().value="500";button("Apply") }
        if(ticks==220) { require(screen().menu.part!!.value==500.0 && screen().menu.part!!.gridState.getInt("turretShots")>0);image("actual-filter-500w-paid-shot-gui");mc.player!!.closeContainer();server.execute { server.playerList.players.first().teleportTo(server.overworld(),1573.5,121.5,142.5,45f,16f) } }
        if(ticks in 240..295 && !photographedShot) {
            val p=(mc.level!!.getBlockEntity(at) as FaceNodeEntity).parts[0]!!
            if(p.gridState.getInt("turretShots")>=2) { photographedShot=true;image("original-aimed-guns-filter-sensor-world") }
        }
        if(ticks==300)open()
        if(ticks==330)button("Attack:")
        if(ticks==350)verify { val p=part(server);require(p.enabled);sparedShots=p.gridState.getInt("turretShots") }
        if(ticks==380) {
            verify { val p=part(server);require(p.gridState.getInt("turretShots")==sparedShots);val cow=server.overworld().getEntity(cowId!!) as Cow;require(cow.health==100f-5f*p.gridState.getInt("turretHits")) }
            require(screen().menu.part!!.enabled);image("actual-do-not-attack-animal-filter-gui");field().value="2000";button("Apply")
        }
        if(ticks==420) { require(screen().menu.part!!.value==2000.0);button("Do not attack:") }
        if(ticks==515) {
            verify {
                val p=part(server);require(!p.enabled && p.gridState.getInt("turretShots")>sparedShots)
                val accounted=p.progress+p.gridState.getDouble("turretShotJ")+p.gridState.getDouble("turretDissipatedJ")
                require(abs(p.energy-accounted)<1e-4 && p.gridState.getDouble("turretShotJ")==p.gridState.getInt("turretShots")*1000.0)
                source(server).parts[0]!!.enabled=false;source(server).changedTopology()
            };image("actual-2kw-recharge-and-shot-counter-gui")
        }
        if(ticks==550) { require(!screen().menu.part!!.gridState.getBoolean("turretArmed"));image("actual-power-loss-disarmed-gui");mc.player!!.closeContainer();server.execute { val player=server.playerList.players.first();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Content.partItems.getValue("800v_defence_turret").get()));player.inventoryMenu.broadcastChanges() } }
        if(ticks==585) {
            require(photographedShot);image("original-held-mesh")
            val model=mc.itemRenderer.getModel(ItemStack(Content.partItems.getValue("800v_defence_turret").get()),mc.level,mc.player,0)
            require(!model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            require(model.applyTransform(net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,com.mojang.blaze3d.vertex.PoseStack(),false).isCustomRenderer)
            Eln.LOGGER.info("ELN_CLIENT_QA original 800V turret actual survival placement, rear power and real quick-move filter, 500/2000 W Apply and attack/spare packets, actual cow damage, strictly paid 1 kJ shots, disarming on outage, original aiming/gun/sensor artwork and held/sprite routes confirmed")
        }
        if(ticks==610)mc.stop()
    }
}
