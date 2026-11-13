package com.liuji.xiaoqi.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteException
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.getSystemService
import com.liuji.xiaoqi.log.AppLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import com.liuji.xiaoqi.data.db.AppMetaEntity
import com.liuji.xiaoqi.data.db.AppSettingsEntity
import com.liuji.xiaoqi.data.db.PrivacyAppEntity
import com.liuji.xiaoqi.data.db.SyncStateEntity
import com.liuji.xiaoqi.data.db.UsageDatabase
import com.liuji.xiaoqi.data.db.UsageSessionEntity
import com.liuji.xiaoqi.data.db.UiFrameEntity
import com.liuji.xiaoqi.data.frame.FrameFtsIndexer
import com.liuji.xiaoqi.data.frame.FrameFtsQuery
import com.liuji.xiaoqi.data.frame.FrameInterval
import com.liuji.xiaoqi.data.frame.FrameJson
import com.liuji.xiaoqi.data.frame.FrameSearchCursor
import com.liuji.xiaoqi.data.frame.FrameSearchHit
import com.liuji.xiaoqi.data.frame.FrameSearchPage
import com.liuji.xiaoqi.data.frame.TimelineItem
import com.liuji.xiaoqi.data.frame.UiFrameSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.math.min

class UsageRepository(private val context: Context) {

    private val usageStatsManager: UsageStatsManager? =
        context.getSystemService()

    private val packageManager: PackageManager = context.packageManager
    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val dao = UsageDatabase.getInstance(context).usageDao()
    private val categoryRepository = CategoryRepository(context)
    private val categoryResolver = CategoryResolver(context)

    val categories: CategoryRepository get() = categoryRepository

    private val labelCache = mutableMapOf<String, String>()
    private val iconCacheLock = Any()
    private val iconCache = object : LinkedHashMap<String, android.graphics.drawable.Drawable?>(64, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, android.graphics.drawable.Drawable?>?,
        ): Boolean = size > MAX_ICON_CACHE
    }

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService<AppOpsManager>() ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /**
     * 先把系统 UsageEvents 增量写入 SQLite，再从数据库聚合看板数据。
     */
    suspend fun loadSummary(period: StatsPeriod, offset: Int): PeriodSummary {
        val today = LocalDate.now(zoneId)
        val (start, endInclusive) = rangeFor(period, today, offset)
        return loadSummaryForDateRange(start, endInclusive, periodHint = period)
    }

    /** 自由日期区间汇总（导出用）；单日走 DAY 结构，多日走 WEEK（含每日明细） */
    suspend fun loadSummaryForDateRange(
        start: LocalDate,
        endInclusive: LocalDate,
        periodHint: StatsPeriod? = null,
    ): PeriodSummary {
        require(!endInclusive.isBefore(start)) { "结束日期不能早于开始日期" }
        val today = LocalDate.now(zoneId)
        val startMillis = start.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endExclusive = endInclusive.plusDays(1)
        val endMillis = min(
            endExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            System.currentTimeMillis(),
        )

        syncFromSystem(
            ensureRangeStart = startMillis,
            untilMillis = System.currentTimeMillis(),
        )
        categoryResolver.refresh()

        dao.getAllAppMeta().forEach { meta ->
            labelCache.putIfAbsent(meta.packageName, meta.label)
        }

        val rows = dao.getSessionsOverlapping(startMillis, endMillis)
        val sessions = rows.mapNotNull { row ->
            val begin = max(row.startMillis, startMillis)
            val end = min(row.endMillis, endMillis)
            if (end > begin) Session(row.packageName, begin, end) else null
        }

        val period = periodHint ?: if (start == endInclusive) StatsPeriod.DAY else StatsPeriod.WEEK
        return buildSummary(period, today, start, endInclusive, sessions)
    }

    /**
     * 增量同步系统使用记录到本地 SQL。
     * - 首次：回溯 [INITIAL_LOOKBACK_DAYS] 天
     * - 之后：从上次水位前 [SYNC_OVERLAP_MILLIS] 开始，覆盖重写该区间，保证幂等
     * - 采集窗口额外前移 [COLLECT_LOOKBACK_MILLIS]（至少 1 天），避免跨午夜会话
     *   的 RESUMED 落在窗外导致孤儿 PAUSE 被丢弃（对齐 ScreenTime Glance）
     * - 重写下界取系统实际返回的最早事件（相对 syncStart，不含 lookback 日）：
     *   UsageEvents 只保留近若干天，不可用更长的 collectStart 清空本地更早历史
     * - 无事件 / 采集失败：不删本地、不推进水位
     */
    suspend fun syncFromSystem(
        ensureRangeStart: Long? = null,
        untilMillis: Long = System.currentTimeMillis(),
    ) {
        if (!hasUsageAccess()) return
        syncMutex.withLock {
            syncFromSystemLocked(ensureRangeStart, untilMillis)
        }
    }

    private suspend fun syncFromSystemLocked(
        ensureRangeStart: Long?,
        untilMillis: Long,
    ) {
        categoryRepository.ensureSeeded()
        categoryResolver.refresh()
        val existingMeta = dao.getAllAppMeta().associateBy { it.packageName }

        val lastSynced = dao.getLastSyncedMillis() ?: 0L
        val defaultStart = if (lastSynced == 0L) {
            untilMillis - INITIAL_LOOKBACK_DAYS * DAY_MILLIS
        } else {
            lastSynced - SYNC_OVERLAP_MILLIS
        }
        val syncStart = min(
            defaultStart,
            ensureRangeStart ?: defaultStart,
        ).coerceAtLeast(0L)

        if (untilMillis <= syncStart) return

        // 多查至少一天，才能配对「昨日 RESUMED → 今日 PAUSE」
        val collectStart = (syncStart - COLLECT_LOOKBACK_MILLIS).coerceAtLeast(0L)
        val collected = collectSessions(collectStart, untilMillis)
        val earliest = collected.earliestEventMillis
        if (earliest == null) {
            AppLog.w(TAG, "no usage events; skip rewrite and watermark")
            return
        }
        val rewriteStart = max(syncStart, earliest)
        if (untilMillis <= rewriteStart) return

        val entities = collected.sessions.mapNotNull { session ->
            val start = max(session.startMillis, rewriteStart)
            val end = min(session.endMillis, untilMillis)
            if (end <= start) return@mapNotNull null
            UsageSessionEntity(
                packageName = session.packageName,
                startMillis = start,
                endMillis = end,
                categoryId = categoryResolver.resolve(session.packageName),
            )
        }
        // 按包合并：系统未返回的包（如被隐藏抹掉的抖音）保留本地会话
        dao.mergeSessionsInRange(rewriteStart, untilMillis, entities)

        val now = System.currentTimeMillis()
        val metas = collected.sessions
            .map { it.packageName }
            .distinct()
            .map { pkg ->
                val existing = existingMeta[pkg]
                val label = resolveLabel(pkg)
                if (existing?.categoryUser == true) {
                    AppMetaEntity(
                        packageName = pkg,
                        label = label,
                        categoryId = existing.categoryId,
                        categoryUser = true,
                        updatedAt = now,
                    )
                } else {
                    AppMetaEntity(
                        packageName = pkg,
                        label = label,
                        categoryId = categoryResolver.resolve(pkg),
                        categoryUser = false,
                        updatedAt = now,
                    )
                }
            }
        if (metas.isNotEmpty()) {
            dao.upsertAppMeta(metas)
        }
        categoryResolver.refresh()

        dao.upsertSyncState(SyncStateEntity(lastSyncedMillis = untilMillis))
    }

    private fun buildSummary(
        period: StatsPeriod,
        today: LocalDate,
        start: LocalDate,
        endInclusive: LocalDate,
        sessions: List<Session>,
    ): PeriodSummary {
        val appMillis = LinkedHashMap<String, Long>()
        val categoryMillis = LinkedHashMap<String, Long>()
        val categoryTopApps = LinkedHashMap<String, MutableMap<String, Long>>()
        val hourCategory = Array(24) { LinkedHashMap<String, Long>() }
        val hourApps = Array(24) { LinkedHashMap<String, Long>() }
        val dayCategory = linkedMapOf<LocalDate, LinkedHashMap<String, Long>>()

        var dayCursor = start
        while (!dayCursor.isAfter(endInclusive)) {
            dayCategory[dayCursor] = LinkedHashMap()
            dayCursor = dayCursor.plusDays(1)
        }

        for (session in sessions) {
            val duration = session.endMillis - session.startMillis
            if (duration <= 0L) continue

            appMillis[session.packageName] =
                (appMillis[session.packageName] ?: 0L) + duration

            val catId = categoryResolver.resolve(session.packageName)
            categoryMillis[catId] = (categoryMillis[catId] ?: 0L) + duration
            categoryTopApps
                .getOrPut(catId) { LinkedHashMap() }
                .merge(session.packageName, duration, Long::plus)

            splitAcrossHours(session.startMillis, session.endMillis) { hour, millis ->
                hourCategory[hour][catId] =
                    (hourCategory[hour][catId] ?: 0L) + millis
                hourApps[hour][session.packageName] =
                    (hourApps[hour][session.packageName] ?: 0L) + millis
            }
            splitAcrossDays(session.startMillis, session.endMillis) { date, millis ->
                dayCategory[date]?.let { map ->
                    map[catId] = (map[catId] ?: 0L) + millis
                }
            }
        }

        val apps = appMillis.entries
            .sortedByDescending { it.value }
            .map { (pkg, millis) ->
                val catId = categoryResolver.resolve(pkg)
                val cat = categoryResolver.displayCategory(catId)
                AppUsageItem(
                    packageName = pkg,
                    label = resolveLabel(pkg),
                    categoryId = catId,
                    millis = millis,
                    categoryName = cat.name,
                    categoryColor = cat.color,
                )
            }

        val categories = categoryMillis.entries
            .sortedByDescending { it.value }
            .map { (catId, millis) ->
                val cat = categoryResolver.displayCategory(catId)
                val tops = categoryTopApps[catId]
                    ?.entries
                    ?.sortedByDescending { it.value }
                    ?.take(3)
                    ?.map { it.key }
                    .orEmpty()
                CategoryUsageItem(
                    categoryId = catId,
                    name = cat.name,
                    color = cat.color,
                    millis = millis,
                    topPackages = tops,
                )
            }

        val hours = (0 until 24).map { hour ->
            HourSlot(
                hour = hour,
                slices = hourCategory[hour].entries
                    .sortedByDescending { it.value }
                    .map { CategorySlice(it.key, it.value) },
            )
        }

        // 周/月不展示尚未到来的日期
        val dayBuckets = dayCategory.entries
            .filter { (date, _) -> !date.isAfter(today) }
            .map { (date, map) ->
                DayBucket(
                    date = date,
                    slices = map.entries
                        .sortedByDescending { it.value }
                        .map { CategorySlice(it.key, it.value) },
                )
            }

        val total = appMillis.values.sum()
        val elapsedDayCount = dayBuckets
            .count { !it.date.isAfter(today) }
            .coerceAtLeast(1)
        val average = if (period == StatsPeriod.DAY) total else total / elapsedDayCount

        val appsByHour = (0 until 24).associateWith { hour ->
            hourApps[hour].entries
                .sortedByDescending { it.value }
                .map { (pkg, millis) ->
                    val catId = categoryResolver.resolve(pkg)
                    val cat = categoryResolver.displayCategory(catId)
                    AppUsageItem(
                        packageName = pkg,
                        label = resolveLabel(pkg),
                        categoryId = catId,
                        millis = millis,
                        categoryName = cat.name,
                        categoryColor = cat.color,
                    )
                }
        }

        return PeriodSummary(
            period = period,
            anchorDate = when (period) {
                StatsPeriod.DAY -> start
                StatsPeriod.WEEK -> start
                StatsPeriod.MONTH -> start.withDayOfMonth(1)
            },
            rangeStart = start,
            rangeEnd = endInclusive,
            totalMillis = total,
            averageDailyMillis = average,
            hours = hours,
            dayBuckets = dayBuckets,
            apps = apps,
            categories = categories,
            appsByHour = appsByHour,
        )
    }

    private fun rangeFor(
        period: StatsPeriod,
        today: LocalDate,
        offset: Int,
    ): Pair<LocalDate, LocalDate> = when (period) {
        StatsPeriod.DAY -> {
            val day = today.plusDays(offset.toLong())
            day to day
        }

        StatsPeriod.WEEK -> {
            val weekStart = today
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .plusWeeks(offset.toLong())
            weekStart to weekStart.plusDays(6)
        }

        StatsPeriod.MONTH -> {
            val monthStart = today.withDayOfMonth(1).plusMonths(offset.toLong())
            monthStart to monthStart.with(TemporalAdjusters.lastDayOfMonth())
        }
    }

    private data class Session(
        val packageName: String,
        val startMillis: Long,
        val endMillis: Long,
    )

    private data class OpenActivity(
        val packageName: String,
        val startMillis: Long,
    )

    private data class CollectedSessions(
        val sessions: List<Session>,
        /** 系统返回的最早一条事件时间；null 表示窗内没有任何事件 */
        val earliestEventMillis: Long?,
    )

    /**
     * 从 UsageEvents 构建前台会话。
     * open 表按 className（缺省 package）配对，对齐 ScreenTime；区间归到 packageName。
     */
    private fun collectSessions(startMillis: Long, endMillis: Long): CollectedSessions {
        val manager = usageStatsManager
            ?: return CollectedSessions(emptyList(), earliestEventMillis = null)
        if (endMillis <= startMillis) {
            return CollectedSessions(emptyList(), earliestEventMillis = null)
        }

        val events = try {
            manager.queryEvents(startMillis, endMillis)
        } catch (e: Exception) {
            AppLog.w(TAG, "queryEvents failed", e)
            return CollectedSessions(emptyList(), earliestEventMillis = null)
        }
        val event = UsageEvents.Event()
        // key = className ?: packageName
        val openAt = HashMap<String, OpenActivity>()
        val sessions = ArrayList<Session>()
        var earliestEventMillis: Long? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (shouldIgnore(pkg)) continue
            val ts = event.timeStamp
            earliestEventMillis = earliestEventMillis?.let { min(it, ts) } ?: ts
            val classKey = event.className?.takeIf { it.isNotBlank() } ?: pkg

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                -> {
                    // 同组件再次 RESUMED：覆盖为最新起点（与 ScreenTime 一致）
                    openAt[classKey] = OpenActivity(
                        packageName = pkg,
                        startMillis = ts,
                    )
                }

                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.MOVE_TO_BACKGROUND,
                -> {
                    closeSession(openAt, sessions, classKey, ts, endMillis)
                }

                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        event.eventType == UsageEvents.Event.ACTIVITY_STOPPED
                    ) {
                        closeSession(openAt, sessions, classKey, ts, endMillis)
                    }
                }
            }
        }

        // 窗尾仍前台：每包只 seal 起点最晚的一条，避免多 activity 叠段
        val closeAt = endMillis
        val latestByPackage = LinkedHashMap<String, OpenActivity>()
        for ((_, open) in openAt) {
            val prev = latestByPackage[open.packageName]
            if (prev == null || open.startMillis >= prev.startMillis) {
                latestByPackage[open.packageName] = open
            }
        }
        for (open in latestByPackage.values) {
            if (closeAt > open.startMillis) {
                sessions += Session(open.packageName, open.startMillis, closeAt)
            }
        }
        return CollectedSessions(sessions, earliestEventMillis)
    }

    private fun closeSession(
        openAt: MutableMap<String, OpenActivity>,
        sessions: MutableList<Session>,
        classKey: String,
        eventTime: Long,
        endMillis: Long,
    ) {
        val open = openAt.remove(classKey) ?: return
        val end = eventTime.coerceAtMost(endMillis)
        if (end > open.startMillis) {
            sessions += Session(open.packageName, open.startMillis, end)
        }
    }

    private fun shouldIgnore(packageName: String): Boolean {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem =
                (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (!isSystem && !isUpdatedSystem) return false
            val keep = packageName.contains("chrome") ||
                packageName.contains("browser") ||
                packageName.contains("photos") ||
                packageName.contains("gallery") ||
                packageName.contains("camera") ||
                packageName.contains("maps") ||
                packageName.contains("youtube") ||
                packageName.contains("gmail") ||
                packageName.contains("vending") ||
                packageName.startsWith("com.google.android.apps.")
            // 系统桌面 / SystemUI / launcher 计入时间线；仅过滤无界面噪音包
            !keep && (
                packageName.contains("packageinstaller") ||
                    packageName.contains("permissioncontroller") ||
                    packageName == "android" ||
                    packageName.startsWith("com.android.providers")
                )
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** 包名 → 应用显示名（导出字典出处等） */
    fun appLabel(packageName: String): String = resolveLabel(packageName)

    private fun resolveLabel(packageName: String): String {
        labelCache[packageName]?.let { return it }
        val label = loadApplicationInfo(packageName)?.let { info ->
            packageManager.getApplicationLabel(info).toString().trim().ifBlank { null }
        } ?: runCatching {
            packageManager.getLaunchIntentForPackage(packageName)
                ?.let { packageManager.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY) }
                ?.loadLabel(packageManager)
                ?.toString()
                ?.trim()
                ?.ifBlank { null }
        }.getOrNull()
            ?: humanizePackageName(packageName)
        labelCache[packageName] = label
        return label
    }

    fun loadAppIcon(packageName: String): android.graphics.drawable.Drawable? {
        synchronized(iconCacheLock) {
            if (iconCache.containsKey(packageName)) return iconCache[packageName]
        }
        val icon = runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
            ?: runCatching {
                packageManager.getLaunchIntentForPackage(packageName)
                    ?.let { packageManager.getActivityIcon(it) }
            }.getOrNull()
            ?: loadApplicationInfo(packageName)?.loadIcon(packageManager)
        synchronized(iconCacheLock) {
            iconCache[packageName] = icon
        }
        return icon
    }

    private fun loadApplicationInfo(packageName: String): ApplicationInfo? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun humanizePackageName(packageName: String): String {
        val last = packageName.substringAfterLast('.').ifBlank { packageName }
        return last.replace('_', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun splitAcrossHours(
        startMillis: Long,
        endMillis: Long,
        emit: (hour: Int, millis: Long) -> Unit,
    ) {
        var cursor = startMillis
        while (cursor < endMillis) {
            val zoned = java.time.Instant.ofEpochMilli(cursor).atZone(zoneId)
            val hour = zoned.hour
            val nextHour = zoned
                .withMinute(0).withSecond(0).withNano(0)
                .plusHours(1)
                .toInstant()
                .toEpochMilli()
            val sliceEnd = min(endMillis, nextHour)
            emit(hour, sliceEnd - cursor)
            cursor = sliceEnd
        }
    }

    private fun splitAcrossDays(
        startMillis: Long,
        endMillis: Long,
        emit: (date: LocalDate, millis: Long) -> Unit,
    ) {
        var cursor = startMillis
        while (cursor < endMillis) {
            val date = java.time.Instant.ofEpochMilli(cursor).atZone(zoneId).toLocalDate()
            val nextDay = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val sliceEnd = min(endMillis, nextDay)
            emit(date, sliceEnd - cursor)
            cursor = sliceEnd
        }
    }

    suspend fun getOrCreateSettings(): AppSettingsEntity {
        return dao.getSettings() ?: AppSettingsEntity().also { dao.upsertSettings(it) }
    }

    suspend fun updateFrameSettings(enabled: Boolean, interval: FrameInterval): AppSettingsEntity {
        val current = getOrCreateSettings()
        val next = current.copy(
            frameRecordingEnabled = enabled,
            frameIntervalMs = interval.millis,
        )
        dao.upsertSettings(next)
        return next
    }

    suspend fun updateThemeMode(themeMode: String): AppSettingsEntity {
        val current = getOrCreateSettings()
        val next = current.copy(themeMode = themeMode)
        dao.upsertSettings(next)
        return next
    }

    /**
     * @param sync 为 false 时只读本地库（切换日视图模式时用，避免重复同步卡顿）
     */
    suspend fun loadDayTimeline(offset: Int, sync: Boolean = false): List<TimelineItem> =
        loadTimelineForPeriod(StatsPeriod.DAY, offset, sync = sync)

    /** 导出 / 云同步：按日/周/月区间加载「进入→离开」时间戳列表 */
    suspend fun loadTimelineForPeriod(
        period: StatsPeriod,
        offset: Int,
        sync: Boolean = false,
    ): List<TimelineItem> {
        val today = LocalDate.now(zoneId)
        val (startDay, endInclusive) = rangeFor(period, today, offset)
        return loadTimelineForDateRange(startDay, endInclusive, sync = sync)
    }

    suspend fun loadTimelineForDateRange(
        start: LocalDate,
        endInclusive: LocalDate,
        sync: Boolean = false,
    ): List<TimelineItem> {
        require(!endInclusive.isBefore(start)) { "结束日期不能早于开始日期" }
        val startMillis = start.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillis = min(
            endInclusive.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
            System.currentTimeMillis(),
        )
        categoryRepository.ensureSeeded()
        if (sync) {
            syncFromSystem(ensureRangeStart = startMillis, untilMillis = System.currentTimeMillis())
        }
        categoryResolver.refresh()
        return mergeSessionsToTimeline(startMillis, endMillis)
    }

    private suspend fun mergeSessionsToTimeline(
        startMillis: Long,
        endMillis: Long,
    ): List<TimelineItem> {
        val sessions = dao.getSessionsOverlapping(startMillis, endMillis)
            .sortedBy { it.startMillis }
        val merged = ArrayList<TimelineItem>()
        for (s in sessions) {
            val start = max(s.startMillis, startMillis)
            val end = min(s.endMillis, endMillis)
            if (end <= start) continue
            val last = merged.lastOrNull()
            if (last != null &&
                last.packageName == s.packageName &&
                start - last.endMillis <= TIMELINE_MERGE_GAP_MILLIS
            ) {
                merged[merged.lastIndex] = last.copy(endMillis = end)
            } else {
                merged += TimelineItem(
                    packageName = s.packageName,
                    label = resolveLabel(s.packageName),
                    categoryId = categoryResolver.resolve(s.packageName),
                    startMillis = start,
                    endMillis = end,
                )
            }
        }
        return merged
    }

    suspend fun loadFramesForPackage(
        packageName: String,
        period: StatsPeriod,
        offset: Int,
    ): List<UiFrameSnapshot> {
        val today = LocalDate.now(zoneId)
        val (start, endInclusive) = rangeFor(period, today, offset)
        val startMillis = start.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillis = endInclusive.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return dao.getFramesForPackage(packageName, startMillis, endMillis).map { it.toSnapshot() }
    }

    suspend fun loadFramesForPackageBetween(
        packageName: String,
        startMillis: Long,
        endMillisExclusive: Long,
    ): List<UiFrameSnapshot> =
        dao.getFramesForPackage(packageName, startMillis, endMillisExclusive).map { it.toSnapshot() }

    /** 含今天在内共 7 个本地日：[今天-6, 明天0点) */
    fun last7DaysRangeMillis(): Pair<Long, Long> {
        val today = LocalDate.now(zoneId)
        val start = today.minusDays(6).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return start to end
    }

    suspend fun loadFramesForPackageLast7Days(packageName: String): List<UiFrameSnapshot> {
        val (start, end) = last7DaysRangeMillis()
        return loadFramesForPackageBetween(packageName, start, end)
    }

    suspend fun countFramesForPackageLast7Days(packageName: String): Long {
        val (start, end) = last7DaysRangeMillis()
        return dao.countFramesForPackageInRange(packageName, start, end)
    }

    suspend fun distinctFramePackages(): List<String> = dao.distinctFramePackages()

    /** 与 [loadSummary] 同一时间窗的全部帧（小窗可用；周/月导出请用 [loadExportFrameTexts]） */
    suspend fun loadFramesForPeriod(period: StatsPeriod, offset: Int): List<UiFrameSnapshot> {
        val today = LocalDate.now(zoneId)
        val (start, endInclusive) = rangeFor(period, today, offset)
        return loadFramesForDateRange(start, endInclusive)
    }

    suspend fun loadFramesForDateRange(
        start: LocalDate,
        endInclusive: LocalDate,
    ): List<UiFrameSnapshot> {
        require(!endInclusive.isBefore(start)) { "结束日期不能早于开始日期" }
        val startMillis = start.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endExclusive = endInclusive.plusDays(1)
        val endMillis = min(
            endExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            System.currentTimeMillis(),
        )
        return dao.getFramesInRange(startMillis, endMillis).map { it.toSnapshot() }
    }

    /**
     * 导出用：分批扫描帧，只收集去重文案，避免整窗 `getFramesInRange` 一次进内存导致 OOM / Cursor 失败。
     */
    suspend fun loadExportFrameTextsForPeriod(
        period: StatsPeriod,
        offset: Int,
        excludePackages: Set<String> = emptySet(),
    ): ExportFrameTexts {
        val today = LocalDate.now(zoneId)
        val (start, endInclusive) = rangeFor(period, today, offset)
        return loadExportFrameTextsForDateRange(start, endInclusive, excludePackages)
    }

    suspend fun loadExportFrameTextsForDateRange(
        start: LocalDate,
        endInclusive: LocalDate,
        excludePackages: Set<String> = emptySet(),
    ): ExportFrameTexts {
        require(!endInclusive.isBefore(start)) { "结束日期不能早于开始日期" }
        val startMillis = start.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillis = min(
            endInclusive.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
            System.currentTimeMillis(),
        )
        val byApp = LinkedHashMap<String, LinkedHashSet<String>>()
        var frameCount = 0
        var afterId = 0L
        var limit = EXPORT_FRAME_BATCH_MAX
        var okStreak = 0
        while (true) {
            val batch = try {
                dao.getFrameTextsInRangeAfterId(startMillis, endMillis, afterId, limit)
            } catch (e: Exception) {
                if (limit > 1 && isCursorWindowError(e)) {
                    limit = (limit / 2).coerceAtLeast(1)
                    okStreak = 0
                    continue
                }
                throw e
            }
            if (batch.isEmpty()) break
            for (row in batch) {
                afterId = row.id
                if (row.packageName in excludePackages) continue
                frameCount++
                val appName = resolveLabel(row.packageName).ifBlank { row.packageName }
                val bucket = byApp.getOrPut(appName) { LinkedHashSet() }
                FrameJson.collectExportStrings(
                    row.textsJson,
                    FrameJson.includeDescForPackage(row.packageName),
                    bucket,
                )
            }
            yield()
            if (batch.size < limit) break
            okStreak++
            if (limit < EXPORT_FRAME_BATCH_MAX && okStreak >= 8) {
                limit = (limit * 2).coerceAtMost(EXPORT_FRAME_BATCH_MAX)
                okStreak = 0
            }
        }
        return ExportFrameTexts(frameCount = frameCount, textsByApp = byApp)
    }

    private fun isCursorWindowError(e: Throwable): Boolean {
        var cur: Throwable? = e
        while (cur != null) {
            if (cur is SQLiteException || cur is IllegalStateException) {
                val hint = cur.message.orEmpty()
                if (hint.contains("CursorWindow", ignoreCase = true) ||
                    hint.contains("Row too big", ignoreCase = true)
                ) {
                    return true
                }
            }
            cur = cur.cause
        }
        return false
    }

    suspend fun listPrivacyApps(): List<PrivacyAppEntity> = dao.getPrivacyApps()

    suspend fun addPrivacyApp(packageName: String, label: String) {
        dao.upsertPrivacyApp(
            PrivacyAppEntity(
                packageName = packageName,
                label = label.ifBlank { packageName },
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun removePrivacyApp(packageName: String) {
        dao.deletePrivacyApp(packageName)
    }

    /** 某包全部时段的帧（搜索跳转用，不受当前统计窗口限制） */
    suspend fun loadFramesForPackageAllTime(packageName: String): List<UiFrameSnapshot> =
        dao.getFramesForPackageAllTime(packageName).map { it.toSnapshot() }

    fun frameFtsProgress() = FrameFtsIndexer.get(context).progress

    fun isFrameFtsReady(): Boolean = FrameFtsIndexer.get(context).isReady()

    /**
     * FTS 分页搜索。索引未就绪时退回 [searchFramesLegacy] 再切片。
     */
    suspend fun searchFramesPage(
        query: String,
        scopePackages: Set<String>?,
        strict: Boolean,
        terms: List<String> = emptyList(),
        matchAll: Boolean = false,
        allowFrameId: Boolean = true,
        cursor: FrameSearchCursor? = null,
        limit: Int = FRAME_SEARCH_PAGE_SIZE,
        startMillis: Long? = null,
        endMillisExclusive: Long? = null,
        descending: Boolean = true,
    ): FrameSearchPage {
        val activeTerms = normalizeTerms(query, terms)
        if (activeTerms.isEmpty()) return FrameSearchPage(emptyList(), null)
        if (scopePackages != null && scopePackages.isEmpty()) {
            return FrameSearchPage(emptyList(), null)
        }
        if (allowFrameId && activeTerms.size == 1 && cursor == null) {
            parseFrameIdQuery(activeTerms.first())?.let { frameId ->
                val row = dao.getFrameById(frameId) ?: return FrameSearchPage(emptyList(), null)
                if (scopePackages != null && row.packageName !in scopePackages) {
                    return FrameSearchPage(emptyList(), null)
                }
                if (!inCapturedRange(row.capturedAt, startMillis, endMillisExclusive)) {
                    return FrameSearchPage(emptyList(), null)
                }
                return FrameSearchPage(
                    hits = listOf(
                        FrameSearchHit(
                            frameId = row.id,
                            packageName = row.packageName,
                            appLabel = resolveLabel(row.packageName),
                            capturedAt = row.capturedAt,
                            snippet = "frame_id=$frameId",
                        ),
                    ),
                    nextCursor = null,
                )
            }
        }

        val indexer = FrameFtsIndexer.get(context)
        if (!indexer.isReady()) {
            return legacyPage(
                activeTerms, scopePackages, strict, matchAll, cursor, limit,
                startMillis, endMillisExclusive, descending,
            )
        }

        return runCatching {
            val sql = FrameFtsQuery.pageQuery(
                terms = activeTerms,
                matchAll = matchAll,
                strict = strict,
                scopePackages = scopePackages,
                cursor = cursor,
                limit = limit,
                startMillis = startMillis,
                endMillisExclusive = endMillisExclusive,
                descending = descending,
            )
            val rows = dao.searchFramesRaw(sql)
            val hits = rows.map { row ->
                FrameSearchHit(
                    frameId = row.id,
                    packageName = row.packageName,
                    appLabel = resolveLabel(row.packageName),
                    capturedAt = row.capturedAt,
                    snippet = matchSnippet(row.textsJson, activeTerms, strict, matchAll)
                        ?: row.textsJson.take(80),
                )
            }
            val next = if (hits.size >= limit) {
                val last = hits.last()
                FrameSearchCursor(last.capturedAt, last.frameId)
            } else {
                null
            }
            FrameSearchPage(hits, next)
        }.getOrElse { e ->
            AppLog.w(TAG, "FTS 分页失败，退回旧路径: ${e.message}", e)
            legacyPage(
                activeTerms, scopePackages, strict, matchAll, cursor, limit,
                startMillis, endMillisExclusive, descending,
            )
        }
    }

    suspend fun countFrameHits(
        query: String,
        scopePackages: Set<String>?,
        strict: Boolean,
        terms: List<String> = emptyList(),
        matchAll: Boolean = false,
        allowFrameId: Boolean = true,
        startMillis: Long? = null,
        endMillisExclusive: Long? = null,
    ): Int {
        val activeTerms = normalizeTerms(query, terms)
        if (activeTerms.isEmpty()) return 0
        if (scopePackages != null && scopePackages.isEmpty()) return 0
        if (allowFrameId && activeTerms.size == 1) {
            parseFrameIdQuery(activeTerms.first())?.let { frameId ->
                val row = dao.getFrameById(frameId) ?: return 0
                if (scopePackages != null && row.packageName !in scopePackages) return 0
                if (!inCapturedRange(row.capturedAt, startMillis, endMillisExclusive)) return 0
                return 1
            }
        }
        val indexer = FrameFtsIndexer.get(context)
        if (!indexer.isReady()) {
            return countLegacyHits(
                activeTerms, scopePackages, strict, matchAll,
                startMillis, endMillisExclusive,
            )
        }
        return runCatching {
            dao.countFramesRaw(
                FrameFtsQuery.countQuery(
                    terms = activeTerms,
                    matchAll = matchAll,
                    strict = strict,
                    scopePackages = scopePackages,
                    startMillis = startMillis,
                    endMillisExclusive = endMillisExclusive,
                ),
            ).toInt()
        }.getOrElse { e ->
            AppLog.w(TAG, "FTS 计数失败: ${e.message}", e)
            countLegacyHits(
                activeTerms, scopePackages, strict, matchAll,
                startMillis, endMillisExclusive,
            )
        }
    }

    private suspend fun countLegacyHits(
        activeTerms: List<String>,
        scopePackages: Set<String>?,
        strict: Boolean,
        matchAll: Boolean,
        startMillis: Long?,
        endMillisExclusive: Long?,
    ): Int {
        var count = 0
        var beforeAt = Long.MAX_VALUE
        var beforeId = Long.MAX_VALUE
        var guard = 0
        while (guard++ < 100_000) {
            val batch = loadLegacyBatch(
                scopePackages, beforeAt, beforeId, LEGACY_SCAN_BATCH, descending = true,
            )
            if (batch.isEmpty()) break
            for (row in batch) {
                if (!inCapturedRange(row.capturedAt, startMillis, endMillisExclusive)) {
                    if (startMillis != null && row.capturedAt < startMillis) {
                        return count
                    }
                    continue
                }
                if (matchSnippet(row.textsJson, activeTerms, strict, matchAll) != null) {
                    count++
                }
            }
            val last = batch.last()
            beforeAt = last.capturedAt
            beforeId = last.id
            if (batch.size < LEGACY_SCAN_BATCH) break
            if (startMillis != null && last.capturedAt < startMillis) break
        }
        return count
    }

    /**
     * 旧路径：分批投影扫描 + contains。不再一次性装入全部帧，避免 OOM。
     * @param maxHits 最多收集多少命中；null 表示不截断（仍按批扫描）。
     */
    suspend fun searchFramesLegacy(
        query: String,
        scopePackages: Set<String>?,
        strict: Boolean,
        terms: List<String> = emptyList(),
        matchAll: Boolean = false,
        allowFrameId: Boolean = true,
        maxHits: Int? = null,
    ): List<FrameSearchHit> {
        val activeTerms = normalizeTerms(query, terms)
        if (activeTerms.isEmpty()) return emptyList()
        if (scopePackages != null && scopePackages.isEmpty()) return emptyList()
        if (allowFrameId && activeTerms.size == 1) {
            parseFrameIdQuery(activeTerms.first())?.let { frameId ->
                val row = dao.getFrameById(frameId) ?: return emptyList()
                if (scopePackages != null && row.packageName !in scopePackages) {
                    return emptyList()
                }
                return listOf(
                    FrameSearchHit(
                        frameId = row.id,
                        packageName = row.packageName,
                        appLabel = resolveLabel(row.packageName),
                        capturedAt = row.capturedAt,
                        snippet = "frame_id=$frameId",
                    ),
                )
            }
        }
        val page = scanLegacyHits(
            activeTerms = activeTerms,
            scopePackages = scopePackages,
            strict = strict,
            matchAll = matchAll,
            cursor = null,
            limit = maxHits ?: Int.MAX_VALUE,
            stopAfterLimit = maxHits != null,
        )
        return page.hits
    }

    /** 兼容旧调用名，转发到旧路径。 */
    suspend fun searchFrames(
        query: String,
        scopePackages: Set<String>?,
        strict: Boolean,
        terms: List<String> = emptyList(),
        matchAll: Boolean = false,
        allowFrameId: Boolean = true,
    ): List<FrameSearchHit> = searchFramesLegacy(
        query, scopePackages, strict, terms, matchAll, allowFrameId,
    )

    private fun normalizeTerms(query: String, terms: List<String>): List<String> =
        terms.map { it.trim() }.filter { it.isNotEmpty() }
            .ifEmpty { listOfNotNull(query.trim().takeIf { it.isNotEmpty() }) }

    private suspend fun legacyPage(
        activeTerms: List<String>,
        scopePackages: Set<String>?,
        strict: Boolean,
        matchAll: Boolean,
        cursor: FrameSearchCursor?,
        limit: Int,
        startMillis: Long?,
        endMillisExclusive: Long?,
        descending: Boolean,
    ): FrameSearchPage = scanLegacyHits(
        activeTerms = activeTerms,
        scopePackages = scopePackages,
        strict = strict,
        matchAll = matchAll,
        cursor = cursor,
        limit = limit,
        stopAfterLimit = true,
        startMillis = startMillis,
        endMillisExclusive = endMillisExclusive,
        descending = descending,
    )

    /**
     * 按 (captured_at, id) 分批扫描投影行，边扫边匹配。
     * [stopAfterLimit]=true 时凑满 [limit] 条命中即停（用于分页）。
     */
    private suspend fun scanLegacyHits(
        activeTerms: List<String>,
        scopePackages: Set<String>?,
        strict: Boolean,
        matchAll: Boolean,
        cursor: FrameSearchCursor?,
        limit: Int,
        stopAfterLimit: Boolean,
        startMillis: Long? = null,
        endMillisExclusive: Long? = null,
        descending: Boolean = true,
    ): FrameSearchPage {
        val hits = ArrayList<FrameSearchHit>(min(limit, 256).coerceAtLeast(16))
        var cursorAt = cursor?.capturedAt ?: if (descending) Long.MAX_VALUE else Long.MIN_VALUE
        var cursorId = cursor?.id ?: if (descending) Long.MAX_VALUE else Long.MIN_VALUE
        var guard = 0
        while (hits.size < limit && guard++ < 100_000) {
            val batch = loadLegacyBatch(
                scopePackages, cursorAt, cursorId, LEGACY_SCAN_BATCH, descending,
            )
            if (batch.isEmpty()) break
            for (row in batch) {
                if (!inCapturedRange(row.capturedAt, startMillis, endMillisExclusive)) {
                    if (descending && startMillis != null && row.capturedAt < startMillis) {
                        return FrameSearchPage(hits, null)
                    }
                    if (!descending && endMillisExclusive != null &&
                        row.capturedAt >= endMillisExclusive
                    ) {
                        return FrameSearchPage(hits, null)
                    }
                    continue
                }
                val snippet = matchSnippet(row.textsJson, activeTerms, strict, matchAll)
                    ?: continue
                hits += FrameSearchHit(
                    frameId = row.id,
                    packageName = row.packageName,
                    appLabel = resolveLabel(row.packageName),
                    capturedAt = row.capturedAt,
                    snippet = snippet,
                )
                if (stopAfterLimit && hits.size >= limit) break
            }
            val last = batch.last()
            cursorAt = last.capturedAt
            cursorId = last.id
            if (batch.size < LEGACY_SCAN_BATCH) break
            if (descending && startMillis != null && last.capturedAt < startMillis) break
            if (!descending && endMillisExclusive != null &&
                last.capturedAt >= endMillisExclusive
            ) {
                break
            }
        }
        val next = if (stopAfterLimit && hits.size >= limit) {
            val last = hits.last()
            FrameSearchCursor(last.capturedAt, last.frameId)
        } else {
            null
        }
        return FrameSearchPage(hits, next)
    }

    private fun inCapturedRange(
        capturedAt: Long,
        startMillis: Long?,
        endMillisExclusive: Long?,
    ): Boolean {
        if (startMillis != null && capturedAt < startMillis) return false
        if (endMillisExclusive != null && capturedAt >= endMillisExclusive) return false
        return true
    }

    private suspend fun loadLegacyBatch(
        scopePackages: Set<String>?,
        at: Long,
        id: Long,
        limit: Int,
        descending: Boolean,
    ): List<com.liuji.xiaoqi.data.db.FrameSearchPageRow> = when {
        descending && scopePackages == null ->
            dao.getFrameSearchProjectionBefore(at, id, limit)
        descending && scopePackages!!.size == 1 ->
            dao.getFrameSearchProjectionForPackageBefore(
                scopePackages.first(), at, id, limit,
            )
        descending -> {
            val merged = ArrayList<com.liuji.xiaoqi.data.db.FrameSearchPageRow>()
            for (chunk in scopePackages!!.toList().chunked(200)) {
                merged += dao.getFrameSearchProjectionForPackagesBefore(
                    chunk, at, id, limit,
                )
            }
            merged
                .sortedWith(
                    compareByDescending<com.liuji.xiaoqi.data.db.FrameSearchPageRow> { it.capturedAt }
                        .thenByDescending { it.id },
                )
                .take(limit)
        }
        scopePackages == null ->
            dao.getFrameSearchProjectionAfter(at, id, limit)
        scopePackages.size == 1 ->
            dao.getFrameSearchProjectionForPackageAfter(
                scopePackages.first(), at, id, limit,
            )
        else -> {
            val merged = ArrayList<com.liuji.xiaoqi.data.db.FrameSearchPageRow>()
            for (chunk in scopePackages.toList().chunked(200)) {
                merged += dao.getFrameSearchProjectionForPackagesAfter(
                    chunk, at, id, limit,
                )
            }
            merged
                .sortedWith(
                    compareBy<com.liuji.xiaoqi.data.db.FrameSearchPageRow> { it.capturedAt }
                        .thenBy { it.id },
                )
                .take(limit)
        }
    }

    /** 支持 `123` / `#123` / `id:123`（忽略大小写与空白） */
    private fun parseFrameIdQuery(query: String): Long? {
        val t = query.trim()
        val raw = when {
            t.startsWith("#") -> t.removePrefix("#").trim()
            t.length >= 3 && t.take(3).equals("id:", ignoreCase = true) ->
                t.substring(3).trim()
            else -> t
        }
        if (raw.isEmpty() || raw.any { !it.isDigit() }) return null
        return raw.toLongOrNull()?.takeIf { it > 0L }
    }

    private fun UiFrameEntity.toSnapshot() = UiFrameSnapshot(
        id = id,
        capturedAt = capturedAt,
        packageName = packageName,
        activityName = activityName,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        texts = FrameJson.textsFromJson(textsJson),
    )

    private fun matchSnippet(
        textsJson: String,
        terms: List<String>,
        strict: Boolean,
        matchAll: Boolean,
    ): String? {
        val nodes = FrameJson.textsFromJson(textsJson)
        val snippets = arrayOfNulls<String>(terms.size)
        var found = 0
        for (n in nodes) {
            for (i in terms.indices) {
                if (snippets[i] != null) continue
                val hit = matchField(n.text, terms[i], strict) ?: matchField(n.desc, terms[i], strict)
                if (hit != null) {
                    snippets[i] = hit
                    found++
                }
            }
            if (found == terms.size) break
        }
        if (found == 0 || (matchAll && found < terms.size)) return null
        return snippets.filterNotNull().distinct().joinToString(" · ").take(160)
    }

    private fun matchField(field: String, query: String, strict: Boolean): String? {
        if (field.isBlank()) return null
        val hit = if (strict) {
            field.contains(query)
        } else {
            field.contains(query, ignoreCase = true)
        }
        if (!hit) return null
        return field.take(80)
    }

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    companion object {
        private const val TAG = "UsageSync"
        private val syncMutex = Mutex()
        private const val MAX_ICON_CACHE = 128
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
        private const val INITIAL_LOOKBACK_DAYS = 60L
        private const val SYNC_OVERLAP_MILLIS = 2L * 60 * 60 * 1000
        /** 对齐 ScreenTime：采集至少多看前一天，避免跨午夜丢段 */
        private const val COLLECT_LOOKBACK_MILLIS = DAY_MILLIS
        /** 同包名会话间隔小于此值则合并为一次进入/离开 */
        private const val TIMELINE_MERGE_GAP_MILLIS = 60_000L
        /** 单批行数；过大可能撑爆 CursorWindow，失败时会自动减半 */
        private const val EXPORT_FRAME_BATCH_MAX = 200
        const val FRAME_SEARCH_PAGE_SIZE = 50
        private const val LEGACY_SCAN_BATCH = 200
    }
}

/** 导出 Markdown「画面文案」段：分批扫描结果 */
data class ExportFrameTexts(
    val frameCount: Int,
    val textsByApp: Map<String, Set<String>>,
)
