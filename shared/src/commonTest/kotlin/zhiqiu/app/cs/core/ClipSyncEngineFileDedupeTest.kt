package zhiqiu.app.cs.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.meshtastic.mqtt.ConnectionState
import zhiqiu.app.cs.files.FileCrypto
import zhiqiu.app.cs.files.FilePayload
import zhiqiu.app.cs.files.FileTransport
import zhiqiu.app.cs.files.UploadedLink
import kotlin.test.Test
import kotlin.test.assertEquals

/** 同一份内容不该在房间里传第二遍：链接还活着就复用，只补一条元信息。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClipSyncEngineFileDedupeTest {

    private val room = "ROOM1"
    private val bytes = ByteArray(64) { 3 }

    @Test
    fun `the same file is uploaded once and the second send reuses that link`() = runTest {
        val transport = CountingTransport()
        val links = FakeLinks()
        val engine = engine(this, links, transport)
        engine.start(config())

        val first = engine.publishFile("shot.png", "image/png", bytes)
        val second = engine.publishFile("shot.png", "image/png", bytes)

        assertEquals(1, transport.uploads.size, "同一份文件不该传第二遍")
        assertEquals(first?.file?.link, second?.file?.link)
        assertEquals(2, clipEnvelopes(links).size, "元信息还是发两条，屋里才看得到又来了一次")
        engine.stop()
    }

    @Test
    fun `the reused link is the one the room gets told about`() = runTest {
        val transport = CountingTransport()
        val links = FakeLinks()
        val engine = engine(this, links, transport)
        engine.start(config())

        engine.publishFile("shot.png", "image/png", bytes)
        engine.publishFile("shot.png", "image/png", bytes)

        val payloads = clipEnvelopes(links).map { payloadOf(it) }
        assertEquals(2, payloads.size)
        assertEquals(payloads[0].link, payloads[1].link)
        assertEquals(payloads[0].sha256, payloads[1].sha256)
        engine.stop()
    }

    @Test
    fun `another name is another file and gets uploaded again`() = runTest {
        val transport = CountingTransport()
        val engine = engine(this, FakeLinks(), transport)
        engine.start(config())

        engine.publishFile("shot.png", "image/png", bytes)
        engine.publishFile("other.png", "image/png", bytes)

        assertEquals(2, transport.uploads.size, "文件名进了密文的附加数据，改名就是另一份")
        engine.stop()
    }

    @Test
    fun `a link that is about to expire is uploaded again`() = runTest {
        val transport = CountingTransport(ttlMillis = 30_000L)
        val engine = engine(this, FakeLinks(), transport)
        engine.start(config())

        engine.publishFile("shot.png", "image/png", bytes)
        advanceTimeBy(120_000)
        engine.publishFile("shot.png", "image/png", bytes)

        assertEquals(2, transport.uploads.size, "快过期的链接不能接着用")
        engine.stop()
    }

    @Test
    fun `another room does not reuse the link`() = runTest {
        val transport = CountingTransport()
        val links = FakeLinks()
        val engine = engine(this, links, transport)
        engine.start(config())

        engine.publishFile("shot.png", "image/png", bytes)
        engine.stop()
        engine.start(config(room = "ROOM2"))
        engine.publishFile("shot.png", "image/png", bytes)

        assertEquals(2, transport.uploads.size, "别的房间解不开这条链接")
        engine.stop()
    }

    private fun engine(scope: TestScope, links: FakeLinks, transport: CountingTransport) =
        ClipSyncEngine(scope, links, { transport }) { scope.currentTime }

    private fun config(room: String = this.room) = ClipSyncEngine.Config(
        roomId = room,
        roomKey = ByteArray(RoomCrypto.KEY_SIZE) { 7 },
        deviceId = "dev-1",
        deviceName = "Laptop",
        deviceOs = "macOS",
    )

    private suspend fun clipTopic(): String = ClipProtocol.clipTopic(ClipProtocol.roomHash(room))

    private suspend fun clipEnvelopes(links: FakeLinks): List<ClipEnvelope> {
        val topic = clipTopic()
        return links.created.flatMap { link ->
            link.published.filter { it.first == topic }.map { ClipProtocol.decode<ClipEnvelope>(it.second) }
        }
    }

    private suspend fun payloadOf(envelope: ClipEnvelope): FilePayload {
        val wire = FileCrypto.decodeCipher(envelope.ct)
        val json = RoomCrypto.restore(ByteArray(RoomCrypto.KEY_SIZE) { 7 })
            .decryptBytes(wire, clipTopic().encodeToByteArray())
            .decodeToString()
        return ClipProtocol.decode<FilePayload>(json)
    }

    private class CountingTransport(private val ttlMillis: Long = 3_600_000L) : FileTransport {
        val uploads = mutableListOf<String>()

        override suspend fun upload(
            fileName: String,
            bytes: ByteArray,
            onProgress: (sent: Long, total: Long) -> Unit,
        ): UploadedLink {
            uploads += fileName
            onProgress(bytes.size.toLong(), bytes.size.toLong())
            return UploadedLink("https://host.test/${uploads.size}.bin", ttlMillis)
        }

        override suspend fun download(link: String, onProgress: (received: Long, total: Long) -> Unit): ByteArray {
            onProgress(0L, 0L)
            return ByteArray(0)
        }
    }

    private class FakeLinks : MqttLinkFactory {
        val created = mutableListOf<FakeLink>()

        override fun create(config: ClipSyncEngine.Config, roomHash: String, willPayload: String): MqttLink =
            FakeLink().also { created += it }
    }

    private class FakeLink : MqttLink {

        private val state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected.Idle)
        private val received = MutableSharedFlow<Incoming>(extraBufferCapacity = 32)

        val published = mutableListOf<Pair<String, String>>()

        override val connectionState: StateFlow<ConnectionState> = state
        override val messages: Flow<Incoming> = received

        override suspend fun connect() {
            state.value = ConnectionState.Connected
        }

        override suspend fun subscribe(topicFilter: String) = Unit

        override suspend fun publish(topic: String, payload: String) {
            published += topic to payload
        }

        override suspend fun close() {
            state.value = ConnectionState.Disconnected.Idle
        }
    }
}
