package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val ArcadeDarkColorScheme = darkColorScheme(
    primary = CyanElectric,
    onPrimary = MidnightBg,
    primaryContainer = CyanDeep,
    onPrimaryContainer = TextPrimary,
    secondary = AmberWarm,
    onSecondary = MidnightBg,
    secondaryContainer = AmberDeep,
    onSecondaryContainer = TextPrimary,
    tertiary = EmeraldMint,
    onTertiary = MidnightBg,
    tertiaryContainer = EmeraldDeep,
    onTertiaryContainer = TextPrimary,
    error = CoralRose,
    onError = MidnightBg,
    errorContainer = CoralDeep,
    onErrorContainer = TextPrimary,
    background = MidnightBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderSubtle
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MultiDropTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = ArcadeDarkColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
