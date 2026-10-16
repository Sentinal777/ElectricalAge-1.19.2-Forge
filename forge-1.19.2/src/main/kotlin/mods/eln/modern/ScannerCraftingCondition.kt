package mods.eln.modern

import com.google.gson.JsonObject
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.common.crafting.conditions.ICondition
import net.minecraftforge.common.crafting.conditions.IConditionSerializer

class ScannerCraftingCondition: ICondition {
    override fun getID()=ID
    override fun test(context: ICondition.IContext)=mods.eln.config.ForgePortConfig.scannerCrafting.get()
    companion object {
        val ID=ResourceLocation("eln","scanner_crafting_enabled")
        val SERIALIZER=object : IConditionSerializer<ScannerCraftingCondition> {
            override fun getID()=ID
            override fun read(json: JsonObject)=ScannerCraftingCondition()
            override fun write(json: JsonObject,value: ScannerCraftingCondition) {}
        }
    }
}
