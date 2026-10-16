package mods.eln.modern

import net.minecraft.world.item.ItemStack

data class ScannerSpec(val watts: Double,val width: Int,val height: Int,val fieldOfView: Double)

/** Original e/s/c/d item NBT, four-second boot, one-second stop and finite 300 W electronics. */
object PortableScanner {
    const val IDLE=0;const val BOOT=1;const val RUN=2;const val STOP=3
    fun spec(stack: ItemStack)=ElectricalItems.spec(stack)?.scanner
    fun initialize(stack: ItemStack) {
        if(spec(stack)==null)return
        val tag=stack.orCreateTag
        if(!tag.contains("e")) {
            tag.putDouble("e",ElectricalItems.spec(stack)!!.capacity*.2);tag.putByte("s",BOOT.toByte());tag.putShort("c",80);tag.putByte("d",0)
        }
    }
    fun state(stack: ItemStack): Int { initialize(stack);return stack.orCreateTag.getByte("s").toInt().coerceIn(IDLE,STOP) }
    fun damage(stack: ItemStack): Int { initialize(stack);return stack.orCreateTag.getByte("d").toInt().coerceIn(0,12) }
    fun modeKey(stack: ItemStack)=if(damage(stack)>=12)"message.eln.scanner_broken" else when(state(stack)) {
        BOOT->"message.eln.scanner_boot";RUN->"message.eln.scanner_run";STOP->"message.eln.scanner_stop";else->"message.eln.scanner_off"
    }
    fun toggle(stack: ItemStack) {
        if(spec(stack)==null || damage(stack)>=12)return
        val tag=stack.orCreateTag
        when(state(stack)) {
            IDLE->if(ElectricalItems.energy(stack)>spec(stack)!!.watts) { tag.putByte("s",BOOT.toByte());tag.putShort("c",80) }
            RUN->{ tag.putByte("s",STOP.toByte());tag.putShort("c",20) }
        }
    }
    fun tick(stack: ItemStack) {
        if(spec(stack)==null)return
        val tag=stack.orCreateTag
        if(damage(stack)>=12) { tag.putByte("s",IDLE.toByte());return }
        when(state(stack)) {
            BOOT,STOP->{
                val counter=(tag.getShort("c").toInt()-1).coerceAtLeast(0);tag.putShort("c",counter.toShort())
                if(counter==0)tag.putByte("s",(if(state(stack)==BOOT)RUN else IDLE).toByte())
            }
        }
    }
    fun consume(stack: ItemStack,seconds: Double): Double {
        val spec=spec(stack) ?: return 0.0
        if(!seconds.isFinite() || seconds<=0 || state(stack)==IDLE)return 0.0
        val energy=ElectricalItems.energy(stack);val amount=minOf(energy,spec.watts*seconds)
        ElectricalItems.setEnergy(stack,energy-amount)
        if(energy<=spec.watts*seconds)stack.orCreateTag.putByte("s",IDLE.toByte())
        return amount
    }
    fun strike(stack: ItemStack) {
        if(spec(stack)==null)return
        stack.orCreateTag.putByte("d",(damage(stack)+1).coerceAtMost(12).toByte())
        if(damage(stack)>=12)stack.orCreateTag.putByte("s",IDLE.toByte())
    }
    fun dropped(stack: ItemStack) { if(spec(stack)!=null) { initialize(stack);stack.orCreateTag.putByte("s",IDLE.toByte()) } }
}
