package com.liuji.xiaoqi.ima

import android.content.Context
import com.liuji.xiaoqi.log.AppLog
import com.liuji.xiaoqi.data.StatsPeriod
import com.liuji.xiaoqi.data.UsageRepository
import com.liuji.xiaoqi.data.db.UsageDatabase
import com.liuji.xiaoqi.data.db.UiFrameEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
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
    private val dao = UsageDatabase.getInstance(app).usageDao()
    private val zone = ZoneId.systemDefault()

    suspend fun syncIncremental(): ImaSyncReport = withContext(Dispatchers.IO) {
        if (!syncMutex.tryLock()) {
            return@withContext ImaSyncReport(false, "同步已在进行中")
        }
        try {
            doSyncIncremental()
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun doSyncIncremental(): ImaSyncReport {
        val cfg = prefs.loadConfig()
        AppLog.i(TAG, "sync start kb=${cfg.knowledgeBaseName}")
        if (cfg.clientId.isBlank() || cfg.apiKey.isBlank() || cfg.knowledgeBaseName.isBlank()) {
            val msg = "请先填写应用 ID、访问密钥与知识库名称"
            prefs.setLastError(msg)
            return ImaSyncReport(false, msg)
        }
        val client = ImaApiClient(cfg.clientId, cfg.apiKey)
        val kbId = resolveKbId(client, cfg) ?: run {
            val msg = prefs.loadConfig().lastError.ifBlank { "找不到知识库，请检查名称是否一致" }
            return ImaSyncReport(false, msg)
        }

        // 确保本地有最近使用数据
        runCatching {
            usageRepo.syncFromSystem(untilMillis = System.currentTimeMillis())
        }.onFailure { AppLog.w(TAG, "syncFromSystem", it) }

        prefs.migrateUsageOffsetSignFix()
        val today = LocalDate.now(zone)
        var framesUploadedDays = 0
        var usageOnlyDays = 0
        var createdNotes = 0
        val errors = mutableListOf<String>()
        val budget = SyncBudget(MAX_APPEND_CALLS_PER_RUN)
        var truncated = false

        for (i in 0 until LOOKBACK_DAYS) {
            val date = today.minusDays(i.toLong())
            val dateKey = date.toString()
            val beforeState = prefs.getDayState(dateKey)
            try {
                if (i > 0) Thread.sleep(DAY_GAP_MS)
                val outcome = syncOneDay(client, kbId, date, today = today, budget = budget)
                when {
                    outcome.noteCreated -> createdNotes++
                    outcome.framesUploaded -> framesUploadedDays++
                    outcome.usageOnly -> usageOnlyDays++
                }
                if (budget.remaining <= 0) {
                    truncated = true
                    AppLog.i(TAG, "append budget exhausted, stop lookback")
                    break
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "sync day $date", e)
                val msg = e.message ?: e.toString()
                val afterState = prefs.getDayState(dateKey)
                val progressed = dayStateProgressed(beforeState, afterState)
                if (progressed) {
                    // 本批失败前已成功写入的游标仍保留；计入部分成功，便于再点续传
                    framesUploadedDays++
                    AppLog.i(
                        TAG,
                        "day $date 已保存游标 lastFrameId=${afterState?.lastFrameId}，可续传",
                    )
                }
                errors += "$date: $msg"
                if (isTransientLimit(msg)) {
                    errors += "临时受限，已保存进度，可稍后再点「立即同步」继续"
                    AppLog.w(TAG, "transient limit after retries, pause lookback (cursor kept)")
                    break
                }
            }
        }

        val daysTouched = framesUploadedDays + usageOnlyDays + createdNotes
        return if (errors.isEmpty()) {
            val msg = buildSuccessMessage(
                framesUploadedDays = framesUploadedDays,
                usageOnlyDays = usageOnlyDays,
                createdNotes = createdNotes,
                truncated = truncated,
            )
            prefs.setLastSuccess(msg)
            AppLog.i(
                TAG,
                "$msg (framesDays=$framesUploadedDays usageOnly=$usageOnlyDays created=$createdNotes)",
            )
            ImaSyncReport(true, msg, daysTouched)
        } else if (daysTouched > 0) {
            val msg = "部分成功：画面 ${framesUploadedDays} 天、新建 $createdNotes、用量 $usageOnlyDays；" +
                "失败：${errors.first()}（可再同步继续）"
            prefs.setLastError(msg)
            AppLog.w(TAG, msg)
            ImaSyncReport(false, msg, daysTouched)
        } else {
            val msg = buildFailureMessage(errors)
            prefs.setLastError(msg)
            AppLog.w(TAG, msg)
            ImaSyncReport(false, msg)
        }
    }

    private fun dayStateProgressed(before: ImaDaySyncState?, after: ImaDaySyncState?): Boolean {
        if (after == null) return false
        if (before == null) return after.noteId.isNotBlank()
        return after.lastFrameId > before.lastFrameId ||
            after.usageSyncedAt > before.usageSyncedAt ||
            (after.noteId.isNotBlank() && after.noteId != before.noteId)
    }

    private fun buildFailureMessage(errors: List<String>): String {
        val first = errors.firstOrNull() ?: "同步失败"
        return if (errors.any { isTransientLimit(it) }) {
            "$first（可稍后再点「立即同步」重试）"
        } else {
            first
        }
    }

    private fun isTransientLimit(msg: String): Boolean =
        ImaApiClient.isRateLimitHint(msg) || ImaApiClient.isRetryableHint(msg)

    private fun buildSuccessMessage(
        framesUploadedDays: Int,
        usageOnlyDays: Int,
        createdNotes: Int,
        truncated: Boolean,
    ): String {
        if (framesUploadedDays == 0 && usageOnlyDays == 0 && createdNotes == 0) {
            return "已是最新"
        }
        val parts = mutableListOf<String>()
        if (createdNotes > 0) parts += "新建 $createdNotes 篇笔记"
        if (framesUploadedDays > 0) parts += "更新 $framesUploadedDays 天画面记录"
        if (usageOnlyDays > 0) {
            parts += if (framesUploadedDays == 0 && createdNotes == 0) {
                if (usageOnlyDays == 1) "已刷新用量" else "已刷新 $usageOnlyDays 天用量"
            } else {
                "刷新 $usageOnlyDays 天用量"
            }
        }
        val base = parts.joinToString("，")
        return if (truncated) {
            "$base（内容较多，可再点「立即同步」继续）"
        } else {
            base
        }
    }

    /** 单次同步允许的 append/import 次数，避免积压帧把 UI 卡死 */
    private class SyncBudget(var remaining: Int) {
        fun take(): Boolean {
            if (remaining <= 0) return false
            remaining--
            return true
        }
    }

    private fun resolveKbId(client: ImaApiClient, cfg: ImaSyncConfig): String? {
        val cached = cfg.knowledgeBaseId
        if (cached.isNotBlank()) return cached
        val (res, id) = client.resolveKnowledgeBaseId(cfg.knowledgeBaseName)
        if (!res.ok || id.isNullOrBlank()) {
            prefs.setLastError(res.error ?: res.errmsg ?: "无法找到知识库")
            return null
        }
        prefs.setKnowledgeBaseId(id)
        return id
    }

    private data class DaySyncOutcome(
        val framesUploaded: Boolean = false,
        val usageOnly: Boolean = false,
        val noteCreated: Boolean = false,
    )

    /**
     * 历史天只在有新帧时同步；用量周期性刷新仅限今天与昨天。
     */
    private suspend fun syncOneDay(
        client: ImaApiClient,
        kbId: String,
        date: LocalDate,
        today: LocalDate,
        allowRecreateOnce: Boolean = true,
        budget: SyncBudget,
    ): DaySyncOutcome {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val title = ImaDailyPayload.noteTitle(date)
        var dayState = prefs.getDayState(date.toString())

        // 与 UsageViewModel 一致：今天=0，昨天=-1（rangeFor 用 today.plusDays(offset)）
        val offsetDays = ChronoUnit.DAYS.between(today, date).toInt()
        val afterId = dayState?.lastFrameId ?: 0L
        val frames = dao.getFramesInRangeAfterId(start, end, afterId, FRAME_BATCH)

        val allowUsageRefresh = !date.isBefore(today.minusDays(USAGE_REFRESH_LOOKBACK_DAYS))
        val usageStale = dayState == null || dayState.usageSyncedAt == 0L ||
            (System.currentTimeMillis() - dayState.usageSyncedAt > USAGE_REFRESH_MS)
        val needUsage = allowUsageRefresh && usageStale
        val needFrames = frames.isNotEmpty()

        val summary = runCatching {
            usageRepo.loadSummary(StatsPeriod.DAY, offsetDays)
        }.getOrNull()

        // 必须以「是否仍在目标知识库」为准；仅笔记本 search 命中不能当已同步
        // （用户从知识库删文件后，笔记可能仍在笔记本里）
        if (dayState != null) {
            val inKb = client.isNoteInKnowledgeBase(kbId, title, dayState.noteId)
            AppLog.i(
                TAG,
                "probe $date inKb=$inKb needUsage=$needUsage needFrames=$needFrames " +
                    "batch=${frames.size} afterId=$afterId idTail=${dayState.noteId.takeLast(8)}",
            )
            if (!inKb) {
                AppLog.i(TAG, "kb missing $date → clear local state and recreate")
                prefs.clearDayState(date.toString())
                dayState = null
            } else if (!needUsage && !needFrames) {
                AppLog.i(TAG, "skip $date afterId=$afterId (up to date, inKb)")
                return DaySyncOutcome()
            }
        }

        if (dayState == null) {
            AppLog.i(TAG, "create note $date pendingFrames≈ batch=${frames.size}")
            val created = createDayNote(client, kbId, date, title, start, end, summary, budget)
            return if (created) DaySyncOutcome(noteCreated = true) else DaySyncOutcome()
        }

        // 已有笔记：追加
        val noteId = dayState.noteId
        var lastId = dayState.lastFrameId
        var framesUploaded = false
        var usageOnly = false

        try {
            if (needUsage && !needFrames) {
                if (!budget.take()) return DaySyncOutcome()
                val timeline = runCatching {
                    usageRepo.loadDayTimeline(offsetDays, sync = false)
                }.getOrElse { emptyList() }
                val md = ImaDailyPayload.appendMarkdown(
                    summary,
                    emptyList(),
                    includeUsageRefresh = true,
                    timeline = timeline,
                    appLabelOf = usageRepo::appLabel,
                )
                AppLog.i(TAG, "append $date 仅用量")
                val res = client.appendDoc(noteId, md)
                if (!res.ok) {
                    AppLog.e(TAG, "appendDoc 失败(仅用量) $date: ${res.error ?: res.errmsg}")
                    throw IllegalStateException(res.error ?: "追加内容失败")
                }
                prefs.putDayState(
                    date.toString(),
                    dayState.copy(usageSyncedAt = System.currentTimeMillis()),
                )
                usageOnly = true
            }

            var batch = if (needFrames) frames else emptyList()
            var firstBatch = true
            var batchIndex = 0
            val timelineForRefresh = if (needUsage) {
                runCatching { usageRepo.loadDayTimeline(offsetDays, sync = false) }
                    .getOrElse { emptyList() }
            } else {
                emptyList()
            }
            while (batch.isNotEmpty()) {
                if (budget.remaining <= 0) {
                    AppLog.i(TAG, "day $date pause at lastId=$lastId (budget)")
                    break
                }
                batchIndex++
                val dictEntries = ImaDailyPayload.buildTextDictionary(batch, usageRepo::appLabel).length()
                val withUsage = firstBatch && needUsage
                AppLog.i(
                    TAG,
                    "append $date batch#$batchIndex 含文本字典 frames=${batch.size} dict=$dictEntries " +
                        "usage=$withUsage id=${batch.first().id}..${batch.last().id} " +
                        "budgetLeft=${budget.remaining}",
                )
                appendFrames(
                    client,
                    noteId,
                    summary = if (withUsage) summary else null,
                    frames = batch,
                    includeUsage = withUsage,
                    budget = budget,
                    timeline = if (withUsage) timelineForRefresh else emptyList(),
                )
                lastId = batch.last().id
                prefs.putDayState(
                    date.toString(),
                    ImaDaySyncState(
                        noteId = noteId,
                        lastFrameId = lastId,
                        usageSyncedAt = if (withUsage) {
                            System.currentTimeMillis()
                        } else {
                            dayState.usageSyncedAt
                        },
                    ),
                )
                framesUploaded = true
                usageOnly = false
                firstBatch = false
                if (budget.remaining <= 0) break
                batch = dao.getFramesInRangeAfterId(start, end, lastId, FRAME_BATCH)
            }
            AppLog.i(
                TAG,
                "day $date done frames=$framesUploaded usageOnly=$usageOnly " +
                    "lastId=$lastId budget=${budget.remaining}",
            )
            return DaySyncOutcome(framesUploaded = framesUploaded, usageOnly = usageOnly)
        } catch (e: IllegalStateException) {
            val msg = e.message.orEmpty()
            // 限流/403 等：游标已在成功 batch 后写入，原样抛出由外层提示续传
            if (isTransientLimit(msg)) {
                AppLog.w(
                    TAG,
                    "day $date 临时失败（保留 lastFrameId=$lastId framesUploaded=$framesUploaded）: $msg",
                )
                throw e
            }
            if (allowRecreateOnce && ImaApiClient.isNoteMissingHint(msg)) {
                AppLog.w(TAG, "append/kb 不可用 $date → clear and recreate once", e)
                prefs.clearDayState(date.toString())
                return syncOneDay(
                    client,
                    kbId,
                    date,
                    today = today,
                    allowRecreateOnce = false,
                    budget = budget,
                )
            }
            throw e
        }
    }

    private suspend fun createDayNote(
        client: ImaApiClient,
        kbId: String,
        date: LocalDate,
        title: String,
        start: Long,
        end: Long,
        summary: com.liuji.xiaoqi.data.PeriodSummary?,
        budget: SyncBudget,
    ): Boolean {
        if (!budget.take()) return false
        val firstBatch = dao.getFramesInRangeAfterId(start, end, 0L, FRAME_BATCH)
        val offsetDays = ChronoUnit.DAYS.between(LocalDate.now(zone), date).toInt()
        val timeline = runCatching {
            usageRepo.loadDayTimeline(offsetDays, sync = false)
        }.getOrElse { emptyList() }
        var md = ImaDailyPayload.createMarkdown(
            date,
            summary,
            firstBatch,
            timeline = timeline,
            appLabelOf = usageRepo::appLabel,
        )
        val droppedFirstBatch = ImaDailyPayload.estimateBytes(md) > MAX_CHUNK_BYTES
        if (droppedFirstBatch) {
            md = ImaDailyPayload.createMarkdown(
                date,
                summary,
                emptyList(),
                timeline = timeline,
                appLabelOf = usageRepo::appLabel,
            )
        }
        val (importRes, noteId) = client.importDoc(title, md)
        if (!importRes.ok || noteId.isNullOrBlank()) {
            throw IllegalStateException(importRes.error ?: "创建笔记失败")
        }
        if (!budget.take()) {
            // 已建笔记，至少记下状态，帧下次再续
            prefs.putDayState(
                date.toString(),
                ImaDaySyncState(
                    noteId = noteId,
                    lastFrameId = if (droppedFirstBatch) 0L else (firstBatch.maxOfOrNull { it.id } ?: 0L),
                    usageSyncedAt = System.currentTimeMillis(),
                ),
            )
            return true
        }
        val addRes = client.addNoteToKnowledgeBase(kbId, noteId, title)
        if (!addRes.ok) {
            throw IllegalStateException(addRes.error ?: "加入知识库失败")
        }
        var lastId = if (droppedFirstBatch) 0L else (firstBatch.maxOfOrNull { it.id } ?: 0L)
        var more = dao.getFramesInRangeAfterId(start, end, lastId, FRAME_BATCH)
        while (more.isNotEmpty() && budget.remaining > 0) {
            appendFrames(client, noteId, null, more, includeUsage = false, budget = budget)
            lastId = more.last().id
            prefs.putDayState(
                date.toString(),
                ImaDaySyncState(
                    noteId = noteId,
                    lastFrameId = lastId,
                    usageSyncedAt = System.currentTimeMillis(),
                ),
            )
            if (budget.remaining <= 0) break
            more = dao.getFramesInRangeAfterId(start, end, lastId, FRAME_BATCH)
        }
        prefs.putDayState(
            date.toString(),
            ImaDaySyncState(
                noteId = noteId,
                lastFrameId = lastId,
                usageSyncedAt = System.currentTimeMillis(),
            ),
        )
        return true
    }

    private fun appendFrames(
        client: ImaApiClient,
        noteId: String,
        summary: com.liuji.xiaoqi.data.PeriodSummary?,
        frames: List<UiFrameEntity>,
        includeUsage: Boolean,
        budget: SyncBudget,
        timeline: List<com.liuji.xiaoqi.data.frame.TimelineItem> = emptyList(),
    ) {
        if (budget.remaining <= 0) return
        val md = ImaDailyPayload.appendMarkdown(
            summary,
            frames,
            includeUsageRefresh = includeUsage,
            timeline = if (includeUsage) timeline else emptyList(),
            appLabelOf = usageRepo::appLabel,
        )
        if (ImaDailyPayload.estimateBytes(md) > MAX_CHUNK_BYTES && frames.size > 1) {
            val mid = frames.size / 2
            appendFrames(
                client,
                noteId,
                summary,
                frames.subList(0, mid),
                includeUsage,
                budget,
                timeline = timeline,
            )
            if (budget.remaining <= 0) return
            appendFrames(
                client,
                noteId,
                null,
                frames.subList(mid, frames.size),
                includeUsage = false,
                budget = budget,
            )
            return
        }
        if (!budget.take()) return
        val res = client.appendDoc(noteId, md)
        if (!res.ok) {
            AppLog.e(TAG, "appendDoc 失败(含文本字典) noteTail=${noteId.takeLast(8)}: ${res.error ?: res.errmsg}")
            throw IllegalStateException(res.error ?: "追加内容失败")
        }
    }

    companion object {
        private const val TAG = "ImaSync"
        private const val LOOKBACK_DAYS = 14
        /** 字典体积通常远小于整帧；提高批次以减少 HTTP 往返 */
        private const val FRAME_BATCH = 40
        private const val MAX_CHUNK_BYTES = 32_000
        /** 单次「立即同步」最多 append/import 次数，避免积压帧卡死 UI */
        private const val MAX_APPEND_CALLS_PER_RUN = 25
        private const val USAGE_REFRESH_MS = 60 * 60 * 1000L
        /** 仅今天与昨天允许周期性刷新用量；更早的日期只在有新帧时同步 */
        private const val USAGE_REFRESH_LOOKBACK_DAYS = 1L
        private const val DAY_GAP_MS = 1_500L
        private val syncMutex = Mutex()
    }
}
