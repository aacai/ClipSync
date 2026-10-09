package zhiqiu.app.cs.files

import zhiqiu.app.cs.core.RoomCrypto
import zhiqiu.app.cs.core.runBlockingTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FileCryptoTest {

    private val room = "TESTROOM01"
    private val payload = "ClipSync 文件内容 \uD83D\uDCE1\nsecond line".encodeToByteArray()

    private fun clipFor(encrypted: EncryptedFile, name: String = encrypted.name, size: Long = encrypted.size) =
        FileClip(
            id = "m1",
            name = name,
            mime = encrypted.mime,
            size = size,
            sha256 = encrypted.sha256,
            link = "https://litterbox.example/cs-x.bin",
            expiresAt = 1L,
            ts = 2L,
            senderId = "dev1",
            senderName = "Pixel",
        )

    @Test
    fun roundtrip_preserves_content() = runBlockingTest {
        val alice = RoomCrypto.open(room, password = "")
        val bob = RoomCrypto.open(room, password = "")
        val enc = FileCrypto(alice).encrypt("shot.png", "image/png", payload)
        assertEquals(payload.size.toLong(), enc.size)
        assertEquals("shot.png", enc.name)

        val dec = FileCrypto(bob).decrypt(clipFor(enc), enc.cipher)
        assertContentEquals(payload, dec.bytes)
        assertEquals(enc.sha256, dec.sha256)
        alice.wipe()
        bob.wipe()
    }

    @Test
    fun tampered_cipher_fails() = runBlockingTest {
        val crypto = RoomCrypto.open(room, password = "")
        val enc = FileCrypto(crypto).encrypt("a.bin", "application/octet-stream", payload)
        val flipped = enc.cipher.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0xFF).toByte() }
        assertFails { FileCrypto(crypto).decrypt(clipFor(enc), flipped) }
        crypto.wipe()
    }

    @Test
    fun wrong_room_password_fails() = runBlockingTest {
        val alice = RoomCrypto.open(room, password = "pw1")
        val mallory = RoomCrypto.open(room, password = "pw2")
        val enc = FileCrypto(alice).encrypt("a.bin", "application/octet-stream", payload)
        assertFails { FileCrypto(mallory).decrypt(clipFor(enc), enc.cipher) }
        alice.wipe()
        mallory.wipe()
    }

    @Test
    fun metadata_substitution_fails() = runBlockingTest {
        val crypto = RoomCrypto.open(room, password = "")
        val enc = FileCrypto(crypto).encrypt("real.png", "image/png", payload)
        // 攻击者把文件名/大小换成别的，头部参与 AAD，必须解密失败
        assertFails { FileCrypto(crypto).decrypt(clipFor(enc, name = "fake.png"), enc.cipher) }
        assertFails { FileCrypto(crypto).decrypt(clipFor(enc, size = enc.size + 1), enc.cipher) }
        crypto.wipe()
    }

    @Test
    fun empty_file_roundtrip() = runBlockingTest {
        val crypto = RoomCrypto.open(room, password = "")
        val enc = FileCrypto(crypto).encrypt("empty.txt", "text/plain", ByteArray(0))
        val dec = FileCrypto(crypto).decrypt(clipFor(enc), enc.cipher)
        assertEquals(0, dec.bytes.size)
        crypto.wipe()
    }

    @Test
    fun upload_file_name_is_random_and_opaque() {
        val a = FileCrypto.uploadFileName()
        val b = FileCrypto.uploadFileName()
        assertTrue(a.startsWith("cs-") && a.endsWith(".bin"))
        assertTrue(a.length <= 40)
        assertTrue(a.none { it.isUpperCase() })
        assertNotEquals(a, b)
        assertTrue(a.all { it.isLowerCase() || it.isDigit() || it == '-' || it == '.' })
    }

    @Test
    fun cipher_codec_roundtrip() {
        val raw = byteArrayOf(0, 1, 2, -1, 127, -128)
        assertContentEquals(raw, FileCrypto.decodeCipher(FileCrypto.encodeCipher(raw)))
    }
}
