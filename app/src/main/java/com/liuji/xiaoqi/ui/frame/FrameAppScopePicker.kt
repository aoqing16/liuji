package com.liuji.xiaoqi.ui.frame

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.liuji.xiaoqi.data.CategoryWithApps
import com.liuji.xiaoqi.ui.components.AppIconImage
import com.liuji.xiaoqi.ui.theme.Accent
import com.liuji.xiaoqi.ui.theme.AccentSoft
import com.liuji.xiaoqi.ui.theme.BorderDefault
import com.liuji.xiaoqi.ui.theme.CardBackground
import com.liuji.xiaoqi.ui.theme.PageBackground
import com.liuji.xiaoqi.ui.theme.Space
import com.liuji.xiaoqi.ui.theme.TextMuted
import com.liuji.xiaoqi.ui.theme.TextStrong

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FrameAppScopePicker(
    categories: List<CategoryWithApps>,
    loading: Boolean,
    initialSelected: Set<String>,
    iconLoader: (String) -> Drawable?,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember(initialSelected) { mutableStateOf(initialSelected) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    val q = query.trim()
    val visible = remember(categories, q) {
        if (q.isEmpty()) {
            categories
        } else {
            categories.mapNotNull { group ->
                val apps = group.apps.filter {
                    it.label.contains(q, ignoreCase = true) ||
                        it.packageName.contains(q, ignoreCase = true) ||
                        group.category.name.contains(q, ignoreCase = true)
                }
                if (apps.isEmpty() && !group.category.name.contains(q, ignoreCase = true)) {
                    null
                } else {
                    group.copy(
                        apps = if (group.category.name.contains(q, ignoreCase = true) && apps.isEmpty()) {
                            group.apps
                        } else {
                            apps
                        },
                    )
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PageBackground)
                .statusBarsPadding()
                .padding(Space.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = TextStrong)
                }
                Text(
                    "选择应用",
                    color = TextStrong,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onConfirm(selected) },
                    enabled = selected.isNotEmpty(),
                ) {
                    Text("确定", color = if (selected.isEmpty()) TextMuted else Accent)
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.sm),
                singleLine = true,
                placeholder = { Text("搜索应用", color = TextMuted) },
            )
            Text(
                text = if (selected.isEmpty()) "可勾选整个分类，或展开后多选应用" else "已选 ${selected.size} 个应用",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = Space.sm),
            )
            when {
                loading && categories.isEmpty() -> {
                    Text("正在加载分类…", color = TextMuted, modifier = Modifier.padding(top = Space.md))
                }
                visible.isEmpty() -> {
                    Text("没有匹配的应用", color = TextMuted, modifier = Modifier.padding(top = Space.md))
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        items(visible, key = { it.category.id }) { group ->
                            val allIds = categories
                                .firstOrNull { it.category.id == group.category.id }
                                ?.apps
                                ?.map { it.packageName }
                                .orEmpty()
                            val pickedInGroup = allIds.count { it in selected }
                            val state = when {
                                allIds.isNotEmpty() && pickedInGroup == allIds.size -> ToggleableState.On
                                pickedInGroup > 0 -> ToggleableState.Indeterminate
                                else -> ToggleableState.Off
                            }
                            val open = q.isNotEmpty() || group.category.id in expanded
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(CardBackground)
                                    .border(1.dp, BorderDefault, RoundedCornerShape(16.dp))
                                    .padding(horizontal = Space.sm, vertical = Space.xs),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    TriStateCheckbox(
                                        state = state,
                                        onClick = {
                                            selected = if (pickedInGroup == allIds.size && allIds.isNotEmpty()) {
                                                selected - allIds.toSet()
                                            } else {
                                                selected + allIds
                                            }
                                        },
                                    )
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(group.category.color),
                                    )
                                    Text(
                                        text = group.category.name,
                                        color = TextStrong,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .padding(start = Space.sm)
                                            .weight(1f)
                                            .clickable {
                                                expanded = if (group.category.id in expanded) {
                                                    expanded - group.category.id
                                                } else {
                                                    expanded + group.category.id
                                                }
                                            },
                                    )
                                    Text(
                                        "$pickedInGroup/${allIds.size}",
                                        color = TextMuted,
                                        fontSize = 12.sp,
                                    )
                                    IconButton(onClick = {
                                        expanded = if (group.category.id in expanded) {
                                            expanded - group.category.id
                                        } else {
                                            expanded + group.category.id
                                        }
                                    }) {
                                        Icon(
                                            imageVector = if (open) {
                                                Icons.Outlined.ExpandLess
                                            } else {
                                                Icons.Outlined.ExpandMore
                                            },
                                            contentDescription = if (open) "收起" else "展开",
                                            tint = TextMuted,
                                        )
                                    }
                                }
                                if (open) {
                                    FlowRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = Space.sm, end = Space.sm, bottom = Space.sm),
                                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                                        verticalArrangement = Arrangement.spacedBy(Space.sm),
                                    ) {
                                        group.apps.forEach { app ->
                                            val picked = app.packageName in selected
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .height(36.dp)
                                                    .widthIn(max = 168.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (picked) AccentSoft else PageBackground)
                                                    .border(
                                                        1.dp,
                                                        if (picked) Accent else BorderDefault,
                                                        RoundedCornerShape(10.dp),
                                                    )
                                                    .clickable {
                                                        selected = if (picked) {
                                                            selected - app.packageName
                                                        } else {
                                                            selected + app.packageName
                                                        }
                                                    }
                                                    .padding(horizontal = Space.sm),
                                            ) {
                                                AppIconImage(
                                                    packageName = app.packageName,
                                                    drawable = iconLoader(app.packageName),
                                                    tint = group.category.color,
                                                    size = 18.dp,
                                                )
                                                Text(
                                                    text = app.label,
                                                    color = TextStrong,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(start = Space.xs),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
