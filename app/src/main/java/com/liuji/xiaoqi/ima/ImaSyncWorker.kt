package com.liuji.xiaoqi.ima

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.liuji.xiaoqi.log.AppLog
import com.liuji.xiaoqi.log.RuntimeLogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImaSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        RuntimeLogStore.init(applicationContext)
        val prefs = ImaSyncPrefs(applicationContext)
        try {
            if (prefs.isConfigured() && prefs.loadConfig().autoEnabled) {
                val report = ImaSyncRepository(applicationContext).syncIncremental(forceOverwrite = false)
                AppLog.i(TAG, "daily sync: ${report.message}")
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "auto sync failed", e)
            prefs.setLastError(e.message ?: e.toString())
        } finally {
            // 无论成败都预约下一天，避免链条中断
            ImaSyncScheduler.enqueueNextDaily(applicationContext)
        }
        Result.success()
    }

    companion object {
        private const val TAG = "ImaSyncWorker"
    }
}
