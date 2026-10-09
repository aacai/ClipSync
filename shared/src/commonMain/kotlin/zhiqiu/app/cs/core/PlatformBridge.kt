package zhiqiu.app.cs.core

import org.meshtastic.mqtt.MqttEndpoint
import org.meshtastic.mqtt.MqttTransportFactory

/**
 * Platform seam: which MQTT transport this build can use, how it reaches the broker, and the
 * per-install identity. Desktop/Android use TLS over TCP (8883), the browser is limited to
 * WebSocket over TLS (8084).
 */
internal expect fun mqttTransportFactory(): MqttTransportFactory

internal expect fun mqttEndpoint(): MqttEndpoint

/** Stable per-install id, persisted on the device. */
internal expect fun installId(): String

/** Human readable device name shown to peers. */
internal expect fun deviceName(): String
