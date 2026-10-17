package com.liuji.xiaoqi.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liuji.xiaoqi.log.RuntimeLogExporter
import com.liuji.xiaoqi.log.RuntimeLogStore
import com.liuji.xiaoqi.ui.theme.BorderDefault
import com.liuji.xiaoqi.ui.theme.CardBackground
import com.liuji.xiaoqi.ui.theme.PageBackground
import com.liuji.xiaoqi.ui.theme.ShapeCard
import com.liuji.xiaoqi.ui.theme.Space
import com.liuji.xiaoqi.ui.theme.Stroke
import com.liuji.xiaoqi.ui.theme.TextMuted
import com.liuji.xiaoqi.ui.theme.TextStrong

@Composable
fun RuntimeLogScreen(
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val logText by RuntimeLogStore.text.collectAsStateWithLifecycle()
    val lines = remember(logText) {
        if (logText.isBlank()) emptyList() else logText.lineSequence().toList()
    }
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.scrollToItem(lines.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .statusBarsPadding()
            .padding(horizontal = Space.lg, vertical = Space.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "运行日志",
                color = TextStrong,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { copyLog(context, logText) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = TextStrong,
                    )
                    Spacer(Modifier.padding(horizontal = 2.dp))
                    Text("复制", color = TextStrong)
                }
            }
            TextButton(onClick = { exportLog(context) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = null,
                        tint = TextStrong,
                    )
                    Spacer(Modifier.padding(horizontal = 2.dp))
                    Text("导出", color = TextStrong)
                }
            }
        }

        Spacer(Modifier.height(Space.sm))
        Text(
            text = "记录本机同步与主要运行信息，便于排查问题。",
            color = TextMuted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(Space.md))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(ShapeCard)
                .background(CardBackground)
                .border(Stroke.hairline, BorderDefault, ShapeCard)
                .padding(Space.md),
        ) {
            if (lines.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Space.xxl),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "暂无运行日志",
                        color = TextStrong,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        "使用 AI对话同步或产生错误后，相关记录会显示在这里。",
                        color = TextMuted,
                        fontSize = 13.sp,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = lines,
                        key = { index, line -> "$index:${line.hashCode()}" },
                    ) { _, line ->
                        Text(
                            text = line,
                            color = TextStrong,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private fun copyLog(context: Context, text: String) {
    if (text.isBlank()) {
        Toast.makeText(context, "暂无日志可复制", Toast.LENGTH_SHORT).show()
        return
    }
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("留迹运行日志", text))
    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
}

private fun exportLog(context: Context) {
    val result = RuntimeLogExporter.writeAndGetShareUri(context) ?: run {
        Toast.makeText(context, "暂无日志可导出", Toast.LENGTH_SHORT).show()
        return
    }
    val share = RuntimeLogExporter.shareIntent(context, result)
    context.startActivity(
        Intent.createChooser(share, "导出运行日志").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
    )
}
