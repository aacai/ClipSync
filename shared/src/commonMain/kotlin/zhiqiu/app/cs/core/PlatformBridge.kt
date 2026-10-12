package zhiqiu.app.cs.core

import io.ktor.client.engine.HttpClientEngine
import org.meshtastic.mqtt.MqttEndpoint
import org.meshtastic.mqtt.MqttTransportFactory

internal expect fun mqttTransportFactory(): MqttTransportFactory

internal expect fun mqttEndpoint(): MqttEndpoint

internal expect fun platformHttpClientEngine(): HttpClientEngine

internal expect fun installId(): String

internal expect fun deviceName(): String

/** 给人看的系统标签：macOS / Windows / Linux / Android / iOS / Web。 */
internal expect fun platformOs(): String

internal expect val platformSupportsResidentSync: Boolean
