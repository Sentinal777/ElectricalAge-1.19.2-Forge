package mods.eln.modern

import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Material
import java.util.PriorityQueue
import java.util.UUID

data class ElectricalToolSpec(val type: String,val strengthOn: Float,val strengthOff: Float,val energyPerBlock: Double)

/** Original powered area drilling and gradual tree traversal. All extra breaks use Forge's player path. */
object PortableTools {
    private val excludedUntil=hashMapOf<Item,Int>()
    private val expanding=hashSetOf<UUID>()
    private val trees=mutableListOf<TreeJob>()
    fun spec(stack: ItemStack)=ElectricalItems.spec(stack)?.tool
    fun effective(tool: ElectricalToolSpec,state: BlockState): Boolean {
        val material=state.material
        return if(tool.type=="AXE")material==Material.WOOD || material==Material.NETHER_WOOD || material==Material.PLANT || material==Material.REPLACEABLE_PLANT || state.`is`(BlockTags.MINEABLE_WITH_AXE)
        else material==Material.METAL || material==Material.GLASS || material==Material.HEAVY_METAL || material==Material.STONE ||
            state.`is`(BlockTags.MINEABLE_WITH_PICKAXE) || state.`is`(BlockTags.MINEABLE_WITH_SHOVEL)
    }
    fun speed(stack: ItemStack,state: BlockState): Float {
        val tool=spec(stack) ?: return 1f
        return if(effective(tool,state))if(ElectricalItems.energy(stack)>=tool.energyPerBlock)tool.strengthOn else tool.strengthOff else 1f
    }
    fun capitation(stack: ItemStack)=!stack.orCreateTag.contains("capitation") || stack.orCreateTag.getBoolean("capitation")
    fun toggle(stack: ItemStack): String {
        val tool=spec(stack) ?: return ""
        if(tool.type=="AXE") {
            val active=!capitation(stack);stack.orCreateTag.putBoolean("capitation",active)
            return if(active)"message.eln.tree_felling_on" else "message.eln.tree_felling_off"
        }
        val conservative=!stack.orCreateTag.getBoolean("conservative");stack.orCreateTag.putBoolean("conservative",conservative)
        return if(conservative)"message.eln.drill_single" else "message.eln.drill_area"
    }
    fun swing(stack: ItemStack,entity: LivingEntity) {
        if(spec(stack)==null || entity.level.isClientSide)return
        entity.server?.let { excludedUntil[stack.item]=it.tickCount+40 }
    }
    fun excluded(stack: ItemStack,tick: Int)=(excludedUntil[stack.item] ?: Int.MIN_VALUE)>tick
    fun mined(stack: ItemStack,level: Level,state: BlockState,pos: BlockPos,entity: LivingEntity): Boolean {
        val tool=spec(stack) ?: return false
        if(level !is ServerLevel)return true
        if(effective(tool,state) && ElectricalItems.energy(stack)>=tool.energyPerBlock)ElectricalItems.setEnergy(stack,ElectricalItems.energy(stack)-tool.energyPerBlock)
        val player=entity as? ServerPlayer ?: return true
        if(player.uuid in expanding)return true
        if(tool.type=="DRILL" && !stack.orCreateTag.getBoolean("conservative")) {
            expanding.add(player.uuid)
            try {
                for(dx in -1..1)for(dy in -1..0)for(dz in -1..1)if(dx!=0 || dy!=0 || dz!=0)breakExtra(player,stack,pos.offset(dx,dy,dz))
            } finally { expanding.remove(player.uuid) }
        } else if(tool.type=="AXE" && capitation(stack))trees.add(TreeJob(level,player,stack,pos.immutable()))
        return true
    }
    private fun breakExtra(player: ServerPlayer,stack: ItemStack,pos: BlockPos): Boolean {
        val level=player.getLevel();val tool=spec(stack) ?: return false
        if(level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos) || player.mainHandItem!==stack || !level.mayInteract(player,pos))return false
        val state=level.getBlockState(pos)
        if(state.isAir || state.getDestroySpeed(level,pos)<0 || state.getDestroyProgress(player,level,pos)<=0 || !effective(tool,state))return false
        if(!player.isCreative && (ElectricalItems.energy(stack)<tool.energyPerBlock || !player.hasCorrectToolForDrops(state)))return false
        return player.gameMode.destroyBlock(pos)
    }
    fun tick(server: MinecraftServer) {
        excludedUntil.entries.removeIf { it.value<=server.tickCount }
        val iterator=trees.iterator()
        while(iterator.hasNext())if(!iterator.next().tick())iterator.remove()
    }
    fun clear() { excludedUntil.clear();expanding.clear();trees.clear() }
    private data class Candidate(val pos: BlockPos,val range: Int)
    private class TreeJob(val level: ServerLevel,val player: ServerPlayer,val stack: ItemStack,origin: BlockPos) {
        val candidates=PriorityQueue<Candidate>(compareByDescending { it.range })
        val best=hashMapOf<BlockPos,Int>()
        val completed=hashSetOf<BlockPos>()
        init { completed.add(origin);adjacent(origin,32) }
        private fun adjacent(pos: BlockPos,range: Int) {
            for(dx in -1..1)for(dy in -1..1)for(dz in -1..1) {
                if(dx==0 && dy==0 && dz==0)continue
                val next=pos.offset(dx,dy,dz)
                if(next in completed || level.isOutsideBuildHeight(next) || !level.hasChunkAt(next))continue
                val state=level.getBlockState(next)
                val leaf=state.`is`(BlockTags.LEAVES)
                if(!leaf && !state.`is`(BlockTags.LOGS))continue
                val remaining=if(leaf)minOf(3,range-1)else range-1
                if(remaining<=0 || remaining<=(best[next] ?: 0))continue
                best[next]=remaining;candidates.offer(Candidate(next,remaining))
            }
        }
        fun tick(): Boolean {
            if(player.isRemoved || !player.isAlive || player.getLevel()!==level || player.mainHandItem!==stack || !capitation(stack))return false
            if(!player.isCreative && ElectricalItems.energy(stack)<(spec(stack)?.energyPerBlock ?: return false))return false
            var remaining=10
            expanding.add(player.uuid)
            try {
                while(remaining>0 && candidates.isNotEmpty()) {
                    val next=candidates.poll()
                    if(next.pos in completed || next.range<(best[next.pos] ?: 0))continue
                    completed.add(next.pos);remaining--
                    // Upstream traverses leaves without harvesting them: its axe does not spend energy on leaves.
                    if(level.hasChunkAt(next.pos) && level.getBlockState(next.pos).`is`(BlockTags.LEAVES))adjacent(next.pos,next.range)
                    else if(breakExtra(player,stack,next.pos))adjacent(next.pos,next.range)
                }
            } finally { expanding.remove(player.uuid) }
            return candidates.isNotEmpty()
        }
    }
}
