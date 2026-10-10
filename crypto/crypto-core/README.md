# crypto-core

纯 Kotlin 多平台轻量密码学原语库，**仅依赖 Kotlin 标准库、零第三方依赖**，可在 JVM / Android / iOS / Web(wasmJs) 一致运行。

提供的算法：

| 类别 | 算法 |
| --- | --- |
| 哈希 | SHA-256、SHA-512、BLAKE2b |
| MAC | HMAC-SHA256、HMAC-SHA512、Poly1305 |
| 密钥派生 | PBKDF2-HMAC-SHA256、Argon2id（RFC 9106，内存硬） |
| 对称 / AEAD | ChaCha20-Poly1305、AES-256-GCM、AES-256-CTR、AES-256-CBC |
| 工具 | `ByteArray.toHex()` / `String.fromHex()` / `randomBytes` / `constantTimeEquals` |

所有加解密函数均为顶层函数，且多数提供等价的 `String` 扩展（如 `"abc".sha256Hex()`），方便文本场景直接使用。

## 平台

JVM 11+ · Android · iOS（arm64 / simulator）· Kotlin/Wasm(JS)。

## 添加依赖

坐标：`io.github.zhiqiu:crypto-core`（当前仓库版本 `0.1.0-SNAPSHOT`）。

```kotlin
// Kotlin 多平台 / Android
dependencies {
    implementation("io.github.zhiqiu:crypto-core:0.1.0")
}

// 纯 JVM
dependencies {
    implementation("io.github.zhiqiu:crypto-core-jvm:0.1.0")
}
```

## 快速开始：用口令加密 / 解密（推荐）

最常见的“用密码保护一段数据”场景，推荐 **Argon2id 派生密钥 + ChaCha20-Poly1305 认证加密**。ChaCha20-Poly1305 是 AEAD，自带完整性校验，一个函数同时完成加密与认证。

```kotlin
import zhiqiu.crypto.*

// 加密：argon2id(口令, 盐) -> 32 字节密钥 -> ChaCha20-Poly1305
fun encrypt(plaintext: String, password: String): String {
    val salt = randomBytes(16)            // 盐，随机且可公开
    val nonce = randomBytes(12)           // ChaCha20 的 nonce，须唯一
    val key = argon2id(password, salt, t = 3, m = 64_000, p = 4) // 默认 32 字节
    val sealed = chacha20Poly1305Seal(key, nonce, plaintext.encodeToByteArray())
    // 把盐/nonce/密文打包成可传输字符串（生产可换成 JSON 信封）
    return "${salt.toHex()}.${nonce.toHex()}.${sealed.toHex()}"
}

// 解密
fun decrypt(bundle: String, password: String): String {
    val (saltHex, nonceHex, dataHex) = bundle.split(".")
    val key = argon2id(password, saltHex.fromHex(), t = 3, m = 64_000, p = 4)
    return chacha20Poly1305Open(key, nonceHex.fromHex(), dataHex.fromHex()).decodeToString()
}

val enc = encrypt("数据abc", "123456")   // enc 为 "盐.nonce.密文" 形式的十六进制字符串
val dec = decrypt(enc, "123456")             // 解密还原：dec == "数据abc"
```

> 标签校验失败（密码错误或数据被篡改）时 `chacha20Poly1305Open` 抛 `IllegalArgumentException`（恒定时间比较，抗时序侧信道）。

## 共享密钥的对称加密

当通信双方已持有同一把 32 字节密钥时，直接用 AEAD 即可。

### ChaCha20-Poly1305（推荐，无填充、快）

```kotlin
val key = randomBytes(32)   // 32 字节，须安全保管
val nonce = randomBytes(12) // 每条消息唯一

val ciphertext = chacha20Poly1305Seal(key, nonce, "secret".encodeToByteArray())
val plaintext = chacha20Poly1305Open(key, nonce, ciphertext).decodeToString()
```

### AES-256-GCM

```kotlin
val key = randomBytes(32)
val iv = randomBytes(12)    // 标准 12 字节，须唯一

val (ct, tag) = aes256GcmEncrypt(key, iv, "secret".encodeToByteArray())
val plaintext = aes256GcmDecrypt(key, iv, ct, tag)            // 校验失败抛异常
// 或：失败返回 null，避免异常
val maybe = aes256GcmDecryptOrNull(key, iv, ct, tag)
```

### AES-256-CTR（无完整性认证）

```kotlin
val key = randomBytes(32); val iv = randomBytes(16)
val ct = aes256Ctr(key, iv, "secret".encodeToByteArray())
val pt = aes256Ctr(key, iv, ct)   // 同一函数加解密
```

> ⚠️ CTR 只保证机密性、不保证完整性。若需防篡改，请额外用 HMAC（见下文），或优先选用上面的 AEAD。

### AES-256-CBC（PKCS#7 填充）

```kotlin
val key = randomBytes(32); val iv = randomBytes(16)
val ct = aes256CbcEncrypt(key, iv, "secret".encodeToByteArray())
val pt = aes256CbcDecrypt(key, iv, ct)
```

## 密钥派生

### Argon2id（首选，抗 GPU/ASIC）

```kotlin
// 字节口令
val key = argon2id(
    password = "password".encodeToByteArray(),
    salt = randomBytes(16),
    t = 3,        // 遍数（时间成本）
    m = 64_000,   // 内存 KiB（越大越抗暴力破解）
    p = 4,        // 并行度
    tagLen = 32,  // 输出字节数
)

// 或直接传字符串口令
val key2 = argon2id("password", randomBytes(16), t = 3, m = 64_000, p = 4)
```

### PBKDF2-HMAC-SHA256（兼容性更好，但仅时间成本）

```kotlin
val key = pbkdf2HmacSha256(
    password = "password".encodeToByteArray(),
    salt = randomBytes(16),
    iterations = 120_000,
    keyLen = 32,
)
```

## 哈希与消息认证

```kotlin
"hello".sha256Hex()                              // SHA-256 十六进制串
"hello".sha512()                                 // SHA-512 字节
"hello".blake2b(32)                              // BLAKE2b，输出 32 字节
"hello".blake2bHex()                             // BLAKE2b 十六进制串

val key = randomBytes(32)
"message".hmacSha256(key)                        // HMAC-SHA256（字节）
"message".hmacSha512(key)                        // HMAC-SHA512（字节）

// 等价于字节接口
sha256("hello".encodeToByteArray())
hmacSha256(key, "message".encodeToByteArray())
```

## 工具函数

```kotlin
val hex = bytes.toHex()        // 字节数组 -> 十六进制小写串
val bytes = hex.fromHex()      // 十六进制串 -> 字节数组
constantTimeEquals(a, b)       // 恒定时间比较，用于 MAC 校验
randomBytes(16)                // 生成随机字节（见下方安全提示）
```

## 安全注意事项

1. **随机量要安全**：`randomBytes` 基于平台默认 `Random`，**不保证密码学安全**。生产环境生成盐(salt)、nonce、IV 等机密随机量时，请使用平台 CSPRNG：
   - JVM / Android：`java.security.SecureRandom`
   - Web：`crypto.getRandomValues`
   - iOS：`SecRandomCopyBytes`
2. **nonce / IV 必须唯一**：同一把密钥下，ChaCha20 的 nonce、AES 的 IV 绝不能复用，否则会灾难性地泄露密钥流（尤其 CTR / ChaCha20）。
3. **优先用 AEAD**：需要“机密性 + 完整性”时，优先 `ChaCha20-Poly1305` 或 `AES-256-GCM`，不要单独用 CTR/CBC。
4. **口令派生用 Argon2id**：比 PBKDF2 更能抵抗GPU/ASIC 集群；`m`（内存）与 `t`（遍数）越大越安全，按设备能力权衡。
5. **密钥管理**：本库只负责算法，密钥的存储与传输由调用方负责，切勿明文写入日志或备份。

## 正确性验证

算法实现均通过权威测试向量（见模块测试 `CryptoTest`）：SHA-256/512（FIPS 180）、HMAC（RFC 4231）、PBKDF2（RFC 6070）、AES-256（FIPS 197）、AES-GCM（Project Wycheproof）、BLAKE2b（RFC 7693）、Argon2id（RFC 9106 §5.3）。

## 许可证

MIT
