package zhiqiu.app.cs.core

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
import zhiqiu.app.cs.ui.PlatformGlobalHotkey
import zhiqiu.app.cs.ui.PlatformScreenCapture
import zhiqiu.app.cs.ui.PlatformUi
import zhiqiu.app.cs.ui.ScreenCapturePermissionMissing
import zhiqiu.app.cs.ui.UnavailableAutostart
import zhiqiu.app.cs.ui.UnavailableFileOpener
import zhiqiu.app.cs.ui.UnavailableFilePicker

/**
 * 截屏这条链路跑的是真实的 AppModel 代码：抓屏、写剪贴板、记一条、发给同屋，
 * 平台那两头换成可控的假实现，好把失败分支也走一遍。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppModelScreenshotTest {

    @Test
    fun `a capture lands on the clipboard and in the list`() = runTest {
        val clipboard = FakeClipboard()
        val capture = FakeCapture(byteArrayOf(1, 2, 3))
        val harness = Harness(clipboard, capture, FakeHotkeys(), backgroundScope)
        runCurrent()

        harness.model.takeScreenshot()
        runCurrent()

        assertContentEquals(listOf(1, 2, 3), clipboard.written.single().toList())
        val item = harness.model.items.value.single()
        val name = item.file?.name.orEmpty()
        assertEquals(ClipRepository.KIND_FILE, item.kind)
        assertEquals("image/png", item.file?.mime)
        assertTrue(name.startsWith("screenshot-"))
        assertTrue(name.endsWith(".png"))
        assertIs<Feedback.ScreenshotTaken>(harness.model.info.value)
    }

    /** 监听器下一轮读到的就是刚自己写进去的那张图，不能记成第二条。 */
    @Test
    fun `the watcher does not record the screenshot a second time`() = runTest {
        val clipboard = FakeClipboard()
        val capture = FakeCapture(byteArrayOf(7, 7))
        val harness = Harness(clipboard, capture, FakeHotkeys(), backgroundScope)
        runCurrent()

        harness.model.takeScreenshot()
        runCurrent()
        clipboard.image = ClipboardImage("image/png", capture.bytes)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, harness.model.items.value.size)
    }

    @Test
    fun `no permission means no clipboard write and no entry`() = runTest {
        val clipboard = FakeClipboard()
        val capture = FakeCapture(byteArrayOf(1)).apply { missingPermission = true }
        val harness = Harness(clipboard, capture, FakeHotkeys(), backgroundScope)
        runCurrent()

        harness.model.takeScreenshot()
        runCurrent()

        assertIs<Feedback.ScreenshotPermission>(harness.model.failure.value)
        assertTrue(clipboard.written.isEmpty())
        assertTrue(harness.model.items.value.isEmpty())
    }

    @Test
    fun `an empty capture is reported as a failed capture`() = runTest {
        val clipboard = FakeClipboard()
        val harness = Harness(clipboard, FakeCapture(ByteArray(0)), FakeHotkeys(), backgroundScope)
        runCurrent()

        harness.model.takeScreenshot()
        runCurrent()

        assertIs<Feedback.ScreenshotEmpty>(harness.model.failure.value)
        assertTrue(clipboard.written.isEmpty())
    }

    @Test
    fun `a whole screen over the size limit is never written anywhere`() = runTest {
        val clipboard = FakeClipboard()
        val capture = FakeCapture(ByteArray(FileLimits.MAX_FILE_BYTES.toInt() + 1))
        val harness = Harness(clipboard, capture, FakeHotkeys(), backgroundScope)
        runCurrent()

        harness.model.takeScreenshot()
        runCurrent()

        val failure = assertIs<Feedback.ScreenshotTooLarge>(harness.model.failure.value)
        assertEquals(FileLimits.maxMb, failure.mb)
        assertTrue(clipboard.written.isEmpty())
        assertTrue(harness.model.items.value.isEmpty())
    }

    /** 剪贴板被别的进程锁住时这条截图等于没发生：不许留半条记录，也不许把防回声标记留下。 */
    @Test
    fun `a refused clipboard write rolls the capture back`() = runTest {
        val clipboard = FakeClipboard().apply { refuseWrites = true }
        val harness = Harness(clipboard, FakeCapture(byteArrayOf(4)), FakeHotkeys(), backgroundScope)
        runCurrent()

        harness.model.takeScreenshot()
        runCurrent()

        assertIs<Feedback.ClipboardWriteBlocked>(harness.model.failure.value)
        assertTrue(harness.model.items.value.isEmpty())

        // 防回声标记已经收回，这张图后来真的进了剪贴板就该记上
        clipboard.refuseWrites = false
        clipboard.image = ClipboardImage("image/png", byteArrayOf(4))
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, harness.model.items.value.size)
    }

    @Test
    fun `the settings group only appears when both halves exist`() = runTest {
        val off = Harness(FakeClipboard(), FakeCapture(byteArrayOf(1)), FakeHotkeys(available = false), backgroundScope)
        runCurrent()
        assertTrue(!off.model.supportsScreenshot)

        val on = Harness(FakeClipboard(), FakeCapture(byteArrayOf(1)), FakeHotkeys(), backgroundScope)
        runCurrent()
        assertTrue(on.model.supportsScreenshot)
    }

    @Test
    fun `startup registers the saved chord`() = runTest {
        val hotkeys = FakeHotkeys()
        val settings = AppSettings(MapSettings(mutableMapOf()), "test-install")
        settings.setScreenshotHotkey("Command+Shift+3")
        val harness = Harness(FakeClipboard(), FakeCapture(byteArrayOf(1)), hotkeys, backgroundScope, settings)
        runCurrent()

        assertEquals("Command+Shift+3", hotkeys.registered.toString())
        assertTrue(harness.model.failure.value == null)
    }

    @Test
    fun `a chord that will not register is reported and kept out of settings`() = runTest {
        val hotkeys = FakeHotkeys().apply { accept = false }
        val harness = Harness(FakeClipboard(), FakeCapture(byteArrayOf(1)), hotkeys, backgroundScope)
        runCurrent()

        // 启动时那条默认的键也注册不上，就该老实告诉用户
        assertIs<Feedback.HotkeyFailed>(harness.model.failure.value)
        assertEquals(ScreenshotHotkey.DEFAULT_SPEC, harness.settings.screenshotHotkey.value)
    }

    @Test
    fun `changing the chord only persists once the platform accepts it`() = runTest {
        val hotkeys = FakeHotkeys()
        val harness = Harness(FakeClipboard(), FakeCapture(byteArrayOf(1)), hotkeys, backgroundScope)
        runCurrent()

        assertTrue(harness.model.setScreenshotHotkey("Command+2"))
        assertEquals("Command+2", hotkeys.registered.toString())
        assertEquals("Command+2", harness.settings.screenshotHotkey.value)
        assertIs<Feedback.HotkeySaved>(harness.model.info.value)

        hotkeys.accept = false
        assertTrue(!harness.model.setScreenshotHotkey("Alt+9"))
        assertEquals("Command+2", hotkeys.registered.toString())
        assertEquals("Command+2", harness.settings.screenshotHotkey.value)
    }

    @Test
    fun `a bare key never reaches the platform`() = runTest {
        val hotkeys = FakeHotkeys()
        val harness = Harness(FakeClipboard(), FakeCapture(byteArrayOf(1)), hotkeys, backgroundScope)
        runCurrent()

        assertTrue(!harness.model.setScreenshotHotkey("A"))
        assertEquals("Alt+A", harness.settings.screenshotHotkey.value)
    }

    private inner class Harness(
        clipboard: FakeClipboard,
        capture: PlatformScreenCapture,
        hotkeys: PlatformGlobalHotkey,
        scope: CoroutineScope,
        val settings: AppSettings = AppSettings(MapSettings(mutableMapOf()), "test-install"),
        store: ClipStore = RecordingStore(),
    ) {
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
                                capture = capture,
                                hotkeys = hotkeys,
                        ),
                        settings,
                )
    }

    private class FakeCapture(var bytes: ByteArray) : PlatformScreenCapture {
        var missingPermission = false
        var calls = 0

        override val isAvailable: Boolean get() = true

        override suspend fun capture(): ByteArray {
            calls++
            if (missingPermission) throw ScreenCapturePermissionMissing()
            return bytes
        }
    }

    private class FakeHotkeys(private val available: Boolean = true) : PlatformGlobalHotkey {
        var registered: ScreenshotHotkey? = null
        var accept = true
        var trigger: (() -> Unit)? = null

        override val isAvailable: Boolean get() = available
        override fun start(onTrigger: () -> Unit) {
            trigger = onTrigger
        }

        override fun update(hotkey: ScreenshotHotkey): Boolean {
            if (!accept) return false
            registered = hotkey
            return true
        }
    }

    private class FakeClipboard : PlatformClipboard {
        var image: ClipboardImage? = null
        var refuseWrites = false
        val written = mutableListOf<ByteArray>()

        override val canReadImages: Boolean get() = true
        override val canWriteImage: Boolean get() = true
        override fun pollIntervalMs(): Long = CLIPBOARD_POLL_MS
        override suspend fun read(): String? = null
        override suspend fun readImage(): ClipboardImage? = image
        override suspend fun write(text: String) = Unit

        override suspend fun writeImage(bytes: ByteArray, mime: String) {
            if (refuseWrites) throw IllegalStateException("clipboard busy")
            written += bytes
        }
    }

    private class RecordingStore(private var data: List<ClipItem> = emptyList()) : ClipStore {
        val caches = mutableMapOf<String, ByteArray>()

        override suspend fun read(): List<ClipItem> = data
        override suspend fun write(items: List<ClipItem>) {
            data = items
        }

        override suspend fun deleteCache(localPath: String?) {
            if (localPath != null) caches.remove(localPath)
        }

        override suspend fun writeCache(id: String, name: String, bytes: ByteArray): String {
            val path = "/cache/$id/$name"
            caches[path] = bytes
            return path
        }

        override suspend fun readCache(localPath: String): ByteArray? = caches[localPath]
    }
}
