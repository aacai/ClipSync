package zhiqiu.app.cs.files

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 一条剪贴板历史（文本或文件），与 CopyQ 的 item 概念对应。 */
@Serializable
data class ClipItem(
    val id: String,
    /** text | file */
    val kind: String,
    val ts: Long,
    val senderId: String,
    val senderName: String,
    /** 文本内容（kind=text）或文件名（kind=file） */
    val text: String,
    /** kind=file 时的元信息 */
    val file: FileClip? = null,
    /** 自己置顶的条目排在最前 */
    val pinned: Boolean = false,
)

/**
 * 内存历史仓库：文本与文件共用一套列表，带搜索/置顶/删除。
 * 持久化由各平台的 [ClipStore] 完成（桌面/Android 写 JSON，Web 用 localStorage）。
 */
class ClipRepository(
    private val store: ClipStore,
    private val maxItems: Int = DEFAULT_MAX_ITEMS,
) {
    private val _items = MutableStateFlow<List<ClipItem>>(emptyList())
    val items: StateFlow<List<ClipItem>> = _items.asStateFlow()

    /** 置顶条目在前，其余按时间倒序。 */
    fun sorted(items: List<ClipItem> = _items.value): List<ClipItem> =
        items.sortedWith(compareByDescending<ClipItem> { it.pinned }.thenByDescending { it.ts })

    suspend fun load() {
        _items.value = store.read()
    }

    /** 追加一条；已存在同 id 则更新时间戳并上移。返回是否新增。 */
    suspend fun add(item: ClipItem): Boolean {
        val existing = _items.value.firstOrNull { it.id == item.id }
        if (existing != null) {
            val updated = existing.copy(ts = item.ts, text = item.text, file = item.file, pinned = item.pinned)
            replace(updated)
            return false
        }
        val next = (_items.value + item).sortedWith(ordering).take(maxItems)
        _items.value = next
        persist()
        return true
    }

    suspend fun togglePin(id: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        replace(item.copy(pinned = !item.pinned))
    }

    suspend fun remove(id: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        _items.value = _items.value.filterNot { it.id == id }
        if (item.kind == KIND_FILE) store.deleteCache(item.file?.localPath)
        persist()
    }

    suspend fun clear() {
        _items.value.filter { it.kind == KIND_FILE }.forEach { store.deleteCache(it.file?.localPath) }
        _items.value = emptyList()
        persist()
    }

    /** 落盘缓存一个解密后的文件，返回本地路径。 */
    suspend fun writeCache(id: String, name: String, bytes: ByteArray): String =
        store.writeCache(id, name, bytes)

    fun search(query: String, items: List<ClipItem> = _items.value): List<ClipItem> {
        if (query.isBlank()) return sorted(items)
        val q = query.trim()
        return sorted(items).filter { item ->
            when (item.kind) {
                KIND_TEXT -> item.text.contains(q, ignoreCase = true)
                KIND_FILE -> item.text.contains(q, ignoreCase = true) ||
                    item.file?.name?.contains(q, ignoreCase = true) == true
                else -> false
            }
        }
    }

    /** 文件下载解密完成后更新本地缓存路径。 */
    suspend fun markCached(id: String, localPath: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        val updated = item.copy(file = item.file?.copy(cached = true, localPath = localPath))
        replace(updated)
    }

    private suspend fun replace(item: ClipItem) {
        val next = _items.value.map { if (it.id == item.id) item else it }
        _items.value = next.sortedWith(ordering)
        persist()
    }

    private suspend fun persist() {
        store.write(sorted())
    }

    companion object {
        const val KIND_TEXT = "text"
        const val KIND_FILE = "file"
        const val DEFAULT_MAX_ITEMS = 500

        val ordering: Comparator<ClipItem> =
            compareByDescending<ClipItem> { it.pinned }.thenByDescending { it.ts }

        val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        private val listSerializer = ListSerializer(ClipItem.serializer())

        fun encode(items: List<ClipItem>): String = json.encodeToString(listSerializer, items)
        fun decode(raw: String): List<ClipItem> =
            if (raw.isBlank()) emptyList() else json.decodeFromString(listSerializer, raw)
    }
}

/** 平台相关的历史 + 文件缓存持久化。 */
interface ClipStore {
    suspend fun read(): List<ClipItem>
    suspend fun write(items: List<ClipItem>)
    suspend fun deleteCache(localPath: String?)
    /** 把解密后的文件字节落到本机缓存，返回路径（可再次复制/保存）。 */
    suspend fun writeCache(id: String, name: String, bytes: ByteArray): String
}
