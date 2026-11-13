package com.liuji.xiaoqi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.liuji.xiaoqi.ui.theme.CardBackground

@Composable
fun PhoneHomeScaffold(
    bottomTab: PhoneBottomTab,
    onBottomTab: (PhoneBottomTab) -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = com.liuji.xiaoqi.ui.theme.PageBackground,
        bottomBar = {
            NavigationBar(containerColor = CardBackground) {
                NavigationBarItem(
                    selected = bottomTab == PhoneBottomTab.DASHBOARD,
                    onClick = { onBottomTab(PhoneBottomTab.DASHBOARD) },
                    icon = { Icon(Icons.Outlined.Dashboard, contentDescription = null) },
                    label = { Text("看板") },
                )
                NavigationBarItem(
                    selected = bottomTab == PhoneBottomTab.FRAMES,
                    onClick = { onBottomTab(PhoneBottomTab.FRAMES) },
                    icon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                    label = { Text("画面记录") },
                )
                NavigationBarItem(
                    selected = bottomTab == PhoneBottomTab.CATEGORIES,
                    onClick = { onBottomTab(PhoneBottomTab.CATEGORIES) },
                    icon = { Icon(Icons.Outlined.Category, contentDescription = null) },
                    label = { Text("分类管理") },
                )
                NavigationBarItem(
                    selected = bottomTab == PhoneBottomTab.CLOUD_SYNC,
                    onClick = { onBottomTab(PhoneBottomTab.CLOUD_SYNC) },
                    icon = { Icon(Icons.Outlined.Chat, contentDescription = null) },
                    label = { Text("AI对话") },
                )
                NavigationBarItem(
                    selected = bottomTab == PhoneBottomTab.SETTINGS,
                    onClick = { onBottomTab(PhoneBottomTab.SETTINGS) },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text("设置") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            content()
        }
    }
}

enum class PhoneBottomTab {
    DASHBOARD,
    FRAMES,
    CATEGORIES,
    CLOUD_SYNC,
    SETTINGS,
}
