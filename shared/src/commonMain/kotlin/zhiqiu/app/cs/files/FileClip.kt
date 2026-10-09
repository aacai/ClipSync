package zhiqiu.app.cs.files

import kotlinx.serialization.Serializable

/** 用户可见的文件/图片剪贴板条目。 */
@Serializable
data class FileClip(
    /** 与 ClipEnvelope.mid 一致，用于去重 */
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    /** 明文内容的 SHA-256，用于下载后校验 */
    val sha256: String,
    /** 托管链接（指向密文） */
    val link: String,
    /** 链接过期时间（epoch ms），来自 1h/12h/24h/72h */
    val expiresAt: Long,
    val ts: Long,
    val senderId: String,
    val senderName: String,
    /** 本地是否已下载并解密缓存 */
    val cached: Boolean = false,
    /** 缓存文件的本地路径（仅本机可见，不同步） */
    val localPath: String? = null,
)

/** 超过这个大小的文件当前不支持（>50MB 暂不支持，见文件协议约定）。 */
const val MAX_FILE_BYTES: Long = 50L * 1024 * 1024

object FileLimits {
    const val MAX_FILE_BYTES = zhiqiu.app.cs.files.MAX_FILE_BYTES
    const val DEFAULT_EXPIRES = "72h"
}

/** MQTT 上传递的文件元信息（加密后放在 ClipPayload 内）。 */
@Serializable
data class FilePayload(
    val name: String,
    val mime: String,
    val size: Long,
    val sha256: String,
    val link: String,
    val expiresAt: Long,
)

sealed interface FileRejectReason {
    data object TooLarge : FileRejectReason
    data object Empty : FileRejectReason
    data class InvalidName(val detail: String) : FileRejectReason
}

/** 上传前的本地校验：空文件与超限文件直接拒绝。 */
fun validateOutgoingFile(name: String, size: Long, bytes: Int): FileRejectReason? = when {
    size <= 0 || bytes <= 0 -> FileRejectReason.Empty
    size > MAX_FILE_BYTES -> FileRejectReason.TooLarge
    sanitizeFileName(name).isBlank() -> FileRejectReason.InvalidName("文件名为空")
    else -> null
}

/**
 * 文件名净化：去掉路径分隔符、控制字符与 Windows 保留名，避免把 `../../etc/passwd`
 * 之类的路径通过文件名带进接收端文件系统。
 */
fun sanitizeFileName(raw: String): String {
    val base = raw.substringAfterLast('/').substringAfterLast('\\')
    val cleaned = buildString(base.length) {
        for (ch in base) {
            when {
                ch.code < 0x20 -> append('_')
                ch in "\"*/:<>?\\|" -> append('_')
                else -> append(ch)
            }
        }
    }.trim().trimEnd('.').take(MAX_NAME_LENGTH)

    val stem = cleaned.substringBeforeLast('.', cleaned)
    val ext = cleaned.removePrefix(stem).removePrefix(".")
    val reserved = setOf(
        "con", "prn", "aux", "nul",
        "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
        "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9",
    )
    if (stem.lowercase() in reserved) return "_$cleaned"
    return cleaned
}

internal fun mimeForName(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "svg" -> "image/svg+xml"
        "heic" -> "image/heic"
        "pdf" -> "application/pdf"
        "txt", "log", "md" -> "text/plain"
        "json" -> "application/json"
        "csv" -> "text/csv"
        "html", "htm" -> "text/html"
        "xml" -> "text/xml"
        "zip" -> "application/zip"
        "gz" -> "application/gzip"
        "7z" -> "application/x-7z-compressed"
        "rar" -> "application/vnd.rar"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "dmg" -> "application/x-apple-diskimage"
        "apk" -> "application/vnd.android.package-archive"
        else -> "application/octet-stream"
    }
}

internal fun isImage(mime: String): Boolean = mime.startsWith("image/")

/** 文件条目在列表中的显示标题（图片显示“图片：xxx”之类的粗粒度归类）。 */
internal fun displayTitle(file: FileClip): String = when {
    isImage(file.mime) -> "图片 · ${file.name}"
    else -> file.name
}

internal fun humanSize(size: Long): String = when {
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> "${dec(size * 10 / 1024)} KB"
    size < 1024 * 1024 * 1024 -> "${dec(size * 10 / (1024 * 1024))} MB"
    else -> "${dec(size * 10 / (1024 * 1024 * 1024))} GB"
}

/** 保留 1~2 位小数的十进制格式化（common，避免依赖 JVM String.format）。 */
private fun dec(tenths: Long): String = (tenths / 10).toString() + "." + (tenths % 10)

private const val MAX_NAME_LENGTH = 120
