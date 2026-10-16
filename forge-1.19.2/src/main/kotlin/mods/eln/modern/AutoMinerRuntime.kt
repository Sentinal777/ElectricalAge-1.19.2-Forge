package mods.eln.modern

import com.mojang.authlib.GameProfile
import mods.eln.config.ForgePortConfig
import mods.eln.modern.grid.GridGhostEntity
import mods.eln.modern.grid.GridMultiblock
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.TagKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.Registry
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.common.ForgeHooks
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.ItemHandlerHelper
import java.util.UUID
import kotlin.math.*

/** Source geometry and finite original mining pipes, shared by placement, cleanup and rendering. */
object AutoMiners {
    const val DEPTH="autoMinerDepth"
    fun depth(p: PartState)=p.gridState.getInt(DEPTH).coerceIn(0,4096)
    fun shaft(node: FaceNodeEntity,p: PartState)=List(depth(p)) { node.blockPos.below(it+1) }
    fun isPipe(node: FaceNodeEntity,p: PartState,at: BlockPos)=at.x==node.blockPos.x && at.z==node.blockPos.z && at.y in node.blockPos.y-depth(p) until node.blockPos.y
    fun releasePipes(node: FaceNodeEntity,p: PartState) {
        val world=node.level as? ServerLevel ?: return
        val count=depth(p);if(count==0)return
        // Clear ownership before a delayed callback can try to refund the same pipe again.
        val cells=shaft(node,p);p.gridState.putInt(DEPTH,0)
        for(at in cells)GridMultiblock.removeOwnedCell(node,p,at)
        var remaining=count
        while(remaining>0) { val n=minOf(64,remaining);Block.popResource(world,node.blockPos,ItemStack(Content.materialItems.getValue("mining_pipe").get(),n));remaining-=n }
        node.changedTopology()
    }
    fun target(node: FaceNodeEntity,p: PartState)=BlockPos.of(p.gridState.getLong("autoMinerTarget"))
    fun job(p: PartState)=p.gridState.getString("autoMinerJob").ifBlank { "none" }
    fun output(node: FaceNodeEntity,p: PartState)=GridMultiblock.offset(node.blockPos,p,listOf(1,-1,0))
    fun pending(p: PartState)=p.gridState.getList("autoMinerDrops",10).map { ItemStack.of(it as CompoundTag) }.filter { !it.isEmpty }
    fun pending(p: PartState,stacks: List<ItemStack>) { p.gridState.put("autoMinerDrops",ListTag().also { list -> stacks.filter { !it.isEmpty }.forEach { list.add(it.save(CompoundTag())) } }) }
}

/** Original 800 V drill, depth-dependent scan and 50 J / second pipe movement. */
class AutoMinerRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private val node=e.node
    private val world=e.circuit.level
    private val inventory=PartInventory.get(node,p)
    val load=ElectricalLoad().apply { serialResistance=PartDefinition.byId.getValue("high_voltage_cable").resistance;state=p.voltage }
    private val resistor=Resistor(load,null).setResistance(1e9)
    private val ores=TagKey.create(Registry.BLOCK_REGISTRY,ResourceLocation("forge","ores"))
    init { e.circuit.root.addState(load);e.circuit.root.addComponent(resistor);for(i in 0..3)e.pins[i]=load }
    private fun drill()=(inventory.getStackInSlot(0).item as? MaterialItem)?.definition?.drill
    private fun silk()=p.value>=.5
    private fun sink(): IItemHandler? {
        val at=AutoMiners.output(node,p)
        val chunk=world.chunkSource.getChunkNow(at.x shr 4,at.z shr 4) ?: return null
        return chunk.getBlockEntity(at)?.getCapability(ForgeCapabilities.ITEM_HANDLER)?.orElse(null)
    }
    private fun flush() {
        val sink=sink() ?: return
        AutoMiners.pending(p,AutoMiners.pending(p).map { ItemHandlerHelper.insertItemStacked(sink,it,false) })
    }
    private fun storageReady(): Boolean {
        if(AutoMiners.pending(p).isNotEmpty())return false
        val sink=sink() ?: return false
        // The source requires an empty output slot, including a real double chest when present.
        return (0 until sink.slots).any { sink.getStackInSlot(it).isEmpty }
    }
    private fun state(at: BlockPos): BlockState? {
        if(world.isOutsideBuildHeight(at) || !world.worldBorder.isWithinBounds(at))return null
        return world.chunkSource.getChunkNow(at.x shr 4,at.z shr 4)?.getBlockState(at)
    }
    private fun minable(at: BlockPos,s: BlockState)=!s.isAir && s.fluidState.isEmpty && s.block!=Blocks.OBSIDIAN && s.getDestroySpeed(world,at)>=0 && s.block!=Content.gridGhost.get()
    private fun choose() {
        val depth=AutoMiners.depth(p);val bottom=node.blockPos.below(depth)
        val previous=AutoMiners.job(p);val previousTarget=AutoMiners.target(node,p)
        if(previous=="ore" && p.gridState.getBoolean("autoMinerPowerOk") && drill()!=null && storageReady() &&
            p.gridState.getString("autoMinerFingerprint")==inventory.getStackInSlot(0).descriptionId+":"+silk() &&
            state(previousTarget)?.let { minable(previousTarget,it) }==true)return
        var job="none";var at=bottom
        if(!p.gridState.getBoolean("autoMinerPowerOk"))Unit
        else if(drill()==null) {
            if(depth>0)job=if(inventory.getStackInSlot(2).count<64)"pipeRemove" else "full"
        } else if(!storageReady())job="chestFull"
        else if((inventory.getStackInSlot(2).item as? MaterialItem)?.definition?.kind=="MINING_PIPE") {
            if(depth>2) {
                val radius=minOf(ForgePortConfig.autoMinerRange.get(),depth+1)
                val miningRadius=minOf(depth/10.0+.1,2.0,(radius-2).toDouble())
                search@for(z in -radius..radius)for(x in -radius..radius) {
                    val candidate=bottom.offset(x,0,z);val s=state(candidate) ?: continue
                    val distance=sqrt((x*x+z*z).toDouble())
                    if(minable(candidate,s) && (s.`is`(ores) || distance>.1 && distance<miningRadius)) { job="ore";at=candidate;break@search }
                }
            }
            if(job=="none") {
                at=bottom.below()
                // Adapt the original y=3 floor to dimensions with a different minimum height.
                job=if(bottom.y<world.minBuildHeight+3)"done" else state(at)?.let { s ->
                    if(s.isAir || !s.fluidState.isEmpty)"pipeAdd" else if(minable(at,s))"ore" else "done"
                } ?: "unloaded"
            }
        }
        val drill=drill();val target=when(job) { "ore" -> (drill?.operationEnergy ?: 0.0)*if(silk())6 else 1;"pipeAdd","pipeRemove" -> 50.0;else -> 0.0 }
        val fingerprint=if(job=="ore")inventory.getStackInSlot(0).descriptionId+":"+silk() else job
        if(job!=AutoMiners.job(p) || p.gridState.getLong("autoMinerTarget")!=at.asLong() || p.gridState.getString("autoMinerFingerprint")!=fingerprint) {
            p.gridState.putDouble("autoMinerDiscardedJ",p.gridState.getDouble("autoMinerDiscardedJ")+p.progress);p.progress=0.0
            p.gridState.putString("autoMinerJob",job);p.gridState.putLong("autoMinerTarget",at.asLong());p.gridState.putString("autoMinerFingerprint",fingerprint)
            val logs=p.gridState.getList("autoMinerLog",8)
            val number=when(job) { "ore" -> p.gridState.getInt("autoMinerDrilled")+1;"pipeAdd" -> depth+1;"pipeRemove" -> depth;else -> 0 }
            logs.add(0,net.minecraft.nbt.StringTag.valueOf("$job:$number"));while(logs.size>9)logs.removeAt(logs.size-1);p.gridState.put("autoMinerLog",logs)
            node.sync()
        }
        p.gridState.putDouble("autoMinerTargetJ",target)
    }
    fun beforeTick() {
        flush()
        val blink=(p.gridState.getInt("autoMinerBlink")+1)%9;p.gridState.putInt("autoMinerBlink",blink)
        if(blink==0) {
            val powered=(load.voltage/800-.5)*3>world.random.nextDouble();p.gridState.putBoolean("autoMinerPowerOk",powered)
            if(powered)mods.eln.modern.lighting.LightSpotBlock.illuminate(world,GridMultiblock.offset(node.blockPos,p,listOf(-3,0,0)),12,11)
        }
        choose()
        val drill=drill();val watts=when(AutoMiners.job(p)) { "ore" -> if(drill==null)0.0 else drill.operationEnergy/drill.operationTime*if(silk())3 else 1;"pipeAdd","pipeRemove" -> 50.0;else -> 0.0 }
        resistor.resistance=if(watts>0)800.0*800/watts else 1e9
    }
    fun afterStep(dt: Double) {
        p.voltage=load.voltage;p.voltageB=0.0;p.current=load.current
        val work=maxOf(0.0,resistor.power)*dt;p.energy+=work
        if(resistor.resistance<1e9)p.progress+=work else p.gridState.putDouble("autoMinerIdleLossJ",p.gridState.getDouble("autoMinerIdleLossJ")+work)
        p.active=resistor.resistance<1e9 && resistor.power>1;p.powerFactor=if(p.active)1.0 else 0.0
        val target=p.gridState.getDouble("autoMinerTargetJ");p.processFraction=if(target>0)(p.progress/target).coerceIn(0.0,1.0)else 0.0
    }
    private fun harvest(at: BlockPos): Boolean {
        val s=state(at) ?: return false
        if(!minable(at,s) || !storageReady())return false
        val id=runCatching { UUID.fromString(p.gridState.getString("owner")) }.getOrElse { UUID.nameUUIDFromBytes(("eln:"+p.gridId).toByteArray(Charsets.UTF_8)) }
        val player=FakePlayerFactory.get(world,GameProfile(id,"[ELN Auto Miner]"))
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL)
        player.setPos(node.blockPos.x+.5,node.blockPos.y+.5,node.blockPos.z+.5)
        val tool=ItemStack(Items.DIAMOND_PICKAXE);if(silk())tool.enchant(Enchantments.SILK_TOUCH,1)
        player.setItemInHand(InteractionHand.MAIN_HAND,tool)
        if(!world.mayInteract(player,at) || ForgeHooks.onBlockBreakEvent(world,player.gameMode.gameModeForPlayer,player,at)<0)return false
        if(state(at)!=s)return false
        val drops=Block.getDrops(s,world,at,world.getBlockEntity(at),player,tool)
        val dx=at.x-node.blockPos.x;val dz=at.z-node.blockPos.z
        if(!world.setBlock(at,(if(dx*dx+dz*dz>25)Blocks.COBBLESTONE else Blocks.AIR).defaultBlockState(),3))return false
        AutoMiners.pending(p,drops);flush();p.gridState.putInt("autoMinerDrilled",p.gridState.getInt("autoMinerDrilled")+1)
        return true
    }
    fun afterTick() {
        val target=p.gridState.getDouble("autoMinerTargetJ");val at=AutoMiners.target(node,p);val job=AutoMiners.job(p)
        if(target>0 && p.progress>=target) {
            val completed=when(job) {
                "ore" -> harvest(at)
                "pipeAdd" -> {
                    val s=state(at);val pipe=inventory.getStackInSlot(2)
                    if(s!=null && (s.isAir || !s.fluidState.isEmpty) && !pipe.isEmpty) {
                        p.gridState.putInt(AutoMiners.DEPTH,AutoMiners.depth(p)+1);GridMultiblock.install(node,p)
                        val ghost=world.getBlockEntity(at) as? GridGhostEntity
                        if(ghost!=null && ghost.owner==node.blockPos && ghost.ownerId==p.gridId) { inventory.extractItem(2,1,false);true }
                        else { p.gridState.putInt(AutoMiners.DEPTH,AutoMiners.depth(p)-1);false }
                    } else false
                }
                "pipeRemove" -> {
                    if(inventory.getStackInSlot(2).count<64 && AutoMiners.depth(p)>0) {
                        GridMultiblock.removeOwnedCell(node,p,at);p.gridState.putInt(AutoMiners.DEPTH,AutoMiners.depth(p)-1)
                        inventory.insertItem(2,ItemStack(Content.materialItems.getValue("mining_pipe").get()),false);true
                    } else false
                }
                else -> false
            }
            if(completed) {
                p.progress-=target;p.gridState.putDouble("autoMinerSpentJ",p.gridState.getDouble("autoMinerSpentJ")+target)
                choose();node.setChanged();node.sync()
            }
        }
        var excess=abs(p.voltage)-1040;val outside=excess>0
        if(outside && !p.wasOvervoltage)excess=0.0;p.wasOvervoltage=outside
        val timeout=minOf(200.0,(if(p.gridState.contains("autoMinerVoltageTimeout"))p.gridState.getDouble("autoMinerVoltageTimeout")else 200.0)-.05*excess*(.5+world.random.nextDouble()))
        p.gridState.putDouble("autoMinerVoltageTimeout",timeout)
        if(timeout<0 && ForgePortConfig.cableVoltageProtection.get())MachineFailure.destroy(node,p,3f)
    }
}
