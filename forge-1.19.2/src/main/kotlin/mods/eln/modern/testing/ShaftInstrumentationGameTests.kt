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
object ShaftInstrumentationGameTests {
    private fun create(h: GameTestHelper,id: String,x: Int,z: Int=2,rotation: Int=1): FaceNodeEntity {
        GameTestYards.clear(h,240)
        val base=h.absolutePos(BlockPos.ZERO);val pos=BlockPos(base.x+x,240,base.z+z);val d=PartDefinition.byId.getValue(id)
        h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
        h.level.setBlockAndUpdate(pos,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
        return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[0]=PartState(d,Direction.DOWN).apply { this.rotation=rotation };it.changedTopology() }
    }
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=130)
    fun tachometerMeasuresActualShaftSpeedAndPreservesItsCalibration(h: GameTestHelper) {
        val flywheel=create(h,"flywheel",1);val tach=create(h,"tachometer",2);val joint=create(h,"joint",3)
        for(n in listOf(flywheel,tach,joint))n.parts[0]!!.apply { shaftEnergy=.5*definition.shaftMass*.05*50*50;shaftGroup="tachometer-fixture" }
        val reader=create(h,"voltage_probe",2,1,3)
        PartInventory.get(reader,reader.parts[0]!!).setStackInSlot(0,ItemStack(Content.partItems.getValue("low_voltage_cable").get()))
        h.runAtTickTime(25) {
            val p=tach.parts[0]!!
            if(p.shaftSpeed !in 49.0..50.0 || abs(reader.parts[0]!!.voltage-p.shaftSpeed/2500*5)>.002)h.fail("Original tachometer failed to measure its real rotating shaft: ${p.save()}, reader=${reader.parts[0]!!.voltage}")
            p.expression="0,100";tach.changedTopology()
        }
        h.runAtTickTime(55) {
            val p=tach.parts[0]!!
            if(abs(reader.parts[0]!!.voltage-p.shaftSpeed/100*5)>.01)h.fail("Tachometer calibration did not scale the finite five-volt output")
            val saved=tach.saveWithoutMetadata();tach.load(saved);tach.changedTopology()
            if(tach.parts[0]!!.expression!="0,100" || tach.parts[0]!!.shaftEnergy<=0)h.fail("Tachometer calibration or finite kinetic share failed persistence")
        }
        h.runAtTickTime(90) {
            if(abs(reader.parts[0]!!.voltage-tach.parts[0]!!.shaftSpeed/100*5)>.01)h.fail("Reloaded tachometer failed to reconnect its real signal and shaft")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="grid_empty",timeoutTicks=120)
    fun fixedShaftLocksTheNetworkWithoutStoringFreeKineticEnergy(h: GameTestHelper) {
        val flywheel=create(h,"flywheel",1);flywheel.parts[0]!!.shaftEnergy=3000.0
        val fixed=create(h,"fixed_shaft",2);val motor=create(h,"shaft_motor",3)
        create(h,"electrical_source",3,1).parts[0]!!.value=3200.0
        h.runAtTickTime(25) {
            if(listOf(flywheel,fixed,motor).any { it.parts[0]!!.shaftSpeed!=0.0 || it.parts[0]!!.shaftEnergy!=0.0 })h.fail("Fixed shaft failed to hold its connected network stationary")
            if(motor.parts[0]!!.voltage*motor.parts[0]!!.current<1100)h.fail("Stationary motor did not draw real electrical power against the fixed shaft")
            val saved=fixed.saveWithoutMetadata();fixed.load(saved);fixed.changedTopology()
        }
        h.runAtTickTime(50) {
            if(fixed.parts[0]!!.shaftEnergy!=0.0)h.fail("Reloading a fixed shaft created kinetic energy")
            h.level.removeBlock(fixed.blockPos,false);create(h,"joint",2)
        }
        h.runAtTickTime(85) {
            if(flywheel.parts[0]!!.shaftSpeed<=0 || motor.parts[0]!!.shaftEnergy<=0)h.fail("Replacing the fixed shaft with a real joint failed to release the powered drive")
            h.succeed()
        }
    }
}
