package zhiqiu.app.cs.files

import zhiqiu.app.cs.core.runBlockingTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeClipStore(
    var data: List<ClipItem> = emptyList(),
) : ClipStore {
    val deleted = mutableListOf<String>()
    val caches = mutableMapOf<String, ByteArray>()
    var writes = 0

    override suspend fun read(): List<ClipItem> = data
    override suspend fun write(items: List<ClipItem>) {
        data = items
        writes++
    }

    override suspend fun deleteCache(localPath: String?) {
        if (localPath != null) deleted += localPath
    }

    override suspend fun writeCache(id: String, name: String, bytes: ByteArray): String {
        caches[id] = bytes
        return "/cache/$id/$name"
    }
}

class ClipRepositoryTest {

    private fun text(id: String, ts: Long, body: String = "body-$id") = ClipItem(
        id = id, kind = ClipRepository.KIND_TEXT, ts = ts,
        senderId = "dev", senderName = "Pixel", text = body,
    )

    private fun file(id: String, ts: Long, name: String, cached: Boolean = false, localPath: String? = null) =
        ClipItem(
            id = id, kind = ClipRepository.KIND_FILE, ts = ts,
            senderId = "dev", senderName = "Pixel", text = name,
            file = FileClip(
                id = id, name = name, mime = "image/png", size = 5, sha256 = "aa",
                link = "http://x/$id", expiresAt = 99, ts = ts,
                senderId = "dev", senderName = "Pixel",
                cached = cached, localPath = localPath,
            ),
        )

    @Test
    fun add_keeps_newest_first_and_dedupes() = runBlockingTest {
        val store = FakeClipStore()
        val repo = ClipRepository(store)
        repo.add(text("a", ts = 1))
        repo.add(text("b", ts = 2))
        repo.add(text("c", ts = 3))
        assertEquals(listOf("c", "b", "a"), repo.items.value.map { it.id })

        // 同 id 再次出现 → 更新而不是重复
        val added = repo.add(text("a", ts = 9, body = "updated"))
        assertFalse(added)
        assertEquals(3, repo.items.value.size)
        val updated = repo.items.value.first { it.id == "a" }
        assertEquals("updated", updated.text)
        assertEquals(9, updated.ts)
        assertEquals(4, store.writes) // 3 次新增 + 1 次去重更新，每次都落盘
    }

    @Test
    fun load_restores_persisted_items() = runBlockingTest {
        val store = FakeClipStore(data = listOf(text("x", ts = 5)))
        val repo = ClipRepository(store)
        repo.load()
        assertEquals(listOf("x"), repo.items.value.map { it.id })
    }

    @Test
    fun pinned_sorts_before_time() = runBlockingTest {
        val store = FakeClipStore()
        val repo = ClipRepository(store)
        repo.add(text("old", ts = 1))
        repo.add(text("new", ts = 100))
        repo.togglePin("old")
        assertEquals(listOf("old", "new"), repo.items.value.map { it.id })
        repo.togglePin("old")
        assertEquals(listOf("new", "old"), repo.items.value.map { it.id })
    }

    @Test
    fun cap_evicts_oldest() = runBlockingTest {
        val repo = ClipRepository(FakeClipStore(), maxItems = 3)
        for (i in 1..5) repo.add(text("i$i", ts = i.toLong()))
        assertEquals(listOf("i5", "i4", "i3"), repo.items.value.map { it.id })
    }

    @Test
    fun search_matches_text_and_file_names() = runBlockingTest {
        val repo = ClipRepository(FakeClipStore())
        repo.add(text("t1", ts = 1, body = "Hello 世界 clipboard"))
        repo.add(file("f1", ts = 2, name = "Screenshot.png"))

        assertEquals(listOf("t1"), repo.search("hello").map { it.id })
        assertEquals(listOf("t1"), repo.search("世界").map { it.id })
        assertEquals(listOf("f1"), repo.search("screen").map { it.id })
        assertEquals(listOf("f1"), repo.search("screenshot.png").map { it.id })
        assertTrue(repo.search("nothing-here").isEmpty())
        assertEquals(2, repo.search("").size)
    }

    @Test
    fun mark_cached_updates_item_and_persists() = runBlockingTest {
        val store = FakeClipStore()
        val repo = ClipRepository(store)
        repo.add(file("f1", ts = 1, name = "a.png"))
        val path = repo.writeCache("f1", "a.png", byteArrayOf(1, 2, 3))
        repo.markCached("f1", path)

        val item = repo.items.value.single { it.id == "f1" }
        assertNotNull(item.file)
        assertTrue(item.file!!.cached)
        assertEquals(path, item.file!!.localPath)
        assertTrue(store.caches.containsKey("f1"))
        assertTrue(store.data.single { it.id == "f1" }.file!!.cached)
    }

    @Test
    fun remove_and_clear_delete_file_caches() = runBlockingTest {
        val store = FakeClipStore()
        val repo = ClipRepository(store)
        repo.add(file("f1", ts = 1, name = "a.png", localPath = "/cache/f1"))
        repo.add(file("f2", ts = 2, name = "b.png", localPath = "/cache/f2"))
        repo.add(text("t1", ts = 3))

        repo.remove("f1")
        assertEquals(listOf("/cache/f1"), store.deleted)
        assertEquals(listOf("t1", "f2"), repo.items.value.map { it.id }) // ts 3 > 2，新在前

        repo.clear()
        assertEquals(listOf("/cache/f1", "/cache/f2"), store.deleted)
        assertTrue(repo.items.value.isEmpty())
        assertTrue(store.data.isEmpty())
    }

    @Test
    fun remove_text_item_does_not_touch_files() = runBlockingTest {
        val store = FakeClipStore()
        val repo = ClipRepository(store)
        repo.add(file("f1", ts = 1, name = "a.png", localPath = "/cache/f1"))
        repo.remove("missing")
        repo.remove("f1")
        assertEquals(listOf("/cache/f1"), store.deleted)
        assertNull(repo.items.value.firstOrNull())
    }

    @Test
    fun history_roundtrips_through_json() {
        val items = listOf(
            text("t1", ts = 11),
            file("f1", ts = 12, name = "照片.png", cached = true, localPath = "/cache/f1"),
            file("f2", ts = 13, name = "报告.pdf").copy(pinned = true),
        )
        val decoded = ClipRepository.decode(ClipRepository.encode(items))
        assertEquals(3, decoded.size)
        assertContentEquals(items.map { it.id }, decoded.map { it.id })
        assertEquals(items[1].file?.name, decoded[1].file?.name)
        assertEquals("/cache/f1", decoded[1].file?.localPath)
        assertTrue(decoded[1].file!!.cached)
        assertTrue(decoded[2].pinned)
        assertEquals("照片.png", decoded[1].text)
        // 空白/非法 JSON 不炸
        assertTrue(ClipRepository.decode("").isEmpty())
    }

    @Test
    fun file_item_search_uses_file_metadata_field() = runBlockingTest {
        val repo = ClipRepository(FakeClipStore())
        repo.add(file("f1", ts = 1, name = "clip.mp4"))
        assertTrue(repo.search("mp4").isNotEmpty())
        assertTrue(repo.search("video").isEmpty())
    }
}
