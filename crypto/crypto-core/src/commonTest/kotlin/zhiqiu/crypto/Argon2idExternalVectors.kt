package zhiqiu.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Argon2id 的外部对照向量：全部由 OpenSSL 3.5.0 的 `openssl kdf ... Argon2id` 算出，不是本实现的输出。
 * 档位必须覆盖 p≠4：RFC 9106 §5.3 只给 p=4 且 segmentLength=1，slice 边界写成 p-1 也能过那一条。
 */
class Argon2idExternalVectors {

    private val pw = "password".encodeToByteArray()
    private val salt = ByteArray(16) { it.toByte() }

    private fun check(t: Int, m: Int, p: Int, expect: String) {
        assertEquals(expect, argon2id(pw, salt, t = t, m = m, p = p, tagLen = 32).toHex(), "t=$t m=$m p=$p")
    }

    @Test fun singlePassSmallMemory() = check(1, 8, 1, "3a008963c65f7ffa7331a90babd1fd17ea9f03bd05356b857ceb43f7b7080adc")

    @Test fun secondPassOneLane() = check(2, 32, 1, "7051b23235b9ac1785936c66426bc83af395e79ec48ecb1951bad387df027584")

    @Test fun secondPassTwoLanes() = check(2, 64, 2, "1f08414698841d6a3e83a122d1348d7d577b31943c597302442de65fc2c9efba")

    @Test fun thirdPassOneLane() = check(3, 1024, 1, "8f92a1acc0c0509e55d57b2be709a525017461f8f29d51008c6a0b247f0d1748")

    /** ClipSync 房间密钥的实际档位。 */
    @Test fun productionParamsOneLane() =
        check(2, 19_456, 1, "39903a34cd393876436e671cd690dfe8ab9ce7b13459a7e04ec0e4f0694ddb0d")

    @Test fun productionParamsFourLanes() =
        check(2, 19_456, 4, "6ba44cd079e7f8703b26487ab3361e122614e4fc296b5160f8d76dcfcb78ab7e")
}
