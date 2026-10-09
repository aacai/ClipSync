package zhiqiu.app.cs.core

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.meshtastic.mqtt.MqttEndpoint
import org.meshtastic.mqtt.MqttTransportFactory
import org.meshtastic.mqtt.transport.ws.WebSocketTransportFactory
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIDevice
// iOS 没有 JVM/TCP 变体的传输，走 WSS（EMQX 8084 /mqtt）。
internal actual fun mqttTransportFactory(): MqttTransportFactory = WebSocketTransportFactory()

internal actual fun mqttEndpoint(): MqttEndpoint =
    MqttEndpoint.parse("wss://${MqttSecrets.HOST}:${MqttSecrets.PORT_WSS}/mqtt")

internal actual fun platformHttpClientEngine(): HttpClientEngine = Darwin.create()

internal actual fun installId(): String {
    val defaults = NSUserDefaults.standardUserDefaults
    val existing = defaults.stringForKey(INSTALL_ID_KEY)
    if (!existing.isNullOrBlank()) return existing
    val fresh = NSUUID().UUIDString
    defaults.setObject(fresh, forKey = INSTALL_ID_KEY)
    return fresh
}

internal actual fun deviceName(): String = UIDevice.currentDevice.name

private const val INSTALL_ID_KEY = "clipsync-install-id"
