package com.liuji.xiaoqi.ui.settings

import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuji.xiaoqi.data.AppCategoryAssignment
import com.liuji.xiaoqi.data.db.PrivacyAppEntity
import com.liuji.xiaoqi.data.frame.FrameInterval
import com.liuji.xiaoqi.frame.FrameBlacklistItem
import com.liuji.xiaoqi.hindsight.WebDavAccount
import com.liuji.xiaoqi.ui.categories.AddAppPickerSheet
import com.liuji.xiaoqi.ui.components.AppIconImage
import com.liuji.xiaoqi.ui.theme.Accent
import com.liuji.xiaoqi.ui.theme.AccentSoft
import com.liuji.xiaoqi.ui.theme.SurfaceChip
import com.liuji.xiaoqi.ui.theme.BorderDefault
import com.liuji.xiaoqi.ui.theme.CardBackground
import com.liuji.xiaoqi.ui.theme.PageBackground
import com.liuji.xiaoqi.ui.theme.ShapeCard
import com.liuji.xiaoqi.ui.theme.ShapeChip
import com.liuji.xiaoqi.ui.theme.Space
import com.liuji.xiaoqi.ui.theme.Stroke
import com.liuji.xiaoqi.ui.theme.TextMuted
import com.liuji.xiaoqi.ui.theme.TextStrong

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    frameEnabled: Boolean,
    frameInterval: FrameInterval,
    accessibilityEnabled: Boolean,
    accessibilityLegacyListed: Boolean = false,
    frameCount: Long,
    webDavAccount: WebDavAccount,
    hindsightConfigured: Boolean,
    hindsightLastSync: String?,
    cloudSyncing: Boolean,
    onBack: () -> Unit,
    onToggleFrame: (Boolean) -> Unit,
    onIntervalChange: (FrameInterval) -> Unit,
    onOpenAccessibility: () -> Unit,
    onRebuildUsage: () -> Unit,
    usageRebuilding: Boolean = false,
    onSaveWebDav: (url: String, user: String, password: String, root: String) -> Unit,
    onSyncCloud: () -> Unit,
    onOpenRuntimeLog: () -> Unit = {},
    frameBlacklist: List<FrameBlacklistItem> = emptyList(),
    frameBlacklistCandidates: List<AppCategoryAssignment> = emptyList(),
    frameBlacklistSearch: String = "",
    frameBlacklistPickerOpen: Boolean = false,
    onOpenBlacklistPicker: () -> Unit = {},
    onDismissBlacklistPicker: () -> Unit = {},
    onBlacklistSearchChange: (String) -> Unit = {},
    onAddBlacklist: (List<AppCategoryAssignment>) -> Unit = {},
    onRemoveBlacklist: (FrameBlacklistItem) -> Unit = {},
    privacyApps: List<PrivacyAppEntity> = emptyList(),
    privacyAppCandidates: List<AppCategoryAssignment> = emptyList(),
    privacyAppSearch: String = "",
    privacyAppPickerOpen: Boolean = false,
    onOpenPrivacyAppPicker: () -> Unit = {},
    onDismissPrivacyAppPicker: () -> Unit = {},
    onPrivacyAppSearchChange: (String) -> Unit = {},
    onAddPrivacyApps: (List<AppCategoryAssignment>) -> Unit = {},
    onRemovePrivacyApp: (PrivacyAppEntity) -> Unit = {},
    iconLoader: (String) -> Drawable? = { null },
) {
    BackHandler(onBack = onBack)
    var url by remember(webDavAccount.url) { mutableStateOf(webDavAccount.url) }
    var user by remember(webDavAccount.user) { mutableStateOf(webDavAccount.user) }
    var password by remember { mutableStateOf("") }
    var root by remember(webDavAccount.rootPath) { mutableStateOf(webDavAccount.rootPath) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "设置",
                color = TextStrong,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(Space.lg))

        SettingsCard(title = "电脑看板 · 云盘只读") {
            Text(
                "填写与电脑端相同的云盘账号，仅拉取电脑使用记录到手机查看。不会向云盘写入任何文件。",
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(Space.md))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("云盘地址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.sm))
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("账号（邮箱）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.sm))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = {
                    Text(
                        if (hindsightConfigured) "应用密码（留空则保持已保存）"
                        else "应用密码",
                    )
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.sm))
            OutlinedTextField(
                value = root,
                onValueChange = { root = it },
                label = { Text("同步目录") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Button(onClick = { onSaveWebDav(url, user, password, root) }) {
                    Text("保存账号")
                }
                Button(
                    onClick = onSyncCloud,
                    enabled = !cloudSyncing && (hindsightConfigured || password.isNotBlank()),
                ) {
                    Text(if (cloudSyncing) "同步中…" else "立即拉取")
                }
            }
            Spacer(Modifier.height(Space.sm))
            Text(
                text = when {
                    hindsightLastSync != null -> "上次同步：$hindsightLastSync"
                    hindsightConfigured -> "已保存账号，尚未拉取"
                    else -> "尚未设置"
                },
                color = TextMuted,
                fontSize = 11.sp,
            )
        }

        Spacer(Modifier.height(Space.md))

        SettingsCard(title = "画面记录") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "无障碍权限",
                        color = TextStrong,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = when {
                            accessibilityEnabled ->
                                "已开启「留迹画面记录」。帧视图与文档视图可读取界面文字与位置；点击开关可前往系统设置关闭。"
                            accessibilityLegacyListed ->
                                "请到系统无障碍中开启「留迹画面记录」。检测到仍挂着旧的「留迹」项：请先关闭旧项，再打开「留迹画面记录」（微信等应用需本项才能正确读字）。"
                            else ->
                                "请到系统无障碍中开启「留迹画面记录」（不是仅显示「留迹」的旧项）。用于帧视图读界面文字；微信等混淆节点的应用依赖此项。"
                        },
                        color = if (accessibilityEnabled) TextMuted else Accent,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = Space.xs, end = Space.sm),
                    )
                }
                Switch(
                    checked = accessibilityEnabled,
                    onCheckedChange = { onOpenAccessibility() },
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "开启画面记录",
                        color = TextStrong,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "记录前台界面上的可见文字与位置。「有操作时」：你点击或滑动且画面内容变化时才记录（带防抖，更省电）。",
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = Space.xs, end = Space.sm),
                    )
                }
                Switch(
                    checked = frameEnabled,
                    onCheckedChange = { enabled ->
                        onToggleFrame(enabled)
                        if (enabled && !accessibilityEnabled) {
                            onOpenAccessibility()
                        }
                    },
                )
            }

            Spacer(Modifier.height(14.dp))
            Text("记录频率", color = TextStrong, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(Space.sm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                FrameInterval.entries.forEach { interval ->
                    val selected = interval == frameInterval
                    Text(
                        text = interval.label,
                        color = if (selected) Accent else TextStrong,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(ShapeChip)
                            .background(if (selected) AccentSoft else PageBackground)
                            .border(
                                Stroke.hairline,
                                if (selected) Accent.copy(alpha = 0.35f) else BorderDefault,
                                ShapeChip,
                            )
                            .clickable { onIntervalChange(interval) }
                            .padding(horizontal = Space.md, vertical = Space.sm),
                    )
                }
            }

            Text(
                text = "已保存画面：$frameCount",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = Space.sm),
            )

            Spacer(Modifier.height(14.dp))
            Text("黑名单", color = TextStrong, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                "名单内应用不会记录画面。可手动增删；文本极少或匹配游戏引擎特征时会自动加入。移除后不再自动加回。",
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = Space.xs, bottom = Space.sm),
            )
            if (frameBlacklist.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    frameBlacklist.forEach { item ->
                        BlacklistAppChip(
                            item = item,
                            iconLoader = iconLoader,
                            onRemove = { onRemoveBlacklist(item) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            AddBlacklistChip(onClick = onOpenBlacklistPicker)
        }

        Spacer(Modifier.height(Space.md))

        SettingsCard(title = "隐私 App") {
            Text(
                "名单内应用仍会统计使用时长与时间戳，但导出 Markdown 时不会附带其画面帧文本。",
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = Space.sm),
            )
            if (privacyApps.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    privacyApps.forEach { item ->
                        PrivacyAppChip(
                            item = item,
                            iconLoader = iconLoader,
                            onRemove = { onRemovePrivacyApp(item) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            AddBlacklistChip(onClick = onOpenPrivacyAppPicker)
        }

        Spacer(Modifier.height(Space.md))

        SettingsCard(title = "诊断") {
            Text(
                "查看本机同步与运行过程中的记录，便于排查问题。",
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeChip)
                    .background(PageBackground)
                    .border(Stroke.hairline, BorderDefault, ShapeChip)
                    .clickable(onClick = onOpenRuntimeLog)
                    .padding(horizontal = Space.md, vertical = Space.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("查看运行日志", color = TextStrong, fontSize = 14.sp)
                Text("›", color = TextMuted, fontSize = 18.sp)
            }
        }

        Spacer(Modifier.height(Space.md))

        SettingsCard(title = "用量数据修复") {
            Text(
                "清空本地使用记录，并重新从系统读取各应用使用时长。可用于修复跨午夜（0 点）缺失等问题。",
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onRebuildUsage,
                enabled = !usageRebuilding,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (usageRebuilding) "正在重建…" else "清空并重建用量")
            }
        }
    }

    if (frameBlacklistPickerOpen) {
        AddAppPickerSheet(
            categoryName = "画面记录黑名单",
            candidates = frameBlacklistCandidates,
            searchQuery = frameBlacklistSearch,
            onSearchChange = onBlacklistSearchChange,
            onDismiss = onDismissBlacklistPicker,
            onConfirm = onAddBlacklist,
            iconLoader = iconLoader,
        )
    }

    if (privacyAppPickerOpen) {
        AddAppPickerSheet(
            categoryName = "隐私 App（导出不含帧）",
            candidates = privacyAppCandidates,
            searchQuery = privacyAppSearch,
            onSearchChange = onPrivacyAppSearchChange,
            onDismiss = onDismissPrivacyAppPicker,
            onConfirm = onAddPrivacyApps,
            iconLoader = iconLoader,
        )
    }
}

@Composable
private fun BlacklistAppChip(
    item: FrameBlacklistItem,
    iconLoader: (String) -> Drawable?,
    onRemove: () -> Unit,
) {
    val sourceHint = when (item.source) {
        "auto" -> "自动"
        "builtin" -> "特征"
        else -> "手动"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceChip)
            .padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconImage(
            packageName = item.packageName,
            drawable = iconLoader(item.packageName),
            tint = Accent,
            size = 18.dp,
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(item.label, color = TextStrong, fontSize = 12.sp, maxLines = 1)
            Text(sourceHint, color = TextMuted, fontSize = 10.sp)
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "移除",
                tint = TextMuted,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun PrivacyAppChip(
    item: PrivacyAppEntity,
    iconLoader: (String) -> Drawable?,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceChip)
            .padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconImage(
            packageName = item.packageName,
            drawable = iconLoader(item.packageName),
            tint = Accent,
            size = 18.dp,
        )
        Spacer(Modifier.width(6.dp))
        Text(item.label, color = TextStrong, fontSize = 12.sp, maxLines = 1)
        IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "移除",
                tint = TextMuted,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun AddBlacklistChip(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("+ 添加应用", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCard)
            .background(CardBackground)
            .border(Stroke.hairline, BorderDefault, ShapeCard)
            .padding(Space.lg),
    ) {
        Text(title, color = TextStrong, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}
