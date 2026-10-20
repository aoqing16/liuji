package com.liuji.xiaoqi.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuji.xiaoqi.data.StatsPeriod
import com.liuji.xiaoqi.ui.theme.Accent
import com.liuji.xiaoqi.ui.theme.TextMuted
import com.liuji.xiaoqi.ui.theme.TextStrong
import com.liuji.xiaoqi.ui.util.periodPillLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialog(
    currentPeriod: StatsPeriod,
    offset: Int,
    exporting: Boolean,
    onDismiss: () -> Unit,
    onExport: (StatsPeriod, Boolean) -> Unit,
    onExportRange: (LocalDate, LocalDate, Boolean) -> Unit = { _, _, _ -> },
) {
    val zone = ZoneId.systemDefault()
    val today = remember { LocalDate.now(zone) }
    var rangeStart by remember { mutableStateOf(today.minusDays(6)) }
    var rangeEnd by remember { mutableStateOf(today) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var includeScreenTexts by remember { mutableStateOf(true) }
    val dateFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }

    AlertDialog(
        onDismissRequest = { if (!exporting) onDismiss() },
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "导出使用数据",
                fontWeight = FontWeight.SemiBold,
                color = TextStrong,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "导出 Markdown（时长、时间戳、画面文本），便于 AI 阅读。隐私应用的帧文本不会写入。",
                    color = TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "导出字段设置",
                    color = TextStrong,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = includeScreenTexts,
                        onCheckedChange = { includeScreenTexts = it },
                        enabled = !exporting,
                    )
                    Column(Modifier.weight(1f)) {
                        Text("屏幕文字记录数据", color = TextStrong, fontSize = 14.sp)
                        Text(
                            "帧视图里的画面文案。取消后不读取、不写入这部分数据。",
                            color = TextMuted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
                ExportOptionButton(
                    title = "导出日统计",
                    subtitle = "范围：${periodPillLabel(StatsPeriod.DAY, if (currentPeriod == StatsPeriod.DAY) offset else 0)}",
                    enabled = !exporting,
                    emphasized = currentPeriod == StatsPeriod.DAY,
                    onClick = { onExport(StatsPeriod.DAY, includeScreenTexts) },
                )
                ExportOptionButton(
                    title = "导出周统计",
                    subtitle = "范围：${periodPillLabel(StatsPeriod.WEEK, if (currentPeriod == StatsPeriod.WEEK) offset else 0)}",
                    enabled = !exporting,
                    emphasized = currentPeriod == StatsPeriod.WEEK,
                    onClick = { onExport(StatsPeriod.WEEK, includeScreenTexts) },
                )
                ExportOptionButton(
                    title = "导出月统计",
                    subtitle = "范围：${periodPillLabel(StatsPeriod.MONTH, if (currentPeriod == StatsPeriod.MONTH) offset else 0)}",
                    enabled = !exporting,
                    emphasized = currentPeriod == StatsPeriod.MONTH,
                    onClick = { onExport(StatsPeriod.MONTH, includeScreenTexts) },
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    "自定义区间",
                    color = TextStrong,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "自由选择起止日期（含首尾两天）",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = { pickingStart = true },
                        enabled = !exporting,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column {
                            Text("开始", fontSize = 11.sp, color = TextMuted)
                            Text(rangeStart.format(dateFmt), fontSize = 13.sp)
                        }
                    }
                    OutlinedButton(
                        onClick = { pickingEnd = true },
                        enabled = !exporting,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column {
                            Text("结束", fontSize = 11.sp, color = TextMuted)
                            Text(rangeEnd.format(dateFmt), fontSize = 13.sp)
                        }
                    }
                }
                val rangeOk = !rangeEnd.isBefore(rangeStart) && !rangeEnd.isAfter(today)
                Button(
                    onClick = { onExportRange(rangeStart, rangeEnd, includeScreenTexts) },
                    enabled = !exporting && rangeOk,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (rangeOk) {
                            "导出 ${rangeStart.format(dateFmt)} ~ ${rangeEnd.format(dateFmt)}"
                        } else {
                            "请选择有效日期区间"
                        },
                    )
                }
                if (!rangeOk) {
                    Text(
                        "结束日期不能早于开始日期，也不能晚于今天",
                        color = Accent,
                        fontSize = 11.sp,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !exporting) {
                Text("取消")
            }
        },
    )

    if (pickingStart) {
        ExportDatePickerDialog(
            initial = rangeStart,
            maxDate = today,
            onDismiss = { pickingStart = false },
            onConfirm = { picked ->
                rangeStart = picked
                if (rangeEnd.isBefore(picked)) rangeEnd = picked
                pickingStart = false
            },
        )
    }
    if (pickingEnd) {
        ExportDatePickerDialog(
            initial = rangeEnd,
            maxDate = today,
            onDismiss = { pickingEnd = false },
            onConfirm = { picked ->
                rangeEnd = picked
                if (rangeStart.isAfter(picked)) rangeStart = picked
                pickingEnd = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportDatePickerDialog(
    initial: LocalDate,
    maxDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    // DatePicker 用 UTC 午夜毫秒表示「日历日」
    val initialMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val maxMillis = maxDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis <= maxMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis ?: return@TextButton
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onConfirm(date)
                },
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun ExportOptionButton(
    title: String,
    subtitle: String,
    enabled: Boolean,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    if (emphasized) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 2.dp)) {
                Text(title)
                Text(subtitle, fontSize = 11.sp)
            }
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 2.dp)) {
                Text(title)
                Text(subtitle, fontSize = 11.sp, color = TextMuted)
            }
        }
    }
}
