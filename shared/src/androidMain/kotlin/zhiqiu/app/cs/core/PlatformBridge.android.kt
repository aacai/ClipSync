package zhiqiu.app.cs.core

import android.content.Context
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

internal actual fun installId(): String {
    val context = ClipSyncAppContext.context ?: return UUID.randomUUID().toString()
    val file = File(context.filesDir, "install-id")
    if (!file.exists()) file.writeText(UUID.randomUUID().toString())
    return file.readText().trim().ifEmpty { UUID.randomUUID().toString() }
}

internal actual fun deviceName(): String =
    "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim().ifEmpty { "Android" }
