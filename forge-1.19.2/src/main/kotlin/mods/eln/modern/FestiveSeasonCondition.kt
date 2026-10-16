package mods.eln.modern

import com.google.gson.JsonObject
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.common.crafting.conditions.ICondition
import net.minecraftforge.common.crafting.conditions.IConditionSerializer
import java.util.Calendar

/** Original crafting is registered only in December; evaluate again on data reload. */
class FestiveSeasonCondition: ICondition {
    override fun getID()=ID
    override fun test(context: ICondition.IContext)=mods.eln.config.ForgePortConfig.enableFestiveItems.get() && Calendar.getInstance().get(Calendar.MONTH)==Calendar.DECEMBER
    companion object {
        val ID=ResourceLocation("eln","festive_season")
        val SERIALIZER=object : IConditionSerializer<FestiveSeasonCondition> {
            override fun getID()=ID
            override fun read(json: JsonObject)=FestiveSeasonCondition()
            override fun write(json: JsonObject,value: FestiveSeasonCondition) {}
        }
    }
}
