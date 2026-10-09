@file:OptIn(dev.whyoleg.cryptography.DelicateCryptographyApi::class)

package zhiqiu.app.cs.core

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlin.io.encoding.Base64

class RoomCrypto private constructor(private val key: ByteArray) {

    // 每条消息重新解码密钥：这样 wipe() 之后任何一次加解密都用的是零密钥，缓存解码结果会让 wipe 变成摆设。
    private suspend fun newCipher() =
        CryptographyProvider.Default
            .get(AES.GCM)
            .keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, key)
            .cipher()

    private fun randomIv() = CryptographyRandom.Default.nextBytes(IV_SIZE)

    suspend fun encrypt(plaintext: ByteArray, aad: ByteArray): String {
        val iv = randomIv()
        val body = newCipher().encryptWithIv(iv, plaintext, aad)
        return Base64.encode(iv + body)
    }

    suspend fun encryptBytes(plaintext: ByteArray, aad: ByteArray): ByteArray {
        val iv = randomIv()
        val body = newCipher().encryptWithIv(iv, plaintext, aad)
        return iv + body
    }

    suspend fun decrypt(wire: String, aad: ByteArray): ByteArray = decryptBytes(Base64.decode(wire), aad)

    suspend fun decryptBytes(raw: ByteArray, aad: ByteArray): ByteArray {
        require(raw.size >= IV_SIZE + TAG_SIZE) { "ciphertext too short" }
        val iv = raw.copyOfRange(0, IV_SIZE)
        val body = raw.copyOfRange(IV_SIZE, raw.size)
        return newCipher().decryptWithIv(iv, body, aad)
    }

    suspend fun safetyNumber(): String = safetyNumber(key)

    fun wipe() {
        key.fill(0)
    }

    companion object {
        const val PBKDF2_ITERATIONS = 210_000
        private const val IV_SIZE = 12
        private const val TAG_SIZE = 16

        // salt 由房间码决定，同房间各设备才能推导出同一把密钥。
        suspend fun open(roomId: String, password: String): RoomCrypto {
            val normalizedRoom = roomId.trim()
            require(normalizedRoom.isNotEmpty()) { "room code must not be empty" }
            val salt = sha256(normalizedRoom.encodeToByteArray()).copyOfRange(0, 16)
            val input = if (password.isEmpty()) {
                normalizedRoom.encodeToByteArray()
            } else {
                (normalizedRoom + "\u0000" + password).encodeToByteArray()
            }
            val derived = CryptographyProvider.Default
                .get(PBKDF2)
                .secretDerivation(
                    digest = SHA256,
                    iterations = PBKDF2_ITERATIONS,
                    outputSize = 32.bytes,
                    salt = salt,
                )
                .deriveSecretToByteArray(input)
            return RoomCrypto(derived)
        }
    }
}
