package mods.eln.modern.thermal

import com.google.gson.JsonParser
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import java.util.Locale

data class ClimateState(val temperatureCelsius: Double,val relativeHumidityPercent: Double,
    val dayBlend: Double,val precipitationActive: Boolean,val precipitationType: String)

/** Original 1.24.6 climate profiles and curves, adapted to modern biome registry names. */
object Climate {
    data class Profile(val day: Double,val night: Double,val dayHumidity: Double,val nightHumidity: Double,val precipitation: String)
    private val profiles: Map<String,Profile> by lazy {
        val stream=javaClass.getResourceAsStream("/assets/eln/biomes.json") ?: error("Missing original climate profiles")
        val result=hashMapOf<String,Profile>()
        stream.reader(Charsets.UTF_8).use { reader ->
            for(entry in JsonParser.parseReader(reader).asJsonArray) {
                val o=entry.asJsonObject
                val p=Profile(o["DayHigh_C"].asDouble,o["NightLow_C"].asDouble,o["DayRH_%"].asDouble,o["NightRH_%"].asDouble,o["Precipitation"].asString.lowercase(Locale.ROOT))
                for(name in o["Biomes"].asJsonArray)result[name.asString.lowercase(Locale.ROOT)]=p
            }
        };result
    }
    // Renamed vanilla biomes use the corresponding original profile. Added biomes keep the original Plains fallback.
    private val aliases=mapOf("snowy_plains" to "ice plains","snowy_taiga" to "cold taiga","old_growth_pine_taiga" to "mega taiga",
        "old_growth_spruce_taiga" to "mega spruce taiga","windswept_hills" to "extreme hills","windswept_forest" to "extreme hills+",
        "windswept_gravelly_hills" to "extreme hills m","badlands" to "mesa","wooded_badlands" to "mesa plateau f",
        "eroded_badlands" to "mesa (bryce)","dark_forest" to "roofed forest","sparse_jungle" to "jungle edge",
        "nether_wastes" to "hell","the_end" to "sky","snowy_beach" to "cold beach","stony_shore" to "stone beach")
    fun profile(key: String)=profiles[(aliases[key] ?: key.replace('_',' ')).lowercase(Locale.ROOT)] ?: profiles.getValue("plains")
    private fun smooth(a: Double,b: Double,t: Double): Double { val n=t.coerceIn(0.0,1.0);return a+(b-a)*n*n*(3-2*n) }
    fun dayBlend(ticks: Long): Double {
        val day=Math.floorMod(ticks,24000L).toDouble()
        return when {
            day<10000 -> smooth(.05,1.0,day/10000)
            day<12000 -> smooth(1.0,.9,(day-10000)/2000)
            day<18000 -> smooth(.9,.35,(day-12000)/6000)
            day<23000 -> smooth(.35,0.0,(day-18000)/5000)
            else -> smooth(0.0,.05,(day-23000)/1000)
        }
    }
    fun at(profile: Profile,time: Long,raining: Boolean,thundering: Boolean,snow: Boolean=false): ClimateState {
        val blend=dayBlend(time)
        var temperature=profile.night+(profile.day-profile.night)*blend
        var humidity=profile.nightHumidity+(profile.dayHumidity-profile.nightHumidity)*blend
        var precipitation="none"
        if(raining) {
            temperature-=2.0;if(thundering)temperature-=1.0
            if(profile.precipitation!="none") {
                precipitation=if(snow || profile.precipitation=="snow")"snow" else "rain"
                humidity+=if(precipitation=="snow")15 else 20
                if(thundering)humidity+=10
                humidity=humidity.coerceAtLeast(if(thundering)95.0 else if(precipitation=="snow")85.0 else 90.0)
            }
        }
        return ClimateState(temperature,humidity.coerceIn(0.0,100.0),blend,precipitation!="none",precipitation)
    }
    fun sample(level: Level,pos: BlockPos): ClimateState {
        val biome=level.getBiome(pos)
        val key=biome.unwrapKey().map { it.location().path }.orElse("plains")
        return at(profile(key),level.dayTime,level.isRaining,level.isThundering,biome.value().coldEnoughToSnow(pos))
    }
}
