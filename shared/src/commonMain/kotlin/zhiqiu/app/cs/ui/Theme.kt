package zhiqiu.app.cs.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zhiqiu.app.cs.core.ThemeMode

internal val LightColors =
        lightColorScheme(
                primary = Color(0xFFC0426B),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFFBDCE4),
                onPrimaryContainer = Color(0xFF6E1235),
                secondary = Color(0xFF7C9A82),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFE3EFE5),
                onSecondaryContainer = Color(0xFF2C4A34),
                onBackground = Color(0xFF241A1E),
                background = Color(0xFFFBF3F5),
                surface = Color(0xFFFFFBFB),
                onSurface = Color(0xFF241A1E),
                surfaceVariant = Color(0xFFF4E7EB),
                onSurfaceVariant = Color(0xFF6B5560),
                surfaceContainer = Color(0xFFF9EDF0),
                surfaceContainerHigh = Color(0xFFF3E2E8),
                outline = Color(0xFFA08794),
                outlineVariant = Color(0xFFE7D5DD),
                error = Color(0xFFC2453F),
                onError = Color(0xFFFFFFFF),
                errorContainer = Color(0xFFFCE9E7),
                onErrorContainer = Color(0xFF7A1B18),
                inverseSurface = Color(0xFF2A1F24),
                inverseOnSurface = Color(0xFFFBEEF1),
        )

internal val DarkColors =
        darkColorScheme(
                primary = Color(0xFFF4A8C0),
                onPrimary = Color(0xFF3D0F20),
                primaryContainer = Color(0xFF6B2440),
                onPrimaryContainer = Color(0xFFFFD9E4),
                secondary = Color(0xFFA3C7AC),
                onSecondary = Color(0xFF16301C),
                secondaryContainer = Color(0xFF35503A),
                onSecondaryContainer = Color(0xFFD5EBD9),
                onBackground = Color(0xFFF0E4E9),
                background = Color(0xFF100A0E),
                surface = Color(0xFF1A1218),
                onSurface = Color(0xFFF0E4E9),
                surfaceVariant = Color(0xFF261B21),
                onSurfaceVariant = Color(0xFFBFA5B0),
                surfaceContainer = Color(0xFF1E151B),
                surfaceContainerHigh = Color(0xFF2A1D25),
                outline = Color(0xFF7A5F6B),
                outlineVariant = Color(0xFF3A2A33),
                error = Color(0xFFF87171),
                onError = Color(0xFF450A0A),
                errorContainer = Color(0xFF4C1D1D),
                onErrorContainer = Color(0xFFFFC9C9),
                inverseSurface = Color(0xFFF0E4E9),
                inverseOnSurface = Color(0xFF1A1218),
        )

internal val AppShapes =
        Shapes(
                extraSmall = RoundedCornerShape(10.dp),
                small = RoundedCornerShape(14.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(26.dp),
                extraLarge = RoundedCornerShape(34.dp),
        )

internal val SuccessGreen = Color(0xFF15803D)
internal val SuccessGreenDark = Color(0xFF4ADE80)
internal val PinAmber = Color(0xFFB45309)
internal val PinAmberDark = Color(0xFFFBBF24)

internal val AuroraSakura = Color(0xFFF472B6)
internal val AuroraPeach = Color(0xFFFBA68C)
internal val AuroraWisteria = Color(0xFFB79BE8)

internal val LocalThemeMode = staticCompositionLocalOf { ThemeMode.System }

@Composable
internal fun isAppDarkTheme(): Boolean =
        when (LocalThemeMode.current) {
            ThemeMode.System -> isSystemInDarkTheme()
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }

@Composable
internal fun glassBorder(): Color =
        if (isAppDarkTheme()) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.75f)

private fun Typography.tuned(): Typography =
        copy(
                titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
                titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
                bodyMedium = bodyMedium.copy(lineHeight = 21.sp),
                bodySmall = bodySmall.copy(lineHeight = 17.sp),
                labelSmall = labelSmall.copy(letterSpacing = 0.2.sp),
                labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium),
        )

@Composable
fun AppTheme(themeMode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val dark =
            when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
    CompositionLocalProvider(LocalThemeMode provides themeMode) {
        MaterialTheme(
                colorScheme = if (dark) DarkColors else LightColors,
                shapes = AppShapes,
                typography = Typography(fontFamily = appFontFamily()).tuned(),
                content = content,
        )
    }
}
