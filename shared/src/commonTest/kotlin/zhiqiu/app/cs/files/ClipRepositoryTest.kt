package zhiqiu.app.cs.files

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

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
    fun add_keeps_newest_first_and_dedupes() = runTest {
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
    fun load_restores_persisted_items() = runTest {
        val store = FakeClipStore(data = listOf(text("x", ts = 5)))
        val repo = ClipRepository(store)
        repo.load()
        assertEquals(listOf("x"), repo.items.value.map { it.id })
    }

    @Test
    fun pinned_sorts_before_time() = runTest {
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
    fun cap_evicts_oldest() = runTest {
        val repo = ClipRepository(FakeClipStore(), maxItems = 3)
        for (i in 1..5) repo.add(text("i$i", ts = i.toLong()))
        assertEquals(listOf("i5", "i4", "i3"), repo.items.value.map { it.id })
    }

    @Test
    fun search_matches_text_and_file_names() = runTest {
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
    fun mark_cached_updates_item_and_persists() = runTest {
        val store = FakeClipStore()
        val repo = ClipRepository(store)
        repo.add(file("f1", ts = 1, name = "a.png"))
        val path = repo.writeCache("f1", "a.png", byteArrayOf(1, 2, 3)) ?: error("可缓存的 store 必须返回路径")
        repo.markCached("f1", path)

        val item = repo.items.value.single { it.id == "f1" }
        assertNotNull(item.file)
        assertTrue(item.file.cached)
        assertEquals(path, item.file.localPath)
        assertTrue(store.caches.containsKey("f1"))
        assertTrue(store.data.single { it.id == "f1" }.file!!.cached)
    }

    @Test
    fun remove_and_clear_delete_file_caches() = runTest {
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
    fun remove_text_item_does_not_touch_files() = runTest {
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
    fun file_item_search_uses_file_metadata_field() = runTest {
        val repo = ClipRepository(FakeClipStore())
        repo.add(file("f1", ts = 1, name = "clip.mp4"))
        assertTrue(repo.search("mp4").isNotEmpty())
        assertTrue(repo.search("video").isEmpty())
    }

    @Test
    fun undo_restores_the_last_deleted_item() = runTest {
        val repo = ClipRepository(FakeClipStore())
        repo.add(text("a", ts = 1))
        repo.add(text("b", ts = 2))
        assertTrue(repo.remove("b"))
        assertEquals(listOf("a"), repo.items.value.map { it.id })
        assertEquals(1, repo.undoDepth.value)

        assertEquals(listOf("b"), repo.undo()?.map { it.id })
        assertEquals(listOf("b", "a"), repo.items.value.map { it.id })
        assertEquals(0, repo.undoDepth.value)
        assertNull(repo.undo())
    }

    @Test
    fun undo_clear_as_one_step() = runTest {
        val repo = ClipRepository(FakeClipStore())
        listOf("a", "b", "c").forEachIndexed { i, id -> repo.add(text(id, ts = i.toLong())) }
        assertEquals(3, repo.clear())
        assertTrue(repo.items.value.isEmpty())
        assertEquals(1, repo.undoDepth.value)

        assertEquals(3, repo.undo()?.size)
        assertEquals(3, repo.items.value.size)
        assertEquals(0, repo.undoDepth.value)
    }

    @Test
    fun undo_restores_file_without_its_deleted_cache() = runTest {
        val store = FakeClipStore()
        val repo = ClipRepository(store)
        repo.add(file("f1", ts = 1, name = "a.png"))
        val path = repo.writeCache("f1", "a.png", byteArrayOf(1)) ?: error("store 应返回缓存路径")
        repo.markCached("f1", path)

        repo.remove("f1")
        assertTrue(store.deleted.contains(path))
        val restored = repo.undo()?.single()?.file
        assertNull(restored?.localPath)
        assertFalse(restored?.cached == true)
    }

    @Test
    fun undo_stack_is_bounded_and_peels_one_batch_at_a_time() = runTest {
        val repo = ClipRepository(FakeClipStore())
        val total = ClipRepository.MAX_UNDO + 5
        val ids = (0 until total).map { "i$it" }
        ids.forEachIndexed { i, id -> repo.add(text(id, ts = i.toLong())) }
        ids.forEach { repo.remove(it) }
        assertEquals(ClipRepository.MAX_UNDO, repo.undoDepth.value)

        var batches = 0
        while (repo.undo() != null) batches++
        assertEquals(ClipRepository.MAX_UNDO, batches)
        assertEquals(ClipRepository.MAX_UNDO, repo.items.value.size)
    }

    @Test
    fun move_swaps_rank_with_the_visual_neighbour() = runTest {
        val repo = ClipRepository(FakeClipStore())
        repo.add(text("a", ts = 300))
        repo.add(text("b", ts = 200))
        repo.add(text("c", ts = 100))
        assertEquals(listOf("a", "b", "c"), repo.sorted().map { it.id })

        assertTrue(repo.move("c", -1))
        assertEquals(listOf("a", "c", "b"), repo.sorted().map { it.id })
        assertEquals(200L, repo.items.value.first { it.id == "c" }.order)

        assertFalse(repo.move("a", -1))
        assertEquals(listOf("a", "c", "b"), repo.sorted().map { it.id })
    }

    @Test
    fun pinned_wins_over_manual_order() = runTest {
        val repo = ClipRepository(FakeClipStore())
        listOf("a", "b", "c").forEachIndexed { i, id -> repo.add(text(id, ts = (3 - i) * 100L)) }
        repo.move("c", -1)
        repo.setPinned(listOf("b"), true)
        assertEquals(listOf("b", "a", "c"), repo.sorted().map { it.id })
    }

    @Test
    fun update_text_and_note_persist() = runTest {
        val store = FakeClipStore()
        val repo = ClipRepository(store)
        repo.add(text("a", ts = 1))
        repo.updateText("a", "changed")
        repo.updateNote("a", "sticky note")

        val item = repo.items.value.single()
        assertEquals("changed", item.text)
        assertEquals("sticky note", item.note)
        assertEquals(item, store.data.single())
    }

    @Test
    fun duplicate_gets_a_fresh_id_and_copies_content() = runTest {
        val repo = ClipRepository(FakeClipStore())
        repo.add(text("a", ts = 10))
        assertTrue(repo.duplicate("a", "a-copy", now = 20))

        val copy = repo.items.value.first { it.id == "a-copy" }
        assertEquals("body-a", copy.text)
        assertEquals(20L, copy.order)
        assertEquals(listOf("a-copy", "a"), repo.sorted().map { it.id })
    }

    @Test
    fun search_supports_regex_and_case() = runTest {
        val repo = ClipRepository(FakeClipStore())
        repo.add(text("a", ts = 1, body = "Alpha 42"))
        repo.add(text("b", ts = 2, body = "beta"))

        assertEquals(listOf("b", "a"), repo.search("beta|42", regex = true).map { it.id })
        assertEquals(listOf("a"), repo.search("\\d+", regex = true).map { it.id })
        assertEquals(emptyList<String>(), repo.search("[", regex = true).map { it.id })
        assertEquals(listOf("a"), repo.search("Alpha", caseSensitive = true).map { it.id })
        assertEquals(emptyList<String>(), repo.search("alpha", caseSensitive = true).map { it.id })
        assertEquals(listOf("a"), repo.search("ALPHA").map { it.id })
        assertEquals(listOf("b", "a"), repo.search("a").map { it.id })
    }
}
