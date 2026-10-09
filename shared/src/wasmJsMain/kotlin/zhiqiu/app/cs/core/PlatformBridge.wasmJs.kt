@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package zhiqiu.app.cs.core

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import org.meshtastic.mqtt.MqttEndpoint
import org.meshtastic.mqtt.MqttTransportFactory
private external interface WebLocalStorage {
    fun getItem(key: String): String?
    fun setItem(key: String, value: String)
}

private val localStorage: WebLocalStorage = js("globalThis.localStorage")

private fun randomUuid(): String =
        js(
                "(globalThis.crypto && globalThis.crypto.randomUUID) ? globalThis.crypto.randomUUID() : 'id-' + Date.now() + '-' + Math.random().toString(36).slice(2)"
        )

internal actual fun mqttTransportFactory(): MqttTransportFactory = BrowserWssTransportFactory()

internal actual fun mqttEndpoint(): MqttEndpoint =
        MqttEndpoint.parse("wss://${MqttSecrets.HOST}:${MqttSecrets.PORT_WSS}/mqtt")

internal actual fun platformHttpClientEngine(): HttpClientEngine = Js.create()

internal actual fun installId(): String {
    val key = "clipsync.installId"
    localStorage.getItem(key)?.let {
        return it
    }
    val id = randomUuid()
    localStorage.setItem(key, id)
    return id
}

internal actual fun deviceName(): String = "Web"
