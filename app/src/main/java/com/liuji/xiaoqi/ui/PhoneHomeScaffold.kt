package com.liuji.xiaoqi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.liuji.xiaoqi.data.StatsPeriod
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
                    selected = bottomTab == PhoneBottomTab.DAY,
                    onClick = { onBottomTab(PhoneBottomTab.DAY) },
                    icon = { Icon(Icons.Outlined.Today, contentDescription = null) },
                    label = { Text("日") },
                )
                NavigationBarItem(
                    selected = bottomTab == PhoneBottomTab.WEEK,
                    onClick = { onBottomTab(PhoneBottomTab.WEEK) },
                    icon = { Icon(Icons.Outlined.DateRange, contentDescription = null) },
                    label = { Text("周") },
                )
                NavigationBarItem(
                    selected = bottomTab == PhoneBottomTab.MONTH,
                    onClick = { onBottomTab(PhoneBottomTab.MONTH) },
                    icon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                    label = { Text("月") },
                )
                NavigationBarItem(
                    selected = bottomTab == PhoneBottomTab.CATEGORIES,
                    onClick = { onBottomTab(PhoneBottomTab.CATEGORIES) },
                    icon = { Icon(Icons.Outlined.Category, contentDescription = null) },
                    label = { Text("分类") },
                )
                NavigationBarItem(
                    selected = bottomTab == PhoneBottomTab.CLOUD_SYNC,
                    onClick = { onBottomTab(PhoneBottomTab.CLOUD_SYNC) },
                    icon = { Icon(Icons.Outlined.CloudSync, contentDescription = null) },
                    label = { Text("云同步") },
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
    DAY,
    WEEK,
    MONTH,
    CATEGORIES,
    CLOUD_SYNC,
    ;

    fun toStatsPeriod(): StatsPeriod? = when (this) {
        DAY -> StatsPeriod.DAY
        WEEK -> StatsPeriod.WEEK
        MONTH -> StatsPeriod.MONTH
        CATEGORIES, CLOUD_SYNC -> null
    }

    companion object {
        fun fromPeriod(period: StatsPeriod): PhoneBottomTab = when (period) {
            StatsPeriod.DAY -> DAY
            StatsPeriod.WEEK -> WEEK
            StatsPeriod.MONTH -> MONTH
        }
    }
}
