package com.liuji.xiaoqi.ima

import android.content.Context
import com.liuji.xiaoqi.data.StatsPeriod
import com.liuji.xiaoqi.data.UsageRepository
import com.liuji.xiaoqi.data.export.UsageJsonExporter
import com.liuji.xiaoqi.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ImaSyncReport(
    val ok: Boolean,
    val message: String,
    val daysSynced: Int = 0,
)

class ImaSyncRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = ImaSyncPrefs(app)
    private val usageRepo = UsageRepository(app)
    private val zone = ZoneId.systemDefault()

    /**
     * @param forceOverwrite true=立即同步：已上传日也重新覆盖上传
     */
    suspend fun syncIncremental(forceOverwrite: Boolean = false): ImaSyncReport =
        withContext(Dispatchers.IO) {
            if (!syncMutex.tryLock()) {
                return@withContext ImaSyncReport(false, "同步已在进行中")
            }
            try {
                doSync(forceOverwrite)
            } finally {
                syncMutex.unlock()
            }
        }

    private suspend fun doSync(forceOverwrite: Boolean): ImaSyncReport {
        val cfg = prefs.loadConfig()
        AppLog.i(TAG, "sync start kb=${cfg.knowledgeBaseName} force=$forceOverwrite")
        if (cfg.clientId.isBlank() || cfg.apiKey.isBlank() || cfg.knowledgeBaseName.isBlank()) {
            val msg = "请先填写应用 ID、访问密钥与知识库名称"
            prefs.setLastError(msg)
            return ImaSyncReport(false, msg)
        }
        val client = ImaApiClient(cfg.clientId, cfg.apiKey)
        var kbId = ensureKbId(client, cfg) ?: run {
            val msg = prefs.loadConfig().lastError.ifBlank { "无法准备知识库" }
            return ImaSyncReport(false, msg)
        }
        var kbRefreshed = false

        runCatching {
            usageRepo.syncFromSystem(untilMillis = System.currentTimeMillis())
        }.onFailure { AppLog.w(TAG, "syncFromSystem", it) }

        val today = LocalDate.now(zone)
        var uploaded = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        // 仅已完成日：yesterday … lookback（不含 today）
        for (i in 1 until LOOKBACK_DAYS + 1) {
            val date = today.minusDays(i.toLong())
            val dateKey = date.toString()
            val state = prefs.getDayState(dateKey)
            if (!forceOverwrite && state?.isFileSynced == true) {
                skipped++
                continue
            }
            try {
                if (uploaded > 0) Thread.sleep(DAY_GAP_MS)
                val mediaId = uploadOneDay(client, kbId, date)
                prefs.putDayState(
                    dateKey,
                    ImaDaySyncState(
                        mediaId = mediaId,
                        fileSyncedAt = System.currentTimeMillis(),
                    ),
                )
                uploaded++
                AppLog.i(TAG, "uploaded $date mediaTail=${mediaId.takeLast(12)}")
            } catch (e: Exception) {
                val hint = e.message.orEmpty()
                AppLog.w(TAG, "sync day $date", e)
                if (!kbRefreshed && ImaApiClient.isInvalidKnowledgeBaseIdHint(hint)) {
                    AppLog.w(TAG, "stale knowledge_base_id; re-resolve by name")
                    val fresh = refreshKbId(client, cfg.knowledgeBaseName, previousId = kbId)
                    if (fresh != null) {
                        kbId = fresh
                        kbRefreshed = true
                        try {
                            if (uploaded > 0) Thread.sleep(DAY_GAP_MS)
                            val mediaId = uploadOneDay(client, kbId, date)
                            prefs.putDayState(
                                dateKey,
                                ImaDaySyncState(
                                    mediaId = mediaId,
                                    fileSyncedAt = System.currentTimeMillis(),
                                ),
                            )
                            uploaded++
                            AppLog.i(TAG, "uploaded $date after kb refresh mediaTail=${mediaId.takeLast(12)}")
                            continue
                        } catch (retry: Exception) {
                            AppLog.w(TAG, "sync day $date after kb refresh", retry)
                            errors += "$date: ${retry.message ?: retry}"
                            if (ImaApiClient.isRateLimitHint(retry.message.orEmpty())) {
                                errors += "请求频率受限，请稍后再试"
                                break
                            }
                            continue
                        }
                    }
                }
                errors += "$date: ${hint.ifBlank { e.toString() }}"
                if (ImaApiClient.isRateLimitHint(hint)) {
                    errors += "请求频率受限，请稍后再试"
                    break
                }
            }
        }

        return if (errors.isEmpty()) {
            val msg = when {
                uploaded > 0 && skipped > 0 -> "已上传 $uploaded 天，跳过已同步 $skipped 天"
                uploaded > 0 -> "已上传 $uploaded 天的使用数据"
                skipped > 0 -> "已是最新（$skipped 天已同步）"
                else -> "没有可同步的已完成日期"
            }
            prefs.setLastSuccess(msg)
            ImaSyncReport(true, msg, uploaded)
        } else if (uploaded > 0) {
            val msg = "部分成功：上传 $uploaded 天；失败：${errors.first()}"
            prefs.setLastError(msg)
            ImaSyncReport(true, msg, uploaded)
        } else {
            val msg = errors.first()
            prefs.setLastError(msg)
            ImaSyncReport(false, msg, 0)
        }
    }

    private fun ensureKbId(client: ImaApiClient, cfg: ImaSyncConfig): String? {
        val cached = cfg.knowledgeBaseId.trim()
        if (cached.isNotBlank() && ImaApiClient.isValidKnowledgeBaseId(cached)) {
            // 名称变更后缓存会清空；格式合法则先信任，失败时再 refreshKbId
            return cached
        }
        if (cached.isNotBlank()) {
            AppLog.w(TAG, "cached kb id rejected by format: ${cached.take(24)}")
            prefs.setKnowledgeBaseId("")
        }
        return resolveAndStoreKbId(client, cfg.knowledgeBaseName)
    }

    /** 清空缓存后按名称重新解析；ID 变化时清日状态，避免指向旧库的「已同步」跳过。 */
    private fun refreshKbId(
        client: ImaApiClient,
        knowledgeBaseName: String,
        previousId: String,
    ): String? {
        prefs.setKnowledgeBaseId("")
        val id = resolveAndStoreKbId(client, knowledgeBaseName) ?: return null
        if (id != previousId) {
            AppLog.i(TAG, "kb id changed; clear day sync states")
            prefs.clearAllDayStates()
        }
        return id
    }

    private fun resolveAndStoreKbId(client: ImaApiClient, knowledgeBaseName: String): String? {
        val (res, id) = client.ensureKnowledgeBase(knowledgeBaseName)
        if (!res.ok || id.isNullOrBlank()) {
            prefs.setLastError(res.error ?: res.errmsg ?: "无法创建或找到知识库")
            return null
        }
        if (!ImaApiClient.isValidKnowledgeBaseId(id)) {
            prefs.setLastError("知识库 ID 格式异常：$id")
            return null
        }
        prefs.setKnowledgeBaseId(id)
        return id
    }

    private suspend fun uploadOneDay(
        client: ImaApiClient,
        kbId: String,
        date: LocalDate,
    ): String {
        val today = LocalDate.now(zone)
        val offsetDays = ChronoUnit.DAYS.between(today, date).toInt() // 昨天 = -1
        val summary = usageRepo.loadSummary(StatsPeriod.DAY, offsetDays)
        val privacy = usageRepo.listPrivacyApps().map { it.packageName }.toSet()
        val frameTexts = usageRepo.loadExportFrameTextsForDateRange(
            date,
            date,
            excludePackages = privacy,
        )
        val timeline = usageRepo.loadTimelineForDateRange(date, date, sync = false)
        val markdown = UsageJsonExporter.toMarkdown(
            summary = summary,
            timeline = timeline,
            appLabelOf = usageRepo::appLabel,
            textsByApp = frameTexts.textsByApp,
            frameCount = frameTexts.frameCount,
        )
        val fileName = dayFileName(date)
        val dir = File(app.cacheDir, "ima_export").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(markdown, StandardCharsets.UTF_8)
        val (res, mediaId) = client.overwriteUploadMarkdownFile(kbId, file, fileName)
        if (!res.ok || mediaId.isNullOrBlank()) {
            throw IllegalStateException(res.error ?: res.errmsg ?: "上传失败")
        }
        return mediaId
    }

    companion object {
        private const val TAG = "ImaSync"
        private const val LOOKBACK_DAYS = 14
        private const val DAY_GAP_MS = 1500L
        private val syncMutex = Mutex()

        fun dayFileName(date: LocalDate): String = "留迹_$date.md"
    }
}
