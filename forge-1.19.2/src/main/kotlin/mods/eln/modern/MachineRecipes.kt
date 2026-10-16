package mods.eln.modern

import com.google.gson.JsonObject
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.GsonHelper
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.*
import net.minecraft.world.level.Level
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.*

class ProcessingRecipe(private val key: ResourceLocation, val machine: Behavior, val ingredient: Ingredient,
    private val result: ItemStack, val joules: Double,val inputCount: Int=1,val extraResults: List<ItemStack> = emptyList(),
    private val resultTag: ResourceLocation?=null,private val resultCount: Int=1) : Recipe<Container> {
    init { require(joules.isFinite() && joules>0 && inputCount in 1..64 && extraResults.size<=3 && resultCount in 1..64) }
    override fun getId() = key
    override fun matches(container: Container, level: Level) = ingredient.test(container.getItem(0)) && container.getItem(0).count>=inputCount && !output().isEmpty
    override fun assemble(container: Container) = output()
    override fun canCraftInDimensions(width: Int, height: Int) = true
    override fun getResultItem() = output()
    override fun getSerializer(): RecipeSerializer<*> = MachineRecipes.serializer.get()
    override fun getType(): RecipeType<*> = MachineRecipes.type.get()
    override fun isSpecial() = true
    fun output(): ItemStack {
        val tag=resultTag ?: return result.copy()
        val item=ForgeRegistries.ITEMS.tags()?.getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.Registry.ITEM_REGISTRY,tag))?.firstOrNull() ?: return ItemStack.EMPTY
        return ItemStack(item,resultCount)
    }
    fun outputs()=listOf(output())+extraResults.map(ItemStack::copy)
}
object MachineRecipes {
    private val types: DeferredRegister<RecipeType<*>> = DeferredRegister.create(ForgeRegistries.RECIPE_TYPES,"eln")
    private val serializers: DeferredRegister<RecipeSerializer<*>> = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS,"eln")
    val type: RegistryObject<RecipeType<ProcessingRecipe>> = types.register("processing") { object : RecipeType<ProcessingRecipe> {} }
    val serializer: RegistryObject<RecipeSerializer<ProcessingRecipe>> = serializers.register("processing") {
        object : RecipeSerializer<ProcessingRecipe> {
            override fun fromJson(id: ResourceLocation, json: JsonObject): ProcessingRecipe {
                val output=GsonHelper.getAsJsonObject(json,"result");val tag=if(output.has("tag"))ResourceLocation(GsonHelper.getAsString(output,"tag"))else null
                return ProcessingRecipe(id,Behavior.valueOf(GsonHelper.getAsString(json,"machine")),Ingredient.fromJson(json.get("ingredient")),
                    if(tag==null)ShapedRecipe.itemStackFromJson(output)else ItemStack.EMPTY,GsonHelper.getAsDouble(json,"joules"),
                    GsonHelper.getAsInt(json,"input_count",1),json.getAsJsonArray("extra_results")?.map { ShapedRecipe.itemStackFromJson(it.asJsonObject) }.orEmpty(),tag,GsonHelper.getAsInt(output,"count",1))
            }
            override fun fromNetwork(id: ResourceLocation, data: FriendlyByteBuf): ProcessingRecipe = ProcessingRecipe(id,
                data.readEnum(Behavior::class.java),Ingredient.fromNetwork(data),data.readItem(),data.readDouble(),data.readVarInt(),data.readList { it.readItem() })
            override fun toNetwork(data: FriendlyByteBuf, recipe: ProcessingRecipe) {
                data.writeEnum(recipe.machine);recipe.ingredient.toNetwork(data);data.writeItem(recipe.resultItem);data.writeDouble(recipe.joules)
                data.writeVarInt(recipe.inputCount);data.writeCollection(recipe.extraResults) { b,s -> b.writeItem(s) }
            }
        }
    }
    fun register(bus: IEventBus) { types.register(bus);serializers.register(bus) }
    fun find(kind: Behavior, input: ItemStack, level: Level,requireCount: Boolean=true): ProcessingRecipe? =
        level.recipeManager.getAllRecipesFor(type.get()).firstOrNull { it.machine==kind && it.ingredient.test(input) && !it.output().isEmpty && (!requireCount || input.count>=it.inputCount) }
}
