package zhiqiu.app.cs.core

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.mqtt.ConnectionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import zhiqiu.app.cs.files.ClipItem
import zhiqiu.app.cs.files.ClipRepository
import zhiqiu.app.cs.files.ClipStore
import zhiqiu.app.cs.ui.AppModel
import zhiqiu.app.cs.ui.CLIPBOARD_POLL_MS
import zhiqiu.app.cs.ui.PlatformClipboard
import zhiqiu.app.cs.ui.PlatformUi
import zhiqiu.app.cs.ui.UnavailableAutostart
import zhiqiu.app.cs.ui.UnavailableFileOpener
import zhiqiu.app.cs.ui.UnavailableFilePicker

/**
 * 回声不盖新复制：这台设备发出去的文本，被屋里另一份 ClipSync 当成新拷贝推回来时，
 * 只进列表，不再写剪贴板——用户刚复制的那一份不能被旧的顶掉。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppModelClipboardEchoTest {

    private val room = "ABC123"
    private val key = ByteArray(RoomCrypto.KEY_SIZE) { 7 }
    private val password = "hunter2"

    @Test
    fun `our own send coming back around leaves the clipboard alone`() = runTest {
        val clipboard = FakeClipboard()
        val links = RoomLinks()
        val model = harness(clipboard, links, backgroundScope)
        runCurrent()
        assertIs<ClipSyncEngine.Status.Online>(model.status.value)

        // 第一次复制：发出去，也记进列表
        clipboard.text = "first"
        advanceTimeBy(CLIPBOARD_POLL_MS + 1)
        runCurrent()
        assertTrue(model.items.value.any { it.text == "first" })

        // 用户接着复制了更新的一份
        clipboard.text = "second"
        advanceTimeBy(CLIPBOARD_POLL_MS + 1)
        runCurrent()

        // 同机的另一份 ClipSync 把 first 当新拷贝推回来
        clipboard.writes.clear()
        links.deliver(clipMessage("first"))
        runCurrent()
        assertEquals(emptyList(), clipboard.writes)

        // 真正的远端内容照样进剪贴板
        links.deliver(clipMessage("from the phone"))
        runCurrent()
        assertEquals(listOf("from the phone"), clipboard.writes)
    }

    @Test
    fun `a clip we copied for a peer is not pushed back onto us`() = runTest {
        val clipboard = FakeClipboard()
        val links = RoomLinks()
        val model = harness(clipboard, links, backgroundScope)
        runCurrent()

        model.sendText("sent from the box")
        runCurrent()

        clipboard.writes.clear()
        links.deliver(clipMessage("sent from the box"))
        runCurrent()

        assertEquals(emptyList(), clipboard.writes)
        assertTrue(model.items.value.any { it.text == "sent from the box" })
    }

    private fun harness(
        clipboard: FakeClipboard,
        links: RoomLinks,
        scope: CoroutineScope,
    ): AppModel {
        val settings = AppSettings(MapSettings(mutableMapOf()), "test-install")
        settings.saveRoomSession(room, key, password)
        return AppModel(
            scope,
            PlatformUi(
                clipboard = clipboard,
                picker = UnavailableFilePicker,
                opener = UnavailableFileOpener,
                store = InMemoryStore(),
                autostart = UnavailableAutostart,
                deviceName = "Laptop",
                deviceOs = "macOS",
                installId = "dev-1",
            ),
            settings,
            links,
        )
    }

    private class FakeClipboard : PlatformClipboard {
        var text: String? = null
        var reads = 0
        val writes = mutableListOf<String>()

        override fun pollIntervalMs(): Long = CLIPBOARD_POLL_MS
        override suspend fun read(): String? {
            reads++
            return text
        }

        override suspend fun write(text: String) {
            writes += text
            this.text = text
        }
    }

    private class RoomLinks : MqttLinkFactory {
        var link: FakeLink? = null

        override fun create(config: ClipSyncEngine.Config, roomHash: String, willPayload: String): MqttLink =
            FakeLink().also { link = it }

        /** broker 递来一条房间里的密文。 */
        suspend fun deliver(message: Incoming) {
            link!!.deliver(message)
        }
    }

    private class FakeLink : MqttLink {
        private val state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected.Idle)
        private val received = MutableSharedFlow<Incoming>(extraBufferCapacity = 32)

        override val connectionState: StateFlow<ConnectionState> = state
        override val messages: Flow<Incoming> = received

        override suspend fun connect() {
            state.value = ConnectionState.Connected
        }

        override suspend fun subscribe(topicFilter: String) = Unit
        override suspend fun publish(topic: String, payload: String) = Unit
        override suspend fun close() = Unit

        fun deliver(message: Incoming) {
            received.tryEmit(message)
        }
    }

    private class InMemoryStore(var data: List<ClipItem> = emptyList()) : ClipStore {
        override suspend fun read(): List<ClipItem> = data
        override suspend fun write(items: List<ClipItem>) {
            data = items
        }

        override suspend fun deleteCache(localPath: String?) = Unit
        override suspend fun writeCache(id: String, name: String, bytes: ByteArray): String = "/cache/$id/$name"
        override suspend fun readCache(localPath: String): ByteArray? = null
    }

    private suspend fun clipMessage(text: String, from: String = "dev-2"): Incoming {
        val topic = ClipProtocol.clipTopic(ClipProtocol.roomHash(room))
        val payload = ClipProtocol.encode(ClipPayload(text = text, len = text.length))
        val ct = RoomCrypto.restore(key).encrypt(payload.encodeToByteArray(), topic.encodeToByteArray())
        val envelope = ClipEnvelope(
            mid = ClipProtocol.randomMid(),
            ts = 1_700_000_000_000,
            dev = from,
            name = "Phone",
            kind = ClipRepository.KIND_TEXT,
            ct = ct,
        )
        return Incoming(topic, ClipProtocol.encode(envelope))
    }
}
