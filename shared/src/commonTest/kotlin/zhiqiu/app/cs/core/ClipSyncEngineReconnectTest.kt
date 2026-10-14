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
import org.meshtastic.mqtt.ConnectionState
import org.meshtastic.mqtt.MqttException
import org.meshtastic.mqtt.ReasonCode
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

    private fun config() = ClipSyncEngine.Config(
        roomId = room,
        roomKey = ByteArray(RoomCrypto.KEY_SIZE) { 7 },
        deviceId = "dev-1",
        deviceName = "Laptop",
        deviceOs = "macOS",
    )

    private class FakeLinks(
        private val clock: () -> Long,
        private val failBefore: Long = 0L,
    ) : MqttLinkFactory {
        val created = mutableListOf<FakeLink>()
        val attempts = mutableListOf<Long>()

        override fun create(config: ClipSyncEngine.Config, roomHash: String, safetyPrefix: String): MqttLink =
            FakeLink {
                attempts += clock()
                if (clock() < failBefore) {
                    throw MqttException.ConnectionFailed(ReasonCode.SERVER_UNAVAILABLE, "broker down")
                }
            }.also { created += it }
    }

    private class FakeLink(private val onConnect: () -> Unit) : MqttLink {

        private val state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected.Idle)
        private val received = MutableSharedFlow<Incoming>(extraBufferCapacity = 32)

        val subscribed = mutableListOf<String>()
        val published = mutableListOf<Pair<String, String>>()
        var closed = false

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
    }
}
