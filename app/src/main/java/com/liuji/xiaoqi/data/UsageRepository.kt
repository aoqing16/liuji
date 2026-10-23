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
import kotlinx.coroutines.yield
import com.liuji.xiaoqi.data.db.AppMetaEntity
import com.liuji.xiaoqi.data.db.AppSettingsEntity
import com.liuji.xiaoqi.data.db.PrivacyAppEntity
import com.liuji.xiaoqi.data.db.SyncStateEntity
import com.liuji.xiaoqi.data.db.UsageDatabase
import com.liuji.xiaoqi.data.db.UsageSessionEntity
import com.liuji.xiaoqi.data.db.UiFrameEntity
import com.liuji.xiaoqi.data.frame.FrameInterval
import com.liuji.xiaoqi.data.frame.FrameJson
import com.liuji.xiaoqi.data.frame.FrameSearchHit
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
     */
    suspend fun syncFromSystem(
        ensureRangeStart: Long? = null,
        untilMillis: Long = System.currentTimeMillis(),
    ) {
        if (!hasUsageAccess()) return

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
        val entities = collected.map { session ->
            UsageSessionEntity(
                packageName = session.packageName,
                startMillis = session.startMillis,
                endMillis = session.endMillis,
                categoryId = categoryResolver.resolve(session.packageName),
            )
        }
        // 按 start 落在采集窗内删除并重写，与 collectStart 对齐
        dao.replaceSessionsInRange(collectStart, untilMillis, entities)

        val now = System.currentTimeMillis()
        val metas = collected
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

    /**
     * 修复损坏的跨夜用量：清空本地会话与同步水位，下次 [syncFromSystem] 将全量回溯重采。
     */
    suspend fun clearLocalUsageForResync() {
        dao.deleteAllSessions()
        dao.clearSyncState()
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

    /**
     * 从 UsageEvents 构建前台会话。
     * open 表按 className（缺省 package）配对，对齐 ScreenTime；区间归到 packageName。
     */
    private fun collectSessions(startMillis: Long, endMillis: Long): List<Session> {
        val manager = usageStatsManager ?: return emptyList()
        if (endMillis <= startMillis) return emptyList()

        val events = manager.queryEvents(startMillis, endMillis)
        val event = UsageEvents.Event()
        // key = className ?: packageName
        val openAt = HashMap<String, OpenActivity>()
        val sessions = ArrayList<Session>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (shouldIgnore(pkg)) continue
            val classKey = event.className?.takeIf { it.isNotBlank() } ?: pkg

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                -> {
                    // 同组件再次 RESUMED：覆盖为最新起点（与 ScreenTime 一致）
                    openAt[classKey] = OpenActivity(
                        packageName = pkg,
                        startMillis = event.timeStamp,
                    )
                }

                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.MOVE_TO_BACKGROUND,
                -> {
                    closeSession(openAt, sessions, classKey, event.timeStamp, endMillis)
                }

                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        event.eventType == UsageEvents.Event.ACTIVITY_STOPPED
                    ) {
                        closeSession(openAt, sessions, classKey, event.timeStamp, endMillis)
                    }
                }
            }
        }

        // 窗尾仍前台：全部 seal 到 endMillis（比只封最晚一个更不易漏）
        val closeAt = endMillis
        for ((_, open) in openAt) {
            if (closeAt > open.startMillis) {
                sessions += Session(open.packageName, open.startMillis, closeAt)
            }
        }
        return sessions
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
        val next = AppSettingsEntity(
            frameRecordingEnabled = enabled,
            frameIntervalMs = interval.millis,
        )
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

    /**
     * @param scopePackages null 为全局；非空则只搜这些包
     * @param strict true=区分大小写 contains；false=不区分大小写 contains
     */
    suspend fun searchFrames(
        query: String,
        scopePackages: Set<String>?,
        strict: Boolean,
    ): List<FrameSearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        if (scopePackages != null && scopePackages.isEmpty()) return emptyList()
        parseFrameIdQuery(q)?.let { frameId ->
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
        val rows = when {
            scopePackages == null -> dao.getAllFramesOrdered()
            scopePackages.size == 1 -> dao.getFramesForPackageAllTime(scopePackages.first())
            else -> {
                val merged = ArrayList<com.liuji.xiaoqi.data.db.UiFrameEntity>()
                for (chunk in scopePackages.toList().chunked(200)) {
                    merged += dao.getFramesForPackages(chunk)
                }
                merged.sortedBy { it.capturedAt }
            }
        }
        val hits = ArrayList<FrameSearchHit>()
        for (e in rows) {
            val snippet = firstMatchSnippet(e.textsJson, q, strict) ?: continue
            hits += FrameSearchHit(
                frameId = e.id,
                packageName = e.packageName,
                appLabel = resolveLabel(e.packageName),
                capturedAt = e.capturedAt,
                snippet = snippet,
            )
        }
        return hits.sortedByDescending { it.capturedAt }
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

    private fun firstMatchSnippet(textsJson: String, query: String, strict: Boolean): String? {
        val nodes = FrameJson.textsFromJson(textsJson)
        for (n in nodes) {
            matchField(n.text, query, strict)?.let { return it }
            matchField(n.desc, query, strict)?.let { return it }
        }
        return null
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
    }
}

/** 导出 Markdown「画面文案」段：分批扫描结果 */
data class ExportFrameTexts(
    val frameCount: Int,
    val textsByApp: Map<String, Set<String>>,
)
