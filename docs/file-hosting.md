# 文件托管方案：现状、腾讯云 IMSDK 评估与推荐路线

本文说明 ClipSync 两端（Kotlin Multiplatform 与 Rust）的端到端加密与文件托管现状，
评估「接入腾讯云即时通信 IM（IMSDK）做文件上传下载」的可行性，并给出推荐路线。

**结论先说**：IMSDK 不能当作「换个文件托管」的插件接进来。真要接，传输层要动、
要凭空多一个后端、桌面端还接不上（IM 没有 JVM / Rust 客户端 SDK）。
想要「链接活得久、下载稳」，正确的切入点是**只换文件托管、不动传输层**，
而传输层与托管层之间已经有 `FileTransport` 这道缝。

---

## 一、现状

### 1.1 房间密钥

两端各算一遍，参数一字不差，所以能互通。

| 步骤 | Kotlin | Rust |
|---|---|---|
| salt | `sha256(房间码)[0..16]` | 同（`clipsync-core/src/room.rs`） |
| 口令输入 | 房间码，或 `房间码 + "\0" + 密码` | 同 |
| KDF | Argon2id(t=2, m=19456 KiB, p=1) | 同（`clipsync-core/src/lib.rs`） |
| 主密钥 | 32 字节 | 32 字节 |

- 代码：`shared/src/commonMain/kotlin/zhiqiu/app/cs/core/RoomCrypto.kt`
- 落盘只存派生密钥，不存明文密码（房间凭据用 installId 派生的 KEK 加密后存）。
- `RoomCrypto` 每次加解密都重新解码密钥，`wipe()` 之后不可能再解出东西。

### 1.2 文本

```
明文 JSON ClipPayload{text, len}
  → AES-256-GCM（随机 12B IV，AAD = clipTopic）
  → 上线 IV || 密文 || tag(16B)
  → Base64 → 信封的 ct 字段
```

- `clipTopic = cs/v1/<房间码 sha256 前 8 位>/clip`
- 信封本身明文走 MQTT：`mid / ts / dev / name / kind` 只有 `ct` 是密的。
- `mid` 是随机 8 字节，不是内容哈希：内容哈希等于给中转一个「拿一句话反查是谁发的」预言机。
- AAD 绑 topic 的效果：同一段密文换个话题/换个房间，GCM 验签直接失败。

### 1.3 文件

```
明文 → sha256（磁盘上的文件按 64KB 分块流式算；剪贴板里的字节直接在内存算）
     → AES-256-GCM，AAD = "clipTopic\nname\nmime\nsize\nsha256"
     → 密文上传托管源，随机文件名 cs-<16 位>.bin
     → MQTT 只广播加密后的元信息 FilePayload{name,mime,size,sha256,link,expiresAt}
```

- 收端：下载 → 同 AAD 解密 → 校验 sha256 与 size，任一不符就丢弃。
- AAD 把密文钉死在「这个房间 + 这个名字 + 这个 mime + 这个大小 + 这个摘要」上，
  跨房搬运在 GCM 阶段就炸，不会静默解出垃圾。
- 托管方只拿到一坨随机名密文；Broker 只拿到一坨 Base64 密文。
  谁都看不见文件名，也看不见大小（大小写在 AAD 里，AAD 不上线）。

### 1.4 托管源与限制

| 项 | 值 |
|---|---|
| 主用 | uguu.se，TTL 3 小时 |
| 兜底 | x0.at，TTL 72 小时 |
| 文件名 | 随机 `cs-<16 位>.bin`，不暴露原名 |
| 单文件上限 | 50MB（两端一致） |

### 1.5 同一份内容不传第二遍（已落地）

- 收端：同 sha256 沿用原来那一条，本地已缓存就不再下载。
- 发端：按「房间 topic + 名字 + mime + 大小 + sha」认出之前那条托管链接，
  链接还剩一分钟以上就直接复用；快到期的宁可重传。
- KMP 侧传输层已抽成 `FileTransport`（`shared/.../files/FileTransport.kt`），
  换源只动 `DefaultFileTransports`；Rust 侧是 `TransferClient`
  （`crates/clipsync-desktop/src/transfer.rs`），内部按凭据切托管源。

---

## 二、腾讯云 IMSDK 评估

### 2.1 官方事实

| 项 | 结论 | 出处 |
|---|---|---|
| IM 存不存文件 | 不存。发图片/语音/视频/文件要注册「上传插件」，文件传到腾讯云 COS；原生端 `createFileMessage` 自动上传，Web 端要 `tim-upload-plugin` | [Web SDK 文档](https://web.sdk.qcloud.com/im/doc/v3/zh-cn/SDK.html)、[发送消息](https://im.shengwang.cn/docs/document/react-native/message_send.html) |
| 上传用的凭据从哪来 | IM 服务端通过 SDK 私有协议下发一份 COS 临时密钥：`_getCosPreSigUrl` 返回 `secretId / secretKey / sessionToken / expiredTime / region / bucketName`，插件拿它往那个桶 POST。REST 接口列表里没有「上传文件」 | 拆 `@tencentcloud/chat@3.6.7` 的 `uploadByCOS` / `_uploadWithPreSigUrl`；[REST API 接口列表](https://cloud.tencent.com/document/product/269/1518) |
| 服务端发文件消息 | 只能自己填 `Url` + `UUID` + `Download_Flag`，字节要事先由客户端上传 | [Message Formats](https://intl.cloud.tencent.com/document/product/647/74355) |
| 单条消息长度 | 12K | [使用限制](https://cloud.tencent.com/document/product/269/9346) |
| 单文件大小 | SDK 最大 100MB（图片/语音 28MB） | 同上 |
| 群聊频率 | 每群 40 条/秒 | 同上 |
| 历史消息留存 | 体验版/专业版 7 天、旗舰版 30 天、企业版 90 天 | 同上 |
| 鉴权 | 必须 `SDKAppID + UserID + UserSig`；UserSig 是用应用 SecretKey 算的 HMAC-SHA256，官方要求**服务端签发**，客户端算仅限调试 | [生成 UserSig](https://cloud.tencent.com/document/product/269/32688)、[登录验证](https://trtc.io/zh/document/33517) |
| SDK 平台 | Android / iOS / Windows(C++·C#) / Web / 小程序 / uni-app / React Native / Flutter / HarmonyOS / Unity / UE —— **没有 KMP，没有 JVM 桌面客户端 SDK，没有 Rust SDK**（Java 只有服务端 REST 封装） | [SDK 列表](https://cloud.tencent.com/document/product/269/1498) |
| 计费 | 体验版免费（100 用户 / 100 峰值 DAU）、开发版免费但仅 1 个月、专业版 1499 元/月起（1 万 DAU） | [基础服务计费说明](https://cloud.tencent.com/document/buy-guide/269/81908) |

### 2.2 对 ClipSync 的四个硬伤

1. **桌面端直接出局**
   ClipSync 的主力是桌面（Compose 桌面 jvm 目标 + Rust 桌面），IM 没有这两个平台的客户端 SDK。
   只能自己打 IM 的 REST API，而 REST 要 admin UserSig / 应用密钥——放进客户端等于把密钥发出去。

2. **「shared 单源」会破**
   每个平台一套 SDK：Android 用 IM Android SDK、iOS 用 IM iOS SDK、web 要塞
   `@tencentcloud/chat` + 上传插件、桌面另想办法 → 变成四套 expect/actual，
   wasm 端还得背一个 JS SDK。

3. **凭空多一个后端**
   UserSig 必须服务端签；还要把「房间」映射成 IM 群（建群、加人、权限）。
   ClipSync 现在的模型是「房间码即一切，无账号无后端」。

4. **加密上没有增量**
   字节离开设备前已经 E2E 加密，IM 看到的仍是密文，它自己的传输加密对我们没用。
   IM 解决的是「稳定托管 + 长留存」，不是安全。

### 2.3 它确实能解决的真问题

- uguu 3 小时、x0.at 72 小时：链接过期对方就下不到了。
- 匿名文件站会单方面删文件、封 IP、限速。
- IM/COS 给的是 7 天级留存 + 国内带宽 + SLA。

### 2.4 顺带能拿到、但属于另一个决策的东西

离线推送、群组、在线状态花名册、消息漫游。这些是好东西，但它们是**产品级重写**
（后端 + 每平台 SDK + 计费模型），跟「文件上传下载」该分开决策，不要捆在一起做。

---

## 三、方案对比

| 方案 | 链接寿命 | 改动面 | 需要后端 | 成本 | 评价 |
|---|---|---|---|---|---|
| 现状（uguu + x0.at） | 3h / 72h | 无 | 无 | 免费 | 匿名站不可控，随时可能删/封 |
| **A. COS 预签名 URL** | 7 天以上，可配 | 只换 `FileTransport` 实现 | 要一个极简签名服务 | COS 按量（存储 + 下行流量），用量小接近免费 | **推荐** |
| B. 加更多托管源 | 各家自己定 | 换 `TransferClient` 里的源列表 | 无 | 免费 | 治标，托管源仍是匿名的 |
| C. 接入 IMSDK | 随套餐 | 传输层 + 每平台 SDK + 账号体系 | 要（UserSig + 群管理） | 套餐起步 1499 元/月 | 与产品形态不匹配，桌面端接不上 |
| D. 自建对象存储服务 | 自定 | 要写服务端 | 要 | 服务器 + 带宽 | 只有一个用户时不划算 |

---

## 四、推荐路线（方案 A）

传输层、加密、房间模型**一点不动**，只换文件托管。已有的 `FileTransport` 缝正好在这里。

### 4.1 后端只做一件事

签发 **预签名 PUT / GET URL**：

- 客户端向后端要一个「上传凭证」：随机 object key + 预签名 PUT URL + 过期时间。
- 客户端把**已经加密好的密文字节**直接 PUT 到 COS（不经过后端）。
- 后端同时给出对应的预签名 GET URL（或桶设为公共读 + 随机 key，语义与现在的
  「拿到链接就能下」一致）。

签名用 COS 对象请求的 `q-sign-algorithm=sha1`（HMAC-SHA1 两跳 + SHA1 十六进制串，
不是 TC3-HMAC-SHA256）。它有两种落法：

- **后端签发**（本节原方案）：客户端只拿签好的 URL，SecretKey 不落客户端。
- **客户端自签**（试过，已撤，见 4.5）：SecretKey 随包发布，省掉后端。

### 4.2 客户端改动

| 端 | 改动 |
|---|---|
| KMP 桌面 / Android / wasm | 在 commonMain 用现成 HTTP 客户端打 COS REST 接口，三端共用一份 Kotlin（不破单源） |
| Rust | `TransferClient` 对外接口不变，内部换成分支：配了 COS 就走 COS，否则还是 uguu → x0.at |

### 4.3 需要一起想清楚的三件事

1. **过期语义变了**
   现在是托管方强制的 TTL（3h/72h），`expiresAt` 是硬承诺；COS 没有原生 TTL，
   靠**桶的生命周期规则**定时删对象。`expiresAt` 变成「我们自己承诺的删除时间」，
   界面上的倒计时仍按它显示，但要保证生命周期规则配得比它更早。
2. **公开读还是预签名读**
   公开读 + 随机 key 最贴近现状（链接即凭证）；预签名读更严，但链接会过期，
   收端晚收到消息时可能已经下不到——那时得能从后端再要一次新的签名。
3. **桶的写入凭证**
   后端签发 PUT URL 时限制 key 前缀与有效期，凭证不落客户端；客户端自签则相反，
   SecretKey 一定在包里（wasm 包等于公开）。自签必须用**单独的子账号 + 只授权这个桶的
   数据读写**，配合桶的生命周期规则，丢了最坏也就是被人塞满这个桶。

### 4.4 迁移与回退

- 老的 `TransferClient` / `FileTransferClient` 保留一个版本，通过设置切换托管源。
- 新旧两种托管源的密文格式完全一致（AAD 不含托管方信息），同一间房可以混用，
  不需要协议升级。

### 4.5 试过：客户端自签 COS（已撤）

客户端自签这条在两仓都写完并跑通过（签名 + `?uploads` 分片上传 + Range 分片下载 +
101MB 真加密往返），后来决定不走，代码已全部删除，`DefaultFileTransports` 与
`TransferClient` 回到 uguu → x0.at。

留下来的结论，下次真要接 COS 时不必重做调研：

- 签名是对象请求的 `q-sign-algorithm=sha1`：HttpString → StringToSign →
  HMAC-SHA1 两跳出 SignKey，**不是** TC3-HMAC-SHA256；百分号编码要用大写十六进制。
- ≤分片大小走单次 PUT；超过才 `?uploads` 初始化 → `?partNumber&uploadId` 逐片 PUT →
  收 ETag 后 `POST ?uploadId` 合并，中途失败要 `DELETE` 中止。
- 下载按 Range 逐片取 206 拼接；服务端忽略 Range 直接回 200 时整包收下。
  5xx 与网络错重试，4xx 直接抛。
- 分片数上限 10000，100MB 一片意味着 COS 侧单文件约 976GB；真正的瓶颈在加密端
  「整段读进内存」，也就是 `FileLimits.MAX_FILE_BYTES`（当前 50MB）。
- 预签名 GET 把方法、路径、host 都签进链接，链接只能 GET 那一个对象。
- 有效期按本机时钟算（`q-sign-time`），设备时钟偏了就会提前或延后过期。
- COS 没有原生 TTL，`expiresAt` 只是我们自己的承诺，必须配桶的生命周期规则，
  且规则要比它更早。
- SecretKey 随包发布在 wasm 端等于公开；走这条路的前提是接受「自用包」这个范围。

---

## 五、IM 作为第二个上传通道（已评估，未接）

它的文件存储就是腾讯自己的 COS 桶，IM 只负责发一张临时 STS 通行证。
所以通道能不能用，不取决于我们会写多少 COS 代码，而取决于**谁能跑 IM 客户端 SDK 拿到那张通行证**。

| 目标 | 有 IM 客户端 SDK | 上传 | 下载 |
|---|---|---|---|
| Android | 有 | 能 | 能 |
| iOS | 有 | 能 | 能 |
| wasmJs | 有（`@tencentcloud/chat` + `tim-upload-plugin`） | 能，要穿 JS 互操作 | 能 |
| Compose Desktop（jvm） | 没有 | 不能 | 能 |
| Rust 桌面 | 没有 | 不能 | 能 |

下载侧没门槛：`downloadUrl` 就是一个 https 直链，任何端都能 GET。所以「移动端传、桌面端下」是成立的，
反过来不行。

接入的最短路径：

1. 在目标平台的 IM SDK 里登录（`SDKAppID + UserID + UserSig`），调一次上传拿到临时 STS 密钥。
2. 用这份密钥往 `bucketName`（`region`）做分片上传，会话 token 要走 `x-cos-security-token` 头，
   签名仍是 4.5 里那套 `q-sign-algorithm=sha1`。
3. 发文件消息时把直链、`UUID`、`Download_Flag` 填进消息体；收端拿 `downloadUrl` 直接 GET。
4. 选择位在 `DefaultFileTransports` / `TransferClient`：有 IM 登录态就用 IM 通道，否则回落 uguu/x0.at。

前置条件先说清楚：

- UserSig 官方要求服务端签发；客户端内置密钥等于把密钥发给所有人（任何拿到包的人都能伪造任意 userID）。
  一个人的自用包可以忍，对外分发不行。
- 每个平台一套 SDK，`shared` 的 `FileTransport` 要变成 expect/actual 或在各端各挂一个实现，
  「各端跑同一份 shared」这条会从构造保证退化成约定。
- IM 单文件上限 100MB，够用；但桌面（jvm）和 Rust 没有客户端 SDK，拿不到那张 STS 通行证。

这几项加起来是「接一次 SDK」的量级，不是改几行配置。要不要做，等需要「移动端传、桌面端下」这个场景真的出现再决定。

### 5.1 不用 SDK 的那一家：环信 / 声网 IM

同一家公司，它的文件消息有纯 REST 上传口，不需要客户端 SDK，所以五个目标端都能走同一条路径：

- 上传 `POST https://{host}/{app-id}/chatfiles`，`Authorization: Bearer {AppToken}`，multipart 字段 `file`。
- 下载 `GET .../chatfiles/{file_uuid}`，除了 Bearer 还要消息体里那份 `share-secret`——链接不是「拿到就能下」，
  和现在「直链即凭证」的语义不一样，收端要能拿到 token，或者由发端另想办法。
- 附件单文件 10MB、留存 7 天；免费版 1TB 存储 / 100GB 月流量（按 1 万 DAU 计），
  超限回 413，频率超限回 429。

也就是说：字节能传，但「匿名直链」这个前提在环信这边要重新设计。真接之前先拿 curl 验两件事——
9MB 应回 200、11MB 应回 413，以及返回的 `uri` 不带鉴权头能不能 GET 到。

---

## 参考

- [即时通信 IM 使用限制](https://cloud.tencent.com/document/product/269/9346)
- [生成 UserSig（用户鉴权）](https://cloud.tencent.com/document/product/269/32688)
- [SDK 列表](https://cloud.tencent.com/document/product/269/1498)
- [基础服务计费说明](https://cloud.tencent.com/document/buy-guide/269/81908)
- [Web SDK 上传插件](https://web.sdk.qcloud.com/im/doc/v3/zh-cn/SDK.html)
- [发送消息（附件消息）](https://im.shengwang.cn/docs/document/react-native/message_send.html)
- [环信 发送聊天室消息与附件上传/下载流程](http://doc.easemob.com/document/server-side/message_chatroom.html)
