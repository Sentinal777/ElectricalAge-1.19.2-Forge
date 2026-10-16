package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object ClutchGameTests {
    private class Rig(val h: GameTestHelper,val speed: Double=100.0) {
        private val base=h.absolutePos(BlockPos.ZERO)
        fun device(id: String,x: Int,z: Int=2,rotation: Int=1): FaceNodeEntity {
            GameTestYards.clear(h,250)
            val pos=BlockPos(base.x+x,250,base.z+z);val d=PartDefinition.byId.getValue(id)
            h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
            h.level.setBlockAndUpdate(pos,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
            return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(d,Direction.DOWN).apply { this.rotation=rotation };it.changedTopology() }
        }
        val left=device("flywheel",1).also { it.parts[0]!!.apply { shaftEnergy=.5*definition.shaftMass*.05*speed*speed;shaftGroup="clutch-left" } }
        val clutch=device("clutch",2).also { it.parts[0]!!.apply { shaftEnergy=.5*definition.shaftMass*.05*speed*speed;gridState.putDouble("clutchEnergy3",0.0);gridState.putDouble("clutchEnergy1",shaftEnergy);gridState.putString("clutchGroup1","clutch-left");gridState.putString("clutchGroup3","clutch-right") } }
        val right=device("flywheel",3).also { it.parts[0]!!.shaftGroup="clutch-right" }
        val signal=device("signal_source",2,1).also { it.parts[0]!!.value=0.0 }
        val inventory get()=PartInventory.get(clutch,clutch.parts[0]!!)
        fun plate(id: String,wear: Double=0.0) { inventory.setStackInSlot(0,ItemStack(Content.materialItems.getValue(id).get()).also { it.orCreateTag.putDouble("wear",wear) }) }
        fun energy()=left.parts[0]!!.shaftEnergy+right.parts[0]!!.shaftEnergy+clutch.parts[0]!!.shaftEnergy
    }
    @JvmStatic @GameTest(batch="shaft_clutch",template="grid_empty",timeoutTicks=160)
    fun realControlPlateFrictionWearAndTwoIndependentShafts(h: GameTestHelper) {
        val rig=Rig(h);rig.plate("iron_clutch_plate");val initial=rig.energy()
        var engagedEnergy=0.0;var wear=0.0;var rightEnergy=0.0
        h.runAtTickTime(20) {
            if(rig.left.parts[0]!!.shaftSpeed<99 || rig.right.parts[0]!!.shaftSpeed!=0.0)h.fail("Disengaged clutch merged its two independent shaft networks")
            rig.signal.parts[0]!!.value=5.0
        }
        h.runAtTickTime(45) {
            val p=rig.clutch.parts[0]!!;wear=rig.inventory.getStackInSlot(0).tag!!.getDouble("wear")
            if(abs(rig.left.parts[0]!!.shaftSpeed-rig.right.parts[0]!!.shaftSpeed)>1e-6 || p.gridState.getBoolean("clutchSlipping") || wear<=0)h.fail("Original iron plate did not couple the actual shaft speeds through finite, wearing friction: ${p.save()}")
            if(rig.energy()>=initial || p.thermalState.getDouble("clutchHeat")<5000 || rig.energy()+p.thermalState.getDouble("clutchHeat")>initial+1e-6)h.fail("Clutch friction created kinetic energy or failed to dissipate slip work")
            engagedEnergy=rig.energy();val saved=rig.clutch.saveWithoutMetadata();rig.clutch.load(saved);rig.clutch.changedTopology()
        }
        h.runAtTickTime(70) {
            if(rig.energy()>engagedEnergy || rig.inventory.getStackInSlot(0).tag!!.getDouble("wear")!=wear || abs(rig.left.parts[0]!!.shaftSpeed-rig.right.parts[0]!!.shaftSpeed)>1e-6)h.fail("Reload duplicated clutch energy or lost its plate/lock state")
            rig.signal.parts[0]!!.value=0.0;rightEnergy=rig.right.parts[0]!!.shaftEnergy
            rig.left.parts[0]!!.shaftEnergy+=1000;rig.left.changedTopology()
        }
        h.runAtTickTime(100) {
            if(rig.right.parts[0]!!.shaftEnergy>rightEnergy || rig.left.parts[0]!!.shaftSpeed-rig.right.parts[0]!!.shaftSpeed<2 || rig.inventory.getStackInSlot(0).tag!!.getDouble("wear")!=wear)h.fail("Zero-volt clutch control continued to transmit or wear its plate")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="shaft_clutch",template="grid_empty",timeoutTicks=100)
    fun wornPlateCannotTransferAndReplacementRestoresActualCoupling(h: GameTestHelper) {
        val rig=Rig(h,40.0);rig.plate("gold_clutch_plate",1.0);rig.signal.parts[0]!!.value=5.0
        h.runAtTickTime(25) {
            if(rig.right.parts[0]!!.shaftEnergy!=0.0 || rig.left.parts[0]!!.shaftSpeed<39)h.fail("A fully worn clutch plate still transmitted torque")
            rig.plate("gold_clutch_plate")
        }
        h.runAtTickTime(55) {
            if(rig.right.parts[0]!!.shaftSpeed<15 || abs(rig.right.parts[0]!!.shaftSpeed-rig.left.parts[0]!!.shaftSpeed)>1e-6)h.fail("Replacing the worn plate did not restore the original gold plate's real coupling")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="shaft_clutch",template="grid_empty",timeoutTicks=140)
    fun lockingPinPreventsReslipUnderARealMotorLoad(h: GameTestHelper) {
        val rig=Rig(h,0.0);rig.plate("iron_clutch_plate");rig.signal.parts[0]!!.value=.01
        rig.inventory.setStackInSlot(1,ItemStack(Content.materialItems.getValue("clutch_pin").get()))
        rig.clutch.parts[0]!!.gridState.putBoolean("clutchSlipping",false)
        val motor=rig.device("shaft_motor",0).also { it.parts[0]!!.enabled=false };rig.device("electrical_source",0,1).parts[0]!!.value=3200.0
        h.runAtTickTime(20) {
            if(rig.clutch.parts[0]!!.voltage<.005 || rig.clutch.parts[0]!!.gridState.getBoolean("clutchSlipping"))h.fail("Zero-speed clutch did not latch after its real control gate became powered")
            motor.parts[0]!!.enabled=true
        }
        h.runAtTickTime(55) {
            if(motor.parts[0]!!.energy<=0 || rig.right.parts[0]!!.shaftSpeed<=0 || abs(rig.left.parts[0]!!.shaftSpeed-rig.right.parts[0]!!.shaftSpeed)>1e-6 || rig.clutch.parts[0]!!.gridState.getBoolean("clutchSlipping"))h.fail("Locking pin failed to transmit the actual motor's energy without reslipping: motor=${motor.parts[0]!!.save()}, clutch=${rig.clutch.parts[0]!!.save()}, right=${rig.right.parts[0]!!.save()}")
            rig.inventory.extractItem(1,1,false)
        }
        h.runAtTickTime(105) {
            if(!rig.clutch.parts[0]!!.gridState.getBoolean("clutchSlipping") || rig.left.parts[0]!!.shaftSpeed-rig.right.parts[0]!!.shaftSpeed<1)h.fail("Removing the pin did not restore the original static-friction limit under the real motor load")
            h.succeed()
        }
    }
}
