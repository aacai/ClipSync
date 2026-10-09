package zhiqiu.app.cs.files

import java.io.File

/** 桌面端（macOS/Windows/Linux）的历史与文件缓存：JSON + 应用数据目录。 */
class JvmClipStore(
    private val historyFile: File,
    private val cacheDir: File,
    private val maxCacheBytes: Long = DEFAULT_MAX_CACHE_BYTES,
) : ClipStore {

    override suspend fun read(): List<ClipItem> = runCatching {
        if (!historyFile.exists()) return emptyList()
        ClipRepository.decode(historyFile.readText())
    }.getOrDefault(emptyList())

    override suspend fun write(items: List<ClipItem>) {
        historyFile.parentFile?.mkdirs()
        val tmp = File(historyFile.parentFile, historyFile.name + ".tmp")
        tmp.writeText(ClipRepository.encode(items))
        if (!tmp.renameTo(historyFile)) {
            historyFile.writeText(tmp.readText())
            tmp.delete()
        }
    }

    override suspend fun deleteCache(localPath: String?) {
        if (localPath.isNullOrBlank()) return
        runCatching { File(localPath).takeIf { it.exists() }?.delete() }
    }

    override suspend fun writeCache(id: String, name: String, bytes: ByteArray): String {
        cacheDir.mkdirs()
        evictIfNeeded(bytes.size.toLong())
        val file = File(cacheDir, "$id-${sanitizeFileName(name)}")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    /** 超出缓存上限时按修改时间淘汰最旧的文件。 */
    private fun evictIfNeeded(incoming: Long) {
        if (incoming > maxCacheBytes / 2) return // 单个文件接近上限就不缓存
        val files = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() } + incoming
        for (f in files) {
            if (total <= maxCacheBytes) break
            total -= f.length()
            f.delete()
        }
    }

    companion object {
        const val DEFAULT_MAX_CACHE_BYTES = 512L * 1024 * 1024

        fun default(): JvmClipStore {
            val home = File(System.getProperty("user.home"), ".clipsync")
            return JvmClipStore(
                historyFile = File(home, "history.json"),
                cacheDir = File(home, "files"),
            )
        }
    }
}
