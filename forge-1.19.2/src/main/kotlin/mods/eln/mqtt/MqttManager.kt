package mods.eln.mqtt

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import mods.eln.Eln
import mods.eln.config.ForgePortConfig
import net.minecraftforge.fml.loading.FMLPaths
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/** Original endpoint file and opt-in global toggle. Credentials remain exclusively server-side. */
object MqttManager {
    private val clients=ConcurrentHashMap<String,SimpleMqttClient>()
    private var initialized=false
    private var configuration=MqttConfiguration(disable=true)
    var revision=0
        private set
    private fun path()=FMLPaths.CONFIGDIR.get().resolve("eln-mqtt.json")
    fun init() { if(!initialized)refreshConfiguration() }
    fun refreshConfiguration(file: Path=path()) {
        shutdown();initialized=true;revision++
        configuration=try {
            if(!Files.exists(file)) {
                Files.createDirectories(file.parent)
                Files.newBufferedWriter(file).use { GsonBuilder().setPrettyPrinting().create().toJson(MqttConfiguration(),it) }
            }
            require(Files.size(file)<=1048576)
            val json=Files.newBufferedReader(file).use { JsonParser.parseReader(it).asJsonObject }
            val disabled=json.get("disable")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
            val endpoints=json.getAsJsonArray("mqtt")?.take(16).orEmpty().mapNotNull { item ->
                runCatching {
                    val obj=item.asJsonObject
                    fun text(key: String)=obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                    val name=text("name")?.trim() ?: return@runCatching null
                    val uri=text("uri") ?: return@runCatching null
                    val parsed=URI(uri)
                    require(name.length in 1..64 && name.none { it.code<32 } && parsed.scheme=="tcp" && !parsed.host.isNullOrBlank() && parsed.userInfo==null && (parsed.port==-1 || parsed.port in 1..65535))
                    val prefix=text("prefix")?.trim()?.trimEnd('/')
                    require(prefix==null || prefix.length<=256 && prefix.none { it=='#' || it=='+' || it.code<32 })
                    MqttServerConfig(name,uri,text("username"),text("password"),prefix)
                }.getOrNull()
            }.distinctBy { it.name.lowercase(java.util.Locale.ROOT) }
            MqttConfiguration(disabled,endpoints)
        }catch(_: Exception) { Eln.LOGGER.warn("Unable to read the MQTT endpoint configuration; MQTT connections are disabled.");MqttConfiguration(disable=true) }
    }
    fun getConfig()=if(ForgePortConfig.mqttEnabled.get())configuration else configuration.copy(disable=true)
    fun getServerByName(name: String?)=configuration.mqtt.firstOrNull { it.name.equals(name,ignoreCase=true) }
    fun getClient(name: String): SimpleMqttClient? {
        if(getConfig().disable)return null
        val server=getServerByName(name) ?: return null
        return clients.computeIfAbsent(server.name.lowercase(java.util.Locale.ROOT)) { SimpleMqttClient(server) }
    }
    fun shutdown() {
        MqttMeterManager.clear();MqttSignalControllerManager.clear()
        clients.values.forEach { it.shutdown() };clients.clear()
    }
    fun reset() { shutdown();initialized=false;configuration=MqttConfiguration(disable=true);revision++ }
}
