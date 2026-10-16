package mods.eln.modern

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.vehicle.Minecart
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.network.NetworkHooks
import kotlin.math.abs
import kotlin.math.sign

/** Original 10 kJ, 40 J/tick electric locomotive, driven by actual overhead resistor work. */
class ElectricMinecartEntity(type: EntityType<*>,level: Level): Minecart(type,level) {
    var energyJ=0.0
    var targetJ=10000.0
    var motorJ=0.0
    val displayedEnergy get()=if(level.isClientSide)entityData.get(ENERGY).toDouble()else energyJ
    override fun defineSynchedData() { super.defineSynchedData();entityData.define(ENERGY,0f) }
    override fun tick() {
        super.tick()
        if(!level.isClientSide) { energyJ+=RailPower.collect(this);RailPower.update(this);entityData.set(ENERGY,energyJ.toFloat()) }
    }
    override fun moveAlongTrack(pos: BlockPos,state: BlockState) {
        super.moveAlongTrack(pos,state)
        if(level.isClientSide || energyJ<=0.0)return
        val speed=deltaMovement
        if((abs(speed.x)>=.0005 || abs(speed.z)>=.0005) && abs(speed.x)<.5 && abs(speed.z)<.5) {
            val work=minOf(40.0,energyJ);val fraction=work/40
            deltaMovement=speed.add(speed.x.sign*.05*fraction,0.0,speed.z.sign*.05*fraction)
            energyJ-=work;motorJ+=work
        }
    }
    override fun getMaxSpeedWithRail()=super.getMaxSpeedWithRail()+1.0
    override fun getDefaultDisplayBlockState(): BlockState=Blocks.IRON_BLOCK.defaultBlockState()
    override fun getDropItem()=Content.materialItems.getValue("electric_minecart").get()
    override fun getAddEntityPacket(): Packet<*> = NetworkHooks.getEntitySpawningPacket(this)
    override fun remove(reason: Entity.RemovalReason) { if(!level.isClientSide)RailPower.disconnect(this);super.remove(reason) }
    override fun onRemovedFromWorld() { if(!level.isClientSide)RailPower.disconnect(this);super.onRemovedFromWorld() }
    override fun addAdditionalSaveData(tag: CompoundTag) {
        if(!level.isClientSide)energyJ+=RailPower.collect(this)
        super.addAdditionalSaveData(tag);tag.putDouble("EnergyBufferJ",energyJ);tag.putDouble("EnergyBufferTargetJ",targetJ);tag.putDouble("MotorJ",motorJ)
    }
    override fun readAdditionalSaveData(tag: CompoundTag) {
        super.readAdditionalSaveData(tag)
        energyJ=tag.getDouble("EnergyBufferJ").takeIf(Double::isFinite)?.coerceIn(0.0,1e9) ?: 0.0
        targetJ=if(tag.contains("EnergyBufferTargetJ"))tag.getDouble("EnergyBufferTargetJ").takeIf(Double::isFinite)?.coerceIn(1.0,1e9) ?: 10000.0 else 10000.0
        motorJ=tag.getDouble("MotorJ").takeIf(Double::isFinite)?.coerceAtLeast(0.0) ?: 0.0
    }
    override fun spawnAtLocation(stack: ItemStack,offset: Float): ItemEntity? {
        if(stack.item==Content.materialItems.getValue("electric_minecart").get()) {
            if(!level.isClientSide)energyJ+=RailPower.collect(this)
            stack.orCreateTag.putDouble("EnergyBufferJ",energyJ);stack.orCreateTag.putDouble("EnergyBufferTargetJ",targetJ)
        }
        return super.spawnAtLocation(stack,offset)
    }
    companion object { private val ENERGY: EntityDataAccessor<Float> = SynchedEntityData.defineId(ElectricMinecartEntity::class.java,EntityDataSerializers.FLOAT) }
}
