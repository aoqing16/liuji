package com.liuji.xiaoqi.ui.sync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.liuji.xiaoqi.ima.ImaApiClient
import com.liuji.xiaoqi.ima.ImaSyncInterval
import com.liuji.xiaoqi.ima.ImaSyncPrefs
import com.liuji.xiaoqi.ima.ImaSyncRepository
import com.liuji.xiaoqi.ima.ImaSyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CloudSyncUiState(
    val clientId: String = "",
    val apiKey: String = "",
    val knowledgeBaseName: String = "",
    val autoEnabled: Boolean = false,
    val interval: ImaSyncInterval = ImaSyncInterval.H1,
    val showApiKey: Boolean = false,
    val saving: Boolean = false,
    val syncing: Boolean = false,
    val testing: Boolean = false,
    val statusLine: String = "",
    val lastSuccessText: String = "",
    val lastErrorText: String = "",
    val snackMessage: String? = null,
)

class CloudSyncViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = ImaSyncPrefs(app)
    private val repo = ImaSyncRepository(app)

    private val _state = MutableStateFlow(CloudSyncUiState())
    val state: StateFlow<CloudSyncUiState> = _state.asStateFlow()

    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    init {
        reload()
        ImaSyncScheduler.reconcile(app)
    }

    fun reload() {
        val c = prefs.loadConfig()
        _state.update {
            it.copy(
                clientId = c.clientId,
                apiKey = c.apiKey,
                knowledgeBaseName = c.knowledgeBaseName,
                autoEnabled = c.autoEnabled,
                interval = c.interval,
                lastSuccessText = formatStamp(c.lastSuccessAt, c.lastMessage),
                lastErrorText = formatStamp(c.lastErrorAt, c.lastError),
                statusLine = when {
                    c.lastError.isNotBlank() && c.lastErrorAt >= c.lastSuccessAt ->
                        "最近失败：${c.lastError}"
                    c.lastMessage.isNotBlank() -> "最近成功：${c.lastMessage}"
                    else -> if (prefs.isConfigured()) "已连接，等待同步" else "尚未设置"
                },
            )
        }
    }

    fun setClientId(v: String) = _state.update { it.copy(clientId = v) }
    fun setApiKey(v: String) = _state.update { it.copy(apiKey = v) }
    fun setKbName(v: String) = _state.update { it.copy(knowledgeBaseName = v) }
    fun setShowApiKey(show: Boolean) = _state.update { it.copy(showApiKey = show) }
    fun setAutoEnabled(v: Boolean) = _state.update { it.copy(autoEnabled = v) }
    fun setInterval(v: ImaSyncInterval) = _state.update { it.copy(interval = v) }
    fun consumeMessage() = _state.update { it.copy(snackMessage = null) }

    fun saveConfig() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            withContext(Dispatchers.IO) {
                val oldName = prefs.loadConfig().knowledgeBaseName
                prefs.saveCredentials(s.clientId, s.apiKey, s.knowledgeBaseName)
                prefs.saveAuto(s.autoEnabled, s.interval)
                if (oldName != s.knowledgeBaseName.trim()) {
                    prefs.setKnowledgeBaseId("")
                }
                ImaSyncScheduler.reconcile(getApplication())
            }
            _state.update {
                it.copy(saving = false, snackMessage = "已保存", statusLine = "设置已保存")
            }
            reload()
        }
    }

    fun testConnection() {
        val s = _state.value
        if (s.clientId.isBlank() || s.apiKey.isBlank()) {
            _state.update { it.copy(snackMessage = "请先填写应用 ID 与访问密钥") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(testing = true) }
            val result = withContext(Dispatchers.IO) {
                val client = ImaApiClient(s.clientId.trim(), s.apiKey)
                if (s.knowledgeBaseName.isNotBlank()) {
                    val (res, id) = client.resolveKnowledgeBaseId(s.knowledgeBaseName.trim())
                    if (res.ok && id != null) {
                        prefs.setKnowledgeBaseId(id)
                        "连接成功，已找到知识库"
                    } else {
                        res.error ?: res.errmsg ?: "连接失败"
                    }
                } else {
                    val (res, list) = client.listAddableKnowledgeBases()
                    if (res.ok) "连接成功，可用知识库 ${list.size} 个" else (res.error ?: "连接失败")
                }
            }
            _state.update {
                it.copy(
                    testing = false,
                    snackMessage = result,
                    statusLine = result,
                )
            }
            reload()
        }
    }

    fun syncNow() {
        if (!prefs.isConfigured() &&
            (_state.value.clientId.isBlank() ||
                _state.value.apiKey.isBlank() ||
                _state.value.knowledgeBaseName.isBlank())
        ) {
            _state.update { it.copy(snackMessage = "请先填写并保存设置") }
            return
        }
        viewModelScope.launch {
            // 先落盘当前表单，避免未保存就同步
            val s = _state.value
            withContext(Dispatchers.IO) {
                prefs.saveCredentials(s.clientId, s.apiKey, s.knowledgeBaseName)
                prefs.saveAuto(s.autoEnabled, s.interval)
            }
            _state.update { it.copy(syncing = true, statusLine = "同步中…") }
            val report = withContext(Dispatchers.IO) { repo.syncIncremental() }
            _state.update {
                it.copy(
                    syncing = false,
                    snackMessage = report.message,
                    statusLine = report.message,
                )
            }
            reload()
        }
    }

    private fun formatStamp(at: Long, text: String): String {
        if (at <= 0L && text.isBlank()) return ""
        val t = if (at > 0L) timeFmt.format(Date(at)) else ""
        return listOf(t, text).filter { it.isNotBlank() }.joinToString(" · ")
    }
}
