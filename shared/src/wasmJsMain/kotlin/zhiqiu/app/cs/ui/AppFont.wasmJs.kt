package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.Font
import zhiqiu.app.cs.resources.Res
import zhiqiu.app.cs.resources.noto_sans_sc

@Composable
internal actual fun appFontFamily(): FontFamily = FontFamily(Font(Res.font.noto_sans_sc))
