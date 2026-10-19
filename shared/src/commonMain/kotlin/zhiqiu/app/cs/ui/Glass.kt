package zhiqiu.app.cs.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import kotlin.math.PI
import kotlin.math.sin

val LocalHazeState: ProvidableCompositionLocal<HazeState?> = compositionLocalOf { null }

@Composable
fun BackdropGlow(modifier: Modifier = Modifier) {
    val dark = isAppDarkTheme()
    val base = MaterialTheme.colorScheme.background
    val drift by
            rememberInfiniteTransition(label = "drift")
                    .animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec =
                                    infiniteRepeatable(tween(36000, easing = LinearEasing), RepeatMode.Restart),
                            label = "drift",
                    )
    val wave = sin(drift * 2 * PI).toFloat()
    Canvas(modifier = modifier.fillMaxSize().background(base)) {
        fun blob(color: Color, cx: Float, cy: Float, radius: Float, alpha: Float, dx: Float, dy: Float) {
            val center = Offset(cx + dx, cy + dy)
            drawCircle(
                    brush =
                            Brush.radialGradient(
                                    listOf(color.copy(alpha = alpha), Color.Transparent),
                                    center = center,
                                    radius = radius,
                            ),
                    radius = radius,
                    center = center,
            )
        }
        val w = size.width
        val h = size.height
        blob(
                AuroraSakura,
                w * 0.14f,
                h * 0.02f,
                w * 0.8f,
                if (dark) 0.5f else 0.32f,
                wave * w * 0.025f,
                wave * h * 0.02f,
        )
        blob(
                AuroraPeach,
                w * 0.9f,
                h * 0.24f,
                w * 0.6f,
                if (dark) 0.4f else 0.26f,
                -wave * w * 0.02f,
                wave * h * 0.012f,
        )
        blob(
                AuroraWisteria,
                w * 0.45f,
                h * 1.02f,
                w * 0.85f,
                if (dark) 0.36f else 0.22f,
                wave * w * 0.018f,
                -wave * h * 0.015f,
        )
    }
}

@Composable
internal fun GlassPanel(
        modifier: Modifier = Modifier,
        cornerRadius: Dp = 26.dp,
        alpha: Float = if (isAppDarkTheme()) 0.6f else 0.7f,
        content: @Composable ColumnScope.() -> Unit,
) {
    val haze = LocalHazeState.current
    val surface = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(cornerRadius)
    val style =
            HazeBlurStyle {
                    blurRadius(28.dp)
                    noiseFactor(0.035f)
                    fallbackColorEffect(
                            HazeColorEffect.tint(surface.copy(alpha = (alpha + 0.35f).coerceAtMost(0.95f)))
                    )
            }
    val surfaceLayer = modifier
    Box(
            modifier =
                    if (haze == null) surfaceLayer
                    else surfaceLayer.hazeBlur(input = HazeInput.Sources(haze), style = style).clip(shape),
    ) {
        Column(
                modifier =
                        Modifier.background(surface.copy(alpha = alpha), shape)
                                .border(1.dp, glassBorder(), shape),
        ) {
            content()
        }
    }
}

internal fun rimHighlight(dark: Boolean): Brush =
        Brush.horizontalGradient(
                listOf(
                        Color.White.copy(alpha = if (dark) 0.45f else 0.85f),
                        Color.White.copy(alpha = if (dark) 0.07f else 0.22f),
                        Color.White.copy(alpha = if (dark) 0.05f else 0.18f),
                        Color.White.copy(alpha = if (dark) 0.25f else 0.6f),
                )
        )
