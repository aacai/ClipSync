package zhiqiu.app.cs.files

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import zhiqiu.app.cs.core.RoomCrypto
import kotlin.io.encoding.Base64
import kotlin.random.Random

/** 解密后的文件缓存条目（明文只落本机磁盘）。 */
data class DecryptedFile(
    val bytes: ByteArray,
    val sha256: String,
) {
    override fun equals(other: Any?): Boolean = this === other || (other is DecryptedFile && sha256 == other.sha256)
    override fun hashCode(): Int = sha256.hashCode()
}

/**
 * 文件内容加密：房间密钥 + AES-256-GCM，仓库前缀绑定文件 id（防换包）。
 * 输出 = base64(iv || ciphertext || tag)，可直接交给 [zhiqiu.app.cs.files.FileTransferClient] 上传。
 */
class FileCrypto(private val room: RoomCrypto) {

    suspend fun encrypt(name: String, mime: String, plain: ByteArray): EncryptedFile {
        val sha = sha256Hex(plain)
        val header = "$name\n$mime\n${plain.size}\n$sha".encodeToByteArray()
        val body = room.encryptBytes(plain, header)
        return EncryptedFile(
            cipher = body,
            name = name,
            mime = mime,
            size = plain.size.toLong(),
            sha256 = sha,
        )
    }

    /** 解密并校验：头部不符或 sha256 不匹配都会抛错（防止链接被替换/篡改）。 */
    suspend fun decrypt(file: FileClip, cipher: ByteArray): DecryptedFile {
        val header = "${file.name}\n${file.mime}\n${file.size}\n${file.sha256}".encodeToByteArray()
        val plain = room.decryptBytes(cipher, header)
        val sha = sha256Hex(plain)
        check(sha == file.sha256) { "文件校验失败：sha256 不匹配" }
        check(plain.size.toLong() == file.size) { "文件校验失败：大小不匹配" }
        return DecryptedFile(plain, sha)
    }

    private suspend fun sha256Hex(bytes: ByteArray): String =
        CryptographyProvider.Default.get(SHA256).hasher().hash(bytes)
            .joinToString("") { b -> ((b.toInt() and 0xFF) + 0x100).toString(16).substring(1) }

    companion object {
        /** 上传用的随机文件名：托管方只知道随机 id，看不到原名。 */
        fun uploadFileName(): String {
            val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
            val id = ByteArray(16).also { Random.nextBytes(it) }
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
