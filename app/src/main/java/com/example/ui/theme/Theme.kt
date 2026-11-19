package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode {
    DARK, LIGHT, SYSTEM
}

@Immutable
data class WafaExtendedColors(
    val glassSurface: Color,
    val glassBorder: Color,
    val subtleDivider: Color,
    val accentBlue: Color,
    val accentBlueContainer: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val mutedText: Color,
    val isDark: Boolean
)

val LocalWafaColors = staticCompositionLocalOf {
    DarkWafaExtendedColors
}

private val DarkWafaExtendedColors = WafaExtendedColors(
    glassSurface = Color(0xCC11192C),
    glassBorder = NavyBorder,
    subtleDivider = NavyBorderSubtle,
    accentBlue = ElectricBlue,
    accentBlueContainer = ElectricBlueContainer,
    success = StatusEmerald,
    successContainer = StatusEmeraldBgDark,
    warning = StatusAmber,
    warningContainer = StatusAmberBgDark,
    danger = StatusCrimson,
    dangerContainer = StatusCrimsonBgDark,
    mutedText = InkDarkMuted,
    isDark = true
)

private val LightWafaExtendedColors = WafaExtendedColors(
    glassSurface = Color(0xEEFFFFFF),
    glassBorder = LightBorder,
    subtleDivider = LightSurfaceHigh,
    accentBlue = CobaltBlue,
    accentBlueContainer = CobaltBlueContainer,
    success = Color(0xFF059669),
    successContainer = StatusEmeraldBgLight,
    warning = Color(0xFFD97706),
    warningContainer = StatusAmberBgLight,
    danger = Color(0xFFDC2626),
    dangerContainer = StatusCrimsonBgLight,
    mutedText = InkLightMuted,
    isDark = false
)

private val WafaDarkColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = Color.White,
    primaryContainer = ElectricVioletContainer,
    onPrimaryContainer = ElectricVioletLight,
    secondary = ElectricBlue,
    onSecondary = NavyVoid,
    secondaryContainer = ElectricBlueContainer,
    onSecondaryContainer = ElectricBlue,
    tertiary = IndigoAccent,
    onTertiary = Color.White,
    background = NavyVoid,
    onBackground = InkDarkPrimary,
    surface = NavySurface,
    onSurface = InkDarkPrimary,
    surfaceVariant = NavySurfaceElevated,
    onSurfaceVariant = InkDarkSecondary,
    surfaceContainerLowest = NavyVoid,
    surfaceContainerLow = NavySurface,
    surfaceContainer = NavySurfaceElevated,
    surfaceContainerHigh = NavySurfaceHigh,
    surfaceContainerHighest = Color(0xFF233152),
    outline = NavyBorder,
    outlineVariant = NavyBorderSubtle,
    error = StatusCrimson,
    onError = Color.White,
    errorContainer = StatusCrimsonBgDark,
    onErrorContainer = Color(0xFFFCA5A5)
)

private val WafaLightColorScheme = lightColorScheme(
    primary = RoyalViolet,
    onPrimary = Color.White,
    primaryContainer = RoyalVioletContainer,
    onPrimaryContainer = RoyalViolet,
    secondary = CobaltBlue,
    onSecondary = Color.White,
    secondaryContainer = CobaltBlueContainer,
    onSecondaryContainer = CobaltBlue,
    tertiary = IndigoAccent,
    onTertiary = Color.White,
    background = AlabasterBg,
    onBackground = InkLightPrimary,
    surface = PureWhite,
    onSurface = InkLightPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = InkLightSecondary,
    surfaceContainerLowest = PureWhite,
    surfaceContainerLow = Color(0xFFF9FBFF),
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = Color(0xFFD6E0F0),
    outline = LightBorder,
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = StatusCrimsonBgLight,
    onErrorContainer = Color(0xFF991B1B)
)

val WafaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun WafaInboxTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (isDark) WafaDarkColorScheme else WafaLightColorScheme
    val extendedColors = if (isDark) DarkWafaExtendedColors else LightWafaExtendedColors

    CompositionLocalProvider(LocalWafaColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WafaTypography,
            shapes = WafaShapes,
            content = content
        )
    }
}
