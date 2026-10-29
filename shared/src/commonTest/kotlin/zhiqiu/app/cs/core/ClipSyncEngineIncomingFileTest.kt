package zhiqiu.app.cs.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.mqtt.ConnectionState
import zhiqiu.app.cs.files.ClipItem
import zhiqiu.app.cs.files.ClipRepository
import zhiqiu.app.cs.files.ClipStore
import zhiqiu.app.cs.files.EncryptedFile
import zhiqiu.app.cs.files.FileClip
import zhiqiu.app.cs.files.FileCrypto
import zhiqiu.app.cs.files.FilePayload
import zhiqiu.app.cs.files.FileTransport
import zhiqiu.app.cs.files.UploadedLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 房间里 announce 一条文件，本机自己把字节取回来：已经有的一份不下第二遍，缓存丢了的要重新下。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClipSyncEngineIncomingFileTest {

    private val room = "INCOMING"
    private val plain = ByteArray(64) { 5 }

    @Test
    fun `a file announced in the room downloads itself`() = runTest {
        val cipher = cipher()
        val transport = FakeTransport(cipher.cipher)
        val store = CacheStore()
        val links = FakeLinks()
        val engine = ClipSyncEngine(this, links, { transport }) { currentTime }
        val seen = mutableListOf<ClipSyncEngine.ClipEvent>()
        val watch = launch { engine.clips.collect { seen += it } }
        engine.start(config(ClipRepository(store)))
        runCurrent()

        links.created.single().deliver(incoming(fileClip(cipher, "https://host.test/1.bin")))
        runCurrent()

        assertEquals(listOf("https://host.test/1.bin"), transport.downloads, "收到元信息就该自己起下载")
        val event = seen.single()
        assertEquals("shot.png", event.text)
        val file = assertNotNull(event.file)
        assertTrue(file.cached, "下完要带上已经落盘的状态")
        assertEquals(store.writes.keys.single(), file.localPath)
        assertEquals(plain.toList(), assertNotNull(store.writes[file.localPath]).toList())
        watch.cancel()
        engine.stop()
    }

    @Test
    fun `content already on disk goes straight to the collector without a second download`() = runTest {
        val cipher = cipher()
        val transport = FakeTransport(cipher.cipher)
        val store = CacheStore()
        val repository = ClipRepository(store)
        val cached = fileClip(cipher, "https://host.test/1.bin")
            .copy(id = "old", cached = true, localPath = store.put("old", plain))
        repository.add(item(cached))
        val links = FakeLinks()
        val engine = ClipSyncEngine(this, links, { transport }) { currentTime }
        val seen = mutableListOf<ClipSyncEngine.ClipEvent>()
        val watch = launch { engine.clips.collect { seen += it } }
        engine.start(config(repository))
        runCurrent()

        links.created.single().deliver(incoming(fileClip(cipher, "https://host.test/2.bin")))
        runCurrent()

        assertEquals(emptyList<String>(), transport.downloads, "本机已经有这份内容，不该再下一遍")
        val event = seen.single()
        assertEquals("Phone", event.senderName)
        assertEquals(cached.localPath, assertNotNull(event.file).localPath, "同内容再发一次也要立刻给出能粘的路径")
        watch.cancel()
        engine.stop()
    }

    @Test
    fun `a cache entry whose bytes are gone is downloaded again`() = runTest {
        val cipher = cipher()
        val transport = FakeTransport(cipher.cipher)
        val store = CacheStore()
        val repository = ClipRepository(store)
        val stale = fileClip(cipher, "https://host.test/1.bin")
            .copy(id = "old", cached = true, localPath = "/cache/old-died.png")
        repository.add(item(stale))
        val links = FakeLinks()
        val engine = ClipSyncEngine(this, links, { transport }) { currentTime }
        val seen = mutableListOf<ClipSyncEngine.ClipEvent>()
        val watch = launch { engine.clips.collect { seen += it } }
        engine.start(config(repository))
        runCurrent()

        links.created.single().deliver(incoming(fileClip(cipher, "https://host.test/2.bin")))
        runCurrent()

        assertEquals(listOf("https://host.test/2.bin"), transport.downloads, "缓存文件已经没了的条目要当没下过")
        val file = assertNotNull(seen.single().file)
        assertEquals(plain.toList(), assertNotNull(store.writes[file.localPath]).toList())
        watch.cancel()
        engine.stop()
    }

    private fun item(file: FileClip) = ClipItem(
        id = file.id,
        kind = ClipRepository.KIND_FILE,
        ts = file.ts,
        senderId = file.senderId,
        senderName = file.senderName,
        text = file.name,
        file = file,
    )

    private suspend fun cipher(): EncryptedFile = crypto().encrypt("shot.png", "image/png", plain)

    private suspend fun crypto(): FileCrypto =
        FileCrypto(RoomCrypto.restore(ByteArray(RoomCrypto.KEY_SIZE) { 7 }), topic())

    private suspend fun topic(): String = ClipProtocol.clipTopic(ClipProtocol.roomHash(room))

    private fun fileClip(cipher: EncryptedFile, link: String): FileClip = FileClip(
        id = "m-1",
        name = cipher.name,
        mime = cipher.mime,
        size = cipher.size,
        sha256 = cipher.sha256,
        link = link,
        expiresAt = 1_700_000_000_000,
        ts = 1_700_000_000_000,
        senderId = "dev-2",
        senderName = "Phone",
    )

    private suspend fun incoming(clip: FileClip): Incoming {
        val target = topic()
        val payload = ClipProtocol.encode(
            FilePayload(
                name = clip.name,
                mime = clip.mime,
                size = clip.size,
                sha256 = clip.sha256,
                link = clip.link,
                expiresAt = clip.expiresAt,
            ),
        )
        val wire = RoomCrypto.restore(ByteArray(RoomCrypto.KEY_SIZE) { 7 })
            .encryptBytes(payload.encodeToByteArray(), target.encodeToByteArray())
        return Incoming(
            target,
            ClipProtocol.encode(
                ClipEnvelope(
                    mid = clip.id,
                    ts = clip.ts,
                    dev = clip.senderId,
                    name = clip.senderName,
                    kind = ClipRepository.KIND_FILE,
                    ct = FileCrypto.encodeCipher(wire),
                ),
            ),
        )
    }

    private fun config(repository: ClipRepository) = ClipSyncEngine.Config(
        roomId = room,
        roomKey = ByteArray(RoomCrypto.KEY_SIZE) { 7 },
        deviceId = "dev-1",
        deviceName = "Laptop",
        deviceOs = "macOS",
        repository = repository,
    )

    private class FakeTransport(private val cipher: ByteArray) : FileTransport {
        val downloads = mutableListOf<String>()

        override suspend fun upload(
            fileName: String,
            bytes: ByteArray,
            onProgress: (sent: Long, total: Long) -> Unit,
        ): UploadedLink = UploadedLink("https://host.test/uploaded.bin", 3_600_000L)

        override suspend fun download(
            link: String,
            onProgress: (received: Long, total: Long) -> Unit,
        ): ByteArray {
            downloads += link
            return cipher
        }
    }

    /** 缓存按路径存字节：readCache 拿不到就是这份东西本机已经没有。 */
    private class CacheStore : ClipStore {
        val writes = linkedMapOf<String, ByteArray>()
        private var data: List<ClipItem> = emptyList()

        fun put(id: String, bytes: ByteArray): String {
            val path = "/cache/$id.png"
            writes[path] = bytes
            return path
        }

        override suspend fun read(): List<ClipItem> = data

        override suspend fun write(items: List<ClipItem>) {
            data = items
        }

        override suspend fun deleteCache(localPath: String?) {
            localPath?.let { writes -= it }
        }

        override suspend fun writeCache(id: String, name: String, bytes: ByteArray): String = put(id, bytes)

        override suspend fun readCache(localPath: String): ByteArray? = writes[localPath]
    }

    private class FakeLinks : MqttLinkFactory {
        val created = mutableListOf<FakeLink>()

        override fun create(config: ClipSyncEngine.Config, roomHash: String, willPayload: String): MqttLink =
            FakeLink().also { created += it }
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

        override suspend fun close() {
            state.value = ConnectionState.Disconnected.Idle
        }

        fun deliver(message: Incoming) {
            received.tryEmit(message)
        }
    }
}
