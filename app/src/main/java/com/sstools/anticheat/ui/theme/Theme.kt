package com.sstools.anticheat.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val Purple = Color(0xFF6C5CE7)
val PurpleLight = Color(0xFFA29BFE)
val Pink = Color(0xFFE056A0)
val DarkBg = Color(0xFF0A0A0F)
val DarkCard = Color(0xFF16161F)
val DarkSurface = Color(0xFF12121A)
val DarkBorder = Color(0xFF2A2A3A)
val TextPrimary = Color(0xFFE8E8F0)
val TextSecondary = Color(0xFF9898B0)
val Success = Color(0xFF00E676)
val Danger = Color(0xFFFF5252)
val Warning = Color(0xFFFFC107)
val Critical = Color(0xFFFF1744)
val High = Color(0xFFFF6E40)
val Medium = Color(0xFFFFAB40)
val Low = Color(0xFF69F0AE)
val Info = Color(0xFF40C4FF)

private val DarkColorScheme = darkColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    primaryContainer = Purple.copy(alpha = 0.3f),
    secondary = PurpleLight,
    tertiary = Pink,
    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = DarkCard,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder,
    error = Danger,
    onError = Color.White,
)

@Composable
fun AntiCheatTheme(content: @Composable () -> Unit) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkBg.toArgb()
            window.navigationBarColor = DarkBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
