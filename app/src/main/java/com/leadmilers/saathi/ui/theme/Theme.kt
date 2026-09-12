package com.leadmilers.saathi.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary              = SaathiPlum,
    onPrimary            = SaathiSurface,
    primaryContainer     = SaathiBlush,
    onPrimaryContainer   = SaathiPlumDark,
    secondary            = SaathiCoral,
    onSecondary          = SaathiSurface,
    secondaryContainer   = Color(0xFFF9E8E7),
    onSecondaryContainer = Color(0xFF4A2020),
    tertiary             = SaathiSuccess,
    onTertiary           = SaathiSurface,
    background           = SaathiBackground,
    onBackground         = SaathiText,
    surface              = SaathiSurface,
    onSurface            = SaathiText,
    surfaceVariant       = SaathiSurfaceVariant,
    onSurfaceVariant     = SaathiTextSecondary,
    outline              = SaathiDivider,
    outlineVariant       = SaathiDivider,
    error                = SaathiError,
    onError              = SaathiSurface,
    errorContainer       = Color(0xFFFAE8E8),
    onErrorContainer     = Color(0xFF5C2020),
)

private val DarkColorScheme = darkColorScheme(
    primary              = SaathiDarkPrimary,
    onPrimary            = SaathiDarkBackground,
    primaryContainer     = SaathiPlumDark,
    onPrimaryContainer   = SaathiDarkPrimary,
    secondary            = SaathiDarkCoral,
    onSecondary          = SaathiDarkBackground,
    secondaryContainer   = Color(0xFF4A2B2B),
    onSecondaryContainer = SaathiDarkCoral,
    tertiary             = SaathiSuccess,
    onTertiary           = SaathiDarkBackground,
    background           = SaathiDarkBackground,
    onBackground         = SaathiDarkText,
    surface              = SaathiDarkSurface,
    onSurface            = SaathiDarkText,
    surfaceVariant       = SaathiDarkSurfaceVariant,
    onSurfaceVariant     = SaathiDarkTextSecondary,
    outline              = SaathiDarkDivider,
    outlineVariant       = SaathiDarkDivider,
    error                = SaathiError,
    onError              = SaathiDarkText,
    errorContainer       = Color(0xFF4A1A1A),
    onErrorContainer     = SaathiDarkCoral,
)

@Composable
fun SaathiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars     = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = SaathiTypography,
        shapes      = SaathiShapes,
        content     = content
    )
}
