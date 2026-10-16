package mods.eln.modern

import net.minecraft.world.item.*

/** Original copper tools use the iron tier, with the corresponding modern vanilla tool actions. */
object CopperTools {
    fun create(definition: MaterialDefinition,properties: Item.Properties): Item=when(definition.id) {
        "copper_sword"->SwordItem(Tiers.IRON,3,-2.4f,properties)
        "copper_pickaxe"->object : PickaxeItem(Tiers.IRON,1,-2.8f,properties) {}
        "copper_axe"->object : AxeItem(Tiers.IRON,6f,-3.1f,properties) {}
        "copper_shovel"->ShovelItem(Tiers.IRON,1.5f,-3f,properties)
        "copper_hoe"->object : HoeItem(Tiers.IRON,-2,-1f,properties) {}
        else->error("Unknown original copper tool ${definition.id}")
    }
}
