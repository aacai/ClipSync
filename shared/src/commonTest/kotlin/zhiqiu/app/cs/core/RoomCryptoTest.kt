package zhiqiu.app.cs.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.test.runTest

/**
 * 房间密钥是 Argon2id 派生的，WebCrypto 算不出这一把，所以密钥用 OpenSSL 命令行算出的向量钉住；
 * 密文向量仍旧由 WebCrypto（Node）生成，用来证明我们的 AES-GCM 和浏览器加解密互通。
 */
class RoomCryptoTest {

    private val roomId = "TESTROOM01"
    private val aad = "cs/v1/prefixclip".encodeToByteArray()
    private val expectedRoomHash = "61232506"
    private val expectedWire =
        "AAECAwQFBgcICQoL+gx2TX+MMFr0F0nlliSlOqTsWYyEZTm3yisCAhw8KjnBqt5N2xJ/xzCxxu9VZLqQH7g="
    private val expectedPlaintext = "{\"text\":\"hello clipsync\",\"len\":15}"

    private fun ByteArray.toHex(): String =
        joinToString("") { ((it.toInt() and 0xFF) + 0x100).toString(16).substring(1) }

    @Test
    fun roomKey_matches_pinned_vectors() = runTest {
        val empty = RoomCrypto.open(roomId, password = "")
        assertEquals("b1b738d23632bb6c4dc8bf9e235d20261e504d80030fc6f998a616ea8c790ee9", empty.keyMaterial().toHex())
        empty.wipe()
        val withPassword = RoomCrypto.open(roomId, password = "hunter2")
        assertEquals("0c507ff2ecd664e9c25d92bc01ab781e257e2a85c7f72b9b911ccdbd1513e16e", withPassword.keyMaterial().toHex())
        withPassword.wipe()
    }

    /** 档位守卫，不是性能基准：把 m/t 提高一个量级会先在这里爆。 */
    @Test
    fun roomKey_derivation_stays_in_the_documented_window() = runTest {
        val started = TimeSource.Monotonic.markNow()
        val crypto = RoomCrypto.open(roomId, password = "hunter2")
        val elapsed = started.elapsedNow()
        crypto.wipe()
        assertTrue(elapsed < 5L.seconds, "派生花了 $elapsed")
    }

    @Test
    fun roomHash_matches_webcrypto() = runTest {
        assertEquals(expectedRoomHash, ClipProtocol.roomHash(roomId))
    }

    @Test
    fun decrypt_browser_payload() = runTest {
        val crypto = RoomCrypto.open(roomId, password = "")
        val decrypted = crypto.decrypt(expectedWire, aad).decodeToString()
        assertEquals(expectedPlaintext, decrypted)
        crypto.wipe()
    }

    @Test
    fun roundtrip_text() = runTest {
        val alice = RoomCrypto.open(roomId, password = "hunter2")
        val bob = RoomCrypto.open(roomId, password = "hunter2")
        val wire = alice.encrypt("跨端同步".encodeToByteArray(), aad)
        assertEquals("跨端同步", bob.decrypt(wire, aad).decodeToString())
        alice.wipe()
        bob.wipe()
    }

    @Test
    fun wrong_password_fails() = runTest {
        val alice = RoomCrypto.open(roomId, password = "hunter2")
        val eve = RoomCrypto.open(roomId, password = "wrong")
        val wire = alice.encrypt("secret".encodeToByteArray(), aad)
        assertFailsWith<Exception> { eve.decrypt(wire, aad) }
        alice.wipe()
        eve.wipe()
    }

    @Test
    fun safetyNumber_stable_across_sessions() = runTest {
        val first = RoomCrypto.open(roomId, password = "hunter2").safetyNumber()
        val second = RoomCrypto.open(roomId, password = "hunter2").safetyNumber()
        val otherRoom = RoomCrypto.open("OTHERROOM", password = "hunter2").safetyNumber()
        assertEquals(first, second)
        assertNotEquals(first, otherRoom)
    }

    @Test
    fun topic_parsing() = runTest {
        val hash = ClipProtocol.roomHash(roomId)
        assertEquals(hash, "61232506")
        assertContentEquals(
            listOf(
                RoomTopic(hash, ClipProtocol.CH_CLIP),
                RoomTopic(hash, ClipProtocol.CH_PRESENCE, "device-1"),
                RoomTopic(hash, ClipProtocol.CH_SYNC),
            ),
            listOf(
                ClipProtocol.parseTopic(ClipProtocol.clipTopic(hash)),
                ClipProtocol.parseTopic(ClipProtocol.presenceTopic(hash, "device-1")),
                ClipProtocol.parseTopic(ClipProtocol.syncTopic(hash)),
            ),
        )
        assertEquals(null, ClipProtocol.parseTopic("cs/v1/$hash/unknown"))
        assertEquals(null, ClipProtocol.parseTopic("other/$hash/clip"))
    }

    @Test
    fun message_ids_are_random_hex() {
        val ids = (1..64).map { ClipProtocol.randomMid() }
        assertTrue(ids.all { Regex("^[0-9a-f]{16}$").matches(it) })
        assertEquals(64, ids.distinct().size)
    }

    @Test
    fun restored_key_interoperates_with_derived_key() = runTest {
        val alice = RoomCrypto.open(roomId, password = "hunter2")
        val material = alice.keyMaterial()
        assertEquals(RoomCrypto.KEY_SIZE, material.size)
        val restored = RoomCrypto.restore(material)
        material.fill(0)
        assertEquals(alice.safetyNumber(), restored.safetyNumber())
        val wire = restored.encrypt("回到房间".encodeToByteArray(), aad)
        assertEquals("回到房间", alice.decrypt(wire, aad).decodeToString())
        alice.wipe()
        restored.wipe()
    }

    @Test
    fun keyMaterial_is_a_copy_that_survives_wipe() = runTest {
        val source = RoomCrypto.open(roomId, password = "hunter2")
        val expected = source.safetyNumber()
        val material = source.keyMaterial()
        source.wipe()
        val revived = RoomCrypto.restore(material)
        assertEquals(expected, revived.safetyNumber())
        revived.wipe()
    }

    @Test
    fun restore_rejects_wrong_key_size() {
        assertFailsWith<IllegalArgumentException> { RoomCrypto.restore(ByteArray(RoomCrypto.KEY_SIZE - 1)) }
        assertFailsWith<IllegalArgumentException> { RoomCrypto.restore(ByteArray(RoomCrypto.KEY_SIZE + 1)) }
    }

    @Test
    fun presence_wire_hides_who_is_who() = runTest {
        val crypto = RoomCrypto.open(roomId, password = "hunter2")
        val topic = ClipProtocol.presenceTopic(expectedRoomHash, "d1")
        val payload = PresencePayload(
            dev = "d1",
            name = "Pixel",
            os = "Android",
            online = true,
            ts = 1700000000000,
        )
        val wire = ClipProtocol.encode(
            PresenceEnvelope(
                ct = crypto.encrypt(ClipProtocol.encode(payload).encodeToByteArray(), topic.encodeToByteArray()),
            ),
        )
        assertTrue("Pixel" !in wire && "Android" !in wire && "1700000000000" !in wire)
        val decoded = ClipProtocol.decode<PresencePayload>(
            crypto.decrypt(ClipProtocol.decode<PresenceEnvelope>(wire).ct, topic.encodeToByteArray())
                .decodeToString(),
        )
        assertEquals(payload, decoded)
        crypto.wipe()
    }

    @Test
    fun presence_json_is_byte_identical_to_rust() {
        assertEquals("""{"v":1,"ct":"abc"}""", ClipProtocol.encode(PresenceEnvelope(ct = "abc")))
        assertEquals(
            """{"dev":"d1","name":"Pixel","os":"Android","online":true,"ts":1700000000000,"answer":false}""",
            ClipProtocol.encode(
                PresencePayload(
                    dev = "d1",
                    name = "Pixel",
                    os = "Android",
                    online = true,
                    ts = 1700000000000,
                ),
            ),
        )
    }
}
