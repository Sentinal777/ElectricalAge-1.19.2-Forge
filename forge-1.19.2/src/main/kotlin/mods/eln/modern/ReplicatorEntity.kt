package mods.eln.modern

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.*
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraftforge.network.NetworkHooks
import java.util.EnumSet
import kotlin.math.min

/** Original persistent, electricity-eating hostile mob; its food is paid circuit work. */
class ReplicatorEntity(type: EntityType<out ReplicatorEntity>,level: Level): Monster(type,level) {
    var hunger=(random.nextDouble()-.5)*.3
    var spawnedFromWeather=false
    var electricalWorkJ=0.0
    var nutritionalWorkJ=0.0
    var dissipatedWorkJ=0.0
    var feedResistance=1e12
    private var nutritionTick=Long.MIN_VALUE
    private var nutritionThisTick=0.0
    init { setPersistenceRequired() }
    override fun registerGoals() {
        goalSelector.addGoal(0,FloatGoal(this))
        goalSelector.addGoal(1,MeleeAttackGoal(this,1.0,false))
        goalSelector.addGoal(4,ReplicatorCableGoal(this))
        goalSelector.addGoal(5,MoveTowardsRestrictionGoal(this,1.0))
        goalSelector.addGoal(6,MoveThroughVillageGoal(this,1.0,false,4) { false })
        goalSelector.addGoal(7,RandomStrollGoal(this,1.0,20))
        goalSelector.addGoal(8,LookAtPlayerGoal(this,Player::class.java,8f))
        goalSelector.addGoal(9,RandomLookAroundGoal(this))
        targetSelector.addGoal(1,HurtByTargetGoal(this).setAlertOthers())
        targetSelector.addGoal(2,NearestAttackableTargetGoal(this,Player::class.java,true))
        targetSelector.addGoal(2,NearestAttackableTargetGoal(this,Villager::class.java,false))
        targetSelector.addGoal(3,object: NearestAttackableTargetGoal<ReplicatorEntity>(this,ReplicatorEntity::class.java,false) {
            override fun canUse()=hunger>=.6 && super.canUse()
            override fun canContinueToUse()=hunger>=.6 && super.canContinueToUse()
        })
    }
    override fun aiStep() {
        super.aiStep()
        if(level.isClientSide || !isAlive)return
        ReplicatorFeeding.collect(this)
        hunger+=.05/600.0
        if(hunger>1 && random.nextDouble()<.05/5)hurt(DamageSource.STARVE,1f)
        if(hunger<.5 && random.nextDouble()*10<.05)heal(1f)
        if(hunger < -10000.0/6000.0) {
            val child=Content.replicator.get().create(level) ?: return
            child.moveTo(x,y,z,0f,0f)
            if(level.addFreshEntity(child)) { hunger=0.0;child.playAmbientSound() }
        }
    }
    fun eatElectricity(work: Double) {
        if(!work.isFinite() || work<=0)return
        if(nutritionTick!=level.gameTime) { nutritionTick=level.gameTime;nutritionThisTick=0.0 }
        val food=min(work,(6.0-nutritionThisTick).coerceAtLeast(0.0))
        nutritionThisTick+=food;electricalWorkJ+=work;nutritionalWorkJ+=food;dissipatedWorkJ+=work-food
        hunger-=food/6000.0
    }
    override fun doHurtTarget(entity: Entity): Boolean {
        if(entity is ReplicatorEntity) { hunger-=.4;entity.hunger+=.4 }
        return super.doHurtTarget(entity)
    }
    override fun getAmbientSound()=SoundEvents.SILVERFISH_AMBIENT
    override fun getHurtSound(source: DamageSource)=SoundEvents.SILVERFISH_HURT
    override fun getDeathSound()=SoundEvents.SILVERFISH_DEATH
    override fun dropCustomDeathLoot(source: DamageSource,looting: Int,recentlyHit: Boolean) {
        super.dropCustomDeathLoot(source,looting,recentlyHit)
        val drops=listOf(Content.materialItems.getValue("iron_dust").get(),Content.materialItems.getValue("copper_dust").get(),Content.materialItems.getValue("gold_dust").get(),Items.REDSTONE,Items.GLOWSTONE_DUST)
        spawnAtLocation(ItemStack(drops[random.nextInt(drops.size)]),.5f)
        if(spawnedFromWeather && random.nextDouble()<.33)spawnAtLocation(ItemStack(Content.replicatorEgg.get()),.5f)
    }
    override fun getAddEntityPacket(): Packet<*> = NetworkHooks.getEntitySpawningPacket(this)
    override fun remove(reason: Entity.RemovalReason) { if(!level.isClientSide)ReplicatorFeeding.disconnect(this);super.remove(reason) }
    override fun onRemovedFromWorld() { if(!level.isClientSide)ReplicatorFeeding.disconnect(this);super.onRemovedFromWorld() }
    override fun addAdditionalSaveData(tag: CompoundTag) {
        if(!level.isClientSide)ReplicatorFeeding.collect(this)
        super.addAdditionalSaveData(tag)
        tag.putDouble("ElnHunger",hunger);tag.putBoolean("isSpawnedFromWeather",spawnedFromWeather)
        tag.putDouble("ElnElectricalWorkJ",electricalWorkJ);tag.putDouble("ElnNutritionalWorkJ",nutritionalWorkJ);tag.putDouble("ElnDissipatedWorkJ",dissipatedWorkJ)
    }
    override fun readAdditionalSaveData(tag: CompoundTag) {
        super.readAdditionalSaveData(tag)
        hunger=tag.getDouble("ElnHunger").takeIf(Double::isFinite) ?: 0.0
        spawnedFromWeather=tag.getBoolean("isSpawnedFromWeather")
        electricalWorkJ=tag.getDouble("ElnElectricalWorkJ").takeIf { it.isFinite() && it>=0 } ?: 0.0
        nutritionalWorkJ=tag.getDouble("ElnNutritionalWorkJ").takeIf { it.isFinite() && it>=0 } ?: 0.0
        dissipatedWorkJ=tag.getDouble("ElnDissipatedWorkJ").takeIf { it.isFinite() && it>=0 } ?: 0.0
    }
    companion object {
        fun attributes()=createMonsterAttributes().add(Attributes.FOLLOW_RANGE,8.0).add(Attributes.MAX_HEALTH,8.0)
            .add(Attributes.MOVEMENT_SPEED,.23000000417232513).add(Attributes.ATTACK_DAMAGE,3.0)
    }
}

/** Original 20 random loaded-node probes, fifteen-block search and 20/120-second timers. */
class ReplicatorCableGoal(private val mob: ReplicatorEntity): Goal() {
    var diagnostics=""
        private set
    private var target: ReplicatorFeeding.Cable?=null
    private var moveLeft=20.0
    private var resetLeft=120.0
    init { flags=EnumSet.of(Flag.MOVE) }
    override fun canUse(): Boolean {
        val world=mob.level as? ServerLevel ?: return false
        if(!pathAreaLoaded(world)) { diagnostics="path region not fully loaded at ${mob.blockPosition()}";return false }
        val nodes=ReplicatorFeeding.candidates(world)
        diagnostics="nodes=${nodes.size}, ground=${mob.isOnGround}, pos=${mob.position()}"
        if(nodes.isEmpty())return false
        repeat(20) {
            val cable=nodes[mob.random.nextInt(nodes.size)]
            if(cable.node.blockPos.distToCenterSqr(mob.position())>225 || !ReplicatorFeeding.interesting(cable))return@repeat
            val path=mob.navigation.createPath(cable.node.blockPos,0) ?: return@repeat
            mob.navigation.moveTo(path,1.0);target=cable;moveLeft=20.0;resetLeft=120*(.8+mob.random.nextDouble()*.4);mob.feedResistance=1e12
            return true
        }
        return false
    }
    override fun canContinueToUse()=target!=null && moveLeft>=0 && resetLeft>=0
    override fun tick() {
        moveLeft-=.05;resetLeft-=.05
        val cable=target ?: return
        if(!ReplicatorFeeding.interesting(cable)) { target=null;ReplicatorFeeding.disconnect(mob);return }
        if(cable.node.blockPos.distToCenterSqr(mob.position())<4) {
            ReplicatorFeeding.feed(mob,cable);moveLeft=20.0
        }else {
            ReplicatorFeeding.disconnect(mob)
            if(mob.navigation.isDone && pathAreaLoaded(mob.level as ServerLevel))mob.navigation.moveTo(cable.node.blockPos.x+.5,cable.node.blockPos.y.toDouble(),cable.node.blockPos.z+.5,1.0)
        }
    }
    private fun pathAreaLoaded(world: ServerLevel): Boolean {
        // PathNavigationRegion reads a followRange + 8 block square with ordinary getChunk.
        val follow=mob.getAttributeValue(Attributes.FOLLOW_RANGE)
        if(!follow.isFinite() || follow !in 0.0..120.0)return false
        val radius=(follow+8).toInt()
        val at=mob.blockPosition()
        for(x in (at.x-radius shr 4)..(at.x+radius shr 4))for(z in (at.z-radius shr 4)..(at.z+radius shr 4))
            if(world.chunkSource.getChunkNow(x,z)==null)return false
        return true
    }
    override fun stop() { target=null;ReplicatorFeeding.disconnect(mob);mob.navigation.stop() }
}
