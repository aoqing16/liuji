package com.liuji.xiaoqi.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liuji.xiaoqi.data.StatsPeriod
import com.liuji.xiaoqi.ui.theme.ShapeChip
import com.liuji.xiaoqi.ui.theme.SurfaceChip
import com.liuji.xiaoqi.ui.theme.TextMuted

@Composable
fun skeletonPulseAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    return alpha
}

@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    color: Color = SurfaceChip,
    shape: Shape = ShapeChip,
) {
    val alpha = skeletonPulseAlpha()
    Box(
        modifier
            .clip(shape)
            .background(color.copy(alpha = alpha.coerceIn(0.35f, 0.9f))),
    )
}

@Composable
fun SkeletonBar(
    width: Dp,
    height: Dp = 12.dp,
    modifier: Modifier = Modifier,
) {
    SkeletonBlock(
        modifier = modifier
            .width(width)
            .height(height),
        color = TextMuted.copy(alpha = 0.18f),
    )
}

@Composable
fun SkeletonBarFill(
    height: Dp = 12.dp,
    fraction: Float = 1f,
    modifier: Modifier = Modifier,
) {
    SkeletonBlock(
        modifier = modifier
            .fillMaxWidth(fraction.coerceIn(0.15f, 1f))
            .height(height),
        color = TextMuted.copy(alpha = 0.18f),
    )
}

/** 日：24 柱；周：7 柱；月：约 10 柱轮廓 */
@Composable
fun ChartSkeleton(
    period: StatsPeriod,
    modifier: Modifier = Modifier,
) {
    val bars = when (period) {
        StatsPeriod.DAY -> 24
        StatsPeriod.WEEK -> 7
        StatsPeriod.MONTH -> 10
    }
    // 固定相对高度，避免随机跳动
    val heights = when (period) {
        StatsPeriod.DAY -> listOf(
            0.25f, 0.18f, 0.12f, 0.10f, 0.15f, 0.28f, 0.40f, 0.55f,
            0.62f, 0.48f, 0.35f, 0.42f, 0.58f, 0.70f, 0.65f, 0.50f,
            0.45f, 0.60f, 0.75f, 0.68f, 0.52f, 0.38f, 0.30f, 0.22f,
        )
        StatsPeriod.WEEK -> listOf(0.45f, 0.62f, 0.55f, 0.70f, 0.48f, 0.80f, 0.35f)
        StatsPeriod.MONTH -> listOf(
            0.40f, 0.55f, 0.35f, 0.70f, 0.50f, 0.60f, 0.45f, 0.75f, 0.55f, 0.42f,
        )
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            horizontalArrangement = Arrangement.spacedBy(if (bars > 12) 3.dp else 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            repeat(bars) { i ->
                val h = heights.getOrElse(i) { 0.4f }
                SkeletonBlock(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(h),
                    color = TextMuted.copy(alpha = 0.16f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) {
                SkeletonBar(width = 48.dp, height = 10.dp)
            }
        }
    }
}

@Composable
fun AppListSkeleton(
    rows: Int = 8,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(rows) { i ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SkeletonBlock(
                    modifier = Modifier.size(36.dp),
                    color = TextMuted.copy(alpha = 0.16f),
                    shape = CircleShape,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBarFill(height = 12.dp, fraction = 0.55f - (i % 3) * 0.08f)
                    Spacer(Modifier.height(6.dp))
                    SkeletonBarFill(height = 8.dp, fraction = 0.35f)
                }
                Spacer(Modifier.width(12.dp))
                SkeletonBar(width = 40.dp, height = 10.dp)
            }
        }
    }
}

@Composable
fun CategoryListSkeleton(
    rows: Int = 5,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(rows) { i ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SkeletonBlock(
                    modifier = Modifier.size(12.dp),
                    color = TextMuted.copy(alpha = 0.2f),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBarFill(height = 12.dp, fraction = 0.4f + (i % 3) * 0.1f)
                    Spacer(Modifier.height(6.dp))
                    SkeletonBarFill(height = 8.dp, fraction = 0.7f)
                }
            }
        }
    }
}

@Composable
fun TimelineListSkeleton(
    rows: Int = 6,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(rows) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    SkeletonBar(width = 36.dp, height = 10.dp)
                    Spacer(Modifier.height(4.dp))
                    SkeletonBar(width = 28.dp, height = 8.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBlock(
                            modifier = Modifier.size(28.dp),
                            color = TextMuted.copy(alpha = 0.16f),
                            shape = CircleShape,
                        )
                        Spacer(Modifier.width(8.dp))
                        SkeletonBarFill(height = 12.dp, fraction = 0.5f)
                    }
                    Spacer(Modifier.height(8.dp))
                    SkeletonBarFill(height = 8.dp, fraction = 0.85f)
                }
            }
        }
    }
}
