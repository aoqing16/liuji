package com.liuji.xiaoqi.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.liuji.xiaoqi.R
import com.liuji.xiaoqi.data.UsageRepository
import com.liuji.xiaoqi.data.db.UsageDatabase
import com.liuji.xiaoqi.data.frame.FrameFtsIndexer
import com.liuji.xiaoqi.data.frame.FrameSearchCursor
import com.liuji.xiaoqi.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * 长时间基准用前台服务，避免 BroadcastReceiver goAsync 超时被系统杀掉。
 * adb -s cf249afc shell am startservice -n com.liuji.xiaoqi/.debug.FrameSearchBenchmarkService
 */
class FrameSearchBenchmarkService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val notif = buildNotif("帧搜索基准运行中…")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        scope.launch {
            try {
                runBench(applicationContext) { msg ->
                    val n = buildNotif(msg)
                    if (Build.VERSION.SDK_INT >= 34) {
                        startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                    } else {
                        startForeground(NOTIF_ID, n)
                    }
                }
            } catch (t: Throwable) {
                AppLog.e(TAG, "基准失败: ${t.message}", Exception(t))
                runCatching {
                    File(filesDir, REPORT_FILE)
                        .writeText("FAILED: ${t.javaClass.simpleName}: ${t.message}")
                }
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runBench(app: Context, progress: (String) -> Unit) {
        val repo = UsageRepository(app)
        val indexer = FrameFtsIndexer.get(app)
        progress("等待 FTS 就绪…")
        indexer.initializeAndBackfill()

        val dbFile = app.getDatabasePath("liuji_usage.db")
        val dbSizeMb = dbFile.length() / (1024.0 * 1024.0)
        val largestPkg = findLargestPackage(app)

        val lines = ArrayList<String>()
        lines += "=== FrameSearchBenchmark ==="
        lines += "time=${System.currentTimeMillis()}"
        lines += "dbSizeMb=${"%.1f".format(dbSizeMb)}"
        lines += "ftsReady=${indexer.isReady()} ftsAvailable=${indexer.isAvailable}"
        lines += "ftsProgress=${indexer.progress.value}"
        lines += "largestPkg=$largestPkg"

        val cases = listOf(
            Case("1c_common", listOf("的"), false, false),
            Case("2c_common", listOf("微信"), false, false),
            Case("2c_rare", listOf("鎦迹"), false, false),
            Case("3c_plus_common", listOf("微信支付"), false, false),
            Case("3c_plus_rare", listOf("留迹画面"), false, false),
            Case("ascii_mixed", listOf("WeChat"), false, false),
            Case("strict_wechat", listOf("微信"), true, false),
            Case("multi_and", listOf("微信", "支付"), false, true),
            Case("multi_or", listOf("微信", "抖音"), false, false),
        )

        for (scopeName in listOf("ALL", "PKG")) {
            val scopePkgs = if (scopeName == "ALL") null else largestPkg?.let { setOf(it) }
            if (scopeName == "PKG" && scopePkgs == null) continue
            for (case in cases) {
                progress("基准 ${case.name}/$scopeName")
                lines += runCase(repo, case, scopeName, scopePkgs)
                File(app.filesDir, REPORT_FILE).writeText(lines.joinToString("\n"))
            }
        }

        lines += measureWriteOverhead(app)
        val report = lines.joinToString("\n")
        AppLog.i(TAG, report)
        File(app.filesDir, REPORT_FILE).writeText(report)
        AppLog.i(TAG, "报告已写入 files/$REPORT_FILE")
        progress("基准完成")
    }

    private suspend fun runCase(
        repo: UsageRepository,
        case: Case,
        scopeName: String,
        scopePkgs: Set<String>?,
    ): String {
        val label = "${case.name}/$scopeName"
        System.gc()

        var legacyCold = -1L
        var legacyHotP50 = -1L
        runCatching {
            legacyCold = measureTimeMillis {
                repo.searchFramesLegacy(
                    query = case.terms.first(),
                    scopePackages = scopePkgs,
                    strict = case.strict,
                    terms = case.terms,
                    matchAll = case.matchAll,
                    allowFrameId = false,
                    maxHits = COMPARE_LIMIT,
                )
            }
            val hot = LongArray(2) {
                measureTimeMillis {
                    repo.searchFramesLegacy(
                        query = case.terms.first(),
                        scopePackages = scopePkgs,
                        strict = case.strict,
                        terms = case.terms,
                        matchAll = case.matchAll,
                        allowFrameId = false,
                        maxHits = COMPARE_LIMIT,
                    )
                }
            }
            legacyHotP50 = p50(hot)
        }.onFailure { e -> AppLog.w(TAG, "$label legacy 失败: ${e.message}", e) }

        var pageCold = -1L
        var countMs = -1L
        var nextPageMs = -1L
        var pageHotP50 = -1L
        var pageN = 0
        var setDiff = -1 to -1
        var orderOk = false
        var legacyHead = 0
        var ftsHead = 0

        runCatching {
            var nextCursor: FrameSearchCursor? = null
            pageCold = measureTimeMillis {
                val page = repo.searchFramesPage(
                    query = case.terms.first(),
                    scopePackages = scopePkgs,
                    strict = case.strict,
                    terms = case.terms,
                    matchAll = case.matchAll,
                    allowFrameId = false,
                    cursor = null,
                    limit = UsageRepository.FRAME_SEARCH_PAGE_SIZE,
                )
                pageN = page.hits.size
                nextCursor = page.nextCursor
            }
            // 计数很慢时单独标出；限制最长感知：仍跑完整 COUNT（计划要求）
            countMs = measureTimeMillis {
                repo.countFrameHits(
                    query = case.terms.first(),
                    scopePackages = scopePkgs,
                    strict = case.strict,
                    terms = case.terms,
                    matchAll = case.matchAll,
                    allowFrameId = false,
                )
            }
            val pageHot = LongArray(3) {
                measureTimeMillis {
                    repo.searchFramesPage(
                        query = case.terms.first(),
                        scopePackages = scopePkgs,
                        strict = case.strict,
                        terms = case.terms,
                        matchAll = case.matchAll,
                        allowFrameId = false,
                        cursor = null,
                        limit = UsageRepository.FRAME_SEARCH_PAGE_SIZE,
                    )
                }
            }
            pageHotP50 = p50(pageHot)
            if (nextCursor != null) {
                nextPageMs = measureTimeMillis {
                    repo.searchFramesPage(
                        query = case.terms.first(),
                        scopePackages = scopePkgs,
                        strict = case.strict,
                        terms = case.terms,
                        matchAll = case.matchAll,
                        allowFrameId = false,
                        cursor = nextCursor,
                        limit = UsageRepository.FRAME_SEARCH_PAGE_SIZE,
                    )
                }
            }

            val ftsIds = ArrayList<Long>(COMPARE_LIMIT)
            var cursor: FrameSearchCursor? = null
            var guard = 0
            while (ftsIds.size < COMPARE_LIMIT && guard++ < 200) {
                val page = repo.searchFramesPage(
                    query = case.terms.first(),
                    scopePackages = scopePkgs,
                    strict = case.strict,
                    terms = case.terms,
                    matchAll = case.matchAll,
                    allowFrameId = false,
                    cursor = cursor,
                    limit = UsageRepository.FRAME_SEARCH_PAGE_SIZE,
                )
                ftsIds += page.hits.map { it.frameId }
                cursor = page.nextCursor ?: break
            }
            while (ftsIds.size > COMPARE_LIMIT) ftsIds.removeAt(ftsIds.lastIndex)

            val legacyIds = repo.searchFramesLegacy(
                query = case.terms.first(),
                scopePackages = scopePkgs,
                strict = case.strict,
                terms = case.terms,
                matchAll = case.matchAll,
                allowFrameId = false,
                maxHits = COMPARE_LIMIT,
            ).map { it.frameId }

            legacyHead = legacyIds.size
            ftsHead = ftsIds.size
            setDiff =
                (legacyIds.toSet() - ftsIds.toSet()).size to (ftsIds.toSet() - legacyIds.toSet()).size
            val n = minOf(legacyIds.size, ftsIds.size)
            orderOk = n > 0 && legacyIds.take(n) == ftsIds.take(n)
        }.onFailure { e -> AppLog.w(TAG, "$label fts 失败: ${e.message}", e) }

        return buildString {
            append("CASE $label")
            append(" legacyCold=${legacyCold}ms")
            append(" legacyHotP50=${legacyHotP50}ms")
            append(" pageCold=${pageCold}ms")
            append(" pageHotP50=${pageHotP50}ms")
            append(" count=${countMs}ms")
            append(" nextPage=${nextPageMs}ms")
            append(" compareN=$COMPARE_LIMIT")
            append(" legacyHead=$legacyHead")
            append(" ftsHead=$ftsHead")
            append(" pageN=$pageN")
            append(" setDiff=${setDiff.first}/${setDiff.second}")
            append(" orderOk=$orderOk")
        }.also { AppLog.i(TAG, it) }
    }

    private suspend fun measureWriteOverhead(app: Context): String {
        val db = UsageDatabase.getInstance(app)
        val sample = db.usageDao().getFrameById(1) ?: return "writeOverhead=skip(no frame)"
        val indexer = FrameFtsIndexer.get(app)
        val plain = LongArray(5) {
            measureTimeMillis {
                if (indexer.isAvailable) {
                    indexer.updateFrameIndex(
                        db.openHelper.writableDatabase,
                        sample.id,
                        sample.textsJson,
                    )
                }
            }
        }
        return "writeIndexP50=${p50(plain)}ms"
    }

    private suspend fun findLargestPackage(app: Context): String? {
        val dao = UsageDatabase.getInstance(app).usageDao()
        var best: String? = null
        var bestN = 0L
        for (p in dao.distinctFramePackages()) {
            val n = dao.countFramesForPackage(p)
            if (n > bestN) {
                bestN = n
                best = p
            }
        }
        return best
    }

    private fun p50(arr: LongArray): Long {
        if (arr.isEmpty()) return 0
        return arr.sorted()[arr.size / 2]
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService<NotificationManager>() ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "帧搜索基准", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun buildNotif(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("留迹基准")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

    private data class Case(
        val name: String,
        val terms: List<String>,
        val strict: Boolean,
        val matchAll: Boolean,
    )

    companion object {
        private const val TAG = "FrameSearchBench"
        private const val REPORT_FILE = "bench_search.txt"
        private const val COMPARE_LIMIT = 200
        private const val CHANNEL = "frame_search_bench"
        private const val NOTIF_ID = 49021
    }
}
