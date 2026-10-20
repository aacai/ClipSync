package zhiqiu.app.cs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.effects.blur

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val backdrop = rememberLayerBackdrop()
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(Palette.Background)
        ) {
            BackdropBlob(Color(0xFF2563EB), 360.dp, (-100).dp, (-80).dp)
            BackdropBlob(Color(0xFF7C3AED), 320.dp, 220.dp, 20.dp)
            BackdropBlob(Color(0xFF0EA5E9), 280.dp, 20.dp, 520.dp)
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            content()
        }
    }
}

@Composable
private fun BackdropBlob(color: Color, size: Dp, x: Dp, y: Dp) {
    Box(
        Modifier
            .offset(x = x, y = y)
            .size(size)
            .background(color.copy(alpha = 0.35f), CircleShape)
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = LocalBackdrop.current
    Box(
        if (backdrop != null) {
            modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = { blur(20.dp.toPx()) },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = 0.05f))
                }
            )
        } else {
            modifier
                .clip(shape)
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.09f), shape)
        }
    ) {
        content()
    }
}
