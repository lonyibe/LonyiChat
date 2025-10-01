package com.arua.lonyichat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A single, consistent color scheme for the app
private val AppColorScheme = darkColorScheme(
    primary = LonyiOrange,
    onPrimary = Color.White,
    background = LonyiDarkBlue,
    onBackground = LonyiWhite,
    // ✨ CHANGED this to use the new surface color ✨
    surface = LonyiSurface,
    onSurface = LonyiWhite,
    secondaryContainer = LonyiOrange,
    onSecondaryContainer = Color.White
)

@Composable
fun LonyiChatTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = AppColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}