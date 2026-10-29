package zhiqiu.app.cs.core

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.mqtt.ConnectionState
import zhiqiu.app.cs.files.ClipItem
import zhiqiu.app.cs.files.ClipStore
import zhiqiu.app.cs.ui.AppModel
import zhiqiu.app.cs.ui.PlatformAboutMenu
import zhiqiu.app.cs.ui.PlatformClipboard
import zhiqiu.app.cs.ui.PlatformUi
import zhiqiu.app.cs.ui.UnavailableAboutMenu
import zhiqiu.app.cs.ui.UnavailableAutostart
import zhiqiu.app.cs.ui.UnavailableFileOpener
import zhiqiu.app.cs.ui.UnavailableFilePicker
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** macOS 应用菜单那条「关于」到卡片的一次开关：菜单是假的，只看回调有没有把状态推过去。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppModelAboutMenuTest {

    @Test
    fun `the about menu opens the card and dismissing closes it`() = runTest {
        val about = FakeAboutMenu()
        val model = AppModel(backgroundScope, platform(about), AppSettings(MapSettings(mutableMapOf()), "test-install"), IdleLinks)
        runCurrent()
        assertFalse(model.aboutPrompt.value)

        about.fire()
        assertTrue(model.aboutPrompt.value)

        model.dismissAbout()
        assertFalse(model.aboutPrompt.value)
    }

    @Test
    fun `a platform without an about menu never calls back`() {
        var fired = false

        UnavailableAboutMenu.observe { fired = true }

        assertFalse(fired)
    }

    private fun platform(aboutMenu: PlatformAboutMenu) = PlatformUi(
        clipboard = IdleClipboard,
        picker = UnavailableFilePicker,
        opener = UnavailableFileOpener,
        store = EmptyStore,
        autostart = UnavailableAutostart,
        deviceName = "Laptop",
        deviceOs = "macOS",
        installId = "dev-1",
        aboutMenu = aboutMenu,
    )

    private class FakeAboutMenu : PlatformAboutMenu {
        private var onAbout: (() -> Unit)? = null

        override fun observe(onAbout: () -> Unit) {
            this.onAbout = onAbout
        }

        fun fire() {
            onAbout?.invoke()
        }
    }

    private object IdleClipboard : PlatformClipboard {
        override fun pollIntervalMs(): Long = 60_000
        override suspend fun read(): String? = null
        override suspend fun write(text: String) = Unit
    }

    private object EmptyStore : ClipStore {
        override suspend fun read(): List<ClipItem> = emptyList()
        override suspend fun write(items: List<ClipItem>) = Unit
        override suspend fun deleteCache(localPath: String?) = Unit
        override suspend fun writeCache(id: String, name: String, bytes: ByteArray): String? = null
        override suspend fun readCache(localPath: String): ByteArray? = null
    }

    private object IdleLinks : MqttLinkFactory {
        override fun create(config: ClipSyncEngine.Config, roomHash: String, willPayload: String): MqttLink = IdleLink
    }

    private object IdleLink : MqttLink {
        private val state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected.Idle)

        override val connectionState: StateFlow<ConnectionState> = state
        override val messages: Flow<Incoming> = flowOf()

        override suspend fun connect() = Unit
        override suspend fun subscribe(topicFilter: String) = Unit
        override suspend fun publish(topic: String, payload: String) = Unit
        override suspend fun close() = Unit
    }
}
