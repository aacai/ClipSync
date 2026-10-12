package zhiqiu.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ClipSync 房间密钥的跨端一致性向量。
 * Argon2id 不在 WebCrypto 里，网页端和桌面端是否算出同一把只能靠这个钉住：
 * salt = SHA-256(房间码) 的前 16 字节，口令 = 房间码 + 0x00 + 密码，档位 t=2 / m=19456KiB / p=1。
 * 下面的 hex 由 OpenSSL 3.5.0 命令行算出，所以这一份同时证明本实现不是「只我们自己认得」。
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
                "b1b738d23632bb6c4dc8bf9e235d20261e504d80030fc6f998a616ea8c790ee9",
                argon2id("TESTROOM01".encodeToByteArray(), salt, t = 2, m = 19_456, p = 1, tagLen = 32).hex(),
        )
    }

    @Test
    fun withPassword() {
        assertEquals(
                "0c507ff2ecd664e9c25d92bc01ab781e257e2a85c7f72b9b911ccdbd1513e16e",
                roomKey("TESTROOM01", "hunter2").hex(),
        )
    }
}
