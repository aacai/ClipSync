package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.Font
import zhiqiu.app.cs.core.platformOs
import zhiqiu.app.cs.resources.Res
import zhiqiu.app.cs.resources.noto_sans_sc

@Composable
internal actual fun appFontFamily(): FontFamily {
    // Windows 上 Compose 默认字体不带中文回退，中文会渲染成方块，改用内置的 Noto Sans SC；
    // macOS / Linux 的系统字体本身含中文，沿用默认即可。
    return if (platformOs() == "Windows") {
        FontFamily(Font(Res.font.noto_sans_sc))
    } else {
        FontFamily.Default
    }
}
