package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object ResourceCompletionGameTests {
    @JvmStatic @GameTest(template="empty",timeoutTicks=20)
    fun bothOriginalArcStorageBlocksCraftBackToExactlyNineIngotsAndDropThemselvesWithOriginalTextures(h: GameTestHelper) {
        for((name,ingot,metal) in listOf(Triple("arc_clay_block","arc_clay_ingot","aluminum"),Triple("arc_metal_block","arc_metal_ingot","steel"))) {
            OriginalCraftingGameTests.craft(h,listOf("III","III","III"),mapOf('I' to "eln:$ingot"),"eln:$name")
            OriginalCraftingGameTests.craft(h,listOf("B"),mapOf('B' to "eln:$name"),"eln:$ingot",9)
            val block=Content.arcBlocks.getValue(name).get();val at=h.absolutePos(net.minecraft.core.BlockPos(2,2,2))
            val drops=Block.getDrops(block.defaultBlockState(),h.level,at,null)
            if(drops.size!=1 || drops[0].item!=block.asItem() || drops[0].count!=1)h.fail("Source $name does not drop exactly itself")
            val tag=net.minecraft.tags.ItemTags.create(ResourceLocation("forge","storage_blocks/$metal"))
            if(!block.asItem().builtInRegistryHolder().`is`(tag))h.fail("Original storage block ore-dictionary alias is missing")
        }
        for(block in Content.ores.values.map { it.get() }+Content.arcBlocks.values.map { it.get() }) {
            val state=block.defaultBlockState()
            if(!ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state) || ItemStack.EMPTY.isCorrectToolForDrops(state))h.fail("Original rock block incorrectly requires an iron pickaxe or drops without a suitable tool: $block")
        }
        for(metal in listOf("arc_clay_block","arc_metal_block"))OriginalCraftingGameTests.craft(h,listOf("PPP","PPP","I I"),mapOf('P' to "eln:small_solar_panel",'I' to "eln:$metal"),"eln:2x3_solar_panel")
        OriginalCraftingGameTests.craft(h,listOf("WWW","IWI"," WI"),mapOf('W' to "minecraft:oak_log",'I' to "eln:arc_metal_ingot"),"eln:direct_utility_pole")
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=260)
    fun originalBatteryRecyclingNeedsPaidWorkAndWholeOutputCapacityAndSurvivesActualReload(h: GameTestHelper) {
        val source=InfrastructureGameTests.create(h,"electrical_source",1,2);source.parts[0]!!.value=200.0;source.changedTopology()
        val machine=InfrastructureGameTests.create(h,"240v_macerator",2,2)
        machine.inventory.setStackInSlot(0,ItemStack(Content.partItems.getValue("cost_oriented_battery").get()))
        machine.inventory.setStackInSlot(1,ItemStack(Content.materialItems.getValue("machine_booster").get(),5))
        for(i in 2..5)machine.inventory.setStackInSlot(i,ItemStack(Items.DIRT,64))
        val unpowered=InfrastructureGameTests.create(h,"240v_macerator",6,6)
        unpowered.inventory.setStackInSlot(0,ItemStack(Content.materialItems.getValue("e_coal_helmet").get()))
        var progress=0.0
        h.runAtTickTime(25) {
            if(machine.parts[0]!!.progress>1e-6 || machine.parts[0]!!.active)h.fail("Recycling consumed power without complete six-dust output capacity")
            machine.inventory.setStackInSlot(5,ItemStack.EMPTY)
        }
        h.runAtTickTime(80) {
            progress=machine.parts[0]!!.progress
            if(progress !in 5000.0..45000.0 || !machine.inventory.getStackInSlot(5).isEmpty || machine.inventory.getStackInSlot(0).count!=1)h.fail("Original recycling skipped its paid 50 kJ operation")
            val saved=machine.saveWithoutMetadata();machine.load(saved);machine.changedTopology()
            if(abs(machine.parts[0]!!.progress-progress)>1e-8)h.fail("Recycling paid work was lost on actual reload")
        }
        h.runAtTickTime(235) {
            val output=machine.inventory.getStackInSlot(5)
            if(output.item!=Content.materialItems.getValue("lead_dust").get() || output.count!=6 || !machine.inventory.getStackInSlot(0).isEmpty || machine.parts[0]!!.energy<50000)h.fail("Actual battery maceration did not consume one battery and paid energy for six lead dust: $output")
            if(!unpowered.inventory.getStackInSlot(2).isEmpty || unpowered.inventory.getStackInSlot(0).count!=1 || unpowered.parts[0]!!.progress>1e-6)h.fail("Unpowered armor recycling generated output or work")
            for((piece,count) in listOf("helmet" to 16,"boots" to 12,"chestplate" to 24,"leggings" to 24)) {
                val recipe=MachineRecipes.find(Behavior.MACERATOR,ItemStack(Content.materialItems.getValue("e_coal_$piece").get()),h.level)
                if(recipe==null || recipe.joules!=40000.0 || recipe.output().count!=count || recipe.output().item!=Content.materialItems.getValue("coal_dust").get())h.fail("Original $piece recycling count or energy is missing")
            }
            for(kind in listOf("life","current","voltage","capacity")) {
                val recipe=MachineRecipes.find(Behavior.MACERATOR,ItemStack(Content.partItems.getValue(kind+"_oriented_battery").get()),h.level)
                if(recipe==null || recipe.joules!=50000.0 || recipe.output().count!=6)h.fail("Original $kind battery recycling differs")
            }
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=20)
    fun optionalModProcessingUsesAnActualTaggedOutputAndDisablesMissingDustInsteadOfInventingAnItem(h: GameTestHelper) {
        for((metal,joules,count) in listOf(Triple("copper",4000.0,2),Triple("lead",8000.0,2),Triple("tungsten",8000.0,2),Triple("cinnabar",8000.0,1))) {
            val ore=MachineRecipes.find(Behavior.MACERATOR,ItemStack(Content.oreItems.getValue(metal).get()),h.level)
            if(ore==null || ore.joules!=joules || ore.output().count!=count || ore.output().item!=Content.materialItems.getValue(metal+"_dust").get())h.fail("Original $metal ore processing was replaced by an obsolete world-generation recipe")
        }
        for((kind,result,joules) in listOf(Triple(Behavior.MACERATOR,"copper_dust",2000.0),Triple(Behavior.PLATE_MACHINE,"copper_plate",10000.0))) {
            val own=MachineRecipes.find(kind,ItemStack(Content.materialItems.getValue("copper_ingot").get()),h.level)
            val vanilla=MachineRecipes.find(kind,ItemStack(Items.COPPER_INGOT),h.level)
            if(own==null || vanilla==null || own.id!=vanilla.id || own.output().item!=Content.materialItems.getValue(result).get() || own.joules!=joules)h.fail("Original ELN copper ingot cannot reenter its own processing progression alongside the vanilla copper tag")
        }
        fun recipe(tag: String)=MachineRecipes.serializer.get().fromJson(ResourceLocation("eln","qa_tag_output"),com.google.gson.JsonParser.parseString("""{"machine":"MACERATOR","ingredient":{"item":"minecraft:dirt"},"result":{"tag":"$tag","count":3},"joules":12000}""").asJsonObject)
        val real=recipe("forge:dusts/coal")
        if(real.output().item!=Content.materialItems.getValue("coal_dust").get() || real.output().count!=3 || !real.matches(SimpleContainer(ItemStack(Items.DIRT)),h.level))h.fail("Original optional ore output did not resolve a real tagged item")
        val absent=recipe("eln:qa_missing_optional_dust")
        if(!absent.output().isEmpty || absent.matches(SimpleContainer(ItemStack(Items.DIRT)),h.level))h.fail("An absent optional mod dust became a fabricated output")
        for(id in listOf("certus_ore","certus_crystal","quartz_ore","quartz_crystal","fluix_crystal")) {
            val entry=h.level.recipeManager.byKey(ResourceLocation("eln","grind_optional_$id")).orElse(null) as? ProcessingRecipe
            if(entry==null || entry.joules!=(if(id.endsWith("ore"))12000.0 else 6000.0))h.fail("Original optional quartz/fluix processing entry is missing")
        }
        h.succeed()
    }
}
