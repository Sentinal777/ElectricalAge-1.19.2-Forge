package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object SharedToolGameTests {
    private class Yard(val h: GameTestHelper) {
        val at=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+8,295,it.z+8) }
        init { GameTestYards.clear(h,295,2,4) }
        fun node(id: String,x: Int=0,z: Int=0): FaceNodeEntity {
            val p=at.offset(x,0,z);h.level.setBlockAndUpdate(p.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(p,Content.node.get().defaultBlockState())
            return (h.level.getBlockEntity(p) as FaceNodeEntity).also { it.parts[0]=PartState(PartDefinition.byId.getValue(id),Direction.DOWN);it.changedTopology() }
        }
        fun player()=h.makeMockPlayer().also { it.abilities.instabuild=false;it.setPos(at.x+.5,295.0,at.z+2.5) }
    }
    @JvmStatic @GameTest(batch="original_shared_copy",template="grid_empty",timeoutTicks=30)
    fun actualSneakCopyAndApplyPreserveRuntimeEnergyWhileMovingOnlyAvailableCapacitorHardware(h: GameTestHelper) {
        val y=Yard(h);val source=y.node("power_capacitor",-2);val target=y.node("power_capacitor",2)
        val a=source.parts[0]!!;val b=target.parts[0]!!;a.energy=8000.0;b.energy=13.0;b.progress=7.0
        PartInventory.get(source,a).setStackInSlot(0,ItemStack(Items.REDSTONE,5));PartInventory.get(source,a).setStackInSlot(1,ItemStack(Content.materialItems.getValue("dielectric").get(),4))
        PartInventory.get(target,b).setStackInSlot(0,ItemStack(Items.REDSTONE,1))
        val player=y.player();val tool=ItemStack(Content.materialItems.getValue("config_copy_tool").get());player.setItemInHand(InteractionHand.MAIN_HAND,tool)
        player.inventory.setItem(9,ItemStack(Items.REDSTONE,2));player.inventory.setItem(10,ItemStack(Content.materialItems.getValue("dielectric").get(),2))
        player.isShiftKeyDown=true
        source.blockState.use(h.level,player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(source.blockPos),Direction.UP,source.blockPos,false))
        player.isShiftKeyDown=false
        target.blockState.use(h.level,player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(target.blockPos),Direction.UP,target.blockPos,false))
        val clipboard=tool.tag?.getCompound("ElnConfiguration") ?: h.fail("Real sneak-copy gesture did not fill tool NBT").let { return }
        if(clipboard.contains("Energy") || clipboard.contains("energy") || clipboard.contains("progress") || b.energy!=13.0 || b.progress!=7.0 || PartInventory.peek(b,0).count!=3 || PartInventory.peek(b,1).count!=2 || PartInventory.peek(a,0).count!=5 || PartInventory.peek(a,1).count!=4 || !player.inventory.getItem(9).isEmpty || !player.inventory.getItem(10).isEmpty)
            h.fail("Configuration copier cloned runtime energy, cloned missing hardware or consumed its source component: clip=$clipboard, a=${a.save()}, b=${b.save()}")
        h.succeed()
    }
    @JvmStatic @GameTest(batch="original_shared_nbt",template="grid_empty",timeoutTicks=30)
    fun replacingARealBulbReturnsItsActualLifeAndConsumesThePlayersActualUsedReplacementWithoutCopyingBlueprintLife(h: GameTestHelper) {
        val y=Yard(h);val source=y.node("basic_floodlight",-2);val target=y.node("basic_floodlight",2)
        val a=source.parts[0]!!;val b=target.parts[0]!!;val bulbs=Materials.all.filter { it.bulb?.socket=="TUBE" }
        val selected=Content.materialItems.getValue(bulbs[0].id).get();val other=Content.materialItems.getValue(bulbs[1].id).get()
        PartInventory.get(source,a).setStackInSlot(0,ItemStack(selected).apply { orCreateTag.putDouble("life",500.0) })
        PartInventory.get(source,a).setStackInSlot(1,ItemStack(selected).apply { orCreateTag.putDouble("life",500.0) })
        PartInventory.get(target,b).setStackInSlot(0,ItemStack(other).apply { orCreateTag.putDouble("life",7.0) })
        val player=y.player();player.inventory.setItem(9,ItemStack(selected).apply { orCreateTag.putDouble("life",12.0) })
        val clip=CompoundTag();if(!ConfigCopyTool.copy(source,a,clip) || !ConfigCopyTool.apply(target,b,clip,player))h.fail("Original floodlight clipboard unsupported")
        val installed=PartInventory.peek(b,0)
        val returned=(0 until player.inventory.containerSize).map { player.inventory.getItem(it) }.filter { it.item===other }
        if(installed.item!==selected || installed.tag?.getDouble("life")!=12.0 || !PartInventory.peek(b,1).isEmpty || returned.sumOf { it.count }!=1 || returned.single().tag?.getDouble("life")!=7.0 || PartInventory.peek(a,0).tag?.getDouble("life")!=500.0)
            h.fail("Clipboard invented a second bulb, refreshed bulb life or lost the old bulb's actual NBT")
        h.succeed()
    }
    @JvmStatic @GameTest(batch="original_shared_settings",template="grid_empty",timeoutTicks=30)
    fun clipboardMergesOriginalChannelAndRangeSettingsAndRejectsNonFiniteValuesWithoutChangingStoredEnergy(h: GameTestHelper) {
        val y=Yard(h);val source=y.node("electrical_source",-3);val target=y.node("electrical_source",3)
        val a=source.parts[0]!!;val b=target.parts[0]!!;a.value=123.0;a.energy=100000.0;b.energy=7.0
        val player=y.player();val clip=CompoundTag();ConfigCopyTool.copy(source,a,clip)
        if(!ConfigCopyTool.apply(target,b,clip,player) || b.value!=123.0 || b.energy!=7.0)h.fail("Original source voltage copy changed runtime energy")
        clip.putDouble("voltage",Double.NaN);ConfigCopyTool.apply(target,b,clip,player)
        if(b.value!=123.0)h.fail("Copier accepted a nonfinite source value")
        val rx=y.node("wireless_signal_receiver",0,-3);rx.parts[0]!!.channel="actual-copy-channel"
        ConfigCopyTool.copy(rx,rx.parts[0]!!,clip);val tx=y.node("wireless_signal_transmitter",0,3)
        if(!ConfigCopyTool.apply(tx,tx.parts[0]!!,clip,player) || tx.parts[0]!!.channel!="actual-copy-channel" || !clip.contains("voltage"))h.fail("Source clipboard did not merge or share original wireless channel settings")
        val meter=PortableInstruments.meter(b).string
        if(!meter.contains("V") || !meter.contains("A") || !meter.contains("°C") || Content.materialItems.getValue("allmeter").get().maxStackSize!=1)h.fail("Combined AllMeter lost electrical/temperature readings or tool stack limit")
        h.succeed()
    }
    @JvmStatic @GameTest(batch="original_shared_radio",template="grid_empty",timeoutTicks=90)
    fun analyserReportsUniqueLoadedTransmittersPerChannelWithTheOriginalWeakestStrengthAndMaximumValue(h: GameTestHelper) {
        val y=Yard(h);val a=y.node("wireless_switch",-4);val b=y.node("wireless_switch",4)
        val channel="analyser-${y.at.asLong()}-${h.level.gameTime}"
        a.parts[0]!!.apply { this.channel=channel;enabled=true };b.parts[0]!!.apply { this.channel=channel;enabled=false }
        h.runAtTickTime(40) {
            val before=h.level.forcedChunks.size
            val result=PortableInstruments.analyse(h.level,y.at).singleOrNull { it.name==channel } ?: h.fail("Actual analyser omitted reachable switches").let { return@runAtTickTime }
            if(result.transmitters!=2 || result.value!=1.0 || abs(result.strength-(mods.eln.config.ForgePortConfig.wirelessRange.get()-4.0))>1e-5 || h.level.forcedChunks.size!=before)h.fail("Analyser changed original weakest-link/maximum-value semantics: $result")
            a.parts[0]!!.enabled=false
        }
        h.runAtTickTime(65) {
            if(PortableInstruments.analyse(h.level,y.at).single { it.name==channel }.value!=0.0)h.fail("Analyser cached an old transmitter value")
            h.succeed()
        }
    }
}
