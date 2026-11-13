package com.liuji.xiaoqi.ui.frame

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuji.xiaoqi.data.CategoryWithApps
import com.liuji.xiaoqi.data.frame.FrameJson
import com.liuji.xiaoqi.data.frame.FrameSearchHit
import com.liuji.xiaoqi.data.frame.FrameTextNode
import com.liuji.xiaoqi.data.frame.UiFrameSnapshot
import com.liuji.xiaoqi.ui.FrameSearchDateMode
import com.liuji.xiaoqi.ui.FrameSearchScope
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.liuji.xiaoqi.ui.theme.Accent
import com.liuji.xiaoqi.ui.theme.AccentSoft
import com.liuji.xiaoqi.ui.theme.AccentStroke
import com.liuji.xiaoqi.ui.theme.CardBackground
import com.liuji.xiaoqi.ui.theme.HighlightFill
import com.liuji.xiaoqi.ui.theme.HighlightStroke
import com.liuji.xiaoqi.ui.theme.PageBackground
import com.liuji.xiaoqi.ui.theme.Radius
import com.liuji.xiaoqi.ui.theme.Scrim
import com.liuji.xiaoqi.ui.theme.ShapeCanvas
import com.liuji.xiaoqi.ui.theme.Success
import com.liuji.xiaoqi.ui.theme.Space
import com.liuji.xiaoqi.ui.theme.SurfaceMuted
import com.liuji.xiaoqi.ui.theme.TextMuted
import com.liuji.xiaoqi.ui.theme.TextStrong
import android.widget.Toast
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val timeFmt = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())

private enum class FrameViewMode {
    CANVAS,
    DOCUMENT,
    CHAT,
}

private const val FULL_DAY_END_MINUTE = 23 * 60 + 59

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameViewerScreen(
    appLabel: String,
    packageName: String,
    frames: List<UiFrameSnapshot>,
    index: Int,
    loading: Boolean,
    searchQuery: String,
    searchMulti: Boolean,
    searchKeywords: List<String>,
    searchMatchAll: Boolean,
    searchScope: FrameSearchScope,
    searchPackages: Set<String>,
    searchCategories: List<CategoryWithApps>,
    searchCategoriesLoading: Boolean,
    searchStrict: Boolean,
    searchHits: List<FrameSearchHit>,
    searchHitIndex: Int,
    searchLoading: Boolean,
    searchLoadingMore: Boolean = false,
    searchHasMore: Boolean = false,
    searchTotal: Int? = null,
    searchMessage: String?,
    searchIndexProgress: String? = null,
    searchSortDescending: Boolean = true,
    searchDateMode: FrameSearchDateMode = FrameSearchDateMode.ALL,
    searchSingleEpochDay: Long = 0L,
    searchRangeStartEpochDay: Long = 0L,
    searchRangeEndEpochDay: Long = 0L,
    onBack: () -> Unit,
    onIndexChange: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchMultiChange: (Boolean) -> Unit,
    onSearchKeywordChange: (Int, String) -> Unit,
    onAddSearchKeyword: () -> Unit,
    onRemoveSearchKeyword: (Int) -> Unit,
    onSearchMatchAllChange: (Boolean) -> Unit,
    onSearchScopeChange: (FrameSearchScope) -> Unit,
    onPickSearchSinglePackage: (String) -> Unit,
    onPickSearchPackages: (Set<String>) -> Unit,
    onLoadSearchCategories: () -> Unit,
    onPrepareSearch: () -> Unit,
    onSearchStrictChange: (Boolean) -> Unit,
    onRunSearch: () -> Unit,
    onJumpToSearchHit: (Int) -> Unit,
    onSearchPrev: () -> Unit,
    onSearchNext: () -> Unit,
    onRestoreSearchOrigin: () -> Boolean,
    onToggleSearchSort: () -> Unit,
    onLoadMoreSearchPreview: () -> Unit,
    onSearchDateModeChange: (FrameSearchDateMode) -> Unit,
    onSearchSingleDayChange: (Long) -> Unit,
    onSearchDateRangeChange: (Long, Long) -> Unit,
    onLoadDateFrames: (Long, Int, Int) -> Unit = { _, _, _ -> },
    onReloadPeriodFrames: () -> Unit = {},
    iconLoader: (String) -> android.graphics.drawable.Drawable?,
) {
    val copyText = rememberCopyAction()
    val zone = ZoneId.systemDefault()
    val isChatGpt = packageName == FrameJson.CHATGPT_PACKAGE
    var filterEpochDay by rememberSaveable { mutableStateOf(-1L) }
    var filterStartMinute by rememberSaveable { mutableStateOf(0) }
    var filterEndMinute by rememberSaveable { mutableStateOf(FULL_DAY_END_MINUTE) }
    val filtering = filterEpochDay >= 0L
    val visibleIndices = remember(frames, filtering, filterEpochDay, filterStartMinute, filterEndMinute) {
        if (!filtering) {
            frames.indices.toList()
        } else {
            val startMinute = filterStartMinute.coerceIn(0, FULL_DAY_END_MINUTE)
            val endMinute = filterEndMinute.coerceIn(startMinute, FULL_DAY_END_MINUTE)
            frames.indices.filter { index ->
                val local = Instant.ofEpochMilli(frames[index].capturedAt).atZone(zone).toLocalDateTime()
                local.toLocalDate().toEpochDay() == filterEpochDay &&
                    local.hour * 60 + local.minute in startMinute..endMinute
            }
        }
    }
    val safeIndex = index.coerceIn(0, (frames.size - 1).coerceAtLeast(0))
    val frame = frames.getOrNull(safeIndex)
    val visiblePos = visibleIndices.indexOf(safeIndex)
    LaunchedEffect(visibleIndices, index) {
        if (visibleIndices.isNotEmpty() && index !in visibleIndices) {
            onIndexChange(visibleIndices.first())
        }
    }
    fun stepVisible(delta: Int) {
        val pos = visibleIndices.indexOf(safeIndex)
        if (pos < 0) return
        val next = pos + delta
        if (next !in visibleIndices.indices) return
        onIndexChange(visibleIndices[next])
    }
    var dragAcc by remember { mutableFloatStateOf(0f) }
    var showDesc by rememberSaveable { mutableStateOf(false) }
    var fullTextNode by remember { mutableStateOf<FrameTextNode?>(null) }
    var docSelecting by remember { mutableStateOf(false) }
    var docSelected by remember { mutableStateOf(setOf<Int>()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var appPickerSingle by remember { mutableStateOf(false) }
    var viewMode by rememberSaveable { mutableStateOf(FrameViewMode.CANVAS) }
    val singleSearchLabel = remember(
        searchScope,
        searchPackages,
        appLabel,
        packageName,
        searchCategories,
    ) {
        if (searchScope != FrameSearchScope.CURRENT) {
            null
        } else {
            val pkg = searchPackages.firstOrNull() ?: packageName.takeIf { it.isNotBlank() }
            when {
                pkg == null -> null
                pkg == packageName && appLabel.isNotBlank() -> appLabel
                else -> searchCategories.asSequence()
                    .flatMap { it.apps.asSequence() }
                    .firstOrNull { it.packageName == pkg }
                    ?.label
                    ?: pkg
            }
        }
    }
    var nonChatMode by rememberSaveable { mutableStateOf(FrameViewMode.CANVAS) }
    LaunchedEffect(isChatGpt) {
        if (!isChatGpt && viewMode == FrameViewMode.CHAT) {
            viewMode = FrameViewMode.CANVAS
        }
    }
    LaunchedEffect(frame?.id, viewMode) {
        docSelecting = false
        docSelected = emptySet()
    }
    BackHandler(enabled = !showSearchDialog && !showAppPicker && fullTextNode == null) {
        if (docSelecting) {
            docSelecting = false
            docSelected = emptySet()
        } else if (onRestoreSearchOrigin()) {
            showSearchDialog = true
        } else {
            onBack()
        }
    }

    if (showSearchDialog) {
        FrameSearchDialog(
            query = searchQuery,
            multi = searchMulti,
            keywords = searchKeywords,
            matchAll = searchMatchAll,
            searchScope = searchScope,
            singlePackageLabel = singleSearchLabel,
            pickedCount = if (searchScope == FrameSearchScope.PICKED) searchPackages.size else 0,
            strict = searchStrict,
            hits = searchHits,
            hitIndex = searchHitIndex,
            loading = searchLoading,
            loadingMore = searchLoadingMore,
            hasMore = searchHasMore,
            total = searchTotal,
            message = searchMessage,
            indexProgress = searchIndexProgress,
            sortDescending = searchSortDescending,
            dateMode = searchDateMode,
            singleEpochDay = searchSingleEpochDay,
            rangeStartEpochDay = searchRangeStartEpochDay,
            rangeEndEpochDay = searchRangeEndEpochDay,
            onQueryChange = onSearchQueryChange,
            onMultiChange = onSearchMultiChange,
            onKeywordChange = onSearchKeywordChange,
            onAddKeyword = onAddSearchKeyword,
            onRemoveKeyword = onRemoveSearchKeyword,
            onMatchAllChange = onSearchMatchAllChange,
            onOpenSinglePicker = {
                onLoadSearchCategories()
                appPickerSingle = true
                showAppPicker = true
            },
            onScopeAll = { onSearchScopeChange(FrameSearchScope.ALL) },
            onOpenMultiPicker = {
                onLoadSearchCategories()
                appPickerSingle = false
                showAppPicker = true
            },
            onStrictChange = onSearchStrictChange,
            onDateModeChange = onSearchDateModeChange,
            onSingleDayChange = onSearchSingleDayChange,
            onDateRangeChange = onSearchDateRangeChange,
            onToggleSort = onToggleSearchSort,
            onSearch = onRunSearch,
            onJump = { i ->
                onJumpToSearchHit(i)
                showSearchDialog = false
            },
            onLoadMoreNearEnd = onLoadMoreSearchPreview,
            onDismiss = { showSearchDialog = false },
        )
    }

    if (showAppPicker) {
        FrameAppScopePicker(
            categories = searchCategories,
            loading = searchCategoriesLoading,
            initialSelected = if (appPickerSingle) {
                searchPackages.take(1).toSet()
            } else {
                searchPackages
            },
            iconLoader = iconLoader,
            singleSelect = appPickerSingle,
            onDismiss = { showAppPicker = false },
            onConfirm = { packages ->
                if (appPickerSingle) {
                    packages.firstOrNull()?.let(onPickSearchSinglePackage)
                } else if (packages.isNotEmpty()) {
                    onPickSearchPackages(packages)
                }
                showAppPicker = false
            },
        )
    }

    fullTextNode?.let { node ->
        AlertDialog(
            onDismissRequest = { fullTextNode = null },
            containerColor = CardBackground,
            titleContentColor = TextStrong,
            textContentColor = TextStrong,
            title = { Text("完整文字", color = TextStrong) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (node.hasText()) {
                        Text("界面文字", color = TextMuted, fontSize = 11.sp)
                        Text(node.text, color = TextStrong, fontSize = 15.sp)
                    }
                    if (node.hasDesc()) {
                        if (node.hasText()) {
                            Text("", modifier = Modifier.padding(top = Space.sm))
                        }
                        Text("说明文字", color = TextMuted, fontSize = 11.sp)
                        Text(node.desc, color = TextStrong, fontSize = 15.sp)
                    }
                    if (!node.hasText() && !node.hasDesc()) {
                        Text("（无文字）", color = TextMuted)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        copyText(fullTextCopy(node))
                        fullTextNode = null
                    },
                    enabled = node.hasText() || node.hasDesc(),
                ) {
                    Text("复制", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { fullTextNode = null }) {
                    Text("关闭", color = TextMuted)
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .statusBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.sm),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = TextStrong,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(appLabel, color = TextStrong, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(packageName, color = TextMuted, fontSize = 11.sp, maxLines = 1)
            }
            IconButton(
                onClick = {
                    val next = if (nonChatMode == FrameViewMode.CANVAS) {
                        FrameViewMode.DOCUMENT
                    } else {
                        FrameViewMode.CANVAS
                    }
                    nonChatMode = next
                    viewMode = next
                },
            ) {
                val showingDocument = (if (viewMode == FrameViewMode.CHAT) nonChatMode else viewMode) ==
                    FrameViewMode.DOCUMENT
                Icon(
                    imageVector = if (showingDocument) {
                        Icons.Outlined.GridView
                    } else {
                        Icons.AutoMirrored.Outlined.Article
                    },
                    contentDescription = if (showingDocument) {
                        "切换画面视图"
                    } else {
                        "切换文档视图"
                    },
                    tint = Accent,
                )
            }
            if (isChatGpt) {
                val inChat = viewMode == FrameViewMode.CHAT
                IconButton(onClick = {
                    viewMode = if (inChat) nonChatMode else FrameViewMode.CHAT
                }) {
                    Icon(
                        Icons.Outlined.Forum,
                        contentDescription = if (inChat) "退出对话视图" else "对话视图",
                        tint = if (inChat) Accent else TextStrong,
                    )
                }
            }
            IconButton(onClick = {
                onPrepareSearch()
                showSearchDialog = true
            }) {
                Icon(Icons.Outlined.Search, contentDescription = "搜索画面", tint = Accent)
            }
        }

        if (searchHits.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.sm),
            ) {
                IconButton(
                    onClick = onSearchPrev,
                    enabled = searchHitIndex > 0,
                ) {
                    Icon(
                        Icons.Outlined.ChevronLeft,
                        contentDescription = "上一条结果",
                        tint = TextStrong,
                    )
                }
                Text(
                    text = buildString {
                        append("结果 ${searchHitIndex + 1} / 共 ")
                        append(searchTotal?.toString() ?: "计算中")
                        if (searchLoadingMore) append(" …")
                    },
                    color = Accent,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onSearchNext,
                    enabled = searchHitIndex < searchHits.lastIndex || searchTotal?.let { it > searchHits.size } == true,
                ) {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = "下一条结果",
                        tint = TextStrong,
                    )
                }
            }
        }

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载画面记录…", color = TextMuted)
                }
            }

            frames.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("该应用暂无画面记录\n请先在设置中开启「画面记录」", color = TextMuted)
                }
            }

            visibleIndices.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("该时间范围内没有画面", color = TextMuted)
                        TextButton(onClick = {
                            filterEpochDay = -1L
                            filterStartMinute = 0
                            filterEndMinute = FULL_DAY_END_MINUTE
                            onReloadPeriodFrames()
                        }) {
                            Text("清除筛选", color = Accent)
                        }
                    }
                }
            }

            frame != null -> {
                val frameHasAnyText = frame.hasAnyText
                val searchTerms = if (searchMulti) {
                    searchKeywords.map { it.trim() }.filter { it.isNotEmpty() }
                } else {
                    searchQuery.trim().takeIf { it.isNotEmpty() }?.let { listOf(it) }.orEmpty()
                }
                val highlightActive = searchHits.isNotEmpty() && searchTerms.isNotEmpty()
                // 命中仅在 desc 上时自动展开 desc，便于看到高亮对应文案
                val forceDescForSearch = highlightActive && frame.texts.any { node ->
                    !fieldMatches(node.text, searchTerms, searchStrict) &&
                        fieldMatches(node.desc, searchTerms, searchStrict)
                }
                // 整帧没有 text 时直接展示 desc，无需点「更多」
                val effectiveShowDesc = showDesc || !frameHasAnyText || forceDescForSearch
                val hasAnyDesc = frame.texts.any { it.hasDesc() }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.md),
                ) {
                    IconButton(
                        onClick = { stepVisible(-1) },
                        enabled = visiblePos > 0,
                    ) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "上一张")
                    }
                    Text(
                        text = buildString {
                            val pos = if (visiblePos >= 0) visiblePos + 1 else 1
                            val total = visibleIndices.size
                            append("$pos / $total")
                            if (filtering) append("（已筛选）")
                            append("  ·  ")
                            append(timeFmt.format(Instant.ofEpochMilli(frame.capturedAt)))
                        },
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { stepVisible(1) },
                        enabled = visiblePos in 0 until visibleIndices.lastIndex,
                    ) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "下一张")
                    }
                }
                if (visibleIndices.size > 1 && visiblePos >= 0) {
                    var sliderValue by remember(visiblePos) { mutableFloatStateOf(visiblePos.toFloat()) }
                    LaunchedEffect(visiblePos) {
                        sliderValue = visiblePos.toFloat()
                    }
                    Slider(
                        value = sliderValue,
                        onValueChange = { v ->
                            sliderValue = v
                            val next = v.roundToInt().coerceIn(0, visibleIndices.lastIndex)
                            val target = visibleIndices[next]
                            if (target != safeIndex) onIndexChange(target)
                        },
                        valueRange = 0f..visibleIndices.lastIndex.toFloat(),
                        steps = (visibleIndices.size - 2).coerceAtLeast(0),
                        colors = SliderDefaults.colors(
                            thumbColor = Accent,
                            activeTrackColor = Accent,
                            inactiveTrackColor = AccentSoft,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.md),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.md),
                ) {
                    Text(
                        text = buildString {
                            when (viewMode) {
                                FrameViewMode.CHAT -> append("对话视图 · 蓝为用户 · 绿为 AI · 灰为未分类")
                                FrameViewMode.DOCUMENT -> {
                                    append("文档视图 · 按位置从上到下、从左到右")
                                    if (!frameHasAnyText) append(" · 无界面文字，已显示说明")
                                    else if (showDesc) append(" · 已显示说明文字")
                                }
                                FrameViewMode.CANVAS -> {
                                    append("文字块 ${frame.texts.size}")
                                    if (!frameHasAnyText) append(" · 无界面文字，已显示说明")
                                    else if (showDesc) append(" · 已显示说明文字")
                                    append(" · 点击看全文 · 左右滑动切换")
                                }
                            }
                        },
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = Space.sm),
                    )
                    if (viewMode != FrameViewMode.CHAT && frameHasAnyText && hasAnyDesc) {
                        TextButton(onClick = { showDesc = !showDesc }) {
                            Text(
                                if (showDesc) "收起" else "更多",
                                color = Accent,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }

                if (viewMode == FrameViewMode.CHAT) {
                    val chatFrames = remember(frames, visibleIndices) {
                        visibleIndices.map { frames[it] }
                    }
                    ChatTranscriptView(
                        frames = chatFrames,
                        anchorFrameId = frame.id,
                        anchorCapturedAt = frame.capturedAt,
                        onLineClick = { fullTextNode = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Space.md),
                    )
                } else if (viewMode == FrameViewMode.DOCUMENT) {
                    FrameDocumentView(
                        frame = frame,
                        highlightActive = highlightActive,
                        terms = searchTerms,
                        searchStrict = searchStrict,
                        showDesc = effectiveShowDesc,
                        frameHasAnyText = frameHasAnyText,
                        selecting = docSelecting,
                        selected = docSelected,
                        onStartSelect = { lineIndex ->
                            docSelecting = true
                            docSelected = setOf(lineIndex)
                        },
                        onToggleSelect = { lineIndex ->
                            val next = if (lineIndex in docSelected) {
                                docSelected - lineIndex
                            } else {
                                docSelected + lineIndex
                            }
                            docSelected = next
                            if (next.isEmpty()) docSelecting = false
                        },
                        onLineClick = { fullTextNode = it },
                        onCopySelection = { text -> copyText(text) },
                        onCancelSelection = {
                            docSelecting = false
                            docSelected = emptySet()
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Space.md)
                            .pointerInput(frames, safeIndex, visibleIndices) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        when {
                                            dragAcc > 80f -> stepVisible(-1)
                                            dragAcc < -80f -> stepVisible(1)
                                        }
                                        dragAcc = 0f
                                    },
                                    onHorizontalDrag = { _, dragAmount ->
                                        dragAcc += dragAmount
                                    },
                                )
                            },
                    )
                } else {
                    val measurer = rememberTextMeasurer()
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val labelColor = TextStrong
                    val scrimColor = Scrim
                    val accentStrokeColor = AccentStroke
                    val layout = remember(
                        frame,
                        effectiveShowDesc,
                        canvasSize,
                        highlightActive,
                        searchTerms,
                        searchStrict,
                        density,
                        labelColor,
                    ) {
                        computeFrameLayout(
                            frame = frame,
                            canvasSize = canvasSize,
                            showDesc = effectiveShowDesc,
                            frameHasAnyText = frameHasAnyText,
                            highlightActive = highlightActive,
                            terms = searchTerms,
                            searchStrict = searchStrict,
                            measurer = measurer,
                            density = density,
                            labelColor = labelColor,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Space.md)
                            .clip(ShapeCanvas)
                            .background(SurfaceMuted)
                            .onSizeChanged { canvasSize = it }
                            .pointerInput(layout, safeIndex) {
                                detectTapGestures { pos ->
                                    val hit = layout.hitRegions
                                        .asReversed()
                                        .firstOrNull { it.bounds.contains(pos) }
                                    if (hit != null) {
                                        fullTextNode = hit.node
                                    }
                                }
                            }
                            .pointerInput(frames, safeIndex) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        when {
                                            dragAcc > 80f -> stepVisible(-1)
                                            dragAcc < -80f -> stepVisible(1)
                                        }
                                        dragAcc = 0f
                                    },
                                    onHorizontalDrag = { _, dragAmount ->
                                        dragAcc += dragAmount
                                    },
                                )
                            },
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRect(
                                color = scrimColor,
                                topLeft = Offset(layout.ox, layout.oy),
                                size = Size(layout.sw * layout.scale, layout.sh * layout.scale),
                                style = FrameStrokeBorder,
                            )
                            for (item in layout.drawItems) {
                                if (item.matched) {
                                    drawRect(
                                        color = HighlightFill,
                                        topLeft = item.topLeft,
                                        size = item.box,
                                    )
                                    drawRect(
                                        color = HighlightStroke,
                                        topLeft = item.topLeft,
                                        size = item.box,
                                        style = FrameStrokeHighlight,
                                    )
                                } else {
                                    drawRect(
                                        color = accentStrokeColor,
                                        topLeft = item.topLeft,
                                        size = item.box,
                                        style = FrameStrokeNormal,
                                    )
                                }
                                val textLayout = item.textLayout ?: continue
                                drawText(
                                    textLayout,
                                    topLeft = Offset(
                                        item.topLeft.x + 2f,
                                        item.topLeft.y + (item.box.height - textLayout.size.height) / 2f,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FrameDocumentView(
    frame: UiFrameSnapshot,
    highlightActive: Boolean,
    terms: List<String>,
    searchStrict: Boolean,
    showDesc: Boolean,
    frameHasAnyText: Boolean,
    selecting: Boolean,
    selected: Set<Int>,
    onStartSelect: (Int) -> Unit,
    onToggleSelect: (Int) -> Unit,
    onLineClick: (FrameTextNode) -> Unit,
    onCopySelection: (String) -> Unit,
    onCancelSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = remember(frame, highlightActive, terms, searchStrict, showDesc, frameHasAnyText) {
        buildDocumentLines(frame, highlightActive, terms, searchStrict, showDesc, frameHasAnyText)
    }
    Column(
        modifier = modifier
            .clip(ShapeCanvas)
            .background(CardBackground),
    ) {
        if (selecting) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.sm),
            ) {
                Text(
                    "已选 ${selected.size}",
                    color = TextStrong,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        val text = lines
                            .filterIndexed { i, _ -> i in selected }
                            .joinToString("\n") { it.text }
                        if (text.isNotBlank()) onCopySelection(text)
                    },
                    enabled = selected.isNotEmpty(),
                ) {
                    Text("复制", color = Accent)
                }
                TextButton(onClick = onCancelSelection) {
                    Text("取消", color = TextMuted)
                }
            }
        }
        if (lines.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("本帧无可显示文字", color = TextMuted, fontSize = 14.sp)
            }
        } else {
            val listState = rememberLazyListState()
            LaunchedEffect(frame.id, lines) {
                val index = lines.indexOfFirst { it.matched }
                if (index >= 0) listState.scrollToItem(index)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Space.md, vertical = Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                itemsIndexed(
                    items = lines,
                    key = { index, line -> "${index}_${line.node.left}_${line.node.top}_${line.text.hashCode()}" },
                ) { lineIndex, line ->
                    val picked = lineIndex in selected
                    Text(
                        text = line.text,
                        color = if (line.matched || picked) Accent else TextStrong,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        fontWeight = if (line.matched || picked) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(
                                when {
                                    picked -> AccentSoft
                                    line.matched -> HighlightFill
                                    else -> Color.Transparent
                                },
                            )
                            .combinedClickable(
                                onClick = {
                                    if (selecting) onToggleSelect(lineIndex) else onLineClick(line.node)
                                },
                                onLongClick = { onStartSelect(lineIndex) },
                            )
                            .padding(horizontal = Space.xs, vertical = Space.xs),
                    )
                }
            }
        }
    }
}

private data class DocumentLine(
    val text: String,
    val node: FrameTextNode,
    val matched: Boolean,
)

/** 用 displayLabel（含 desc），按 UI 坐标从上到下、从左到右排列 */
private fun buildDocumentLines(
    frame: UiFrameSnapshot,
    highlightActive: Boolean,
    terms: List<String>,
    searchStrict: Boolean,
    showDesc: Boolean,
    frameHasAnyText: Boolean,
): List<DocumentLine> {
    return frame.texts
        .mapNotNull { node ->
            val text = FrameJson.displayLabel(node, showDesc, frameHasAnyText)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val matched = highlightActive && (
                fieldMatches(node.text, terms, searchStrict) ||
                    fieldMatches(node.desc, terms, searchStrict)
                )
            DocumentLine(text = text, node = node, matched = matched)
        }
        .sortedWith(compareBy({ it.node.top }, { it.node.left }))
}

private fun fieldMatches(field: String, terms: List<String>, strict: Boolean): Boolean {
    if (field.isBlank() || terms.isEmpty()) return false
    return terms.any { term ->
        if (strict) field.contains(term) else field.contains(term, ignoreCase = true)
    }
}

private val FrameStrokeBorder = Stroke(width = 2f)
private val FrameStrokeHighlight = Stroke(width = 3f)
private val FrameStrokeNormal = Stroke(width = 1.5f)

private data class HitRegion(
    val node: FrameTextNode,
    val bounds: Rect,
)

private data class FrameDrawItem(
    val topLeft: Offset,
    val box: Size,
    val matched: Boolean,
    val textLayout: androidx.compose.ui.text.TextLayoutResult?,
)

private data class FrameHitLayout(
    val hitRegions: List<HitRegion>,
    val drawItems: List<FrameDrawItem>,
    val scale: Float,
    val ox: Float,
    val oy: Float,
    val sw: Float,
    val sh: Float,
)

private fun computeFrameLayout(
    frame: UiFrameSnapshot,
    canvasSize: IntSize,
    showDesc: Boolean,
    frameHasAnyText: Boolean,
    highlightActive: Boolean,
    terms: List<String>,
    searchStrict: Boolean,
    measurer: androidx.compose.ui.text.TextMeasurer,
    density: androidx.compose.ui.unit.Density,
    labelColor: Color,
): FrameHitLayout {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) {
        return FrameHitLayout(emptyList(), emptyList(), 1f, 0f, 0f, 1f, 1f)
    }
    val sw = frame.screenWidth.coerceAtLeast(1).toFloat()
    val sh = frame.screenHeight.coerceAtLeast(1).toFloat()
    val scale = minOf(canvasSize.width / sw, canvasSize.height / sh)
    val ox = (canvasSize.width - sw * scale) / 2f
    val oy = (canvasSize.height - sh * scale) / 2f
    val regions = ArrayList<HitRegion>()
    val drawItems = ArrayList<FrameDrawItem>()
    for (node in frame.texts) {
        val matched = highlightActive && (
            fieldMatches(node.text, terms, searchStrict) ||
                fieldMatches(node.desc, terms, searchStrict)
            )
        val label = FrameJson.displayLabel(node, showDesc, frameHasAnyText)
        if (label == null && !matched) continue
        val l = ox + node.left * scale
        val t = oy + node.top * scale
        val r = ox + node.right * scale
        val b = oy + node.bottom * scale
        val w = (r - l).coerceAtLeast(1f)
        val h = (b - t).coerceAtLeast(1f)
        regions += HitRegion(node, Rect(l, t, r, b))
        val textLayout = if (label != null) {
            val fontPx = (h * 0.55f).coerceIn(8f, 28f)
            measurer.measure(
                text = label,
                style = TextStyle(
                    color = labelColor,
                    fontSize = with(density) { fontPx.toSp() },
                    fontWeight = if (matched) FontWeight.Bold else FontWeight.Normal,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                constraints = androidx.compose.ui.unit.Constraints(
                    maxWidth = w.toInt().coerceAtLeast(1),
                ),
            )
        } else {
            null
        }
        drawItems += FrameDrawItem(
            topLeft = Offset(l, t),
            box = Size(w, h),
            matched = matched,
            textLayout = textLayout,
        )
    }
    return FrameHitLayout(regions, drawItems, scale, ox, oy, sw, sh)
}

private fun fullTextCopy(node: FrameTextNode): String = buildString {
    if (node.hasText()) append(node.text.trim())
    if (node.hasDesc()) {
        if (isNotEmpty()) append('\n')
        append(node.desc.trim())
    }
}

@Composable
private fun rememberCopyAction(): (String) -> Unit {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    return { text ->
        if (text.isNotBlank()) {
            clipboard.setText(AnnotatedString(text))
            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
        }
    }
}


private enum class ChatRole { USER, AI, OTHER }

private data class ChatLine(
    val text: String,
    val role: ChatRole,
    val node: FrameTextNode,
    val frameId: Long,
    val capturedAt: Long,
)

private fun chatRole(className: String): ChatRole = when (className) {
    FrameJson.CHATGPT_USER_CLASS -> ChatRole.USER
    FrameJson.CHATGPT_AI_CLASS -> ChatRole.AI
    else -> ChatRole.OTHER
}

private fun buildChatLines(frames: List<UiFrameSnapshot>): List<ChatLine> {
    val raw = ArrayList<ChatLine>()
    for (frame in frames.sortedBy { it.capturedAt }) {
        val nodes = frame.texts.sortedWith(compareBy({ it.top }, { it.left }))
        for (node in nodes) {
            val text = node.text.trim()
            if (text.isEmpty()) continue
            raw += ChatLine(
                text = text,
                role = chatRole(node.className),
                node = node,
                frameId = frame.id,
                capturedAt = frame.capturedAt,
            )
        }
    }
    val adjacent = ArrayList<ChatLine>(raw.size)
    for (line in raw) {
        val last = adjacent.lastOrNull()
        if (last != null && last.role == line.role && line.text.contains(last.text)) {
            adjacent[adjacent.lastIndex] = line
        } else {
            adjacent += line
        }
    }
    val kept = ArrayList<ChatLine>(adjacent.size)
    for (line in adjacent) {
        kept.removeAll { existing ->
            existing.role == line.role &&
                existing.text.length <= line.text.length &&
                line.text.contains(existing.text)
        }
        kept += line
    }
    return kept
}

@Composable
private fun ChatTranscriptView(
    frames: List<UiFrameSnapshot>,
    anchorFrameId: Long,
    anchorCapturedAt: Long,
    onLineClick: (FrameTextNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lines by remember { mutableStateOf<List<ChatLine>?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(frames) {
        lines = null
        lines = withContext(Dispatchers.Default) { buildChatLines(frames) }
    }
    val readyLines = lines
    LaunchedEffect(anchorFrameId, anchorCapturedAt, readyLines) {
        val current = readyLines ?: return@LaunchedEffect
        if (current.isEmpty()) return@LaunchedEffect
        val exact = current.indexOfFirst { it.frameId == anchorFrameId }
        val target = if (exact >= 0) {
            exact
        } else {
            val later = current.indexOfFirst { it.capturedAt >= anchorCapturedAt }
            if (later >= 0) later else current.lastIndex
        }
        if (target >= 0) listState.scrollToItem(target)
    }
    if (readyLines == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("正在整理对话…", color = TextMuted)
        }
        return
    }
    if (readyLines.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("该范围内没有可合并的界面文字", color = TextMuted)
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        itemsIndexed(readyLines, key = { index, _ -> index }) { _, line ->
            val color = when (line.role) {
                ChatRole.USER -> Accent
                ChatRole.AI -> Success
                ChatRole.OTHER -> TextMuted
            }
            val label = when (line.role) {
                ChatRole.USER -> "用户"
                ChatRole.AI -> "AI"
                ChatRole.OTHER -> "未分类"
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(SurfaceMuted)
                    .clickable { onLineClick(line.node) }
                    .padding(Space.sm),
            ) {
                Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text(line.text, color = color, fontSize = 14.sp)
            }
        }
    }
}


