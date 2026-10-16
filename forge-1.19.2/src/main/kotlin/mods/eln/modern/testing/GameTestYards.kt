package mods.eln.modern.testing

import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import java.util.WeakHashMap

/** Vanilla reloads the low template; our sky fixtures must also reset their own isolated test volume. */
object GameTestYards {
    private val prepared=WeakHashMap<GameTestHelper,MutableSet<Int>>()
    fun clear(h: GameTestHelper,height: Int,below: Int=1,above: Int=5) {
        if(!prepared.getOrPut(h) { hashSetOf() }.add(height))return
        val base=h.absolutePos(BlockPos.ZERO)
        for(x in 0..16)for(z in 0..16)for(y in height-below..height+above)
            h.level.setBlockAndUpdate(BlockPos(base.x+x,y,base.z+z),Blocks.AIR.defaultBlockState())
    }
}
