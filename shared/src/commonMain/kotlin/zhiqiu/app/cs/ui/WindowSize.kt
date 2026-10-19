package zhiqiu.app.cs.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

enum class WindowWidthClass { Compact, Medium, Expanded }

data class WindowSize(val widthDp: Float) {
    val widthClass: WindowWidthClass
        get() =
                when {
                    widthDp < 600f -> WindowWidthClass.Compact
                    widthDp < 840f -> WindowWidthClass.Medium
                    else -> WindowWidthClass.Expanded
                }

    val isCompact: Boolean
        get() = widthClass == WindowWidthClass.Compact
}

val LocalWindowSize = staticCompositionLocalOf { WindowSize(Float.POSITIVE_INFINITY) }

@Composable
fun rememberWindowSize(): WindowSize {
    val info = LocalWindowInfo.current
    val density = LocalDensity.current
    val width = with(density) { info.containerSize.width.toDp() }
    return remember(info.containerSize, density) { WindowSize(width.value) }
}
