@file:OptIn(dev.whyoleg.cryptography.DelicateCryptographyApi::class)

package zhiqiu.app.cs.core

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlin.io.encoding.Base64
import kotlin.random.Random

/**
 * Room key material. Derived once per session from the room code + optional password and
 * held only in memory ([wipe] drops it as soon as the room is left).
 *
 * The key never leaves the device: MQTT only ever sees base64(iv||ct||tag).
 */
class RoomCrypto private constructor(private val key: ByteArray) {

    private suspend fun newCipher() =
        CryptographyProvider.Default
            .get(AES.GCM)
            .keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, key)
            .cipher()

    /** Encrypts [plaintext] for [aad]; returns base64(iv || ciphertext || tag). */
    suspend fun encrypt(plaintext: ByteArray, aad: ByteArray): String {
        val iv = Random.nextBytes(IV_SIZE)
        val body = newCipher().encryptWithIv(iv, plaintext, aad)
        return Base64.encode(iv + body)
    }

    /** Byte-level variant for large payloads (files): iv || ciphertext || tag. */
    suspend fun encryptBytes(plaintext: ByteArray, aad: ByteArray): ByteArray {
        val iv = Random.nextBytes(IV_SIZE)
        val body = newCipher().encryptWithIv(iv, plaintext, aad)
        return iv + body
    }

    /** Inverse of [encrypt]. Throws when the payload was tampered with or the key is wrong. */
    suspend fun decrypt(wire: String, aad: ByteArray): ByteArray = decryptBytes(Base64.decode(wire), aad)

    /** Byte-level inverse of [encryptBytes]. Throws on tampering or a wrong key. */
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

        /**
         * Derives the room key: PBKDF2-SHA256(roomId [+ \0 + password]) with a deterministic
         * salt of SHA-256(roomId)[0:16], so every device in the room lands on the same key.
         */
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
