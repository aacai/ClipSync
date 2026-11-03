package zhiqiu.app.cs.files

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** 桌面缓存的回收规矩：先扔最旧的剪贴板文件，但用户自己选的背景图不参与回收。 */
class JvmClipStoreEvictionTest {

    private val root =
        File(System.getProperty("java.io.tmpdir"), "clipsync-evict-${System.nanoTime()}")
    private val cache = File(root, "files")

    private fun store(maxBytes: Long) = JvmClipStore(File(root, "history.json"), cache, maxBytes)

    @Test
    fun the_oldest_clip_files_make_room_and_the_background_is_spared() = runBlocking<Unit> {
        cache.mkdirs()
        val store = store(100)
        val background =
            store.writeCache(ClipRepository.BACKGROUND_ID, "wall.png", ByteArray(10))
        val oldest = store.writeCache("a", "oldest.txt", ByteArray(40))
        val newest = store.writeCache("b", "newest.txt", ByteArray(40))
        val now = System.currentTimeMillis()
        File(background).setLastModified(now - 300_000)
        File(oldest).setLastModified(now - 200_000)
        File(newest).setLastModified(now)

        val incoming = store.writeCache("c", "incoming.txt", ByteArray(30))

        assertTrue(File(background).exists(), "the background is a setting, not clip bulk")
        assertFalse(File(oldest).exists(), "the oldest clip file must make room")
        assertTrue(File(newest).exists())
        assertTrue(File(incoming).exists())
        root.deleteRecursively()
    }
}
