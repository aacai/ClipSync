@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package zhiqiu.crypto

import kotlin.math.min

// ---------------------------------------------------------------- ChaCha20

private fun Int.rotL(n: Int): Int = (this shl n) or (this ushr (32 - n))

private fun le32(b: ByteArray, off: Int): Int =
    (b[off].toInt() and 0xFF) or
        ((b[off + 1].toInt() and 0xFF) shl 8) or
        ((b[off + 2].toInt() and 0xFF) shl 16) or
        ((b[off + 3].toInt() and 0xFF) shl 24)

private fun le32ToBytes(v: Int, out: ByteArray, off: Int) {
    out[off] = v.toByte(); out[off + 1] = (v ushr 8).toByte()
    out[off + 2] = (v ushr 16).toByte(); out[off + 3] = (v ushr 24).toByte()
}

private fun le64(b: ByteArray, off: Int): Long =
    (b[off].toLong() and 0xFF) or
        ((b[off + 1].toLong() and 0xFF) shl 8) or
        ((b[off + 2].toLong() and 0xFF) shl 16) or
        ((b[off + 3].toLong() and 0xFF) shl 24) or
        ((b[off + 4].toLong() and 0xFF) shl 32) or
        ((b[off + 5].toLong() and 0xFF) shl 40) or
        ((b[off + 6].toLong() and 0xFF) shl 48) or
        ((b[off + 7].toLong() and 0xFF) shl 56)

private const val CHACHA_CONST0 = 0x61707865
private const val CHACHA_CONST1 = 0x3320646e
private const val CHACHA_CONST2 = 0x79622d32
private const val CHACHA_CONST3 = 0x6b206574

private fun chachaQuarterRound(s: IntArray, a: Int, b: Int, c: Int, d: Int) {
    s[a] += s[b]; s[d] = s[d] xor s[a]; s[d] = s[d].rotL(16)
    s[c] += s[d]; s[b] = s[b] xor s[c]; s[b] = s[b].rotL(12)
    s[a] += s[b]; s[d] = s[d] xor s[a]; s[d] = s[d].rotL(8)
    s[c] += s[d]; s[b] = s[b] xor s[c]; s[b] = s[b].rotL(7)
}

/** 生成 ChaCha20 的 64 字节密钥流块。 */
fun chacha20Block(key: ByteArray, counter: Int, nonce: ByteArray): ByteArray {
    require(key.size == 32) { "ChaCha20 需要 32 字节密钥" }
    require(nonce.size == 12) { "ChaCha20 需要 12 字节 nonce" }
    val state = IntArray(16)
    state[0] = CHACHA_CONST0; state[1] = CHACHA_CONST1; state[2] = CHACHA_CONST2; state[3] = CHACHA_CONST3
    for (i in 0..7) state[4 + i] = le32(key, i * 4)
    state[12] = counter
    state[13] = le32(nonce, 0)
    state[14] = le32(nonce, 4)
    state[15] = le32(nonce, 8)
    val w = state.copyOf()
    repeat(10) {
        chachaQuarterRound(w, 0, 4, 8, 12); chachaQuarterRound(w, 1, 5, 9, 13)
        chachaQuarterRound(w, 2, 6, 10, 14); chachaQuarterRound(w, 3, 7, 11, 15)
        chachaQuarterRound(w, 0, 5, 10, 15); chachaQuarterRound(w, 1, 6, 11, 12)
        chachaQuarterRound(w, 2, 7, 8, 13); chachaQuarterRound(w, 3, 4, 9, 14)
    }
    for (i in 0..15) w[i] += state[i]
    val out = ByteArray(64)
    for (i in 0..15) le32ToBytes(w[i], out, i * 4)
    return out
}

/** ChaCha20 流密码：密钥流与明文异或（加解密为同一操作）。[counter] 为起始块计数器。 */
fun chacha20(key: ByteArray, counter: Int, nonce: ByteArray, plaintext: ByteArray): ByteArray {
    require(key.size == 32) { "ChaCha20 需要 32 字节密钥" }
    require(nonce.size == 12) { "ChaCha20 需要 12 字节 nonce" }
    val out = ByteArray(plaintext.size)
    var pos = 0
    var ctr = counter
    while (pos < plaintext.size) {
        val ks = chacha20Block(key, ctr, nonce)
        val n = minOf(64, plaintext.size - pos)
        for (j in 0 until n) out[pos + j] = (plaintext[pos + j].toInt() xor ks[j].toInt()).toByte()
        pos += n
        ctr += 1
    }
    return out
}

// ---------------------------------------------------------------- Poly1305 (RFC 8439)

private const val P_LIMBS = 10

private fun uZero() = UIntArray(P_LIMBS)

private fun uAdd(a: UIntArray, b: UIntArray): UIntArray {
    val r = UIntArray(P_LIMBS)
    var carry = 0uL
    for (i in 0 until P_LIMBS) {
        val s = a[i].toULong() + b[i].toULong() + carry
        r[i] = s.toUInt(); carry = s shr 32
    }
    return r
}

private fun uSub(a: UIntArray, b: UIntArray): UIntArray {
    val r = UIntArray(P_LIMBS)
    var borrow = 0L
    for (i in 0 until P_LIMBS) {
        val s = a[i].toLong() - b[i].toLong() - borrow
        r[i] = s.toUInt()
        borrow = if (s < 0) 1 else 0
    }
    return r
}

private fun uCmp(a: UIntArray, b: UIntArray): Int {
    for (i in (P_LIMBS - 1) downTo 0) {
        if (a[i] != b[i]) return if (a[i] > b[i]) 1 else -1
    }
    return 0
}

private fun uMul(a: UIntArray, b: UIntArray): UIntArray {
    val r = UIntArray(P_LIMBS)
    for (i in 0 until 5) {
        if (a[i] == 0u) continue
        var carry = 0uL
        for (j in 0 until 5) {
            val cur = r[i + j].toULong() + a[i].toULong() * b[j].toULong() + carry
            r[i + j] = cur.toUInt(); carry = cur shr 32
        }
        var k = i + 5
        while (carry != 0uL && k < P_LIMBS) {
            val cur = r[k].toULong() + carry
            r[k] = cur.toUInt(); carry = cur shr 32; k++
        }
    }
    return r
}

private fun uMul5(a: UIntArray): UIntArray {
    var x = uAdd(a, a) // 2a
    x = uAdd(x, x) // 4a
    return uAdd(x, a) // 5a
}

private fun uMask130(a: UIntArray): UIntArray {
    val r = a.copyOf()
    r[4] = r[4] and 0x00000003u
    for (i in 5 until P_LIMBS) r[i] = 0u
    return r
}

private fun uShr130(a: UIntArray): UIntArray {
    val r = UIntArray(P_LIMBS)
    for (i in 0 until P_LIMBS) {
        val src = i + 4
        var v = if (src < P_LIMBS) a[src].toULong() else 0uL
        if (src + 1 < P_LIMBS) v = v or (a[src + 1].toULong() shl 32)
        r[i] = (v shr 2).toUInt()
    }
    return r
}

private fun uModP(a: UIntArray): UIntArray {
    var x = a
    repeat(6) {
        val hi = uShr130(x)
        val lo = uMask130(x)
        x = uAdd(lo, uMul5(hi))
    }
    val p = uZero().apply {
        this[0] = 0xFFFFFFFBu; this[1] = 0xFFFFFFFFu; this[2] = 0xFFFFFFFFu
        this[3] = 0xFFFFFFFFu; this[4] = 0x00000003u
    }
    return if (uCmp(x, p) >= 0) uSub(x, p) else x
}

private fun uLoadBlock(msg: ByteArray, off: Int, len: Int): UIntArray {
    val a = uZero()
    if (len == 16) {
        for (i in 0..3) a[i] = le32(msg, off + i * 4).toUInt()
        a[4] = a[4] + 1u // + 2^128
    } else {
        for (i in 0 until len) {
            val limb = i / 4; val bit = (i % 4) * 8
            a[limb] = a[limb] or ((msg[off + i].toLong() and 0xFF) shl bit).toUInt()
        }
        val limb = (8 * len) / 32; val bit = (8 * len) % 32
        a[limb] = a[limb] + (1u shl bit) // + 2^(8*len)
    }
    return a
}

private fun uFrom128(lo: Long, hi: Long): UIntArray {
    val a = uZero()
    a[0] = lo.toUInt(); a[1] = (lo ushr 32).toUInt()
    a[2] = hi.toUInt(); a[3] = (hi ushr 32).toUInt()
    return a
}

private fun uToLe16(a: UIntArray): ByteArray {
    val out = ByteArray(16)
    for (i in 0..3) {
        val v = a[i]
        out[i * 4] = v.toByte(); out[i * 4 + 1] = (v shr 8).toByte()
        out[i * 4 + 2] = (v shr 16).toByte(); out[i * 4 + 3] = (v shr 24).toByte()
    }
    return out
}

/** Poly1305 一次性 MAC（[key] 为 32 字节一次性密钥 = r(16) || s(16)）。 */
fun poly1305Mac(message: ByteArray, key: ByteArray): ByteArray {
    require(key.size == 32) { "Poly1305 需要 32 字节一次性密钥" }
    val r = UIntArray(5)
    r[0] = le32(key, 0).toUInt() and 0x0FFFFFFFu
    r[1] = le32(key, 4).toUInt() and 0x0FFFFFFCu
    r[2] = le32(key, 8).toUInt() and 0x0FFFFFFCu
    r[3] = le32(key, 12).toUInt() and 0x0FFFFFFCu
    val sLo = le64(key, 16)
    val sHi = le64(key, 24)
    var a = uZero()
    var offset = 0
    while (offset < message.size) {
        val len = minOf(16, message.size - offset)
        a = uAdd(a, uLoadBlock(message, offset, len))
        a = uMul(a, r)
        a = uModP(a)
        offset += len
    }
    // 最终：acc = ((acc mod p) + s) mod 2^128 —— 加 s 后是截断到 128 位，不能再模 p
    a = uAdd(a, uFrom128(sLo, sHi))
    return uToLe16(a)
}

// ---------------------------------------------------------------- ChaCha20-Poly1305 AEAD (RFC 8439)

private fun buildMacData(aad: ByteArray, ciphertext: ByteArray): ByteArray {
    val out = ArrayList<Byte>()
    fun append(b: ByteArray) { for (x in b) out.add(x) }
    fun pad16(n: Int) { val rem = n % 16; if (rem != 0) repeat(16 - rem) { out.add(0) } }
    append(aad); pad16(aad.size)
    append(ciphertext); pad16(ciphertext.size)
    val aadLen = aad.size.toLong(); val ctLen = ciphertext.size.toLong()
    for (i in 0..7) out.add(((aadLen ushr (8 * i)) and 0xFF).toByte())
    for (i in 0..7) out.add(((ctLen ushr (8 * i)) and 0xFF).toByte())
    return out.toByteArray()
}

/**
 * ChaCha20-Poly1305 AEAD 加密（RFC 8439）。
 * @param key 32 字节密钥
 * @param nonce 12 字节随机数（**必须**对每个密钥唯一；复用会灾难性地泄露密钥流）
 * @param plaintext 明文
 * @param aad 附加认证数据（不参与加密，但参与认证；可为空）
 * @return `密文 || 16 字节 tag`（拼接形式，解密时用 [chacha20Poly1305Open]）
 */
fun chacha20Poly1305Seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
    require(key.size == 32) { "AEAD 需要 32 字节密钥" }
    require(nonce.size == 12) { "AEAD 需要 12 字节 nonce" }
    val otk = chacha20Block(key, 0, nonce).copyOfRange(0, 32) // 密钥流块前 32 字节即 Poly1305 一次性密钥
    val ciphertext = chacha20(key, 1, nonce, plaintext)
    val tag = poly1305Mac(buildMacData(aad, ciphertext), otk)
    return ciphertext + tag
}

/**
 * ChaCha20-Poly1305 AEAD 解密（RFC 8439）。
 * @param key 32 字节密钥
 * @param nonce 12 字节随机数（须与加密时一致）
 * @param ciphertextAndTag `密文 || 16 字节 tag`
 * @param aad 附加认证数据
 * @return 明文；标签校验失败抛出 [IllegalArgumentException]（恒定时间比较，抗时序侧信道）
 */
fun chacha20Poly1305Open(key: ByteArray, nonce: ByteArray, ciphertextAndTag: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
    require(key.size == 32) { "AEAD 需要 32 字节密钥" }
    require(nonce.size == 12) { "AEAD 需要 12 字节 nonce" }
    require(ciphertextAndTag.size >= 16) { "数据过短，缺少 tag" }
    val ctLen = ciphertextAndTag.size - 16
    val ciphertext = ciphertextAndTag.copyOfRange(0, ctLen)
    val tag = ciphertextAndTag.copyOfRange(ctLen, ciphertextAndTag.size)
    val otk = chacha20Block(key, 0, nonce).copyOfRange(0, 32)
    val expected = poly1305Mac(buildMacData(aad, ciphertext), otk)
    if (!constantTimeEquals(tag, expected)) throw IllegalArgumentException("Poly1305 校验失败（数据被篡改）")
    return chacha20(key, 1, nonce, ciphertext)
}

/** ChaCha20 流密码（明文为 UTF-8 文本，[counter] 为起始块计数器）。 */
fun chacha20(key: ByteArray, counter: Int, nonce: ByteArray, plaintext: String): ByteArray =
    chacha20(key, counter, nonce, plaintext.encodeToByteArray())

/**
 * ChaCha20-Poly1305 AEAD 加密（明文为 UTF-8 文本，[aad] 为 UTF-8 关联数据）。
 * 返回 `密文 || 16 字节 tag`。**注意：同一密钥下的 [nonce] 必须唯一。**
 */
fun chacha20Poly1305Seal(key: ByteArray, nonce: ByteArray, plaintext: String, aad: String = ""): ByteArray =
    chacha20Poly1305Seal(key, nonce, plaintext.encodeToByteArray(), aad.encodeToByteArray())
