package com.sstools.anticheat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Purple = Color(0xFF9B6DFF)
val PurpleLight = Color(0xFFB899FF)
val Pink = Color(0xFFFF5C8A)
val DarkBg = Color(0xFF0A0C12)
val DarkSurface = Color(0xFF0A0C12)
val DarkCard = Color(0xFF12151E)
val DarkBorder = Color(0xFF232636)
val TextPrimary = Color(0xFFEEF0F8)
val TextSecondary = Color(0xFF7E8499)
val Success = Color(0xFF3DDC84)
val Danger = Color(0xFFFF4757)
val Warning = Color(0xFFFFD93D)
val Info = Color(0xFF4E8AFF)
val Critical = Color(0xFFFF4757)
val High = Color(0xFFFF8C42)
val Medium = Color(0xFFFFD93D)
val Low = Color(0xFF3DDC84)

private val DarkColorScheme = darkColorScheme(
    primary = Purple,
    secondary = Pink,
    tertiary = Info,
    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = DarkCard,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = DarkBorder,
)

@Composable
fun AntiCheatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
