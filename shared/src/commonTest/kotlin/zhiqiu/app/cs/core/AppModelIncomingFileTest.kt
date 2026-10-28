package zhiqiu.app.cs.core

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import zhiqiu.app.cs.files.ClipItem
import zhiqiu.app.cs.files.ClipStore
import zhiqiu.app.cs.files.FileClip
import zhiqiu.app.cs.ui.AppModel
import zhiqiu.app.cs.ui.CLIPBOARD_POLL_MS
import zhiqiu.app.cs.ui.CopyTarget
import zhiqiu.app.cs.ui.Feedback
import zhiqiu.app.cs.ui.PlatformClipboard
import zhiqiu.app.cs.ui.PlatformUi
import zhiqiu.app.cs.ui.UnavailableAutostart
import zhiqiu.app.cs.ui.UnavailableFileOpener
import zhiqiu.app.cs.ui.UnavailableFilePicker

/** 同伴发来的文件下完后该自动躺在剪贴板里；开关关掉就一个字都不该写。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppModelIncomingFileTest {

    @Test
    fun `a downloaded file goes on the clipboard on its own`() = runTest {
        val clipboard = FakeFileClipboard()
        val harness = Harness(clipboard, backgroundScope)
        runCurrent()

        harness.model.onIncomingFile(cachedFile("/cache/clip1/report.pdf"))
        runCurrent()

        assertEquals(listOf("/cache/clip1/report.pdf"), clipboard.written.single())
        val feedback = harness.model.info.value
        assertIs<Feedback.Copied>(feedback)
        assertEquals(CopyTarget.File, feedback.what)
    }

    @Test
    fun `turning the switch off leaves the clipboard alone`() = runTest {
        val clipboard = FakeFileClipboard()
        val harness = Harness(clipboard, backgroundScope)
        runCurrent()
        harness.settings.setAutoCopyIncomingFile(false)

        harness.model.onIncomingFile(cachedFile("/cache/clip1/report.pdf"))
        runCurrent()

        assertTrue(clipboard.written.isEmpty())
        assertEquals(null, harness.model.info.value)
    }

    @Test
    fun `a platform that cannot hold files writes nothing`() = runTest {
        val clipboard = FakeFileClipboard(writable = false)
        val harness = Harness(clipboard, backgroundScope)
        runCurrent()

        harness.model.onIncomingFile(cachedFile("/cache/clip1/report.pdf"))
        runCurrent()

        assertTrue(clipboard.written.isEmpty())
    }

    @Test
    fun `a file that never reached the cache is not offered`() = runTest {
        val clipboard = FakeFileClipboard()
        val harness = Harness(clipboard, backgroundScope)
        runCurrent()

        harness.model.onIncomingFile(cachedFile(null))
        runCurrent()

        assertTrue(clipboard.written.isEmpty())
    }

    private class Harness(
        clipboard: FakeFileClipboard,
        scope: CoroutineScope,
    ) {
        val settings = AppSettings(MapSettings(mutableMapOf()), "test-install")
        val model =
                AppModel(
                        scope,
                        PlatformUi(
                                clipboard = clipboard,
                                picker = UnavailableFilePicker,
                                opener = UnavailableFileOpener,
                                store = EmptyStore,
                                autostart = UnavailableAutostart,
                                deviceName = "Laptop",
                                deviceOs = "macOS",
                                installId = "dev-1",
                        ),
                        settings,
                )
    }

    private class FakeFileClipboard(private val writable: Boolean = true) : PlatformClipboard {
        val written = mutableListOf<List<String>>()

        override val canWriteFiles: Boolean get() = writable
        override fun pollIntervalMs(): Long = CLIPBOARD_POLL_MS
        override suspend fun read(): String? = null
        override suspend fun write(text: String) = Unit
        override suspend fun writeFiles(paths: List<String>) {
            written += paths
        }
    }

    private object EmptyStore : ClipStore {
        override suspend fun read(): List<ClipItem> = emptyList()
        override suspend fun write(items: List<ClipItem>) = Unit
        override suspend fun deleteCache(localPath: String?) = Unit
        override suspend fun writeCache(id: String, name: String, bytes: ByteArray): String = "/cache/$id/$name"
        override suspend fun readCache(localPath: String): ByteArray? = null
    }

    private fun cachedFile(localPath: String?) = FileClip(
            id = "clip1",
            name = "report.pdf",
            mime = "application/pdf",
            size = 4L,
            sha256 = "0".repeat(64),
            link = "https://litterbox/clip1",
            expiresAt = 1L,
            ts = 1L,
            senderId = "peer",
            senderName = "Studio",
            cached = localPath != null,
            localPath = localPath,
    )
}
