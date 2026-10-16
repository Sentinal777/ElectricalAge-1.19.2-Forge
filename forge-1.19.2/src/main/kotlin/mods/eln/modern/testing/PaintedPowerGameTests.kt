package mods.eln.modern.testing

import mods.eln.modern.*
import mods.eln.modern.lighting.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object PaintedPowerGameTests {
    private class Yard(val h: GameTestHelper,val wall: Boolean=false) {
        val base=h.absolutePos(BlockPos.ZERO)
        init { GameTestYards.clear(h,245,2,5) }
        fun node(id: String,x: Int,z: Int=8,rotation: Int=0,color: Int=0): FaceNodeEntity {
            val pos=BlockPos(base.x+x,245,base.z+z);val face=if(wall)Direction.NORTH else Direction.DOWN
            h.level.setBlockAndUpdate(pos.relative(face),Blocks.STONE.defaultBlockState())
            h.level.setBlockAndUpdate(pos,Content.node.get().defaultBlockState())
            return (h.level.getBlockEntity(pos) as FaceNodeEntity).also { n ->
                n.parts[face.get3DDataValue()]=PartState(PartDefinition.byId.getValue(id),face).apply { this.rotation=rotation;gridState.putInt("paintColor",color) };n.changedTopology()
            }
        }
        fun part(n: FaceNodeEntity)=n.parts.filterNotNull().single()
        fun board(x: Int,channel: String,enabled: Boolean=true): FaceNodeEntity=node("lamp_supply",x).also { n ->
            val p=part(n);PartInventory.get(n,p).setStackInSlot(0,ItemStack(Content.partItems.getValue("low_voltage_cable").get()))
            LampChannels.write(p,listOf(LampChannel(channel,if(enabled)"true" else "false",0)));n.changedTopology()
        }
    }
    @JvmStatic @GameTest(batch="original_painted_power",template="grid_empty",timeoutTicks=120)
    fun originalSocketsUseNearestSupplyAndFiniteResistivePowerAcrossChannelDisableAndReload(h: GameTestHelper) {
        val y=Yard(h,true);val channel="socket-${y.base.asLong()}-${h.level.gameTime}"
        val board=y.board(2,channel);y.part(y.node("electrical_source",3)).value=50.0
        var socket=y.node("type_j_socket",9);y.part(socket).channel=channel
        val resistor=y.node("creative_power_resistor",10);y.part(resistor).value=20.0;y.node("ground_cable",11)
        var billed=0.0
        h.runAtTickTime(20) {
            val p=y.part(socket);val bp=y.part(board)
            if(!p.active || p.voltage !in 48.0..50.0 || abs(y.part(resistor).current) !in 2.4..2.5 || bp.current !in 2.4..2.5 || bp.energy<20.0)h.fail("Socket failed to draw real current through its lamp-supply connection: ${p.save()}, board=${bp.save()}")
            if(bp.energy<y.part(resistor).energy || bp.energy>y.part(resistor).energy*1.03)h.fail("Socket/supply/resistor finite energy ledger disagrees")
            val tag=socket.saveWithoutMetadata();socket.load(tag);socket.changedTopology()
        }
        h.runAtTickTime(35) {
            if(!y.part(socket).active || y.part(socket).channel!=channel)h.fail("Socket channel or real connection was lost on reload")
            // The source chooses the nearest provider even when that provider's channel is off.
            y.board(7,channel,false)
        }
        h.runAtTickTime(50) {
            if(y.part(socket).active || abs(y.part(resistor).current)>1e-6)h.fail("Disabled nearer lamp supply was incorrectly bypassed for an active farther provider")
            billed=y.part(board).energy
            h.level.destroyBlock(BlockPos(y.base.x+7,245,y.base.z+8),false)
        }
        h.runAtTickTime(65) {
            if(!y.part(socket).active || y.part(board).energy<=billed+20.0)h.fail("Removing nearer disabled supply failed to reconnect and bill actual power")
            y.part(socket).channel="unmatched";socket.changedTopology()
        }
        h.runAtTickTime(80) {
            if(y.part(socket).active || abs(y.part(resistor).current)>1e-6)h.fail("Socket continued generating power after its supply channel was removed")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_painted_power",template="grid_empty",timeoutTicks=70)
    fun originalTypeESocketBackfeedsTheSupplyAndActualWirelessLampWithoutInventedEnergy(h: GameTestHelper) {
        val y=Yard(h,true);val channel="backfeed-${y.base.asLong()}-${h.level.gameTime}"
        val board=y.board(2,channel)
        val resistor=y.node("creative_power_resistor",3);y.part(resistor).value=100.0;y.node("ground_cable",4)
        val socket=y.node("type_e_socket",9);y.part(socket).channel=channel
        val source=y.node("electrical_source",10);y.part(source).value=50.0
        val lamp=y.node("classic_lamp_socket",14);val lp=y.part(lamp);lp.enabled=false;lp.channel=channel
        PartInventory.get(lamp,lp).apply {
            setStackInSlot(0,ItemStack(Content.materialItems.getValue("50v_led_bulb").get()))
            setStackInSlot(1,ItemStack(Content.partItems.getValue("low_voltage_cable").get()))
        }
        h.runAtTickTime(40) {
            val bp=y.part(board)
            if(bp.voltage !in 49.0..50.0 || abs(y.part(resistor).current) !in .49.. .5 || lp.signal<13 || lp.energy<20.0 || !y.part(socket).active)h.fail("Original symmetric socket link failed to backfeed a real supply, wired load and wireless lamp: board=${bp.save()}, lamp=${lp.save()}")
            if(bp.energy<lp.energy || y.part(socket).energy<lp.energy+y.part(resistor).energy)h.fail("Backfed supply or lamp duplicated/misreported its finite energy")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_painted_power",template="grid_empty",timeoutTicks=80)
    fun coloredSignalBusCarriesIndependentSimultaneousSignalsAndNeutralOnlyAddressesBlack(h: GameTestHelper) {
        val y=Yard(h);val buses=(4..10).map { y.node("signal_bus_cable",it) }
        y.part(y.node("signal_source",4,6)).value=1.5;y.node("signal_cable",4,7,color=1)
        y.part(y.node("signal_source",7,6)).value=4.0;y.node("signal_cable",7,7,color=4)
        val red=y.node("signal_cable",10,9,color=1);val blue=y.node("signal_cable",8,9,color=4)
        val neutral=y.node("wireless_signal_transmitter",6,9,rotation=3)
        h.runAtTickTime(30) {
            if(abs(y.part(red).voltage-1.5)>.001 || abs(y.part(blue).voltage-4)>.001 || abs(y.part(neutral).voltage)>1e-6)h.fail("Signal bus shorted channels or connected all channels to a neutral gate")
            for((i,n) in buses.withIndex()) {
                val v=y.part(n).gridState.getLongArray("busVoltages").map(Double::fromBits)
                if(v.size!=16 || abs(v[1]-1.5)>.001 || abs(v[4]-4)>.001 || v.withIndex().any { it.index !in setOf(1,4) && abs(it.value)>1e-6 })h.fail("Bus-to-bus connection lost individual loads: $v")
                if(i==2)y.part(n).gridState.putInt("paintColor",1)
                if(i==4)y.part(n).gridState.putInt("paintColor",4)
                val tag=n.saveWithoutMetadata();n.load(tag);n.changedTopology()
            }
        }
        h.runAtTickTime(50) {
            if(abs(y.part(red).voltage-1.5)>.001 || abs(y.part(blue).voltage-4)>.001)h.fail("Reload merged or disconnected signal-bus channels")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_painted_power",template="grid_empty",timeoutTicks=170)
    fun everyOriginalDyeSelectsItsOwnOneOfSixteenSignalBusLoads(h: GameTestHelper) {
        val y=Yard(h);val bus=y.node("signal_bus_cable",8);val cable=y.node("signal_cable",7);y.part(y.node("signal_source",6)).value=3.25
        for(color in 0..15) {
            h.runAtTickTime((10+color*8).toLong()) { y.part(cable).gridState.putInt("paintColor",color);cable.changedTopology() }
            h.runAtTickTime((15+color*8).toLong()) {
                val values=y.part(bus).gridState.getLongArray("busVoltages").map(Double::fromBits)
                if(values.size!=16 || abs(values[color]-3.25)>.001 || values.withIndex().any { it.index!=color && abs(it.value)>1e-6 })h.fail("Original dye $color did not select exactly its own signal-bus load: $values")
                if(color==15)h.succeed()
            }
        }
    }
    @JvmStatic @GameTest(batch="original_painted_power",template="grid_empty",timeoutTicks=105)
    fun actualBrushAndWrenchInteractionsEnforceColorCareWithoutChargingSameColorOrDryBrush(h: GameTestHelper) {
        val y=Yard(h);val source=y.node("signal_source",6);y.part(source).value=5.0
        val a=y.node("signal_cable",7);val b=y.node("signal_cable",8)
        val player=h.makeMockPlayer();player.abilities.instabuild=false
        fun click(n: FaceNodeEntity,stack: ItemStack) {
            player.setItemInHand(InteractionHand.MAIN_HAND,stack);player.setPos(n.blockPos.x+.5,246.0,n.blockPos.z+2.0)
            val point=PartGeometry.body(y.part(n)).center.add(Vec3.atLowerCornerOf(n.blockPos))
            n.blockState.use(h.level,player,InteractionHand.MAIN_HAND,BlockHitResult(point,Direction.UP,n.blockPos,false))
        }
        val brush=ItemStack(Content.materialItems.getValue("red_brush").get())
        h.runAtTickTime(15) {
            if(y.part(b).voltage<4.9)h.fail("Unpainted default cables did not connect")
            click(b,brush);click(b,brush)
            if(Brushes.life(brush)!=31 || brush.count!=1 || PaintedWiring.color(y.part(b))!=1)h.fail("Brush consumed an item or charged the unchanged color")
        }
        h.runAtTickTime(30) {
            if(abs(y.part(b).voltage)>1e-6)h.fail("Different cared colors remained electrically connected")
            click(a,ItemStack(Content.wrench.get()))
        }
        h.runAtTickTime(45) {
            if(y.part(b).voltage<4.9 || PaintedWiring.cares(y.part(a)))h.fail("Wrench did not restore connection using the original two-sided color-care rule")
            val dry=ItemStack(Content.materialItems.getValue("blue_brush").get()).apply { orCreateTag.putInt("life",0) };click(b,dry)
            if(PaintedWiring.color(y.part(b))!=1 || Brushes.life(dry)!=0)h.fail("Dry brush changed color or gained uses")
            player.abilities.instabuild=true;click(b,dry)
            if(PaintedWiring.color(y.part(b))!=4 || Brushes.life(dry)!=0)h.fail("Creative painting did not preserve infinite paint behavior")
            val saved=b.saveWithoutMetadata();b.load(saved);b.changedTopology()
            if(PaintedWiring.color(y.part(b))!=4 || PaintedWiring.cares(y.part(a)))h.fail("Paint or color-care state was not saved")
            if(PartPlacement.fits(PartState(PartDefinition.byId.getValue("type_j_socket"),Direction.DOWN),null))h.fail("Wall socket could be placed on the floor")
        }
        h.runAtTickTime(60) { if(y.part(b).voltage<4.9)h.fail("Reload lost a color-agnostic connection");h.succeed() }
    }
    @JvmStatic @GameTest(batch="original_painted_power",template="grid_empty",timeoutTicks=65)
    fun bothOriginalSocketTypesKeepTheSameThreeHundredVoltWatchdogAndPhysicalFailure(h: GameTestHelper) {
        val y=Yard(h,true)
        val sockets=listOf("type_j_socket","type_e_socket").mapIndexed { i,id ->
            val z=3+i*10;val channel="socket-voltage-${y.base.asLong()}-$i"
            val board=y.node("lamp_supply",2,z);val bp=y.part(board)
            PartInventory.get(board,bp).setStackInSlot(0,ItemStack(Content.partItems.getValue("very_high_voltage_cable").get()))
            LampChannels.write(bp,listOf(LampChannel(channel,"true",0)));board.changedTopology()
            y.part(y.node("electrical_source",3,z)).value=800.0
            y.node(id,9,z).also { y.part(it).channel=channel }
        }
        h.runAtTickTime(2) { if(sockets.any { h.level.getBlockState(it.blockPos).block!=Content.node.get() })h.fail("Socket skipped the original voltage watchdog's first-excursion/tolerance delay") }
        h.runAtTickTime(45) {
            if(sockets.any { h.level.getBlockState(it.blockPos).block==Content.node.get() })h.fail("An original socket survived sustained 800 V despite its shared 300 V nominal watchdog")
            h.succeed()
        }
    }
}
