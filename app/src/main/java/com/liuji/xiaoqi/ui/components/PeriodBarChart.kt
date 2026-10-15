package com.liuji.xiaoqi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuji.xiaoqi.data.Categories
import com.liuji.xiaoqi.data.DayBucket
import com.liuji.xiaoqi.ui.theme.TextMuted
import com.liuji.xiaoqi.ui.theme.TextStrong
import com.liuji.xiaoqi.ui.util.formatDuration
import com.liuji.xiaoqi.ui.util.formatWeekday
import java.time.LocalDate
import kotlin.math.max

@Composable
fun PeriodBarChart(
    days: List<DayBucket>,
    labelFor: (LocalDate) -> String = { formatWeekday(it) },
    modifier: Modifier = Modifier,
) {
    val maxMillis = remember(days) {
        max(1L, days.maxOfOrNull { it.totalMillis } ?: 1L)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        days.forEach { day ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = labelFor(day.date),
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.width(36.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(androidx.compose.ui.graphics.Color(0xFFF1F3F7)),
                ) {
                    if (day.totalMillis > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((day.totalMillis.toFloat() / maxMillis.toFloat()).coerceIn(0.02f, 1f))
                                .clip(RoundedCornerShape(6.dp)),
                        ) {
                            day.slices.forEach { slice ->
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .weight(slice.millis.toFloat().coerceAtLeast(1f))
                                        .background(Categories.of(slice.categoryId).color),
                                )
                            }
                        }
                    }
                }
                Text(
                    text = formatDuration(day.totalMillis),
                    color = TextStrong,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .width(72.dp),
                )
            }
        }
        if (days.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("暂无数据", color = TextMuted, fontSize = 13.sp)
        }
    }
}
