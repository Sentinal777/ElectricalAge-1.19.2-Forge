package mods.eln.modern.testing

import com.google.gson.JsonParser
import mods.eln.modern.*
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.ItemTags
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import net.minecraftforge.registries.ForgeRegistries

/** Independent 1.24.6 recipe inventory checked against Minecraft's actual loaded recipe and tag managers. */
@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object SourceProcessingGameTests {
    @JvmStatic @GameTest(template="empty",timeoutTicks=30)
    fun allSixtyOneOriginalProcessingOperationsAndFiveConditionalModOreOperationsKeepTheirRealInputsOutputsCountsAndEnergy(h: GameTestHelper) {
        val manifest=SourceProcessingGameTests::class.java.getResourceAsStream("/assets/eln/original_processing_expected.json")!!.reader().use { JsonParser.parseReader(it).asJsonObject }
        if(manifest.getAsJsonArray("direct").size()!=61)h.fail("Independent original processing inventory is incomplete")
        fun inputs(id: String,count: Int): List<ItemStack> {
            if(id.startsWith('#'))return ForgeRegistries.ITEMS.tags()!!.getTag(ItemTags.create(ResourceLocation(id.substring(1)))).map { ItemStack(it,count) }
            val key=ResourceLocation(id)
            if(!ForgeRegistries.ITEMS.containsKey(key))h.fail("Original processing input $id is not registered")
            return listOf(ItemStack(ForgeRegistries.ITEMS.getValue(key)!!,count))
        }
        for(raw in manifest.getAsJsonArray("direct")) {
            val source=raw.asJsonObject;val line=source["line"].asInt;val kind=Behavior.valueOf(source["machine"].asString)
            val variants=inputs(source["input"].asString,source["inputCount"].asInt)
            if(variants.isEmpty())h.fail("Original processing input at source line $line has no real items")
            for(input in variants) {
                val recipe=MachineRecipes.find(kind,input,h.level) ?: h.fail("Source line $line has no usable $kind recipe for $input").let { return }
                val expected=source.getAsJsonArray("outputs");val actual=recipe.outputs()
                if(recipe.inputCount!=source["inputCount"].asInt || recipe.joules!=source["joules"].asDouble || actual.size!=expected.size())h.fail("Source line $line differs in count, paid work or output arity: ${recipe.id}")
                for(i in actual.indices) {
                    val output=expected[i].asJsonObject
                    if(ForgeRegistries.ITEMS.getKey(actual[i].item).toString()!=output["item"].asString || actual[i].count!=output["count"].asInt)h.fail("Source line $line has a different actual output: ${recipe.id}: ${actual[i]}")
                }
            }
        }
        for(raw in manifest.getAsJsonArray("conditional")) {
            val source=raw.asJsonObject;val id=ResourceLocation("eln",source["recipe"].asString)
            val recipe=h.level.recipeManager.byKey(id).orElse(null) as? ProcessingRecipe ?: h.fail("Missing original conditional processing operation $id").let { return }
            val outputs=ForgeRegistries.ITEMS.tags()!!.getTag(ItemTags.create(ResourceLocation(source["outputTag"].asString))).toList()
            if(recipe.joules!=source["joules"].asDouble || recipe.machine!=Behavior.MACERATOR)h.fail("Conditional source operation $id changed its energy or machine")
            if(outputs.isEmpty()) { if(!recipe.output().isEmpty)h.fail("Missing optional dust was synthesized by $id") }
            else {
                if(recipe.output().item!=outputs.first() || recipe.output().count!=source["count"].asInt)h.fail("Conditional source operation $id did not use its first real tagged dust")
                for(input in inputs('#'+source["inputTag"].asString,1))if(!recipe.matches(SimpleContainer(input),h.level))h.fail("Conditional source operation $id rejects a real original input tag")
            }
        }
        h.succeed()
    }
}
