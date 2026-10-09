package zhiqiu.app.cs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

val LocalHazeState: ProvidableCompositionLocal<HazeState?> = compositionLocalOf { null }

/** 背景光晕：毛玻璃层透出来的就是它，所以必须放在 hazeSource 之下。 */
@Composable
fun BackdropGlow(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val accent = if (dark) Color(0xFF1B3B6B) else Color(0xFFB6DDFA)
    val accent2 = if (dark) Color(0xFF3A1D5C) else Color(0xFFD8C7F7)
    val accent3 = if (dark) Color(0xFF0E3A3A) else Color(0xFFC6EBD8)
    val base = MaterialTheme.colorScheme.background
    Box(
            modifier =
                    modifier
                            .fillMaxSize()
                            .background(base)
                            .background(
                                    Brush.linearGradient(
                                            listOf(base.copy(alpha = 0.92f), accent2.copy(alpha = 0.55f)),
                                            start = Offset.Zero,
                                            end = Offset.Infinite,
                                    )
                            ),
    ) {
        Box(
                modifier =
                        Modifier.align(Alignment.TopStart)
                                .fillMaxSize(0.85f)
                                .background(
                                        Brush.radialGradient(
                                                listOf(accent.copy(alpha = if (dark) 0.85f else 0.7f), Color.Transparent)
                                        )
                                ),
        )
        Box(
                modifier =
                        Modifier.align(Alignment.BottomEnd)
                                .fillMaxSize(0.75f)
                                .background(
                                        Brush.radialGradient(
                                                listOf(accent3.copy(alpha = if (dark) 0.8f else 0.65f), Color.Transparent)
                                        )
                                ),
        )
    }
}

@Composable
fun glassStyle(
        blurRadius: Dp = 28.dp,
        surfaceAlpha: Float = if (isSystemInDarkTheme()) 0.5f else 0.62f,
): HazeBlurStyle {
    val surface = MaterialTheme.colorScheme.surface
    return HazeBlurStyle {
        this.blurRadius(blurRadius)
        noiseFactor(0.035f)
        backgroundColor(surface.copy(alpha = surfaceAlpha * 0.55f))
        colorEffects(listOf(HazeColorEffect.tint(surface.copy(alpha = surfaceAlpha))))
        fallbackColorEffect(HazeColorEffect.tint(surface.copy(alpha = (surfaceAlpha + 0.35f).coerceAtMost(0.95f))))
    }
}

/** 浮动玻璃面板：从最近的 hazeSource 里取像素做真模糊。 */
@Composable
fun Modifier.glassSurface(
        shape: Shape,
        blurRadius: Dp = 28.dp,
        surfaceAlpha: Float = if (isSystemInDarkTheme()) 0.5f else 0.62f,
        border: Color? = null,
): Modifier {
    val state = LocalHazeState.current
    val base = if (state == null) this else hazeBlur(input = HazeInput.Sources(state), style = glassStyle(blurRadius, surfaceAlpha))
    return base.clip(shape)
}

@Composable
internal fun GlassPanel(
        modifier: Modifier = Modifier,
        shape: Shape = MaterialTheme.shapes.large,
        alpha: Float = if (isSystemInDarkTheme()) 0.6f else 0.7f,
        content: @Composable ColumnScope.() -> Unit,
) {
    Column(
            modifier =
                    modifier
                            .glassSurface(shape = shape, surfaceAlpha = alpha)
                            .border(1.dp, glassBorder(), shape),
    ) {
        content()
    }
}
