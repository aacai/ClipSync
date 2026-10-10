package zhiqiu.app.cs.core

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import org.meshtastic.mqtt.MqttEndpoint
import org.meshtastic.mqtt.MqttTransportFactory
import org.meshtastic.mqtt.plus
import org.meshtastic.mqtt.transport.tcp.TcpTransportFactory
import org.meshtastic.mqtt.transport.ws.WebSocketTransportFactory
import java.io.File
import java.util.UUID

/** Set from the Android Application/Activity so shared code can persist a stable install id. */
object ClipSyncAppContext {
    @Volatile
    var context: Context? = null
}

internal actual fun mqttTransportFactory(): MqttTransportFactory =
    TcpTransportFactory() + WebSocketTransportFactory()

internal actual fun mqttEndpoint(): MqttEndpoint =
    MqttEndpoint.parse("${MqttSecrets.SCHEME}://${MqttSecrets.HOST}:${MqttSecrets.PORT_TLS}")

internal actual fun platformHttpClientEngine(): HttpClientEngine = CIO.create()

internal actual fun installId(): String {
    val context = ClipSyncAppContext.context ?: return UUID.randomUUID().toString()
    val file = File(context.filesDir, "install-id")
    if (!file.exists()) file.writeText(UUID.randomUUID().toString())
    return file.readText().trim().ifEmpty { UUID.randomUUID().toString() }
}

internal actual fun deviceName(): String =
    "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim().ifEmpty { "Android" }

internal actual val platformSupportsResidentSync: Boolean = true

internal actual fun platformSettings(): Settings {
    val context = checkNotNull(ClipSyncAppContext.context) { "ClipSyncAppContext.context must be set before settings are used" }
    return SharedPreferencesSettings(context.getSharedPreferences("clipsync", Context.MODE_PRIVATE))
}

internal actual val appLanguageSupport: AppLanguageSupport
    get() = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        AppLanguageSupport.Immediate
    } else {
        AppLanguageSupport.Unsupported
    }

// android.app.locale.LocaleManager 不在当前 compileSdk 的 android.jar 里，只能运行时反射拿。
internal actual fun applyAppLanguage(language: AppLanguage) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
    val context = ClipSyncAppContext.context ?: return
    runCatching {
        val managerClass = Class.forName("android.app.locale.LocaleManager")
        val manager = context.getSystemService(managerClass) ?: return
        val locales = language.tag
            ?.let { android.os.LocaleList.forLanguageTags(it) }
            ?: android.os.LocaleList.getEmptyLocaleList()
        managerClass.getMethod("setApplicationLocales", android.os.LocaleList::class.java)
            .invoke(manager, locales)
    }
}
