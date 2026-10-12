package zhiqiu.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ClipSync 房间密钥的跨端一致性向量。
 * Argon2id 不在 WebCrypto 里，网页端和桌面端是否算出同一把只能靠这个钉住：
 * salt = SHA-256(房间码) 的前 16 字节，口令 = 房间码 + 0x00 + 密码，档位 t=2 / m=19456KiB / p=1。
 */
class RoomKeyVectors {

    private fun roomKey(roomCode: String, password: String): ByteArray {
        val salt = sha256(roomCode.encodeToByteArray()).copyOfRange(0, 16)
        val input = roomCode.encodeToByteArray() + byteArrayOf(0) + password.encodeToByteArray()
        return argon2id(input, salt, t = 2, m = 19_456, p = 1, tagLen = 32)
    }

    private fun ByteArray.hex() = joinToString("") { ((it.toInt() and 0xFF) + 0x100).toString(16).substring(1) }

    @Test
    fun emptyPassword() {
        // 无密码时 ClipSync 直接用房间码当口令
        val salt = sha256("TESTROOM01".encodeToByteArray()).copyOfRange(0, 16)
        assertEquals(
                "93d4209e0e24234aa41373925656009956cf70665afd2d9ce4fd2e2316e52a4c",
                argon2id("TESTROOM01".encodeToByteArray(), salt, t = 2, m = 19_456, p = 1, tagLen = 32).hex(),
        )
    }

    @Test
    fun withPassword() {
        assertEquals(
                "4e2a5f0a88f27bacbe50791075e10d6779878281f5b3066cbe62aec8eff52af1",
                roomKey("TESTROOM01", "hunter2").hex(),
        )
    }
}
