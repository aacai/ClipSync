package zhiqiu.app.cs.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
    val dark = isAppDarkTheme()
    val themeSpec = tween<Color>(M3.MEDIUM_3, easing = M3.STANDARD)
    val accent by
            animateColorAsState(
                    if (dark) Color(0xFF1B3B6B) else Color(0xFFB6DDFA),
                    themeSpec,
                    label = "glowAccent",
            )
    val accent2 by
            animateColorAsState(
                    if (dark) Color(0xFF3A1D5C) else Color(0xFFD8C7F7),
                    themeSpec,
                    label = "glowAccent2",
            )
    val accent3 by
            animateColorAsState(
                    if (dark) Color(0xFF0E3A3A) else Color(0xFFC6EBD8),
                    themeSpec,
                    label = "glowAccent3",
            )
    val base by animateColorAsState(MaterialTheme.colorScheme.background, themeSpec, label = "glowBase")
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
internal fun GlassPanel(
        modifier: Modifier = Modifier,
        shape: Shape = MaterialTheme.shapes.large,
        alpha: Float = if (isAppDarkTheme()) 0.6f else 0.7f,
        content: @Composable ColumnScope.() -> Unit,
) {
    val haze = LocalHazeState.current
    val surface = MaterialTheme.colorScheme.surface
    val style =
            HazeBlurStyle {
                    this.blurRadius(28.dp)
                    noiseFactor(0.035f)
                    backgroundColor(surface.copy(alpha = alpha * 0.55f))
                    colorEffects(listOf(HazeColorEffect.tint(surface.copy(alpha = alpha))))
                    fallbackColorEffect(HazeColorEffect.tint(surface.copy(alpha = (alpha + 0.35f).coerceAtMost(0.95f))))
            }
    val base = if (haze == null) modifier else modifier.hazeBlur(input = HazeInput.Sources(haze), style = style)
    Column(modifier = base.clip(shape).border(1.dp, glassBorder(), shape)) { content() }
}
