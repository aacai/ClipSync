package zhiqiu.crypto

import kotlin.random.Random

/**
 * 纯 Kotlin 实现的轻量密码学原语，可在 JVM / Android / iOS / Web(wasmJs) 一致运行，**仅依赖 Kotlin 标准库、零第三方依赖**。
 *
 * 提供的算法（均为顶层函数，字节接口与 `String` 便捷扩展并存）：
 * - 哈希：SHA-256、SHA-512、BLAKE2b
 * - MAC：HMAC-SHA256、HMAC-SHA512、Poly1305
 * - 密钥派生：PBKDF2-HMAC-SHA256、Argon2id（RFC 9106，内存硬，抗 GPU/ASIC）
 * - 对称加密（AEAD）：AES-256-GCM、ChaCha20-Poly1305、AES-256-CTR、AES-256-CBC
 * - 工具：`ByteArray.toHex()` / `String.fromHex()` / `randomBytes` / `constantTimeEquals`
 *
 * 正确性由 [CryptoTest] 中的权威测试向量保证：
 * - SHA-256/SHA-512：FIPS 180 已知向量
 * - HMAC：RFC 4231 向量
 * - PBKDF2-HMAC-SHA256：RFC 6070 向量
 * - AES-256：FIPS 197 ECB 向量
 * - AES-256-GCM：Project Wycheproof 向量
 * - BLAKE2b：RFC 7693 向量
 * - Argon2id：RFC 9106 §5.3 官方向量
 *
 * 安全约定：所有 AEAD 解密均使用恒定时间比较；同一密钥下的 IV/nonce **必须**唯一（详见各函数文档）。
 */

// ---------------------------------------------------------------- 字节工具

private val HEX = "0123456789abcdef"

/** 字节数组转十六进制小写串 */
fun ByteArray.toHex(): String = buildString(size * 2) {
    for (b in this@toHex) {
        val v = b.toInt() and 0xFF
        append(HEX[v ushr 4])
        append(HEX[v and 0x0F])
    }
}

/** 十六进制串转字节数组 */
fun String.fromHex(): ByteArray {
    require(length % 2 == 0) { "非法的十六进制串" }
    val out = ByteArray(length / 2)
    for (i in out.indices) {
        val hi = hexVal(this[i * 2])
        val lo = hexVal(this[i * 2 + 1])
        out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
}

private fun hexVal(c: Char): Int {
    val v = c.digitToIntOrNull(16)
    requireNotNull(v) { "非法的十六进制字符: $c" }
    return v
}

/**
 * 生成 [n] 字节随机数据（基于平台默认 [kotlin.random.Random]）。
 *
 * 注意：平台默认 [kotlin.random.Random] 不保证密码学安全。生产环境生成盐(salt)、IV 等
 * 机密随机量时，应使用平台 CSPRNG（如 JVM/Android 的 `java.security.SecureRandom`、
 * Web 的 `crypto.getRandomValues`、iOS 的 `SecRandomCopyBytes`）。
 */
fun randomBytes(n: Int): ByteArray {
    val a = ByteArray(n)
    for (i in a.indices) a[i] = Random.Default.nextInt(256).toByte()
    return a
}

/** 恒定时间比较，避免时序侧信道（用于 MAC 校验） */
fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var r = 0
    for (i in a.indices) r = r or (a[i].toInt() xor b[i].toInt())
    return r == 0
}

// ---------------------------------------------------------------- SHA-256

/** SHA-256 摘要 */
fun sha256(message: ByteArray): ByteArray {
    fun Int.rotR(n: Int) = (this ushr n) or (this shl (32 - n))
    val k = intArrayOf(
        0x428a2f98.toInt(), 0x71374491.toInt(), 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(), 0x3956c25b.toInt(), 0x59f111f1.toInt(), 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01.toInt(), 0x243185be.toInt(), 0x550c7dc3.toInt(), 0x72be5d74.toInt(), 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6.toInt(), 0x240ca1cc.toInt(), 0x2de92c6f.toInt(), 0x4a7484aa.toInt(), 0x5cb0a9dc.toInt(), 0x76f988da.toInt(),
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(), 0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351.toInt(), 0x14292967.toInt(),
        0x27b70a85.toInt(), 0x2e1b2138.toInt(), 0x4d2c6dfc.toInt(), 0x53380d13.toInt(), 0x650a7354.toInt(), 0x766a0abb.toInt(), 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(), 0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070.toInt(),
        0x19a4c116.toInt(), 0x1e376c08.toInt(), 0x2748774c.toInt(), 0x34b0bcb5.toInt(), 0x391c0cb3.toInt(), 0x4ed8aa4a.toInt(), 0x5b9cca4f.toInt(), 0x682e6ff3.toInt(),
        0x748f82ee.toInt(), 0x78a5636f.toInt(), 0x84c87814.toInt(), 0x8cc70208.toInt(), 0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
    )
    var h0 = 0x6a09e667.toInt(); var h1 = 0xbb67ae85.toInt(); var h2 = 0x3c6ef372.toInt(); var h3 = 0xa54ff53a.toInt()
    var h4 = 0x510e527f.toInt(); var h5 = 0x9b05688c.toInt(); var h6 = 0x1f83d9ab.toInt(); var h7 = 0x5be0cd19.toInt()

    val bitLen = message.size.toLong() shl 3
    val bytes = buildList<Byte> {
        addAll(message.asList())
        add(0x80.toByte())
        while ((size * 8 + 64) % 512 != 0) add(0.toByte())
        for (i in 7 downTo 0) add(((bitLen ushr (i * 8)) and 0xFF).toByte())
    }.toByteArray()

    for (chunkStart in bytes.indices step 64) {
        val w = IntArray(64)
        for (i in 0..15) {
            val j = chunkStart + i * 4
            w[i] = ((bytes[j].toInt() and 0xFF) shl 24) or
                ((bytes[j + 1].toInt() and 0xFF) shl 16) or
                ((bytes[j + 2].toInt() and 0xFF) shl 8) or
                (bytes[j + 3].toInt() and 0xFF)
        }
        for (i in 16..63) {
            val s0 = w[i - 15].rotR(7) xor w[i - 15].rotR(18) xor (w[i - 15] ushr 3)
            val s1 = w[i - 2].rotR(17) xor w[i - 2].rotR(19) xor (w[i - 2] ushr 10)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }
        var a = h0; var b = h1; var c = h2; var d = h3; var e = h4; var f = h5; var g = h6; var h = h7
        for (i in 0..63) {
            val S1 = e.rotR(6) xor e.rotR(11) xor e.rotR(25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = h + S1 + ch + k[i] + w[i]
            val S0 = a.rotR(2) xor a.rotR(13) xor a.rotR(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = S0 + maj
            h = g; g = f; f = e; e = d + temp1; d = c; c = b; b = a; a = temp1 + temp2
        }
        h0 += a; h1 += b; h2 += c; h3 += d; h4 += e; h5 += f; h6 += g; h7 += h
    }
    return intsToBytes(intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7))
}

/** 对 UTF-8 文本计算 SHA-256 摘要。 */
fun String.sha256(): ByteArray = sha256(encodeToByteArray())

/** 对 UTF-8 文本计算 SHA-256，并以十六进制小写串返回（便于展示或校验）。 */
fun String.sha256Hex(): String = sha256(encodeToByteArray()).toHex()

private fun intsToBytes(xs: IntArray): ByteArray {
    val out = ByteArray(xs.size * 4)
    for (i in xs.indices) {
        val v = xs[i]
        out[i * 4] = (v ushr 24).toByte()
        out[i * 4 + 1] = (v ushr 16).toByte()
        out[i * 4 + 2] = (v ushr 8).toByte()
        out[i * 4 + 3] = v.toByte()
    }
    return out
}

// ---------------------------------------------------------------- HMAC-SHA256

/**
 * HMAC-SHA256 消息认证码（RFC 2104）。
 * @param key 密钥（任意长度；> 64 字节会先经 SHA-256 压缩到 64 字节）
 * @param data 待认证数据
 * @return 32 字节 MAC
 */
fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val blockSize = 64
    val k = if (key.size > blockSize) sha256(key) else key.copyOf(blockSize)
    val oKeyPad = ByteArray(blockSize) { (k[it].toInt() xor 0x5c).toByte() }
    val iKeyPad = ByteArray(blockSize) { (k[it].toInt() xor 0x36).toByte() }
    val inner = sha256(iKeyPad + data)
    return sha256(oKeyPad + inner)
}

/** 对 UTF-8 文本计算 HMAC-SHA256（[key] 为密钥字节）。 */
fun String.hmacSha256(key: ByteArray): ByteArray = hmacSha256(key, encodeToByteArray())

// ---------------------------------------------------------------- PBKDF2-HMAC-SHA256

/**
 * PBKDF2-HMAC-SHA256 密钥派生（RFC 8018）。
 * @param password 口令（任意长度字节）
 * @param salt 盐（建议 >= 16 字节随机值，可公开）
 * @param iterations 迭代次数（越大越慢、抗暴力破解；建议 >= 100_000）
 * @param keyLen 输出密钥字节数
 * @return [keyLen] 字节派生密钥
 *
 * 注：PBKDF2 仅提高时间成本，不抗 GPU/ASIC 集群；若场景允许（无极度受限设备），
 * 优先使用内存硬函数 [argon2id]。
 */
fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, keyLen: Int): ByteArray {
    val hLen = 32
    val l = (keyLen + hLen - 1) / hLen
    val r = keyLen - (l - 1) * hLen
    val out = ByteArray(keyLen)
    var offset = 0
    for (i in 1..l) {
        var u = hmacSha256(password, salt + int32ToBytes(i))
        val t = u.copyOf()
        for (j in 2..iterations) {
            u = hmacSha256(password, u)
            for (k in t.indices) t[k] = (t[k].toInt() xor u[k].toInt()).toByte()
        }
        val copyLen = if (i == l) r else hLen
        t.copyInto(out, offset, 0, copyLen)
        offset += copyLen
    }
    return out
}

private fun int32ToBytes(i: Int): ByteArray = byteArrayOf(
    (i ushr 24).toByte(), (i ushr 16).toByte(), (i ushr 8).toByte(), i.toByte(),
)

// ---------------------------------------------------------------- AES-256 (用于 CTR)

internal val SBOX = intArrayOf(
    0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
    0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
    0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
    0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
    0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0, 0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
    0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b, 0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
    0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
    0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5, 0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
    0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
    0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88, 0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
    0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
    0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
    0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
    0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e, 0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
    0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
    0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68, 0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16,
)

private val RCON = intArrayOf(0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1b, 0x36)

/**
 * AES-256 在 CTR 模式下加解密（对称）。[key] 32 字节，[iv] 16 字节作为初始计数器。
 * 计数器以 128 位大端整数递增（与 NIST SP 800-38A 一致）。
 */
fun aes256Ctr(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
    require(key.size == 32) { "AES-256 需要 32 字节密钥" }
    require(iv.size == 16) { "CTR 模式需要 16 字节 IV" }
    val rk = keyExpansion(key)
    val out = ByteArray(data.size)
    val counter = iv.copyOf()
    var pos = 0
    while (pos < data.size) {
        val ks = aesEncryptBlock(counter, rk)
        val n = minOf(16, data.size - pos)
        for (j in 0 until n) out[pos + j] = (data[pos + j].toInt() xor ks[j].toInt()).toByte()
        incrementCounter(counter)
        pos += n
    }
    return out
}

/** AES-256-CTR 加解密（数据为 UTF-8 文本，与二进制接口对称）。 */
fun aes256Ctr(key: ByteArray, iv: ByteArray, data: String): ByteArray =
    aes256Ctr(key, iv, data.encodeToByteArray())

private fun incrementCounter(c: ByteArray) {
    for (i in 15 downTo 0) {
        val v = c[i].toInt() and 0xFF
        if (v == 255) {
            c[i] = 0
        } else {
            c[i] = ((v + 1) and 0xFF).toByte()
            break
        }
    }
}

internal fun keyExpansion(key: ByteArray): IntArray {
    val nk = 8
    val nr = 14
    val w = IntArray(4 * (nr + 1))
    for (i in 0 until nk) {
        w[i] = (key[4 * i].toInt() and 0xFF shl 24) or
            (key[4 * i + 1].toInt() and 0xFF shl 16) or
            (key[4 * i + 2].toInt() and 0xFF shl 8) or
            (key[4 * i + 3].toInt() and 0xFF)
    }
    for (i in nk until w.size) {
        var temp = w[i - 1]
        if (i % nk == 0) {
            temp = subWord(rotWord(temp)) xor (RCON[i / nk - 1] shl 24)
        } else if (i % nk == 4) {
            temp = subWord(temp)
        }
        w[i] = w[i - nk] xor temp
    }
    return w
}

private fun rotWord(w: Int): Int = (w shl 8) or (w ushr 24)

private fun subWord(w: Int): Int =
    (sbox((w ushr 24) and 0xFF) shl 24) or
        (sbox((w ushr 16) and 0xFF) shl 16) or
        (sbox((w ushr 8) and 0xFF) shl 8) or
        sbox(w and 0xFF)

private fun sbox(x: Int): Int = SBOX[x and 0xFF]

internal fun aesEncryptBlock(block: ByteArray, rk: IntArray): ByteArray {
    val s = block.copyOf()
    addRoundKey(s, rk, 0)
    for (round in 1..13) {
        subBytes(s)
        shiftRows(s)
        mixColumns(s)
        addRoundKey(s, rk, round * 4)
    }
    subBytes(s)
    shiftRows(s)
    addRoundKey(s, rk, 56)
    return s
}

private fun subBytes(s: ByteArray) {
    for (i in 0..15) s[i] = sbox(s[i].toInt() and 0xFF).toByte()
}

private fun shiftRows(s: ByteArray) {
    val t = s.copyOf()
    for (r in 1..3) for (c in 0..3) s[r + 4 * c] = t[r + 4 * ((c + r) % 4)]
}

private fun mixColumns(s: ByteArray) {
    for (c in 0..3) {
        val i = 4 * c
        val a0 = s[i].toInt() and 0xFF
        val a1 = s[i + 1].toInt() and 0xFF
        val a2 = s[i + 2].toInt() and 0xFF
        val a3 = s[i + 3].toInt() and 0xFF
        s[i] = (gmul(a0, 2) xor gmul(a1, 3) xor a2 xor a3).toByte()
        s[i + 1] = (a0 xor gmul(a1, 2) xor gmul(a2, 3) xor a3).toByte()
        s[i + 2] = (a0 xor a1 xor gmul(a2, 2) xor gmul(a3, 3)).toByte()
        s[i + 3] = (gmul(a0, 3) xor a1 xor a2 xor gmul(a3, 2)).toByte()
    }
}

internal fun gmul(a: Int, b: Int): Int {
    var p = 0
    var a = a
    var b = b
    for (i in 0..7) {
        if (b and 1 != 0) p = p xor a
        val hi = a and 0x80
        a = (a shl 1) and 0xFF
        if (hi != 0) a = a xor 0x1b
        b = b ushr 1
    }
    return p and 0xFF
}

internal fun addRoundKey(s: ByteArray, w: IntArray, off: Int) {
    for (c in 0..3) for (r in 0..3) {
        val byteOfWord = (w[off + c] ushr (24 - 8 * r)) and 0xFF
        s[r + 4 * c] = (s[r + 4 * c].toInt() xor byteOfWord).toByte()
    }
}
