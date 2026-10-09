package zhiqiu.app.cs.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val LightColors =
        lightColorScheme(
                primary = Color(0xFF0969DA),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFDDF4FF),
                onPrimaryContainer = Color(0xFF0A3060),
                onBackground = Color(0xFF1F2328),
                background = Color(0xFFF2F5F9),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF1F2328),
                surfaceVariant = Color(0xFFEEF1F4),
                onSurfaceVariant = Color(0xFF57606A),
                surfaceContainer = Color(0xFFF3F5F7),
                surfaceContainerHigh = Color(0xFFEAEEF2),
                outline = Color(0xFF6E7781),
                outlineVariant = Color(0xFFCBD5E1),
                error = Color(0xFFCF222E),
                onError = Color(0xFFFFFFFF),
                errorContainer = Color(0xFFFFEBE9),
                onErrorContainer = Color(0xFF86181D),
        )

internal val DarkColors =
        darkColorScheme(
                primary = Color(0xFF4493F8),
                onPrimary = Color(0xFF0D1117),
                primaryContainer = Color(0xFF143A66),
                onPrimaryContainer = Color(0xFFB6E3FF),
                onBackground = Color(0xFFE6EDF3),
                background = Color(0xFF0A0E14),
                surface = Color(0xFF161B22),
                onSurface = Color(0xFFE6EDF3),
                surfaceVariant = Color(0xFF1C2128),
                onSurfaceVariant = Color(0xFF9DA7B3),
                surfaceContainer = Color(0xFF161B22),
                surfaceContainerHigh = Color(0xFF1C2128),
                outline = Color(0xFF6E7681),
                outlineVariant = Color(0xFF3D444D),
                error = Color(0xFFF85149),
                onError = Color(0xFF0D1117),
                errorContainer = Color(0xFF490202),
                onErrorContainer = Color(0xFFFFB4AE),
        )

internal val AppShapes =
        Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(22.dp),
                extraLarge = RoundedCornerShape(28.dp),
        )

internal val SuccessGreen = Color(0xFF1A7F37)
internal val SuccessGreenDark = Color(0xFF3FB950)
internal val PinAmber = Color(0xFF9A6700)
internal val PinAmberDark = Color(0xFFD29922)

@Composable
internal fun glassBorder(): Color =
        if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.75f)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
            colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
            shapes = AppShapes,
            content = content,
    )
}
