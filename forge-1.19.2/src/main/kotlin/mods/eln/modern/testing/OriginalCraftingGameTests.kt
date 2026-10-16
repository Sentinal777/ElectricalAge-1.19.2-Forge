package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.CraftingContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeType
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import net.minecraftforge.registries.ForgeRegistries

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object OriginalCraftingGameTests {
    private fun stack(id: String)=ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation(id)) ?: error("Missing registered item $id"))
    private fun oreChoice(vararg names: String): String {
        for(name in names) {
            val tag=ForgeRegistries.ITEMS.tags()!!.getTag(net.minecraft.tags.ItemTags.create(ResourceLocation("forge",name)))
            val item=tag.firstOrNull() ?: continue
            return ForgeRegistries.ITEMS.getKey(item)!!.toString()
        }
        error("Missing original metal choices: ${names.toList()}")
    }
    internal fun craft(h: GameTestHelper,pattern: List<String>,keys: Map<Char,String>,result: String,count: Int=1) {
        val menu=object : AbstractContainerMenu(null,0) {
            override fun stillValid(player: Player)=true
            override fun quickMoveStack(player: Player,index: Int)=ItemStack.EMPTY
        }
        val grid=CraftingContainer(menu,3,3)
        pattern.forEachIndexed { y,row -> row.forEachIndexed { x,c -> if(c!=' ')grid.setItem(y*3+x,stack(keys.getValue(c))) } }
        val recipe=h.level.recipeManager.getRecipeFor(RecipeType.CRAFTING,grid,h.level).orElse(null) ?: h.fail("Original recipe for $result is unavailable to the real crafting table").let { return }
        val output=recipe.assemble(grid)
        if(output.item!=stack(result).item || output.count!=count)h.fail("Original crafting recipe for $result produced $output rather than $count items")
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=20)
    fun originalConductorCableAndLightingCountsWorkInTheCraftingTable(h: GameTestHelper) {
        craft(h,listOf("W W","WW "),mapOf('W' to "minecraft:oak_planks"),"eln:tree_resin_collector")
        craft(h,listOf("III"),mapOf('I' to "minecraft:iron_ingot"),"eln:iron_cable",12)
        craft(h,listOf("III"),mapOf('I' to "minecraft:copper_ingot"),"eln:copper_cable",12)
        craft(h,listOf("R","C","C"),mapOf('R' to "eln:rubber",'C' to "eln:copper_cable"),"eln:low_voltage_cable",2)
        craft(h,listOf("RRR","CCC","RRR"),mapOf('R' to "eln:rubber",'C' to "minecraft:copper_ingot"),"eln:low_voltage_cable",12)
        craft(h,listOf("G ","IG","G "),mapOf('G' to "minecraft:glass_pane",'I' to "eln:iron_cable"),"eln:classic_lamp_socket",3)
        craft(h,listOf("GGG","SSS"," C "),mapOf('G' to "minecraft:glass_pane",'S' to "eln:silicon_ingot",'C' to "eln:low_voltage_cable"),"eln:50v_led_bulb",2)
        craft(h,listOf("GGG","CTC","GGG"),mapOf('G' to "minecraft:glass_pane",'C' to "eln:medium_voltage_cable",'T' to "eln:tungsten_ingot"),"eln:200v_halogen_bulb",2)
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=20)
    fun originalBatterySolarAndConverterHardwareUseTheirRealMaterials(h: GameTestHelper) {
        craft(h,listOf("C C","PPP","PPP"),mapOf('C' to "eln:low_voltage_cable",'P' to "eln:lead_ingot"),"eln:cost_oriented_battery")
        craft(h,listOf("PBP"),mapOf('P' to "eln:lead_ingot",'B' to "eln:cost_oriented_battery"),"eln:capacity_oriented_battery")
        craft(h,listOf("LLL","CSC","III"),mapOf('L' to "eln:lapis_dust",'C' to "eln:low_voltage_cable",'S' to "eln:silicon_plate",'I' to "minecraft:iron_ingot"),"eln:small_solar_panel")
        craft(h,listOf("LLL","L  ","LLL"),mapOf('L' to "eln:iron_cable"),"eln:cheap_ferromagnetic_core")
        craft(h,listOf("PCP"),mapOf('P' to "eln:iron_plate",'C' to "eln:cheap_ferromagnetic_core"),"eln:average_ferromagnetic_core")
        craft(h,listOf(" P ","PCP"," P "),mapOf('P' to "eln:iron_plate",'C' to "eln:average_ferromagnetic_core"),"eln:optimal_ferromagnetic_core")
        craft(h,listOf("ppp","p p","ppp"),mapOf('p' to "eln:iron_cable"),"eln:casing")
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=20)
    fun originalResinSiliconAndSyntheticDiamondProgressionExists(h: GameTestHelper) {
        val input=net.minecraft.world.SimpleContainer(stack("eln:tree_resin"))
        val recipe=h.level.recipeManager.getRecipeFor(RecipeType.SMELTING,input,h.level).orElse(null)
        if(recipe==null || recipe.resultItem.item!=stack("eln:rubber").item || recipe.resultItem.count!=2)h.fail("Original resin smelting did not yield two rubber items")
        val silicon=h.level.recipeManager.getRecipeFor(RecipeType.SMELTING,net.minecraft.world.SimpleContainer(stack("eln:silicon_dust")),h.level).orElse(null)
        if(silicon==null || silicon.resultItem.item!=stack("eln:silicon_ingot").item)h.fail("Original silicon smelting progression is missing")
        craft(h,listOf("D"),mapOf('D' to "eln:synthetic_diamond"),"minecraft:diamond",2)
        for(id in listOf("eln:grind_sand","eln:compress_synthetic_diamond","eln:arc_clay","eln:arc_steel","eln:magnetize_optimal_core"))if(h.level.recipeManager.byKey(ResourceLocation(id)).isEmpty)h.fail("Original processing stage $id is missing")
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=20)
    fun originalPortableToolsLightsDrillHardwareAndBothArmorSetsWorkInRealCraftingTables(h: GameTestHelper) {
        craft(h,listOf("A","A"),mapOf('A' to "eln:alloy_ingot"),"eln:mining_pipe",12)
        craft(h,listOf("CMC"," T "," P "),mapOf('C' to "eln:cheap_chip",'M' to "eln:electrical_motor",'T' to "eln:mining_pipe",'P' to "minecraft:iron_pickaxe"),"eln:cheap_electrical_drill")
        craft(h,listOf("RCR"," D "," d "),mapOf('R' to "minecraft:redstone",'C' to "eln:cheap_chip",'D' to "eln:cheap_electrical_drill",'d' to "minecraft:diamond"),"eln:average_electrical_drill")
        craft(h,listOf("MCM"," T "," P "),mapOf('M' to "eln:advanced_electrical_motor",'C' to "eln:advanced_chip",'T' to "eln:mining_pipe",'P' to "minecraft:diamond_pickaxe"),"eln:fast_electrical_drill")
        craft(h,listOf("RCR"," F "," D "),mapOf('R' to "eln:graphite_rod",'C' to "eln:advanced_chip",'F' to "eln:fast_electrical_drill",'D' to "eln:synthetic_diamond"),"eln:turbo_electrical_drill")
        craft(h,listOf("DDD","DFD","DDD"),mapOf('D' to "eln:synthetic_diamond",'F' to "eln:turbo_electrical_drill"),"eln:irresponsible_electrical_drill")
        craft(h,listOf(" T ","IBI"," I "),mapOf('T' to "eln:average_electrical_drill",'B' to "eln:portable_battery",'I' to "minecraft:iron_ingot"),"eln:portable_electrical_mining_drill")
        craft(h,listOf(" T ","IMI","IBI"),mapOf('T' to "minecraft:iron_axe",'M' to "eln:electrical_motor",'B' to "eln:portable_battery",'I' to "minecraft:iron_ingot"),"eln:portable_electrical_axe")
        for((id,bulb,battery) in listOf(Triple("small_flashlight","50v_incandescent_light_bulb","portable_battery"),Triple("improved_flashlight","50v_led_bulb","portable_battery_pack")))
            craft(h,listOf("GLG","IBI"," I "),mapOf('G' to "minecraft:glass_pane",'L' to "eln:$bulb",'I' to "minecraft:iron_ingot",'B' to "eln:$battery"),"eln:$id")
        for((id,pattern) in listOf("helmet" to listOf("CCC","C C"),"chestplate" to listOf("C C","CCC","CCC"),"leggings" to listOf("CCC","C C","C C"),"boots" to listOf("C C","C C")))
            craft(h,pattern,mapOf('C' to "minecraft:copper_ingot"),"eln:copper_$id")
        for((id,pattern) in listOf("sword" to listOf("i","i","s"),"pickaxe" to listOf("iii"," s "," s "),"axe" to listOf("ii","is"," s"),"shovel" to listOf("i","s","s"),"hoe" to listOf("ii"," s"," s")))
            craft(h,pattern,mapOf('i' to "minecraft:copper_ingot",'s' to "minecraft:stick"),"eln:copper_$id")
        for((id,pattern) in listOf("helmet" to listOf("PPP","PCP"),"chestplate" to listOf("P P","PCP","PPP"),"leggings" to listOf("PPP","PCP","P P"),"boots" to listOf(" C ","P P","P P")))
            craft(h,pattern,mapOf('P' to "eln:coal_plate",'C' to "eln:portable_condensator"),"eln:e_coal_$id")
        craft(h,listOf("IGI","RCR","IGI"),mapOf('I' to "eln:iron_cable",'G' to "minecraft:gold_ingot",'R' to "minecraft:redstone",'C' to "eln:cheap_chip"),"eln:ore_scanner")
        craft(h,listOf("PGP","PCP","PBP"),mapOf('P' to "eln:iron_cable",'G' to "eln:ore_scanner",'C' to "eln:advanced_chip",'B' to "eln:portable_battery"),"eln:x_ray_scanner")
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=20)
    fun originalHeavyMechanicalPartsUseOrderedAvailableMetals(h: GameTestHelper) {
        craft(h,listOf("ISI","MAM","ISI"),mapOf('I' to "eln:iron_plate",'S' to oreChoice("ingots/steel","ingots/aluminum","ingots/alloy"),'M' to "eln:machine_block",'A' to "eln:advanced_machine_block"),"eln:motor_cowling")
        craft(h,listOf("cRc","RTR","cRc"),mapOf('c' to "eln:copper_cable",'R' to "eln:tree_resin",'T' to "eln:rubber"),"eln:heavy_winding")
        craft(h,listOf(" s ","sFs","AMA"),mapOf('s' to oreChoice("ingots/steel","ingots/alloy","ingots/iron"),'F' to "eln:fixed_shaft",'A' to "eln:advanced_machine_block",'M' to oreChoice("storage_blocks/steel","storage_blocks/alloy","storage_blocks/iron")),"eln:reinforced_shaft")
        craft(h,listOf("SIS","BAB","SIS"),mapOf('S' to oreChoice("ingots/aluminum","ingots/steel"),'I' to "eln:iron_plate",'B' to "eln:alloy_plate",'A' to "eln:advanced_machine_block"),"eln:turbine_blade_set")
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=20)
    fun originalClocksTimerAndBothAlarmsAreAvailableFromActualCraftingTables(h: GameTestHelper) {
        craft(h,listOf("I I"," c ","I I"),mapOf('c' to "eln:copper_cable",'I' to "eln:iron_cable"),"eln:hub")
        craft(h,listOf("crc","III"),mapOf('c' to "eln:iron_cable",'r' to "minecraft:redstone",'I' to "eln:iron_cable"),"eln:analog_watch")
        craft(h,listOf("rcr","III"),mapOf('c' to "eln:iron_cable",'r' to "minecraft:redstone",'I' to "eln:iron_cable"),"eln:digital_watch")
        craft(h,listOf("RC"),mapOf('R' to "minecraft:repeater",'C' to "eln:cheap_chip"),"eln:electrical_timer")
        val keys=mapOf('I' to "eln:iron_cable",'T' to "minecraft:redstone_torch",'c' to "eln:signal_cable",'M' to "minecraft:note_block")
        craft(h,listOf("ITI","IMI","IcI"),keys,"eln:nuclear_alarm")
        craft(h,listOf("MTM","IcI","III"),keys,"eln:standard_alarm")
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=20)
    fun originalManualAndSchmittContactFamiliesUseTheirActualCableCraftingProgression(h: GameTestHelper) {
        for((id,cable) in listOf("low_voltage_switch" to "low_voltage_cable","medium_voltage_switch" to "medium_voltage_cable","high_voltage_switch" to "high_voltage_cable","very_high_voltage_switch" to "very_high_voltage_cable")) {
            val recipe=h.level.recipeManager.byKey(ResourceLocation("eln",id)).orElse(null)
            if(recipe==null || recipe.resultItem.item!=stack("eln:$id").item)h.fail("Original switch $id is absent from survival crafting")
        }
        craft(h,listOf("  I"," I ","CAC"),mapOf('I' to "eln:copper_cable",'A' to "eln:rubber",'C' to "eln:low_voltage_cable"),"eln:low_voltage_switch")
        for((id,cable) in listOf("low_voltage_relay" to "low_voltage_cable","medium_voltage_relay" to "medium_voltage_cable","high_voltage_relay" to "high_voltage_cable","very_high_voltage_relay" to "very_high_voltage_cable","signal_relay" to "signal_cable","low_current_relay" to "low_current_cable","medium_current_relay" to "medium_current_cable","high_current_relay" to "high_current_cable"))
            craft(h,listOf("GGG","OIO","CRC"),mapOf('G' to "minecraft:glass_pane",'O' to "eln:iron_cable",'I' to "eln:copper_cable",'R' to "minecraft:redstone",'C' to "eln:$cable"),"eln:$id")
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=20)
    fun originalPassiveHardwareAndNTCMaterialKeepTheirSourceCraftingPatterns(h: GameTestHelper) {
        craft(h,listOf("cCc"),mapOf('c' to "eln:copper_cable",'C' to "eln:coal_dust"),"eln:power_resistor")
        craft(h,listOf(" R "," MS","cmc"),mapOf('R' to "eln:power_resistor",'M' to "eln:electrical_motor",'S' to "eln:signal_cable",'c' to "eln:copper_cable",'m' to "eln:machine_block"),"eln:rheostat")
        craft(h,listOf("cPc","III"),mapOf('c' to "eln:iron_cable",'P' to "eln:iron_plate",'I' to "minecraft:iron_ingot"),"eln:power_capacitor")
        craft(h,listOf("cIc"),mapOf('c' to "eln:copper_cable",'I' to "minecraft:iron_ingot"),"eln:power_inductor")
        craft(h,listOf(" S ","cIc"," c "),mapOf('c' to "eln:copper_cable",'S' to "eln:signal_cable",'I' to "minecraft:iron_ingot"),"eln:variable_inductor")
        craft(h,listOf("csc"),mapOf('c' to "eln:copper_cable",'s' to "eln:silicon_dust"),"eln:ntc_thermistor")
        craft(h,listOf(" D ","CRC"),mapOf('D' to "eln:small_passive_thermal_dissipator",'C' to "eln:copper_thermal_cable",'R' to "eln:rheostat"),"eln:large_rheostat")
        craft(h,listOf(" g ","grg","iii"),mapOf('g' to "minecraft:glass_pane",'r' to "minecraft:redstone",'i' to "eln:iron_cable"),"eln:nixie_tube")
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=20)
    fun originalRadioAntennaTransmittersReceiversRepeaterAndBothControlsUseActualCrafting(h: GameTestHelper) {
        craft(h,listOf("c","c"),mapOf('c' to "eln:iron_cable"),"eln:signal_antenna")
        val keys=mapOf('S' to "eln:signal_antenna",'R' to "minecraft:redstone",'I' to "eln:iron_cable",'C' to "eln:cheap_chip")
        craft(h,listOf(" S "," R ","ICI"),keys,"eln:wireless_signal_transmitter")
        craft(h,listOf(" S ","ICI"),keys,"eln:wireless_signal_receiver")
        craft(h,listOf("S S","R R","ICI"),keys,"eln:wireless_signal_repeater")
        val controls=mapOf('a' to "eln:signal_antenna",'r' to "eln:rubber",'C' to "eln:cheap_chip",'R' to "minecraft:redstone")
        craft(h,listOf(" a ","rCr"," r "),controls,"eln:wireless_switch",3)
        craft(h,listOf(" a ","rCr"," R "),controls,"eln:wireless_button",3)
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=20)
    fun originalSacrificialFusesAndFittedCableBreakerKeepTheProtectionCraftingChain(h: GameTestHelper) {
        craft(h,listOf("SCD"),mapOf('S' to "eln:electrical_probe_chip",'C' to "eln:cheap_chip",'D' to "minecraft:redstone"),"eln:overvoltage_protection",4)
        craft(h,listOf("SCD"),mapOf('S' to "eln:thermal_probe_chip",'C' to "eln:cheap_chip",'D' to "minecraft:redstone"),"eln:overheating_protection",4)
        craft(h,listOf("crC"),mapOf('c' to "eln:overvoltage_protection",'r' to "eln:high_voltage_relay",'C' to "eln:overheating_protection"),"eln:5a_electrical_breaker")
        craft(h,listOf("i"," ","i"),mapOf('i' to "eln:iron_cable"),"eln:electrical_fuse_holder")
        for(level in listOf("low","medium","high","very_high"))craft(h,listOf("rcr"),mapOf('r' to "eln:rubber",'c' to "eln:${level}_voltage_cable"),"eln:lead_fuse_for_${level}_voltage_cables",4)
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=20)
    fun originalSocketsAndAllSixteenBrushColorsHaveActualCraftingAndEmptyBrushNbt(h: GameTestHelper) {
        craft(h,listOf("R","C"),mapOf('R' to "eln:rubber",'C' to "eln:signal_cable"),"eln:signal_bus_cable")
        val keys=mapOf('R' to "eln:rubber",'U' to "eln:copper_plate",'A' to "eln:alloy_plate",'C' to "eln:low_voltage_cable")
        craft(h,listOf("RUR","ACA"),keys,"eln:type_j_socket",16)
        craft(h,listOf("RUR","ACA"),keys+('C' to "eln:medium_voltage_cable"),"eln:type_e_socket",16)
        val menu=object : AbstractContainerMenu(null,0) {
            override fun stillValid(player: Player)=true
            override fun quickMoveStack(player: Player,index: Int)=ItemStack.EMPTY
        }
        val grid=CraftingContainer(menu,3,3)
        grid.setItem(0,stack("minecraft:red_wool"));grid.setItem(1,stack("eln:iron_cable"))
        val recipe=h.level.recipeManager.getRecipeFor(RecipeType.CRAFTING,grid,h.level).orElse(null) ?: h.fail("Original empty brush recipe is absent").let { return }
        val empty=recipe.assemble(grid)
        if(empty.item!=Content.materialItems.getValue("white_brush").get() || empty.tag?.getInt("life")!=0 || Brushes.life(empty)!=0)h.fail("Crafted white brush did not start dry with original life NBT")
        val dyes=listOf("black","red","green","brown","blue","purple","cyan","light_gray","gray","pink","lime","yellow","light_blue","magenta","orange","white")
        val brushes=Materials.all.filter { it.kind=="BRUSH" }.sortedBy { it.variant }
        for((i,brush) in brushes.withIndex()) {
            grid.clearContent();grid.setItem(0,empty.copy());grid.setItem(1,stack("minecraft:${dyes[i]}_dye"))
            val colored=h.level.recipeManager.getRecipeFor(RecipeType.CRAFTING,grid,h.level).orElse(null)?.assemble(grid) ?: h.fail("Original brush color $i recipe is absent").let { return }
            if(colored.item!=Content.materialItems.getValue(brush.id).get() || Brushes.life(colored)!=32 || Brushes.color(colored)!=i)h.fail("Original brush color $i lost its dye identity or fresh 32 uses")
        }
        for((color,pigment) in mapOf(0 to "ink_sac",3 to "cocoa_beans",4 to "lapis_lazuli",15 to "bone_meal")) {
            grid.clearContent();grid.setItem(0,empty.copy());grid.setItem(1,stack("minecraft:$pigment"))
            val colored=h.level.recipeManager.getRecipeFor(RecipeType.CRAFTING,grid,h.level).orElse(null)?.assemble(grid) ?: h.fail("Original raw pigment $pigment recipe is absent").let { return }
            if(Brushes.color(colored)!=color || Brushes.life(colored)!=32)h.fail("Original raw pigment $pigment did not fill the correct brush")
        }
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=20)
    fun originalStationaryScannerUsesAnActualComparatorAndAdvancedChip(h: GameTestHelper) {
        craft(h,listOf("CA"),mapOf('C' to "minecraft:comparator",'A' to "eln:advanced_chip"),"eln:scanner")
        h.succeed()
    }
    @JvmStatic @GameTest(template="empty",timeoutTicks=20)
    fun allSixOriginalPowerAntennaRecipesUseTheCorrectTierMaterials(h: GameTestHelper) {
        val keys=mapOf('R' to "minecraft:redstone",'i' to "minecraft:iron_ingot",'I' to "eln:iron_plate",'C' to "eln:cheap_chip",'c' to "eln:cheap_chip",'D' to "minecraft:diamond")
        craft(h,listOf("R i","CI ","R i"),keys,"eln:low_power_transmitter_antenna")
        craft(h,listOf("i  "," IC","i  "),keys,"eln:low_power_receiver_antenna")
        val advanced=keys+('C' to "eln:advanced_chip")
        craft(h,listOf("c I","CI ","c I"),advanced,"eln:medium_power_transmitter_antenna")
        craft(h,listOf("I  "," IC","I  "),advanced,"eln:medium_power_receiver_antenna")
        craft(h,listOf("C I","CI ","C I"),advanced,"eln:high_power_transmitter_antenna")
        craft(h,listOf("I D"," IC","I D"),advanced,"eln:high_power_receiver_antenna")
        h.succeed()
    }
}
