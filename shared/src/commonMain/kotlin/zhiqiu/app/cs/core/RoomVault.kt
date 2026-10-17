@file:OptIn(dev.whyoleg.cryptography.DelicateCryptographyApi::class)

package zhiqiu.app.cs.core

import com.russhwolf.settings.Settings
import dev.whyoleg.cryptography.random.CryptographyRandom
import zhiqiu.crypto.chacha20Poly1305Open
import zhiqiu.crypto.chacha20Poly1305Seal
import zhiqiu.crypto.fromHex
import zhiqiu.crypto.toHex

/** 房间凭据在本机设置里不落明文：用安装号派生的密钥做 ChaCha20-Poly1305 封好再写。 */
internal class RoomVault(private val store: Settings, seed: String) {

    private val kek = zhiqiu.crypto.sha256((SALT + seed).encodeToByteArray())

    fun write(key: String, value: String) {
        val nonce = CryptographyRandom.Default.nextBytes(NONCE_SIZE)
        val sealed = chacha20Poly1305Seal(kek, nonce, value.encodeToByteArray())
        store.putString(key, (nonce + sealed).toHex())
    }

    fun read(key: String): String? {
        val raw = store.getStringOrNull(key) ?: return null
        val bytes = runCatching { raw.fromHex() }.getOrNull() ?: return null
        if (bytes.size < NONCE_SIZE + TAG_SIZE) return null
        val nonce = bytes.copyOfRange(0, NONCE_SIZE)
        val sealed = bytes.copyOfRange(NONCE_SIZE, bytes.size)
        val open = runCatching { chacha20Poly1305Open(kek, nonce, sealed) }.getOrNull() ?: return null
        return open.decodeToString()
    }

    fun writeBytes(key: String, value: ByteArray) = write(key, value.toHex())

    fun readBytes(key: String): ByteArray? = read(key)?.fromHex()

    fun forget(key: String) {
        store.remove(key)
    }

    private companion object {
        const val SALT = "clipsync.room-vault."
        const val NONCE_SIZE = 12
        const val TAG_SIZE = 16
    }
}
