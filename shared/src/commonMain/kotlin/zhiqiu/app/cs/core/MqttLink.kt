package zhiqiu.app.cs.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import org.meshtastic.mqtt.ConnectionState
import org.meshtastic.mqtt.MqttClient
import org.meshtastic.mqtt.QoS

/**
 * 引擎用到的那点 MQTT 能力：真机上是 [MqttClient]，测试里换成假的。
 * 只留 connect/subscribe/publish/close 四个动作，重连策略归引擎管。
 */
internal interface MqttLink {
    val connectionState: StateFlow<ConnectionState>
    val messages: Flow<Incoming>

    suspend fun connect()

    suspend fun subscribe(topicFilter: String)

    suspend fun publish(topic: String, payload: String)

    suspend fun close()
}

internal data class Incoming(val topic: String, val payload: String)

internal fun interface MqttLinkFactory {
    fun create(config: ClipSyncEngine.Config, roomHash: String, safetyPrefix: String): MqttLink
}

private const val RECONNECT_BASE_MS = 1_000L
private const val RECONNECT_MAX_MS = 30_000L
private const val RECONNECT_HANDOFF_ATTEMPTS = 8

internal val DefaultMqttLinks = MqttLinkFactory { config, hash, safetyPrefix ->
    val client = MqttClient("cs-$hash-${config.deviceId}") {
        username = MqttSecrets.USERNAME
        password(MqttSecrets.PASSWORD)
        transportFactory = mqttTransportFactory()
        keepAliveSeconds = 60
        cleanStart = true
        autoReconnect = true
        reconnectBaseDelayMs = RECONNECT_BASE_MS
        reconnectMaxDelayMs = RECONNECT_MAX_MS
        // 库只快试几轮（1→30s），之后把断线交回引擎：那边的退避到 60s 封顶。
        // 长时间没网时（合盖、进电梯）少一半连接尝试，就少一半无线电唤醒。
        maxReconnectAttempts = RECONNECT_HANDOFF_ATTEMPTS
        will {
            topic = ClipProtocol.presenceTopic(hash, config.deviceId)
            payload(
                ClipProtocol.encode(
                    PresenceEnvelope(
                        dev = config.deviceId,
                        name = config.deviceName,
                        os = config.deviceOs,
                        online = false,
                        ts = currentTimeMillis(),
                        sp = safetyPrefix,
                    ),
                ),
            )
            qos = QoS.AT_LEAST_ONCE
        }
    }
    RealMqttLink(client)
}

private class RealMqttLink(private val client: MqttClient) : MqttLink {

    override val connectionState: StateFlow<ConnectionState> get() = client.connectionState

    override val messages: Flow<Incoming>
        get() = client.messages.map { Incoming(it.topic, it.payloadAsString()) }

    override suspend fun connect() {
        client.connect(mqttEndpoint())
    }

    override suspend fun subscribe(topicFilter: String) {
        // noLocal: the broker does not echo our own publishes back to us (MQTT 5 feature).
        client.subscribe(topicFilter, QoS.AT_LEAST_ONCE, noLocal = true)
    }

    override suspend fun publish(topic: String, payload: String) {
        client.publish(topic = topic, payload = payload, qos = QoS.AT_LEAST_ONCE)
    }

    override suspend fun close() {
        client.close()
    }
}
