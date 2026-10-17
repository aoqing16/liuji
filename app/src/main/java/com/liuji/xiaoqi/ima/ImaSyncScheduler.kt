package com.liuji.xiaoqi.ima

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object ImaSyncScheduler {
    const val UNIQUE_WORK = "ima_ai_sync_daily"
    /** 旧周期任务名，reconcile 时一并取消 */
    private const val LEGACY_PERIODIC = "ima_cloud_sync_periodic"

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun reconcile(context: Context) {
        val prefs = ImaSyncPrefs(context)
        val cfg = prefs.loadConfig()
        val wm = WorkManager.getInstance(context.applicationContext)
        wm.cancelUniqueWork(LEGACY_PERIODIC)
        if (!cfg.autoEnabled || !prefs.isConfigured()) {
            wm.cancelUniqueWork(UNIQUE_WORK)
            return
        }
        enqueueNextDaily(context)
    }

    /** Worker 跑完后调用，预约下一天 00:30 */
    fun enqueueNextDaily(context: Context) {
        val prefs = ImaSyncPrefs(context)
        if (!prefs.loadConfig().autoEnabled || !prefs.isConfigured()) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK)
            return
        }
        val delayMs = millisUntilNextRun()
        val req = OneTimeWorkRequestBuilder<ImaSyncWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            req,
        )
    }

    /** 距下次本地 00:30 的毫秒数（至少 1 分钟，避免紧挨着反复触发） */
    fun millisUntilNextRun(now: LocalDateTime = LocalDateTime.now(zone)): Long {
        var next = LocalDateTime.of(LocalDate.now(zone), LocalTime.of(0, 30))
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return Duration.between(now, next).toMillis().coerceAtLeast(60_000L)
    }
}
