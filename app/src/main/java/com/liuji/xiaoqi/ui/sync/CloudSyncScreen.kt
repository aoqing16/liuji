package com.liuji.xiaoqi.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liuji.xiaoqi.ui.theme.Space
import com.liuji.xiaoqi.ui.theme.Stroke
import com.liuji.xiaoqi.ui.theme.TextMuted
import com.liuji.xiaoqi.ui.theme.TextStrong

@Composable
fun CloudSyncScreen(
    viewModel: CloudSyncViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackHost = remember { SnackbarHostState() }

    LaunchedEffect(state.snackMessage) {
        val msg = state.snackMessage ?: return@LaunchedEffect
        snackHost.showSnackbar(msg)
        viewModel.consumeMessage()
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Chat, contentDescription = null)
                Spacer(Modifier.padding(Space.xs))
                Text("AI对话", style = MaterialTheme.typography.titleLarge, color = TextStrong)
            }
            Text(
                "把已统计完成日期的使用数据上传到腾讯 ima 知识库，便于在 ima 里与 AI 对话分析习惯、查找内容。" +
                    "填写知识库名称：已存在则写入，不存在则自动创建。" +
                    "今日进行中的数据不会上传；开启自动同步后，在自然日结束后上传已完成日（约凌晨执行，打开 App 也会补传）。",
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = state.clientId,
                onValueChange = viewModel::setClientId,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("应用 ID") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::setApiKey,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("访问密钥") },
                singleLine = true,
                visualTransformation = if (state.showApiKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { viewModel.setShowApiKey(!state.showApiKey) }) {
                        Icon(
                            if (state.showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = "显示或隐藏密钥",
                        )
                    }
                },
            )
            OutlinedTextField(
                value = state.knowledgeBaseName,
                onValueChange = viewModel::setKbName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("知识库名称") },
                singleLine = true,
                supportingText = { Text("已存在则写入；不存在则自动创建") },
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("自动同步", color = TextStrong)
                    Text(
                        "自然日结束后上传已完成日（约凌晨执行；打开 App 也会补传）",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = state.autoEnabled,
                    onCheckedChange = viewModel::setAutoEnabled,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                OutlinedButton(
                    onClick = viewModel::saveConfig,
                    enabled = !state.saving && !state.syncing,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.height(Space.xl), strokeWidth = Stroke.indicator)
                    } else {
                        Text("保存")
                    }
                }
                OutlinedButton(
                    onClick = viewModel::testConnection,
                    enabled = !state.testing && !state.syncing,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.testing) {
                        CircularProgressIndicator(Modifier.height(Space.xl), strokeWidth = Stroke.indicator)
                    } else {
                        Text("测试连接")
                    }
                }
            }

            Button(
                onClick = viewModel::syncNow,
                enabled = !state.syncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.syncing) {
                    CircularProgressIndicator(
                        Modifier.height(Space.xl),
                        strokeWidth = Stroke.indicator,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.padding(Space.sm))
                    Text("同步中…")
                } else {
                    Text("立即同步")
                }
            }

            Spacer(Modifier.height(Space.sm))
            Text("同步状态", style = MaterialTheme.typography.titleSmall, color = TextStrong)
            Text(state.statusLine, color = TextMuted)
            if (state.lastSuccessText.isNotBlank()) {
                Text(
                    "上次成功：${state.lastSuccessText}",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.lastErrorText.isNotBlank()) {
                Text(
                    "上次失败：${state.lastErrorText}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        SnackbarHost(hostState = snackHost)
    }
}
