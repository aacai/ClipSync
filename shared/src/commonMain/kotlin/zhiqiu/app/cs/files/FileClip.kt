package zhiqiu.app.cs.files

import kotlinx.serialization.Serializable

@Serializable
data class FileClip(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    /** 明文内容的 SHA-256，用于下载后校验 */
    val sha256: String,
    val link: String,
    val expiresAt: Long,
    val ts: Long,
    val senderId: String,
    val senderName: String,
    val cached: Boolean = false,
    val localPath: String? = null,
)

object FileLimits {
    const val MAX_FILE_BYTES = 50L * 1024 * 1024
    val maxMb: Int get() = (MAX_FILE_BYTES / 1024 / 1024).toInt()
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
    data object InvalidName : FileRejectReason
}

fun validateOutgoingFile(name: String, size: Long, bytes: Int): FileRejectReason? = when {
    size <= 0 || bytes <= 0 -> FileRejectReason.Empty
    size > FileLimits.MAX_FILE_BYTES -> FileRejectReason.TooLarge
    sanitizeFileName(name).isBlank() -> FileRejectReason.InvalidName
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

/** mime 反推扩展名，让剪贴板里抓来的图片有个能打开的文件名。 */
internal fun extForMime(mime: String): String = when (mime.lowercase()) {
    "image/jpeg" -> "jpg"
    "image/png" -> "png"
    "image/gif" -> "gif"
    "image/webp" -> "webp"
    "image/bmp" -> "bmp"
    "image/heic" -> "heic"
    "image/svg+xml" -> "svg"
    else -> mime.substringAfterLast('/').substringBefore(';').takeIf { it.length in 2..5 } ?: "bin"
}

internal fun isImage(mime: String): Boolean = mime.startsWith("image/")

/** 下载目录里重名不自作主张覆盖：在扩展名前插序号。 */
fun numberedName(name: String, exists: (String) -> Boolean): String {
    if (!exists(name)) return name
    val stem = name.substringBeforeLast('.', name)
    val ext = name.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it != stem }
    var index = 1
    while (index < 1000) {
        val candidate = if (ext == null) "$stem ($index)" else "$stem ($index).$ext"
        if (!exists(candidate)) return candidate
        index++
    }
    return name
}

internal fun displayTitle(file: FileClip): String = file.name

internal fun humanSize(size: Long): String = when {
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> "${dec(size * 10 / 1024)} KB"
    size < 1024 * 1024 * 1024 -> "${dec(size * 10 / (1024 * 1024))} MB"
    else -> "${dec(size * 10 / (1024 * 1024 * 1024))} GB"
}

private fun dec(tenths: Long): String = (tenths / 10).toString() + "." + (tenths % 10)

private const val MAX_NAME_LENGTH = 120
