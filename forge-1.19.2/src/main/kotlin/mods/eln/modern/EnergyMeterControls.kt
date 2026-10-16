package mods.eln.modern

import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.security.MessageDigest
import java.util.Locale

/** Source controls remain physical menu actions; password checks run on the server as well as the GUI. */
object EnergyMeterControls {
    private fun digest(salt: String,password: String)=MessageDigest.getInstance("SHA-256").digest((salt+":"+password).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun authenticate(p: PartState,password: String): Boolean {
        val s=p.gridState;val expected=s.getString("meterPasswordHash")
        return expected.isEmpty() || MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8),digest(s.getString("meterPasswordSalt"),password).toByteArray(Charsets.UTF_8))
    }
    fun command(node: FaceNodeEntity,p: PartState,command: Int,value: Double,text: String,password: String): Boolean {
        if(node.isRemoved || node.parts[p.face.get3DDataValue()]!==p || command !in 0..7 || !value.isFinite() || text.length>64 || password.length>64 || !authenticate(p,password))return false
        EnergyMeters.initialize(p)
        val s=p.gridState
        when(command) {
            0 -> Unit
            1 -> { p.enabled=!p.enabled;node.level!!.playSound(null,node.blockPos,net.minecraft.sounds.SoundEvents.LEVER_CLICK,net.minecraft.sounds.SoundSource.BLOCKS,.3f,.6f) }
            2 -> s.putInt("meterMode",1-s.getInt("meterMode").coerceIn(0,1))
            3 -> if(!EnergyMeters.setCounter(node,p,value))return false
            4 -> s.putDouble("meterTime",0.0)
            5 -> if(p.definition.id=="advanced_energy_meter")s.putInt("meterEnergyUnit",(s.getInt("meterEnergyUnit")+1)%4)
            6 -> if(p.definition.id=="advanced_energy_meter")s.putInt("meterTimeUnit",1-s.getInt("meterTimeUnit").coerceIn(0,1))
            7 -> {
                if(text.isEmpty()) { s.remove("meterPasswordSalt");s.remove("meterPasswordHash") }
                else { val salt=java.util.UUID.randomUUID().toString();s.putString("meterPasswordSalt",salt);s.putString("meterPasswordHash",digest(salt,text)) }
            }
        }
        node.setChanged();node.sync();return true
    }
    fun reading(p: PartState): Component {
        fun n(v: Double,precision: Int)=String.format(Locale.ROOT,"%.${precision}f",v)
        return Component.translatable("message.eln.energy_meter",n(p.voltage,3),n(p.voltageB,3),n(p.current,4),n(p.gridState.getDouble("meterPowerW"),2))
    }
    fun handler(node: FaceNodeEntity)=object : net.minecraftforge.items.IItemHandler {
        private fun inv()=if(node.isRemoved)null else node.parts.filterNotNull().firstOrNull { it.definition.behavior==Behavior.ENERGY_METER }?.let { PartInventory.get(node,it) }
        override fun getSlots()=1
        override fun getStackInSlot(slot: Int)=if(slot==0)inv()?.getStackInSlot(0) ?: ItemStack.EMPTY else ItemStack.EMPTY
        override fun insertItem(slot: Int,stack: ItemStack,simulate: Boolean)=if(slot==0)inv()?.insertItem(0,stack,simulate) ?: stack else stack
        override fun extractItem(slot: Int,amount: Int,simulate: Boolean)=if(slot==0)inv()?.extractItem(0,amount,simulate) ?: ItemStack.EMPTY else ItemStack.EMPTY
        override fun getSlotLimit(slot: Int)=if(slot==0)1 else 0
        override fun isItemValid(slot: Int,stack: ItemStack)=slot==0 && inv()?.isItemValid(0,stack)==true
    }
}
