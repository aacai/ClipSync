package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/** wasm 上 Skia 拿不到系统字体，中文字形要靠自带字体；其余平台交给系统。 */
@Composable internal expect fun appFontFamily(): FontFamily
