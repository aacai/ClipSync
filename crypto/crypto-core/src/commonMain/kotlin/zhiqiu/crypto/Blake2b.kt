package zhiqiu.crypto

// BLAKE2b (RFC 7693) — 纯 Kotlin 实现，仅依赖 Kotlin 标准库。
// Argon2 (RFC 9106) 将其作为内部哈希 H。摘要长度可配置（默认 64 字节）。

// 注：Kotlin 拒绝最高位为 1 的 64 位十六进制字面量（其无符号幅值超过 Long.MAX_VALUE），
// 因此对高位置位的常量用「高位 shl 32 or 低位」方式书写，等价且可移植。
private val BLAKE2B_IV = longArrayOf(
    0x6a09e667f3bcc908L,
    (0xbb67ae85L shl 32) or 0x84caa73bL,
    0x3c6ef372fe94f82bL,
    (0xa54ff53aL shl 32) or 0x5f1d36f1L,
    0x510e527fade682d1L,
    (0x9b05688cL shl 32) or 0x2b3e6c1fL,
    0x1f83d9abfb41bd6bL,
    0x5be0cd19137e2179L,
)

// BLAKE2b 消息调度（12 轮，第 10/11 轮复用 SIGMA[0]/[1]）。
private val BLAKE2B_SIGMA = arrayOf(
    intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
    intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3),
    intArrayOf(11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4),
    intArrayOf(7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8),
    intArrayOf(9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13),
    intArrayOf(2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9),
    intArrayOf(12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11),
    intArrayOf(13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10),
    intArrayOf(6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5),
    intArrayOf(10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0),
)

internal fun rotr64(x: Long, n: Int): Long = (x ushr n) or (x shl (64 - n))

// BLAKE2b 的 G 混合函数（作用于 4 个 64 位字 a,b,c,d，消息字 x,y）。
private fun blake2bG(s: LongArray, a: Int, b: Int, c: Int, d: Int, x: Long, y: Long) {
    s[a] = (s[a] + s[b] + x)
    s[d] = rotr64(s[d] xor s[a], 32)
    s[c] = (s[c] + s[d])
    s[b] = rotr64(s[b] xor s[c], 24)
    s[a] = (s[a] + s[b] + y)
    s[d] = rotr64(s[d] xor s[a], 16)
    s[c] = (s[c] + s[d])
    s[b] = rotr64(s[b] xor s[c], 63)
}

/** BLAKE2b 单轮（8 次 G 混合，使用 [round] 对应的 SIGMA 行）。[state] 与 [msg] 均为 16 个 64 位字。 */
internal fun blake2bRound1(state: LongArray, msg: LongArray, round: Int) {
    require(state.size == 16 && msg.size == 16)
    val sm = BLAKE2B_SIGMA[round % 10]
    blake2bG(state, 0, 4, 8, 12, msg[sm[0]], msg[sm[1]])
    blake2bG(state, 1, 5, 9, 13, msg[sm[2]], msg[sm[3]])
    blake2bG(state, 2, 6, 10, 14, msg[sm[4]], msg[sm[5]])
    blake2bG(state, 3, 7, 11, 15, msg[sm[6]], msg[sm[7]])
    blake2bG(state, 0, 5, 10, 15, msg[sm[8]], msg[sm[9]])
    blake2bG(state, 1, 6, 11, 12, msg[sm[10]], msg[sm[11]])
    blake2bG(state, 2, 7, 8, 13, msg[sm[12]], msg[sm[13]])
    blake2bG(state, 3, 4, 9, 14, msg[sm[14]], msg[sm[15]])
}

/** BLAKE2b 哈希的一轮组（12 轮，轮索引 0..11）。 */
internal fun blake2bRound(state: LongArray, msg: LongArray) {
    require(state.size == 16 && msg.size == 16)
    for (round in 0..11) blake2bRound1(state, msg, round)
}

private fun readLE64(b: ByteArray, off: Int): Long {
    var w = 0L
    for (i in 0 until 8) w = w or ((b[off + i].toLong() and 0xFF) shl (8 * i))
    return w
}

/**
 * BLAKE2b 哈希（RFC 7693）。比 SHA-2 更快且结构上更安全。
 * @param message 待哈希数据
 * @param digestLen 输出字节数（1..64，默认 64）
 * @return [digestLen] 字节摘要
 *
 * 当前仅支持 keyLen=0、fanout=1、depth=1 的默认参数化（满足 Argon2 内部哈希 H 的需求）。
 */
fun blake2b(message: ByteArray, digestLen: Int = 64): ByteArray {
    require(digestLen in 1..64) { "BLAKE2b 摘要长度须为 1..64 字节" }
    // 参数块（64 字节，小端）：byte0 = 摘要长度；byte2 = fanout=1；byte3 = depth=1。
    val param = ByteArray(64)
    param[0] = digestLen.toByte()
    param[2] = 1
    param[3] = 1
    val h = LongArray(8) { i -> BLAKE2B_IV[i] xor readLE64(param, i * 8) }
    val v = LongArray(16)
    val total = message.size
    val blockCount = maxOf((total + 127) / 128, 1)
    for (bc in 0 until blockCount) {
        val isLast = (bc == blockCount - 1)
        val m = LongArray(16)
        val start = bc * 128
        for (i in 0 until 16) {
            var w = 0L
            for (b in 0 until 8) {
                val idx = start + i * 8 + b
                val byteVal = if (idx < total) (message[idx].toInt() and 0xFF) else 0
                w = w or (byteVal.toLong() shl (8 * b))
            }
            m[i] = w
        }
        val tlen = if (isLast) total.toLong() else ((bc + 1) * 128L)
        v[0] = h[0]; v[1] = h[1]; v[2] = h[2]; v[3] = h[3]
        v[4] = h[4]; v[5] = h[5]; v[6] = h[6]; v[7] = h[7]
        v[8] = BLAKE2B_IV[0]; v[9] = BLAKE2B_IV[1]; v[10] = BLAKE2B_IV[2]; v[11] = BLAKE2B_IV[3]
        v[12] = BLAKE2B_IV[4] xor tlen
        v[13] = BLAKE2B_IV[5]
        v[14] = BLAKE2B_IV[6] xor (if (isLast) -1L else 0L)
        v[15] = BLAKE2B_IV[7]
        blake2bRound(v, m)
        for (i in 0..7) h[i] = h[i] xor v[i] xor v[i + 8]
    }
    val out = ByteArray(digestLen)
    for (i in 0 until digestLen) {
        val word = h[i / 8]
        out[i] = (word ushr (8 * (i % 8))).toByte()
    }
    return out
}

/** 对 UTF-8 文本计算 BLAKE2b 摘要。 */
fun String.blake2b(digestLen: Int = 64): ByteArray = blake2b(encodeToByteArray(), digestLen)

/** 对 UTF-8 文本计算 BLAKE2b，并以十六进制小写串返回。 */
fun String.blake2bHex(digestLen: Int = 64): String = blake2b(encodeToByteArray(), digestLen).toHex()
