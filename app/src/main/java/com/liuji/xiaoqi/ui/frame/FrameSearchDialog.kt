package com.liuji.xiaoqi.ui.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuji.xiaoqi.data.frame.FrameSearchHit
import com.liuji.xiaoqi.ui.FrameSearchDateMode
import com.liuji.xiaoqi.ui.FrameSearchScope
import com.liuji.xiaoqi.ui.theme.Accent
import com.liuji.xiaoqi.ui.theme.AccentSoft
import com.liuji.xiaoqi.ui.theme.CardBackground
import com.liuji.xiaoqi.ui.theme.Radius
import com.liuji.xiaoqi.ui.theme.Space
import com.liuji.xiaoqi.ui.theme.SurfaceMuted
import com.liuji.xiaoqi.ui.theme.TextMuted
import com.liuji.xiaoqi.ui.theme.TextStrong
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private val hitTimeFmt = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())

private enum class DatePickTarget {
    SINGLE,
    RANGE_START,
    RANGE_END,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FrameSearchDialog(
    query: String,
    multi: Boolean,
    keywords: List<String>,
    matchAll: Boolean,
    searchScope: FrameSearchScope,
    singlePackageLabel: String?,
    pickedCount: Int,
    strict: Boolean,
    hits: List<FrameSearchHit>,
    hitIndex: Int,
    loading: Boolean,
    loadingMore: Boolean,
    hasMore: Boolean,
    total: Int?,
    message: String?,
    indexProgress: String?,
    sortDescending: Boolean,
    dateMode: FrameSearchDateMode,
    singleEpochDay: Long,
    rangeStartEpochDay: Long,
    rangeEndEpochDay: Long,
    onQueryChange: (String) -> Unit,
    onMultiChange: (Boolean) -> Unit,
    onKeywordChange: (Int, String) -> Unit,
    onAddKeyword: () -> Unit,
    onRemoveKeyword: (Int) -> Unit,
    onMatchAllChange: (Boolean) -> Unit,
    onOpenSinglePicker: () -> Unit,
    onScopeAll: () -> Unit,
    onOpenMultiPicker: () -> Unit,
    onStrictChange: (Boolean) -> Unit,
    onDateModeChange: (FrameSearchDateMode) -> Unit,
    onSingleDayChange: (Long) -> Unit,
    onDateRangeChange: (Long, Long) -> Unit,
    onToggleSort: () -> Unit,
    onSearch: () -> Unit,
    onJump: (Int) -> Unit,
    onLoadMoreNearEnd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dateFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val today = remember { LocalDate.now() }
    val singleDay = remember(singleEpochDay) {
        if (singleEpochDay > 0L) LocalDate.ofEpochDay(singleEpochDay) else today
    }
    val rangeStart = remember(rangeStartEpochDay) {
        if (rangeStartEpochDay > 0L) LocalDate.ofEpochDay(rangeStartEpochDay) else today
    }
    val rangeEnd = remember(rangeEndEpochDay) {
        if (rangeEndEpochDay > 0L) LocalDate.ofEpochDay(rangeEndEpochDay) else today
    }
    var pickTarget by remember { mutableStateOf<DatePickTarget?>(null) }
    var pendingRangeStartDay by remember { mutableStateOf<Long?>(null) }
    var expandedSnippets by remember { mutableStateOf(setOf<String>()) }
    val hitGroups = remember(hits) { groupHitsBySnippet(hits) }
    val hitListState = rememberLazyListState()
    LaunchedEffect(hitListState, hits.size, hasMore, loadingMore) {
        snapshotFlow {
            val info = hitListState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItems = info.totalItemsCount
            last to totalItems
        }
            .distinctUntilChanged()
            .filter { (last, totalItems) ->
                totalItems > 0 && last >= totalItems - 5 && hasMore && !loadingMore
            }
            .collect { onLoadMoreNearEnd() }
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextStrong,
        unfocusedTextColor = TextStrong,
        focusedContainerColor = SurfaceMuted,
        unfocusedContainerColor = SurfaceMuted,
        cursorColor = Accent,
        focusedBorderColor = Accent,
        unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
        focusedLabelColor = TextMuted,
        unfocusedLabelColor = TextMuted,
        focusedPlaceholderColor = TextMuted,
        unfocusedPlaceholderColor = TextMuted,
    )
    val keywordRows = if (keywords.isEmpty()) listOf("") else keywords
    val singleChipLabel = when {
        !singlePackageLabel.isNullOrBlank() -> "单应用 · $singlePackageLabel"
        else -> "单应用"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        titleContentColor = TextStrong,
        textContentColor = TextStrong,
        title = { Text("搜索画面", color = TextStrong) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (hits.isEmpty()) 480.dp else 220.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Text("关键词", color = TextMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    FilterChip(
                        selected = !multi,
                        onClick = { onMultiChange(false) },
                        label = { Text("单个") },
                    )
                    FilterChip(
                        selected = multi,
                        onClick = { onMultiChange(true) },
                        label = { Text("多个") },
                    )
                }
                if (!multi) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("关键词或记录编号") },
                        placeholder = { Text("例如：微信 / 123") },
                        colors = fieldColors,
                    )
                } else {
                    keywordRows.forEachIndexed { index, value ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { onKeywordChange(index, it) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("关键词 ${index + 1}") },
                                placeholder = { Text("输入关键词") },
                                colors = fieldColors,
                            )
                            if (keywordRows.size > 1) {
                                IconButton(onClick = { onRemoveKeyword(index) }) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "删除关键词",
                                        tint = TextMuted,
                                    )
                                }
                            }
                        }
                    }
                    TextButton(onClick = onAddKeyword) {
                        Text("添加关键词", color = Accent)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        FilterChip(
                            selected = !matchAll,
                            onClick = { onMatchAllChange(false) },
                            label = { Text("或") },
                        )
                        FilterChip(
                            selected = matchAll,
                            onClick = { onMatchAllChange(true) },
                            label = { Text("且") },
                        )
                    }
                }
                Text("范围", color = TextMuted, fontSize = 12.sp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    FilterChip(
                        selected = searchScope == FrameSearchScope.CURRENT,
                        onClick = onOpenSinglePicker,
                        label = { Text(singleChipLabel) },
                    )
                    FilterChip(
                        selected = searchScope == FrameSearchScope.PICKED,
                        onClick = onOpenMultiPicker,
                        label = {
                            Text(if (pickedCount > 0) "多个应用 · $pickedCount" else "多个应用")
                        },
                    )
                    FilterChip(
                        selected = searchScope == FrameSearchScope.ALL,
                        onClick = onScopeAll,
                        label = { Text("全部应用") },
                    )
                }
                Text("匹配方式", color = TextMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    FilterChip(
                        selected = !strict,
                        onClick = { onStrictChange(false) },
                        label = { Text("模糊") },
                    )
                    FilterChip(
                        selected = strict,
                        onClick = { onStrictChange(true) },
                        label = { Text("精确") },
                    )
                }
                Text("日期限制", color = TextMuted, fontSize = 12.sp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    FilterChip(
                        selected = dateMode == FrameSearchDateMode.ALL,
                        onClick = {
                            pendingRangeStartDay = null
                            pickTarget = null
                            onDateModeChange(FrameSearchDateMode.ALL)
                        },
                        label = { Text("全部") },
                    )
                    FilterChip(
                        selected = dateMode == FrameSearchDateMode.SINGLE,
                        onClick = {
                            pendingRangeStartDay = null
                            onDateModeChange(FrameSearchDateMode.SINGLE)
                            pickTarget = DatePickTarget.SINGLE
                        },
                        label = { Text("单日") },
                    )
                    FilterChip(
                        selected = dateMode == FrameSearchDateMode.RANGE,
                        onClick = {
                            pendingRangeStartDay = null
                            onDateModeChange(FrameSearchDateMode.RANGE)
                            pickTarget = DatePickTarget.RANGE_START
                        },
                        label = { Text("多日") },
                    )
                }
                when (dateMode) {
                    FrameSearchDateMode.ALL -> Unit
                    FrameSearchDateMode.SINGLE -> {
                        Text(
                            "日期 ${singleDay.format(dateFmt)}",
                            color = TextMuted,
                            fontSize = 13.sp,
                        )
                    }
                    FrameSearchDateMode.RANGE -> {
                        Text(
                            "开始 ${rangeStart.format(dateFmt)} · 结束 ${rangeEnd.format(dateFmt)}",
                            color = TextMuted,
                            fontSize = 13.sp,
                        )
                    }
                }
                if (indexProgress != null) {
                    Text(indexProgress, color = TextMuted, fontSize = 13.sp)
                }
                if (loading) {
                    Text("搜索中…", color = TextMuted, fontSize = 13.sp)
                }
                message?.let {
                    Text(it, color = TextMuted, fontSize = 13.sp)
                }
            }
            if (hits.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = buildString {
                            append("已加载 ${hits.size}")
                            append(" / 共 ")
                            append(total?.toString() ?: "…")
                            append(" · ${hitGroups.size} 组")
                            if (loadingMore) append(" · 加载中")
                        },
                        color = TextStrong,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = true,
                        onClick = onToggleSort,
                        label = {
                            Text(if (sortDescending) "时间↓" else "时间↑")
                        },
                    )
                }
                LazyColumn(
                    state = hitListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(hitGroups, key = { it.snippet.hashCode().toString() + it.items.first().index }) { group ->
                        val expanded = group.snippet in expandedSnippets
                        val head = group.items.first()
                        val selectedInGroup = group.items.any { it.index == hitIndex }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(
                                    if (selectedInGroup) AccentSoft else Color.Transparent,
                                )
                                .clickable {
                                    if (group.items.size == 1) {
                                        onJump(head.index)
                                    } else {
                                        expandedSnippets = if (expanded) {
                                            expandedSnippets - group.snippet
                                        } else {
                                            expandedSnippets + group.snippet
                                        }
                                    }
                                }
                                .padding(horizontal = Space.sm, vertical = Space.sm),
                        ) {
                            Text(
                                text = buildString {
                                    append(
                                        hitTimeFmt.format(
                                            Instant.ofEpochMilli(head.hit.capturedAt),
                                        ),
                                    )
                                    if (group.items.size > 1) {
                                        append(" · 相同 ×${group.items.size}")
                                    } else if (searchScope != FrameSearchScope.CURRENT) {
                                        append(" · ")
                                        append(head.hit.appLabel.ifBlank { head.hit.packageName })
                                    }
                                },
                                color = Accent,
                                fontSize = 12.sp,
                            )
                            Text(
                                text = group.snippet,
                                color = TextStrong,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (expanded && group.items.size > 1) {
                            group.items.forEach { item ->
                                val selected = item.index == hitIndex
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = Space.md)
                                        .clip(RoundedCornerShape(Radius.sm))
                                        .background(
                                            if (selected) AccentSoft else Color.Transparent,
                                        )
                                        .clickable { onJump(item.index) }
                                        .padding(
                                            horizontal = Space.sm,
                                            vertical = Space.xs,
                                        ),
                                ) {
                                    Text(
                                        text = buildString {
                                            append(
                                                hitTimeFmt.format(
                                                    Instant.ofEpochMilli(item.hit.capturedAt),
                                                ),
                                            )
                                            append(" · ")
                                            append(
                                                item.hit.appLabel.ifBlank { item.hit.packageName },
                                            )
                                        },
                                        color = Accent,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                    if (loadingMore) {
                        item(key = "loading-more") {
                            Text(
                                "加载更多…",
                                color = TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(Space.sm),
                            )
                        }
                    }
                }
            }
            }
        },
        confirmButton = {
            TextButton(onClick = onSearch, enabled = !loading) {
                Text(if (loading) "搜索中" else "搜索")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
    pickTarget?.let { target ->
        val initial = when (target) {
            DatePickTarget.SINGLE -> singleDay
            DatePickTarget.RANGE_START -> rangeStart
            DatePickTarget.RANGE_END -> {
                val startDay = pendingRangeStartDay ?: rangeStart.toEpochDay()
                val end = rangeEnd.toEpochDay().coerceAtLeast(startDay)
                LocalDate.ofEpochDay(end)
            }
        }
        key(target) {
            FrameSearchDatePickerDialog(
                initial = initial,
                onDismiss = {
                    pendingRangeStartDay = null
                    pickTarget = null
                },
                onConfirm = { picked ->
                    val day = picked.toEpochDay()
                    when (target) {
                        DatePickTarget.SINGLE -> {
                            onSingleDayChange(day)
                            pickTarget = null
                        }
                        DatePickTarget.RANGE_START -> {
                            pendingRangeStartDay = day
                            onDateRangeChange(day, rangeEnd.toEpochDay().coerceAtLeast(day))
                            pickTarget = DatePickTarget.RANGE_END
                        }
                        DatePickTarget.RANGE_END -> {
                            val start = pendingRangeStartDay ?: rangeStart.toEpochDay()
                            pendingRangeStartDay = null
                            onDateRangeChange(start, day)
                            pickTarget = null
                        }
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrameSearchDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val initialMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val maxMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= maxMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis ?: return@TextButton
                onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(state = state)
    }
}

private data class IndexedSearchHit(
    val index: Int,
    val hit: FrameSearchHit,
)

private data class SnippetHitGroup(
    val snippet: String,
    val items: List<IndexedSearchHit>,
)

/** 按 snippet 完全一致分组，保留首次出现顺序（与当前时间排序一致）。 */
private fun groupHitsBySnippet(hits: List<FrameSearchHit>): List<SnippetHitGroup> {
    val map = LinkedHashMap<String, MutableList<IndexedSearchHit>>()
    hits.forEachIndexed { index, hit ->
        map.getOrPut(hit.snippet) { mutableListOf() }
            .add(IndexedSearchHit(index, hit))
    }
    return map.map { (snippet, items) -> SnippetHitGroup(snippet, items) }
}
