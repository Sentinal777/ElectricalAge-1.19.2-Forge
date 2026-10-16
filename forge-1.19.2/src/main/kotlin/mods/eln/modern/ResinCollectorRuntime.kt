package mods.eln.modern

import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import kotlin.math.min

/** Original two-item pot, tree-height/leaf check and sharing between pots on the same trunk side. */
class ResinCollectorRuntime(private val e: ElementRuntime) {
    fun afterTick() {
        val p=e.part
        p.thermalState.putDouble("resinElapsed",(elapsed(p)+.05).coerceAtMost(1e12))
        if(p.timer++%20==0) {
            val rate=rate(e.node,p);p.thermalState.putDouble("resinRate",rate)
            p.thermalState.putDouble("resinStock",rate*elapsed(p));p.processFraction=(rate*elapsed(p)).coerceIn(0.0,1.0)
        }
    }
    companion object {
        private fun elapsed(p: PartState)=p.thermalState.getDouble("resinElapsed").takeIf(Double::isFinite)?.coerceIn(0.0,1e12) ?: 0.0
        fun rate(node: FaceNodeEntity,p: PartState): Double {
            val world=node.level ?: return 0.0;val origin=node.blockPos;val trunk=origin.relative(p.face)
            if(p.face.axis==net.minecraft.core.Direction.Axis.Y || !world.hasChunkAt(trunk) || !world.getBlockState(trunk).`is`(BlockTags.LOGS))return 0.0
            var bottom=trunk.y;var top=trunk.y;var leaves=0
            while(bottom>world.minBuildHeight && world.getBlockState(trunk.atY(bottom-1)).`is`(BlockTags.LOGS))bottom--
            while(top+1<world.maxBuildHeight && world.getBlockState(trunk.atY(top+1)).`is`(BlockTags.LOGS)) {
                top++;if(world.getBlockState(origin.atY(top)).`is`(BlockTags.LEAVES))leaves++
            }
            var collectors=0
            for(y in bottom..top) {
                val n=world.getBlockEntity(origin.atY(y)) as? FaceNodeEntity ?: continue
                if(n.parts[p.face.get3DDataValue()]?.definition?.behavior==Behavior.RESIN_COLLECTOR)collectors++
            }
            return min(.05,(3f/5f/(60f*24f)).toDouble()*(top-bottom+1)/collectors.coerceAtLeast(1))*(if(leaves>0)1.0 else 1e-9)
        }
        fun harvest(node: FaceNodeEntity,p: PartState,player: Player): Int {
            val world=node.level ?: return 0;val rate=rate(node,p);val stock=rate*elapsed(p)
            val count=stock.toInt().coerceIn(0,2)
            val remaining=if(stock>2)0.0 else if(rate>0)(elapsed(p)-count/rate).coerceAtLeast(0.0)else elapsed(p)
            p.thermalState.putDouble("resinElapsed",remaining);p.thermalState.putDouble("resinStock",rate*remaining)
            if(count>0)Block.popResource(world,node.blockPos,ItemStack(Content.materialItems.getValue("tree_resin").get(),count))
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.eln.resin",String.format(java.util.Locale.ROOT,"%.2f",rate*remaining)),true)
            node.setChanged();node.sync();return count
        }
    }
}
