package zhiqiu.crypto

import kotlin.math.max

// Argon2id (RFC 9106, v0x13) — 纯 Kotlin 实现，依赖本包的 BLAKE2b 作为内部哈希 H。
// 内存硬密钥派生函数，抗 GPU/ASIC 暴力破解，优于 PBKDF2。

private fun putLE32(buf: ByteArray, off: Int, value: Int): Int {
    buf[off] = (value and 0xFF).toByte()
    buf[off + 1] = (value ushr 8 and 0xFF).toByte()
    buf[off + 2] = (value ushr 16 and 0xFF).toByte()
    buf[off + 3] = (value ushr 24 and 0xFF).toByte()
    return off + 4
}

private fun le32Bytes(value: Int): ByteArray = byteArrayOf(
    (value and 0xFF).toByte(), (value ushr 8 and 0xFF).toByte(),
    (value ushr 16 and 0xFF).toByte(), (value ushr 24 and 0xFF).toByte(),
)

private fun concat(a: ByteArray, b: ByteArray): ByteArray {
    val out = ByteArray(a.size + b.size)
    a.copyInto(out, 0)
    b.copyInto(out, a.size)
    return out
}

// 1024 字节块 <-> 128 个 64 位小端字。
private fun blockToWords(block: ByteArray): LongArray {
    val w = LongArray(128)
    for (i in 0 until 128) {
        var x = 0L
        for (b in 0 until 8) x = x or ((block[i * 8 + b].toLong() and 0xFF) shl (8 * b))
        w[i] = x
    }
    return w
}

private fun wordsToBlock(w: LongArray): ByteArray {
    val b = ByteArray(1024)
    for (i in 0 until 128) {
        for (j in 0 until 8) b[i * 8 + j] = (w[i] ushr (8 * j)).toByte()
    }
    return b
}

// Argon2 的 GB 混合（RFC 9106 §3.6）：与 BLAKE2b 不同，不消费消息字，仅做 4 字混合。
private fun argonGb(s: LongArray, a: Int, b: Int, c: Int, d: Int) {
    val lo = { x: Long -> x and 0xFFFFFFFFL }
    s[a] = s[a] + s[b] + 2L * lo(s[a]) * lo(s[b])
    s[d] = rotr64(s[d] xor s[a], 32)
    s[c] = s[c] + s[d] + 2L * lo(s[c]) * lo(s[d])
    s[b] = rotr64(s[b] xor s[c], 24)
    s[a] = s[a] + s[b] + 2L * lo(s[a]) * lo(s[b])
    s[d] = rotr64(s[d] xor s[a], 16)
    s[c] = s[c] + s[d] + 2L * lo(s[c]) * lo(s[d])
    s[b] = rotr64(s[b] xor s[c], 63)
}

// 排列 P 的单次调用：对 16 个 64 位字做 8 次 GB（列向 + 对角向），无消息调度。
private fun argonPRound(s: LongArray) {
    argonGb(s, 0, 4, 8, 12)
    argonGb(s, 1, 5, 9, 13)
    argonGb(s, 2, 6, 10, 14)
    argonGb(s, 3, 7, 11, 15)
    argonGb(s, 0, 5, 10, 15)
    argonGb(s, 1, 6, 11, 12)
    argonGb(s, 2, 7, 8, 13)
    argonGb(s, 3, 4, 9, 14)
}

// 压缩函数 G(X, Y) = P(P(X xor Y)) xor (X xor Y)：
// R = X xor Y，先对 8 行、再对 8 列各应用一次 P（共 16 次 P 调用）。
private fun argonG(x: ByteArray, y: ByteArray): ByteArray {
    val r = ByteArray(1024) { (x[it].toInt() xor y[it].toInt()).toByte() }
    val w = blockToWords(r)
    val tmp = LongArray(16)
    for (row in 0 until 8) {
        for (k in 0 until 16) tmp[k] = w[row * 16 + k]
        argonPRound(tmp)
        for (k in 0 until 16) w[row * 16 + k] = tmp[k]
    }
    for (col in 0 until 8) {
        for (k in 0 until 16) tmp[k] = w[col * 2 + 16 * (k / 2) + (k % 2)]
        argonPRound(tmp)
        for (k in 0 until 16) w[col * 2 + 16 * (k / 2) + (k % 2)] = tmp[k]
    }
    val out = wordsToBlock(w)
    for (i in 0 until 1024) out[i] = (out[i].toInt() xor r[i].toInt()).toByte()
    return out
}

// 变长哈希 H'^T(A)：T<=64 时 H'^T(A) = H^T(LE32(T) || A)，否则递推。
private fun hPrime(t: Int, a: ByteArray): ByteArray {
    if (t <= 64) return blake2b(concat(le32Bytes(t), a), t)
    val r = (t + 31) / 32 - 2
    var v = blake2b(concat(le32Bytes(t), a), 64)
    val out = ByteArray(t)
    v.copyInto(out, 0, 0, 32)
    var offset = 32
    for (k in 2..r) {
        v = blake2b(v, 64)
        v.copyInto(out, offset, 0, 32)
        offset += 32
    }
    val lastLen = t - 32 * r
    blake2b(v, lastLen).copyInto(out, offset)
    return out
}

/**
 * Argon2id 密钥派生。
 * @param password 口令（任意长度）
 * @param salt 盐（建议 >= 16 字节）
 * @param t 遍历遍数（时间成本）
 * @param m 内存大小（KiB，须 >= 8*p）
 * @param p 并行度（lane 数）
 * @param tagLen 输出标签字节数
 * @param secret 可选密钥（机密数据，可为空）
 * @param ad 可选关联数据（可为空）
 * @return [tagLen] 字节派生密钥
 */
fun argon2id(
    password: ByteArray,
    salt: ByteArray,
    t: Int,
    m: Int,
    p: Int,
    tagLen: Int = 32,
    secret: ByteArray = ByteArray(0),
    ad: ByteArray = ByteArray(0),
): ByteArray {
    require(t >= 1) { "iterations(t) >= 1" }
    require(p >= 1) { "parallelism(p) >= 1" }
    require(m >= 8 * p) { "memory(m) >= 8 * parallelism(p)" }
    require(tagLen >= 4) { "tagLen >= 4" }

    val y = 2 // Argon2id
    val version = 0x13
    val mPrime = 4 * p * (m / (4 * p))
    val q = mPrime / p
    val segmentLength = q / 4

    // H0 = H^64(LE32(p)||LE32(tagLen)||LE32(m)||LE32(t)||LE32(v)||LE32(y)||LE32(lenP)||P||... )
    val h0 = run {
        val buf = ByteArray(6 * 4 + 4 + password.size + 4 + salt.size + 4 + secret.size + 4 + ad.size)
        var o = 0
        o = putLE32(buf, o, p)
        o = putLE32(buf, o, tagLen)
        o = putLE32(buf, o, m)
        o = putLE32(buf, o, t)
        o = putLE32(buf, o, version)
        o = putLE32(buf, o, y)
        o = putLE32(buf, o, password.size); password.copyInto(buf, o); o += password.size
        o = putLE32(buf, o, salt.size); salt.copyInto(buf, o); o += salt.size
        o = putLE32(buf, o, secret.size); secret.copyInto(buf, o); o += secret.size
        o = putLE32(buf, o, ad.size); ad.copyInto(buf, o); o += ad.size
        blake2b(buf, 64)
    }

    val B = Array(p) { Array(q) { ByteArray(1024) } }
    for (i in 0 until p) {
        B[i][0] = hPrime(1024, concat(concat(h0, le32Bytes(0)), le32Bytes(i)))
        B[i][1] = hPrime(1024, concat(concat(h0, le32Bytes(1)), le32Bytes(i)))
    }

    // 数据无关寻址：为每个 (pass, lane, slice) 缓存一个地址块 G(ZERO, G(ZERO, Z))。
    val addrCache = mutableMapOf<Triple<Int, Int, Int>, LongArray>()
    fun getAddressBlock(s: Int, lane: Int, slice: Int, blockIndex: Int): LongArray {
        val key = Triple(s, lane, (slice shl 8) or blockIndex)
        addrCache[key]?.let { return it }
        val iw = LongArray(128)
        iw[0] = s.toLong() and 0xFFFFFFFFL
        iw[1] = lane.toLong() and 0xFFFFFFFFL
        iw[2] = slice.toLong() and 0xFFFFFFFFL
        iw[3] = mPrime.toLong() and 0xFFFFFFFFL
        iw[4] = t.toLong() and 0xFFFFFFFFL
        iw[5] = y.toLong() and 0xFFFFFFFFL
        iw[6] = (blockIndex + 1).toLong() and 0xFFFFFFFFL
        val zero = ByteArray(1024)
        val ab = argonG(zero, argonG(zero, wordsToBlock(iw)))
        return blockToWords(ab).also { addrCache[key] = it }
    }

    // 计算参考块索引 [refLane][refIndex]。
    fun computeRef(s: Int, lane: Int, slice: Int, index: Int, prevBlock: ByteArray): Pair<Int, Int> {
        val dataIndependent = (y == 1) || (y == 2 && s == 0 && slice < 2)
        val j1: ULong
        val j2: ULong
        if (dataIndependent) {
            val ab = getAddressBlock(s, lane, slice, index / 128)
            val word = ab[index % 128]
            j1 = (word and 0xFFFFFFFFL).toULong()
            j2 = (word ushr 32).toULong()
        } else {
            val w0 = blockToWords(prevBlock)[0]
            j1 = (w0 and 0xFFFFFFFFL).toULong()
            j2 = (w0 ushr 32).toULong()
        }
        var refLane = (j2 % p.toULong()).toInt()
        if (s == 0 && slice == 0) refLane = lane
        val sameLane = (refLane == lane)
        val rawArea = if (s == 0) {
            if (slice == 0) {
                index - 1
            } else if (sameLane) {
                slice * segmentLength + index - 1
            } else {
                slice * segmentLength + if (index == 0) -1 else 0
            }
        } else {
            if (sameLane) {
                q - segmentLength + index - 1
            } else {
                q - segmentLength + if (index == 0) -1 else 0
            }
        }
        val refArea = max(rawArea, 1)
        val j1u = j1
        val x = (j1u * j1u) shr 32
        val yRel = (refArea.toULong() * x) shr 32
        var zz = (refArea - 1) - yRel.toInt()
        if (zz < 0) zz = 0
        val startPos = if (s != 0 && slice != p - 1) (slice + 1) * segmentLength else 0
        val absPos = (startPos + zz) % q
        return refLane to absPos
    }

    for (s in 0 until t) {
        for (slice in 0 until 4) {
            for (lane in 0 until p) {
                val startIdx = if (s == 0 && slice == 0) 2 else 0
                for (index in startIdx until segmentLength) {
                    val j = slice * segmentLength + index
                    val prevOffset = if (j == 0) q - 1 else j - 1
                    val prev = B[lane][prevOffset]
                    val (refLane, refIndex) = computeRef(s, lane, slice, index, prev)
                    val g = argonG(prev, B[refLane][refIndex])
                    if (s == 0) {
                        g.copyInto(B[lane][j])
                    } else {
                        for (k in 0 until 1024) B[lane][j][k] = (B[lane][j][k].toInt() xor g[k].toInt()).toByte()
                    }
                }
            }
        }
    }

    val c = ByteArray(1024)
    for (lane in 0 until p) {
        val last = B[lane][q - 1]
        for (k in 0 until 1024) c[k] = (c[k].toInt() xor last[k].toInt()).toByte()
    }
    return hPrime(tagLen, c)
}

/**
 * 以 UTF-8 文本口令调用 [argon2id]。
 * @see argon2id
 */
fun argon2id(
    password: String, salt: ByteArray, t: Int, m: Int, p: Int, tagLen: Int = 32,
    secret: ByteArray = ByteArray(0), ad: ByteArray = ByteArray(0),
): ByteArray = argon2id(password.encodeToByteArray(), salt, t, m, p, tagLen, secret, ad)
