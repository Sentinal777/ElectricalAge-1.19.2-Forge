package mods.eln.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path

/** Reads the original hierarchical setting names without loading legacy game classes. */
class PortConfig {
    private var values = JsonObject()
    private val overrides = mutableMapOf<String, Any>()
    @JvmOverloads fun load(path: Path = Path.of("config", "eln", "eln.json")) {
        if (Files.isRegularFile(path)) Files.newBufferedReader(path).use { values = JsonParser.parseReader(it).asJsonObject }
    }
    private fun value(key: String): Any? {
        overrides[key]?.let { return it }
        var node: com.google.gson.JsonElement = values
        for (part in key.split('.')) {
            if (!node.isJsonObject) return null
            node = node.asJsonObject.get(part) ?: return null
        }
        if (!node.isJsonPrimitive) return null
        val p = node.asJsonPrimitive
        return when { p.isBoolean -> p.asBoolean; p.isNumber -> p.asDouble; else -> p.asString }
    }
    fun getBooleanOrElse(key: String, fallback: Boolean): Boolean = value(key) as? Boolean ?: fallback
    fun getDoubleOrElse(key: String, fallback: Double): Double = (value(key) as? Number)?.toDouble() ?: fallback
    fun getIntOrElse(key: String, fallback: Int): Int = (value(key) as? Number)?.toInt() ?: fallback
    fun getStringOrElse(key: String, fallback: String): String = value(key) as? String ?: fallback
    fun setBoolean(key: String, v: Boolean) { overrides[key] = v }
    fun setDouble(key: String, v: Double) { overrides[key] = v }
    fun setInt(key: String, v: Int) { overrides[key] = v }
}
