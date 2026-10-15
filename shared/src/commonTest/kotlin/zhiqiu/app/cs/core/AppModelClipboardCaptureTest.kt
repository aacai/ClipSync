package zhiqiu.app.cs.core

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import zhiqiu.app.cs.files.ClipItem
import zhiqiu.app.cs.files.ClipRepository
import zhiqiu.app.cs.files.ClipStore
import zhiqiu.app.cs.files.FileLimits
import zhiqiu.app.cs.ui.AppModel
import zhiqiu.app.cs.ui.CLIPBOARD_POLL_MS
import zhiqiu.app.cs.ui.ClipboardImage
import zhiqiu.app.cs.ui.Feedback
import zhiqiu.app.cs.ui.PlatformClipboard
import zhiqiu.app.cs.ui.PickedSource
import zhiqiu.app.cs.ui.PlatformUi
import zhiqiu.app.cs.ui.UnavailableAutostart
import zhiqiu.app.cs.ui.UnavailableFileOpener
import zhiqiu.app.cs.ui.UnavailableFilePicker

/** 抓图、抓文件走的是真实采集路径：假剪贴板换内容，轮询数着步子推进。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppModelClipboardCaptureTest {

    @Test
    fun `an image is recorded once and only again when it changes`() = runTest {
        val clipboard = FakeClipboard()
        val harness = Harness(clipboard, backgroundScope)
        runCurrent()

        clipboard.image = image("image/png", byteArrayOf(1, 2, 3))
        advanceTimeBy(1_000)
        runCurrent()

        val captured = harness.model.items.value.single()
        assertEquals(ClipRepository.KIND_FILE, captured.kind)
        assertEquals("image/png", captured.file?.mime)
        assertTrue(captured.file?.name?.endsWith(".png") == true)
        assertEquals(3L, captured.file?.size)
        assertTrue(captured.file?.cached == true)

        clipboard.image = image("image/png", byteArrayOf(1, 2, 3))
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, harness.model.items.value.size)

        clipboard.image = image("image/png", byteArrayOf(9, 9))
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, harness.model.items.value.size)
    }

    @Test
    fun `the image already on the clipboard at startup is only a baseline`() = runTest {
        val clipboard = FakeClipboard()
        clipboard.image = image("image/png", byteArrayOf(1))
        val harness = Harness(clipboard, backgroundScope)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        assertTrue(harness.model.items.value.isEmpty())
    }

    @Test
    fun `switching image capture off stops recording`() = runTest {
        val clipboard = FakeClipboard()
        val harness = Harness(clipboard, backgroundScope)
        runCurrent()
        harness.settings.setWatchImages(false)

        clipboard.image = image("image/png", byteArrayOf(1, 2))
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(harness.model.items.value.isEmpty())
    }

    @Test
    fun `a platform that cannot read images records nothing`() = runTest {
        val clipboard = FakeClipboard(readable = false)
        val harness = Harness(clipboard, backgroundScope)
        runCurrent()

        clipboard.image = image("image/png", byteArrayOf(1, 2))
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(harness.model.items.value.isEmpty())
    }

    @Test
    fun `text still wins when the clipboard carries both`() = runTest {
        val clipboard = FakeClipboard()
        val harness = Harness(clipboard, backgroundScope)
        runCurrent()

        clipboard.image = image("image/png", byteArrayOf(1, 2))
        clipboard.text = "hello"
        advanceTimeBy(1_000)
        runCurrent()

        val item = harness.model.items.value.single()
        assertEquals(ClipRepository.KIND_TEXT, item.kind)
        assertEquals("hello", item.text)
    }

    @Test
    fun `an image over the size limit is skipped`() = runTest {
        val clipboard = FakeClipboard()
        val harness = Harness(clipboard, backgroundScope)
        runCurrent()

        clipboard.image = image("image/png", ByteArray(FileLimits.MAX_FILE_BYTES.toInt() + 1))
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(harness.model.items.value.isEmpty())
    }

    @Test
    fun `a copied file is cached and recorded once`() = runTest {
        val clipboard = FakeClipboard()
        val harness = Harness(clipboard, backgroundScope)
        runCurrent()

        clipboard.files = listOf(clipboard.source("report.pdf", "application/pdf", byteArrayOf(1, 2, 3, 4)))
        advanceTimeBy(1_000)
        runCurrent()

        val item = harness.model.items.value.single()
        assertEquals("report.pdf", item.file?.name)
        assertEquals("application/pdf", item.file?.mime)
        assertEquals(4L, item.file?.size)
        assertTrue(item.file?.cached == true)
        assertEquals(1, clipboard.opens)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, harness.model.items.value.size)
        assertEquals(1, clipboard.opens)
    }

    @Test
    fun `every copied file becomes its own entry`() = runTest {
        val clipboard = FakeClipboard()
        val harness = Harness(clipboard, backgroundScope)
        runCurrent()

        clipboard.files =
                listOf(
                        clipboard.source("a.txt", "text/plain", byteArrayOf(1)),
                        clipboard.source("b.txt", "text/plain", byteArrayOf(2, 2)),
                )
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(setOf("a.txt", "b.txt"), harness.model.items.value.map { it.file?.name }.toSet())
    }

    @Test
    fun `a file over the size limit is not even read`() = runTest {
        val clipboard = FakeClipboard()
        val harness = Harness(clipboard, backgroundScope)
        runCurrent()

        clipboard.files =
                listOf(
                        clipboard.source(
                                "huge.bin",
                                "application/octet-stream",
                                ByteArray(0),
                                sizeHint = FileLimits.MAX_FILE_BYTES + 1,
                        )
                )
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(harness.model.items.value.isEmpty())
        assertEquals(0, clipboard.opens)
    }

    @Test
    fun `switching files off still lets an image through`() = runTest {
        val clipboard = FakeClipboard()
        val harness = Harness(clipboard, backgroundScope)
        runCurrent()
        harness.settings.setWatchFiles(false)

        clipboard.files = listOf(clipboard.source("photo.png", "image/png", byteArrayOf(1)))
        clipboard.image = image("image/png", byteArrayOf(8, 8))
        advanceTimeBy(1_000)
        runCurrent()

        val item = harness.model.items.value.single()
        assertEquals("image/png", item.file?.mime)
        assertEquals(0, clipboard.opens)
    }

    @Test
    fun `expired entries are pruned once the switch is turned on`() = runTest {
        val store = RecordingStore(mutableListOf(textItem("old", ts = 1)))
        val harness = Harness(FakeClipboard(), backgroundScope, store)
        runCurrent()
        assertEquals(listOf("old"), harness.model.items.value.map { it.id })

        harness.settings.setDeleteExpired(true)
        runCurrent()

        assertEquals(emptyList<String>(), harness.model.items.value.map { it.id })
        val pruned = harness.model.info.value
        assertIs<Feedback.Pruned>(pruned)
        assertEquals(1, pruned.count)
    }

    private inner class Harness(
        clipboard: FakeClipboard,
        scope: CoroutineScope,
        store: RecordingStore = RecordingStore(),
    ) {
        val settings = AppSettings(MapSettings(mutableMapOf()))
        val model =
                AppModel(
                        scope,
                        PlatformUi(
                                clipboard = clipboard,
                                picker = UnavailableFilePicker,
                                opener = UnavailableFileOpener,
                                store = store,
                                autostart = UnavailableAutostart,
                                deviceName = "Laptop",
                                deviceOs = "macOS",
                                installId = "dev-1",
                        ),
                        settings,
                )
    }

    private class FakeClipboard(private val readable: Boolean = true) : PlatformClipboard {
        var image: ClipboardImage? = null
        var text: String? = null
        var files: List<PickedSource> = emptyList()
        var opens = 0

        override val canReadImages: Boolean get() = readable
        override val canReadFiles: Boolean get() = readable
        override fun pollIntervalMs(): Long = CLIPBOARD_POLL_MS
        override suspend fun read(): String? = text
        override suspend fun readImage(): ClipboardImage? = image
        override suspend fun readFiles(): List<PickedSource> = files
        override suspend fun write(text: String) {
            this.text = text
        }

        fun source(name: String, mime: String, bytes: ByteArray, sizeHint: Long = bytes.size.toLong()) =
            PickedSource(name, mime, sizeHint) {
                opens++
                bytes
            }
    }

    private class RecordingStore(private var data: List<ClipItem> = emptyList()) : ClipStore {
        val deleted = mutableListOf<String?>()
        val caches = mutableMapOf<String, ByteArray>()

        override suspend fun read(): List<ClipItem> = data
        override suspend fun write(items: List<ClipItem>) {
            data = items
        }

        override suspend fun deleteCache(localPath: String?) {
            deleted += localPath
            if (localPath != null) caches.remove(localPath)
        }

        override suspend fun writeCache(id: String, name: String, bytes: ByteArray): String {
            val path = "/cache/$id/$name"
            caches[path] = bytes
            return path
        }

        override suspend fun readCache(localPath: String): ByteArray? = caches[localPath]
    }

    private fun image(mime: String, bytes: ByteArray) = ClipboardImage(mime, bytes)

    private fun textItem(id: String, ts: Long) = ClipItem(
            id = id,
            kind = ClipRepository.KIND_TEXT,
            ts = ts,
            senderId = "dev",
            senderName = "Laptop",
            text = "body-$id",
    )
}
