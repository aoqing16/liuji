package com.liuji.xiaoqi.ui.dashboard

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import com.liuji.xiaoqi.data.StatsPeriod
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

fun periodAnchorDate(
    period: StatsPeriod,
    offset: Int,
    today: LocalDate = LocalDate.now(),
): LocalDate = when (period) {
    StatsPeriod.DAY -> today.plusDays(offset.toLong())
    StatsPeriod.WEEK -> today
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .plusWeeks(offset.toLong())
    StatsPeriod.MONTH -> today.withDayOfMonth(1).plusMonths(offset.toLong())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodDatePickerDialog(
    period: StatsPeriod,
    initial: LocalDate,
    onDismiss: () -> Unit,
    onToday: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val initialMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val maxMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val hint = when (period) {
        StatsPeriod.DAY -> "选择某一天"
        StatsPeriod.WEEK -> "选择日期，将打开该日所在周"
        StatsPeriod.MONTH -> "选择日期，将打开该日所在月"
    }
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
            TextButton(onClick = onToday) { Text("今天") }
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    ) {
        Text(hint)
        DatePicker(state = state)
    }
}
