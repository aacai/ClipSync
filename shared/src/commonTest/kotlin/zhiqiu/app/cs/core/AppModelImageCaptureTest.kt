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
import zhiqiu.app.cs.ui.PlatformUi
import zhiqiu.app.cs.ui.UnavailableAutostart
import zhiqiu.app.cs.ui.UnavailableFileOpener
import zhiqiu.app.cs.ui.UnavailableFilePicker

/** 抓图走的是真实采集路径：假剪贴板换内容，轮询数着步子推进。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppModelImageCaptureTest {

    @Test
    fun `an image is recorded once and only again when it changes`() = runTest {
        val clipboard = ImageClipboard()
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
        val clipboard = ImageClipboard()
        clipboard.image = image("image/png", byteArrayOf(1))
        val harness = Harness(clipboard, backgroundScope)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        assertTrue(harness.model.items.value.isEmpty())
    }

    @Test
    fun `switching image capture off stops recording`() = runTest {
        val clipboard = ImageClipboard()
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
        val clipboard = ImageClipboard(readable = false)
        val harness = Harness(clipboard, backgroundScope)
        runCurrent()

        clipboard.image = image("image/png", byteArrayOf(1, 2))
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(harness.model.items.value.isEmpty())
    }

    @Test
    fun `text still wins when the clipboard carries both`() = runTest {
        val clipboard = ImageClipboard()
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
        val clipboard = ImageClipboard()
        val harness = Harness(clipboard, backgroundScope)
        runCurrent()

        clipboard.image = image("image/png", ByteArray(FileLimits.MAX_FILE_BYTES.toInt() + 1))
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(harness.model.items.value.isEmpty())
    }

    @Test
    fun `expired entries are pruned once the switch is turned on`() = runTest {
        val store = RecordingStore(mutableListOf(textItem("old", ts = 1)))
        val harness = Harness(ImageClipboard(), backgroundScope, store)
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
        clipboard: ImageClipboard,
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

    private class ImageClipboard(private val readable: Boolean = true) : PlatformClipboard {
        var image: ClipboardImage? = null
        var text: String? = null

        override val canReadImages: Boolean get() = readable
        override fun pollIntervalMs(): Long = CLIPBOARD_POLL_MS
        override suspend fun read(): String? = text
        override suspend fun readImage(): ClipboardImage? = image
        override suspend fun write(text: String) {
            this.text = text
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
