package mods.eln.modern

import net.minecraft.world.level.material.Fluid
import net.minecraftforge.registries.ForgeRegistries

/** Upstream FuelRegistry heating values and scale; BCCE's qualified fuel IDs are resolved at runtime. */
object FuelFluids {
    private val gasoline=mapOf("fuel" to 31570000.0,"rc ethanol" to 21172000.0,
        "biofuel" to 17826480.0,"bioethanol" to 17826480.0,"ic2biogas" to 17826480.0,
        "fire_water" to 35652960.0,"rocket_fuel" to 17826480.0*1.866,
        "gasoline" to 25820000.0,"kerosene" to 34800000.0,"highgradekerosene" to 39200000.0,
        "lpg" to 24840000.0,"fuelgc" to 31570000.0,"lightoil" to 35358000.0)
    private val gases=mapOf("naturalgas" to 36000000.0,"syngas" to 20000000.0)
    private val diesel=mapOf("biodiesel" to 32560000.0,"heavyoil" to 39100000.0,"diesel" to 39100000.0,
        "creosote" to 750000.0,"hootch" to 17826480.0*.6)
    fun joulesPerMb(fluid: Fluid): Double {
        val id=ForgeRegistries.FLUIDS.getKey(fluid) ?: return 0.0
        val name=id.path.removePrefix("flowing_")
        if(name=="steam" || name=="ic2steam")return 2.257
        if(name=="ic2superheatedsteam")return 6.771
        if(id.namespace.startsWith("buildcraft") && name.startsWith("fuel"))return (if(name.startsWith("fuel_dense") || name.startsWith("fuel_mixed_heavy"))39100000.0 else 31570000.0)*0.0000675
        return (gasoline[name] ?: gases[name] ?: diesel[name] ?: 0.0)*0.0000675
    }
    fun accepts(kind: Behavior, fluid: Fluid): Boolean {
        val id=ForgeRegistries.FLUIDS.getKey(fluid) ?: return false
        val name=id.path.removePrefix("flowing_")
        // Upstream rejects diesel/heavy oil in these machines, and only gas turbines accept gas fuels.
        return when(kind) {
            Behavior.STEAM_TURBINE -> name in setOf("steam","ic2steam","ic2superheatedsteam")
            Behavior.GAS_TURBINE,Behavior.RADIAL_MOTOR -> name in gasoline || name in gases || (id.namespace.startsWith("buildcraft") &&
                (name.startsWith("fuel_light") || name.startsWith("fuel_mixed_light") || name.startsWith("fuel_gaseous")))
            Behavior.FUEL_GENERATOR -> name in gasoline || (id.namespace.startsWith("buildcraft") &&
                (name.startsWith("fuel_light") || name.startsWith("fuel_mixed_light")))
            Behavior.FUEL_HEAT_FURNACE -> name in gasoline || name in diesel || (id.namespace.startsWith("buildcraft") &&
                (name.startsWith("fuel_light") || name.startsWith("fuel_mixed_light") || name.startsWith("fuel_dense") || name.startsWith("fuel_mixed_heavy")))
            else -> false
        }
    }
}
