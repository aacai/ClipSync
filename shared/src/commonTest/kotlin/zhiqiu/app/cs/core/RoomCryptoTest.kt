package zhiqiu.app.cs.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlinx.coroutines.test.runTest

/**
 * Golden vectors generated with WebCrypto (Node) — the same implementation family the
 * browser build uses. Passing here proves desktop/Android and web derive byte-identical
 * room keys and AES-GCM payloads.
 */
class RoomCryptoTest {

    private val roomId = "TESTROOM01"
    private val aad = "cs/v1/prefixclip".encodeToByteArray()
    private val expectedRoomHash = "61232506"
    private val expectedWire =
        "AQIDBAUGBwgJCgsMqX/6mwiZEwOr46w4pKr8lfl8FdKfhnh2UcF+TFWRXWWSHnXM29wVkMQv1B+BuMlLHUs="
    private val expectedPlaintext = "{\"text\":\"hello clipsync\",\"len\":15}"

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
    fun presence_from_older_client_decodes_without_safety() = runTest {
        val legacy = """{"v":1,"dev":"d1","name":"Pixel","online":true,"ts":1700000000000}"""
        val envelope = ClipProtocol.decode<PresenceEnvelope>(legacy)
        assertEquals("", envelope.sp)
        assertEquals("d1", envelope.dev)
        val withSafety = ClipProtocol.encode(envelope.copy(sp = "482"))
        assertContains(withSafety, "\"sp\":\"482\"")
        assertEquals("482", ClipProtocol.decode<PresenceEnvelope>(withSafety).sp)
    }

    @Test
    fun safety_prefix_is_the_first_group() = runTest {
        val crypto = RoomCrypto.open(roomId, password = "hunter2")
        val safety = crypto.safetyNumber()
        assertEquals(safety.substringBefore(' '), ClipProtocol.safetyPrefix(safety))
        assertEquals(3, ClipProtocol.safetyPrefix(safety).length)
        crypto.wipe()
    }
}
