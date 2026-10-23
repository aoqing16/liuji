package com.liuji.xiaoqi.frame

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.liuji.xiaoqi.MainActivity
import com.liuji.xiaoqi.R
import com.liuji.xiaoqi.data.db.UiFrameEntity
import com.liuji.xiaoqi.data.db.UsageDatabase
import com.liuji.xiaoqi.data.frame.FrameInterval
import com.liuji.xiaoqi.data.frame.FrameJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 帧视图记录：定时档位轮询；「操作变化」档位由无障碍防抖后触发，且文本变化才入库。
 */
class FrameRecorderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private val tickRunnable = object : Runnable {
        override fun run() {
            scope.launch { captureOnce(requireTextChange = false) }
            handler.postDelayed(this, cachedIntervalMs.coerceAtLeast(5_000L))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = buildNotification("帧视图记录运行中")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CAPTURE -> {
                scope.launch {
                    ensureEventDrivenFromSettings()
                    captureOnce(requireTextChange = true)
                }
            }
            else -> configureModeFromSettings()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        FrameCaptureAccessibilityService.setEventDrivenEnabled(false)
        scope.cancel()
        super.onDestroy()
    }

    private fun configureModeFromSettings() {
        scope.launch {
            val settings = withContext(Dispatchers.IO) {
                val dao = UsageDatabase.getInstance(applicationContext).usageDao()
                FrameBlacklistRules.ensureLoaded(applicationContext)
                FrameBlacklistStore.refresh(dao)
                dao.getSettings()
            }
            if (settings == null || !settings.frameRecordingEnabled) {
                stopSelf()
                return@launch
            }
            applyIntervalMode(FrameInterval.fromMillis(settings.frameIntervalMs))
        }
    }

    private suspend fun ensureEventDrivenFromSettings() {
        val settings = withContext(Dispatchers.IO) {
            val dao = UsageDatabase.getInstance(applicationContext).usageDao()
            FrameBlacklistRules.ensureLoaded(applicationContext)
            FrameBlacklistStore.refresh(dao)
            dao.getSettings()
        }
        if (settings == null || !settings.frameRecordingEnabled) {
            stopSelf()
            return
        }
        val interval = FrameInterval.fromMillis(settings.frameIntervalMs)
        if (!interval.isTimed) {
            eventDrivenMode = true
            FrameCaptureAccessibilityService.setEventDrivenEnabled(true)
            cachedIntervalMs = 0L
        }
    }

    private fun applyIntervalMode(interval: FrameInterval) {
        cachedIntervalMs = interval.millis
        eventDrivenMode = !interval.isTimed
        FrameCaptureAccessibilityService.setEventDrivenEnabled(eventDrivenMode)
        handler.removeCallbacks(tickRunnable)
        if (interval.isTimed) {
            updateNotification("定时记录 · ${interval.label}")
            handler.post(tickRunnable)
        } else {
            updateNotification("操作变化时记录（防抖+文本变化）")
        }
    }

    private suspend fun captureOnce(requireTextChange: Boolean) = withContext(Dispatchers.IO) {
        val dao = UsageDatabase.getInstance(applicationContext).usageDao()
        val settings = dao.getSettings()
        if (settings == null || !settings.frameRecordingEnabled) {
            stopSelf()
            return@withContext
        }
        val interval = FrameInterval.fromMillis(settings.frameIntervalMs)
        cachedIntervalMs = interval.millis
        val needTextChange = requireTextChange || !interval.isTimed

        if (!FrameCaptureAccessibilityService.isEnabled(applicationContext)) {
            updateNotification("请开启无障碍权限以记录帧视图")
            return@withContext
        }

        FrameBlacklistRules.ensureLoaded(applicationContext)
        val snaps = FrameCaptureAccessibilityService.capture()
        if (snaps.isEmpty()) return@withContext

        val selfPkg = packageName
        var saved = 0
        var lastPkg = ""
        for (snap in snaps) {
            val pkg = snap.packageName.trim()
            if (pkg.isEmpty() || pkg == selfPkg) continue
            if (FrameBlacklistStore.isBlocked(pkg)) continue

            // 悬浮窗/小窗不参与特征库与稀疏自动拉黑，避免 AutoJS 浮窗误判
            if (!snap.overlayOnly && FrameBlacklistRules.matches(pkg, snap.activityName)) {
                FrameBlacklistStore.add(
                    dao,
                    pkg,
                    resolveLabel(pkg),
                    FrameBlacklistStore.SOURCE_BUILTIN,
                )
                continue
            }

            val hash = FrameJson.contentFingerprint(pkg, snap.texts)
            val sparse = !snap.overlayOnly && FrameJson.isSparseText(snap.texts)
            if (needTextChange && lastTextHashByPkg[pkg] == hash) {
                if (sparse) noteSparseFrame(dao, pkg) else if (!snap.overlayOnly) {
                    clearSparse(pkg)
                }
                continue
            }
            lastTextHashByPkg[pkg] = hash

            dao.insertFrame(
                UiFrameEntity(
                    capturedAt = snap.capturedAt,
                    packageName = pkg,
                    activityName = snap.activityName,
                    screenWidth = snap.screenWidth,
                    screenHeight = snap.screenHeight,
                    textCount = snap.texts.size,
                    textsJson = FrameJson.textsToJson(snap.texts),
                ),
            )
            saved++
            lastPkg = pkg

            if (sparse) noteSparseFrame(dao, pkg) else clearSparse(pkg)
        }
        if (saved > 0) {
            val modeHint = if (needTextChange) "文本变化" else interval.label
            updateNotification("已记录 $saved 包 · $modeHint · $lastPkg")
        }
    }

    private suspend fun noteSparseFrame(dao: com.liuji.xiaoqi.data.db.UsageDao, pkg: String) {
        if (
            FrameBlacklistStore.isBlocked(pkg) ||
            FrameBlacklistStore.isExempt(pkg) ||
            FrameBlacklistStore.isPending(pkg)
        ) {
            clearSparse(pkg)
            return
        }
        val now = System.currentTimeMillis()
        val since = sparseSinceByPkg[pkg] ?: 0L
        val lastSeen = sparseLastSeenByPkg[pkg] ?: 0L
        when (val step = SparseAutoPolicy.observe(since, lastSeen, now)) {
            is SparseAutoPolicy.Step.Tracking -> {
                sparseSinceByPkg[pkg] = step.since
                sparseLastSeenByPkg[pkg] = now
            }
            SparseAutoPolicy.Step.Due -> {
                clearSparse(pkg)
                FrameBlacklistStore.propose(dao, pkg, resolveLabel(pkg))
            }
        }
    }

    private fun clearSparse(pkg: String) {
        sparseSinceByPkg.remove(pkg)
        sparseLastSeenByPkg.remove(pkg)
    }

    private fun resolveLabel(packageName: String): String {
        return runCatching {
            val pm = applicationContext.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrElse { packageName.substringAfterLast('.') }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService<NotificationManager>() ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "帧视图记录",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("留迹 · 帧视图记录")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService<NotificationManager>() ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_STOP = "com.liuji.xiaoqi.frame.STOP"
        const val ACTION_CAPTURE = "com.liuji.xiaoqi.frame.CAPTURE"
        private const val CHANNEL_ID = "frame_recorder"
        private const val NOTIFICATION_ID = 42

        @Volatile
        private var cachedIntervalMs: Long = 30_000L

        @Volatile
        private var eventDrivenMode: Boolean = false

        private val lastTextHashByPkg = ConcurrentHashMap<String, String>()
        private val sparseSinceByPkg = ConcurrentHashMap<String, Long>()
        private val sparseLastSeenByPkg = ConcurrentHashMap<String, Long>()

        fun start(context: Context) {
            val intent = Intent(context, FrameRecorderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            FrameCaptureAccessibilityService.setEventDrivenEnabled(false)
            lastTextHashByPkg.clear()
            sparseSinceByPkg.clear()
            sparseLastSeenByPkg.clear()
            context.startService(
                Intent(context, FrameRecorderService::class.java).setAction(ACTION_STOP),
            )
            context.stopService(Intent(context, FrameRecorderService::class.java))
        }

        fun syncWithSettings(context: Context, enabled: Boolean) {
            if (enabled) start(context) else stop(context)
        }

        fun requestCapture(context: Context) {
            if (!eventDrivenMode) return
            context.startService(
                Intent(context, FrameRecorderService::class.java).setAction(ACTION_CAPTURE),
            )
        }
    }
}
