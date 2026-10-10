@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package zhiqiu.crypto

// ---------------------------------------------------------------- SHA-512

private fun Long.rotR(n: Int): Long = (this ushr n) or (this shl (64 - n))

/** 将 16 位十六进制字解析为 Long（按位模式，超 Long.MAX 时以补码表示）。 */
private fun ul(hex: String): Long = hex.toULong(16).toLong()

/** SHA-512 轮常量（FIPS 180-4）：前 64 位小数部分 of 立方根 of 前 80 个素数。 */
private val K512: LongArray = longArrayOf(
    ul("428a2f98d728ae22"), ul("7137449123ef65cd"), ul("b5c0fbcfec4d3b2f"), ul("e9b5dba58189dbbc"),
    ul("3956c25bf348b538"), ul("59f111f1b605d019"), ul("923f82a4af194f9b"), ul("ab1c5ed5da6d8118"),
    ul("d807aa98a3030242"), ul("12835b0145706fbe"), ul("243185be4ee4b28c"), ul("550c7dc3d5ffb4e2"),
    ul("72be5d74f27b896f"), ul("80deb1fe3b1696b1"), ul("9bdc06a725c71235"), ul("c19bf174cf692694"),
    ul("e49b69c19ef14ad2"), ul("efbe4786384f25e3"), ul("0fc19dc68b8cd5b5"), ul("240ca1cc77ac9c65"),
    ul("2de92c6f592b0275"), ul("4a7484aa6ea6e483"), ul("5cb0a9dcbd41fbd4"), ul("76f988da831153b5"),
    ul("983e5152ee66dfab"), ul("a831c66d2db43210"), ul("b00327c898fb213f"), ul("bf597fc7beef0ee4"),
    ul("c6e00bf33da88fc2"), ul("d5a79147930aa725"), ul("06ca6351e003826f"), ul("142929670a0e6e70"),
    ul("27b70a8546d22ffc"), ul("2e1b21385c26c926"), ul("4d2c6dfc5ac42aed"), ul("53380d139d95b3df"),
    ul("650a73548baf63de"), ul("766a0abb3c77b2a8"), ul("81c2c92e47edaee6"), ul("92722c851482353b"),
    ul("a2bfe8a14cf10364"), ul("a81a664bbc423001"), ul("c24b8b70d0f89791"), ul("c76c51a30654be30"),
    ul("d192e819d6ef5218"), ul("d69906245565a910"), ul("f40e35855771202a"), ul("106aa07032bbd1b8"),
    ul("19a4c116b8d2d0c8"), ul("1e376c085141ab53"), ul("2748774cdf8eeb99"), ul("34b0bcb5e19b48a8"),
    ul("391c0cb3c5c95a63"), ul("4ed8aa4ae3418acb"), ul("5b9cca4f7763e373"), ul("682e6ff3d6b2b8a3"),
    ul("748f82ee5defb2fc"), ul("78a5636f43172f60"), ul("84c87814a1f0ab72"), ul("8cc702081a6439ec"),
    ul("90befffa23631e28"), ul("a4506cebde82bde9"), ul("bef9a3f7b2c67915"), ul("c67178f2e372532b"),
    ul("ca273eceea26619c"), ul("d186b8c721c0c207"), ul("eada7dd6cde0eb1e"), ul("f57d4f7fee6ed178"),
    ul("06f067aa72176fba"), ul("0a637dc5a2c898a6"), ul("113f9804bef90dae"), ul("1b710b35131c471b"),
    ul("28db77f523047d84"), ul("32caab7b40c72493"), ul("3c9ebe0a15c9bebc"), ul("431d67c49c100d4c"),
    ul("4cc5d4becb3e42b6"), ul("597f299cfc657e2a"), ul("5fcb6fab3ad6faec"), ul("6c44198c4a475817"),
)

/** SHA-512 摘要（64 字节）。 */
fun sha512(message: ByteArray): ByteArray {
    var h0 = ul("6a09e667f3bcc908"); var h1 = ul("bb67ae8584caa73b")
    var h2 = ul("3c6ef372fe94f82b"); var h3 = ul("a54ff53a5f1d36f1")
    var h4 = ul("510e527fade682d1"); var h5 = ul("9b05688c2b3e6c1f")
    var h6 = ul("1f83d9abfb41bd6b"); var h7 = ul("5be0cd19137e2179")

    val bitLen = message.size.toLong() shl 3
    val padTotal = ((message.size + 17 + 127) / 128) * 128
    val bytes = ByteArray(padTotal)
    message.copyInto(bytes)
    bytes[message.size] = 0x80.toByte()
    for (i in 0..7) bytes[padTotal - 16 + i] = 0
    for (i in 0..7) bytes[padTotal - 8 + i] = ((bitLen ushr (56 - 8 * i)) and 0xFF).toByte()

    val w = LongArray(80)
    for (chunkStart in bytes.indices step 128) {
        for (i in 0..15) {
            val j = chunkStart + i * 8
            w[i] = ((bytes[j].toLong() and 0xFF) shl 56) or
                ((bytes[j + 1].toLong() and 0xFF) shl 48) or
                ((bytes[j + 2].toLong() and 0xFF) shl 40) or
                ((bytes[j + 3].toLong() and 0xFF) shl 32) or
                ((bytes[j + 4].toLong() and 0xFF) shl 24) or
                ((bytes[j + 5].toLong() and 0xFF) shl 16) or
                ((bytes[j + 6].toLong() and 0xFF) shl 8) or
                (bytes[j + 7].toLong() and 0xFF)
        }
        for (i in 16..79) {
            val s0 = w[i - 15].rotR(1) xor w[i - 15].rotR(8) xor (w[i - 15] ushr 7)
            val s1 = w[i - 2].rotR(19) xor w[i - 2].rotR(61) xor (w[i - 2] ushr 6)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }
        var a = h0; var b = h1; var c = h2; var d = h3; var e = h4; var f = h5; var g = h6; var h = h7
        for (i in 0..79) {
            val S1 = e.rotR(14) xor e.rotR(18) xor e.rotR(41)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = h + S1 + ch + K512[i] + w[i]
            val S0 = a.rotR(28) xor a.rotR(34) xor a.rotR(39)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = S0 + maj
            h = g; g = f; f = e; e = d + temp1; d = c; c = b; b = a; a = temp1 + temp2
        }
        h0 += a; h1 += b; h2 += c; h3 += d; h4 += e; h5 += f; h6 += g; h7 += h
    }
    val out = ByteArray(64)
    longToBytes(h0, out, 0); longToBytes(h1, out, 8); longToBytes(h2, out, 16); longToBytes(h3, out, 24)
    longToBytes(h4, out, 32); longToBytes(h5, out, 40); longToBytes(h6, out, 48); longToBytes(h7, out, 56)
    return out
}

/** 对 UTF-8 文本计算 SHA-512 摘要。 */
fun String.sha512(): ByteArray = sha512(encodeToByteArray())

/** 对 UTF-8 文本计算 SHA-512，并以十六进制小写串返回。 */
fun String.sha512Hex(): String = sha512(encodeToByteArray()).toHex()

private fun longToBytes(v: Long, out: ByteArray, off: Int) {
    out[off] = (v ushr 56).toByte(); out[off + 1] = (v ushr 48).toByte()
    out[off + 2] = (v ushr 40).toByte(); out[off + 3] = (v ushr 32).toByte()
    out[off + 4] = (v ushr 24).toByte(); out[off + 5] = (v ushr 16).toByte()
    out[off + 6] = (v ushr 8).toByte(); out[off + 7] = v.toByte()
}

/**
 * HMAC-SHA512 消息认证码（RFC 2104）。
 * @param key 密钥（任意长度；> 128 字节会先经 SHA-512 压缩到 128 字节）
 * @param data 待认证数据
 * @return 64 字节 MAC
 */
fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
    val blockSize = 128
    val k = if (key.size > blockSize) sha512(key) else key.copyOf(blockSize)
    val oKeyPad = ByteArray(blockSize) { (k[it].toInt() xor 0x5c).toByte() }
    val iKeyPad = ByteArray(blockSize) { (k[it].toInt() xor 0x36).toByte() }
    val inner = sha512(iKeyPad + data)
    return sha512(oKeyPad + inner)
}

/** 对 UTF-8 文本计算 HMAC-SHA512（[key] 为密钥字节）。 */
fun String.hmacSha512(key: ByteArray): ByteArray = hmacSha512(key, encodeToByteArray())
