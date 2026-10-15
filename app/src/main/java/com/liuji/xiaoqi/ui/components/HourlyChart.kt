package com.liuji.xiaoqi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuji.xiaoqi.data.Categories
import com.liuji.xiaoqi.data.HourSlot
import com.liuji.xiaoqi.ui.theme.TextFaint
import com.liuji.xiaoqi.ui.theme.TextMuted
import com.liuji.xiaoqi.ui.util.formatAxisMinutes
import kotlin.math.ceil

@Composable
fun HourlyChart(
    hours: List<HourSlot>,
    selectedHour: Int?,
    onHourClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val peakMinutes = remember(hours) {
        hours.maxOfOrNull { (it.totalMillis / 60_000L).toInt() } ?: 0
    }
    val yMax = remember(peakMinutes) {
        when {
            peakMinutes <= 60 -> 60
            else -> ceil(peakMinutes / 15.0).toInt() * 15
        }
    }
    val yTicks = listOf(yMax / 4, yMax / 2, yMax * 3 / 4, yMax)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                yTicks.reversed().forEach { tick ->
                    Text(
                        text = formatAxisMinutes(tick),
                        color = TextFaint,
                        fontSize = 10.sp,
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    yTicks.forEach { _ ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0x14000000)),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    hours.forEach { slot ->
                        val minutes = (slot.totalMillis / 60_000L).toInt()
                        val heightFraction =
                            if (yMax == 0 || minutes <= 0) 0f else minutes / yMax.toFloat()
                        val dimmed = selectedHour != null && selectedHour != slot.hour
                        val selected = selectedHour == slot.hour
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onHourClick(slot.hour) },
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (heightFraction > 0f) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth(if (selected) 1f else 0.85f)
                                        .fillMaxHeight(heightFraction)
                                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                                ) {
                                    slot.slices.asReversed().forEach { slice ->
                                        val weight = slice.millis.toFloat().coerceAtLeast(1f)
                                        val color = Categories.of(slice.categoryId).color
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .weight(weight)
                                                .background(
                                                    color.copy(alpha = if (dimmed) 0.35f else 1f),
                                                ),
                                        )
                                    }
                                }
                            } else {
                                Spacer(Modifier.height(1.dp))
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("00", "06", "12", "18", "24").forEach { label ->
                Text(label, color = TextMuted, fontSize = 10.sp)
            }
        }
    }
}
