package com.liuji.xiaoqi.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuji.xiaoqi.data.Categories
import com.liuji.xiaoqi.data.frame.TimelineItem
import com.liuji.xiaoqi.ui.TimelineGranularity
import com.liuji.xiaoqi.ui.theme.Accent
import com.liuji.xiaoqi.ui.theme.AccentSoft
import com.liuji.xiaoqi.ui.theme.CardBackground
import com.liuji.xiaoqi.ui.theme.SurfaceChip
import com.liuji.xiaoqi.ui.theme.TextMuted
import com.liuji.xiaoqi.ui.theme.TextStrong
import com.liuji.xiaoqi.ui.util.formatDuration
import com.liuji.xiaoqi.ui.util.formatDurationPrecise
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.min

private val zone: ZoneId = ZoneId.systemDefault()
private val hm = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone)
private val hourFmt = DateTimeFormatter.ofPattern("HH:00").withZone(zone)
private val minuteFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
private val TimeFaint = com.liuji.xiaoqi.ui.theme.TextTimeFaint

data class TimelineBucketApp(
    val packageName: String,
    val label: String,
    val categoryId: String,
    val millis: Long,
)

data class TimelineBucket(
    val startMillis: Long,
    val label: String,
    val apps: List<TimelineBucketApp>,
)

fun bucketTimeline(
    items: List<TimelineItem>,
    granularity: TimelineGranularity,
): List<TimelineBucket> {
    if (granularity == TimelineGranularity.SECOND || items.isEmpty()) return emptyList()
    val unit = when (granularity) {
        TimelineGranularity.HOUR -> ChronoUnit.HOURS
        TimelineGranularity.MINUTE -> ChronoUnit.MINUTES
        TimelineGranularity.SECOND -> return emptyList()
    }
    val labelFmt = when (granularity) {
        TimelineGranularity.HOUR -> hourFmt
        else -> minuteFmt
    }
    val acc = LinkedHashMap<Long, LinkedHashMap<String, TimelineBucketApp>>()
    for (item in items) {
        var cursor = item.startMillis
        val end = item.endMillis
        while (cursor < end) {
            val zdt = Instant.ofEpochMilli(cursor).atZone(zone)
            val bucketStart = zdt.truncatedTo(unit).toInstant().toEpochMilli()
            val nextBucket = zdt.truncatedTo(unit).plus(1, unit).toInstant().toEpochMilli()
            val sliceEnd = min(end, nextBucket)
            val dur = sliceEnd - cursor
            if (dur > 0L) {
                val apps = acc.getOrPut(bucketStart) { LinkedHashMap() }
                val existing = apps[item.packageName]
                apps[item.packageName] = if (existing == null) {
                    TimelineBucketApp(
                        packageName = item.packageName,
                        label = item.label,
                        categoryId = item.categoryId,
                        millis = dur,
                    )
                } else {
                    existing.copy(millis = existing.millis + dur)
                }
            }
            cursor = sliceEnd
        }
    }
    return acc.map { (start, apps) ->
        TimelineBucket(
            startMillis = start,
            label = labelFmt.format(Instant.ofEpochMilli(start)),
            apps = apps.values.sortedByDescending { it.millis },
        )
    }
}

/** 裁剪到 [rangeStart, rangeEnd)，用于钻取后只看某一小时 / 分钟 */
fun clipTimeline(items: List<TimelineItem>, rangeStart: Long, rangeEnd: Long): List<TimelineItem> =
    items.mapNotNull { item ->
        val s = maxOf(item.startMillis, rangeStart)
        val e = minOf(item.endMillis, rangeEnd)
        if (e > s) item.copy(startMillis = s, endMillis = e) else null
    }

fun LazyListScope.timelineItems(
    items: List<TimelineItem>,
    granularity: TimelineGranularity = TimelineGranularity.SECOND,
    iconLoader: (String) -> Drawable?,
    onBucketClick: ((Long) -> Unit)? = null,
) {
    if (items.isEmpty()) {
        item(key = "timeline-empty") {
            Text(
                "暂无前台时间线",
                color = TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        return
    }
    if (granularity == TimelineGranularity.SECOND) {
        items(
            items = items,
            key = { "${it.packageName}-${it.startMillis}-${it.endMillis}" },
            contentType = { "timeline" },
        ) { item ->
            TimelineSessionRow(item = item, iconLoader = iconLoader)
        }
        return
    }
    val buckets = bucketTimeline(items, granularity)
    if (buckets.isEmpty()) {
        item(key = "timeline-empty") {
            Text(
                "暂无前台时间线",
                color = TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        return
    }
    items(
        items = buckets,
        key = { "bucket-${it.startMillis}" },
        contentType = { "timeline-bucket" },
    ) { bucket ->
        TimelineBucketRow(
            bucket = bucket,
            iconLoader = iconLoader,
            onClick = onBucketClick?.let { handler -> { handler(bucket.startMillis) } },
        )
    }
}

private const val STACK_MAX_ICONS = 5
private val StackIconSize = 30.dp
private val StackIconStep = 18.dp

/** 一个时段一行：同档 App 图标重叠堆叠，点击进入下一级 */
@Composable
private fun TimelineBucketRow(
    bucket: TimelineBucket,
    iconLoader: (String) -> Drawable?,
    onClick: (() -> Unit)?,
) {
    val shown = bucket.apps.take(STACK_MAX_ICONS)
    val overflow = bucket.apps.size - shown.size
    val total = bucket.apps.sumOf { it.millis }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceChip)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = bucket.label,
            color = TextStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(52.dp),
        )
        Box(Modifier.height(StackIconSize)) {
            shown.forEachIndexed { i, app ->
                val icon = remember(app.packageName) { iconLoader(app.packageName) }
                Box(
                    Modifier
                        .padding(start = StackIconStep * i)
                        .zIndex((shown.size - i).toFloat())
                        .size(StackIconSize)
                        .clip(CircleShape)
                        .background(CardBackground)
                        .border(2.dp, CardBackground, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIconImage(
                        packageName = app.packageName,
                        drawable = icon,
                        tint = Categories.of(app.categoryId).color,
                        size = StackIconSize - 6.dp,
                    )
                }
            }
            if (overflow > 0) {
                Box(
                    Modifier
                        .padding(start = StackIconStep * shown.size)
                        .size(StackIconSize)
                        .clip(CircleShape)
                        .background(AccentSoft)
                        .border(2.dp, CardBackground, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "+$overflow",
                        color = Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(
                text = formatDuration(total),
                color = Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = "${bucket.apps.size} 个应用",
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        if (onClick != null) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "查看更细时段",
                tint = TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 文字层级：
 * 1. App 名称（主标题）
 * 2. 停留时长（强调色）
 * 3. 进入/离开时间点
 * 4. 「进入/离开」辅助标签（最弱）
 */
@Composable
private fun TimelineSessionRow(
    item: TimelineItem,
    iconLoader: (String) -> Drawable?,
) {
    val icon = remember(item.packageName) { iconLoader(item.packageName) }
    val accent = Categories.of(item.categoryId).color
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(20.dp)
                .padding(top = 6.dp),
        ) {
            TimelineDot(accent)
            Spacer(
                Modifier
                    .width(2.dp)
                    .height(52.dp)
                    .background(accent.copy(alpha = 0.28f)),
            )
            TimelineDot(accent.copy(alpha = 0.5f))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconImage(
                    packageName = item.packageName,
                    drawable = icon,
                    tint = accent,
                    size = 28.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    item.label,
                    color = TextStrong,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            TimelineEdge(
                time = hm.format(Instant.ofEpochMilli(item.startMillis)),
                verb = "进入",
                timeStrong = true,
            )
            Text(
                text = "停留 ${formatDurationPrecise(item.durationMillis)}",
                color = Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
            )
            TimelineEdge(
                time = hm.format(Instant.ofEpochMilli(item.endMillis)),
                verb = "离开",
                timeStrong = false,
            )
        }
    }
}

@Composable
private fun TimelineEdge(
    time: String,
    verb: String,
    timeStrong: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = time,
            color = if (timeStrong) TextStrong else TextMuted,
            fontSize = if (timeStrong) 13.sp else 12.sp,
            fontWeight = if (timeStrong) FontWeight.SemiBold else FontWeight.Normal,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = verb,
            color = TimeFaint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun TimelineDot(color: Color) {
    Spacer(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}
