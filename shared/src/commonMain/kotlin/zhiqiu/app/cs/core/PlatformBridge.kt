package zhiqiu.app.cs.core

import io.ktor.client.engine.HttpClientEngine
import org.meshtastic.mqtt.MqttEndpoint
import org.meshtastic.mqtt.MqttTransportFactory

internal expect fun mqttTransportFactory(): MqttTransportFactory

internal expect fun mqttEndpoint(): MqttEndpoint

internal expect fun platformHttpClientEngine(): HttpClientEngine

internal expect fun installId(): String

internal expect fun deviceName(): String

internal expect val platformSupportsResidentSync: Boolean
