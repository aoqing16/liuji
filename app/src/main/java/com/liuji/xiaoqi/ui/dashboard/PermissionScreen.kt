package com.liuji.xiaoqi.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuji.xiaoqi.ui.theme.Accent
import com.liuji.xiaoqi.ui.theme.AccentSoft
import com.liuji.xiaoqi.ui.theme.CardBackground
import com.liuji.xiaoqi.ui.theme.PageBackground
import com.liuji.xiaoqi.ui.theme.ShapeCard
import com.liuji.xiaoqi.ui.theme.ShapeIconWell
import com.liuji.xiaoqi.ui.theme.Space
import com.liuji.xiaoqi.ui.theme.TextMuted
import com.liuji.xiaoqi.ui.theme.TextStrong

@Composable
fun PermissionScreen(
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onOpenAppSettings: () -> Unit = {},
    onUseCloudBoard: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .padding(Space.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(ShapeIconWell)
                .background(AccentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.QueryStats,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(Space.xl))
        Text(
            text = "需要「使用情况访问」权限",
            color = TextStrong,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "留迹会读取各应用的前台使用时长，用于日 / 周 / 月看板。数据只保存在本机。",
            color = TextMuted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier
                .clip(ShapeCard)
                .background(CardBackground)
                .padding(Space.lg),
        )
        Spacer(Modifier.height(Space.xxl))
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("去开启权限")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("我已开启，刷新")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onUseCloudBoard,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("改看电脑云端看板")
        }
        Spacer(Modifier.height(Space.sm))
        OutlinedButton(
            onClick = onOpenAppSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("配置云盘账号")
        }
    }
}
