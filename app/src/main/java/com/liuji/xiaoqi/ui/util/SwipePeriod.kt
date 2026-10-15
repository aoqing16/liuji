package com.liuji.xiaoqi.ui.util

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 横向滑动切换日/周/月。
 * 使用 [detectHorizontalDragGestures]，在确认横向手势前不抢占纵向列表滚动。
 */
fun Modifier.horizontalPeriodSwipe(
    enabled: Boolean = true,
    thresholdPx: Float = 96f,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier {
    if (!enabled) return this
    return composed {
        var acc by remember { mutableFloatStateOf(0f) }
        val left by rememberUpdatedState(onSwipeLeft)
        val right by rememberUpdatedState(onSwipeRight)
        pointerInput(thresholdPx) {
            detectHorizontalDragGestures(
                onDragStart = { acc = 0f },
                onDragEnd = {
                    when {
                        acc <= -thresholdPx -> left()
                        acc >= thresholdPx -> right()
                    }
                    acc = 0f
                },
                onDragCancel = { acc = 0f },
                onHorizontalDrag = { change, dragAmount ->
                    acc += dragAmount
                    // 超过阈值后再消费，减少与纵向滚动的指针竞争
                    if (kotlin.math.abs(acc) >= thresholdPx * 0.35f) {
                        change.consume()
                    }
                },
            )
        }
    }
}
