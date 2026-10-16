package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln")
@PrefixGameTestTemplate(false)
object InfrastructureGameTests {
    @JvmStatic @GameTest(template="empty",timeoutTicks=80)
    fun digitalDisplayKeepsIndependentInputDotsAndStrobe(h: GameTestHelper) {
        create(h,"signal_source",1).parts[0]!!.value=2.5
        val node=create(h,"digital_display",2);val display=node.parts[0]!!;display.expression="1000,2000"
        create(h,"signal_source",3).parts[0]!!.value=1.25
        val strobe=create(h,"signal_source",2,3).parts[0]!!.apply { value=0.0 }
        h.runAtTickTime(20) {
            check(h,display.displayReading==1500 && display.displayBits==64 && !display.displayBlank,"Display range or independent dot inputs failed")
            strobe.value=5.0
            val player=h.makeMockPlayer();player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack(Items.BLUE_DYE))
            (node.blockState.block as NodeBlock).use(node.blockState,h.level,node.blockPos,player,net.minecraft.world.InteractionHand.MAIN_HAND,
                net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(node.blockPos),Direction.UP,node.blockPos,false))
            check(h,display.displayColor==net.minecraft.world.item.DyeColor.BLUE.fireworkColor,"Display dye interaction did not update server color")
        }
        h.runAtTickTime(40) {
            check(h,display.displayBlank && display.displayReading==1500,"Strobe shorted measurement input or did not blank display")
            val saved=PartState.load(display.save())!!
            check(h,saved.expression=="1000,2000" && saved.displayBits==64 && saved.displayColor==display.displayColor,"Display settings did not persist")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=500)
    fun conductorLossAndThermalOverload(h: GameTestHelper) {
        create(h,"electrical_source",1)
        create(h,"low_voltage_cable",2)
        // The original inline electrical probe has no thermal load. It isolates the cable's
        // burnout test from the resistor's now-restored, large thermal capacity.
        val probe=create(h,"electrical_probe",3,rotation=1)
        PartInventory.get(probe,probe.parts[0]!!).setStackInSlot(0,ItemStack(Content.partItems.getValue("low_voltage_cable").get()))
        val load=create(h,"creative_power_resistor",4,rotation=2).parts[0]!!.apply { value=1.0 }
        create(h,"ground_cable",5)
        h.runAtTickTime(10) {
            check(h,abs(load.current-50/1.056003)<.005,"Original cable and probe resistance produced incorrect voltage drop: ${load.current}")
        }
        h.runAtTickTime(450) {
            val remaining=(h.level.getBlockEntity(h.absolutePos(BlockPos(2,2,2))) as? FaceNodeEntity)?.parts?.get(0)
            check(h,h.getBlockState(BlockPos(2,2,2)).isAir,"Overloaded 24 A conductor did not burn out: $remaining, temperature=${remaining?.temperature}, fault=${remaining?.thermalState}")
            check(h,abs(load.current)<1e-5,"Burned conductor still delivered power")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=70)
    fun conductorOvervoltageProtection(h: GameTestHelper) {
        create(h,"electrical_source",1).parts[0]!!.value=200.0
        create(h,"low_voltage_cable",2)
        create(h,"creative_power_resistor",3,rotation=2)
        create(h,"ground_cable",4)
        h.runAtTickTime(25) {
            check(h,h.getBlockState(BlockPos(2,2,2)).isAir,"50 V conductor survived sustained 200 V overvoltage")
            h.succeed()
        }
    }
    fun create(h: GameTestHelper,id: String,x: Int,z: Int=2,rotation: Int=0): FaceNodeEntity {
        val d=PartDefinition.byId.getValue(id)
        h.setBlock(x,1,z,Blocks.STONE);h.setBlock(x,2,z,if(d.machine)Content.machine.get() else Content.node.get())
        val n=h.getBlockEntity(BlockPos(x,2,z)) as FaceNodeEntity
        n.parts[0]=PartState(d,Direction.DOWN).apply { this.rotation=rotation };n.changedTopology();return n
    }
    private fun check(h: GameTestHelper,condition: Boolean,message: String) { if(!condition)h.fail(message) }

    @JvmStatic @GameTest(template="empty",timeoutTicks=80)
    fun fractionalEnergyAndTransferBudget(h: GameTestHelper) {
        create(h,"electrical_source",1).parts[0]!!.value=5.0
        val converter=create(h,"energy_converter",2)
        h.runAtTickTime(22) {
            check(h,converter.energyBuffer.energy>9 && converter.energyBuffer.energy<11,"Fractional 10 W production was rounded away: ${converter.energyBuffer.energy}")
            converter.energyBuffer.energy=10000.5
            val cap=converter.getCapability(ForgeCapabilities.ENERGY).orElseThrow { IllegalStateException("Missing FE output") }
            check(h,cap.extractEnergy(100000,true)==1000,"Output preview ignored tick budget")
            check(h,abs(converter.energyBuffer.energy-10000.5)<1e-9,"Simulation request consumed energy")
            check(h,cap.extractEnergy(-1,false)==0,"Negative extraction accepted")
            check(h,cap.extractEnergy(100000,false)==1000 && cap.extractEnergy(100000,false)==0,"Repeated requests bypassed shared tick budget")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=90)
    fun forgeEnergyInputPowersRealResistorAndPersists(h: GameTestHelper) {
        val input=create(h,"energy_converter",1);input.parts[0]!!.value=1.0;input.changedTopology()
        val resistor=create(h,"creative_power_resistor",2,rotation=2).parts[0]!!
        create(h,"ground_cable",3)
        val cap=input.getCapability(ForgeCapabilities.ENERGY).orElseThrow { IllegalStateException("Missing FE input") }
        check(h,cap.canReceive() && !cap.canExtract(),"Input converter allowed feedback")
        check(h,cap.receiveEnergy(500,false)==500,"Input did not receive bounded FE")
        h.runAtTickTime(30) {
            check(h,abs(resistor.current-.5)<.01,"FE input failed real 50 V / 100 ohm circuit")
            check(h,input.energyBuffer.energy<480 && input.energyBuffer.energy>430,"Input conversion did not debit measured circuit power")
            val energy=input.energyBuffer.energy;val saved=input.saveWithoutMetadata();input.load(saved)
            check(h,abs(energy-input.energyBuffer.energy)<1e-9,"Save/reload lost fractional bridge energy")
        }
        h.runAtTickTime(60) {
            check(h,abs(resistor.current-.5)<.01,"Restored input lost electrical connectivity")
            input.energyBuffer.energy=0.0
        }
        h.runAtTickTime(70) { check(h,abs(resistor.current)<1e-5,"Empty FE input generated free electricity");h.succeed() }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=70)
    fun sidedMachineAutomationRejectsWrongResources(h: GameTestHelper) {
        val machine=create(h,"electrical_furnace",2)
        val input=machine.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.UP).orElseThrow { IllegalStateException("No input") }
        val output=machine.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.DOWN).orElseThrow { IllegalStateException("No output") }
        check(h,input.insertItem(0,ItemStack(Items.DIRT),false).count==1,"Machine accepted invalid recipe input")
        check(h,input.insertItem(0,ItemStack(Items.RAW_IRON),true).isEmpty && machine.inventory.getStackInSlot(0).isEmpty,"Input preview mutated inventory")
        check(h,output.insertItem(0,ItemStack(Items.RAW_IRON),false).count==1,"Output face accepted inputs")
        input.insertItem(0,ItemStack(Items.RAW_IRON),false)
        machine.inventory.setStackInSlot(2,ItemStack(Items.IRON_INGOT))
        check(h,input.extractItem(1,1,false).isEmpty,"Input face extracted outputs")
        check(h,output.extractItem(1,1,false).`is`(Items.IRON_INGOT),"Output face did not extract output")
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=220)
    fun electricalMotorShaftGeneratorAndSplit(h: GameTestHelper) {
        create(h,"electrical_source",1).parts[0]!!.value=3200.0
        val motor=create(h,"shaft_motor",2)
        val joint=create(h,"joint",2,3)
        val generator=create(h,"generator",2,4)
        val resistor=create(h,"creative_power_resistor",3,4,2).parts[0]!!.apply { value=10000.0 }
        create(h,"ground_cable",4,4)
        h.runAtTickTime(100) {
            check(h,motor.parts[0]!!.shaftSpeed>20 && generator.parts[0]!!.shaftSpeed>20,"Motor did not drive connected shaft")
            check(h,resistor.voltage-resistor.voltageB>100 && resistor.current>.01,"Shaft generator did not power electrical load")
            val saved=joint.saveWithoutMetadata();val energy=joint.parts[0]!!.shaftEnergy;joint.load(saved)
            check(h,abs(joint.parts[0]!!.shaftEnergy-energy)<1e-9,"Shaft save/reload changed kinetic energy")
            h.setBlock(2,2,3,Blocks.AIR)
        }
        h.runAtTickTime(180) {
            check(h,motor.parts[0]!!.shaftSpeed>generator.parts[0]!!.shaftSpeed,"Shaft split failed to separate networks")
            check(h,generator.parts[0]!!.shaftEnergy>=0,"Generator made negative kinetic energy")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=230)
    fun rollingMachineConsumesKineticEnergy(h: GameTestHelper) {
        val roller=create(h,"rolling_shaft",2)
        val flywheel=create(h,"flywheel",2,3)
        roller.parts[0]!!.shaftGroup="saved-rolling-shaft";flywheel.parts[0]!!.shaftGroup="saved-rolling-shaft"
        roller.parts[0]!!.shaftEnergy=11000.0*5/55;flywheel.parts[0]!!.shaftEnergy=11000.0*50/55
        roller.inventory.setStackInSlot(0,ItemStack(Items.IRON_INGOT));roller.changedTopology()
        h.runAtTickTime(205) {
            check(h,roller.inventory.getStackInSlot(2).`is`(Content.materialItems.getValue("iron_plate").get()),"Mechanical rollers did not make plate")
            check(h,roller.inventory.getStackInSlot(0).isEmpty && roller.parts[0]!!.shaftEnergy+flywheel.parts[0]!!.shaftEnergy<1000,"Mechanical processing did not consume input and kinetic energy")
            h.succeed()
        }
    }
}
