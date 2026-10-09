package zhiqiu.app.cs.files

/** Web(wasmJs) 端：历史存 localStorage，文件不缓存（浏览器沙箱内无本地路径概念）。 */
class WasmClipStore(private val storage: WebStorage) : ClipStore {

    override suspend fun read(): List<ClipItem> =
        runCatching { ClipRepository.decode(storage.getItem(KEY) ?: "") }.getOrDefault(emptyList())

    override suspend fun write(items: List<ClipItem>) {
        storage.setItem(KEY, ClipRepository.encode(items))
    }

    override suspend fun deleteCache(localPath: String?) = Unit

    override suspend fun writeCache(id: String, name: String, bytes: ByteArray): String =
        "memory://$id/${sanitizeFileName(name)}"

    private companion object {
        const val KEY = "clipsync.history.v1"
    }
}

/** 薄封装，便于测试替换。 */
interface WebStorage {
    fun getItem(key: String): String?
    fun setItem(key: String, value: String)
}

/**
 * 内存实现：Kotlin/Wasm 不支持 `dynamic`，localStorage 的跨类型绑定留到 Web 端正式支持时补。
 * Web 端历史仅在会话内有效（文件本来也没有本地路径可缓存）。
 */
class LocalWebStorage : WebStorage {
    private val map = LinkedHashMap<String, String>()

    override fun getItem(key: String): String? = map[key]

    override fun setItem(key: String, value: String) {
        map[key] = value
    }
}
