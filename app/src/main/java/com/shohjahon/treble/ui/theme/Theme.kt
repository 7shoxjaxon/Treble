package com.shohjahon.treble.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = LimeGreen,
    secondary = LimeSoft,
    background = BackgroundDark,
    surface = Surface1,
    onPrimary = DarkText,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun TrebleTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
