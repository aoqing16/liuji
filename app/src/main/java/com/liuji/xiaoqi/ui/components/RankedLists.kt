package com.liuji.xiaoqi.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuji.xiaoqi.data.AppUsageItem
import com.liuji.xiaoqi.data.Categories
import com.liuji.xiaoqi.data.CategoryUsageItem
import com.liuji.xiaoqi.ui.theme.Accent
import com.liuji.xiaoqi.ui.theme.TextFaint
import com.liuji.xiaoqi.ui.theme.TextMuted
import com.liuji.xiaoqi.ui.theme.TextStrong
import com.liuji.xiaoqi.ui.util.formatDuration
import com.liuji.xiaoqi.ui.util.formatDurationCompact

@Composable
fun AppRankedList(
    apps: List<AppUsageItem>,
    totalMillis: Long,
    iconLoader: (String) -> Drawable?,
    modifier: Modifier = Modifier,
    defaultLimit: Int = 10,
    onAppClick: ((AppUsageItem) -> Unit)? = null,
    clickHint: String? = "点进画面记录",
    emptyText: String = "这段时间没有应用使用记录",
) {
    val denom = totalMillis.coerceAtLeast(1L)
    var expanded by remember(apps) { mutableStateOf(false) }
    val canExpand = apps.size > defaultLimit
    val visible = if (canExpand && !expanded) apps.take(defaultLimit) else apps

    Column(modifier = modifier.fillMaxWidth()) {
        visible.forEachIndexed { index, app ->
            val hint = if (onAppClick != null && !clickHint.isNullOrBlank()) " · $clickHint" else ""
            RankRow(
                rank = index + 1,
                title = app.label,
                subtitle = app.categoryName + hint,
                millis = app.millis,
                fraction = app.millis.toFloat() / denom,
                barColor = app.categoryColor,
                leading = {
                    AppIconImage(
                        packageName = app.packageName,
                        drawable = iconLoader(app.packageName),
                        tint = app.categoryColor,
                    )
                },
                onClick = onAppClick?.let { { it(app) } },
            )
        }
        if (canExpand) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (expanded) "收起" else "展示更多（还有 ${apps.size - defaultLimit} 个）",
                    color = Accent,
                )
            }
        }
        if (apps.isEmpty()) {
            Text(
                text = emptyText,
                color = TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

/** 分类明细用：约 4 列宫格，可嵌在 verticalScroll 中 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppUsageGrid(
    apps: List<AppUsageItem>,
    iconLoader: (String) -> Drawable?,
    modifier: Modifier = Modifier,
    defaultLimit: Int = 50,
    onAppClick: ((AppUsageItem) -> Unit)? = null,
) {
    var expanded by remember(apps) { mutableStateOf(false) }
    val canExpand = apps.size > defaultLimit
    val visible = if (canExpand && !expanded) apps.take(defaultLimit) else apps

    Column(modifier = modifier.fillMaxWidth()) {
        if (apps.isEmpty()) {
            Text(
                text = "这段时间没有应用使用记录",
                color = TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            return
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 4,
        ) {
            visible.forEach { app ->
                AppUsageGridCell(
                    app = app,
                    icon = iconLoader(app.packageName),
                    onClick = onAppClick?.let { handler -> { handler(app) } },
                    modifier = Modifier.weight(1f),
                )
            }
            // 补齐最后一行空位，避免 weight 拉伸不均
            val remainder = visible.size % 4
            if (remainder != 0) {
                repeat(4 - remainder) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        if (canExpand) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (expanded) "收起" else "展示更多（还有 ${apps.size - defaultLimit} 个）",
                    color = Accent,
                )
            }
        }
    }
}

@Composable
private fun AppUsageGridCell(
    app: AppUsageItem,
    icon: Drawable?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(com.liuji.xiaoqi.ui.theme.SurfaceChip)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIconImage(
            packageName = app.packageName,
            drawable = icon,
            tint = app.categoryColor,
            size = 36.dp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.label,
            color = TextStrong,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = formatDurationCompact(app.millis),
            color = TextMuted,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
        )
    }
}

@Composable
fun CategoryRankedList(
    categories: List<CategoryUsageItem>,
    totalMillis: Long,
    iconLoader: (String) -> Drawable?,
    modifier: Modifier = Modifier,
    limit: Int = 12,
    /** 看板关闭可避免一次加载几十个小图标导致主线程卡顿 */
    showTopAppIcons: Boolean = true,
    onCategoryClick: ((CategoryUsageItem) -> Unit)? = null,
) {
    val denom = totalMillis.coerceAtLeast(1L)
    Column(modifier = modifier.fillMaxWidth()) {
        categories.take(limit).forEachIndexed { index, item ->
            RankRow(
                rank = index + 1,
                title = item.name,
                subtitle = if (onCategoryClick != null) "点击查看应用明细" else null,
                millis = item.millis,
                fraction = item.millis.toFloat() / denom,
                barColor = item.color,
                leading = {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(item.color),
                    )
                },
                extras = if (showTopAppIcons) {
                    {
                        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                            item.topPackages.take(3).forEach { pkg ->
                                AppIconImage(
                                    packageName = pkg,
                                    drawable = iconLoader(pkg),
                                    tint = item.color,
                                    size = 16.dp,
                                )
                            }
                        }
                    }
                } else {
                    null
                },
                onClick = onCategoryClick?.let { { it(item) } },
            )
        }
        if (categories.isEmpty()) {
            Text(
                text = "暂无分类数据",
                color = TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun RankRow(
    rank: Int,
    title: String,
    subtitle: String?,
    millis: Long,
    fraction: Float,
    barColor: Color,
    leading: @Composable () -> Unit,
    extras: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = rank.toString(),
                color = TextFaint,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(18.dp),
            )
            leading()
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextStrong,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (extras != null) {
                extras()
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = formatDuration(millis),
                color = TextStrong,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(com.liuji.xiaoqi.ui.theme.Track),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(barColor.copy(alpha = 0.75f)),
            )
        }
    }
}

@Composable
fun AppIconImage(
    drawable: Drawable?,
    tint: Color,
    size: androidx.compose.ui.unit.Dp = 22.dp,
    packageName: String? = null,
) {
    val bitmap = remember(packageName, drawable) {
        IconBitmapCache.getOrCreate(packageName, drawable)
    }
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(5.dp)),
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(5.dp))
                .background(tint.copy(alpha = 0.25f)),
        )
    }
}

/** 跨 Composition 复用解码后的 Bitmap，避免列表滑动时反复栅格化 */
object IconBitmapCache {
    private val lock = Any()
    private val cache = LinkedHashMap<String, Bitmap>(64, 0.75f, true)

    fun getOrCreate(packageName: String?, drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        val key = packageName?.takeIf { it.isNotBlank() }
        if (key != null) {
            synchronized(lock) {
                cache[key]?.let { cached ->
                    if (!cached.isRecycled) return cached
                    cache.remove(key)
                }
            }
        }
        val bmp = drawable.toBitmapOrNull() ?: return null
        if (key != null) {
            synchronized(lock) {
                cache[key] = bmp
                while (cache.size > 128) {
                    val eldest = cache.entries.iterator().next()
                    cache.remove(eldest.key)
                }
            }
        }
        return bmp
    }

    fun clear() {
        synchronized(lock) {
            cache.clear()
        }
    }
}

private fun Drawable.toBitmapOrNull(sizePx: Int = 96): Bitmap? = try {
    when {
        this is BitmapDrawable && bitmap != null && !bitmap.isRecycled -> {
            // 始终 copy，避免与 HindsightIconStore / 系统 Drawable 共享同一 Bitmap
            val src = bitmap
            val config = src.config ?: Bitmap.Config.ARGB_8888
            src.copy(if (config == Bitmap.Config.HARDWARE) Bitmap.Config.ARGB_8888 else config, false)
                ?: src.copy(Bitmap.Config.ARGB_8888, false)
        }
        else -> {
            // 复制再绘制，避免改动缓存 Drawable 的 bounds 引发卡顿/错乱
            val d = constantState?.newDrawable()?.mutate() ?: mutate()
            val width = (d.intrinsicWidth.takeIf { it > 0 } ?: sizePx).coerceAtMost(192)
            val height = (d.intrinsicHeight.takeIf { it > 0 } ?: sizePx).coerceAtMost(192)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            d.setBounds(0, 0, canvas.width, canvas.height)
            d.draw(canvas)
            bmp
        }
    }
} catch (_: Exception) {
    null
}

@Composable
fun CategoryLegend(
    categoryIds: List<String>,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categoryIds.distinct().forEach { id ->
            val cat = Categories.of(id)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(cat.color),
                )
                Spacer(Modifier.width(4.dp))
                Text(cat.name, color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}
