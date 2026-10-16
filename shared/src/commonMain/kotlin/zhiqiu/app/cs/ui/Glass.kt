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

/** 背景光晕：毛玻璃层透出来的就是它，所以必须放在 hazeSource 之下。
 *
 *  只用同一色系的两束光——顶部主光源压住视线起点，底部一束给纵深，
 *  不再拿三种高饱和色互相叠加，避免脏、花。 */
@Composable
fun BackdropGlow(modifier: Modifier = Modifier) {
    val dark = isAppDarkTheme()
    val themeSpec = tween<Color>(M3.MEDIUM_3, easing = M3.STANDARD)
    val glowTop by
            animateColorAsState(
                    if (dark) Color(0xFF1A3D66) else Color(0xFFBFD9F5),
                    themeSpec,
                    label = "glowTop",
            )
    val glowBottom by
            animateColorAsState(
                    if (dark) Color(0xFF1D1A38) else Color(0xFFDDE5F2),
                    themeSpec,
                    label = "glowBottom",
            )
    val base by animateColorAsState(MaterialTheme.colorScheme.background, themeSpec, label = "glowBase")
    Box(
            modifier =
                    modifier
                            .fillMaxSize()
                            .background(base)
                            .background(
                                    Brush.verticalGradient(
                                            0f to glowTop.copy(alpha = if (dark) 0.55f else 0.45f),
                                            0.5f to Color.Transparent,
                                            1f to glowBottom.copy(alpha = if (dark) 0.45f else 0.35f),
                                    )
                            ),
    ) {
        Box(
                modifier =
                        Modifier.align(Alignment.TopStart)
                                .fillMaxSize(0.9f)
                                .background(
                                        Brush.radialGradient(
                                                listOf(
                                                        glowTop.copy(alpha = if (dark) 0.5f else 0.38f),
                                                        Color.Transparent,
                                                )
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
