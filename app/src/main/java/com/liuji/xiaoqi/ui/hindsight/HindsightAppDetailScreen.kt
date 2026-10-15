package com.liuji.xiaoqi.ui.hindsight

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuji.xiaoqi.data.HindsightAppDetail
import com.liuji.xiaoqi.ui.components.AppIconImage
import com.liuji.xiaoqi.ui.theme.Accent
import com.liuji.xiaoqi.ui.theme.BorderDefault
import com.liuji.xiaoqi.ui.theme.CardBackground
import com.liuji.xiaoqi.ui.theme.PageBackground
import com.liuji.xiaoqi.ui.theme.TextMuted
import com.liuji.xiaoqi.ui.theme.TextStrong
import com.liuji.xiaoqi.ui.util.formatDuration

/**
 * 电脑云端应用详情（对齐桌面 Hindsight 弹层：图标/分类/总时长 + 24h 条形图 + 窗口标题明细）。
 */
@Composable
fun HindsightAppDetailScreen(
    detail: HindsightAppDetail?,
    loading: Boolean,
    iconLoader: (String) -> Drawable?,
    onClose: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(PageBackground)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .shadow(4.dp, RoundedCornerShape(20.dp), ambientColor = com.liuji.xiaoqi.ui.theme.ShadowTint, spotColor = com.liuji.xiaoqi.ui.theme.ShadowTint)
                .clip(RoundedCornerShape(20.dp))
                .background(CardBackground)
                .border(1.dp, BorderDefault, RoundedCornerShape(20.dp)),
        ) {
            when {
                loading && detail == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                    }
                }
                detail == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无详情", color = TextMuted)
                    }
                }
                else -> {
                    DetailHeader(detail = detail, icon = iconLoader(detail.processName), onClose = onClose)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BorderDefault.copy(alpha = 0.7f)),
                    )
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item(key = "chart") {
                            AppHourlyBars(
                                hourlyMillis = detail.hourlyMillis,
                                barColor = detail.categoryColor,
                            )
                        }
                        items(detail.windows, key = { it.title }) { win ->
                            WindowRow(
                                title = win.title,
                                millis = win.millis,
                                fraction = win.millis.toFloat() / detail.totalMillis.coerceAtLeast(1L),
                                barColor = detail.categoryColor,
                            )
                        }
                        if (detail.windows.isEmpty()) {
                            item {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 28.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("暂无窗口明细", color = TextMuted, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(
    detail: HindsightAppDetail,
    icon: Drawable?,
    onClose: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconImage(
            packageName = detail.processName,
            drawable = icon,
            tint = detail.categoryColor,
            size = 36.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Text(
                text = detail.displayName,
                color = TextStrong,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(detail.categoryColor),
                )
                Spacer(Modifier.width(5.dp))
                Text(text = detail.categoryName, color = TextMuted, fontSize = 12.sp)
            }
        }
        Text(
            text = formatDuration(detail.totalMillis),
            color = TextStrong,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = TextMuted)
        }
    }
}

@Composable
private fun AppHourlyBars(
    hourlyMillis: List<Long>,
    barColor: Color,
    modifier: Modifier = Modifier,
) {
    val peak = remember(hourlyMillis) { hourlyMillis.maxOrNull() ?: 0L }
    val hours = remember(hourlyMillis) {
        if (hourlyMillis.size >= 24) hourlyMillis.take(24) else hourlyMillis + List(24 - hourlyMillis.size) { 0L }
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(com.liuji.xiaoqi.ui.theme.SurfaceMuted)
                .border(1.dp, BorderDefault.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            hours.forEach { ms ->
                val frac = if (peak <= 0L) 0f else (ms.toFloat() / peak).coerceIn(0f, 1f)
                val h = frac.coerceAtLeast(if (ms > 0) 0.06f else 0f)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(h)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (ms > 0) barColor.copy(alpha = 0.55f) else Color.Transparent),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "6", "12", "18", "24").forEach { label ->
                Text(text = label, color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun WindowRow(
    title: String,
    millis: Long,
    fraction: Float,
    barColor: Color,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = TextStrong,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .width(72.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(com.liuji.xiaoqi.ui.theme.Track),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.04f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(99.dp))
                    .background(barColor.copy(alpha = 0.7f)),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatDuration(millis),
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.width(72.dp),
        )
    }
}
