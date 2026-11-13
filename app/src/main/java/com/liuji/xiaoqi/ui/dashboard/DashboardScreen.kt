package com.liuji.xiaoqi.ui.dashboard

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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Today
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuji.xiaoqi.data.AppUsageItem
import com.liuji.xiaoqi.data.PeriodSummary
import com.liuji.xiaoqi.data.StatsPeriod
import com.liuji.xiaoqi.ui.DataSource
import com.liuji.xiaoqi.ui.DayViewMode
import com.liuji.xiaoqi.ui.TimelineDrillLevel
import com.liuji.xiaoqi.ui.TimelineGranularity
import com.liuji.xiaoqi.ui.UsageUiState
import com.liuji.xiaoqi.ui.components.clipTimeline
import com.liuji.xiaoqi.ui.effectiveTimelineGranularity
import com.liuji.xiaoqi.ui.components.AppListSkeleton
import com.liuji.xiaoqi.ui.components.AppRankedList
import com.liuji.xiaoqi.ui.components.CategoryLegend
import com.liuji.xiaoqi.ui.components.CategoryListSkeleton
import com.liuji.xiaoqi.ui.components.CategoryRankedList
import com.liuji.xiaoqi.ui.components.ChartSkeleton
import com.liuji.xiaoqi.ui.components.HourlyChart
import com.liuji.xiaoqi.ui.components.PeriodBarChart
import com.liuji.xiaoqi.ui.components.SkeletonBar
import com.liuji.xiaoqi.ui.components.TimelineListSkeleton
import com.liuji.xiaoqi.ui.components.bucketTimeline
import com.liuji.xiaoqi.ui.components.timelineItems
import com.liuji.xiaoqi.ui.theme.Accent
import com.liuji.xiaoqi.ui.theme.AccentSoft
import com.liuji.xiaoqi.ui.theme.BorderDefault
import com.liuji.xiaoqi.ui.theme.CardBackground
import com.liuji.xiaoqi.ui.theme.Danger
import com.liuji.xiaoqi.ui.theme.Elevation
import com.liuji.xiaoqi.ui.theme.PageBackground
import com.liuji.xiaoqi.ui.theme.ShadowTint
import com.liuji.xiaoqi.ui.theme.ShadowTintStrong
import com.liuji.xiaoqi.ui.theme.ShapeCard
import com.liuji.xiaoqi.ui.theme.Space
import com.liuji.xiaoqi.ui.theme.Stroke
import com.liuji.xiaoqi.ui.theme.Success
import com.liuji.xiaoqi.ui.theme.TextMuted
import com.liuji.xiaoqi.ui.theme.TextStrong
import com.liuji.xiaoqi.ui.util.formatDuration
import com.liuji.xiaoqi.ui.util.formatFullDate
import com.liuji.xiaoqi.ui.util.formatMonthDay
import com.liuji.xiaoqi.ui.util.horizontalPeriodSwipe
import com.liuji.xiaoqi.ui.util.periodPillLabel
import com.liuji.xiaoqi.ui.util.rememberAccelerationFlingBehavior
import java.time.LocalDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: UsageUiState,
    onPeriodChange: (StatsPeriod) -> Unit,
    onShiftPeriod: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJumpCurrent: () -> Unit,
    onJumpToDate: (LocalDate) -> Unit,
    onSelectHour: (Int?) -> Unit,
    onOpenExport: () -> Unit,
    onDismissExport: () -> Unit,
    onExportPeriod: (StatsPeriod, Boolean) -> Unit,
    onExportDateRange: (LocalDate, LocalDate, Boolean) -> Unit = { _, _, _ -> },
    onMessageShown: () -> Unit,
    onCategoryClick: ((com.liuji.xiaoqi.data.CategoryUsageItem) -> Unit)? = null,
    onDayViewMode: (DayViewMode) -> Unit,
    onTimelineGranularity: (TimelineGranularity) -> Unit = {},
    onTimelineBucketClick: (Long) -> Unit = {},
    onTimelineDrillUp: () -> Unit = {},
    onAppClick: (AppUsageItem) -> Unit,
    onRefresh: () -> Unit,
    onDataSource: (DataSource) -> Unit,
    onDashboardAppQuery: (String) -> Unit = {},
    onScroll: (index: Int, offset: Int) -> Unit = { _, _ -> },
    iconLoader: (String) -> Drawable?,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var pickingPeriod by remember { mutableStateOf(false) }
    val listState = remember {
        LazyListState(
            firstVisibleItemIndex = state.dashboardFirstVisibleIndex,
            firstVisibleItemScrollOffset = state.dashboardFirstVisibleOffset,
        )
    }
    val scope = rememberCoroutineScope()
    val flingBehavior = rememberAccelerationFlingBehavior()
    val iconCache = remember { mutableMapOf<String, Drawable?>() }
    val cachedIconLoader: (String) -> Drawable? = remember(iconLoader) {
        { pkg -> iconCache.getOrPut(pkg) { iconLoader(pkg) } }
    }
    val canScrollUp by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val canScrollDown by remember {
        derivedStateOf { listState.canScrollForward }
    }

    // 仅在离开组合时写回滚动位置，避免滑动过程中刷 StateFlow 导致整树重组
    DisposableEffect(Unit) {
        onDispose {
            onScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        onMessageShown()
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

    if (state.showExportDialog) {
        ExportDialog(
            currentPeriod = state.period,
            offset = state.offset,
            exporting = state.exporting,
            onDismiss = onDismissExport,
            onExport = onExportPeriod,
            onExportRange = onExportDateRange,
        )
    }

    Scaffold(
        // 外层 PhoneHomeScaffold 已铺 PageBackground，避免双层全屏 Overdraw
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val summary = state.summary?.takeIf { it.period == state.period }
        val showTimeline =
            state.period == StatsPeriod.DAY && state.dayViewMode == DayViewMode.TIMELINE
        val drill = state.timelineDrillStack.lastOrNull()
        val effectiveGranularity = state.effectiveTimelineGranularity
        val visibleTimeline = remember(state.timeline, drill) {
            if (drill == null) state.timeline
            else clipTimeline(state.timeline, drill.rangeStart, drill.rangeEnd)
        }
        val timelineBucketCount = remember(visibleTimeline, effectiveGranularity) {
            if (effectiveGranularity == TimelineGranularity.SECOND) {
                0
            } else {
                bucketTimeline(visibleTimeline, effectiveGranularity).size
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
        LaunchedEffect(state.dashboardAppQuery, state.period, showTimeline, summary != null) {
            val query = state.dashboardAppQuery.trim()
            if (query.isEmpty() || summary == null) return@LaunchedEffect
            // header + period-toggle + data-source (+ day-mode) 之后才是应用列表卡片
            val index = if (showTimeline) {
                4
            } else if (state.period == StatsPeriod.DAY) {
                5
            } else {
                4
            }
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > index }
            listState.animateScrollToItem(index)
        }
        LazyColumn(
            state = listState,
            flingBehavior = if (showTimeline) flingBehavior else ScrollableDefaults.flingBehavior(),
            // 整页可左右滑切换日/周/月；手势在确认横向后才接管，不挡纵向滚动
            // 本 App 自带无障碍服务时，隐藏列表 Semantics 可大幅降低滚动时的 a11y 广播开销
            modifier = Modifier
                .fillMaxSize()
                .horizontalPeriodSwipe(
                    onSwipeLeft = { onShiftPeriod(1) },
                    onSwipeRight = { onShiftPeriod(-1) },
                )
                .semantics { hideFromAccessibility() },
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") {
                HeaderBlock(
                    state = state,
                    summary = summary,
                    exporting = state.exporting,
                    onExportClick = onOpenExport,
                    onAppQueryChange = onDashboardAppQuery,
                )
            }

            item(key = "period-toggle") {
                PeriodToggle(
                    period = state.period,
                    onPeriod = onPeriodChange,
                )
            }

            item(key = "data-source") {
                DataSourceToggle(
                    source = state.dataSource,
                    onSource = onDataSource,
                    cloudReady = state.hindsightConfigured,
                )
            }

            if (state.period == StatsPeriod.DAY) {
                item(key = "day-mode") {
                    DayModeToggle(
                        mode = state.dayViewMode,
                        onMode = onDayViewMode,
                    )
                }
            }

            if (showTimeline) {
                val appQuery = state.dashboardAppQuery.trim()
                if (appQuery.isNotEmpty()) {
                    summary?.let { data ->
                        val filteredApps = filterAppsByQuery(data.apps, appQuery)
                        item(key = "apps-search") {
                            DashboardCard(title = "搜索结果") {
                                AppRankedList(
                                    apps = filteredApps,
                                    totalMillis = data.totalMillis,
                                    iconLoader = cachedIconLoader,
                                    defaultLimit = Int.MAX_VALUE,
                                    onAppClick = onAppClick,
                                    emptyText = "未找到匹配「$appQuery」的应用",
                                )
                            }
                        }
                    }
                }
                item(key = "timeline-card-head") {
                    DashboardCard(
                        title = "时间戳视图 · 前台记录",
                        headExtras = {
                            PeriodNav(
                                label = periodPillLabel(state.period, state.offset),
                                canGoForward = state.offset < 0,
                                onPrev = onPrev,
                                onNext = onNext,
                                onOpenCalendar = { pickingPeriod = true },
                            )
                        },
                    ) {
                        TimelineGranularityToggle(
                            granularity = effectiveGranularity,
                            onGranularity = onTimelineGranularity,
                        )
                        if (drill != null) {
                            TimelineDrillBar(
                                level = drill,
                                onUp = onTimelineDrillUp,
                            )
                        }
                        Text(
                            text = when (effectiveGranularity) {
                                TimelineGranularity.HOUR ->
                                    "按小时堆叠 · 共 $timelineBucketCount 个时段 · 点击查看分钟"
                                TimelineGranularity.MINUTE ->
                                    "按分钟堆叠 · 共 $timelineBucketCount 个时段 · 点击查看秒级"
                                TimelineGranularity.SECOND ->
                                    "共 ${visibleTimeline.size} 次进入 · 每条为进入→离开"
                            },
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                if (state.timelineLoading && state.timeline.isEmpty()) {
                    item(key = "timeline-loading") {
                        TimelineListSkeleton(rows = 6)
                    }
                } else {
                    timelineItems(
                        items = visibleTimeline,
                        granularity = effectiveGranularity,
                        iconLoader = cachedIconLoader,
                        onBucketClick = onTimelineBucketClick,
                    )
                }
            } else {
                item(key = "chart") {
                    ChartCard(
                        state = state,
                        summary = summary,
                        onPrev = onPrev,
                        onNext = onNext,
                        onOpenCalendar = { pickingPeriod = true },
                        onSelectHour = onSelectHour,
                    )
                }

                if (state.loading && summary == null) {
                    item(key = "apps-skeleton") {
                        DashboardCard(title = "最常用应用") {
                            AppListSkeleton(rows = 8)
                        }
                    }
                    item(key = "cats-skeleton") {
                        DashboardCard(title = "最常用分类") {
                            CategoryListSkeleton(rows = 5)
                        }
                    }
                }

                summary?.let { data ->
                    val hour = if (state.period == StatsPeriod.DAY) state.selectedHour else null
                    val scopedApps = data.appsForHour(hour)
                    val filteredApps = filterAppsByQuery(scopedApps, state.dashboardAppQuery)
                    val scopedCategories = data.categoriesForHour(hour)
                    val scopedMillis = data.millisForHour(hour)
                    val appQuery = state.dashboardAppQuery.trim()

                    item(key = "apps") {
                        DashboardCard(
                            title = when {
                                hour != null -> "${hour}:00 最常用应用"
                                state.period == StatsPeriod.DAY -> "最常用应用"
                                state.period == StatsPeriod.WEEK -> "本周最常用应用"
                                else -> "本月最常用应用"
                            },
                            trailing = formatDuration(scopedMillis),
                        ) {
                            AppRankedList(
                                apps = filteredApps,
                                totalMillis = scopedMillis,
                                iconLoader = cachedIconLoader,
                                defaultLimit = if (appQuery.isNotEmpty()) Int.MAX_VALUE else 10,
                                onAppClick = onAppClick,
                                emptyText = if (appQuery.isNotEmpty()) {
                                    "未找到匹配「$appQuery」的应用"
                                } else {
                                    "这段时间没有应用使用记录"
                                },
                            )
                        }
                    }

                    item(key = "cats") {
                        DashboardCard(
                            title = when {
                                hour != null -> "${hour}:00 最常用分类"
                                state.period == StatsPeriod.DAY -> "最常用分类"
                                state.period == StatsPeriod.WEEK -> "本周最常用分类"
                                else -> "本月最常用分类"
                            },
                        ) {
                            CategoryRankedList(
                                categories = scopedCategories,
                                totalMillis = scopedMillis,
                                iconLoader = cachedIconLoader,
                                showTopAppIcons = false,
                                onCategoryClick = onCategoryClick,
                            )
                        }
                    }
                }
            }

            state.error?.let { err ->
                item {
                    Text(text = err, color = Danger, fontSize = 13.sp)
                }
            }
        }
        }

        if (showTimeline && state.timeline.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End,
            ) {
                AnimatedVisibility(
                    visible = canScrollUp,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    TimelineJumpFab(
                        icon = Icons.Outlined.KeyboardArrowUp,
                        label = "顶部",
                        onClick = {
                            scope.launch { listState.animateScrollToItem(0) }
                        },
                    )
                }
                AnimatedVisibility(
                    visible = canScrollDown,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    TimelineJumpFab(
                        icon = Icons.Outlined.KeyboardArrowDown,
                        label = "底部",
                        onClick = {
                            scope.launch {
                                val last = listState.layoutInfo.totalItemsCount - 1
                                if (last >= 0) listState.animateScrollToItem(last)
                            }
                        },
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun TimelineJumpFab(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .shadow(Elevation.fab, CircleShape, ambientColor = ShadowTintStrong, spotColor = ShadowTintStrong)
                .clip(CircleShape)
                .background(CardBackground)
                .border(1.dp, BorderDefault, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Accent)
        }
        Spacer(Modifier.height(2.dp))
        Text(label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DataSourceToggle(
    source: DataSource,
    onSource: (DataSource) -> Unit,
    cloudReady: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(1.dp, BorderDefault, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModeChip(
            text = "手机本机",
            selected = source == DataSource.PHONE,
            onClick = { onSource(DataSource.PHONE) },
            modifier = Modifier.weight(1f),
        )
        ModeChip(
            text = if (cloudReady) "电脑云端" else "电脑云端(未配置)",
            selected = source == DataSource.HINDSIGHT_CLOUD,
            onClick = { onSource(DataSource.HINDSIGHT_CLOUD) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PeriodToggle(
    period: StatsPeriod,
    onPeriod: (StatsPeriod) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(1.dp, BorderDefault, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModeChip(
            text = "日",
            selected = period == StatsPeriod.DAY,
            onClick = { onPeriod(StatsPeriod.DAY) },
            modifier = Modifier.weight(1f),
        )
        ModeChip(
            text = "周",
            selected = period == StatsPeriod.WEEK,
            onClick = { onPeriod(StatsPeriod.WEEK) },
            modifier = Modifier.weight(1f),
        )
        ModeChip(
            text = "月",
            selected = period == StatsPeriod.MONTH,
            onClick = { onPeriod(StatsPeriod.MONTH) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DayModeToggle(
    mode: DayViewMode,
    onMode: (DayViewMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(1.dp, BorderDefault, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModeChip(
            text = "时段分布",
            selected = mode == DayViewMode.CHART,
            onClick = { onMode(DayViewMode.CHART) },
            modifier = Modifier.weight(1f),
        )
        ModeChip(
            text = "时间戳视图",
            selected = mode == DayViewMode.TIMELINE,
            onClick = { onMode(DayViewMode.TIMELINE) },
            modifier = Modifier.weight(1f),
        )
    }
}

private val drillHourFmt = java.time.format.DateTimeFormatter.ofPattern("HH:00")
    .withZone(java.time.ZoneId.systemDefault())
private val drillMinuteFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    .withZone(java.time.ZoneId.systemDefault())

@Composable
private fun TimelineDrillBar(
    level: TimelineDrillLevel,
    onUp: () -> Unit,
) {
    val start = java.time.Instant.ofEpochMilli(level.rangeStart)
    val end = java.time.Instant.ofEpochMilli(level.rangeEnd)
    val (range, upLabel) = when (level.granularity) {
        TimelineGranularity.MINUTE ->
            "${drillHourFmt.format(start)} – ${drillHourFmt.format(end)}" to "返回按小时"
        else ->
            "${drillMinuteFmt.format(start)} – ${drillMinuteFmt.format(end)}" to "返回按分钟"
    }
    Row(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AccentSoft)
            .clickable(onClick = onUp)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.ChevronLeft,
            contentDescription = upLabel,
            tint = Accent,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = upLabel,
            color = Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = range,
            color = TextStrong,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun TimelineGranularityToggle(
    granularity: TimelineGranularity,
    onGranularity: (TimelineGranularity) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(PageBackground)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ModeChip(
            text = "时",
            selected = granularity == TimelineGranularity.HOUR,
            onClick = { onGranularity(TimelineGranularity.HOUR) },
            modifier = Modifier.weight(1f),
        )
        ModeChip(
            text = "分",
            selected = granularity == TimelineGranularity.MINUTE,
            onClick = { onGranularity(TimelineGranularity.MINUTE) },
            modifier = Modifier.weight(1f),
        )
        ModeChip(
            text = "秒",
            selected = granularity == TimelineGranularity.SECOND,
            onClick = { onGranularity(TimelineGranularity.SECOND) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AccentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Accent else TextMuted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun HeaderBlock(
    state: UsageUiState,
    summary: PeriodSummary?,
    exporting: Boolean,
    onExportClick: () -> Unit,
    onAppQueryChange: (String) -> Unit,
) {
    val title = when (state.period) {
        StatsPeriod.DAY -> "日统计"
        StatsPeriod.WEEK -> "周统计"
        StatsPeriod.MONTH -> "月统计"
    }
    val sourceTag = when (state.dataSource) {
        DataSource.PHONE -> "手机"
        DataSource.HINDSIGHT_CLOUD -> "电脑·只读"
    }
    val matchedSummary = summary?.takeIf { it.period == state.period }
    val subtitle = when {
        matchedSummary == null -> ""
        state.period == StatsPeriod.DAY -> "${formatFullDate(matchedSummary.rangeStart)} · $sourceTag"
        else -> {
            val avg = formatDuration(matchedSummary.averageDailyMillis)
            "${formatMonthDay(matchedSummary.rangeStart)} – ${formatMonthDay(matchedSummary.rangeEnd)} · 日均 $avg · $sourceTag"
        }
    }
    var searchExpanded by remember {
        mutableStateOf(state.dashboardAppQuery.isNotBlank())
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "留迹",
                color = Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(8.dp))
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Success),
            )
            Spacer(Modifier.size(4.dp))
            Text("记录中", color = TextMuted, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            if (exporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Accent,
                )
                Spacer(Modifier.size(8.dp))
            }
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
            IconButton(
                onClick = onExportClick,
                enabled = !exporting && matchedSummary != null,
            ) {
                Icon(
                    imageVector = Icons.Outlined.FileUpload,
                    contentDescription = "导出数据",
                    tint = Accent,
                )
            }
        }
        if (searchExpanded) {
            Spacer(Modifier.height(Space.xs))
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
        Spacer(Modifier.height(Space.xs))
        Text(
            text = title,
            color = TextStrong,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        when {
            subtitle.isNotBlank() -> {
                Spacer(Modifier.height(Space.xxs))
                Text(text = subtitle, color = TextMuted, fontSize = 13.sp)
            }
            state.loading && matchedSummary == null -> {
                Spacer(Modifier.height(Space.xxs))
                SkeletonBar(width = 180.dp, height = 12.dp)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = when (state.dataSource) {
                DataSource.PHONE -> "点上方日/周/月，或左右滑切换 · 下拉刷新"
                DataSource.HINDSIGHT_CLOUD -> "点上方日/周/月，或左右滑切换 · 下拉同步云盘"
            },
            color = TextMuted,
            fontSize = 11.sp,
        )
    }
}

internal fun filterAppsByQuery(apps: List<AppUsageItem>, query: String): List<AppUsageItem> {
    val q = query.trim()
    if (q.isEmpty()) return apps
    return apps.filter {
        it.label.contains(q, ignoreCase = true) ||
            it.packageName.contains(q, ignoreCase = true)
    }
}

@Composable
private fun ChartCard(
    state: UsageUiState,
    summary: PeriodSummary?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenCalendar: () -> Unit,
    onSelectHour: (Int?) -> Unit,
) {
    val matched = summary?.takeIf { it.period == state.period }
    DashboardCard(
        title = when (state.period) {
            StatsPeriod.DAY -> "24 小时活动分布"
            StatsPeriod.WEEK -> "每日活动分布"
            StatsPeriod.MONTH -> "每日活动分布"
        },
        headExtras = {
            PeriodNav(
                label = periodPillLabel(state.period, state.offset),
                canGoForward = state.offset < 0,
                onPrev = onPrev,
                onNext = onNext,
                onOpenCalendar = onOpenCalendar,
            )
        },
    ) {
        when {
            matched == null && state.loading -> {
                ChartSkeleton(period = state.period)
            }

            matched == null -> {
                Text("暂无数据", color = TextMuted, fontSize = 13.sp)
            }

            state.period == StatsPeriod.DAY -> {
                HourlyChart(
                    hours = matched.hours,
                    selectedHour = state.selectedHour,
                    onHourClick = { onSelectHour(it) },
                )
                Spacer(Modifier.height(12.dp))
                CategoryLegend(categoryIds = matched.categories.map { it.categoryId })
            }

            else -> {
                PeriodBarChart(
                    days = matched.dayBuckets,
                    labelFor = {
                        if (state.period == StatsPeriod.MONTH) {
                            formatMonthDay(it)
                        } else {
                            com.liuji.xiaoqi.ui.util.formatWeekday(it)
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                CategoryLegend(categoryIds = matched.categories.map { it.categoryId })
            }
        }
    }
}

@Composable
private fun PeriodNav(
    label: String,
    canGoForward: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "上一期", tint = TextMuted)
        }
        TextButton(onClick = onOpenCalendar) {
            Text(label, color = TextStrong, fontSize = 12.sp)
        }
        IconButton(
            onClick = onNext,
            enabled = canGoForward,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "下一期", tint = TextMuted)
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    trailing: String? = null,
    headExtras: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                Elevation.card,
                ShapeCard,
                ambientColor = ShadowTint,
                spotColor = ShadowTint,
            )
            .clip(ShapeCard)
            .background(CardBackground)
            .border(Stroke.hairline, BorderDefault, ShapeCard)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = TextStrong,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (trailing != null) {
                Text(trailing, color = TextMuted, fontSize = 12.sp)
            }
        }
        if (headExtras != null) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                headExtras()
            }
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}
