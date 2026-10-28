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

/**
 * 这个进程当前占了多少物理内存，给状态卡片看；问不到的平台返回 null，那一行直接不显示。
 * 桌面端对齐各自系统监视器的那一列（macOS 的 RSS、Windows 的工作集），不是托管堆。
 */
internal expect fun platformResidentMemory(): Long?

private const val KB = 1024L
private const val MB = KB * 1024
private const val GB = MB * 1024

/**
 * 和 Rust 端同一套规则：1MB 以下报 KB，10MB 以下留一位小数，再往上取整到 MB，过 1GB 换单位。
 * 一位小数走四舍五入 —— 整数除法只截断，会跟 Rust 的 `{:.1}` 对不上。
 */
internal fun formatResidentMemory(bytes: Long): String =
        when {
            bytes < MB -> "${bytes / KB} KB"
            bytes < 10 * MB -> "${tenths(bytes, MB)} MB"
            bytes < GB -> "${bytes / MB} MB"
            else -> "${tenths(bytes, GB)} GB"
        }

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
