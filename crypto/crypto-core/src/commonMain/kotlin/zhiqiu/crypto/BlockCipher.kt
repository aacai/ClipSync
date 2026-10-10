package zhiqiu.crypto

// ---------------------------------------------------------------- AES-256 解密（逆变换，复用 Crypto.kt 的 AES 内核）

private val INV_SBOX: IntArray by lazy {
    val inv = IntArray(256)
    for (i in 0..255) inv[SBOX[i]] = i
    inv
}

private fun invSubBytes(s: ByteArray) {
    for (i in 0..15) s[i] = INV_SBOX[s[i].toInt() and 0xFF].toByte()
}

private fun invShiftRows(s: ByteArray) {
    val t = s.copyOf()
    for (r in 1..3) for (c in 0..3) s[r + 4 * c] = t[r + 4 * ((c - r + 4) % 4)]
}

private fun invMixColumns(s: ByteArray) {
    for (c in 0..3) {
        val i = 4 * c
        val a0 = s[i].toInt() and 0xFF
        val a1 = s[i + 1].toInt() and 0xFF
        val a2 = s[i + 2].toInt() and 0xFF
        val a3 = s[i + 3].toInt() and 0xFF
        s[i] = (gmul(a0, 0x0e) xor gmul(a1, 0x0b) xor gmul(a2, 0x0d) xor gmul(a3, 0x09)).toByte()
        s[i + 1] = (gmul(a0, 0x09) xor gmul(a1, 0x0e) xor gmul(a2, 0x0b) xor gmul(a3, 0x0d)).toByte()
        s[i + 2] = (gmul(a0, 0x0d) xor gmul(a1, 0x09) xor gmul(a2, 0x0e) xor gmul(a3, 0x0b)).toByte()
        s[i + 3] = (gmul(a0, 0x0b) xor gmul(a1, 0x0d) xor gmul(a2, 0x09) xor gmul(a3, 0x0e)).toByte()
    }
}

private fun aesDecryptBlock(block: ByteArray, rk: IntArray): ByteArray {
    val s = block.copyOf()
    addRoundKey(s, rk, 56)
    for (round in 13 downTo 1) {
        invShiftRows(s)
        invSubBytes(s)
        addRoundKey(s, rk, round * 4)
        invMixColumns(s)
    }
    invShiftRows(s)
    invSubBytes(s)
    addRoundKey(s, rk, 0)
    return s
}

private fun xorBlocks(a: ByteArray, b: ByteArray): ByteArray {
    val out = ByteArray(a.size)
    for (i in a.indices) out[i] = (a[i].toInt() xor b[i].toInt()).toByte()
    return out
}

/** AES-256-CBC 加密（无填充，[data] 长度须为 16 字节整数倍）。 */
fun aes256CbcEncryptRaw(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
    require(key.size == 32) { "AES-256 需要 32 字节密钥" }
    require(iv.size == 16) { "CBC 需要 16 字节 IV" }
    require(data.size % 16 == 0) { "CBC 明文需为 16 字节整数倍" }
    val rk = keyExpansion(key)
    val out = ByteArray(data.size)
    var prev = iv.copyOf()
    var pos = 0
    while (pos < data.size) {
        val enc = aesEncryptBlock(xorBlocks(data.copyOfRange(pos, pos + 16), prev), rk)
        enc.copyInto(out, pos)
        prev = enc
        pos += 16
    }
    return out
}

/** AES-256-CBC 解密（无填充，[data] 长度须为 16 字节整数倍）。 */
fun aes256CbcDecryptRaw(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
    require(key.size == 32) { "AES-256 需要 32 字节密钥" }
    require(iv.size == 16) { "CBC 需要 16 字节 IV" }
    require(data.size % 16 == 0) { "CBC 密文需为 16 字节整数倍" }
    val rk = keyExpansion(key)
    val out = ByteArray(data.size)
    var prev = iv.copyOf()
    var pos = 0
    while (pos < data.size) {
        val block = data.copyOfRange(pos, pos + 16)
        xorBlocks(aesDecryptBlock(block, rk), prev).copyInto(out, pos)
        prev = block
        pos += 16
    }
    return out
}

/** AES-256-CBC 加密并做 PKCS#7 填充。 */
fun aes256CbcEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray): ByteArray =
    aes256CbcEncryptRaw(key, iv, pkcs7Pad(plaintext))

/** AES-256-CBC 解密并去除 PKCS#7 填充。 */
fun aes256CbcDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray =
    pkcs7Unpad(aes256CbcDecryptRaw(key, iv, ciphertext))

/** PKCS#7 填充（默认块大小 16）。 */
fun pkcs7Pad(data: ByteArray, blockSize: Int = 16): ByteArray {
    val pad = blockSize - (data.size % blockSize)
    val out = ByteArray(data.size + pad)
    data.copyInto(out)
    for (i in data.size until out.size) out[i] = pad.toByte()
    return out
}

/** 去除 PKCS#7 填充（校验填充合法性，防止 Padding Oracle）。 */
fun pkcs7Unpad(data: ByteArray, blockSize: Int = 16): ByteArray {
    require(data.isNotEmpty()) { "空数据无法去填充" }
    val pad = data[data.size - 1].toInt() and 0xFF
    require(pad in 1..blockSize) { "非法 PKCS7 填充长度: $pad" }
    require(data.size >= pad) { "PKCS7 填充超长" }
    return data.copyOfRange(0, data.size - pad)
}
