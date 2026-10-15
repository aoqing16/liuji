package com.liuji.xiaoqi.ui.util

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 根据甩动初速度（加速度的近似）放大惯性滚动：
 * 轻滑接近默认，甩得越快列表滚动越远、越快。
 */
@Composable
fun rememberAccelerationFlingBehavior(): FlingBehavior {
    val density = LocalDensity.current
    return remember(density) {
        val decay: DecayAnimationSpec<Float> = exponentialDecay(
            // 略低于默认摩擦，高速甩动能滑得更远
            frictionMultiplier = 0.72f,
            absVelocityThreshold = 0.5f,
        )
        // 用 dp 换算速度阈值，避免不同密度手感差过大
        val slow = with(density) { 1800.dp.toPx() }
        val mid = with(density) { 3600.dp.toPx() }
        val fast = with(density) { 6000.dp.toPx() }

        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                val absV = abs(initialVelocity)
                val boost = when {
                    absV >= fast -> 2.4f
                    absV >= mid -> 1.8f
                    absV >= slow -> 1.35f
                    else -> 1f
                }
                var velocityLeft = initialVelocity * boost
                if (abs(velocityLeft) > 1f) {
                    var lastValue = 0f
                    AnimationState(
                        initialValue = 0f,
                        initialVelocity = velocityLeft,
                    ).animateDecay(decay) {
                        val delta = value - lastValue
                        val consumed = scrollBy(delta)
                        lastValue = value
                        // 碰到边界则停
                        if (abs(delta - consumed) > 0.5f) {
                            cancelAnimation()
                        }
                    }
                    velocityLeft = 0f
                }
                return velocityLeft
            }
        }
    }
}
