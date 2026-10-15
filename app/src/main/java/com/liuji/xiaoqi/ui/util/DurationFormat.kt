package com.liuji.xiaoqi.ui.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatDuration(millis: Long): String {
    val totalMinutes = (millis / 60_000L).toInt()
    if (totalMinutes <= 0) return "0 分"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> "$minutes 分"
        minutes == 0 -> "$hours 小时"
        else -> "$hours 小时 $minutes 分"
    }
}

/** 宫格用短写，避免「1 小时 23 分」撑破窄格 */
fun formatDurationCompact(millis: Long): String {
    val totalMinutes = (millis / 60_000L).toInt()
    if (totalMinutes <= 0) return "0分"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> "${minutes}分"
        minutes == 0 -> "${hours}小时"
        else -> "${hours}时${minutes}分"
    }
}

/** 时间戳视图用：细化到秒 */
fun formatDurationPrecise(millis: Long): String {
    val totalSec = (millis / 1000L).coerceAtLeast(0L)
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return when {
        hours > 0L -> "${hours}小时 ${minutes}分 ${seconds}秒"
        minutes > 0L -> "${minutes}分 ${seconds}秒"
        else -> "${seconds}秒"
    }
}

fun formatAxisMinutes(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> {
        val h = minutes / 60.0
        if (h == h.toInt().toDouble()) "${h.toInt()}h" else String.format(Locale.US, "%.1fh", h)
    }
}

private val fullDateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE", Locale.CHINA)
private val monthDayFmt = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val weekDayFmt = DateTimeFormatter.ofPattern("EEE", Locale.CHINA)

fun formatFullDate(date: LocalDate): String = date.format(fullDateFmt)

fun formatMonthDay(date: LocalDate): String = date.format(monthDayFmt)

fun formatWeekday(date: LocalDate): String = date.format(weekDayFmt)

fun periodPillLabel(period: com.liuji.xiaoqi.data.StatsPeriod, offset: Int): String =
    when (period) {
        com.liuji.xiaoqi.data.StatsPeriod.DAY -> when (offset) {
            0 -> "今天"
            -1 -> "昨天"
            else -> "${-offset} 天前"
        }

        com.liuji.xiaoqi.data.StatsPeriod.WEEK -> when (offset) {
            0 -> "本周"
            -1 -> "上周"
            else -> "${-offset} 周前"
        }

        com.liuji.xiaoqi.data.StatsPeriod.MONTH -> when (offset) {
            0 -> "本月"
            -1 -> "上月"
            else -> "${-offset} 月前"
        }
    }
