package com.arua.lonyichat.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ✨ Using the Facebook-inspired dark colors ✨
private val DarkColorScheme = darkColorScheme(
    primary = LonyiDarkPrimary,
    secondary = LonyiLightOrange,
    background = LonyiDarkBackground,
    surface = LonyiDarkSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = LonyiDarkTextPrimary,
    onSurface = LonyiDarkTextPrimary,
)

private val LightColorScheme = lightColorScheme(
    primary = LonyiOrange,
    secondary = LonyiDarkBlue,
    background = LonyiLightGray,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = LonyiTextPrimary,
    onSurface = LonyiTextPrimary,
)

@Composable
fun LonyiChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // ✨ Disabling dynamic color to enforce our Facebook-style theme ✨
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Use the surface color for the status bar for a seamless header
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}