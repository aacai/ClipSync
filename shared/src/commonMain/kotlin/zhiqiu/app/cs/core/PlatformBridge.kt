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

internal expect fun platformResidentMemory(): Long?

private const val KB = 1024L
private const val MB = KB * 1024
private const val GB = MB * 1024

internal fun formatResidentMemory(bytes: Long): String =
        when {
            bytes < MB -> "${bytes / KB} KB"
            bytes < 10 * MB -> "${tenths(bytes, MB)} MB"
            bytes < GB -> "${bytes / MB} MB"
            else -> "${tenths(bytes, GB)} GB"
        }

/** /proc/self/status 的 VmRSS 行，Linux 和 Android 同一个口径；内核没给这一行就当问不到。 */
internal fun parseResidentMemory(status: String): Long? =
        status.lineSequence()
                .firstOrNull { it.startsWith("VmRSS:") }
                ?.substringAfter(':')
                ?.trim()
                ?.removeSuffix("kB")
                ?.trim()
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?.times(KB)

private fun tenths(bytes: Long, unit: Long): String {
    val rounded = (bytes.toDouble() * 10 / unit + 0.5).toLong()
    return "${rounded / 10}.${rounded % 10}"
}

/** 各家报的架构名不统一，收敛成安装文件名里那两个标签。 */
internal fun normalizeArch(raw: String): String {
    val v = raw.lowercase()
    return when {
        v.contains("aarch64") || v.contains("arm64") -> "arm64"
        v.contains("amd64") || v.contains("x86_64") || v.contains("x64") -> "x64"
        else -> ""
    }
}
