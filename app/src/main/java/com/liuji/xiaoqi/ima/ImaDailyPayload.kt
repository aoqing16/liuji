package com.liuji.xiaoqi.ima

import com.liuji.xiaoqi.data.PeriodSummary
import com.liuji.xiaoqi.data.db.UiFrameEntity
import com.liuji.xiaoqi.data.export.UsageJsonExporter
import com.liuji.xiaoqi.data.frame.TimelineItem
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 日笔记 Markdown：用量汇总 + 时间戳会话 + text_dictionary（与导出 schema 1.5 对齐）。
 * 字典为对象：键=App 显示名，值=该 App 去重文本列表。
 */
object ImaDailyPayload {
    private val zone: ZoneId = ZoneId.systemDefault()

    fun noteTitle(date: LocalDate): String = "留迹_${date}"

    fun createMarkdown(
        date: LocalDate,
        summary: PeriodSummary?,
        frames: List<UiFrameEntity>,
        timeline: List<TimelineItem> = emptyList(),
        appLabelOf: (String) -> String = { it },
    ): String {
        val sb = StringBuilder()
        sb.append("# ").append(noteTitle(date)).append("\n\n")
        sb.append("- 设备时区: ").append(zone.id).append("\n")
        sb.append("- 生成于: ").append(Instant.now()).append("\n")
        sb.append("- 含时间戳会话 timeline 与帧文本 text_dictionary\n\n")
        sb.append(usageSection(summary))
        sb.append(timelineSection(timeline, isAppend = false))
        sb.append(dictionarySection(frames, appLabelOf, isAppend = false))
        return sb.toString()
    }

    fun appendMarkdown(
        summary: PeriodSummary?,
        frames: List<UiFrameEntity>,
        includeUsageRefresh: Boolean,
        timeline: List<TimelineItem> = emptyList(),
        appLabelOf: (String) -> String = { it },
    ): String {
        val sb = StringBuilder()
        sb.append("\n\n---\n\n")
        sb.append("## 增量同步 ").append(Instant.now()).append("\n\n")
        if (includeUsageRefresh) {
            sb.append(usageSection(summary))
            sb.append(timelineSection(timeline, isAppend = true))
        }
        sb.append(dictionarySection(frames, appLabelOf, isAppend = true))
        return sb.toString()
    }

    private fun usageSection(summary: PeriodSummary?): String {
        val sb = StringBuilder()
        sb.append("## 使用时长汇总\n\n")
        if (summary == null) {
            sb.append("_当日暂无使用时长数据_\n\n")
            return sb.toString()
        }
        val totalMin = (summary.totalMillis / 60_000L).toInt()
        sb.append("- 总时长: ").append(totalMin).append(" 分钟\n")
        sb.append("- 应用数: ").append(summary.apps.size).append("\n")
        sb.append("- 分类数: ").append(summary.categories.size).append("\n\n")
        sb.append("### Top 应用\n\n")
        summary.apps.take(20).forEach { app ->
            val min = (app.millis / 60_000L).toInt()
            sb.append("- ")
                .append(app.label)
                .append(" (`")
                .append(app.packageName)
                .append("`) · ")
                .append(min)
                .append(" 分钟 · ")
                .append(app.categoryName)
                .append("\n")
        }
        sb.append("\n### 分类占比\n\n")
        summary.categories.forEach { cat ->
            val min = (cat.millis / 60_000L).toInt()
            sb.append("- ")
                .append(cat.name)
                .append(": ")
                .append(min)
                .append(" 分钟\n")
        }
        sb.append("\n")
        return sb.toString()
    }

    private fun timelineSection(
        timeline: List<TimelineItem>,
        isAppend: Boolean,
    ): String {
        val sb = StringBuilder()
        sb.append(if (isAppend) "### 时间戳会话（当日刷新）\n\n" else "## 时间戳会话\n\n")
        if (timeline.isEmpty()) {
            sb.append("_当日暂无前台时间戳_\n\n")
            return sb.toString()
        }
        sb.append("共 ").append(timeline.size).append(" 次进入→离开（同应用短间隔已合并）\n\n")
        sb.append("```json\n")
        sb.append(UsageJsonExporter.timelineArray(timeline, zone).toString(2))
        sb.append("\n```\n\n")
        return sb.toString()
    }

    private fun dictionarySection(
        frames: List<UiFrameEntity>,
        appLabelOf: (String) -> String,
        isAppend: Boolean,
    ): String {
        val sb = StringBuilder()
        sb.append(if (isAppend) "### 新增文本字典\n\n" else "## 文本字典\n\n")
        if (frames.isEmpty()) {
            sb.append("_本段无新文本_\n\n")
            return sb.toString()
        }
        val dictionary = buildTextDictionary(frames, appLabelOf)
        sb.append("本批帧数: ").append(frames.size)
            .append(" · App 数: ").append(dictionary.length()).append("\n\n")
        sb.append("```json\n")
        sb.append(dictionary.toString(2))
        sb.append("\n```\n\n")
        return sb.toString()
    }

    /**
     * 与导出 schema 1.5 一致：键=App 显示名，值=去重文本数组。
     */
    fun buildTextDictionary(
        frames: List<UiFrameEntity>,
        appLabelOf: (String) -> String,
    ): JSONObject {
        val byApp = LinkedHashMap<String, LinkedHashSet<String>>()
        for (f in frames) {
            val appName = appLabelOf(f.packageName).ifBlank { f.packageName }
            val bucket = byApp.getOrPut(appName) { LinkedHashSet() }
            val nodes = runCatching { JSONArray(f.textsJson) }.getOrElse { JSONArray() }
            for (i in 0 until nodes.length()) {
                val o = nodes.optJSONObject(i) ?: continue
                o.optString("text").trim().takeIf { it.isNotEmpty() }?.let { bucket.add(it) }
                o.optString("desc").trim().takeIf { it.isNotEmpty() }?.let { bucket.add(it) }
            }
        }
        val dictionary = JSONObject()
        for ((app, texts) in byApp) {
            val arr = JSONArray()
            for (t in texts) arr.put(t)
            dictionary.put(app, arr)
        }
        return dictionary
    }

    /** 粗估 markdown 体积，用于分批 */
    fun estimateBytes(md: String): Int = md.toByteArray(Charsets.UTF_8).size
}
