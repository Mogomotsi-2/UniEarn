package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Sleek Interface Material 3 Colors
val SleekPrimary = Color(0xFF21005D)          // Deep purple text/primary actions
val SleekOnPrimary = Color.White
val SleekPrimaryContainer = Color(0xFFEADDFF) // Light profile avatar / accent
val SleekOnPrimaryContainer = Color(0xFF21005D)

val SleekSecondaryContainer = Color(0xFFE8DEF8) // Rewards Wallet Card / light purple
val SleekOnSecondaryContainer = Color(0xFF1D192B)

val SleekTertiaryContainer = Color(0xFFFFD8E4)  // Value tag / Soft pink badge
val SleekOnTertiaryContainer = Color(0xFF31111D)

val SleekBackground = Color(0xFFFCF8FD)         // Main background
val SleekOnBackground = Color(0xFF1C1B1F)       // Main text
val SleekSurface = Color(0xFFFCF8FD)
val SleekOnSurface = Color(0xFF1C1B1F)
val SleekSurfaceVariant = Color(0xFFF3F3F3)      // Map/Idle background
val SleekOnSurfaceVariant = Color(0xFF49454F)

val SleekOutline = Color(0xFFE0E0E0)            // Card borders
val SleekOutlineVariant = Color(0xFFCAC4D0)     // Nav borders

private val SleekColorScheme = lightColorScheme(
    primary = SleekPrimary,
    onPrimary = SleekOnPrimary,
    primaryContainer = SleekPrimaryContainer,
    onPrimaryContainer = SleekOnPrimaryContainer,
    secondaryContainer = SleekSecondaryContainer,
    onSecondaryContainer = SleekOnSecondaryContainer,
    tertiaryContainer = SleekTertiaryContainer,
    onTertiaryContainer = SleekOnTertiaryContainer,
    background = SleekBackground,
    onBackground = SleekOnBackground,
    surface = SleekSurface,
    onSurface = SleekOnSurface,
    surfaceVariant = SleekSurfaceVariant,
    onSurfaceVariant = SleekOnSurfaceVariant,
    outline = SleekOutline,
    outlineVariant = SleekOutlineVariant
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Force Light "Sleek Interface" theme by default
    dynamicColor: Boolean = false, // Force consistent branding
    content: @Composable () -> Unit,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                window.statusBarColor = android.graphics.Color.parseColor("#21005D")
                androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = SleekColorScheme,
        typography = Typography,
        content = content
    )
}
