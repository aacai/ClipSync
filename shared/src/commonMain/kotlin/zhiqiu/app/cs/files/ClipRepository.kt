package zhiqiu.app.cs.files

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class ClipItem(
    val id: String,
    /** text | file */
    val kind: String,
    val ts: Long,
    val senderId: String,
    val senderName: String,
    val text: String,
    val file: FileClip? = null,
    val pinned: Boolean = false,
    /** 手动排序用的位次；0 = 跟随时间戳。 */
    val order: Long = 0L,
    val note: String = "",
) {
    val rank: Long get() = if (order != 0L) order else ts
}

/** 一条条目能落到的可视位置区间（都从 1 起，含两端）；置顶条目只在置顶区内移动。 */
data class MoveInfo(val position: Int, val from: Int, val to: Int)

class ClipRepository(
    private val store: ClipStore,
    private val maxItems: Int = DEFAULT_MAX_ITEMS,
) {
    private val _items = MutableStateFlow<List<ClipItem>>(emptyList())
    val items: StateFlow<List<ClipItem>> = _items.asStateFlow()

    private val _undoDepth = MutableStateFlow(0)
    val undoDepth: StateFlow<Int> = _undoDepth.asStateFlow()

    private val trash = ArrayList<List<ClipItem>>(MAX_UNDO + 1)

    fun sorted(items: List<ClipItem> = _items.value): List<ClipItem> = items.sortedWith(ordering)

    suspend fun load() {
        _items.value = store.read()
    }

    /** 追加一条；同 id 已存在则更新。返回是否新增。 */
    suspend fun add(item: ClipItem): Boolean {
        val existing = _items.value.firstOrNull { it.id == item.id }
        if (existing != null) {
            replace(existing.copy(ts = item.ts, text = item.text, file = item.file, pinned = item.pinned))
            return false
        }
        _items.value = (_items.value + item).sortedWith(ordering).take(maxItems)
        persist()
        return true
    }

    suspend fun updateText(id: String, text: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (item.text == text) return
        replace(item.copy(text = text))
    }

    suspend fun updateNote(id: String, note: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        replace(item.copy(note = note))
    }

    suspend fun togglePin(id: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        setPinned(listOf(id), !item.pinned)
    }

    suspend fun setPinned(ids: Collection<String>, pinned: Boolean) {
        if (ids.isEmpty()) return
        val set = ids.toSet()
        _items.value = _items.value.map { if (it.id in set) it.copy(pinned = pinned) else it }.sortedWith(ordering)
        persist()
    }

    /** 与可视顺序里的邻居交换位次。返回是否移动成功。 */
    suspend fun move(id: String, delta: Int): Boolean {
        val view = sorted()
        val from = view.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to < 0 || to >= view.size) return false
        val a = view[from]
        val b = view[to]
        val next = _items.value.map {
            when (it.id) {
                a.id -> it.copy(order = b.rank)
                b.id -> it.copy(order = a.rank)
                else -> it
            }
        }
        _items.value = next.sortedWith(ordering)
        persist()
        return true
    }

    /** 把条目移到可视列表的第 [target] 位（1 起），只在同组（置顶/普通）内可落地。返回落点，原地不动则 null。 */
    suspend fun moveTo(id: String, target: Int): Int? {
        val view = sorted()
        val from = view.indexOfFirst { it.id == id }
        if (from < 0) return null
        val bounds = boundsOf(view, from)
        val to = (target - 1).coerceIn(bounds)
        if (to == from) return null
        val ranks = (bounds.first..bounds.last).map { view[it].rank }
        val part = view.subList(bounds.first, bounds.last + 1).toMutableList()
        part.add(to - bounds.first, part.removeAt(from - bounds.first))
        val span = minOf(from, to)..maxOf(from, to)
        val moved = HashMap<String, Long>(span.count())
        for (slot in span) moved[part[slot - bounds.first].id] = ranks[slot - bounds.first]
        _items.value = _items.value.map { item -> moved[item.id]?.let { rank -> item.copy(order = rank) } ?: item }
        persist()
        return to + 1
    }

    /** 排序视图里每条条目当前位置与可落点区间。 */
    fun moveInfos(): Map<String, MoveInfo> {
        val view = sorted()
        val out = LinkedHashMap<String, MoveInfo>(view.size)
        var start = 0
        while (start < view.size) {
            var end = start
            while (end + 1 < view.size && view[end + 1].pinned == view[start].pinned) end++
            for (i in start..end) out[view[i].id] = MoveInfo(i + 1, start + 1, end + 1)
            start = end + 1
        }
        return out
    }

    private fun boundsOf(view: List<ClipItem>, index: Int): IntRange {
        val pinned = view[index].pinned
        var lo = index
        while (lo > 0 && view[lo - 1].pinned == pinned) lo--
        var hi = index
        while (hi < view.lastIndex && view[hi + 1].pinned == pinned) hi++
        return lo..hi
    }

    suspend fun duplicate(id: String, newId: String, now: Long): Boolean {
        val item = _items.value.firstOrNull { it.id == id } ?: return false
        _items.value = (_items.value + item.copy(id = newId, ts = now, order = now)).sortedWith(ordering).take(maxItems)
        persist()
        return true
    }

    suspend fun remove(id: String): Boolean = removeMany(listOf(id)) == 1

    suspend fun removeMany(ids: Collection<String>): Int {
        if (ids.isEmpty()) return 0
        val set = ids.toSet()
        val removed = _items.value.filter { it.id in set }
        if (removed.isEmpty()) return 0
        _items.value = _items.value.filterNot { it.id in set }
        removed.filter { it.kind == KIND_FILE }.forEach { store.deleteCache(it.file?.localPath) }
        push(removed)
        persist()
        return removed.size
    }

    suspend fun clear(): Int = removeMany(_items.value.map { it.id })

    /** 弹出最近一批被删的条目放回列表；没有可撤销的批次时返回 null。 */
    suspend fun undo(): List<ClipItem>? {
        if (trash.isEmpty()) return null
        val batch = trash.removeAt(trash.lastIndex)
        _undoDepth.value = trash.size
        _items.value = (batch + _items.value).sortedWith(ordering).take(maxItems)
        persist()
        return batch
    }

    private fun push(batch: List<ClipItem>) {
        if (batch.isEmpty()) return
        // 缓存字节跟着删除一起没了，撤销回来的条目不能再谎称自己还在本机。
        trash.add(
                batch.map { item ->
                    if (item.file?.localPath == null) item
                    else item.copy(file = item.file.copy(cached = false, localPath = null))
                },
        )
        while (trash.size > MAX_UNDO) trash.removeAt(0)
        _undoDepth.value = trash.size
    }

    suspend fun writeCache(id: String, name: String, bytes: ByteArray): String? =
        store.writeCache(id, name, bytes)

    fun search(
        query: String,
        regex: Boolean = false,
        caseSensitive: Boolean = false,
        items: List<ClipItem> = _items.value,
    ): List<ClipItem> {
        val list = sorted(items)
        val q = query.trim()
        if (q.isEmpty()) return list
        if (regex) {
            val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            val pattern = runCatching { Regex(q, options) }.getOrNull() ?: return emptyList()
            return list.filter { matches(pattern::containsMatchIn, it) }
        }
        return list.filter { matches({ s -> s.contains(q, ignoreCase = !caseSensitive) }, it) }
    }

    private fun matches(match: (String) -> Boolean, item: ClipItem): Boolean =
        match(item.text) ||
            item.note.isNotEmpty() && match(item.note) ||
            item.file?.let { f -> match(f.name) || match(f.mime) } == true

    suspend fun markCached(id: String, localPath: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        replace(item.copy(file = item.file?.copy(cached = true, localPath = localPath)))
    }

    private suspend fun replace(item: ClipItem) {
        _items.value = _items.value.map { if (it.id == item.id) item else it }.sortedWith(ordering)
        persist()
    }

    private suspend fun persist() {
        store.write(sorted())
    }

    companion object {
        const val KIND_TEXT = "text"
        const val KIND_FILE = "file"
        const val DEFAULT_MAX_ITEMS = 500
        const val MAX_UNDO = 20

        val ordering: Comparator<ClipItem> =
            compareByDescending<ClipItem> { it.pinned }.thenByDescending { it.rank }

        val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        private val listSerializer = ListSerializer(ClipItem.serializer())

        fun encode(items: List<ClipItem>): String = json.encodeToString(listSerializer, items)
        fun decode(raw: String): List<ClipItem> =
            if (raw.isBlank()) emptyList() else json.decodeFromString(listSerializer, raw)
    }
}

interface ClipStore {
    suspend fun read(): List<ClipItem>
    suspend fun write(items: List<ClipItem>)
    suspend fun deleteCache(localPath: String?)
    /** 把解密后的文件字节落到本机缓存，返回路径（可再次复制/保存）。 */
    suspend fun writeCache(id: String, name: String, bytes: ByteArray): String?
}
