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

/** 安装包挑架构用的短标签：arm64 / x64，认不出来就空着。 */
internal expect fun platformArch(): String

internal expect val platformSupportsResidentSync: Boolean

/** 这个平台的更新是自己下的包，还是刷新页面就换新的。 */
internal expect val platformSupportsSelfUpdate: Boolean

/** 各家报的架构名不统一，收敛成安装文件名里那两个标签。 */
internal fun normalizeArch(raw: String): String {
    val v = raw.lowercase()
    return when {
        v.contains("aarch64") || v.contains("arm64") -> "arm64"
        v.contains("amd64") || v.contains("x86_64") || v.contains("x64") -> "x64"
        else -> ""
    }
}
