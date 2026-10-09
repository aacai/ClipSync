@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package zhiqiu.app.cs.files

/** Web(wasmJs) 端：历史存 localStorage，文件不缓存（浏览器沙箱内无本地路径概念）。 */
class WasmClipStore(private val storage: WebStorage) : ClipStore {

    override suspend fun read(): List<ClipItem> =
        runCatching { ClipRepository.decode(storage.getItem(KEY) ?: "") }.getOrDefault(emptyList())

    override suspend fun write(items: List<ClipItem>) {
        storage.setItem(KEY, ClipRepository.encode(items))
    }

    override suspend fun deleteCache(localPath: String?) = Unit

    /** 浏览器沙箱里没有可复用的本地文件，缓存直接放弃，界面也不会谎称“已缓存”。 */
    override suspend fun writeCache(id: String, name: String, bytes: ByteArray): String? = null

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
 * 真实的 `localStorage`：历史刷新页面后还在。
 *
 * 隐私模式 / 被 iframe 屏蔽时访问 localStorage 会抛异常，这时退回内存，
 * 同步照常工作，只是不再持久化。
 */
class LocalWebStorage : WebStorage {
    private val memory = LinkedHashMap<String, String>()

    override fun getItem(key: String): String? =
        runCatching { jsGetItem(key) }.getOrElse { memory[key] }

    override fun setItem(key: String, value: String) {
        runCatching { jsSetItem(key, value) }.onFailure { memory[key] = value }
    }
}

@JsFun("(k) => globalThis.localStorage.getItem(k)")
private external fun jsGetItem(key: String): String?

@JsFun("(k, v) => { globalThis.localStorage.setItem(k, v); }")
private external fun jsSetItem(key: String, value: String)
