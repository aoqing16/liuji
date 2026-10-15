package com.liuji.xiaoqi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color(0xFF12141A),
    surface = Color(0xFF1C1F2A),
    onPrimary = Color.White,
    onBackground = Color(0xFFE8EAF0),
    onSurface = Color(0xFFE8EAF0),
    error = Danger,
    outline = Color(0xFF3A3F4B),
)

private val LightColorScheme = lightColorScheme(
    primary = Accent,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = PageBackground,
    surface = CardBackground,
    onPrimary = Color.White,
    onBackground = TextStrong,
    onSurface = TextStrong,
    error = Danger,
    outline = BorderDefault,
)

@Composable
fun 留迹Theme(
    /** 产品设计为浅色高对比；默认不跟随系统暗色，避免帧视图/Sheet 控件看不清 */
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
