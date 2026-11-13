package com.liuji.xiaoqi.ui.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalPointerSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/**
 * 横向滑动切换日/周/月。
 *
 * 使用 [awaitHorizontalPointerSlopOrCancellation]：仅在越过 touchSlop 且方向为横向时接管，
 * 纵向滑动交给 LazyColumn / 下拉刷新，可安全挂在整页列表上。
 */
fun Modifier.horizontalPeriodSwipe(
    enabled: Boolean = true,
    thresholdPx: Float = 96f,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier {
    if (!enabled) return this
    return composed {
        val left by rememberUpdatedState(onSwipeLeft)
        val right by rememberUpdatedState(onSwipeRight)
        pointerInput(thresholdPx) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var total = 0f
                val slopChange = awaitHorizontalPointerSlopOrCancellation(
                    down.id,
                    down.type,
                ) { change, overSlop ->
                    change.consume()
                    total = overSlop
                } ?: return@awaitEachGesture

                var triggered = false
                fun maybeTrigger() {
                    if (triggered) return
                    when {
                        total <= -thresholdPx -> {
                            triggered = true
                            left()
                        }
                        total >= thresholdPx -> {
                            triggered = true
                            right()
                        }
                    }
                }
                maybeTrigger()
                if (triggered) return@awaitEachGesture

                horizontalDrag(slopChange.id) { change ->
                    total += change.positionChange().x
                    change.consume()
                    maybeTrigger()
                }
            }
        }
    }
}
