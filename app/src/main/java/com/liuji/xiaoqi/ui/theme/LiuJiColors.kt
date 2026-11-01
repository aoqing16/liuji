package com.liuji.xiaoqi.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class LiuJiColors(
    val pageBackground: Color,
    val cardBackground: Color,
    val surfaceMuted: Color,
    val surfaceChip: Color,
    val track: Color,
    val borderDefault: Color,
    val textStrong: Color,
    val textMuted: Color,
    val textFaint: Color,
    val textTimeFaint: Color,
    val accentSoft: Color,
    val accentStroke: Color,
    val scrim: Color,
    val shadowTint: Color,
    val shadowTintStrong: Color,
)

val LightLiuJiColors = LiuJiColors(
    pageBackground = Color(0xFFF3F4F7),
    cardBackground = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF7F8FB),
    surfaceChip = Color(0xFFF3F4F6),
    track = Color(0xFFF1F3F7),
    borderDefault = Color(0xFFE6E8EE),
    textStrong = Color(0xFF1C1F2A),
    textMuted = Color(0xFF6B7280),
    textFaint = Color(0xFF9CA3AF),
    textTimeFaint = Color(0xFFB0B7C3),
    accentSoft = Color(0x1A5B6CFF),
    accentStroke = Color(0x595B6CFF),
    scrim = Color(0x22000000),
    shadowTint = Color(0x14001428),
    shadowTintStrong = Color(0x22001428),
)

val DarkLiuJiColors = LiuJiColors(
    pageBackground = Color(0xFF12141A),
    cardBackground = Color(0xFF1C1F2A),
    surfaceMuted = Color(0xFF161922),
    surfaceChip = Color(0xFF262A36),
    track = Color(0xFF262A36),
    borderDefault = Color(0xFF3A3F4B),
    textStrong = Color(0xFFE8EAF0),
    textMuted = Color(0xFF9CA3AF),
    textFaint = Color(0xFF6B7280),
    textTimeFaint = Color(0xFF5C6370),
    accentSoft = Color(0x335B6CFF),
    accentStroke = Color(0x995B6CFF),
    scrim = Color(0x66000000),
    shadowTint = Color(0x33000000),
    shadowTintStrong = Color(0x44000000),
)

val LocalLiuJiColors = staticCompositionLocalOf { LightLiuJiColors }

/** 页面背景 */
val PageBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalLiuJiColors.current.pageBackground

/** 卡片 / 导航栏表面 */
val CardBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalLiuJiColors.current.cardBackground

/** 次级表面 · 画布底、详情块 */
val SurfaceMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalLiuJiColors.current.surfaceMuted

/** 芯片 / 小图标井 */
val SurfaceChip: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalLiuJiColors.current.surfaceChip

/** 进度条轨道 */
val Track: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalLiuJiColors.current.track

/** 默认描边 */
val BorderDefault: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalLiuJiColors.current.borderDefault

/** 主文案 */
val TextStrong: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalLiuJiColors.current.textStrong

/** 次要说明 */
val TextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalLiuJiColors.current.textMuted

/** 更弱提示 */
val TextFaint: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalLiuJiColors.current.textFaint

/** 时间轴弱字 */
val TextTimeFaint: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalLiuJiColors.current.textTimeFaint

/** 强调色浅底 · Chip / 图标井 */
val AccentSoft: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalLiuJiColors.current.accentSoft

/** 帧节点描边 · 比 AccentSoft 更清晰 */
val AccentStroke: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalLiuJiColors.current.accentStroke

/** 画布边框 / 轻遮罩 */
val Scrim: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalLiuJiColors.current.scrim

/** 卡片阴影色相 */
val ShadowTint: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalLiuJiColors.current.shadowTint

val ShadowTintStrong: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalLiuJiColors.current.shadowTintStrong
