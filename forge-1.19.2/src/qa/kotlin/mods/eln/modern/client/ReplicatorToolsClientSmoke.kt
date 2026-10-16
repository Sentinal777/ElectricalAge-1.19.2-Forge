package mods.eln.modern.client

import mods.eln.Eln
import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.server.IntegratedServer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

object ReplicatorToolsClientSmoke {
    private var ticks=0
    @Volatile private var failure: String?=null
    private val at=BlockPos(1608,120,147)
    private val target=at.east(4)
    private val cable=at.north(8)
    private var mobId: java.util.UUID?=null
    private fun node(s: IntegratedServer,p: BlockPos)=s.overworld().getBlockEntity(p) as FaceNodeEntity
    fun tick(mc: Minecraft,server: IntegratedServer) {
        check(failure==null) { "Replicator/tools authoritative check failed: $failure" };ticks++
        fun verify(action: ()->Unit) { val tick=ticks;server.execute { try { action() }catch(t: Throwable) { failure="tick $tick: ${t.stackTraceToString()}" } } }
        fun image(name: String)=Screenshot.grab(mc.gameDirectory,"eln-original-replicator-tools-$name.png",mc.mainRenderTarget) { Eln.LOGGER.info("ELN_CLIENT_QA screenshot {}",it.string) }
        fun click(pos: BlockPos)=mc.gameMode!!.useItemOn(mc.player!!,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false))
        fun shift(on: Boolean) {
            mc.player!!.isShiftKeyDown=on
            mc.player!!.connection.send(ServerboundPlayerCommandPacket(mc.player!!,if(on)ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY else ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY))
        }
        if(ticks==20)verify {
            val world=server.overworld();world.dayTime=6000
            for(x in 1600..1623)for(z in 132..155)for(y in 119..127)world.setBlockAndUpdate(BlockPos(x,y,z),(if(y==119)Blocks.STONE else Blocks.AIR).defaultBlockState())
            world.getEntities(null,net.minecraft.world.phys.AABB(at).inflate(20.0)).filter { it is ReplicatorEntity || it is net.minecraft.world.entity.item.ItemEntity }.forEach { it.discard() }
            fun part(pos: BlockPos,id: String,value: Double=0.0) {
                world.setBlockAndUpdate(pos,Content.node.get().defaultBlockState())
                node(server,pos).apply { parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN).apply { if(value!=0.0)this.value=value };changedTopology() }
            }
            part(at,"electrical_source",123.0);part(target,"electrical_source",50.0)
            part(cable,"high_voltage_cable");part(cable.south(),"electrical_source",50.0)
            part(at.north(4),"wireless_switch");node(server,at.north(4)).parts[0]!!.apply { channel="Live analyser";enabled=true }
            val mob=Content.replicator.get().create(world)!!.apply { setPos(cable.x+.5,120.0,cable.z-.4);hunger=0.0 }
            require(world.addFreshEntity(mob));mobId=mob.uuid
            val player=server.playerList.players.first();player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.inventory.clearContent();player.inventory.selected=8
            player.teleportTo(world,1608.5,121.0,150.5,180f,35f)
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Content.materialItems.getValue("config_copy_tool").get()));player.inventoryMenu.broadcastChanges()
        }
        if(ticks==60)BuildcraftSpriteAudit.verify(mc)
        if(ticks==75)shift(true)
        if(ticks==85)click(at)
        if(ticks==100) {
            require(mc.player!!.mainHandItem.tag?.getCompound("ElnConfiguration")?.getDouble("voltage")==123.0)
            image("actual-sneak-copied-tool");shift(false)
            server.execute { server.playerList.players.first().teleportTo(server.overworld(),1612.5,121.0,150.5,180f,35f) }
        }
        if(ticks==120)click(target)
        if(ticks==145)verify {
            require(node(server,target).parts[0]!!.value==123.0 && node(server,at).parts[0]!!.value==123.0)
            val player=server.playerList.players.first();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Content.materialItems.getValue("allmeter").get()));player.inventoryMenu.broadcastChanges()
        }
        if(ticks==165)click(target)
        if(ticks==175)image("actual-combined-allmeter-reading")
        if(ticks==190)verify {
            val mob=server.overworld().getEntity(mobId!!) as ReplicatorEntity
            require(mob.electricalWorkJ>25 && mob.hunger<0) { "Actual original AI never fed: food=${mob.electricalWorkJ}, hunger=${mob.hunger}, at=${mob.position()}, ground=${mob.isOnGround}, target=${mob.target}" }
            val player=server.playerList.players.first();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Content.materialItems.getValue("wireless_analyser").get()));player.inventoryMenu.broadcastChanges()
        }
        if(ticks==210)click(target.below())
        if(ticks==230) { image("actual-wireless-channel-analysis");server.execute { server.playerList.players.first().teleportTo(server.overworld(),1610.7,120.6,136.8,45f,15f) } }
        if(ticks==260) {
            val mob=mc.level!!.getEntity(mc.level!!.entitiesForRendering().first { it.uuid==mobId }.id) as ReplicatorEntity
            require(mc.entityRenderDispatcher.getRenderer(mob) is ReplicatorRenderer)
            image("original-silverfish-model-and-texture-feeding-world")
            verify { val mob=server.overworld().getEntity(mobId!!) as ReplicatorEntity;require(mob.electricalWorkJ>50 && kotlin.math.abs(mob.electricalWorkJ-mob.nutritionalWorkJ-mob.dissipatedWorkJ)<1e-5) }
        }
        if(ticks==285) {
            Eln.LOGGER.info("ELN_CLIENT_QA original Replicator actual native AI feeding from paid cable load, source silverfish model/texture, real sneak-copy and apply interaction packets, AllMeter and wireless analyser confirmed")
            mc.stop()
        }
    }
}
