package com.liuji.xiaoqi.frame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SparseAutoPolicyTest {
    @Test
    fun notDueBeforeTwoMinutes() {
        val since = 1_000L
        val now = since + SparseAutoPolicy.HOLD_MS - 1
        val step = SparseAutoPolicy.observe(since, lastSeen = now - 10_000L, now = now)
        assertEquals(SparseAutoPolicy.Step.Tracking(since), step)
    }

    @Test
    fun gapOver45SecondsRestartsInsteadOfDue() {
        val since = 1_000L
        val lastSeen = since + 80_000L
        val now = lastSeen + SparseAutoPolicy.GAP_MS + 1
        val step = SparseAutoPolicy.observe(since, lastSeen, now)
        assertEquals(SparseAutoPolicy.Step.Tracking(now), step)
    }

    @Test
    fun dueAfterTwoMinutesWithoutGap() {
        val since = 1_000L
        val now = since + SparseAutoPolicy.HOLD_MS
        val step = SparseAutoPolicy.observe(since, lastSeen = now - 10_000L, now = now)
        assertTrue(step is SparseAutoPolicy.Step.Due)
    }
}
