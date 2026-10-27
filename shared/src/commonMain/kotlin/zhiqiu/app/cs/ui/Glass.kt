package zhiqiu.app.cs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
        CompositionLocalProvider(LocalBackdrop provides backdrop        ) {
            content()
        }
    }
}

// ClipRow 行高光描边（ClipSync 原有辅助，HouseStudy 方案未含）。
internal fun rimHighlight(dark: Boolean) =
        Brush.verticalGradient(
                colorStops =
                        if (dark) {
                            arrayOf(
                                    0f to Color.White.copy(alpha = 0.20f),
                                    0.15f to Color.White.copy(alpha = 0.04f),
                                    1f to Color.White.copy(alpha = 0.0f),
                            )
                        } else {
                            arrayOf(
                                    0f to Color.White.copy(alpha = 0.65f),
                                    0.15f to Color.White.copy(alpha = 0.10f),
                                    1f to Color.White.copy(alpha = 0.0f),
                            )
                        },
                startY = 0f,
                endY = 240f,
        )

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
    /** 叠在 blur 上的雾面；对话框用近不透明色做磨砂。 */
    surface: Color = Color.White.copy(alpha = 0.05f),
    blurRadius: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = LocalBackdrop.current
    Box(
        if (backdrop != null) {
            modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = { blur(blurRadius.toPx()) },
                onDrawSurface = { drawRect(surface) },
            )
        } else {
            modifier
                .clip(shape)
                .background(surface)
                .border(1.dp, Color.White.copy(alpha = 0.09f), shape)
        }
    ) {
        content()
    }
}

/**
 * 同窗 Popup + GlassCard，才能采到 [LocalBackdrop]。
 * Material [androidx.compose.material3.AlertDialog] 走独立窗/实心 surface，只会看起来像纯色块。
 */
@Composable
internal fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val scrimW = with(density) { windowInfo.containerSize.width.toDp() }
    val scrimH = with(density) { windowInfo.containerSize.height.toDp() }
    val scrimInteraction = remember { MutableInteractionSource() }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismissRequest,
        properties =
            PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                clippingEnabled = false,
            ),
    ) {
        Box(
            modifier =
                Modifier.size(scrimW, scrimH)
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable(
                        interactionSource = scrimInteraction,
                        indication = null,
                        onClick = onDismissRequest,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            GlassCard(
                modifier =
                    modifier
                        .widthIn(min = 280.dp, max = 520.dp)
                        .padding(horizontal = 24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                shape = GlassDialogShape,
                // 近不透明 + 强 blur：磨砂面板，不当透层
                surface = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                blurRadius = 28.dp,
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
                    if (title != null) {
                        Box(Modifier.padding(bottom = if (text != null) 12.dp else 0.dp)) {
                            title()
                        }
                    }
                    if (text != null) {
                        Box(Modifier.padding(bottom = 8.dp)) { text() }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}
