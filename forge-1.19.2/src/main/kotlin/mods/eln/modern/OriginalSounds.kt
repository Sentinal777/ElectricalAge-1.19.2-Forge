package mods.eln.modern

import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries

/** Registers the preserved original sound collection for native client and server use. */
object OriginalSounds {
    private val registry=DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,"eln")
    val events=OriginalSounds::class.java.getResourceAsStream("/assets/eln/sounds.json")!!.reader().use { input ->
        JsonParser.parseReader(input).asJsonObject.keySet().associateWith { id -> registry.register(id) { SoundEvent(ResourceLocation("eln",id)) } }
    }
    fun event(id: String)=events.getValue(id).get()
    fun register(bus: IEventBus)=registry.register(bus)
}
