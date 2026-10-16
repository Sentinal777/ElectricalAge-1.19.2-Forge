package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object EnvironmentSensorGameTests {
    private fun at(h: GameTestHelper,x: Int=8,z: Int=8)=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+x,205,it.z+z) }
    private fun device(h: GameTestHelper,id: String,pos: BlockPos=at(h),face: Direction=Direction.DOWN): FaceNodeEntity {
        GameTestYards.clear(h,205,4,18)
        h.level.setBlockAndUpdate(pos.relative(face),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(pos,Content.node.get().defaultBlockState())
        return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { it.parts[face.ordinal]=PartState(PartDefinition.byId.getValue(id),face);it.changedTopology() }
    }
    private fun part(n: FaceNodeEntity)=n.parts.filterNotNull().first()
    private fun fire(h: GameTestHelper,pos: BlockPos) {
        h.level.setBlockAndUpdate(pos.below(),Blocks.NETHERRACK.defaultBlockState());h.level.setBlockAndUpdate(pos,Blocks.FIRE.defaultBlockState())
    }
    private fun cow(h: GameTestHelper,pos: BlockPos)=EntityType.COW.create(h.level)!!.also {
        it.isNoAi=true;it.setNoGravity(true);it.moveTo(pos.x+.5,pos.y.toDouble(),pos.z+.5,0f,0f);h.level.addFreshEntity(it)
    }
    @JvmStatic @GameTest(batch="environment_sensors",template="grid_empty",timeoutTicks=170)
    fun humidityKeepsOriginalSparseThreeSecondFilterAndSavedControllerAcrossRebuild(h: GameTestHelper) {
        val n=device(h,"humidity_sensor");var p=part(n);val wire=device(h,"signal_cable",n.blockPos.south())
        h.runAtTickTime(100) {
            val relative=p.thermalState.getDouble("envMeasured")/100
            if(relative<=0 || p.voltage/5/relative !in .27.. .40 || abs(part(wire).voltage-p.voltage)>.002 || p.connectedMask!=2)h.fail("Humidity sensor lost its original sparse RC sampling or separate actual signal pin: ${p.save()}")
            val value=p.thermalState.getFloat("envRC1factorFiltered");val timer=p.thermalState.getDouble("envTimer")
            val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology();p=part(n)
            if(p.thermalState.getFloat("envRC1factorFiltered")!=value || p.thermalState.getDouble("envTimer")!=timer)h.fail("Humidity filter or sample phase was lost on node reload")
        }
        h.runAtTickTime(140) {
            if(p.voltage<=0 || abs(part(wire).voltage-p.voltage)>.002)h.fail("Restored humidity sensor did not resume its real electrical output")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="environment_sensors",template="grid_empty",timeoutTicks=110)
    fun actualAnemometerPlacementRejectsFloorAndDrivesOnlyItsWallSignalTerminal(h: GameTestHelper) {
        GameTestYards.clear(h,205,4,18);val pos=at(h);val player=h.makeMockPlayer();player.abilities.instabuild=false
        val held=ItemStack(Content.partItems.getValue("electrical_anemometer_sensor").get());player.setItemInHand(InteractionHand.MAIN_HAND,held)
        h.level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState())
        val floor=held.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(pos.below()).add(0.0,.5,0.0),Direction.UP,pos.below(),false)))
        if(floor.consumesAction() || held.count!=1)h.fail("Original wall anemometer was accepted on the floor")
        val support=pos.north();h.level.setBlockAndUpdate(support,Blocks.STONE.defaultBlockState());player.setPos(pos.x+.5,205.0,pos.z+2.5)
        val wall=held.useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(support).add(0.0,0.0,.5),Direction.SOUTH,support,false)))
        if(!wall.consumesAction() || !held.isEmpty)h.fail("Actual survival wall placement failed or did not consume the anemometer")
        val n=h.level.getBlockEntity(pos) as FaceNodeEntity;val p=part(n)
        if(p.face.axis==Direction.Axis.Y)h.fail("Anemometer wall placement lost its mounting side")
        val output=FaceBasis.edges(p)[1];val wire=device(h,"signal_cable",pos.relative(output),p.face)
        h.runAtTickTime(70) {
            val expected=WorldWind.speed(h.level,pos.y)
            if(p.voltage !in 4.9..5.001 || abs(part(wire).voltage-p.voltage)>.002 || abs(p.thermalState.getDouble("envMeasured")-expected)>.001 || p.connectedMask!=2)h.fail("Anemometer lost the original 25 m/s scale, actual pin or source height/weather behavior: ${p.save()}")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="environment_sensors",template="grid_empty",timeoutTicks=230)
    fun actualMovingEntityDrivesOriginalDoubleFilterAndStationaryEntityStopsIt(h: GameTestHelper) {
        val n=device(h,"electrical_entity_sensor");val p=part(n);device(h,"signal_cable",n.blockPos.south())
        val animal=cow(h,n.blockPos.east(3))
        h.runAtTickTime(40) { if(p.voltage>.01)h.fail("Motion sensor reported an unmoving living entity") }
        for(t in 45L..110L)h.runAtTickTime(t) { animal.setPos(n.blockPos.x+3.5+(if(t%8<4).5 else -.5),205.0,n.blockPos.z+.5) }
        h.runAtTickTime(115) {
            if(p.voltage<3 || !p.active)h.fail("Actual entity movement did not reach the original weighted double-RC output: ${p.save()}")
            n.changedTopology()
        }
        h.runAtTickTime(210) {
            if(p.voltage>.1 || p.active)h.fail("Stationary entity kept triggering the original movement sensor")
            animal.discard();h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="environment_sensors",template="grid_empty",timeoutTicks=290)
    fun realFilterInsertionSelectsAnimalsAndOpaqueWallBlocksTheirMovement(h: GameTestHelper) {
        val n=device(h,"electrical_entity_sensor");val p=part(n);val inv=PartInventory.get(n,p)
        inv.setStackInSlot(0,ItemStack(Content.materialItems.getValue("player_filter").get()))
        val animal=cow(h,n.blockPos.east(3))
        for(t in 1L..250L)h.runAtTickTime(t) { animal.setPos(n.blockPos.x+3.5+(if(t%8<4).5 else -.5),205.0,n.blockPos.z+.5) }
        h.runAtTickTime(55) {
            if(p.voltage>.01)h.fail("Player filter accepted animal movement")
            inv.extractItem(0,1,false)
            val player=h.makeMockPlayer();player.abilities.instabuild=false;val held=ItemStack(Content.materialItems.getValue("animal_filter").get());player.setItemInHand(InteractionHand.MAIN_HAND,held)
            n.blockState.use(h.level,player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(n.blockPos),Direction.UP,n.blockPos,false))
            if(!held.isEmpty || inv.getStackInSlot(0).item!=Content.materialItems.getValue("animal_filter").get())h.fail("Actual sensor block interaction did not consume and insert its entity filter")
        }
        h.runAtTickTime(135) {
            if(p.voltage<3 || !p.active)h.fail("Animal filter rejected visible animal movement")
            for(y in 205..208)for(z in -1..1)h.level.setBlockAndUpdate(n.blockPos.east(2).offset(0,y-205,z),Blocks.STONE.defaultBlockState())
        }
        h.runAtTickTime(260) {
            if(p.voltage>.1 || p.active)h.fail("Motion sensor saw moving animals through an opaque wall")
            if((0 until inv.slots).any { inv.isItemValid(it,ItemStack(Items.IRON_INGOT)) })h.fail("Entity filter slot accepted unrelated material")
            animal.discard();h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="environment_sensors",template="grid_empty",timeoutTicks=235)
    fun wiredFireSensorDetectsVisibleFlameThenDecaysBehindAnActualOpaqueWall(h: GameTestHelper) {
        val n=device(h,"electrical_fire_detector");val p=part(n);device(h,"signal_cable",n.blockPos.south());fire(h,n.blockPos.east(3))
        h.runAtTickTime(85) {
            if(!p.active || !p.thermalState.getBoolean("envFire") || p.voltage<4.8)h.fail("Original 15-block wired fire detector did not see actual visible fire")
            h.level.setBlockAndUpdate(n.blockPos.east(2),Blocks.STONE.defaultBlockState())
        }
        h.runAtTickTime(205) {
            if(p.active || p.thermalState.getBoolean("envFire") || p.voltage>.05)h.fail("Opaque wall did not block original fire sight and filtered signal decay")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="environment_sensors",template="grid_empty",timeoutTicks=245)
    fun batteryBuzzerConsumesFiniteStoredEnergySurvivesReloadAndStopsAtOriginalReserve(h: GameTestHelper) {
        val n=device(h,"electrical_fire_buzzer");var p=part(n);val player=h.makeMockPlayer();player.abilities.instabuild=false
        val battery=PortableEnergyGameTests.item("portable_battery",1.0);player.setItemInHand(InteractionHand.MAIN_HAND,battery)
        n.blockState.use(h.level,player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(n.blockPos),Direction.UP,n.blockPos,false))
        if(!battery.isEmpty || PartInventory.get(n,p).slots!=1 || PartInventory.get(n,p).isItemValid(0,PortableEnergyGameTests.item("small_flashlight",100.0)))h.fail("Actual battery buzzer insertion, one-slot limit or original portable-store-only contract failed")
        fire(h,n.blockPos.east(3))
        h.runAtTickTime(65) {
            val remaining=ElectricalItems.energy(PartInventory.get(n,p).getStackInSlot(0))
            if(!p.active || !p.thermalState.getBoolean("envPowered") || abs(p.energy+remaining-1)>1e-9)h.fail("Battery fire buzzer did not run on finite conserved original idle power")
            val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology();p=part(n)
        }
        h.runAtTickTime(210) {
            val remaining=ElectricalItems.energy(PartInventory.get(n,p).getStackInSlot(0))
            if(p.active || p.thermalState.getBoolean("envPowered") || p.thermalState.getBoolean("envFire") || remaining !in 0.0.. .027778 || abs(p.energy+remaining-1)>1e-9 || p.voltage!=0.0 || p.current!=0.0 || p.connectedMask!=0)h.fail("Depleted battery buzzer kept sounding, gained energy on reload or exposed an electrical terminal: ${p.save()}")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="environment_sensors",template="grid_empty",timeoutTicks=40)
    fun fireVolumeAndOpaqueSightQueriesNeverRequestMissingChunks(h: GameTestHelper) {
        val pos=BlockPos(20000000,205,20000000);val definition=PartDefinition.byId.getValue("electrical_fire_detector")
        val n=FaceNodeEntity(pos,Content.node.get().defaultBlockState());val p=PartState(definition,Direction.DOWN);n.parts[0]=p
        val circuit=LevelCircuit(h.level);val runtime=EnvironmentSensorRuntime(ElementRuntime(circuit,n,p))
        repeat(12) { runtime.beforeTick() };runtime.afterTick()
        if(p.thermalState.getBoolean("envFire") || LoadedRay.visible(h.level,Vec3.atCenterOf(pos),Vec3.atCenterOf(pos.east(10))))h.fail("Unavailable world cells became visible to an environmental sensor")
        for(x in -1..1)for(z in -1..1)if(h.level.chunkSource.getChunkNow((pos.x shr 4)+x,(pos.z shr 4)+z)!=null)h.fail("Environmental sensing requested a missing chunk")
        h.succeed()
    }
}
