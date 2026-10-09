# ClipSync

跨平台剪贴板同步工具：桌面（macOS / Windows / Linux）、Android、Web（及实验性 iOS）之间同步文本与文件剪贴板。
Kotlin Multiplatform + Compose Multiplatform 实现，MQTT + 端到端加密。

## 特性

- **房间制**：随机 6 位房间码（可自定义）+ 可选密码，加入即同步
- **端到端加密**：AES-256-GCM，密钥由 `PBKDF2-SHA256(210k 迭代)` 从房间码+密码派生；Broker 只能见到密文
- **安全码**：双方界面显示同一组 6 组数字（Safety Number），可口头核对中间人攻击
- **文本剪贴板**：收到即写入本机剪贴板；本机复制自动广播（带自回环抑制）
- **文件剪贴板**（≤50MB）：本地加密后上传到临时托管，只广播加密元信息；接收端下载→sha256 校验→解密→落缓存
- **历史列表**（CopyQ 风格）：搜索 / 置顶 / 复制 / 下载 / 打开 / 删除 / 清空，文本与文件混排
- **平台剪贴板桥**：桌面 AWT、Android 系统剪贴板、Web/iOS 为可编译桩

## 架构速览

```
shared/src/
  commonMain/
    core/     ClipProtocol（主题+信封）、RoomCrypto（KDF+AES-GCM）、ClipSyncEngine（MQTT 引擎）
    files/    FileCrypto、FileTransferClient（litterbox 上传/下载）、ClipRepository（历史+缓存）
    ui/       AppModel（编排）、PlatformUi（平台能力注入）
  jvmMain/    AWT 剪贴板/文件对话框、桌面历史持久化 ~/.clipsync
  androidMain/SAF 文件选择、FileProvider 打开、应用私有目录持久化
  wasmJsMain/ 会话内历史（剪贴板为桩）
  iosMain/    UIPasteboard、WSS 传输（实验性）
```

- MQTT 主题：`cs/v1/{roomHash8}/clip|presence/{devId}`，`roomHash = sha256(房间码)[0:8]`
- 信封 `ClipEnvelope{kind=text|file, ct}`：`ct` 为 base64(IV‖密文‖tag)，AAD 绑定主题
- 文件元信息（文件名/大小/sha256/托管链接/过期）同样加密后放进 `ct`，托管方只见随机文件名的密文

## 快速开始

### 1. 配置 Broker（EMQX Serverless 示例）

`local.properties`（已被 gitignore）：

```properties
mqtt.host=<your-broker>.ala.cn-shenzhen.emqxsl.cn
mqtt.scheme=ssl
mqtt.portTls=8883
mqtt.portWss=8084
mqtt.username=<MQTT 用户名>
mqtt.password=<MQTT 密码>
```

> EMQX 控制台首页的 “App ID / App Secret” 是 **API 凭据，不是 MQTT 登录**。
> MQTT 账号在：**部署 → 访问控制 → 客户端认证 → 添加**。

配置会在 Gradle 配置期生成 `MqttSecrets.kt`（gitignore，勿手改）。

### 2. 运行

```bash
./gradlew :desktopApp:run          # 桌面
./gradlew :androidApp:assembleDebug
./gradlew :webApp:wasmJsBrowserDevelopmentRun
```

## 测试

```bash
./gradlew :shared:jvmTest          # 单测：加解密黄金向量、文件校验、历史仓库等（32 项）
./gradlew :desktopApp:smoke -Proom=ROOM1      # 文本端到端（需可达 broker）
./gradlew :desktopApp:fileSmoke -Proom=ROOM2  # 文件端到端（需可达 broker + 托管站点）
```

黄金向量由 WebCrypto（Node）生成，保证桌面/Android 与 Web 派生出逐字节一致的房间密钥。

## 安全边界

- Broker 运行在他人服务器上：客户端凭据可被提取，因此**通信内容必须端到端加密**（本项目已内置）
- 房间码 + 密码共同决定密钥；密码可显著提高对“猜房间码”的抵抗力
- 文件先加密再上传，托管方拿到的是密文；上传文件名为随机 `cs-<16>.bin`，不含原文件名
- 托管链接 72 小时过期；文件名经过净化（防路径穿越 / Windows 保留名）

## 已知限制

- 文件 >50MB 暂不支持
- Web 端历史仅会话内存、剪贴板读写为桩；iOS 为实验性（WSS 传输）
- MQTT 单条消息上限受 Broker 限制（文件本体不走 MQTT）

## License

待选择（开源前请添加 LICENSE 文件，例如 MIT 或 Apache-2.0）。
