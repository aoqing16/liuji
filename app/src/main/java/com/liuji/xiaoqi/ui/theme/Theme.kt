package com.liuji.xiaoqi.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = DarkLiuJiColors.pageBackground,
    surface = DarkLiuJiColors.cardBackground,
    onPrimary = Color.White,
    onBackground = DarkLiuJiColors.textStrong,
    onSurface = DarkLiuJiColors.textStrong,
    error = Danger,
    outline = DarkLiuJiColors.borderDefault,
)

private val LightColorScheme = lightColorScheme(
    primary = Accent,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = LightLiuJiColors.pageBackground,
    surface = LightLiuJiColors.cardBackground,
    onPrimary = Color.White,
    onBackground = LightLiuJiColors.textStrong,
    onSurface = LightLiuJiColors.textStrong,
    error = Danger,
    outline = LightLiuJiColors.borderDefault,
)

@Composable
fun 留迹Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkLiuJiColors else LightLiuJiColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = !darkTheme
            insets.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    CompositionLocalProvider(LocalLiuJiColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content,
        )
    }
}
