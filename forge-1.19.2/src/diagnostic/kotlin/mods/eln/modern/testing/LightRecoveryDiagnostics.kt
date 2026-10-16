package mods.eln.modern.testing

import net.minecraft.gametest.framework.GameTestGenerator
import net.minecraft.gametest.framework.TestFunction
import net.minecraft.world.level.block.Rotation
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate

/** Development-only repeated real-light cases; excluded from ordinary builds and releases. */
@GameTestHolder("eln_lighting_qa") @PrefixGameTestTemplate(false)
object LightRecoveryDiagnostics {
    @JvmStatic @GameTestGenerator fun sources(): List<TestFunction> = ((0..11).map { i ->
        TestFunction("projected_light_recovery","eln_lighting_qa:projected_rays_$i","eln_lighting_qa:grid_empty",Rotation.NONE,125,0,true) { helper ->
            PortableLightGameTests.unselectedCarriedFlashlightProjectsBothOriginalRaysStopsAtSolidWallAndActuallyExpires(helper)
        }
    }+TestFunction("vanilla_relight_recovery","eln_lighting_qa:projected_and_vanilla","eln_lighting_qa:grid_empty",Rotation.NONE,240,0,true) { helper ->
        LightingGameTests.sectionUpdatesPreserveProjectedAndOrdinaryEmittersAndStillExpireTheProjection(helper)
    }+TestFunction("festive_power_recovery","eln_lighting_qa:festive_power","eln_lighting_qa:grid_empty",Rotation.NONE,205,0,true) { helper ->
        FestiveGameTests.allOriginalFestiveLoadsBillTheirSupplyBeyondNormalRangeKeepTheCachedProviderAndExtinguish(helper)
    }+TestFunction("festive_placement_recovery","eln_lighting_qa:festive_placement","eln_lighting_qa:grid_empty",Rotation.NONE,45,0,true) { helper ->
        FestiveGameTests.originalTreeReservationsRejectObstructionsAndSupportLossRemovesTheWholeAssembly(helper)
    }+TestFunction("festive_crafting_recovery","eln_lighting_qa:festive_crafting","eln_lighting_qa:grid_empty",Rotation.NONE,20,0,true) { helper ->
        FestiveGameTests.originalDecemberRecipesKeepTheirIngredientsCountsAndActualSeasonalRegistration(helper)
    }+TestFunction("water_flow_recovery","eln_lighting_qa:water_flow","eln_lighting_qa:grid_empty",Rotation.NONE,280,0,true) { helper ->
        RenewableGameTests.realFlowingWaterDrivesOriginalSavedDoubleFilterWhileStationaryAndDryIntakesDoNot(helper)
    }+TestFunction("incubator_recovery","eln_lighting_qa:incubator","eln_lighting_qa:grid_empty",Rotation.NONE,105,0,true) { helper ->
        IncubatorGameTests.savedPaidIncubationStopsWithoutPowerAndHatchesExactlyOneBabyForOneRealEgg(helper)
    }+TestFunction("incubator_automation_recovery","eln_lighting_qa:incubator_automation","eln_lighting_qa:grid_empty",Rotation.NONE,25,0,true) { helper ->
        IncubatorGameTests.originalIncubatorAutomationAcceptsEggsFromEverySideWithoutExtractingOrDuplicatingThem(helper)
    }+TestFunction("incubator_voltage_recovery","eln_lighting_qa:incubator_voltage","eln_lighting_qa:grid_empty",Rotation.NONE,55,0,true) { helper ->
        IncubatorGameTests.originalIncubatorVoltageWatchdogDestroysAnOvervoltedActualMachine(helper)
    }+TestFunction("dormant_light_recovery","eln_lighting_qa:dormant_light","eln_lighting_qa:grid_empty",Rotation.NONE,600,0,true) { helper ->
        ProjectedLightChunkGameTests.realProjectedLightExpiresInALoadedFullChunkWithoutNormalBlockOrEntityTicking(helper)
    }+TestFunction("auto_miner_power","eln_lighting_qa:auto_miner_power","eln_lighting_qa:grid_empty",Rotation.NONE,210,0,true) { h ->
        AutoMinerGameTests.originalRaisedPowerPortsPayForActualBlocksPipesAndDepthDependentOreScanAndSaveTheWork(h)
    }+TestFunction("auto_miner_silk","eln_lighting_qa:auto_miner_silk","eln_lighting_qa:grid_empty",Rotation.NONE,160,0,true) { h ->
        AutoMinerGameTests.fullRearChestBlocksMiningAndSilkTouchUsesOriginalSixfoldWorkWithThreefoldPower(h)
    }+TestFunction("auto_miner_pipes","eln_lighting_qa:auto_miner_pipes","eln_lighting_qa:grid_empty",Rotation.NONE,135,0,true) { h ->
        AutoMinerGameTests.removingTheDrillRetractsPaidPipesAndBreakingOneOwnedPipeRefundsTheRemainingColumnOnce(h)
    }+TestFunction("auto_miner_protection","eln_lighting_qa:auto_miner_protection","eln_lighting_qa:grid_empty",Rotation.NONE,80,0,true) { h ->
        AutoMinerGameTests.realForgeBreakCancellationAndEverySidedHardwareSlotPreserveBlocksAndIngredients(h)
    }+TestFunction("auto_miner_removal","eln_lighting_qa:auto_miner_removal","eln_lighting_qa:grid_empty",Rotation.NONE,95,0,true) { h ->
        AutoMinerGameTests.breakingTheActualBodyReturnsInstalledPipesAndContainedHardwareOnceAndInvalidatesOldAutomation(h)
    }+TestFunction("auto_miner_overvoltage","eln_lighting_qa:auto_miner_overvoltage","eln_lighting_qa:grid_empty",Rotation.NONE,60,0,true) { h ->
        AutoMinerGameTests.originalVoltageWatchdogDestroysAnActualOvervoltedMiner(h)
    }+TestFunction("transporter_trip","eln_lighting_qa:transporter_trip","eln_lighting_qa:grid_empty",Rotation.NONE,245,0,true) { h ->
        TransporterGameTests.actualNamedPairClosesBothDoorsPaysOriginalOccupantAndDistanceCostAndMovesRealEntitiesOnce(h)
    }+TestFunction("transporter_reload","eln_lighting_qa:transporter_reload","eln_lighting_qa:grid_empty",Rotation.NONE,255,0,true) { h ->
        TransporterGameTests.circuitRebuildRetainsChargeButActualNodeReloadResetsItsInterruptedTripWithoutFreeArrival(h)
    }+TestFunction("transporter_names","eln_lighting_qa:transporter_names","eln_lighting_qa:grid_empty",Rotation.NONE,100,0,true) { h ->
        TransporterGameTests.originalNameErrorsBusyReservationAndCraftingAreRealWithoutStartingUnpaidTrips(h)
    }+TestFunction("transporter_malfunction","eln_lighting_qa:transporter_malfunction","eln_lighting_qa:grid_empty",Rotation.NONE,250,0,true) { h ->
        TransporterGameTests.unstableActualChargingSupplyTriggersOriginalMalfunctionDiscardsPaidWorkAndNeverForcesRandomChunks(h)
    }+TestFunction("transporter_overvoltage","eln_lighting_qa:transporter_overvoltage","eln_lighting_qa:grid_empty",Rotation.NONE,60,0,true) { h ->
        TransporterGameTests.originalTransporterOvervoltageWatchdogDestroysItsActualMultiblock(h)
    }+TestFunction("turret_shot","eln_lighting_qa:turret_shot","eln_lighting_qa:grid_empty",Rotation.NONE,460,0,true) { h ->
        TurretGameTests.actualFiveDamageShotCostsOneThousandPaidJoulesAndAnOutageReloadCannotReplayIt(h)
    }+TestFunction("turret_filter","eln_lighting_qa:turret_filter","eln_lighting_qa:grid_empty",Rotation.NONE,430,0,true) { h ->
        TurretGameTests.invertedAnimalFilterSparesTheCowAndOnlyShootsAnotherVisibleLivingClassThroughGlass(h)
    }+TestFunction("turret_voltage","eln_lighting_qa:turret_voltage","eln_lighting_qa:grid_empty",Rotation.NONE,260,0,true) { h ->
        TurretGameTests.originalVoltageHysteresisAndFiveSecondRecoveryDelayControlActualArming(h)
    }+TestFunction("turret_protected","eln_lighting_qa:turret_protected","eln_lighting_qa:grid_empty",Rotation.NONE,430,0,true) { h ->
        TurretGameTests.actualForgeDamageCancellationStillDebitsTheOriginalShotAndCannotCreateFreeAttempts(h)
    }+TestFunction("turret_overvoltage","eln_lighting_qa:turret_overvoltage","eln_lighting_qa:grid_empty",Rotation.NONE,55,0,true) { h ->
        TurretGameTests.originalInstantOvervoltageFailureRemovesTheArmedActualTurret(h)
    }+TestFunction("turret_hardware","eln_lighting_qa:turret_hardware","eln_lighting_qa:grid_empty",Rotation.NONE,100,0,true) { h ->
        TurretGameTests.originalCraftingManualFilterReplacementAndAllSixAutomationSidesPreserveActualHardware(h)
    }+TestFunction("fuse_pickup","eln_lighting_qa:fuse_pickup","eln_lighting_qa:grid_empty",Rotation.NONE,110,0,true) { h ->
        ProtectionGameTests.actualFuseReplacementExtractionAndOrdinaryPickupConserveEveryInstalledItem(h)
    }+TestFunction("power_antenna_boundary","eln_lighting_qa:power_antenna_boundary","eln_lighting_qa:grid_empty",Rotation.NONE,210,0,true) { h ->
        PowerAntennaGameTests.originalTwoHundredBlockLimitIsInclusiveAndBeamScansNeverForceChunks(h)
    }+TestFunction("classic_lamp_recovery","eln_lighting_qa:classic_lamp","eln_lighting_qa:grid_empty",Rotation.NONE,320,0,true) { h ->
        LightingGameTests.realBulbLoadProjectedLightSavedLifetimeAndRemoval(h)
    }+TestFunction("fabricator_operations","eln_lighting_qa:fabricator_operations","eln_lighting_qa:grid_empty",Rotation.NONE,165,0,true) { h ->
        FabricatorGameTests.allFifteenOriginalChipOperationsConsumeRealWafersCopperAndFourKilojoulesIncludingAFailedAluBatch(h)
    }+TestFunction("fabricator_blocked","eln_lighting_qa:fabricator_blocked","eln_lighting_qa:grid_empty",Rotation.NONE,95,0,true) { h ->
        FabricatorGameTests.blockedFabricatorDoesNotConsumeInputsAndOperationSelectionDiscardsOnlyPreviouslyPaidProgress(h)
    }+TestFunction("fabricator_automation","eln_lighting_qa:fabricator_automation","eln_lighting_qa:grid_empty",Rotation.NONE,20,0,true) { h ->
        FabricatorGameTests.allSixOriginalAutomationSidesExposeOnlyTheCorrectInputsAndOutput(h)
    }+TestFunction("vanilla_control","eln_lighting_qa:vanilla_control","eln_lighting_qa:grid_empty",Rotation.NONE,120,0,true) { h ->
        GameTestYards.clear(h,270,2,6)
        val base=h.absolutePos(net.minecraft.core.BlockPos.ZERO)
        val a=net.minecraft.core.BlockPos(base.x+4,270,base.z+8);val b=a.east(6)
        for(at in listOf(a,b)) {
            h.level.setBlockAndUpdate(at.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState())
            h.level.setBlockAndUpdate(at,net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState())
        }
        for(tick in 1L..100L)h.runAtTickTime(tick) {
            if(listOf(a,b).all { h.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,it)==14 })h.succeed()
            else if(tick>=100)h.fail("Pure vanilla two-torch control failed: ${listOf(a,b).map { mods.eln.modern.lighting.LightRecovery.describe(h.level,it) }}")
        }
    }+(listOf(NodeCoverGameTests::class.java,BatteryVariantGameTests::class.java,DiodeGameTests::class.java,SensorGameTests::class.java,SimpleSignalGameTests::class.java,PortGameTests::class.java,SourceProcessingGameTests::class.java,ComputerProbeGameTests::class.java,TutorialSignGameTests::class.java,ResourceCompletionGameTests::class.java,ModbusGameTests::class.java,MqttGameTests::class.java,HeatExchangerGameTests::class.java,SignalDeviceGameTests::class.java,ReplicatorGameTests::class.java,SharedToolGameTests::class.java,DataLoggerGameTests::class.java,EnergyMeterGameTests::class.java,ThermalGameTests::class.java)+listOfNotNull(runCatching { Class.forName("mods.eln.modern.testing.CcProbeGameTests") }.getOrNull())).flatMap { type ->
        type.methods.mapNotNull { method ->
            val annotation=method.getAnnotation(net.minecraft.gametest.framework.GameTest::class.java) ?: return@mapNotNull null
            val prefix=when(type) { NodeCoverGameTests::class.java->"node_cover_";BatteryVariantGameTests::class.java->"battery_variants_";DiodeGameTests::class.java->"diode_";SensorGameTests::class.java->"sensor_";SimpleSignalGameTests::class.java->"simple_signal_";PortGameTests::class.java->"port_";SourceProcessingGameTests::class.java->"source_processing_";ComputerProbeGameTests::class.java->"computer_probe_";TutorialSignGameTests::class.java->"tutorial_";ResourceCompletionGameTests::class.java->"resources_";ModbusGameTests::class.java->"modbus_";MqttGameTests::class.java->"mqtt_";HeatExchangerGameTests::class.java->"exchanger_";SignalDeviceGameTests::class.java->"signal_device_";ReplicatorGameTests::class.java->"replicator_";DataLoggerGameTests::class.java->"logger_";EnergyMeterGameTests::class.java->"energy_meter_";ThermalGameTests::class.java->"thermal_";else->if(type.simpleName=="CcProbeGameTests")"cc_probe_" else "shared_tool_" }
            TestFunction(if(annotation.batch!="defaultBatch")annotation.batch else "source_restoration", "eln_lighting_qa:"+prefix+method.name.lowercase(),"eln_lighting_qa:grid_empty",Rotation.NONE,annotation.timeoutTicks,0,true) { h -> method.invoke(null,h) }
        }
    }).filter { test ->
        val wanted=System.getProperty("eln.qaCases","").split(',').filter { it.isNotBlank() }
        wanted.isEmpty() || wanted.any { test.testName.substringAfter(':').startsWith(it) }
    }
}
