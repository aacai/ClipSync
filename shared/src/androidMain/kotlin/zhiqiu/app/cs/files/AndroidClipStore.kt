package zhiqiu.app.cs.files

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Android 端历史与文件缓存：应用私有目录，卸载即清理。 */
class AndroidClipStore(context: Context) : ClipStore {

    private val historyFile = File(context.filesDir, "clipsync-history.json")
    private val cacheDir = File(context.filesDir, "clipsync-files")

    override suspend fun read(): List<ClipItem> = withContext(Dispatchers.IO) {
        runCatching {
            if (!historyFile.exists()) return@withContext emptyList()
            ClipRepository.decode(historyFile.readText())
        }.getOrDefault(emptyList())
    }

    override suspend fun write(items: List<ClipItem>) = withContext(Dispatchers.IO) {
        historyFile.parentFile?.mkdirs()
        val tmp = File(historyFile.parentFile, historyFile.name + ".tmp")
        tmp.writeText(ClipRepository.encode(items))
        if (!tmp.renameTo(historyFile)) {
            historyFile.writeText(tmp.readText())
            tmp.delete()
        }
        Unit
    }

    override suspend fun deleteCache(localPath: String?) = withContext(Dispatchers.IO) {
        localPath?.let { path ->
            runCatching { File(path).takeIf { it.exists() }?.delete() }
        }
        Unit
    }

    override suspend fun writeCache(id: String, name: String, bytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            cacheDir.mkdirs()
            File(cacheDir, "$id-${sanitizeFileName(name)}").apply { writeBytes(bytes) }.absolutePath
        }
}
