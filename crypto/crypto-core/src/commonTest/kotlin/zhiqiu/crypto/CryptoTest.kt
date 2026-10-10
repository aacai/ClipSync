package zhiqiu.crypto

import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertNull

class CryptoTest {
    // ---------------- 原有：SHA-256 / HMAC-SHA256 / PBKDF2 / AES-256-CTR ----------------

    @Test
    fun sha256KnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256("abc".encodeToByteArray()).toHex(),
        )
    }

    @Test
    fun hmacSha256Rfc4231Case1() {
        val key = ByteArray(20) { 0x0b }
        val data = "Hi There".encodeToByteArray()
        assertEquals(
            "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
            hmacSha256(key, data).toHex(),
        )
    }

    @Test
    fun pbkdf2HmacSha256Rfc7914() {
        val password = "passwd".encodeToByteArray()
        val salt = "salt".encodeToByteArray()
        assertEquals(
            "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc",
            pbkdf2HmacSha256(password, salt, iterations = 1, keyLen = 32).toHex(),
        )
    }

    @Test
    fun aes256EcbZeroBlock() {
        val ct = aes256Ctr(ByteArray(32), ByteArray(16), ByteArray(16)).toHex()
        assertEquals("dc95c078a2408989ad48a21492842087", ct)
    }

    @Test
    fun aes256EcbNonTrivialBlock() {
        val key = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f".fromHex()
        val block = "00112233445566778899aabbccddeeff".fromHex()
        assertEquals("8ea2b7ca516745bfeafc49904b496089", aes256Ctr(key, block, ByteArray(16)).toHex())
    }

    @Test
    fun aes256CtrRoundTrip() {
        val key = randomBytes(32); val iv = randomBytes(16)
        val pt = "命盘数据 destiny backup 🔒".encodeToByteArray()
        assertTrue(pt.contentEquals(aes256Ctr(key, iv, aes256Ctr(key, iv, pt))))
    }

    // ---------------- SHA-512 ----------------

    @Test
    fun sha512KnownVector() {
        // FIPS 180-4 已知向量
        assertEquals(
            "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a" +
                "274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
            sha512("abc".encodeToByteArray()).toHex(),
        )
    }

    @Test
    fun sha512Empty() {
        assertEquals(
            "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce" +
                "47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e",
            sha512(ByteArray(0)).toHex(),
        )
    }

    @Test
    fun hmacSha512Rfc4231Case1() {
        val key = ByteArray(20) { 0x0b }
        val data = "Hi There".encodeToByteArray()
        assertEquals(
            "87aa7cdea5ef619d4ff0b4241a1d6cb02379f4e2ce4ec2787ad0b30545e17cd" +
                "edaa833b7d6b8a702038b274eaea3f4e4be9d914eeb61f1702e696c203a126854",
            hmacSha512(key, data).toHex(),
        )
    }

    // ---------------- AES-256-CBC ----------------

    @Test
    fun aes256CbcNistVector() {
        // NIST SP 800-38A CBC-AES256
        val key = "603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4".fromHex()
        val iv = "000102030405060708090a0b0c0d0e0f".fromHex()
        val pt = ("6bc1bee22e409f96e93d7e117393172a" + "ae2d8a571e03ac9c9eb76fac45af8e51" +
            "30c81c46a35ce411e5fbc1191a0a52ef" + "f69f2445df4f9b17ad2b417be66c3710").fromHex()
        val ct = aes256CbcEncryptRaw(key, iv, pt).toHex()
        assertEquals(
            "f58c4c04d6e5f1ba779eabfb5f7bfbd69cfc4e967edb808d679f777bc6702c7d" +
                "39f23369a9d9bacfa530e26304231461b2eb05e2c39be9fcda6c19078c6a9d1b",
            ct,
        )
    }

    @Test
    fun aes256CbcRoundTrip() {
        val key = randomBytes(32); val iv = randomBytes(16)
        val pt = "命盘数据 CBC 🔒🔒🔒".encodeToByteArray()
        val ct = aes256CbcEncrypt(key, iv, pt)
        assertTrue(ct.size % 16 == 0)
        assertTrue(pt.contentEquals(aes256CbcDecrypt(key, iv, ct)))
    }

    @Test
    fun aes256CbcRawRoundTrip() {
        val key = randomBytes(32); val iv = randomBytes(16)
        val pt = ByteArray(64) { it.toByte() }
        val ct = aes256CbcEncryptRaw(key, iv, pt)
        assertTrue(pt.contentEquals(aes256CbcDecryptRaw(key, iv, ct)))
    }

    // ---------------- ChaCha20 / Poly1305 / AEAD ----------------

    @Test
    fun chacha20BlockVector() {
        // RFC 8439 §2.3.2
        val key = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f".fromHex()
        val nonce = "000000090000004a00000000".fromHex()
        assertEquals(
            "10f1e7e4d13b5915500fdd1fa32071c4c7d1f4c733c068030422aa9ac3d46c4e" +
                "d2826446079faa0914c2d705d98b02a2b5129cd1de164eb9cbd083e8a2503c4e",
            chacha20Block(key, 1, nonce).toHex(),
        )
    }

    @Test
    fun chacha20EncryptVector() {
        // RFC 8439 §2.4.2
        val key = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f".fromHex()
        val nonce = "000000000000004a00000000".fromHex()
        val pt = "Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, sunscreen would be it.".encodeToByteArray()
        assertEquals(
            "6e2e359a2568f98041ba0728dd0d6981e97e7aec1d4360c20a27afccfd9fae0b" +
                "f91b65c5524733ab8f593dabcd62b3571639d624e65152ab8f530c359f0861d8" +
                "07ca0dbf500d6a6156a38e088a22b65e52bc514d16ccf806818ce91ab7793736" +
                "5af90bbf74a35be6b40b8eedf2785e42874d",
            chacha20(key, 1, nonce, pt).toHex(),
        )
    }

    @Test
    fun chacha20RoundTrip() {
        val key = ByteArray(32) { it.toByte() }; val nonce = ByteArray(12) { (it * 7).toByte() }
        val pt = randomBytes(137)
        assertTrue(pt.contentEquals(chacha20(key, 0, nonce, chacha20(key, 0, nonce, pt))))
    }

    @Test
    fun poly1305Vector() {
        // RFC 8439 §2.5.2
        val key = "85d6be7857556d337f4452fe42d506a80103808afb0db2fd4abff6af4149f51b".fromHex()
        val msg = "Cryptographic Forum Research Group".encodeToByteArray()
        assertEquals("a8061dc1305136c6c22b8baf0c0127a9", poly1305Mac(msg, key).toHex())
    }

    @Test
    fun poly1305KeyGenVector() {
        // RFC 8439 §2.6.2
        val key = "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f".fromHex()
        val nonce = "000000000001020304050607".fromHex()
        val block = chacha20Block(key, 0, nonce)
        assertEquals(
            "8ad5a08b905f81cc815040274ab29471a833b637e3fd0da508dbb8e2fdd1a646",
            block.copyOfRange(0, 32).toHex(),
        )
    }

    @Test
    fun chacha20Poly1305AeadVector() {
        // RFC 8439 §2.8.2
        val key = "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f".fromHex()
        val nonce = "070000004041424344454647".fromHex()
        val aad = "50515253c0c1c2c3c4c5c6c7".fromHex()
        val pt = "Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, sunscreen would be it.".encodeToByteArray()
        val sealed = chacha20Poly1305Seal(key, nonce, pt, aad)
        val expectedCt = ("d31a8d34648e60db7b86afbc53ef7ec2a4aded51296e08fea9e2b5a736ee62d6" +
            "3dbea45e8ca9671282fafb69da92728b1a71de0a9e060b2905d6a5b67ecd3b36" +
            "92ddbd7f2d778b8c9803aee328091b58fab324e4fad675945585808b4831d7bc" +
            "3ff4def08e4b7a9de576d26586cec64b6116")
        assertEquals(expectedCt, sealed.copyOfRange(0, sealed.size - 16).toHex())
        assertEquals("1ae10b594f09e26a7e902ecbd0600691", sealed.copyOfRange(sealed.size - 16, sealed.size).toHex())
        assertTrue(pt.contentEquals(chacha20Poly1305Open(key, nonce, sealed, aad)))
    }

    @Test
    fun chacha20Poly1305RoundTrip() {
        val key = randomBytes(32); val nonce = randomBytes(12)
        val pt = "命盘数据 AEAD 🔐".encodeToByteArray()
        val aad = "header".encodeToByteArray()
        val sealed = chacha20Poly1305Seal(key, nonce, pt, aad)
        assertTrue(pt.contentEquals(chacha20Poly1305Open(key, nonce, sealed, aad)))
    }

    @Test
    fun chacha20Poly1305TamperFails() {
        val key = randomBytes(32); val nonce = randomBytes(12)
        val pt = randomBytes(50)
        val sealed = chacha20Poly1305Seal(key, nonce, pt)
        sealed[0] = (sealed[0].toInt() xor 0x01).toByte()
        assertFailsWith<IllegalArgumentException> { chacha20Poly1305Open(key, nonce, sealed) }
    }

    // ---------------- BLAKE2b (RFC 7693, Argon2 内部哈希) ----------------

    @Test
    fun blake2bAbc() {
        // RFC 7693 附录 BLAKE2b-512("abc")
        assertEquals(
            "ba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d1" +
                "7d87c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923",
            blake2b("abc".encodeToByteArray()).toHex(),
        )
    }

    // ---------------- AES-256-GCM (NIST SP 800-38D) ----------------

    @Test
    fun aes256GcmWycheproofTc91() {
        // Project Wycheproof (aes_gcm) tcId 91：keySize=256, ivSize=96, tagSize=128, result=valid
        val key = "92ace3e348cd821092cd921aa3546374299ab46209691bc28b8752d17f123c20".fromHex()
        val iv = "00112233445566778899aabb".fromHex()
        val aad = "00000000ffffffff".fromHex()
        val pt = "00010203040506070809".fromHex()
        val (ct, tag) = aes256GcmEncrypt(key, iv, pt, aad)
        assertEquals("e27abdd2d2a53d2f136b", ct.toHex())
        assertEquals("9a4a2579529301bcfb71c78d4060f52c", tag.toHex())
        assertTrue(pt.contentEquals(aes256GcmDecrypt(key, iv, ct, tag, aad)))
    }

    @Test
    fun aes256GcmRoundTrip() {
        val key = randomBytes(32)
        val iv = randomBytes(12)
        val aad = "auth-header".encodeToByteArray()
        val pt = "命盘加密 GCM 🔐🔐🔐".encodeToByteArray()
        val (ct, tag) = aes256GcmEncrypt(key, iv, pt, aad)
        assertTrue(ct.size == pt.size)
        assertTrue(pt.contentEquals(aes256GcmDecrypt(key, iv, ct, tag, aad)))
    }

    @Test
    fun aes256GcmTamperFails() {
        val key = randomBytes(32)
        val iv = randomBytes(12)
        val pt = randomBytes(64)
        val (ct, tag) = aes256GcmEncrypt(key, iv, pt)
        val bad = ct.copyOf()
        bad[0] = (bad[0].toInt() xor 0x01).toByte()
        assertNull(aes256GcmDecryptOrNull(key, iv, bad, tag))
        // 篡改标签
        val badTag = tag.copyOf()
        badTag[0] = (badTag[0].toInt() xor 0x01).toByte()
        assertNull(aes256GcmDecryptOrNull(key, iv, ct, badTag))
    }

    // ---------------- Argon2id (RFC 9106) ----------------

    @Test
    fun argon2idRfc9106Vector() {
        // RFC 9106 §5.3 官方测试向量（v=0x13, m=32 KiB, t=3, p=4, tag=32B）
        val password = ByteArray(32) { 0x01.toByte() }
        val salt = ByteArray(16) { 0x02.toByte() }
        val secret = ByteArray(8) { 0x03.toByte() }
        val ad = ByteArray(12) { 0x04.toByte() }
        val tag = argon2id(password, salt, t = 3, m = 32, p = 4, tagLen = 32, secret = secret, ad = ad)
        assertEquals(
            "0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659",
            tag.toHex(),
        )
    }

    @Test
    fun argon2idRoundTripConsistency() {
        val password = "correct horse battery staple".encodeToByteArray()
        val salt = randomBytes(16)
        val tag1 = argon2id(password, salt, t = 3, m = 64, p = 2)
        val tag2 = argon2id(password, salt, t = 3, m = 64, p = 2)
        assertTrue(tag1.contentEquals(tag2))
        val other = argon2id("different".encodeToByteArray(), salt, t = 3, m = 64, p = 2)
        assertTrue(!tag1.contentEquals(other))
    }

    // ---------------- 便捷 String 扩展（可用性验证） ----------------

    @Test
    fun stringExtensionsMatchByteApi() {
        assertEquals(sha256("abc".encodeToByteArray()).toHex(), "abc".sha256Hex())
        assertEquals(sha512("abc".encodeToByteArray()).toHex(), "abc".sha512Hex())
        assertEquals(blake2b("abc".encodeToByteArray()).toHex(), "abc".blake2bHex())
        val key = ByteArray(20) { 0x0b }
        assertEquals(hmacSha256(key, "Hi There".encodeToByteArray()).toHex(), "Hi There".hmacSha256(key).toHex())
    }

    @Test
    fun aes256GcmStringOverloadMatches() {
        val key = randomBytes(32); val iv = randomBytes(12)
        val aad = "header"
        val (ct, tag) = aes256GcmEncrypt(key, iv, "明文文本 🔒", aad)
        val (ct2, tag2) = aes256GcmEncrypt(key, iv, "明文文本 🔒".encodeToByteArray(), aad.encodeToByteArray())
        assertTrue(ct.contentEquals(ct2) && tag.contentEquals(tag2))
        assertTrue("明文文本 🔒".encodeToByteArray().contentEquals(aes256GcmDecrypt(key, iv, ct, tag, aad.encodeToByteArray())))
    }

    @Test
    fun chacha20StringOverloadRoundTrip() {
        val key = ByteArray(32) { it.toByte() }; val nonce = ByteArray(12) { (it * 7).toByte() }
        val ct = chacha20(key, 0, nonce, "命盘数据 🔐")
        assertTrue("命盘数据 🔐".encodeToByteArray().contentEquals(chacha20(key, 0, nonce, ct)))
    }

    @Test
    fun argon2idStringOverloadMatches() {
        val salt = ByteArray(16) { 0x02.toByte() }
        val pw = "correct horse battery staple"
        assertTrue(
            argon2id(pw.encodeToByteArray(), salt, t = 3, m = 64, p = 2)
                .contentEquals(argon2id(pw, salt, t = 3, m = 64, p = 2)),
        )
    }

    @Test
    fun aes256CtrStringOverloadRoundTrip() {
        val key = randomBytes(32); val iv = randomBytes(16)
        val ct = aes256Ctr(key, iv, "命盘 CTR 🔒")
        assertTrue("命盘 CTR 🔒".encodeToByteArray().contentEquals(aes256Ctr(key, iv, ct)))
    }
}
