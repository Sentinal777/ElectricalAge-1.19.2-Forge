package mods.eln.modern.testing

import com.google.gson.JsonParser
import mods.eln.modern.*
import mods.eln.modern.grid.GridMultiblock
import mods.eln.modern.lighting.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.CraftingContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.crafting.RecipeManager
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import net.minecraftforge.registries.ForgeRegistries
import java.util.Calendar
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object FestiveGameTests {
    private class Yard(val h: GameTestHelper) {
        val base=h.absolutePos(BlockPos.ZERO)
        init { GameTestYards.clear(h,250,2,5);GameTestYards.clear(h,300,2,8) }
        fun node(id: String,x: Int,z: Int=8,y: Int=300,rotation: Int=0): FaceNodeEntity {
            val at=BlockPos(base.x+x,y,base.z+z);val d=PartDefinition.byId.getValue(id)
            h.level.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState())
            if(id=="string_lights")h.level.setBlockAndUpdate(at.north(),Blocks.STONE.defaultBlockState())
            h.level.setBlockAndUpdate(at,(if(d.machine)Content.machine.get()else Content.node.get()).defaultBlockState())
            return (h.level.getBlockEntity(at) as FaceNodeEntity).also { n ->
                n.parts[0]=PartState(d,Direction.DOWN).apply { this.rotation=rotation }
                GridMultiblock.install(n,n.parts[0]!!);n.changedTopology()
            }
        }
        fun board(x: Int,y: Int=250,on: Boolean=true): FaceNodeEntity=node("lamp_supply",x,y=y,rotation=2).also { n ->
            PartInventory.get(n,n.parts[0]!!).setStackInSlot(0,ItemStack(Content.partItems.getValue("medium_voltage_cable").get()))
            LampChannels.write(n.parts[0]!!,listOf(LampChannel("xmas",if(on)"true" else "false",0)));n.changedTopology()
        }
    }
    @JvmStatic @GameTest(batch="original_festive_power",template="grid_empty",timeoutTicks=205)
    fun allOriginalFestiveLoadsBillTheirSupplyBeyondNormalRangeKeepTheCachedProviderAndExtinguish(h: GameTestHelper) {
        val y=Yard(h);val source=y.node("electrical_source",1,y=250).parts[0]!!.apply { value=180.0 }
        val board=y.board(2);val bp=board.parts[0]!!
        val fixtures=listOf("christmas_tree","holiday_candle","string_lights").mapIndexed { i,id -> y.node(id,6+i*4) }
        val ids=fixtures.map { it.parts[0]!!.definition.legacyId }
        if(ids!=listOf(4416,4417,4418))h.fail("Original festive save IDs changed")
        var near: FaceNodeEntity?=null
        h.runAtTickTime(20) {
            if(fixtures.any { it.parts[0]!!.signal!=0 || it.parts[0]!!.active })h.fail("The original 180 V light threshold changed")
            source.value=180.6
        }
        h.runAtTickTime(35) {
            if(fixtures.any { it.parts[0]!!.signal!=5 || !it.parts[0]!!.active })h.fail("Source factor-of-sixteen light curve or >4 render threshold changed: ${fixtures.map { it.parts[0]!!.save() }}")
            source.value=200.0
        }
        h.runAtTickTime(60) {
            for(n in fixtures) {
                val p=n.parts[0]!!
                if(p.signal!=15 || !p.active || abs(p.voltage-200)>.05 || abs(p.current-.2)>.001 || h.level.getBrightness(LightLayer.BLOCK,n.blockPos)!=15)h.fail("Festive decoration lacks real light or original 1 kΩ wireless load: ${p.save()}")
                if(n.blockPos.distSqr(board.blockPos)<=33*33)h.fail("Fixture did not exercise original unlimited festive range")
            }
            if(abs(bp.voltage*bp.current-120)>.1 || abs(bp.energy-fixtures.sumOf { it.parts[0]!!.energy })>1e-4)h.fail("Festive demand was not paid by the real shared lamp-supply resistor")
            near=y.board(9,y=300,on=false)
        }
        h.runAtTickTime(80) {
            if(fixtures.any { it.parts[0]!!.signal!=15 || it.parts[0]!!.gridState.getString("festiveSupply")!=bp.gridId })h.fail("New nearer provider incorrectly replaced a still-valid cached festive handle")
            fixtures.forEach { n -> val saved=n.saveWithoutMetadata();n.load(saved);n.changedTopology() }
        }
        h.runAtTickTime(100) {
            if(fixtures.any { it.parts[0]!!.signal!=15 || it.parts[0]!!.gridState.getString("festiveSupply")!=bp.gridId })h.fail("Circuit rebuild/reload lost retained festive provider")
            LampChannels.write(bp,listOf(LampChannel("other","true",0)));board.changedTopology()
        }
        h.runAtTickTime(120) {
            if(fixtures.any { it.parts[0]!!.signal!=0 || it.parts[0]!!.active })h.fail("Disabled nearest provider was bypassed after the cached handle disappeared")
            LampChannels.write(bp,listOf(LampChannel("xmas","true",0)));board.changedTopology()
        }
        h.runAtTickTime(140) {
            if(fixtures.any { it.parts[0]!!.signal!=0 })h.fail("Returning farther provider replaced the retained disabled closer handle")
            h.level.destroyBlock(near!!.blockPos,false)
        }
        h.runAtTickTime(165) {
            if(fixtures.any { it.parts[0]!!.signal!=15 })h.fail("Removing invalid closest provider did not restore paid festive lighting")
            source.enabled=false;board.changedTopology()
        }
        h.runAtTickTime(190) {
            if(fixtures.any { it.parts[0]!!.signal!=0 || it.parts[0]!!.active || h.level.getBrightness(LightLayer.BLOCK,it.blockPos)>0 })h.fail("Unpowered festive carrier retained actual emitted light")
            LampChannels.write(bp,emptyList());board.changedTopology();h.succeed()
        }
    }
    @JvmStatic @GameTest(batch="original_festive_placement",template="grid_empty",timeoutTicks=45)
    fun originalTreeReservationsRejectObstructionsAndSupportLossRemovesTheWholeAssembly(h: GameTestHelper) {
        val y=Yard(h);val tree=y.node("christmas_tree",8);val p=tree.parts[0]!!
        val cells=GridMultiblock.cells(tree.blockPos,p)
        if(cells.size!=19 || cells.toSet().size!=19 || cells.any { (h.level.getBlockEntity(it) as? mods.eln.modern.grid.GridGhostEntity)?.ownerId!=p.gridId })h.fail("Original tree's nineteen reserved cells were not installed")
        val player=h.makeMockPlayer();val saved=tree.saveWithoutMetadata();tree.load(saved);tree.changedTopology()
        h.runAtTickTime(5) {
            if(!GridMultiblock.rotate(tree,tree.parts[0]!!,player))h.fail("Clear tree could not rotate its original footprint")
            val updated=GridMultiblock.cells(tree.blockPos,tree.parts[0]!!)
            if(updated==cells || updated.any { (h.level.getBlockEntity(it) as? mods.eln.modern.grid.GridGhostEntity)?.ownerId!=p.gridId })h.fail("Tree rotation lost ownership or reserved the wrong orientation")
            h.level.destroyBlock(tree.blockPos.below(),false)
        }
        h.runAtTickTime(10) {
            if(h.level.getBlockEntity(tree.blockPos) is FaceNodeEntity || (cells+GridMultiblock.cells(tree.blockPos,tree.parts[0] ?: p)).any { h.level.getBlockState(it).block==Content.gridGhost.get() })h.fail("Tree support loss left a root or ghost block")
            val at=BlockPos(y.base.x+3,300,y.base.z+3);val candidate=PartState(PartDefinition.byId.getValue("christmas_tree"),Direction.DOWN)
            val blocked=GridMultiblock.cells(at,candidate).first();h.level.setBlockAndUpdate(blocked,Blocks.STONE.defaultBlockState())
            if(GridMultiblock.canPlace(h.level,at,candidate,player))h.fail("Tree placement ignored an occupied reserved cell")
            val string=y.node("string_lights",14,z=3)
            h.level.destroyBlock(string.blockPos.below(),false)
            if(!PartPlacement.canMount(h.level,string.blockPos,string.parts[0]!!))h.fail("String lights incorrectly require a floor")
            h.level.destroyBlock(string.blockPos.north(),false)
            if(h.level.getBlockEntity(string.blockPos) is FaceNodeEntity)h.fail("Wall-less string lights were not removed")
            val candleAt=BlockPos(y.base.x+14,300,y.base.z+13)
            if(PartPlacement.canMount(h.level,candleAt,PartState(PartDefinition.byId.getValue("holiday_candle"),Direction.DOWN)))h.fail("Candle accepted an unsupported placement")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=20)
    fun originalDecemberRecipesKeepTheirIngredientsCountsAndActualSeasonalRegistration(h: GameTestHelper) {
        val patterns=listOf(
            Triple("christmas_tree",listOf("LSL"),mapOf('L' to "eln:string_lights",'S' to "minecraft:spruce_sapling")),
            Triple("holiday_candle",listOf(" g ","gbg"," i "),mapOf('g' to "minecraft:glass_pane",'b' to "eln:200v_led_bulb",'i' to "minecraft:iron_ingot")),
            Triple("string_lights",listOf("BS"),mapOf('B' to "eln:200v_led_bulb",'S' to "minecraft:string")))
        val menu=object : AbstractContainerMenu(null,0) {
            override fun stillValid(player: Player)=true
            override fun quickMoveStack(player: Player,index: Int)=ItemStack.EMPTY
        }
        for((id,pattern,keys) in patterns) {
            val json=FestiveGameTests::class.java.getResourceAsStream("/data/eln/recipes/$id.json")!!.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
            val wrapper=json.getAsJsonArray("recipes")[0].asJsonObject
            if(wrapper.getAsJsonArray("conditions")[0].asJsonObject.get("type").asString!="eln:festive_season")h.fail("Original festive recipe lost its December condition")
            val recipe=RecipeManager.fromJson(ResourceLocation("eln",id),wrapper.getAsJsonObject("recipe")) as net.minecraft.world.item.crafting.CraftingRecipe
            val grid=CraftingContainer(menu,3,3)
            pattern.forEachIndexed { z,row -> row.forEachIndexed { x,key -> if(key!=' ')grid.setItem(z*3+x,ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation(keys.getValue(key)))!!)) } }
            if(!recipe.matches(grid,h.level))h.fail("Original festive ingredients do not match $id")
            val output=recipe.assemble(grid)
            if(output.item!=Content.partItems.getValue(id).get() || output.count!=if(id=="string_lights")2 else 1)h.fail("Original festive recipe output changed")
            val expected=mods.eln.config.ForgePortConfig.enableFestiveItems.get() && Calendar.getInstance().get(Calendar.MONTH)==Calendar.DECEMBER
            if(h.level.recipeManager.byKey(ResourceLocation("eln",id)).isPresent!=expected)h.fail("Actual seasonal recipe registration disagrees with December/config state")
        }
        h.succeed()
    }
}
