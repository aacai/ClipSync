package zhiqiu.app.cs.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import org.meshtastic.mqtt.ConnectionState
import org.meshtastic.mqtt.MqttException
import org.meshtastic.mqtt.ReasonCode
import zhiqiu.app.cs.files.ClipItem
import zhiqiu.app.cs.files.ClipRepository
import zhiqiu.app.cs.files.ClipStore
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 断线自愈只看引擎：假的链路按虚拟时钟记录每次 connect，不碰真 broker。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClipSyncEngineReconnectTest {

    private val room = "ABC123"

    @Test
    fun `a live session subscribes to the room and announces itself`() = runTest {
        val links = FakeLinks(clock = { currentTime })
        val engine = ClipSyncEngine(this, links)

        engine.start(config())

        assertIs<ClipSyncEngine.Status.Online>(engine.status.value)
        val link = links.created.single()
        assertEquals(listOf(ClipProtocol.roomFilter(hash())), link.subscribed)
        assertTrue(link.published.any { it.first == ClipProtocol.presenceTopic(hash(), "dev-1") })
        engine.stop()
    }

    @Test
    fun `a failed first connect keeps retrying and lands in the same room`() = runTest {
        val links = FakeLinks({ currentTime }, failBefore = 20_000)
        val engine = ClipSyncEngine(this, links)

        runCatching { engine.start(config()) }
        assertIs<ClipSyncEngine.Status.Offline>(engine.status.value)

        advanceTimeBy(60_000)

        assertEquals(listOf(0L, 5_000L, 15_000L, 35_000L), links.attempts)
        val online = assertIs<ClipSyncEngine.Status.Online>(engine.status.value)
        // 退避到第三次才连上，续上的还是原来那间房：安全码由同一把密钥算出来
        assertEquals(engineSafety(), online.safetyNumber)
        assertEquals(1, links.created.last().subscribed.size)
        engine.stop()
    }

    @Test
    fun `a rejected connection opens a fresh session`() = runTest {
        val links = FakeLinks(clock = { currentTime })
        val engine = ClipSyncEngine(this, links)
        engine.start(config())

        links.created.single().drop(MqttException.ConnectionRejected(ReasonCode.NOT_AUTHORIZED, "kicked"))
        runCurrent()
        assertIs<ClipSyncEngine.Status.Offline>(engine.status.value)

        advanceTimeBy(5_001)

        assertEquals(2, links.created.size)
        assertTrue(links.created[0].closed)
        assertIs<ClipSyncEngine.Status.Online>(engine.status.value)
        val fresh = links.created[1]
        assertEquals(listOf(ClipProtocol.roomFilter(hash())), fresh.subscribed)
        assertTrue(fresh.published.any { it.first == ClipProtocol.presenceTopic(hash(), "dev-1") })
        engine.stop()
    }

    @Test
    fun `a clip from a peer still gets through after recovery`() = runTest {
        val repository = ClipRepository(InMemoryStore())
        val links = FakeLinks(clock = { currentTime })
        val engine = ClipSyncEngine(this, links)
        val received = mutableListOf<ClipSyncEngine.ClipEvent>()
        val watch = launch { engine.clips.collect { received += it } }
        engine.start(config().copy(repository = repository))

        links.created.single().drop(MqttException.ConnectionLost(ReasonCode.UNSPECIFIED_ERROR, "wifi off"))
        advanceTimeBy(5_001)
        assertIs<ClipSyncEngine.Status.Online>(engine.status.value)

        links.created.last().deliver(clipMessage("from the other side"))
        runCurrent()

        assertEquals(listOf("from the other side"), repository.items.value.map { it.text })
        assertEquals(listOf("from the other side"), received.map { it.text })
        watch.cancel()
        engine.stop()
    }

    /** 同 clientId 开两条会话会被 broker 踢掉其中一条，来回踢就永远连不稳。 */
    @Test
    fun `never holds two sessions at once`() = runTest {
        val links = FakeLinks({ currentTime }, failBefore = 20_000)
        val engine = ClipSyncEngine(this, links)

        runCatching { engine.start(config()) }
        advanceTimeBy(60_000)

        assertEquals(listOf(0, 0, 0, 0), links.aliveBesideNew)
        assertIs<ClipSyncEngine.Status.Online>(engine.status.value)
        engine.stop()
    }

    /** 库自己续上连接后必须补一条 presence，否则同伴要等遗嘱过期才知道我们回来了。 */
    @Test
    fun `a healed connection re-announces the device`() = runTest {
        val links = FakeLinks(clock = { currentTime })
        val engine = ClipSyncEngine(this, links)
        engine.start(config())
        val link = links.created.single()
        val presence = ClipProtocol.presenceTopic(hash(), "dev-1")
        val announced = { link.published.count { it.first == presence } }
        assertEquals(1, announced())

        link.drop(MqttException.ConnectionLost(ReasonCode.UNSPECIFIED_ERROR, "wifi off"))
        runCurrent()
        link.heal()
        runCurrent()

        assertEquals(2, announced())
        assertIs<ClipSyncEngine.Status.Online>(engine.status.value)
        engine.stop()
    }

    @Test
    fun `a send that never reaches the broker still keeps the local copy`() = runTest {
        val repository = ClipRepository(InMemoryStore())
        val links = FakeLinks(clock = { currentTime })
        val engine = ClipSyncEngine(this, links)
        engine.start(config().copy(repository = repository))
        // 连接正在死：状态还是 Online，但 publish 已经抛了
        links.created.single().failPublish = true

        val thrown = runCatching { engine.publishText("copied on the way out") }.exceptionOrNull()
        assertIs<MqttException>(thrown)

        assertEquals(listOf("copied on the way out"), repository.items.value.map { it.text })
        engine.stop()
    }

    @Test
    fun `leaving the room stops the retries`() = runTest {
        val links = FakeLinks({ currentTime }, failBefore = Long.MAX_VALUE)
        val engine = ClipSyncEngine(this, links)

        runCatching { engine.start(config()) }
        engine.stop()
        advanceTimeBy(600_000)

        assertEquals(listOf(0L), links.attempts)
        assertIs<ClipSyncEngine.Status.Idle>(engine.status.value)
    }

    @Test
    fun `backoff doubles and caps at a minute`() = runTest {
        val links = FakeLinks({ currentTime }, failBefore = Long.MAX_VALUE)
        val engine = ClipSyncEngine(this, links)

        runCatching { engine.start(config()) }
        advanceTimeBy(200_000)

        assertEquals(listOf(0L, 5_000L, 15_000L, 35_000L, 75_000L, 135_000L, 195_000L), links.attempts)
        assertIs<ClipSyncEngine.Status.Offline>(engine.status.value)
        engine.stop()
    }

    @Test
    fun `a connection the library restored is left alone`() = runTest {
        val links = FakeLinks(clock = { currentTime })
        val engine = ClipSyncEngine(this, links)
        engine.start(config())
        val link = links.created.single()

        link.drop(MqttException.ConnectionLost(ReasonCode.UNSPECIFIED_ERROR, "wifi off"))
        runCurrent()
        assertIs<ClipSyncEngine.Status.Offline>(engine.status.value)

        link.heal()
        runCurrent()
        assertIs<ClipSyncEngine.Status.Online>(engine.status.value)

        advanceTimeBy(60_000)
        assertEquals(1, links.created.size)
        engine.stop()
    }

    /** 新面孔刚订阅上，错过我们上一次心跳就看不见彼此：见到就回一条。 */
    @Test
    fun `a peer we just discovered gets an immediate hello`() = runTest {
        val links = FakeLinks(clock = { currentTime })
        val engine = ClipSyncEngine(this, links)
        engine.start(config())
        val link = links.created.single()
        runCurrent()
        val presence = ClipProtocol.presenceTopic(hash(), "dev-1")
        val announced = { link.published.count { it.first == presence } }
        assertEquals(1, announced())

        link.deliver(presenceMessage("dev-2", "Phone", online = true))
        runCurrent()

        assertEquals(2, announced())
        assertEquals(1, engine.devices.value.size)

        link.deliver(presenceMessage("dev-2", "Phone", online = true))
        runCurrent()

        assertEquals(2, announced())
        engine.stop()
    }

    @Test
    fun `presence heartbeat stays slow now that discovery answers back`() = runTest {
        val links = FakeLinks(clock = { currentTime })
        val engine = ClipSyncEngine(this, links)
        engine.start(config())
        val link = links.created.single()
        val presence = ClipProtocol.presenceTopic(hash(), "dev-1")
        val announced = { link.published.count { it.first == presence } }

        advanceTimeBy(60_000)
        assertEquals(1, announced())

        advanceTimeBy(60_001)
        assertEquals(2, announced())
        engine.stop()
    }

    /** 回到前台踢一脚：不用等退避跑完，房间立刻续上。 */
    @Test
    fun `a kick lands the room without waiting out the backoff`() = runTest {
        val links = FakeLinks({ currentTime }, failBefore = 1_000)
        val engine = ClipSyncEngine(this, links)

        runCatching { engine.start(config()) }
        advanceTimeBy(2_000)
        assertEquals(listOf(0L), links.attempts)

        engine.retryNow()
        runCurrent()

        assertEquals(listOf(0L, 2_000L), links.attempts)
        assertIs<ClipSyncEngine.Status.Online>(engine.status.value)
        engine.stop()
    }

    /** 连着的三下和一下一样：踢的是「现在就试」这个念头，不是一次尝试。 */
    @Test
    fun `kicks collapse into one attempt`() = runTest {
        val links = FakeLinks({ currentTime }, failBefore = Long.MAX_VALUE)
        val engine = ClipSyncEngine(this, links)

        runCatching { engine.start(config()) }
        advanceTimeBy(2_000)
        engine.retryNow()
        engine.retryNow()
        engine.retryNow()
        runCurrent()

        assertEquals(listOf(0L, 2_000L), links.attempts)
        engine.stop()
    }

    @Test
    fun `a kick changes nothing while online`() = runTest {
        val links = FakeLinks({ currentTime }, failBefore = 0)
        val engine = ClipSyncEngine(this, links)
        engine.start(config())

        engine.retryNow()
        advanceTimeBy(600_000)

        assertEquals(1, links.created.size)
        engine.stop()
    }

    @Test
    fun `a kick with no room wanted opens nothing`() = runTest {
        val links = FakeLinks({ currentTime }, failBefore = Long.MAX_VALUE)
        val engine = ClipSyncEngine(this, links)

        engine.retryNow()
        advanceTimeBy(600_000)

        assertEquals(emptyList(), links.attempts)
    }

    /** 告别（含遗嘱）在我们断线期间会整个丢，名单只能靠时间自己收：停跳心跳的同伴要自己掉出去。 */
    @Test
    fun `a peer who stops beating drops out on his own`() = runTest {
        val links = FakeLinks(clock = { currentTime })
        val engine = ClipSyncEngine(this, links) { currentTime }
        engine.start(config())
        val link = links.created.single()
        runCurrent()

        link.deliver(presenceMessage("dev-2", "Phone", online = true))
        runCurrent()
        assertEquals(1, engine.devices.value.size)

        // 三条心跳还没到，不该动他
        advanceTimeBy(240_000)
        runCurrent()
        assertEquals(1, engine.devices.value.size)

        // 过期要等下一次心跳来清：清理挂在本来就有的唤醒上，不多要一次无线电
        advanceTimeBy(240_000)
        runCurrent()
        assertEquals(emptyList(), engine.devices.value)
        engine.stop()
    }

    /** 迟到的那条不作数：晚到的 goodbye 不该把一个在场的同伴抹掉。 */
    @Test
    fun `a late goodbye cannot erase a peer who is here`() = runTest {
        val links = FakeLinks(clock = { currentTime })
        val engine = ClipSyncEngine(this, links) { currentTime }
        engine.start(config())
        val link = links.created.single()
        runCurrent()

        link.deliver(presenceMessage("dev-2", "Phone", online = true, ts = 2_000))
        runCurrent()
        link.deliver(presenceMessage("dev-2", "Phone", online = false, ts = 1_000))
        runCurrent()
        assertEquals(1, engine.devices.value.size)

        link.deliver(presenceMessage("dev-2", "Phone", online = false, ts = 3_000))
        runCurrent()
        assertEquals(emptyList(), engine.devices.value)
        engine.stop()
    }

    /** 密码打错的设备互相看得见 presence，却一条剪贴板都收不到：不能算进人数。 */
    @Test
    fun `a peer on a different key is not counted in the room`() = runTest {
        val links = FakeLinks(clock = { currentTime })
        val engine = ClipSyncEngine(this, links) { currentTime }
        engine.start(config())
        val link = links.created.single()
        runCurrent()

        link.deliver(presenceMessage("dev-2", "Phone", online = true, sp = "999"))
        runCurrent()

        assertEquals(emptyList(), engine.devices.value)
        assertEquals(listOf("Phone"), engine.keyMismatch.value)
        engine.stop()
    }

    /** 解得开他的密文就是活证据：有动静的同伴不该被 TTL 误伤。 */
    @Test
    fun `a peer with recent activity survives the heartbeat gap`() = runTest {
        val links = FakeLinks(clock = { currentTime })
        val engine = ClipSyncEngine(this, links) { currentTime }
        engine.start(config())
        val link = links.created.single()
        runCurrent()

        link.deliver(presenceMessage("dev-2", "Phone", online = true))
        runCurrent()
        advanceTimeBy(300_000)
        link.deliver(clipMessage("copied just now"))
        runCurrent()

        // 再往后 240s：光看那条 presence 早就过期了
        advanceTimeBy(240_000)
        runCurrent()
        assertEquals(1, engine.devices.value.size)

        advanceTimeBy(240_000)
        runCurrent()
        assertEquals(emptyList(), engine.devices.value)
        engine.stop()
    }

    /** 掉线期间没人可证：留着的名单是一个没人认的数，清了重连后再点名重建。 */
    @Test
    fun `a lost link leaves no roster behind`() = runTest {
        val links = FakeLinks(clock = { currentTime })
        val engine = ClipSyncEngine(this, links) { currentTime }
        engine.start(config())
        val link = links.created.single()
        runCurrent()
        link.deliver(presenceMessage("dev-2", "Phone", online = true))
        runCurrent()
        assertEquals(1, engine.devices.value.size)

        link.drop(MqttException.ConnectionLost(ReasonCode.UNSPECIFIED_ERROR, "wifi off"))
        runCurrent()

        assertEquals(emptyList(), engine.devices.value)
        engine.stop()
    }

    /** 进房和重连各点一次名；同伴回一条 presence，名单一轮就重建起来，不用等下一次心跳。 */
    @Test
    fun `a roll-call rebuilds the roster after the link heals`() = runTest {
        val links = FakeLinks(clock = { currentTime })
        val engine = ClipSyncEngine(this, links) { currentTime }
        engine.start(config())
        val link = links.created.single()
        runCurrent()
        val sync = ClipProtocol.syncTopic(hash())
        val called = { link.published.count { it.first == sync } }
        assertEquals(1, called())

        link.deliver(presenceMessage("dev-2", "Phone", online = true))
        runCurrent()
        assertEquals(1, engine.devices.value.size)

        link.drop(MqttException.ConnectionLost(ReasonCode.UNSPECIFIED_ERROR, "wifi off"))
        runCurrent()
        link.heal()
        runCurrent()

        assertEquals(2, called())
        assertEquals(emptyList(), engine.devices.value)

        // 同伴听到点名就回话：不用等心跳，名单立刻回来
        link.deliver(presenceMessage("dev-2", "Phone", online = true, ts = 1_700_000_000_500))
        runCurrent()
        assertEquals(1, engine.devices.value.size)
        engine.stop()
    }

    /** 点名不该把两个人算成两个：他自己那条 presence 会正常把名单填回来。 */
    @Test
    fun `a roll-call is answered but never counted as a peer`() = runTest {
        val links = FakeLinks(clock = { currentTime })
        val engine = ClipSyncEngine(this, links) { currentTime }
        engine.start(config())
        val link = links.created.single()
        val presence = ClipProtocol.presenceTopic(hash(), "dev-1")
        val announced = { link.published.count { it.first == presence } }
        runCurrent()
        assertEquals(1, announced())

        link.deliver(rollCall("dev-9"))
        runCurrent()

        assertEquals(2, announced())
        assertEquals(emptyList(), engine.devices.value)
        engine.stop()
    }

    @Test
    fun `a room that never opened is not retried`() = runTest {
        val links = FakeLinks(clock = { currentTime })
        val engine = ClipSyncEngine(this, links)
        val broken = config().copy(roomKey = ByteArray(RoomCrypto.KEY_SIZE - 1))

        assertFailsWith<IllegalArgumentException> { engine.start(broken) }
        advanceTimeBy(600_000)

        assertEquals(emptyList(), links.attempts)
        assertIs<ClipSyncEngine.Status.Offline>(engine.status.value)
        engine.stop()
    }

    private suspend fun engineSafety(): String =
        RoomCrypto.restore(ByteArray(RoomCrypto.KEY_SIZE) { 7 }).safetyNumber()

    private suspend fun hash(): String = ClipProtocol.roomHash(room)

    /** 同伴那侧的一条真实密文：同一把房间密钥加密，AAD 是 clip 主题。 */
    private suspend fun clipMessage(text: String, from: String = "dev-2"): Incoming {
        val topic = ClipProtocol.clipTopic(hash())
        val payload = ClipProtocol.encode(ClipPayload(text = text, len = text.length))
        val ct = RoomCrypto.restore(ByteArray(RoomCrypto.KEY_SIZE) { 7 })
            .encrypt(payload.encodeToByteArray(), topic.encodeToByteArray())
        val envelope = ClipEnvelope(
            mid = sha256Hex(payload.encodeToByteArray() + from.encodeToByteArray()).take(16),
            ts = 1_700_000_000_000,
            dev = from,
            name = "Phone",
            kind = ClipRepository.KIND_TEXT,
            ct = ct,
        )
        return Incoming(topic, ClipProtocol.encode(envelope))
    }

    /** sp 留空 = 和我们同一把钥匙；填别的等于「同屋但密码打错」。 */
    private suspend fun presenceMessage(
        from: String,
        name: String,
        online: Boolean,
        sp: String = "",
        ts: Long = 1_700_000_000_000,
    ): Incoming {
        val hash = hash()
        val envelope = PresenceEnvelope(
            dev = from,
            name = name,
            os = "Android",
            online = online,
            ts = ts,
            sp = sp.ifEmpty { ClipProtocol.safetyPrefix(engineSafety()) },
        )
        return Incoming(ClipProtocol.presenceTopic(hash, from), ClipProtocol.encode(envelope))
    }

    /** broker 递来的一条点名：收到的人该回一条 presence。 */
    private suspend fun rollCall(from: String): Incoming {
        val envelope = PresenceEnvelope(
            dev = from,
            name = "Phone",
            os = "Android",
            online = true,
            ts = 1_700_000_000_000,
            sp = "999",
            answer = true,
        )
        return Incoming(ClipProtocol.syncTopic(hash()), ClipProtocol.encode(envelope))
    }

    private fun config() = ClipSyncEngine.Config(
        roomId = room,
        roomKey = ByteArray(RoomCrypto.KEY_SIZE) { 7 },
        deviceId = "dev-1",
        deviceName = "Laptop",
        deviceOs = "macOS",
    )

    private class InMemoryStore(var data: List<ClipItem> = emptyList()) : ClipStore {
        override suspend fun read(): List<ClipItem> = data
        override suspend fun write(items: List<ClipItem>) {
            data = items
        }

        override suspend fun deleteCache(localPath: String?) = Unit
        override suspend fun writeCache(id: String, name: String, bytes: ByteArray): String? = null
    }

    private class FakeLinks(
        private val clock: () -> Long,
        private val failBefore: Long = 0L,
    ) : MqttLinkFactory {
        val created = mutableListOf<FakeLink>()

        /** 每次新开一条会话时，手上还剩几条没关的。 */
        val aliveBesideNew = mutableListOf<Int>()

        override fun create(config: ClipSyncEngine.Config, roomHash: String, safetyPrefix: String): MqttLink {
            aliveBesideNew += created.count { !it.closed }
            return FakeLink {
                attempts += clock()
                if (clock() < failBefore) {
                    throw MqttException.ConnectionFailed(ReasonCode.SERVER_UNAVAILABLE, "broker down")
                }
            }.also { created += it }
        }

        val attempts = mutableListOf<Long>()
    }

    private class FakeLink(private val onConnect: () -> Unit) : MqttLink {

        private val state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected.Idle)
        private val received = MutableSharedFlow<Incoming>(extraBufferCapacity = 32)

        val subscribed = mutableListOf<String>()
        val published = mutableListOf<Pair<String, String>>()
        var closed = false
        var failPublish = false

        override val connectionState: StateFlow<ConnectionState> = state
        override val messages: Flow<Incoming> = received

        override suspend fun connect() {
            onConnect()
            state.value = ConnectionState.Connected
        }

        override suspend fun subscribe(topicFilter: String) {
            subscribed += topicFilter
        }

        override suspend fun publish(topic: String, payload: String) {
            if (failPublish) throw MqttException.ConnectionLost(ReasonCode.UNSPECIFIED_ERROR, "socket gone")
            published += topic to payload
        }

        override suspend fun close() {
            closed = true
        }

        /** 模拟库放弃重连：MQTT 层报出带原因的 Disconnected，引擎接手。 */
        fun drop(reason: MqttException) {
            state.value = ConnectionState.Disconnected(reason)
        }

        /** 模拟库自己把连接续上了。 */
        fun heal() {
            state.value = ConnectionState.Connected
        }

        /** 模拟 broker 递来一条房间里的消息。 */
        fun deliver(message: Incoming) {
            received.tryEmit(message)
        }
    }
}
