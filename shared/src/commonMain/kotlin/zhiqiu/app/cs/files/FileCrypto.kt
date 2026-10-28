package zhiqiu.app.cs.files

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import zhiqiu.app.cs.core.RoomCrypto
import kotlin.io.encoding.Base64

data class DecryptedFile(
    val bytes: ByteArray,
    val sha256: String,
) {
    override fun equals(other: Any?): Boolean = this === other || (other is DecryptedFile && sha256 == other.sha256)
    override fun hashCode(): Int = sha256.hashCode()
}

class FileCrypto(private val room: RoomCrypto, private val clipTopic: String) {

    suspend fun encrypt(name: String, mime: String, plain: ByteArray): EncryptedFile =
        encrypt(name, mime, plain, sha256(plain))

    suspend fun encrypt(name: String, mime: String, plain: ByteArray, sha256: String): EncryptedFile {
        val body = room.encryptBytes(plain, aad(name, mime, plain.size.toLong(), sha256))
        return EncryptedFile(
            cipher = body,
            name = name,
            mime = mime,
            size = plain.size.toLong(),
            sha256 = sha256,
        )
    }

    suspend fun decrypt(file: FileClip, cipher: ByteArray): DecryptedFile {
        val plain = room.decryptBytes(cipher, aad(file.name, file.mime, file.size, file.sha256))
        val sha = sha256(plain)
        check(sha == file.sha256) { "sha256 mismatch" }
        check(plain.size.toLong() == file.size) { "size mismatch" }
        return DecryptedFile(plain, sha)
    }

    // 带上 clip topic：同一份文件在两个房间的密文不再相同，跨房搬运会在 GCM 阶段失败。
    private fun aad(name: String, mime: String, size: Long, sha256: String): ByteArray =
        "$clipTopic\n$name\n$mime\n$size\n$sha256".encodeToByteArray()

    suspend fun sha256(bytes: ByteArray): String =
        CryptographyProvider.Default.get(SHA256).hasher().hash(bytes)
            .joinToString("") { b -> ((b.toInt() and 0xFF) + 0x100).toString(16).substring(1) }

    companion object {
        fun uploadFileName(): String {
            val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
            val id = ByteArray(16).also { CryptographyRandom.Default.nextBytes(it) }
                .joinToString("") { alphabet[(it.toInt() and 0xFF) % alphabet.length].toString() }
                .take(16)
            return "cs-$id.bin"
        }

        fun encodeCipher(cipher: ByteArray): String = Base64.encode(cipher)
        fun decodeCipher(encoded: String): ByteArray = Base64.decode(encoded)
    }
}

data class EncryptedFile(
    val cipher: ByteArray,
    val name: String,
    val mime: String,
    val size: Long,
    val sha256: String,
)
