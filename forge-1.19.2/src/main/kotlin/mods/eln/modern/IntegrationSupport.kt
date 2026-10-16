package mods.eln.modern

object IntegrationSupport {
    fun computerCraft()=net.minecraftforge.fml.ModList.get().isLoaded("computercraft")
    /** Other BCCE releases can still use standard FE; native MJ linkage is scoped to the audited API. */
    fun buildCraft(): Boolean {
        val version=net.minecraftforge.fml.ModList.get().getModContainerById("buildcraftlib")
            .map { it.modInfo.version.toString() }.orElse("")
        return version=="8.0.23" || version.startsWith("8.0.23+")
    }
}
