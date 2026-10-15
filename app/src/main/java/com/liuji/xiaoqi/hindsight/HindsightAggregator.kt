package com.liuji.xiaoqi.hindsight

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import com.liuji.xiaoqi.data.AppUsageItem
import com.liuji.xiaoqi.data.Categories
import com.liuji.xiaoqi.data.CategorySlice
import com.liuji.xiaoqi.data.CategoryUsageItem
import com.liuji.xiaoqi.data.DayBucket
import com.liuji.xiaoqi.data.HindsightAppDetail
import com.liuji.xiaoqi.data.HourSlot
import com.liuji.xiaoqi.data.PeriodSummary
import com.liuji.xiaoqi.data.StatsPeriod
import com.liuji.xiaoqi.data.WindowUsageItem
import com.liuji.xiaoqi.data.frame.TimelineItem
import com.liuji.xiaoqi.hindsight.db.HindsightDatabase
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

class HindsightAggregator(context: Context) {
    private val dao = HindsightDatabase.getInstance(context).dao()
    private val zone = ZoneId.systemDefault()
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE

    suspend fun loadSummary(period: StatsPeriod, offset: Int): PeriodSummary {
        val today = LocalDate.now(zone)
        val (start, end) = rangeFor(period, today, offset)
        val rows = dao.activitiesBetween(start.format(dateFmt), end.format(dateFmt))
        val catById = dao.activeCategories().associateBy { it.id }
        val groups = dao.activeAppGroups().associateBy { it.id }
        val members = dao.activeAppGroupMembers()
        val processCat = members.associate { m ->
            val catId = groups[m.groupId]?.categoryId?.takeIf { it.isNotBlank() } ?: "other"
            m.processName to catId
        }
        val processGroup = members.associate { it.processName to it.groupId }

        fun resolveCat(process: String, fallback: String): String =
            processCat[process] ?: fallback.ifBlank { "other" }
        fun displayName(process: String): String =
            processGroup[process]?.let { groups[it]?.displayName }?.takeIf { it.isNotBlank() } ?: process

        val appMillis = LinkedHashMap<String, Long>()
        val categoryMillis = LinkedHashMap<String, Long>()
        val categoryTopApps = LinkedHashMap<String, MutableMap<String, Long>>()
        val hourCategory = Array(24) { LinkedHashMap<String, Long>() }
        val hourApps = Array(24) { LinkedHashMap<String, Long>() }
        val dayCategory = linkedMapOf<LocalDate, LinkedHashMap<String, Long>>()

        var cursor = start
        while (!cursor.isAfter(end)) {
            dayCategory[cursor] = LinkedHashMap()
            cursor = cursor.plusDays(1)
        }

        for (row in rows) {
            val millis = max(0L, row.durationSecs * 1000L)
            if (millis <= 0L) continue
            val catId = resolveCat(row.processName, row.categoryId)
            val process = row.processName

            appMillis[process] = (appMillis[process] ?: 0L) + millis
            categoryMillis[catId] = (categoryMillis[catId] ?: 0L) + millis
            categoryTopApps.getOrPut(catId) { LinkedHashMap() }
                .merge(process, millis, Long::plus)

            val hour = row.localHour.coerceIn(0, 23)
            hourCategory[hour][catId] = (hourCategory[hour][catId] ?: 0L) + millis
            hourApps[hour][process] = (hourApps[hour][process] ?: 0L) + millis

            val date = runCatching { LocalDate.parse(row.localDate) }.getOrNull() ?: continue
            dayCategory[date]?.let { map ->
                map[catId] = (map[catId] ?: 0L) + millis
            }
        }

        fun catName(id: String) = catById[id]?.name ?: Categories.of(id).name
        fun catColor(id: String): Color {
            val hex = catById[id]?.color
            return parseColor(hex) ?: Categories.of(id).color
        }

        val apps = appMillis.entries
            .sortedByDescending { it.value }
            .map { (process, millis) ->
                val catId = resolveCat(process, "other")
                AppUsageItem(
                    packageName = process,
                    label = displayName(process),
                    categoryId = catId,
                    millis = millis,
                    categoryName = catName(catId),
                    categoryColor = catColor(catId),
                )
            }

        val categories = categoryMillis.entries
            .sortedByDescending { it.value }
            .map { (catId, millis) ->
                CategoryUsageItem(
                    categoryId = catId,
                    name = catName(catId),
                    color = catColor(catId),
                    millis = millis,
                    topPackages = categoryTopApps[catId]
                        ?.entries
                        ?.sortedByDescending { it.value }
                        ?.take(3)
                        ?.map { it.key }
                        .orEmpty(),
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
        val elapsed = dayBuckets.count { !it.date.isAfter(today) }.coerceAtLeast(1)
        val average = if (period == StatsPeriod.DAY) total else total / elapsed

        val appsByHour = (0 until 24).associateWith { hour ->
            hourApps[hour].entries
                .sortedByDescending { it.value }
                .map { (process, millis) ->
                    val catId = resolveCat(process, "other")
                    AppUsageItem(
                        packageName = process,
                        label = displayName(process),
                        categoryId = catId,
                        millis = millis,
                        categoryName = catName(catId),
                        categoryColor = catColor(catId),
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
            rangeEnd = end,
            totalMillis = total,
            averageDailyMillis = average,
            hours = hours,
            dayBuckets = dayBuckets,
            apps = apps,
            categories = categories,
            appsByHour = appsByHour,
        )
    }

    /**
     * 应用详情：当前 period/offset 内该进程的小时分布 + 窗口标题汇总。
     * [hourFilter] 非空时仅统计该小时（与看板选中小时一致）。
     */
    suspend fun loadAppDetail(
        processName: String,
        period: StatsPeriod,
        offset: Int,
        hourFilter: Int? = null,
    ): HindsightAppDetail {
        val today = LocalDate.now(zone)
        val (start, end) = rangeFor(period, today, offset)
        val rows = dao.activitiesBetween(start.format(dateFmt), end.format(dateFmt))
            .filter { it.processName == processName }
            .filter { hourFilter == null || it.localHour == hourFilter }

        val catById = dao.activeCategories().associateBy { it.id }
        val groups = dao.activeAppGroups().associateBy { it.id }
        val members = dao.activeAppGroupMembers()
        val groupId = members.firstOrNull { it.processName == processName }?.groupId
        val group = groupId?.let { groups[it] }
        val fallbackCat = rows.firstOrNull()?.categoryId.orEmpty()
        val catId = group?.categoryId?.takeIf { it.isNotBlank() }
            ?: fallbackCat.ifBlank { "other" }
        val displayName = group?.displayName?.takeIf { it.isNotBlank() } ?: processName
        val categoryName = catById[catId]?.name ?: Categories.of(catId).name
        val categoryColor = parseColor(catById[catId]?.color) ?: Categories.of(catId).color

        val hourly = LongArray(24)
        val windowMillis = LinkedHashMap<String, Long>()
        var total = 0L
        for (row in rows) {
            val millis = max(0L, row.durationSecs * 1000L)
            if (millis <= 0L) continue
            total += millis
            val hour = row.localHour.coerceIn(0, 23)
            hourly[hour] += millis
            val title = row.windowTitle?.trim()?.takeIf { it.isNotEmpty() } ?: "(无标题)"
            windowMillis[title] = (windowMillis[title] ?: 0L) + millis
        }

        val windows = windowMillis.entries
            .sortedByDescending { it.value }
            .map { WindowUsageItem(title = it.key, millis = it.value) }

        return HindsightAppDetail(
            processName = processName,
            displayName = displayName,
            categoryId = catId,
            categoryName = categoryName,
            categoryColor = categoryColor,
            totalMillis = total,
            hourlyMillis = hourly.toList(),
            windows = windows,
        )
    }

    suspend fun loadDayTimeline(offset: Int): List<TimelineItem> {
        val day = LocalDate.now(zone).plusDays(offset.toLong())
        val dateStr = day.format(dateFmt)
        val rows = dao.activitiesBetween(dateStr, dateStr).sortedBy { it.startedAt }
        val groups = dao.activeAppGroups().associateBy { it.id }
        val processCat = dao.activeAppGroupMembers().associate { m ->
            val catId = groups[m.groupId]?.categoryId?.takeIf { it.isNotBlank() } ?: "other"
            m.processName to catId
        }

        val merged = ArrayList<TimelineItem>()
        for (row in rows) {
            val startMs = parseInstant(row.startedAt) ?: continue
            val endMs = parseInstant(row.endedAt) ?: (startMs + row.durationSecs * 1000L)
            if (endMs <= startMs) continue
            val catId = processCat[row.processName] ?: row.categoryId.ifBlank { "other" }
            val last = merged.lastOrNull()
            if (last != null &&
                last.packageName == row.processName &&
                startMs - last.endMillis <= 60_000L
            ) {
                merged[merged.lastIndex] = last.copy(endMillis = endMs)
            } else {
                merged += TimelineItem(
                    packageName = row.processName,
                    label = row.processName,
                    categoryId = catId,
                    startMillis = startMs,
                    endMillis = endMs,
                )
            }
        }
        return merged
    }

    private fun rangeFor(period: StatsPeriod, today: LocalDate, offset: Int): Pair<LocalDate, LocalDate> =
        when (period) {
            StatsPeriod.DAY -> {
                val d = today.plusDays(offset.toLong())
                d to d
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

    private fun parseInstant(text: String): Long? = runCatching {
        Instant.parse(text).toEpochMilli()
    }.recoverCatching {
        // +08:00 等带偏移的 ISO
        java.time.OffsetDateTime.parse(text).toInstant().toEpochMilli()
    }.getOrNull()

    private fun parseColor(hex: String?): Color? {
        if (hex.isNullOrBlank()) return null
        return runCatching {
            Color(AndroidColor.parseColor(hex))
        }.getOrNull()
    }
}
