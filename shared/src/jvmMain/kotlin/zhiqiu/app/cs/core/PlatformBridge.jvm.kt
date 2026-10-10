package zhiqiu.app.cs.core

import com.russhwolf.settings.Settings
import com.russhwolf.settings.PreferencesSettings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.java.Java
import org.meshtastic.mqtt.MqttEndpoint
import org.meshtastic.mqtt.MqttTransportFactory
import org.meshtastic.mqtt.plus
import org.meshtastic.mqtt.transport.tcp.TcpTransportFactory
import org.meshtastic.mqtt.transport.ws.WebSocketTransportFactory
import java.io.File
import java.util.UUID
import java.util.prefs.Preferences
private val installIdFile: File
    get() = File(System.getProperty("user.home"), ".clipsync/install-id")

internal actual fun mqttTransportFactory(): MqttTransportFactory =
    TcpTransportFactory() + WebSocketTransportFactory()

internal actual fun mqttEndpoint(): MqttEndpoint =
    MqttEndpoint.parse("${MqttSecrets.SCHEME}://${MqttSecrets.HOST}:${MqttSecrets.PORT_TLS}")

internal actual fun platformHttpClientEngine(): HttpClientEngine = Java.create()

internal actual fun installId(): String {
    installIdFile.parentFile?.mkdirs()
    if (!installIdFile.exists()) {
        installIdFile.writeText(UUID.randomUUID().toString())
    }
    return installIdFile.readText().trim().ifEmpty { UUID.randomUUID().toString() }
}

internal actual fun deviceName(): String {
    val user = System.getProperty("user.name")?.takeIf { it.isNotBlank() }
    val os = System.getProperty("os.name")?.takeIf { it.isNotBlank() } ?: "Desktop"
    return if (user != null) "$os · $user" else os
}

internal actual val platformSupportsResidentSync: Boolean = false

internal actual fun platformSettings(): Settings =
    PreferencesSettings(Preferences.userRoot().node("clipsync"))

private val systemLocale: java.util.Locale = java.util.Locale.getDefault()

internal actual val appLanguageSupport: AppLanguageSupport
    get() = AppLanguageSupport.Immediate

internal actual fun applyAppLanguage(language: AppLanguage) {
    java.util.Locale.setDefault(
        language.tag?.let { java.util.Locale.forLanguageTag(it) } ?: systemLocale
    )
}
