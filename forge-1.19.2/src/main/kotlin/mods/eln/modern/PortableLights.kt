package mods.eln.modern

import mods.eln.modern.lighting.LightSpotBlock
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

/** Original two-height quarter-block rays and five-tick leases; carried lights work in every slot. */
object PortableLights {
    fun state(stack: ItemStack)=(stack.tag?.getInt("LightState") ?: 0).coerceIn(0,2)
    fun modeKey(state: Int)=when(state) { 1->"message.eln.flashlight_on";2->"message.eln.flashlight_boosted";else->"message.eln.flashlight_off" }
    fun light(stack: ItemStack): Int {
        val spec=ElectricalItems.spec(stack)?.flashlight ?: return 0
        return when(state(stack)) {
            1->if(ElectricalItems.energy(stack)>spec.wattsLow)spec.lightLow else 0
            2->if(ElectricalItems.energy(stack)>spec.wattsHigh)spec.lightHigh else 0
            else->0
        }
    }
    fun consume(stack: ItemStack,seconds: Double): Double {
        val spec=ElectricalItems.spec(stack)?.flashlight ?: return 0.0
        if(!seconds.isFinite() || seconds<=0)return 0.0
        val power=when(state(stack)) { 1->spec.wattsLow;2->spec.wattsHigh;else->0.0 }
        val energy=ElectricalItems.energy(stack);val used=minOf(energy,power*seconds)
        ElectricalItems.setEnergy(stack,energy-used)
        if(energy<=power*seconds)stack.orCreateTag.putInt("LightState",0)
        return used
    }
    fun project(stack: ItemStack,level: ServerLevel,entity: Entity) {
        val spec=ElectricalItems.spec(stack)?.flashlight ?: return
        val brightness=light(stack);if(brightness<=0)return
        val delta=entity.lookAngle.scale(.25)
        val steps=(if(state(stack)==1)spec.rangeLow else spec.rangeHigh)+1
        fun open(pos: BlockPos): Boolean {
            if(level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos))return false
            val block=level.getBlockState(pos)
            return block.isAir || block.block==Content.lightSpot.get()
        }
        for(yOffset in 0..1) {
            var point=Vec3(entity.x,entity.y+1.62-yOffset,entity.z);var count=0
            for(step in 0 until steps) {
                val next=point.add(delta)
                if(!open(BlockPos(next)))break
                point=next;count++
            }
            while(count>0) {
                val pos=BlockPos(point)
                val stride=if(open(pos)) { LightSpotBlock.illuminate(level,pos,brightness);3 }else 1
                point=point.subtract(delta.scale(stride.toDouble()));count-=stride
            }
        }
    }
}
