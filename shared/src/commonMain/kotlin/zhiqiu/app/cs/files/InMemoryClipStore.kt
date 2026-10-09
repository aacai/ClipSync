package zhiqiu.app.cs.files

/** 纯内存实现：iOS/Web 端或测试用（文件不落盘）。 */
class InMemoryClipStore : ClipStore {
    private var data: List<ClipItem> = emptyList()
    private val caches = LinkedHashMap<String, ByteArray>()

    override suspend fun read(): List<ClipItem> = data

    override suspend fun write(items: List<ClipItem>) {
        data = items
    }

    override suspend fun deleteCache(localPath: String?) {
        if (localPath != null) caches.remove(localPath.substringBefore('/'))
    }

    override suspend fun writeCache(id: String, name: String, bytes: ByteArray): String {
        caches[id] = bytes
        return "$id/${sanitizeFileName(name)}"
    }
}
