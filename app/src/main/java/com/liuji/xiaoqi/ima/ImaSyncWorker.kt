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
        if (!prefs.isConfigured() || !prefs.loadConfig().autoEnabled) {
            return@withContext Result.success()
        }
        return@withContext try {
            val report = ImaSyncRepository(applicationContext).syncIncremental()
            if (report.ok) Result.success() else Result.retry()
        } catch (e: Exception) {
            AppLog.w(TAG, "auto sync failed", e)
            prefs.setLastError(e.message ?: e.toString())
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ImaSyncWorker"
    }
}
