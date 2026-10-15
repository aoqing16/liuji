package com.liuji.xiaoqi.ima

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ImaSyncScheduler {
    const val UNIQUE_WORK = "ima_cloud_sync_periodic"

    fun reconcile(context: Context) {
        val prefs = ImaSyncPrefs(context)
        val cfg = prefs.loadConfig()
        val wm = WorkManager.getInstance(context.applicationContext)
        if (!cfg.autoEnabled || !prefs.isConfigured()) {
            wm.cancelUniqueWork(UNIQUE_WORK)
            return
        }
        val hours = cfg.interval.hours.coerceAtLeast(1L)
        // WorkManager 周期最小约 15 分钟；我们用小时级
        val req = PeriodicWorkRequestBuilder<ImaSyncWorker>(hours, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        wm.enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            req,
        )
    }
}
