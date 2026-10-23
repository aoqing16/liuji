package com.liuji.xiaoqi.frame

/**
 * 稀疏画面是否该进入待确认。
 * 同一画面反复轮询只延续计时；离开超过 [GAP_MS] 则从当前观测重新计。
 */
internal object SparseAutoPolicy {
    const val HOLD_MS = 120_000L
    const val GAP_MS = 45_000L
    // shortcut: 采样间隔大于 45 秒时，相邻两次观测会被当成离开，AUTO 不到点；要覆盖 1 分钟及以上档位，把断档阈值抬到大于采样间隔。

    sealed interface Step {
        data class Tracking(val since: Long) : Step
        data object Due : Step
    }

    fun observe(since: Long, lastSeen: Long, now: Long): Step {
        val broken = lastSeen > 0L && now - lastSeen > GAP_MS
        val start = if (broken || since <= 0L) now else since
        return if (now - start >= HOLD_MS) Step.Due else Step.Tracking(start)
    }
}
