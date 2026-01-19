package com.contactless.fingerprint.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Light Color Scheme - Using the new design system
private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = BackgroundWhite,
    primaryContainer = PrimaryBlueLight,
    onPrimaryContainer = PrimaryBlue,
    
    secondary = SecondaryText,
    onSecondary = BackgroundWhite,
    
    tertiary = SuccessGreen,
    onTertiary = BackgroundWhite,
    
    error = DangerRed,
    onError = BackgroundWhite,
    errorContainer = Color(0x1AFF453A), // DangerRed / 10
    
    background = BackgroundWhite,
    onBackground = PrimaryText,
    
    surface = BackgroundWhite,
    onSurface = PrimaryText,
    surfaceVariant = LightGray,
    onSurfaceVariant = SecondaryText,
    
    outline = BorderGray,
    outlineVariant = SubtleGray,
    
    scrim = BlackOverlay,
    inverseSurface = PrimaryText,
    inverseOnSurface = BackgroundWhite,
    inversePrimary = PrimaryBlue
)

// Dark Color Scheme - Adjusted for dark mode
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = BackgroundWhite,
    primaryContainer = Color(0x330A84FF), // PrimaryBlue with opacity
    onPrimaryContainer = PrimaryBlue,
    
    secondary = Color(0xFF8E8E93), // Lighter secondary for dark mode
    onSecondary = BackgroundWhite,
    
    tertiary = SuccessGreen,
    onTertiary = BackgroundWhite,
    
    error = DangerRed,
    onError = BackgroundWhite,
    errorContainer = Color(0x33FF453A),
    
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF8E8E93),
    
    outline = Color(0xFF38383A),
    outlineVariant = Color(0xFF2C2C2E),
    
    scrim = BlackOverlay,
    inverseSurface = Color(0xFFFFFFFF),
    inverseOnSurface = Color(0xFF1C1C1E),
    inversePrimary = PrimaryBlue
)

@Composable
fun ContactlessFingerprintTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled to use our custom design system
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
