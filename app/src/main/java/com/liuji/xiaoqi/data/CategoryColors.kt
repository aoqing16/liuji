package com.liuji.xiaoqi.data

import androidx.compose.ui.graphics.Color

object CategoryColors {
    fun toHex(color: Color): String {
        val a = (color.alpha * 255).toInt().coerceIn(0, 255)
        val r = (color.red * 255).toInt().coerceIn(0, 255)
        val g = (color.green * 255).toInt().coerceIn(0, 255)
        val b = (color.blue * 255).toInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X%02X", a, r, g, b)
    }

    fun fromHex(hex: String): Color? {
        val raw = hex.trim().removePrefix("#")
        return when (raw.length) {
            6 -> {
                val rgb = raw.toLongOrNull(16) ?: return null
                Color(
                    alpha = 1f,
                    red = ((rgb shr 16) and 0xFF) / 255f,
                    green = ((rgb shr 8) and 0xFF) / 255f,
                    blue = (rgb and 0xFF) / 255f,
                )
            }
            8 -> {
                val argb = raw.toLongOrNull(16) ?: return null
                Color(
                    alpha = ((argb shr 24) and 0xFF) / 255f,
                    red = ((argb shr 16) and 0xFF) / 255f,
                    green = ((argb shr 8) and 0xFF) / 255f,
                    blue = (argb and 0xFF) / 255f,
                )
            }
            else -> null
        }
    }

    val pickerPresets: List<Color> = listOf(
        Color(0xFF6FCF97),
        Color(0xFF5B9BD5),
        Color(0xFF6B5B95),
        Color(0xFFE88B9A),
        Color(0xFFF0B429),
        Color(0xFF4ECDC4),
        Color(0xFFFF8A65),
        Color(0xFF8B7CF8),
        Color(0xFF9CA3AF),
        Color(0xFF22C55E),
        Color(0xFFEF4444),
        Color(0xFF3B82F6),
    )
}
