package com.liuji.xiaoqi.ui.frame

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.liuji.xiaoqi.data.frame.FrameJson
import com.liuji.xiaoqi.data.frame.FrameSearchHit
import com.liuji.xiaoqi.data.frame.FrameTextNode
import com.liuji.xiaoqi.data.frame.UiFrameSnapshot
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
import com.liuji.xiaoqi.ui.theme.Space
import com.liuji.xiaoqi.ui.theme.SurfaceMuted
import com.liuji.xiaoqi.ui.theme.TextMuted
import com.liuji.xiaoqi.ui.theme.TextStrong
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())

private enum class FrameViewMode {
    CANVAS,
    DOCUMENT,
}

@Composable
fun FrameViewerScreen(
    appLabel: String,
    packageName: String,
    frames: List<UiFrameSnapshot>,
    index: Int,
    loading: Boolean,
    searchQuery: String,
    searchInCurrentApp: Boolean,
    searchStrict: Boolean,
    searchHits: List<FrameSearchHit>,
    searchHitIndex: Int,
    searchLoading: Boolean,
    searchMessage: String?,
    onBack: () -> Unit,
    onIndexChange: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchInCurrentAppChange: (Boolean) -> Unit,
    onSearchStrictChange: (Boolean) -> Unit,
    onRunSearch: () -> Unit,
    onJumpToSearchHit: (Int) -> Unit,
    onSearchPrev: () -> Unit,
    onSearchNext: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val safeIndex = index.coerceIn(0, (frames.size - 1).coerceAtLeast(0))
    val frame = frames.getOrNull(safeIndex)
    var dragAcc by remember { mutableFloatStateOf(0f) }
    var showDesc by remember(safeIndex) { mutableStateOf(false) }
    var fullTextNode by remember { mutableStateOf<FrameTextNode?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var viewMode by rememberSaveable { mutableStateOf(FrameViewMode.CANVAS) }

    // 有结果时自动关闭对话框（ViewModel 已跳转到首条命中帧）
    LaunchedEffect(searchLoading, searchHits.size) {
        if (!searchLoading && searchHits.isNotEmpty() && showSearchDialog) {
            showSearchDialog = false
        }
    }

    if (showSearchDialog) {
        FrameSearchDialog(
            query = searchQuery,
            inCurrentApp = searchInCurrentApp,
            strict = searchStrict,
            hits = searchHits,
            hitIndex = searchHitIndex,
            loading = searchLoading,
            message = searchMessage,
            onQueryChange = onSearchQueryChange,
            onInCurrentAppChange = onSearchInCurrentAppChange,
            onStrictChange = onSearchStrictChange,
            onSearch = onRunSearch,
            onJump = { i ->
                onJumpToSearchHit(i)
                showSearchDialog = false
            },
            onDismiss = { showSearchDialog = false },
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
                TextButton(onClick = { fullTextNode = null }) {
                    Text("关闭", color = Accent)
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
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
                    viewMode = if (viewMode == FrameViewMode.CANVAS) {
                        FrameViewMode.DOCUMENT
                    } else {
                        FrameViewMode.CANVAS
                    }
                },
            ) {
                Icon(
                    imageVector = if (viewMode == FrameViewMode.DOCUMENT) {
                        Icons.Outlined.GridView
                    } else {
                        Icons.AutoMirrored.Outlined.Article
                    },
                    contentDescription = if (viewMode == FrameViewMode.DOCUMENT) {
                        "切换画面视图"
                    } else {
                        "切换文档视图"
                    },
                    tint = Accent,
                )
            }
            IconButton(onClick = { showSearchDialog = true }) {
                Icon(Icons.Outlined.Search, contentDescription = "搜索画面", tint = Accent)
            }
        }

        if (searchHits.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
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
                    text = "结果 ${searchHitIndex + 1} / ${searchHits.size}",
                    color = Accent,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onSearchNext,
                    enabled = searchHitIndex < searchHits.lastIndex,
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

            frame != null -> {
                val frameHasAnyText = frame.hasAnyText
                val q = searchQuery.trim()
                val highlightActive = searchHits.isNotEmpty() && q.isNotEmpty()
                // 命中仅在 desc 上时自动展开 desc，便于看到高亮对应文案
                val forceDescForSearch = highlightActive && frame.texts.any { node ->
                    !fieldMatches(node.text, q, searchStrict) &&
                        fieldMatches(node.desc, q, searchStrict)
                }
                // 整帧没有 text 时直接展示 desc，无需点「更多」
                val effectiveShowDesc = showDesc || !frameHasAnyText || forceDescForSearch
                val hasAnyDesc = frame.texts.any { it.hasDesc() }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    IconButton(
                        onClick = { onIndexChange(safeIndex - 1) },
                        enabled = safeIndex > 0,
                    ) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "上一张")
                    }
                    Text(
                        text = "${safeIndex + 1} / ${frames.size}  ·  ${timeFmt.format(Instant.ofEpochMilli(frame.capturedAt))}",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onIndexChange(safeIndex + 1) },
                        enabled = safeIndex < frames.lastIndex,
                    ) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "下一张")
                    }
                }
                if (frames.size > 1) {
                    var sliderValue by remember(safeIndex) { mutableFloatStateOf(safeIndex.toFloat()) }
                    LaunchedEffect(safeIndex) {
                        sliderValue = safeIndex.toFloat()
                    }
                    Slider(
                        value = sliderValue,
                        onValueChange = { v ->
                            sliderValue = v
                            val next = v.roundToInt().coerceIn(0, frames.lastIndex)
                            if (next != safeIndex) onIndexChange(next)
                        },
                        valueRange = 0f..frames.lastIndex.toFloat(),
                        steps = (frames.size - 2).coerceAtLeast(0),
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
                        .padding(horizontal = Space.sm),
                ) {
                    Text(
                        text = buildString {
                            if (viewMode == FrameViewMode.DOCUMENT) {
                                append("文档视图 · 按位置从上到下、从左到右")
                                if (!frameHasAnyText) append(" · 无界面文字，已显示说明")
                                else if (showDesc) append(" · 已显示说明文字")
                            } else {
                                append("文字块 ${frame.texts.size}")
                                if (!frameHasAnyText) append(" · 无界面文字，已显示说明")
                                else if (showDesc) append(" · 已显示说明文字")
                                append(" · 点击看全文 · 左右滑动切换")
                            }
                        },
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = Space.sm),
                    )
                    if (frameHasAnyText && hasAnyDesc) {
                        TextButton(onClick = { showDesc = !showDesc }) {
                            Text(
                                if (showDesc) "收起" else "更多",
                                color = Accent,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }

                if (viewMode == FrameViewMode.DOCUMENT) {
                    FrameDocumentView(
                        frame = frame,
                        highlightActive = highlightActive,
                        query = q,
                        searchStrict = searchStrict,
                        showDesc = effectiveShowDesc,
                        frameHasAnyText = frameHasAnyText,
                        onLineClick = { fullTextNode = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Space.md)
                            .pointerInput(frames, safeIndex) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        when {
                                            dragAcc > 80f -> onIndexChange(safeIndex - 1)
                                            dragAcc < -80f -> onIndexChange(safeIndex + 1)
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
                    val layout = remember(
                        frame,
                        effectiveShowDesc,
                        canvasSize,
                        highlightActive,
                        q,
                        searchStrict,
                        density,
                    ) {
                        computeFrameLayout(
                            frame = frame,
                            canvasSize = canvasSize,
                            showDesc = effectiveShowDesc,
                            frameHasAnyText = frameHasAnyText,
                            highlightActive = highlightActive,
                            query = q,
                            searchStrict = searchStrict,
                            measurer = measurer,
                            density = density,
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
                                            dragAcc > 80f -> onIndexChange(safeIndex - 1)
                                            dragAcc < -80f -> onIndexChange(safeIndex + 1)
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
                                color = Scrim,
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
                                        color = AccentStroke,
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
    query: String,
    searchStrict: Boolean,
    showDesc: Boolean,
    frameHasAnyText: Boolean,
    onLineClick: (FrameTextNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = remember(frame, highlightActive, query, searchStrict, showDesc, frameHasAnyText) {
        buildDocumentLines(frame, highlightActive, query, searchStrict, showDesc, frameHasAnyText)
    }
    Box(
        modifier = modifier
            .clip(ShapeCanvas)
            .background(CardBackground),
    ) {
        if (lines.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("本帧无可显示文字", color = TextMuted, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Space.md, vertical = Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                itemsIndexed(
                    items = lines,
                    key = { index, line -> "${index}_${line.node.left}_${line.node.top}_${line.text.hashCode()}" },
                ) { _, line ->
                    Text(
                        text = line.text,
                        color = if (line.matched) Accent else TextStrong,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        fontWeight = if (line.matched) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(if (line.matched) HighlightFill else Color.Transparent)
                            .clickable { onLineClick(line.node) }
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
    query: String,
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
                fieldMatches(node.text, query, searchStrict) ||
                    fieldMatches(node.desc, query, searchStrict)
                )
            DocumentLine(text = text, node = node, matched = matched)
        }
        .sortedWith(compareBy({ it.node.top }, { it.node.left }))
}

@Composable
private fun FrameSearchDialog(
    query: String,
    inCurrentApp: Boolean,
    strict: Boolean,
    hits: List<FrameSearchHit>,
    hitIndex: Int,
    loading: Boolean,
    message: String?,
    onQueryChange: (String) -> Unit,
    onInCurrentAppChange: (Boolean) -> Unit,
    onStrictChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
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
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("关键词或记录编号") },
                    placeholder = { Text("例如：微信 / 123") },
                    colors = OutlinedTextFieldDefaults.colors(
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
                    ),
                )
                Text("范围", color = TextMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    FilterChip(
                        selected = inCurrentApp,
                        onClick = { onInCurrentAppChange(true) },
                        label = { Text("仅当前应用") },
                    )
                    FilterChip(
                        selected = !inCurrentApp,
                        onClick = { onInCurrentAppChange(false) },
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
                if (loading) {
                    Text("搜索中…", color = TextMuted, fontSize = 13.sp)
                }
                message?.let {
                    Text(it, color = TextMuted, fontSize = 13.sp)
                }
                if (hits.isNotEmpty()) {
                    Text(
                        "共 ${hits.size} 条，点选跳转",
                        color = TextStrong,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    hits.forEachIndexed { i, hit ->
                        val selected = i == hitIndex
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(if (selected) AccentSoft else Color.Transparent)
                                .clickable { onJump(i) }
                                .padding(horizontal = Space.sm, vertical = Space.sm),
                        ) {
                            Text(
                                text = buildString {
                                    append(timeFmt.format(Instant.ofEpochMilli(hit.capturedAt)))
                                    if (!inCurrentApp) {
                                        append(" · ")
                                        append(hit.appLabel.ifBlank { hit.packageName })
                                    }
                                },
                                color = Accent,
                                fontSize = 12.sp,
                            )
                            Text(
                                text = hit.snippet,
                                color = TextStrong,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
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
}

private fun fieldMatches(field: String, query: String, strict: Boolean): Boolean {
    if (field.isBlank() || query.isBlank()) return false
    return if (strict) field.contains(query) else field.contains(query, ignoreCase = true)
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
    query: String,
    searchStrict: Boolean,
    measurer: androidx.compose.ui.text.TextMeasurer,
    density: androidx.compose.ui.unit.Density,
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
            fieldMatches(node.text, query, searchStrict) ||
                fieldMatches(node.desc, query, searchStrict)
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
                    color = TextStrong,
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
