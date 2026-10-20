package com.liuji.xiaoqi.data.export

import com.liuji.xiaoqi.data.Categories
import com.liuji.xiaoqi.data.PeriodSummary
import com.liuji.xiaoqi.data.StatsPeriod
import com.liuji.xiaoqi.data.frame.FrameJson
import com.liuji.xiaoqi.data.frame.TimelineItem
import com.liuji.xiaoqi.data.frame.UiFrameSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * 将看板汇总转为「便于 AI 阅读」的 Markdown。
 * timeline 会话 duration 为整数秒；画面文案按 App 显示名分组去重。
 *
 * [timelineArray] 仍保留 JSON 形态，供云同步日笔记等复用。
 */
object UsageJsonExporter {

    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE
    private val isoOffset = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun toMarkdown(
        summary: PeriodSummary,
        frames: List<UiFrameSnapshot> = emptyList(),
        timeline: List<TimelineItem> = emptyList(),
        appLabelOf: (String) -> String = { it },
        zoneId: ZoneId = ZoneId.systemDefault(),
        textsByApp: Map<String, Set<String>>? = null,
        frameCount: Int? = null,
        includeScreenTexts: Boolean = true,
    ): String {
        val sb = StringBuilder()
        writeMarkdown(
            sb, summary, frames, timeline, appLabelOf, zoneId, textsByApp, frameCount, includeScreenTexts,
        )
        return sb.toString()
    }

    fun writeMarkdown(
        out: Appendable,
        summary: PeriodSummary,
        frames: List<UiFrameSnapshot> = emptyList(),
        timeline: List<TimelineItem> = emptyList(),
        appLabelOf: (String) -> String = { it },
        zoneId: ZoneId = ZoneId.systemDefault(),
        textsByApp: Map<String, Set<String>>? = null,
        frameCount: Int? = null,
        includeScreenTexts: Boolean = true,
    ) {
        val dict = if (includeScreenTexts) {
            textsByApp ?: buildTextDictionary(frames, appLabelOf)
        } else {
            emptyMap()
        }
        val framesN = frameCount ?: frames.size
        val sb = out
        val totalMinutes = toMinutes(summary.totalMillis)
        val avgMinutes = toMinutes(summary.averageDailyMillis)
        val periodLabel = if (summary.rangeStart == summary.rangeEnd) {
            summary.rangeStart.format(isoDate)
        } else {
            "${summary.rangeStart.format(isoDate)} ~ ${summary.rangeEnd.format(isoDate)}"
        }

        sb.appendLine("# 留迹使用数据")
        sb.appendLine()
        sb.appendLine("- 导出时间：${ZonedDateTime.now(zoneId).format(isoOffset)}")
        sb.appendLine("- 时区：${zoneId.id}")
        sb.appendLine("- 统计类型：${periodTypeZh(summary)}")
        sb.appendLine("- 日期范围：$periodLabel")
        sb.appendLine("- schema：1.6-md")
        if (includeScreenTexts) {
            sb.appendLine("- 画面文案：不含 desc，只保留可见 text（闲鱼仍保留 desc）")
        } else {
            sb.appendLine("- 屏幕文字记录：未导出")
        }
        sb.appendLine()
        sb.appendLine(
            if (includeScreenTexts) {
                "> 供 AI 分析时间去向与习惯。汇总层时长为分钟级中文；" +
                    "timeline 会话 duration 为整数秒。" +
                    "隐私 App 不出现在画面文案（时长与 timeline 仍保留）。"
            } else {
                "> 供 AI 分析时间去向与习惯。汇总层时长为分钟级中文；" +
                    "timeline 会话 duration 为整数秒。本次未导出帧视图画面文案。"
            },
        )
        sb.appendLine()

        sb.appendLine("## 汇总")
        sb.appendLine()
        sb.appendLine("- 总时长：${formatDurationZh(summary.totalMillis)}（$totalMinutes 分钟）")
        sb.appendLine("- 日均时长：${formatDurationZh(summary.averageDailyMillis)}（$avgMinutes 分钟）")
        sb.appendLine("- 应用数：${summary.apps.size}")
        sb.appendLine("- 分类数：${summary.categories.size}")
        sb.appendLine("- 会话数：${timeline.size}")
        if (includeScreenTexts) {
            sb.appendLine("- 画面帧数：$framesN")
        }
        sb.appendLine()

        val screenHint = if (includeScreenTexts) "、画面文案" else ""
        when (summary.period) {
            StatsPeriod.DAY -> {
                sb.appendLine(
                    "> 单日数据：下方含分时明细、应用/分类排行、进入→离开会话$screenHint。",
                )
            }
            StatsPeriod.WEEK -> {
                sb.appendLine(
                    "> 一周数据：日均仅按已过去天数计算；含每日明细、整周会话$screenHint。",
                )
            }
            StatsPeriod.MONTH -> {
                sb.appendLine(
                    "> 一月数据：日均仅按已过去天数计算；含每日明细、整月会话$screenHint。",
                )
            }
        }
        sb.appendLine()

        appendAppsSection(sb, summary, totalMinutes)
        appendCategoriesSection(sb, summary, totalMinutes)

        when (summary.period) {
            StatsPeriod.DAY -> appendHourlySection(sb, summary)
            StatsPeriod.WEEK, StatsPeriod.MONTH -> appendDailySection(sb, summary)
        }

        appendTimelineSection(sb, timeline, zoneId)
        if (includeScreenTexts) {
            appendTextDictionarySection(sb, dict)
        }
    }

    /** @deprecated 请用 [toMarkdown]；保留别名以免旧调用方编译失败 */
    fun toJson(
        summary: PeriodSummary,
        frames: List<UiFrameSnapshot> = emptyList(),
        timeline: List<TimelineItem> = emptyList(),
        appLabelOf: (String) -> String = { it },
        zoneId: ZoneId = ZoneId.systemDefault(),
        pretty: Boolean = true,
    ): String = toMarkdown(summary, frames, timeline, appLabelOf, zoneId)

    fun timelineArray(
        timeline: List<TimelineItem>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): JSONArray {
        val arr = JSONArray()
        for (item in timeline) {
            arr.put(
                JSONObject()
                    .put("app_name", item.label)
                    .put("category_name", Categories.of(item.categoryId).name)
                    .put("start", Instant.ofEpochMilli(item.startMillis).atZone(zoneId).format(isoOffset))
                    .put("end", Instant.ofEpochMilli(item.endMillis).atZone(zoneId).format(isoOffset))
                    .put("duration", toSeconds(item.durationMillis)),
            )
        }
        return arr
    }

    fun fileName(summary: PeriodSummary): String {
        val start = summary.rangeStart.format(isoDate)
        val end = summary.rangeEnd.format(isoDate)
        return if (summary.rangeStart == summary.rangeEnd) {
            "liuji_day_$start.md"
        } else {
            "liuji_range_${start}_to_$end.md"
        }
    }

    private fun appendAppsSection(sb: Appendable, summary: PeriodSummary, totalMinutes: Int) {
        sb.appendLine("## 应用排行")
        sb.appendLine()
        if (summary.apps.isEmpty()) {
            sb.appendLine("_无应用数据_")
            sb.appendLine()
            return
        }
        sb.appendLine("| 应用 | 分类 | 时长 | 分钟 | 占比% |")
        sb.appendLine("| --- | --- | --- | ---: | ---: |")
        for (app in summary.apps) {
            val minutes = toMinutes(app.millis)
            sb.append("| ")
                .append(mdCell(app.label))
                .append(" | ")
                .append(mdCell(Categories.of(app.categoryId).name))
                .append(" | ")
                .append(formatDurationZh(app.millis))
                .append(" | ")
                .append(minutes.toString())
                .append(" | ")
                .append(percent(minutes, totalMinutes).toString())
                .appendLine(" |")
        }
        sb.appendLine()
    }

    private fun appendCategoriesSection(sb: Appendable, summary: PeriodSummary, totalMinutes: Int) {
        sb.appendLine("## 分类排行")
        sb.appendLine()
        if (summary.categories.isEmpty()) {
            sb.appendLine("_无分类数据_")
            sb.appendLine()
            return
        }
        sb.appendLine("| 分类 | 时长 | 分钟 | 占比% | 代表应用 |")
        sb.appendLine("| --- | --- | ---: | ---: | --- |")
        for (cat in summary.categories) {
            val minutes = toMinutes(cat.millis)
            val top = cat.topPackages.joinToString("、") { pkg ->
                summary.apps.firstOrNull { it.packageName == pkg }?.label
                    ?: pkg.substringAfterLast('.')
            }
            sb.append("| ")
                .append(mdCell(cat.name))
                .append(" | ")
                .append(formatDurationZh(cat.millis))
                .append(" | ")
                .append(minutes.toString())
                .append(" | ")
                .append(percent(minutes, totalMinutes).toString())
                .append(" | ")
                .append(mdCell(top))
                .appendLine(" |")
        }
        sb.appendLine()
    }

    private fun appendHourlySection(sb: Appendable, summary: PeriodSummary) {
        sb.appendLine("## 分时明细")
        sb.appendLine()
        sb.appendLine("| 小时 | 时长 | 分钟 | 主要分类 | 主要应用 |")
        sb.appendLine("| ---: | --- | ---: | --- | --- |")
        summary.hours.forEach { slot ->
            val minutes = toMinutes(slot.totalMillis)
            val cats = slot.slices.joinToString("、") {
                "${Categories.of(it.categoryId).name} ${formatDurationZh(it.millis)}"
            }
            val apps = summary.appsByHour[slot.hour].orEmpty().take(5).joinToString("、") {
                "${it.label} ${formatDurationZh(it.millis)}"
            }
            sb.append("| ")
                .append(slot.hour.toString())
                .append(" | ")
                .append(formatDurationZh(slot.totalMillis))
                .append(" | ")
                .append(minutes.toString())
                .append(" | ")
                .append(mdCell(cats.ifBlank { "-" }))
                .append(" | ")
                .append(mdCell(apps.ifBlank { "-" }))
                .appendLine(" |")
        }
        sb.appendLine()
    }

    private fun appendDailySection(sb: Appendable, summary: PeriodSummary) {
        sb.appendLine("## 每日明细")
        sb.appendLine()
        sb.appendLine("| 日期 | 星期 | 时长 | 分钟 | 分类 |")
        sb.appendLine("| --- | --- | --- | ---: | --- |")
        summary.dayBuckets.forEach { day ->
            val minutes = toMinutes(day.totalMillis)
            val cats = day.slices.joinToString("、") {
                "${Categories.of(it.categoryId).name} ${formatDurationZh(it.millis)}"
            }
            sb.append("| ")
                .append(day.date.format(isoDate))
                .append(" | ")
                .append(weekdayZh(day.date.dayOfWeek.value))
                .append(" | ")
                .append(formatDurationZh(day.totalMillis))
                .append(" | ")
                .append(minutes.toString())
                .append(" | ")
                .append(mdCell(cats.ifBlank { "-" }))
                .appendLine(" |")
        }
        sb.appendLine()
    }

    private fun appendTimelineSection(
        sb: Appendable,
        timeline: List<TimelineItem>,
        zoneId: ZoneId,
    ) {
        sb.appendLine("## 时间戳会话")
        sb.appendLine()
        if (timeline.isEmpty()) {
            sb.appendLine("_无前台会话_")
            sb.appendLine()
            return
        }
        sb.appendLine("共 ${timeline.size} 次进入→离开（同应用短间隔已合并）。duration 单位：秒。")
        sb.appendLine()
        sb.appendLine("| 应用 | 分类 | 开始 | 结束 | 时长(秒) |")
        sb.appendLine("| --- | --- | --- | --- | ---: |")
        for (item in timeline) {
            sb.append("| ")
                .append(mdCell(item.label))
                .append(" | ")
                .append(mdCell(Categories.of(item.categoryId).name))
                .append(" | ")
                .append(Instant.ofEpochMilli(item.startMillis).atZone(zoneId).format(isoOffset))
                .append(" | ")
                .append(Instant.ofEpochMilli(item.endMillis).atZone(zoneId).format(isoOffset))
                .append(" | ")
                .append(toSeconds(item.durationMillis).toString())
                .appendLine(" |")
        }
        sb.appendLine()
    }

    private fun buildTextDictionary(
        frames: List<UiFrameSnapshot>,
        appLabelOf: (String) -> String,
    ): Map<String, Set<String>> {
        val byApp = LinkedHashMap<String, LinkedHashSet<String>>()
        for (f in frames) {
            val appName = appLabelOf(f.packageName).ifBlank { f.packageName }
            val bucket = byApp.getOrPut(appName) { LinkedHashSet() }
            for (n in f.texts) {
                n.text.trim().takeIf { it.isNotEmpty() }?.let { bucket.add(it) }
                if (FrameJson.includeDescForPackage(f.packageName)) {
                    n.desc.trim().takeIf { it.isNotEmpty() }?.let { bucket.add(it) }
                }
            }
        }
        return byApp
    }

    private fun appendTextDictionarySection(
        sb: Appendable,
        byApp: Map<String, Set<String>>,
    ) {
        sb.appendLine("## 画面文案")
        sb.appendLine()
        if (byApp.isEmpty()) {
            sb.appendLine("_无画面文案（或均在隐私名单中）_")
            sb.appendLine()
            return
        }
        for ((app, texts) in byApp) {
            sb.appendLine("### ${mdInline(app)}")
            sb.appendLine()
            for (t in texts) {
                sb.append("- ").appendLine(escapeMdListItem(t))
            }
            sb.appendLine()
        }
    }

    private fun periodTypeZh(summary: PeriodSummary): String = when {
        summary.rangeStart == summary.rangeEnd -> "日统计"
        summary.period == StatsPeriod.MONTH -> "月统计"
        summary.period == StatsPeriod.WEEK -> "周统计"
        else -> "自定义区间"
    }

    private fun weekdayZh(isoDay: Int): String = when (isoDay) {
        1 -> "星期一"
        2 -> "星期二"
        3 -> "星期三"
        4 -> "星期四"
        5 -> "星期五"
        6 -> "星期六"
        7 -> "星期日"
        else -> ""
    }

    private fun toMinutes(millis: Long): Int = (millis / 60_000L).toInt()

    private fun toSeconds(millis: Long): Int = (millis / 1_000L).toInt().coerceAtLeast(0)

    private fun percent(partMinutes: Int, totalMinutes: Int): Double {
        if (totalMinutes <= 0) return 0.0
        return ((partMinutes * 1000.0 / totalMinutes).roundToInt() / 10.0)
    }

    private fun formatDurationZh(millis: Long): String {
        val totalMinutes = toMinutes(millis)
        if (totalMinutes <= 0) return "0分"
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours == 0 -> "${minutes}分"
            minutes == 0 -> "${hours}小时"
            else -> "${hours}小时${minutes}分"
        }
    }

    private fun mdCell(raw: String): String =
        raw.replace("|", "\\|").replace("\n", " ").ifBlank { "-" }

    private fun mdInline(raw: String): String =
        raw.replace("\n", " ").ifBlank { "未知应用" }

    private fun escapeMdListItem(raw: String): String =
        raw.replace("\n", " ").ifBlank { "-" }
}
