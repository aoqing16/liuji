package com.liuji.xiaoqi.ui.category

import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuji.xiaoqi.data.AppUsageItem
import com.liuji.xiaoqi.ui.components.AppUsageGrid
import com.liuji.xiaoqi.ui.theme.CardBackground
import com.liuji.xiaoqi.ui.theme.TextMuted
import com.liuji.xiaoqi.ui.theme.TextStrong
import com.liuji.xiaoqi.ui.util.formatDuration

@Composable
fun CategoryUsageDetailScreen(
    categoryName: String,
    categoryColor: Color,
    totalMillis: Long,
    apps: List<AppUsageItem>,
    iconLoader: (String) -> Drawable?,
    onBack: () -> Unit,
    onAppClick: ((AppUsageItem) -> Unit)? = null,
) {
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            // 外层 PhoneHomeScaffold 已铺底色
            .statusBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = categoryName,
                color = TextStrong,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(categoryColor),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "合计 ${formatDuration(totalMillis)} · ${apps.size} 个应用",
                    color = TextMuted,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.size(16.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardBackground)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                AppUsageGrid(
                    apps = apps,
                    iconLoader = iconLoader,
                    defaultLimit = 50,
                    onAppClick = onAppClick,
                )
            }
        }
    }
}
