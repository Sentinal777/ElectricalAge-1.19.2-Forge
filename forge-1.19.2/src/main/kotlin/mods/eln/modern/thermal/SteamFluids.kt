package mods.eln.modern.thermal

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.material.Fluid
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fluids.FluidType
import net.minecraftforge.fluids.ForgeFlowingFluid
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject
import java.util.function.Consumer

/** A contained steam fluid supplies the original exchanger/turbine chain without an obsolete IC2 dependency. */
object SteamFluids {
    private val types: DeferredRegister<FluidType> = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES,"eln")
    private val fluids: DeferredRegister<Fluid> = DeferredRegister.create(ForgeRegistries.FLUIDS,"eln")
    private val type=types.register("steam") {
        object : FluidType(FluidType.Properties.create().density(1).viscosity(100).temperature(373)
            .canConvertToSource(false).canSwim(false).canDrown(false).canHydrate(false).canExtinguish(false)) {
            override fun initializeClient(consumer: Consumer<IClientFluidTypeExtensions>) {
                consumer.accept(object : IClientFluidTypeExtensions {
                    override fun getStillTexture()=ResourceLocation("minecraft","block/water_still")
                    override fun getFlowingTexture()=ResourceLocation("minecraft","block/water_flow")
                    override fun getTintColor()=0xBFE8EEF2.toInt()
                })
            }
        }
    }
    val steam: RegistryObject<Fluid> = fluids.register("steam") { ForgeFlowingFluid.Source(properties()) }
    private val flowing: RegistryObject<Fluid> = fluids.register("flowing_steam") { ForgeFlowingFluid.Flowing(properties()) }
    // No world block or bucket: steam is handled by machine tanks and ordinary Forge fluid pipes.
    private fun properties()=ForgeFlowingFluid.Properties(type,steam,flowing)
    fun register(bus: IEventBus) { types.register(bus);fluids.register(bus) }
}
