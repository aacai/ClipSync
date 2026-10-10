package zhiqiu.app.cs.core

import io.ktor.client.engine.HttpClientEngine
import org.meshtastic.mqtt.MqttEndpoint
import org.meshtastic.mqtt.MqttTransportFactory

/**
 * Platform seam: which MQTT transport this build can use, how it reaches the broker, and the
 * per-install identity. Desktop/Android use TLS over TCP (8883), the browser is limited to
 * WebSocket over TLS (8084).
 */
internal expect fun mqttTransportFactory(): MqttTransportFactory

internal expect fun mqttEndpoint(): MqttEndpoint

/**
 * File upload/download engine. Declared per platform instead of letting Ktor probe the
 * classpath at runtime, so adding an engine can't silently change which one is picked.
 */
internal expect fun platformHttpClientEngine(): HttpClientEngine

/** Stable per-install id, persisted on the device. */
internal expect fun installId(): String

/** Human readable device name shown to peers. */
internal expect fun deviceName(): String

/** 平台能否把同步挂到常驻前台服务上（目前只有 Android）。 */
internal expect val platformSupportsResidentSync: Boolean
