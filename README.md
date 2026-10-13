# ClipSync

跨平台剪贴板同步：桌面（macOS / Windows / Linux）、Android、Web 之间同步文本与文件剪贴板。
Kotlin Multiplatform + Compose Multiplatform，MQTT 传输 + 端到端加密。

在线版 → <https://aacai.github.io/ClipSync/> · 下载 → <https://github.com/aacai/ClipSync/releases>

## 特性

**同步**
- 房间制：6 位房间码（可自定义）+ 可选密码，加入即同步
- 端到端加密：AES-256-GCM，密钥由 Argon2id 从房间码 + 密码派生，Broker 只见密文
- 安全码：双方显示同一组 Safety Number，可口头核对中间人；同屋设备指纹不符直接弹警告
- 断线自愈：自动重连（1s→30s 退避）+ 重连后补发 presence，界面状态跟真实连接走
- 记住房间：重开 App 自动回到上次的房间，落盘的是派生密钥而非明文密码
- 文本剪贴板：收到即写入本机，本机复制自动广播（带自回环抑制）
- 文件剪贴板（≤50MB）：本地加密后上传临时托管，只广播加密元信息；接收端下载 → 校验 → 解密

**历史**
- 搜索（正则 / 区分大小写）、置顶、复制、下载、打开、删除、清空，文本与文件混排
- F2 编辑、备注、重排与移动到指定位置、副本、详情、12 种文本变换
- 多选批量：复制 / 置顶 / 导出 JSON / 删除
- 删除可撤销（Ctrl+Z 恢复整批，附件自动重下）
- 全键盘操作：方向键 / Enter / F2 / Del / Home·End / Esc 与一组 Ctrl 组合键

**界面**
- 毛玻璃：Haze 2 实时背景模糊 + 自适应明暗主题 + 统一 M3 动效
- 纯图标按钮一律带悬停说明，网页端同样可用
- 中 / 英双语，跟随系统；网页端自带 subset 后的中文字体
- 独立设置页：主题、语言、房间与剪贴板开关

**平台**
- Android 后台常驻（可选前台服务 + 静默常驻通知），切后台继续收内容
- 原生剪贴板桥：桌面 AWT、Android FileProvider、Web ClipboardItem，支持文件与图片
- iOS 实验性（WSS，仅文本）

## 开发

```bash
./gradlew :desktopApp:run              # 桌面
./gradlew :androidApp:assembleDebug    # Android
./gradlew :webApp:wasmJsBrowserDevelopmentRun
./gradlew :shared:jvmTest              # 单测：加解密黄金向量、文件校验、历史仓库、文本变换
```

推 `v*` tag 触发 `.github/workflows/build.yml`：签名 APK、macOS dmg、Windows msi、Linux deb（x64 / arm64）、`web.zip`，汇总成一个 GitHub Release。

Broker 地址与登录凭据在 `shared/src/commonMain/.../core/MqttSecrets.kt`，用公共 EMQX 实例即可，换成自己的 broker 就改这一个文件——内容是端到端加密的，Broker 侧看不到明文。

**macOS 首次打开**：没有开发者签名与公证，Gatekeeper 会拦，拖进「应用程序」后执行
`xattr -dr com.apple.quarantine "/Applications/ClipSync.app"`。
