# ClipSync

跨平台剪贴板同步工具：桌面（macOS / Windows / Linux）、Android、Web（及实验性 iOS）之间同步文本与文件剪贴板。
Kotlin Multiplatform + Compose Multiplatform 实现，MQTT + 端到端加密。

## 特性

- **房间制**：随机 6 位房间码（可自定义）+ 可选密码，加入即同步
- **端到端加密**：AES-256-GCM，密钥由 `PBKDF2-SHA256(210k 迭代)` 从房间码+密码派生；Broker 只能见到密文
- **安全码**：双方界面显示同一组 6 组数字（Safety Number），可口头核对中间人攻击
- **文本剪贴板**：收到即写入本机剪贴板；本机复制自动广播（带自回环抑制）
- **文件剪贴板**（≤50MB）：本地加密后上传到临时托管，只广播加密元信息；接收端下载→sha256 校验→解密→落缓存
- **历史列表**（CopyQ 风格）：搜索（支持正则 / 区分大小写）/ 置顶 / 复制 / 下载 / 打开 / 删除 / 清空，文本与文件混排
- **条目编辑**：F2 改文本、备注（笔记）、Ctrl+Shift+↑↓ 重排、Ctrl+D 副本、详情、12 种文本变换（去空白 / 排序 / 去重 / 大小写 / 拼接 / URL / Base64）——作用于文本条目
- **多选批量**：Ctrl+A 全选、行首勾选、Shift+↑↓ 扩展；批量复制 / 置顶 / 导出 JSON / 删除
- **删除可撤销**：Ctrl+Z 或工具栏撤销恢复整批，并自动重下刚删掉的附件；编辑历史不会被撤销覆盖
- **键盘操作**：↑↓ 光标、Shift+↑↓ 扩展多选、Ctrl+Shift+↑↓ 重排、Enter 复制/打开、F2 编辑、Del 删除、Home/End 首尾、Esc 退出多选/清空搜索；Ctrl/Cmd 组合键 F 搜索、C 复制、N 新建、A 全选、Z 撤销、D 副本
- **毛玻璃界面**：Haze 2 实时背景模糊（顶部工具条 + 底部多选条浮在列表上），自适应明暗主题
- **应用内设置**：主题（跟随系统 / 浅色 / 深色）+ 语言（跟随系统 / 简体中文 / English），用 multiplatform-settings 落在各平台自己的存储里
- **多语言界面**：文案走 Compose Resources（中/英，149 条 key 对齐），语言切换在桌面/网页即时生效，Android 走系统 per-app locale（13+），iOS 下次启动生效
- **平台剪贴板桥**：桌面 AWT（文件走 CF_HDROP / file URL，图片走位图）、Android（FileProvider content URI）、Web（navigator.clipboard + ClipboardItem）；iOS 仅文本

## 架构速览

```
shared/src/
  commonMain/
    core/     ClipProtocol（主题+信封）、RoomCrypto（KDF+AES-GCM）、ClipSyncEngine（MQTT 引擎）、AppSettings（主题/语言）
    files/    FileCrypto、FileTransferClient（litterbox 上传/下载）、ClipRepository（历史+缓存）
    ui/       AppModel（编排）、PlatformUi（平台能力注入）
  jvmMain/    AWT 剪贴板/文件对话框、桌面历史持久化 ~/.clipsync
  androidMain/SAF 文件选择、FileProvider 打开、应用私有目录持久化
  wasmJsMain/ localStorage 历史、navigator.clipboard 读写
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
./gradlew :shared:jvmTest          # 单测：加解密黄金向量、文件校验、历史仓库、文本变换（45 项）
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
- Web 端浏览器不允许后台读剪贴板：需要用户手势 + `clipboard-read` 授权，因此发送靠“发送剪贴板”或“新建”手动输入；沙箱内没有本地文件，文件条目只能收不能落盘
- iOS 为实验性（WSS 传输，仅文本剪贴板，历史不落盘）
- MQTT 单条消息上限受 Broker 限制（文件本体不走 MQTT）

## License

待选择（开源前请添加 LICENSE 文件，例如 MIT 或 Apache-2.0）。
