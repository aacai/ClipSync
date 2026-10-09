package zhiqiu.app.cs.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

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
    fun roomHash_matches_webcrypto() = runBlockingTest {
        assertEquals(expectedRoomHash, ClipProtocol.roomHash(roomId))
    }

    @Test
    fun decrypt_browser_payload() = runBlockingTest {
        val crypto = RoomCrypto.open(roomId, password = "")
        val decrypted = crypto.decrypt(expectedWire, aad).decodeToString()
        assertEquals(expectedPlaintext, decrypted)
        crypto.wipe()
    }

    @Test
    fun roundtrip_text() = runBlockingTest {
        val alice = RoomCrypto.open(roomId, password = "hunter2")
        val bob = RoomCrypto.open(roomId, password = "hunter2")
        val wire = alice.encrypt("跨端同步".encodeToByteArray(), aad)
        assertEquals("跨端同步", bob.decrypt(wire, aad).decodeToString())
        alice.wipe()
        bob.wipe()
    }

    @Test
    fun wrong_password_fails() = runBlockingTest {
        val alice = RoomCrypto.open(roomId, password = "hunter2")
        val eve = RoomCrypto.open(roomId, password = "wrong")
        val wire = alice.encrypt("secret".encodeToByteArray(), aad)
        assertFailsWith<Exception> { eve.decrypt(wire, aad) }
        alice.wipe()
        eve.wipe()
    }

    @Test
    fun safetyNumber_stable_across_sessions() = runBlockingTest {
        val first = RoomCrypto.open(roomId, password = "hunter2").safetyNumber()
        val second = RoomCrypto.open(roomId, password = "hunter2").safetyNumber()
        val otherRoom = RoomCrypto.open("OTHERROOM", password = "hunter2").safetyNumber()
        assertEquals(first, second)
        assertNotEquals(first, otherRoom)
    }

    @Test
    fun topic_parsing() = runBlockingTest {
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
}
