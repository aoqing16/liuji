package com.liuji.xiaoqi.data

import androidx.compose.ui.graphics.Color
import java.time.LocalDate

enum class StatsPeriod {
    DAY,
    WEEK,
    MONTH,
}

data class AppCategory(
    val id: String,
    val name: String,
    val color: Color,
)

object Categories {
    val Social = AppCategory("social", "社交", Color(0xFF6FCF97))
    val Browse = AppCategory("browse", "浏览", Color(0xFF5B9BD5))
    val Video = AppCategory("video", "影音", Color(0xFF6B5B95))
    val Game = AppCategory("game", "游戏", Color(0xFFE88B9A))
    val Work = AppCategory("work", "办公", Color(0xFFF0B429))
    val Comm = AppCategory("comm", "工作沟通", Color(0xFF4ECDC4))
    val Shop = AppCategory("shop", "购物", Color(0xFFFF8A65))
    val Tool = AppCategory("tool", "工具", Color(0xFF8B7CF8))
    val System = AppCategory("system", "系统", Color(0xFF9CA3AF))
    val Other = AppCategory("other", "其他", Color(0xFFB0B7C3))

    val all: List<AppCategory> = listOf(
        Social, Browse, Video, Game, Work, Comm, Shop, Tool, System, Other,
    )

    private val byId = all.associateBy { it.id }

    /** 静态内置 lookup（Hindsight 等未加载 DB 时） */
    internal fun staticOf(id: String): AppCategory = byId[id] ?: Other

    fun of(id: String): AppCategory = CategoryRegistry.of(id)
}

data class CategorySlice(
    val categoryId: String,
    val millis: Long,
)

data class HourSlot(
    val hour: Int,
    val slices: List<CategorySlice>,
) {
    val totalMillis: Long get() = slices.sumOf { it.millis }
}

data class AppUsageItem(
    val packageName: String,
    val label: String,
    val categoryId: String,
    val millis: Long,
    val categoryName: String = Categories.of(categoryId).name,
    val categoryColor: Color = Categories.of(categoryId).color,
)

/** 电脑云端：某应用在窗口标题维度的明细 */
data class WindowUsageItem(
    val title: String,
    val millis: Long,
)

/** 电脑云端：应用详情（对应桌面 Hindsight 弹层） */
data class HindsightAppDetail(
    val processName: String,
    val displayName: String,
    val categoryId: String,
    val categoryName: String,
    val categoryColor: Color,
    val totalMillis: Long,
    /** 24 小时占用（毫秒），与当前 period/offset（及可选小时筛选）一致 */
    val hourlyMillis: List<Long>,
    val windows: List<WindowUsageItem>,
)

data class CategoryUsageItem(
    val categoryId: String,
    val name: String,
    val color: Color,
    val millis: Long,
    val topPackages: List<String>,
)

data class DayBucket(
    val date: LocalDate,
    val slices: List<CategorySlice>,
) {
    val totalMillis: Long get() = slices.sumOf { it.millis }
}

data class PeriodSummary(
    val period: StatsPeriod,
    val anchorDate: LocalDate,
    val rangeStart: LocalDate,
    val rangeEnd: LocalDate,
    val totalMillis: Long,
    val averageDailyMillis: Long,
    val hours: List<HourSlot>,
    val dayBuckets: List<DayBucket>,
    val apps: List<AppUsageItem>,
    val categories: List<CategoryUsageItem>,
    val appsByHour: Map<Int, List<AppUsageItem>> = emptyMap(),
) {
    fun appsForHour(hour: Int?): List<AppUsageItem> =
        if (hour == null) apps else appsByHour[hour].orEmpty()

    fun millisForHour(hour: Int?): Long =
        if (hour == null) totalMillis
        else hours.firstOrNull { it.hour == hour }?.totalMillis ?: 0L

    fun categoriesForHour(hour: Int?): List<CategoryUsageItem> {
        if (hour == null) return categories
        val slot = hours.firstOrNull { it.hour == hour } ?: return emptyList()
        return slot.slices.map { slice ->
            val cat = Categories.of(slice.categoryId)
            CategoryUsageItem(
                categoryId = slice.categoryId,
                name = cat.name,
                color = cat.color,
                millis = slice.millis,
                topPackages = appsForHour(hour)
                    .filter { it.categoryId == slice.categoryId }
                    .take(3)
                    .map { it.packageName },
            )
        }
    }
}
