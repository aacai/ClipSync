# Compose Multiplatform 液态玻璃实现指南

本文基于本项目（HouseStudy）的落地经验，用官方 **Kyant Backdrop** 实现可切换的「液态玻璃 / 磨砂玻璃」。

## 推荐库

| 库 | 坐标 | 说明 |
|---|---|---|
| **Kyant Backdrop（推荐）** | `io.github.kyant0:backdrop:2.0.1` | 正宗液态玻璃：backdrop 采样 + `lens` 折射 |
| 不要用 | `io.github.kashif-mehmood-km:backdrop` | 旧 fork，和 Compose 1.12 / Skiko 0.150 的 RuntimeShader 签名冲突，`Highlight.Default` 会 `NoSuchMethodError` |

- GitHub：https://github.com/Kyant0/AndroidLiquidGlass
- 文档：https://kyant.gitbook.io/backdrop
- 官方液态配方教程：https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar

### 依赖

```kotlin
// gradle/libs.versions.toml 或 build.gradle.kts
implementation("io.github.kyant0:backdrop:2.0.1")
```

Release / Desktop ProGuard：

```proguard
-keep class com.kyant.backdrop.** { *; }
-dontwarn com.kyant.backdrop.**
```

## 核心概念（两层）

液态玻璃必须「有东西可透」：

1. **Backdrop 层**：被采样的背景（色块、渐变、图片、滚动内容）
2. **Glass 层**：用 `drawBackdrop` / `drawPlainBackdrop` 采样背景并套效果

没有正确接入 backdrop 的玻璃，看起来只会是半透明矩形。

```
┌─────────────────────────────┐
│  AppBackground              │
│  ┌───────────────────────┐  │
│  │ layerBackdrop(...)    │  │  ← 捕获背景（含底色 + blob）
│  │ 背景色 / 色块 / 内容   │  │
│  └───────────────────────┘  │
│  ┌───────────────────────┐  │
│  │ GlassCard             │  │  ← 采样 + blur / lens
│  │ 卡片 / 底栏 / 弹层    │  │
│  └───────────────────────┘  │
└─────────────────────────────┘
```

## 最小可运行示例

### 1. 背景：创建并挂上 LayerBackdrop

```kotlin
@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    // 背景色也要画进 backdrop，否则玻璃边缘会采到透明空洞
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor) // 你的页面底色
        drawContent()
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(backgroundColor)
        ) {
            // 可选：放色块 / 图片，让折射更明显
            // BackdropBlob(...)
        }

        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            content()
        }
    }
}
```

### 2. 卡片：液态 vs 磨砂

```kotlin
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = LocalBackdrop.current
    val style = LocalGlassStyle.current

    Box(
        when {
            backdrop != null && style == GlassStyle.Liquid -> {
                // 官方液态配方：vibrancy → blur → lens
                modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx())
                        lens(
                            refractionHeight = 16.dp.toPx(),
                            refractionAmount = 32.dp.toPx(),
                            depthEffect = true
                        )
                    },
                    highlight = { Highlight.Default },
                    onDrawSurface = {
                        // 深色主题建议 0.08~0.12；浅色主题可到 0.4~0.5
                        drawRect(Color.White.copy(alpha = 0.10f))
                    }
                )
            }
            backdrop != null -> {
                // 磨砂：只要模糊，不要 lens / Highlight
                modifier.drawPlainBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = { blur(20.dp.toPx()) },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 0.05f))
                    }
                )
            }
            else -> {
                // 无 backdrop 时的降级（预览 / Web 等）
                modifier
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.09f), shape)
            }
        }
    ) {
        content()
    }
}
```

### 3. 根节点注入样式

```kotlin
var glassStyle by remember { mutableStateOf(GlassStyle.Frosted) }

CompositionLocalProvider(LocalGlassStyle provides glassStyle) {
    AppBackground {
        // 所有 GlassCard（含底栏）都会跟随切换
        Column(Modifier.fillMaxSize()) {
            /* 页面内容 */
            BottomBar() // 内部同样用 GlassCard
        }
    }
}
```

底栏用同一套 `GlassCard`，并打开胶囊配方：

```kotlin
GlassCard(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(50), // 胶囊；lens 需要 CornerBasedShape
    pill = true                     // tab bar：lens(24.dp, 24.dp)
) {
    Row(/* 导航项 */)
}
```

底栏附近的 backdrop 里最好有色块/内容，否则胶囊折射不明显。注意：不要把带 `drawBackdrop` 的玻璃节点再塞进同一个 `layerBackdrop` 子树里采样自己，桌面端 Skia 可能直接 SIGILL。

## 效果配方速查

效果按声明顺序链式作用，推荐顺序：

```text
backdrop 像素
  → vibrancy / colorControls   （先调色）
  → blur                       （再软化）
  → lens                       （最后边缘折射）
  → onDrawSurface              （可读性遮罩）
```

| 目标 | 推荐参数 |
|---|---|
| 液态玻璃（卡片 / 底栏） | `vibrancy()` + `blur(4.dp)` + `lens(16.dp, 32.dp, depthEffect = true)` + `Highlight.Default` |
| 磨砂玻璃 | 仅 `blur(16~20.dp)`，用 `drawPlainBackdrop` |
| 更「厚」的对话框感 | `lens(24.dp, 48.dp, depthEffect = true)` + 略大 blur |
| 胶囊按钮 | `lens(12.dp, 24.dp)`，blur 保持很小 |

`lens` 只支持 `CornerBasedShape` / `AbsoluteRoundedCornerShape` / Kyant 的 `RoundedRectangularShape`。任意自定义 Shape 会抛异常。

## 设置里切换（可选）

本项目在「关于」页提供开关，并持久化：

```kotlin
enum class GlassStyle {
    Frosted, Liquid;

    fun stored(): String = if (this == Liquid) "liquid" else "frosted"

    companion object {
        fun fromStored(value: String): GlassStyle =
            if (value == "liquid") Liquid else Frosted
    }
}
```

用 `multiplatform-settings` 或任意 KV 存 `"liquid"` / `"frosted"` 即可。

## 常见坑

1. **玻璃不透 / 发黑**  
   背景没有进 backdrop。用 `rememberLayerBackdrop { drawRect(bg); drawContent() }`，并给背景 `Modifier.layerBackdrop(backdrop)`。

2. **选液态就崩溃 `NoSuchMethodError: RuntimeShaderBuilder.makeShader$default`**  
   多半是旧 fork。换成 `io.github.kyant0:backdrop:2.0.1`。

3. **底栏没有折射感**  
   - 确认底栏也在 `AppBackground` + `LocalGlassStyle` 作用域内  
   - 确认用的是 `GlassCard` / `drawBackdrop`，不是普通 `background`  
   - `shape` 必须是圆角 Shape（如 `RoundedCornerShape(50)`）  
   - 背景要有可折射的色块/内容，纯色底折射不明显  

4. **可读性差**  
   用 `onDrawSurface { drawRect(Color.White.copy(alpha = …)) }` 平衡通透与文字对比度。深色 UI alpha 宜低，浅色 UI alpha 宜高。

5. **滚动列表上的粘性玻璃**  
   官方建议：滚动场景只用 `blur`（± `colorControls`），不要每帧跑 `vibrancy` + `lens`，性能开销大。

## 本项目对应文件

| 文件 | 作用 |
|---|---|
| `shared/.../ui/Glass.kt` | `AppBackground` / `GlassCard` / 样式枚举 |
| `shared/.../App.kt` | 注入 `LocalGlassStyle`，底栏用 `GlassCard` |
| `shared/.../ui/AboutScreen.kt` | 磨砂 / 液态切换 UI |
| `shared/.../storage/AppSettings.kt` | 持久化 `app_glass` |
| `gradle/libs.versions.toml` | `io.github.kyant0:backdrop:2.0.1` |

## 参考链接

- Kyant Backdrop 文档：https://kyant.gitbook.io/backdrop
- Glass Bottom Bar 教程：https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar
- 仓库：https://github.com/Kyant0/AndroidLiquidGlass
- 备选（若只要玻璃材质、不绑 Kyant）：[chrisbanes/haze](https://github.com/chrisbanes/haze) 的 `haze-glass`、[fletchmckee/liquid](https://github.com/fletchmckee/liquid)
