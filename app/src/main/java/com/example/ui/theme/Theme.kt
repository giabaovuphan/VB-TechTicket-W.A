package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = BrandOrangePrimaryDark,
    onPrimary = OnBrandOrangePrimaryDark,
    primaryContainer = BrandOrangeContainerDark,
    onPrimaryContainer = OnBrandOrangeContainerDark,
    secondary = CharcoalSecondaryDark,
    onSecondary = OnCharcoalSecondaryDark,
    secondaryContainer = CharcoalContainerDark,
    onSecondaryContainer = OnCharcoalContainerDark,
    tertiary = RoastTertiaryDark,
    onTertiary = OnRoastTertiaryDark,
    tertiaryContainer = RoastContainerDark,
    onTertiaryContainer = OnRoastContainerDark,
    background = DarkBackground,
    onBackground = OnDarkBackground,
    surface = DarkSurface,
    onSurface = OnDarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnDarkSurfaceVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = ErrorRedDark,
    onError = OnErrorRedDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = BrandOrangePrimary,
    onPrimary = OnBrandOrangePrimary,
    primaryContainer = BrandOrangeContainer,
    onPrimaryContainer = OnBrandOrangeContainer,
    secondary = CharcoalSecondary,
    onSecondary = OnCharcoalSecondary,
    secondaryContainer = CharcoalContainer,
    onSecondaryContainer = OnCharcoalContainer,
    tertiary = RoastTertiary,
    onTertiary = OnRoastTertiary,
    tertiaryContainer = RoastContainer,
    onTertiaryContainer = OnRoastContainer,
    background = LightBackground,
    onBackground = OnLightBackground,
    surface = LightSurface,
    onSurface = OnLightSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = OnLightSurfaceVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = ErrorRed,
    onError = OnErrorRed,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

@Immutable
data class SemanticBadgeColors(
    val openFg: Color = StatusOpenFg,
    val openBg: Color = StatusOpenBg,
    val acceptedFg: Color = StatusAcceptedFg,
    val acceptedBg: Color = StatusAcceptedBg,
    val progressFg: Color = StatusProgressFg,
    val progressBg: Color = StatusProgressBg,
    val completedFg: Color = StatusCompletedFg,
    val completedBg: Color = StatusCompletedBg,
    val lowFg: Color = PriorityLowFg,
    val lowBg: Color = PriorityLowBg,
    val mediumFg: Color = PriorityMediumFg,
    val mediumBg: Color = PriorityMediumBg,
    val highFg: Color = PriorityHighFg,
    val highBg: Color = PriorityHighBg,
    val urgentFg: Color = PriorityUrgentFg,
    val urgentBg: Color = PriorityUrgentBg
)

val LocalSemanticColors = staticCompositionLocalOf { SemanticBadgeColors() }

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun TechTicketTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val semanticColors = if (darkTheme) {
        SemanticBadgeColors(
            openFg = StatusOpenFgDark,
            openBg = StatusOpenBgDark,
            acceptedFg = StatusAcceptedFgDark,
            acceptedBg = StatusAcceptedBgDark,
            progressFg = StatusProgressFgDark,
            progressBg = StatusProgressBgDark,
            completedFg = StatusCompletedFgDark,
            completedBg = StatusCompletedBgDark,
            lowFg = PriorityLowFgDark,
            lowBg = PriorityLowBgDark,
            mediumFg = PriorityMediumFgDark,
            mediumBg = PriorityMediumBgDark,
            highFg = PriorityHighFgDark,
            highBg = PriorityHighBgDark,
            urgentFg = PriorityUrgentFgDark,
            urgentBg = PriorityUrgentBgDark
        )
    } else {
        SemanticBadgeColors()
    }

    CompositionLocalProvider(LocalSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
