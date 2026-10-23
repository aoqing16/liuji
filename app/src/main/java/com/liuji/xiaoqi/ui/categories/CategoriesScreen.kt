package com.liuji.xiaoqi.ui.categories

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuji.xiaoqi.data.AppCategoryAssignment
import com.liuji.xiaoqi.data.CategoryColors
import com.liuji.xiaoqi.data.CategoryWithApps
import com.liuji.xiaoqi.data.UserCategoryWithCount
import com.liuji.xiaoqi.ui.components.AppIconImage
import com.liuji.xiaoqi.ui.theme.Accent
import com.liuji.xiaoqi.ui.theme.AccentSoft
import com.liuji.xiaoqi.ui.theme.BorderDefault
import com.liuji.xiaoqi.ui.theme.CardBackground
import com.liuji.xiaoqi.ui.theme.Danger
import com.liuji.xiaoqi.ui.theme.SurfaceChip
import com.liuji.xiaoqi.ui.theme.SurfaceMuted
import com.liuji.xiaoqi.ui.theme.TextMuted
import com.liuji.xiaoqi.ui.theme.TextStrong

/** 分类卡片默认展示的应用 chip 数；超出需点「展开」以降低滚动时节点与 Semantics 开销 */
private const val COLLAPSED_APP_CHIP_LIMIT = 8

@Composable
fun CategoriesScreen(
    loading: Boolean,
    categoriesWithApps: List<CategoryWithApps>,
    listFirstVisibleIndex: Int = 0,
    listFirstVisibleOffset: Int = 0,
    scrollToCategoryId: String? = null,
    onScroll: (index: Int, offset: Int) -> Unit = { _, _ -> },
    onScrollToCategoryConsumed: () -> Unit = {},
    onCreateCategory: () -> Unit,
    onEditCategory: (UserCategoryWithCount) -> Unit,
    onDeleteCategory: (UserCategoryWithCount) -> Unit,
    onAddAppToCategory: (UserCategoryWithCount) -> Unit,
    onRemoveAppFromCategory: (UserCategoryWithCount, AppCategoryAssignment) -> Unit,
    iconLoader: (String) -> Drawable?,
) {
    val listState = remember {
        LazyListState(
            firstVisibleItemIndex = listFirstVisibleIndex,
            firstVisibleItemScrollOffset = listFirstVisibleOffset,
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            onScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }
    LaunchedEffect(scrollToCategoryId) {
        val id = scrollToCategoryId ?: return@LaunchedEffect
        val index = categoriesWithApps.indexOfFirst { it.category.id == id }
        if (index >= 0) {
            listState.scrollToItem(index)
            onScrollToCategoryConsumed()
        }
    }

    val iconCache = remember { mutableMapOf<String, Drawable?>() }
    val cachedIconLoader: (String) -> Drawable? = remember(iconLoader) {
        { pkg -> iconCache.getOrPut(pkg) { iconLoader(pkg) } }
    }

    Scaffold(
        // 外层 PhoneHomeScaffold 已铺 PageBackground，避免双层全屏 Overdraw
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "分类",
                    color = TextStrong,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCreateCategory) {
                    Icon(Icons.Outlined.Add, contentDescription = "新建分类", tint = Accent)
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                loading && categoriesWithApps.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Accent,
                    )
                }
                categoriesWithApps.isEmpty() -> {
                    Text(
                        "暂无分类",
                        color = TextMuted,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.semantics { hideFromAccessibility() },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(categoriesWithApps, key = { it.category.id }) { item ->
                            CategoryCard(
                                item = item,
                                iconLoader = cachedIconLoader,
                                onEdit = { onEditCategory(item.category) },
                                onDelete = { onDeleteCategory(item.category) },
                                onAddApp = { onAddAppToCategory(item.category) },
                                onRemoveApp = { app -> onRemoveAppFromCategory(item.category, app) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    item: CategoryWithApps,
    iconLoader: (String) -> Drawable?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddApp: () -> Unit,
    onRemoveApp: (AppCategoryAssignment) -> Unit,
) {
    val cat = item.category
    val tintBg = cat.color.copy(alpha = 0.18f)
    val tintBorder = cat.color.copy(alpha = 0.35f)
    var appsExpanded by remember(cat.id) { mutableStateOf(false) }
    val totalApps = item.apps.size
    val collapsed = totalApps > COLLAPSED_APP_CHIP_LIMIT && !appsExpanded
    val visibleApps = if (collapsed) {
        item.apps.take(COLLAPSED_APP_CHIP_LIMIT)
    } else {
        item.apps
    }

    val slots = buildList {
        visibleApps.forEach { add(CategoryChipSlot.App(it)) }
        if (totalApps > COLLAPSED_APP_CHIP_LIMIT) {
            add(
                CategoryChipSlot.Expand(
                    collapsed = collapsed,
                    hiddenCount = totalApps - COLLAPSED_APP_CHIP_LIMIT,
                ),
            )
        }
        add(CategoryChipSlot.Add)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBackground)
            .border(1.dp, BorderDefault, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.DragIndicator,
                contentDescription = null,
                tint = TextMuted.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tintBg)
                    .border(1.dp, tintBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Category,
                    contentDescription = null,
                    tint = cat.color,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = cat.name,
                color = TextStrong,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceChip),
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑", tint = TextMuted)
            }
            if (!cat.builtin) {
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceChip),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = Danger)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            slots.chunked(2).forEach { rowSlots ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowSlots.forEach { slot ->
                        Box(Modifier.weight(1f)) {
                            when (slot) {
                                is CategoryChipSlot.App -> key(slot.app.packageName) {
                                    AppTagChip(
                                        app = slot.app,
                                        iconLoader = iconLoader,
                                        onRemove = { onRemoveApp(slot.app) },
                                    )
                                }
                                is CategoryChipSlot.Expand -> ExpandAppsChip(
                                    collapsed = slot.collapsed,
                                    hiddenCount = slot.hiddenCount,
                                    accent = cat.color,
                                    onClick = { appsExpanded = !appsExpanded },
                                )
                                CategoryChipSlot.Add -> AddAppChip(
                                    onClick = onAddApp,
                                    accent = cat.color,
                                )
                            }
                        }
                    }
                    if (rowSlots.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private sealed interface CategoryChipSlot {
    data class App(val app: AppCategoryAssignment) : CategoryChipSlot
    data class Expand(val collapsed: Boolean, val hiddenCount: Int) : CategoryChipSlot
    data object Add : CategoryChipSlot
}

@Composable
private fun AppTagChip(
    app: AppCategoryAssignment,
    iconLoader: (String) -> Drawable?,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceChip)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconImage(
            packageName = app.packageName,
            drawable = iconLoader(app.packageName),
            tint = app.categoryColor,
            size = 18.dp,
        )
        Text(
            app.label,
            color = TextStrong,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .weight(1f),
        )
        Icon(
            Icons.Filled.Close,
            contentDescription = "移除",
            tint = TextMuted,
            modifier = Modifier
                .size(16.dp)
                .clickable(onClick = onRemove),
        )
    }
}

@Composable
private fun AddAppChip(onClick: () -> Unit, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("添加应用", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ExpandAppsChip(
    collapsed: Boolean,
    hiddenCount: Int,
    accent: Color,
    onClick: () -> Unit,
) {
    val label = if (collapsed) "还有 $hiddenCount 个" else "收起"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceChip)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryEditorDialog(
    title: String,
    initialName: String,
    initialColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: Color) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var picked by remember(initialColor) { mutableStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        titleContentColor = TextStrong,
        textContentColor = TextStrong,
        title = { Text(title, color = TextStrong) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分类名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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
                    ),
                )
                Spacer(Modifier.height(12.dp))
                Text("配色", color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryColors.pickerPresets.forEach { color ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(color)
                                .border(
                                    width = if (picked == color) 2.dp else 0.dp,
                                    color = TextStrong,
                                    shape = RoundedCornerShape(99.dp),
                                )
                                .clickable { picked = color },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, picked) },
                enabled = name.trim().isNotEmpty(),
            ) {
                Text("保存", color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextMuted) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppPickerSheet(
    categoryName: String,
    candidates: List<AppCategoryAssignment>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (List<AppCategoryAssignment>) -> Unit,
    iconLoader: (String) -> Drawable?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableStateOf(setOf<String>()) }
    val iconCache = remember { mutableMapOf<String, Drawable?>() }
    val cachedIconLoader: (String) -> Drawable? = remember(iconLoader) {
        { pkg -> iconCache.getOrPut(pkg) { iconLoader(pkg) } }
    }
    val filtered = remember(candidates, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) candidates
        else candidates.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }
    val selectedApps = remember(candidates, selected) {
        candidates.filter { it.packageName in selected }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardBackground,
        contentColor = TextStrong,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "添加到「$categoryName」",
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                color = TextStrong,
            )
            TextButton(
                onClick = { onConfirm(selectedApps) },
                enabled = selected.isNotEmpty(),
            ) {
                Text(
                    if (selected.isEmpty()) "添加" else "添加 ${selected.size} 个",
                    color = if (selected.isEmpty()) TextMuted else Accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("搜索已安装应用") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextStrong,
                unfocusedTextColor = TextStrong,
                focusedContainerColor = SurfaceMuted,
                unfocusedContainerColor = SurfaceMuted,
                cursorColor = Accent,
                focusedBorderColor = Accent,
                unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                focusedPlaceholderColor = TextMuted,
                unfocusedPlaceholderColor = TextMuted,
            ),
        )
        if (filtered.isEmpty()) {
            Text(
                "没有可添加的应用",
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.semantics { hideFromAccessibility() },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
        ) {
            items(filtered, key = { it.packageName }) { app ->
                val checked = app.packageName in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (checked) AccentSoft else Color.Transparent)
                        .clickable {
                            selected = if (checked) {
                                selected - app.packageName
                            } else {
                                selected + app.packageName
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (checked) Accent else SurfaceChip)
                            .border(
                                1.dp,
                                if (checked) Accent else BorderDefault,
                                RoundedCornerShape(6.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (checked) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    AppIconImage(
                        packageName = app.packageName,
                        drawable = cachedIconLoader(app.packageName),
                        tint = app.categoryColor,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, color = TextStrong)
                        Text(app.categoryName, color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteCategoryConfirmDialog(
    categoryName: String,
    appCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        titleContentColor = TextStrong,
        textContentColor = TextStrong,
        title = { Text("删除分类", color = TextStrong) },
        text = {
            Text(
                if (appCount > 0) {
                    "确定删除「$categoryName」？其下 $appCount 个应用将归入「其他」。"
                } else {
                    "确定删除「$categoryName」？"
                },
                color = TextStrong,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("删除", color = Danger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextMuted) }
        },
    )
}
