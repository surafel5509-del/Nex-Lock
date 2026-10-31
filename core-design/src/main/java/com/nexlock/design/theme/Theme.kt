package com.nexlock.design.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nexlock.common.model.AppLanguage
import com.nexlock.common.model.ThemeMode
import com.nexlock.design.strings.LocalStrings
import com.nexlock.design.strings.stringsFor

/**
 * Extra design tokens Material 3 does not model directly (gradients, glass, glow).
 */
data class NexExtraColors(
    val heroGradient: List<Color>,
    val accentGradient: List<Color>,
    val shieldGradient: List<Color>,
    val successGradient: List<Color>,
    val dangerGradient: List<Color>,
    val glassFill: Color,
    val glassBorder: Color,
    val glow: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val isDark: Boolean,
) {
    val heroBrush: Brush get() = Brush.linearGradient(heroGradient)
    val accentBrush: Brush get() = Brush.linearGradient(accentGradient)
    val shieldBrush: Brush get() = Brush.linearGradient(shieldGradient)
    val successBrush: Brush get() = Brush.linearGradient(successGradient)
    val dangerBrush: Brush get() = Brush.linearGradient(dangerGradient)
}

private val DarkExtra = NexExtraColors(
    heroGradient = listOf(Color(0xFF0B1526), Color(0xFF0A1020), Color(0xFF04050A)),
    accentGradient = listOf(ElectricBlueBright, ElectricBlue, VioletAccent),
    shieldGradient = listOf(CyanAccent, ElectricBlue, VioletAccent),
    successGradient = listOf(Color(0xFF2BD98B), Color(0xFF12A46B)),
    dangerGradient = listOf(Color(0xFFFF7A8C), DangerRed),
    glassFill = Color(0x12FFFFFF),
    glassBorder = Color(0x22FFFFFF),
    glow = ElectricBlue,
    success = SuccessGreen,
    warning = WarningAmber,
    danger = DangerRed,
    isDark = true,
)

private val LightExtra = NexExtraColors(
    heroGradient = listOf(Color(0xFFF7FAFF), Color(0xFFEDF3FF), Color(0xFFF9FBFF)),
    accentGradient = listOf(ElectricBlueDeep, ElectricBlue, VioletAccent),
    shieldGradient = listOf(ElectricBlue, ElectricBlueDeep, VioletAccent),
    successGradient = listOf(Color(0xFF24C97F), Color(0xFF0E8F5B)),
    dangerGradient = listOf(Color(0xFFF0607A), Color(0xFFD63B57)),
    glassFill = Color(0x0D0B1220),
    glassBorder = Color(0x140B1220),
    glow = ElectricBlueDeep,
    success = Color(0xFF12955F),
    warning = Color(0xFFB97200),
    danger = Color(0xFFD63B57),
    isDark = false,
)

private val DarkScheme = darkColorScheme(
    primary = ElectricBlueBright,
    onPrimary = Color(0xFF02102B),
    primaryContainer = ElectricBlueDeep,
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = CyanAccent,
    onSecondary = Color(0xFF00202A),
    tertiary = VioletAccent,
    onTertiary = Color(0xFF12002B),
    background = DeepBlack,
    onBackground = Color(0xFFE8EEF9),
    surface = MidnightNavy,
    onSurface = Color(0xFFE8EEF9),
    surfaceVariant = MidnightNavySurface,
    onSurfaceVariant = Color(0xFF9BAAC4),
    surfaceContainer = MidnightNavySurface,
    surfaceContainerHigh = MidnightNavyElevated,
    outline = SlateLine,
    outlineVariant = Color(0xFF1B2740),
    error = DangerRed,
    onError = Color(0xFF2A0008),
    errorContainer = Color(0xFF3A0A14),
    onErrorContainer = Color(0xFFFFD9DF),
)

private val LightScheme = lightColorScheme(
    primary = ElectricBlueDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF001B4D),
    secondary = Color(0xFF00687A),
    onSecondary = Color.White,
    tertiary = Color(0xFF5A3BD6),
    onTertiary = Color.White,
    background = CloudWhite,
    onBackground = Ink,
    surface = LightSurface,
    onSurface = Ink,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = InkMuted,
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = Color(0xFFE3E9F3),
    outline = LightOutline,
    outlineVariant = Color(0xFFE6EBF4),
    error = Color(0xFFC2223C),
    onError = Color.White,
    errorContainer = Color(0xFFFFDCE1),
    onErrorContainer = Color(0xFF40000D),
)

val LocalNexColors = staticCompositionLocalOf { DarkExtra }
val LocalHapticsEnabled = staticCompositionLocalOf { true }

@Composable
fun NexLockTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    hapticsEnabled: Boolean = true,
    language: AppLanguage = AppLanguage.ENGLISH,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    val extra = if (darkTheme) DarkExtra else LightExtra

    CompositionLocalProvider(
        LocalNexColors provides extra,
        LocalHapticsEnabled provides hapticsEnabled,
        LocalStrings provides stringsFor(language),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = NexTypography,
            shapes = NexShapes,
            content = content,
        )
    }
}

object NexSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val huge = 40.dp
}
