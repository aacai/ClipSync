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
import zhiqiu.applewidgets.kmp.oink.theme.OinkAccent
import zhiqiu.applewidgets.kmp.oink.theme.OinkColors
import zhiqiu.applewidgets.kmp.oink.theme.OinkTheme
import zhiqiu.applewidgets.kmp.oink.theme.oinkAppleColors
import zhiqiu.applewidgets.kmp.oink.theme.oinkAppleTextStyles
import zhiqiu.applewidgets.kmp.oink.theme.oinkDarkColors
import zhiqiu.applewidgets.kmp.oink.theme.oinkLightColors
import zhiqiu.applewidgets.kmp.theme.AppleWidgetsTheme
import zhiqiu.app.cs.core.ThemeMode

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

object Palette {
    val Background = Color(0xFF0E1014)
    val Surface = Color(0xFF161A21)
    val SurfaceHigh = Color(0xFF1E242D)
    val Primary = Color(0xFF4C8DFF)
    val Success = Color(0xFF34C759)
    val Danger = Color(0xFFFF453A)
    val TextPrimary = Color(0xFFF2F2F7)
    val TextSecondary = Color(0xFF9AA1AD)
    val TextMuted = Color(0xFF6B7280)

    val Gold = Primary
    val GoldSoft = Primary
    val Jade = Success
    val Crimson = Danger
    val Ink = Background
    val InkMid = Surface
    val InkSoft = SurfaceHigh
}

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

internal val GlassDialogShape = RoundedCornerShape(22.dp)

private fun Typography.tuned(): Typography =
        copy(
                titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
                titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
                bodyMedium = bodyMedium.copy(lineHeight = 21.sp),
                bodySmall = bodySmall.copy(lineHeight = 17.sp),
                labelSmall = labelSmall.copy(letterSpacing = 0.2.sp),
                labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium),
        )

private fun oinkColorScheme(c: OinkColors, dark: Boolean) =
        if (dark) {
            darkColorScheme(
                    primary = c.accent,
                    onPrimary = c.onAccent,
                    primaryContainer = c.accentSoft,
                    onPrimaryContainer = c.accentText,
                    secondary = c.accentDeep,
                    onSecondary = c.onAccent,
                    secondaryContainer = c.accentTint,
                    onSecondaryContainer = c.accentText,
                    background = c.bg,
                    onBackground = c.text,
                    surface = c.surface,
                    onSurface = c.text,
                    surfaceVariant = c.well,
                    onSurfaceVariant = c.text2,
                    surfaceContainer = c.card,
                    surfaceContainerHigh = c.raised,
                    outline = c.line,
                    outlineVariant = c.line2,
                    error = c.danger,
                    onError = c.onInverse,
                    errorContainer = c.dangerSoft,
                    onErrorContainer = c.dangerText,
                    inverseSurface = c.inverse,
                    inverseOnSurface = c.onInverse,
            )
        } else {
            lightColorScheme(
                    primary = c.accent,
                    onPrimary = c.onAccent,
                    primaryContainer = c.accentSoft,
                    onPrimaryContainer = c.accentText,
                    secondary = c.accentDeep,
                    onSecondary = c.onAccent,
                    secondaryContainer = c.accentTint,
                    onSecondaryContainer = c.accentText,
                    background = c.bg,
                    onBackground = c.text,
                    surface = c.surface,
                    onSurface = c.text,
                    surfaceVariant = c.well,
                    onSurfaceVariant = c.text2,
                    surfaceContainer = c.card,
                    surfaceContainerHigh = c.raised,
                    outline = c.line,
                    outlineVariant = c.line2,
                    error = c.danger,
                    onError = c.onInverse,
                    errorContainer = c.dangerSoft,
                    onErrorContainer = c.dangerText,
                    inverseSurface = c.inverse,
                    inverseOnSurface = c.onInverse,
            )
        }

@Composable
fun AppTheme(themeMode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val dark =
            when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
    val accent = OinkAccent.Pink
    val oinkColors = if (dark) oinkDarkColors(accent) else oinkLightColors(accent)
    CompositionLocalProvider(LocalThemeMode provides themeMode) {
        OinkTheme(colors = oinkColors) {
            AppleWidgetsTheme(
                    colors = oinkAppleColors(OinkTheme.colors),
                    textStyles = oinkAppleTextStyles(OinkTheme.textStyles),
            ) {
                MaterialTheme(
                        colorScheme = oinkColorScheme(OinkTheme.colors, dark),
                        shapes = AppShapes,
                        typography = Typography(fontFamily = appFontFamily()).tuned(),
                        content = content,
                )
            }
        }
    }
}
