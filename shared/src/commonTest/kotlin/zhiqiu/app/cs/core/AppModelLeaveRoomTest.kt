package zhiqiu.app.cs.core

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.mqtt.ConnectionState
import org.meshtastic.mqtt.MqttException
import org.meshtastic.mqtt.ReasonCode
import zhiqiu.app.cs.files.ClipItem
import zhiqiu.app.cs.files.ClipStore
import zhiqiu.app.cs.ui.AppModel
import zhiqiu.app.cs.ui.CLIPBOARD_POLL_MS
import zhiqiu.app.cs.ui.PlatformClipboard
import zhiqiu.app.cs.ui.PlatformUi
import zhiqiu.app.cs.ui.UnavailableAutostart
import zhiqiu.app.cs.ui.UnavailableFileOpener
import zhiqiu.app.cs.ui.UnavailableFilePicker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 退房确认与剪贴板轮询都只看 AppModel：链路是假的，剪贴板数着读了几次。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppModelLeaveRoomTest {

    private val room = "ABC123"
    private val key = ByteArray(RoomCrypto.KEY_SIZE) { 7 }

    @Test
    fun `leaving the room asks first and only then lets go`() = runTest {
        val harness = Harness(backgroundScope)
        val model = harness.model
        runCurrent()
        assertIs<ClipSyncEngine.Status.Online>(model.status.value)

        model.requestLeaveRoom()
        runCurrent()

        // 第一次点只是问一句：房间还在，保存的会话也还在
        assertTrue(model.leavePrompt.value)
        assertEquals(room, model.joinedRoom.value)
        assertEquals(room, harness.settings.roomSession()?.first)

        model.confirmLeaveRoom()
        runCurrent()

        assertEquals(null, model.joinedRoom.value)
        assertEquals(null, harness.settings.roomSession())
        assertEquals(false, model.leavePrompt.value)
        assertIs<ClipSyncEngine.Status.Idle>(model.status.value)
    }

    @Test
    fun `cancelling the prompt keeps the room`() = runTest {
        val harness = Harness(backgroundScope)
        runCurrent()

        harness.model.requestLeaveRoom()
        runCurrent()
        harness.model.cancelLeaveRoom()
        runCurrent()

        assertEquals(false, harness.model.leavePrompt.value)
        assertEquals(room, harness.model.joinedRoom.value)
        assertIs<ClipSyncEngine.Status.Online>(harness.model.status.value)
    }

    @Test
    fun `nothing to confirm when there is no room`() = runTest {
        val harness = Harness(backgroundScope, savedRoom = false)
        runCurrent()

        harness.model.requestLeaveRoom()
        runCurrent()

        assertEquals(false, harness.model.leavePrompt.value)
    }

    @Test
    fun `switching off the clipboard watch stops the polling`() = runTest {
        val harness = Harness(backgroundScope)
        runCurrent()
        assertTrue(harness.clipboard.reads > 0)

        val whileOn = harness.clipboard.reads
        harness.settings.setWatchClipboard(false)
        advanceTimeBy(60_000)

        assertEquals(whileOn, harness.clipboard.reads)
    }

    @Test
    fun `switching the watch back on resumes polling`() = runTest {
        val harness = Harness(backgroundScope)
        runCurrent()
        harness.settings.setWatchClipboard(false)
        advanceTimeBy(1_000)
        val stopped = harness.clipboard.reads

        harness.settings.setWatchClipboard(true)
        advanceTimeBy(1_000)

        assertTrue(harness.clipboard.reads > stopped)
    }

    @Test
    fun `polling follows the platform interval`() = runTest {
        val harness = Harness(backgroundScope, FakeClipboard(interval = 4_000))
        runCurrent()
        assertEquals(1, harness.clipboard.reads)

        advanceTimeBy(3_999)
        assertEquals(1, harness.clipboard.reads)

        advanceTimeBy(2)
        assertEquals(2, harness.clipboard.reads)
    }

    @Test
    fun `a room that lands late is still treated as joined`() = runTest {
        val links = RecordingLinks(failFirstConnects = 1)
        val harness = Harness(backgroundScope, links = links)
        runCurrent()
        assertIs<ClipSyncEngine.Status.Offline>(harness.model.status.value)
        assertEquals(null, harness.model.joinedRoom.value)

        harness.model.retryNow()
        runCurrent()

        // 头一下没连上不代表完：续上之后界面得知道我们在哪间房，收来的内容也要写回剪贴板
        assertEquals(room, harness.model.joinedRoom.value)
        assertIs<ClipSyncEngine.Status.Online>(harness.model.status.value)
        assertEquals(2, links.connects)
    }

    private inner class Harness(
        private val scope: CoroutineScope,
        clipboard: FakeClipboard = FakeClipboard(),
        links: RecordingLinks = RecordingLinks(),
        savedRoom: Boolean = true,
    ) {
        val clipboard = clipboard
        val links = links
        val settings = AppSettings(MapSettings(mutableMapOf()))
        val model: AppModel

        init {
            if (savedRoom) settings.saveRoomSession(room, key)
            model = AppModel(scope, platform(clipboard), settings, links)
        }
    }

    private fun platform(clipboard: PlatformClipboard) = PlatformUi(
        clipboard = clipboard,
        picker = UnavailableFilePicker,
        opener = UnavailableFileOpener,
        store = InMemoryStore(),
        autostart = UnavailableAutostart,
        deviceName = "Laptop",
        deviceOs = "macOS",
        installId = "dev-1",
    )

    private class FakeClipboard(private val interval: Long = CLIPBOARD_POLL_MS) : PlatformClipboard {
        var reads = 0

        override fun pollIntervalMs(): Long = interval

        override suspend fun read(): String? {
            reads++
            return null
        }

        override suspend fun write(text: String) = Unit
    }

    private class RecordingLinks(private val failFirstConnects: Int = 0) : MqttLinkFactory {
        var connects = 0

        override fun create(config: ClipSyncEngine.Config, roomHash: String, safetyPrefix: String): MqttLink =
            FakeLink {
                connects++
                if (connects <= failFirstConnects) {
                    throw MqttException.ConnectionFailed(ReasonCode.SERVER_UNAVAILABLE, "broker down")
                }
            }
    }

    private class FakeLink(private val onConnect: () -> Unit = {}) : MqttLink {
        private val state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected.Idle)

        override val connectionState: StateFlow<ConnectionState> = state
        override val messages: Flow<Incoming> = flowOf()

        override suspend fun connect() {
            onConnect()
            state.value = ConnectionState.Connected
        }

        override suspend fun subscribe(topicFilter: String) = Unit
        override suspend fun publish(topic: String, payload: String) = Unit
        override suspend fun close() = Unit
    }

    private class InMemoryStore(var data: List<ClipItem> = emptyList()) : ClipStore {
        override suspend fun read(): List<ClipItem> = data
        override suspend fun write(items: List<ClipItem>) {
            data = items
        }

        override suspend fun deleteCache(localPath: String?) = Unit
        override suspend fun writeCache(id: String, name: String, bytes: ByteArray): String? = null
    }
}
