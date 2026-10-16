package mods.eln.mqtt

import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

object MqttMeterManager {
    private val meterSubscriptions = ConcurrentHashMap<String, MeterSubscription>()
    private val infoCache = ConcurrentHashMap<String, MeterInfoState>()

    data class MeterSubscription(
        val key: String,
        val meterId: String,
        val subscriptionHandle: MqttSubscriptionHandle
    )

    private data class MeterInfoState(
        var connectionEpoch: Int = -1,
        var name: String = "",
        var level: String = "",
        var position: String = ""
    )

    fun ensureSubscription(meterId: String, coordinate: MqttCoordinate, serverConfig: MqttServerConfig, commandHandler: (MqttMeterCommand) -> Unit) {
        val key = keyOf(coordinate)
        val prefix = serverConfig.prefix.orEmpty()
        val baseTopic = topicFor(prefix, meterId)
        val resetTopic = "$baseTopic/ctrl/reset"
        val statusTopic = "$baseTopic/ctrl/status"

        val current = meterSubscriptions[key]
        if (current != null && current.meterId == meterId) {
            return
        }
        current?.subscriptionHandle?.close()

        val client = MqttManager.getClient(serverConfig.name) ?: return
        val resetHandle = client.subscribe(resetTopic) { _, _ ->
            commandHandler.invoke(MqttMeterCommand.Reset)
        }
        val statusHandle = client.subscribe(statusTopic) { _, payload ->
            val text = payload.toString(StandardCharsets.UTF_8).trim()
            val enabled = text.equals("on", ignoreCase = true)
            commandHandler.invoke(MqttMeterCommand.SetStatus(enabled))
        }
        val combinedHandle = MqttSubscriptionHandle {
            resetHandle.close()
            statusHandle.close()
        }
        meterSubscriptions[key] = MeterSubscription(key, meterId, combinedHandle)
    }

    fun publishSnapshot(snapshot: MqttMeterSnapshot) {
        val server = MqttManager.getServerByName(snapshot.info.serverName) ?: return
        val client = MqttManager.getClient(server.name) ?: return
        if(!client.isConnected)return
        val baseTopic = topicFor(server.prefix.orEmpty(), snapshot.info.meterId)
        publishText(client, "$baseTopic/stat/power", snapshot.powerWatts)
        publishText(client, "$baseTopic/stat/energy", snapshot.energyWh)
        publishText(client, "$baseTopic/stat/time", snapshot.timeSeconds)
        publishText(client, "$baseTopic/stat/current", snapshot.currentAmps)
        publishText(client, "$baseTopic/stat/voltage", snapshot.voltage)
        publishText(client, "$baseTopic/stat/status", if (snapshot.status) "on" else "off")
        publishInfoIfNeeded(client, baseTopic, snapshot)
    }

    fun release(coordinate: MqttCoordinate) {
        val key = keyOf(coordinate)
        val subscription = meterSubscriptions.remove(key) ?: return
        infoCache.remove(subscription.meterId)
        subscription.subscriptionHandle.close()
    }

    fun clear() { meterSubscriptions.values.forEach { it.subscriptionHandle.close() };meterSubscriptions.clear();infoCache.clear() }

    private fun publishInfoIfNeeded(client: SimpleMqttClient, baseTopic: String, snapshot: MqttMeterSnapshot) {
        val state = infoCache.computeIfAbsent(snapshot.info.meterId) { MeterInfoState() }
        val force=state.connectionEpoch!=client.connectionEpoch
        state.connectionEpoch=client.connectionEpoch
        val desiredName = snapshot.info.meterName
        val desiredLevel = snapshot.dimensionName
        val desiredPos = "${snapshot.coordinate.x},${snapshot.coordinate.y},${snapshot.coordinate.z}"
        if (force || state.name != desiredName) {
            state.name = desiredName
            publishText(client, "$baseTopic/info/name", desiredName, retain = true)
        }
        if (force || state.level != desiredLevel) {
            state.level = desiredLevel
            publishText(client, "$baseTopic/info/level", desiredLevel, retain = true)
        }
        if (force || state.position != desiredPos) {
            state.position = desiredPos
            publishText(client, "$baseTopic/info/pos", desiredPos, retain = true)
        }
    }

    private fun publishText(client: SimpleMqttClient, topic: String, value: Any, retain: Boolean = false) {
        val payload = value.toString().toByteArray()
        client.publish(topic, payload, retain)
    }

    private fun topicFor(prefix: String, meterId: String): String {
        val builder = StringBuilder()
        if (prefix.isNotBlank()) {
            builder.append(prefix.trimEnd('/'))
            builder.append('/')
        }
        builder.append("eln/meter/")
        builder.append(meterId)
        return builder.toString()
    }

    private fun keyOf(coordinate: MqttCoordinate): String {
        return "${coordinate.dimension}:${coordinate.x}:${coordinate.y}:${coordinate.z}:${coordinate.face}"
    }
}
