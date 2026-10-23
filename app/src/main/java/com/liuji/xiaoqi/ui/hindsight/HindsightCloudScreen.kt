package com.liuji.xiaoqi.ui.hindsight

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuji.xiaoqi.data.AppUsageItem
import com.liuji.xiaoqi.data.PeriodSummary
import com.liuji.xiaoqi.data.StatsPeriod
import com.liuji.xiaoqi.ui.UsageUiState
import com.liuji.xiaoqi.ui.components.AppRankedList
import com.liuji.xiaoqi.ui.components.CategoryLegend
import com.liuji.xiaoqi.ui.components.CategoryRankedList
import com.liuji.xiaoqi.ui.components.HourlyChart
import com.liuji.xiaoqi.ui.components.PeriodBarChart
import com.liuji.xiaoqi.ui.dashboard.PeriodDatePickerDialog
import com.liuji.xiaoqi.ui.dashboard.filterAppsByQuery
import com.liuji.xiaoqi.ui.dashboard.periodAnchorDate
import com.liuji.xiaoqi.ui.theme.Accent
import com.liuji.xiaoqi.ui.theme.AccentSoft
import com.liuji.xiaoqi.ui.theme.BorderDefault
import com.liuji.xiaoqi.ui.theme.CardBackground
import com.liuji.xiaoqi.ui.theme.PageBackground
import com.liuji.xiaoqi.ui.theme.TextMuted
import com.liuji.xiaoqi.ui.theme.TextStrong
import com.liuji.xiaoqi.ui.util.formatDuration
import com.liuji.xiaoqi.ui.util.formatFullDate
import com.liuji.xiaoqi.ui.util.formatMonthDay
import com.liuji.xiaoqi.ui.util.formatWeekday
import com.liuji.xiaoqi.ui.util.periodPillLabel
import java.time.LocalDate

/**
 * 电脑 Hindsight 同版看板（与手机本机看板分离）。
 * 视觉与交互贴近桌面 Today：标题区 + PeriodCard 图表 + 排行卡片。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HindsightCloudScreen(
    state: UsageUiState,
    deviceLabel: String,
    onPeriodChange: (StatsPeriod) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJumpCurrent: () -> Unit,
    onJumpToDate: (LocalDate) -> Unit,
    onSelectHour: (Int?) -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchToPhone: () -> Unit,
    onMessageShown: () -> Unit,
    onAppClick: (AppUsageItem) -> Unit,
    onDashboardAppQuery: (String) -> Unit = {},
    onScroll: (index: Int, offset: Int) -> Unit = { _, _ -> },
    iconLoader: (String) -> Drawable?,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var pickingPeriod by remember { mutableStateOf(false) }
    val listState = remember {
        LazyListState(
            firstVisibleItemIndex = state.hindsightListFirstVisibleIndex,
            firstVisibleItemScrollOffset = state.hindsightListFirstVisibleOffset,
        )
    }
    val iconsReady = state.hindsightIconEpoch
    val cachedIconLoader: (String) -> Drawable? = remember(iconLoader, iconsReady) {
        { name -> iconLoader(name) }
    }

    // 仅在离开组合时写回滚动位置，避免滑动过程中刷 StateFlow 导致整树重组
    DisposableEffect(Unit) {
        onDispose {
            onScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }

    if (pickingPeriod) {
        PeriodDatePickerDialog(
            period = state.period,
            initial = periodAnchorDate(state.period, state.offset),
            onDismiss = { pickingPeriod = false },
            onToday = {
                onJumpCurrent()
                pickingPeriod = false
            },
            onConfirm = { date ->
                onJumpToDate(date)
                pickingPeriod = false
            },
        )
    }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        onMessageShown()
    }

    Scaffold(
        containerColor = PageBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing || state.cloudSyncing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LaunchedEffect(state.dashboardAppQuery, state.summary != null) {
                val query = state.dashboardAppQuery.trim()
                if (query.isEmpty() || state.summary == null) return@LaunchedEffect
                snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 3 }
                listState.animateScrollToItem(3)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "hs-header") {
                    CloudHeader(
                        state = state,
                        deviceLabel = deviceLabel,
                        onOpenSettings = onOpenSettings,
                        onRefresh = onRefresh,
                        onSwitchToPhone = onSwitchToPhone,
                        onAppQueryChange = onDashboardAppQuery,
                    )
                }

                item(key = "hs-period-tabs") {
                    PeriodTabs(
                        period = state.period,
                        onPeriodChange = onPeriodChange,
                    )
                }

                item(key = "hs-chart") {
                    HindsightPeriodCard(
                        state = state,
                        summary = state.summary,
                        onPrev = onPrev,
                        onNext = onNext,
                        onOpenCalendar = { pickingPeriod = true },
                        onSelectHour = onSelectHour,
                    )
                }

                if (state.loading && state.summary == null) {
                    item(key = "hs-loading") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                        }
                    }
                }

                state.summary?.let { data ->
                    val hour = if (state.period == StatsPeriod.DAY) state.selectedHour else null
                    val scopedApps = data.appsForHour(hour)
                    val appQuery = state.dashboardAppQuery.trim()
                    val filteredApps = filterAppsByQuery(scopedApps, appQuery)
                    val scopedCategories = data.categoriesForHour(hour)
                    val scopedMillis = data.millisForHour(hour)

                    item(key = "hs-ranks") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            HindsightRankCard(
                                title = when {
                                    hour != null -> "${hour}:00 最常用应用"
                                    state.period == StatsPeriod.DAY -> "最常用应用"
                                    state.period == StatsPeriod.WEEK -> "本周最常用应用"
                                    else -> "本月最常用应用"
                                },
                                trailing = formatDuration(scopedMillis),
                                selectionLabel = hour?.let { "$it:00–$it:59" },
                            ) {
                                if (filteredApps.isEmpty()) {
                                    EmptyHint(
                                        if (appQuery.isNotEmpty()) {
                                            "未找到匹配「$appQuery」的应用"
                                        } else {
                                            "这段时间没有应用使用记录"
                                        },
                                    )
                                } else {
                                    AppRankedList(
                                        apps = filteredApps,
                                        totalMillis = scopedMillis,
                                        iconLoader = cachedIconLoader,
                                        defaultLimit = if (appQuery.isNotEmpty()) Int.MAX_VALUE else 10,
                                        onAppClick = onAppClick,
                                        clickHint = "点进详情",
                                    )
                                }
                            }

                            HindsightRankCard(
                                title = when {
                                    hour != null -> "${hour}:00 分类构成"
                                    else -> "分类构成"
                                },
                                selectionLabel = hour?.let { "$it:00–$it:59" },
                            ) {
                                if (scopedCategories.isEmpty()) {
                                    EmptyHint()
                                } else {
                                    CategoryRankedList(
                                        categories = scopedCategories,
                                        totalMillis = scopedMillis,
                                        iconLoader = cachedIconLoader,
                                    )
                                }
                            }
                        }
                    }
                }

                state.error?.let { err ->
                    item {
                        Text(err, color = com.liuji.xiaoqi.ui.theme.Danger, fontSize = 13.sp)
                    }
                }

                item {
                    Text(
                        "只读来自云盘电脑记录 · 不会写回云盘",
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudHeader(
    state: UsageUiState,
    deviceLabel: String,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onSwitchToPhone: () -> Unit,
    onAppQueryChange: (String) -> Unit,
) {
    var searchExpanded by remember {
        mutableStateOf(state.dashboardAppQuery.isNotBlank())
    }
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudDone,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("电脑看板", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(AccentSoft)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(deviceLabel, color = Accent, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = {
                    if (searchExpanded) {
                        searchExpanded = false
                        onAppQueryChange("")
                    } else {
                        searchExpanded = true
                    }
                },
            ) {
                Icon(
                    imageVector = if (searchExpanded) Icons.Outlined.Close else Icons.Outlined.Search,
                    contentDescription = if (searchExpanded) "关闭搜索" else "搜索应用",
                    tint = Accent,
                )
            }
            IconButton(onClick = onRefresh, enabled = !state.cloudSyncing) {
                Icon(Icons.Outlined.Sync, contentDescription = "同步", tint = Accent)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "设置", tint = Accent)
            }
        }
        if (searchExpanded) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.dashboardAppQuery,
                onValueChange = onAppQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("搜索应用名或包名", color = TextMuted, fontSize = 13.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = BorderDefault,
                    focusedTextColor = TextStrong,
                    unfocusedTextColor = TextStrong,
                    cursorColor = Accent,
                ),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (state.period) {
                StatsPeriod.DAY -> "今天"
                StatsPeriod.WEEK -> "本周"
                StatsPeriod.MONTH -> "本月"
            },
            color = TextStrong,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
        )
        Spacer(Modifier.height(4.dp))
        val meta = state.summary?.let { s ->
            when (state.period) {
                StatsPeriod.DAY -> formatFullDate(s.rangeStart)
                else -> "${formatMonthDay(s.rangeStart)} – ${formatMonthDay(s.rangeEnd)} · 日均 ${formatDuration(s.averageDailyMillis)}"
            }
        } ?: "电脑使用数据 · 云端只读"
        Text(meta, color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onSwitchToPhone) {
            Text("切换回手机看板", color = Accent, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PeriodTabs(
    period: StatsPeriod,
    onPeriodChange: (StatsPeriod) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(1.dp, BorderDefault, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(
            StatsPeriod.DAY to "日",
            StatsPeriod.WEEK to "周",
            StatsPeriod.MONTH to "月",
        ).forEach { (p, label) ->
            val selected = period == p
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) AccentSoft else Color.Transparent)
                    .clickable { onPeriodChange(p) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) Accent else TextMuted,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun HindsightPeriodCard(
    state: UsageUiState,
    summary: PeriodSummary?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenCalendar: () -> Unit,
    onSelectHour: (Int?) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(16.dp), ambientColor = com.liuji.xiaoqi.ui.theme.ShadowTint, spotColor = com.liuji.xiaoqi.ui.theme.ShadowTint)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .border(1.dp, BorderDefault, RoundedCornerShape(16.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (state.period) {
                    StatsPeriod.DAY -> "24 小时活动"
                    else -> "每日活动"
                },
                color = TextStrong,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onPrev, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = null, tint = TextMuted)
            }
            TextButton(onClick = onOpenCalendar) {
                Text(
                    periodPillLabel(state.period, state.offset),
                    color = TextStrong,
                    fontSize = 12.sp,
                )
            }
            IconButton(
                onClick = onNext,
                enabled = state.offset < 0,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextMuted)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(BorderDefault.copy(alpha = 0.7f)),
        )
        Column(Modifier.padding(14.dp)) {
            when {
                summary == null && state.loading -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                    }
                }
                summary == null -> EmptyHint()
                state.period == StatsPeriod.DAY -> {
                    HourlyChart(
                        hours = summary.hours,
                        selectedHour = state.selectedHour,
                        onHourClick = onSelectHour,
                    )
                    Spacer(Modifier.height(12.dp))
                    CategoryLegend(categoryIds = summary.categories.map { it.categoryId })
                }
                else -> {
                    PeriodBarChart(
                        days = summary.dayBuckets,
                        labelFor = {
                            if (state.period == StatsPeriod.MONTH) formatMonthDay(it)
                            else formatWeekday(it)
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    CategoryLegend(categoryIds = summary.categories.map { it.categoryId })
                }
            }
        }
    }
}

@Composable
private fun HindsightRankCard(
    title: String,
    trailing: String? = null,
    selectionLabel: String? = null,
    body: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp), ambientColor = com.liuji.xiaoqi.ui.theme.ShadowTint, spotColor = com.liuji.xiaoqi.ui.theme.ShadowTint)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .border(1.dp, BorderDefault, RoundedCornerShape(16.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                color = TextStrong,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (selectionLabel != null) {
                Text(selectionLabel, color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.width(8.dp))
            }
            if (trailing != null) {
                Text(trailing, color = TextStrong, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(BorderDefault.copy(alpha = 0.7f)),
        )
        Box(Modifier.padding(10.dp)) {
            body()
        }
    }
}

@Composable
private fun EmptyHint(text: String = "暂无活动") {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = TextMuted, fontSize = 13.sp)
    }
}
