package zhiqiu.crypto

import kotlin.math.min

// AES-256-GCM (NIST SP 800-38D) — 纯 Kotlin 实现，复用本包的 AES-256 内核
// （SBOX / keyExpansion / aesEncryptBlock 等 internal 符号）。

// GF(2^128) 乘法（约减多项式 x^128 + x^7 + x^2 + x + 1）。
private fun gfMult(x: ByteArray, y: ByteArray): ByteArray {
    val r = ByteArray(16)
    val v = x.copyOf()
    for (bit in 0 until 128) {
        if ((y[bit / 8].toInt() ushr (7 - (bit % 8))) and 1 != 0) {
            for (i in 0 until 16) r[i] = (r[i].toInt() xor v[i].toInt()).toByte()
        }
        val lsb = v[15].toInt() and 1
        for (i in 15 downTo 1) {
            v[i] = (((v[i].toInt() and 0xFF) ushr 1) or ((v[i - 1].toInt() and 1) shl 7)).toByte()
        }
        v[0] = ((v[0].toInt() and 0xFF) ushr 1).toByte()
        if (lsb != 0) v[0] = (v[0].toInt() xor 0xe1).toByte()
    }
    return r
}

// GHASH(H, data)：data 须为 16 字节整数倍。
private fun ghash(h: ByteArray, data: ByteArray): ByteArray {
    require(data.size % 16 == 0) { "GHASH 输入须为 16 字节整数倍" }
    var y = ByteArray(16)
    var i = 0
    while (i < data.size) {
        val block = ByteArray(16) { (y[it].toInt() xor data[i + it].toInt()).toByte() }
        y = gfMult(block, h)
        i += 16
    }
    return y
}

// J0：IV 为 96 位时 J0 = IV || 0x00000001；否则 J0 = GHASH(H, IV 补零至 128 位边界)。
private fun computeJ0(iv: ByteArray, h: ByteArray): ByteArray {
    if (iv.size == 12) return iv.copyOf(16).also { it[15] = 1 }
    val padded = iv.copyOf((iv.size + 15) / 16 * 16)
    return ghash(h, padded)
}

// 将计数器（128 位）的低 32 位按大端整数自增 1。
private fun inc32(b: ByteArray): ByteArray {
    val out = b.copyOf()
    var carry = 1
    for (i in 15 downTo 12) {
        val v = (out[i].toInt() and 0xFF) + carry
        out[i] = (v and 0xFF).toByte()
        carry = v ushr 8
    }
    return out
}

// 组装 GHASH 的认证数据：AAD || 0^p || C || 0^q || [len(A)]_64 || [len(C)]_64（均为大端）。
private fun buildAuthData(aad: ByteArray, ct: ByteArray): ByteArray {
    val aadPad = ((aad.size + 15) / 16) * 16
    val ctPad = ((ct.size + 15) / 16) * 16
    val out = ByteArray(aadPad + ctPad + 16)
    aad.copyInto(out, 0)
    ct.copyInto(out, aadPad)
    val aadBits = (aad.size * 8L)
    val ctBits = (ct.size * 8L)
    for (i in 0 until 8) out[aadPad + ctPad + i] = ((aadBits ushr (56 - 8 * i)) and 0xFF).toByte()
    for (i in 0 until 8) out[aadPad + ctPad + 8 + i] = ((ctBits ushr (56 - 8 * i)) and 0xFF).toByte()
    return out
}

private fun gcmKeystream(rk: IntArray, j0: ByteArray, dataLen: Int): ByteArray {
    val ks = ByteArray(dataLen)
    var counter = inc32(j0)
    var pos = 0
    while (pos < dataLen) {
        val block = aesEncryptBlock(counter, rk)
        val n = minOf(16, dataLen - pos)
        for (j in 0 until n) ks[pos + j] = block[j]
        counter = inc32(counter)
        pos += n
    }
    return ks
}

/**
 * AES-256-GCM 认证加密（NIST SP 800-38D）。
 * @param key 32 字节密钥
 * @param iv 初始化向量（任意长度，标准推荐 12 字节；**必须**对每个密钥唯一，严禁复用）
 * @param plaintext 明文
 * @param aad 附加认证数据（不参与加密，但参与认证；可为空）
 * @return `(密文, 16 字节认证标签)`
 */
fun aes256GcmEncrypt(
    key: ByteArray,
    iv: ByteArray,
    plaintext: ByteArray,
    aad: ByteArray = ByteArray(0),
): Pair<ByteArray, ByteArray> {
    require(key.size == 32) { "AES-256 需要 32 字节密钥" }
    val rk = keyExpansion(key)
    val h = aesEncryptBlock(ByteArray(16), rk)
    val j0 = computeJ0(iv, h)
    val ks = gcmKeystream(rk, j0, plaintext.size)
    val ct = ByteArray(plaintext.size) { (plaintext[it].toInt() xor ks[it].toInt()).toByte() }
    val s = ghash(h, buildAuthData(aad, ct))
    val tag = ByteArray(16) { (s[it].toInt() xor aesEncryptBlock(j0, rk)[it].toInt()).toByte() }
    return ct to tag
}

/** AES-256-GCM 解密校验：标签不符返回 null（避免时序侧信道）。 */
fun aes256GcmDecryptOrNull(
    key: ByteArray,
    iv: ByteArray,
    ciphertext: ByteArray,
    tag: ByteArray,
    aad: ByteArray = ByteArray(0),
): ByteArray? {
    require(key.size == 32) { "AES-256 需要 32 字节密钥" }
    val rk = keyExpansion(key)
    val h = aesEncryptBlock(ByteArray(16), rk)
    val j0 = computeJ0(iv, h)
    val s = ghash(h, buildAuthData(aad, ciphertext))
    val expected = ByteArray(16) { (s[it].toInt() xor aesEncryptBlock(j0, rk)[it].toInt()).toByte() }
    if (!constantTimeEquals(expected, tag)) return null
    val ks = gcmKeystream(rk, j0, ciphertext.size)
    return ByteArray(ciphertext.size) { (ciphertext[it].toInt() xor ks[it].toInt()).toByte() }
}

/** AES-256-GCM 解密；标签校验失败抛出 [IllegalArgumentException]。 */
fun aes256GcmDecrypt(
    key: ByteArray,
    iv: ByteArray,
    ciphertext: ByteArray,
    tag: ByteArray,
    aad: ByteArray = ByteArray(0),
): ByteArray =
    aes256GcmDecryptOrNull(key, iv, ciphertext, tag, aad)
        ?: throw IllegalArgumentException("AES-GCM 认证标签校验失败")

/** AES-256-GCM 加密（明文为 UTF-8 文本，[aad] 为 UTF-8 关联数据）。返回 (密文, 标签)。 */
fun aes256GcmEncrypt(
    key: ByteArray, iv: ByteArray, plaintext: String, aad: String = "",
): Pair<ByteArray, ByteArray> = aes256GcmEncrypt(key, iv, plaintext.encodeToByteArray(), aad.encodeToByteArray())
