package zhiqiu.app.cs.core

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import zhiqiu.app.cs.files.InMemoryClipStore
import zhiqiu.app.cs.ui.AppModel
import zhiqiu.app.cs.ui.PlatformClipboard
import zhiqiu.app.cs.ui.PlatformDownloads
import zhiqiu.app.cs.ui.PlatformUi
import zhiqiu.app.cs.ui.UnavailableAutostart
import zhiqiu.app.cs.ui.UnavailableDownloads
import zhiqiu.app.cs.ui.UnavailableFileOpener
import zhiqiu.app.cs.ui.UnavailableFilePicker

/** 检查/下载更新走 AppModel 真状态机：网络那层换成假的，下载目录换成记录用的。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppModelUpdateTest {

    @Test
    fun `checking flips to busy and then to the newer installer`() = runTest {
        val harness = Harness(backgroundScope, FakeReleaseSource(version = "9.9.9"))
        runCurrent()
        assertEquals(UpdateState.Idle, harness.model.update.value)

        harness.model.checkUpdates()
        assertEquals(UpdateState.Checking, harness.model.update.value)
        runCurrent()

        val available = assertIs<UpdateState.Available>(harness.model.update.value)
        assertEquals("9.9.9", available.release.version)
        val asset = assertNotNull(available.asset)
        assertTrue(asset.name.startsWith("ClipSync-9.9.9-"), asset.name)
    }

    @Test
    fun `the version we are running is not an update`() = runTest {
        val harness = Harness(backgroundScope, FakeReleaseSource(version = APP_VERSION))
        runCurrent()

        harness.model.checkUpdates()
        runCurrent()

        assertEquals(APP_VERSION, assertIs<UpdateState.UpToDate>(harness.model.update.value).version)
    }

    @Test
    fun `a failed check reports the reason instead of going quiet`() = runTest {
        val harness = Harness(backgroundScope, FakeReleaseSource(problem = "发布页返回 403"))
        runCurrent()

        harness.model.checkUpdates()
        runCurrent()

        assertEquals("发布页返回 403", assertIs<UpdateState.Failed>(harness.model.update.value).message)
    }

    @Test
    fun `the installer lands in the download folder`() = runTest {
        val downloads = RecordingDownloads()
        val releases = FakeReleaseSource(version = "9.9.9", bytes = byteArrayOf(7, 7))
        val harness = Harness(backgroundScope, releases, downloads)
        runCurrent()

        harness.model.checkUpdates()
        runCurrent()
        val asset = assertNotNull(assertIs<UpdateState.Available>(harness.model.update.value).asset)
        harness.model.downloadUpdate()
        assertIs<UpdateState.Downloading>(harness.model.update.value).let {
            assertEquals(0L, it.received)
            assertEquals(asset, it.asset)
        }
        runCurrent()

        val downloaded = assertIs<UpdateState.Downloaded>(harness.model.update.value)
        assertEquals(downloaded.name, asset.name)
        assertEquals(1, downloads.saves.size)
        assertEquals(asset.name, downloads.saves.first().first)
        assertContentEquals(byteArrayOf(7, 7), downloads.saves.first().second)
    }

    @Test
    fun `a download folder that refuses says so`() = runTest {
        val downloads = RecordingDownloads(refusing = "磁盘满了")
        val harness = Harness(backgroundScope, FakeReleaseSource(version = "9.9.9"), downloads)
        runCurrent()

        harness.model.checkUpdates()
        runCurrent()
        harness.model.downloadUpdate()
        runCurrent()

        assertEquals("磁盘满了", assertIs<UpdateState.Failed>(harness.model.update.value).message)
        assertTrue(downloads.saves.isEmpty())
    }

    @Test
    fun `nothing to download when there is no installer for this machine`() = runTest {
        val downloads = RecordingDownloads()
        val harness = Harness(backgroundScope, FakeReleaseSource(version = "9.9.9", onlyWeb = true), downloads)
        runCurrent()

        harness.model.checkUpdates()
        runCurrent()
        assertNull(assertIs<UpdateState.Available>(harness.model.update.value).asset)

        harness.model.downloadUpdate()
        runCurrent()
        assertIs<UpdateState.Available>(harness.model.update.value)
        assertTrue(downloads.saves.isEmpty())
    }

    private inner class Harness(
        scope: CoroutineScope,
        private val releases: ReleaseSource,
        private val downloads: PlatformDownloads = UnavailableDownloads,
    ) {
        val settings = AppSettings(MapSettings(mutableMapOf()), "test-install")
        val model =
                AppModel(
                        scope,
                        PlatformUi(
                                clipboard = SilentClipboard(),
                                picker = UnavailableFilePicker,
                                opener = UnavailableFileOpener,
                                store = InMemoryClipStore(),
                                autostart = UnavailableAutostart,
                                deviceName = "Laptop",
                                deviceOs = "macOS",
                                installId = "dev-1",
                                downloads = downloads,
                        ),
                        settings,
                        DefaultMqttLinks,
                        releases,
                )
    }

    private class SilentClipboard : PlatformClipboard {
        override suspend fun read(): String? = null
        override suspend fun write(text: String) = Unit
    }

    /** 本机是什么架构就挑对应那份，认不出架构时退到不分架构的那份，测试不绑死在某台机器上。 */
    private class FakeReleaseSource(
        private val version: String = "9.9.9",
        private val problem: String? = null,
        private val bytes: ByteArray = byteArrayOf(1),
        private val onlyWeb: Boolean = false,
    ) : ReleaseSource {
        val requested = mutableListOf<String>()

        override suspend fun latest(): Release {
            problem?.let { throw UpdateException(it) }
            val assets =
                    if (onlyWeb) {
                        listOf(ReleaseAsset("web.zip", "https://g/web.zip", 10L))
                    } else {
                        listOf(
                                ReleaseAsset("ClipSync-$version-x64.dmg", "https://g/x64.dmg", 12L),
                                ReleaseAsset("ClipSync-$version-arm64.dmg", "https://g/arm64.dmg", 34L),
                                ReleaseAsset("ClipSync-$version-universal.dmg", "https://g/u.dmg", 56L),
                        )
                    }
            return Release(version, assets)
        }

        override suspend fun download(
            asset: ReleaseAsset,
            onProgress: (received: Long, total: Long) -> Unit,
        ): ByteArray {
            requested += asset.name
            onProgress(asset.size, asset.size)
            return bytes
        }
    }

    private class RecordingDownloads(private val refusing: String? = null) : PlatformDownloads {
        val saves = mutableListOf<Pair<String, ByteArray>>()
        override val isAvailable: Boolean = true

        override suspend fun save(name: String, bytes: ByteArray): String {
            refusing?.let { error(it) }
            saves += name to bytes
            return name
        }
    }
}
