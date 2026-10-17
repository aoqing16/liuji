package com.liuji.xiaoqi.ima

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONObject

data class ImaDaySyncState(
    /** 知识库文件 media_id（文件上传模式） */
    val mediaId: String = "",
    /** 文件上传成功时间 */
    val fileSyncedAt: Long = 0L,
    /** 兼容旧笔记同步字段（只读迁移） */
    val noteId: String = "",
    val lastFrameId: Long = 0L,
    val usageSyncedAt: Long = 0L,
) {
    val isFileSynced: Boolean get() = mediaId.isNotBlank() && fileSyncedAt > 0L
}

data class ImaSyncConfig(
    val clientId: String = "",
    val apiKey: String = "",
    val knowledgeBaseName: String = "",
    val knowledgeBaseId: String = "",
    val autoEnabled: Boolean = false,
    val lastSuccessAt: Long = 0L,
    val lastErrorAt: Long = 0L,
    val lastError: String = "",
    val lastMessage: String = "",
)

class ImaSyncPrefs(context: Context) {
    private val app = context.applicationContext

    private val prefs by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "ima_sync_secure",
            masterKeyAlias,
            app,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun loadConfig(): ImaSyncConfig = ImaSyncConfig(
        clientId = prefs.getString(KEY_CLIENT_ID, "").orEmpty(),
        apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
        knowledgeBaseName = prefs.getString(KEY_KB_NAME, "").orEmpty(),
        knowledgeBaseId = prefs.getString(KEY_KB_ID, "").orEmpty(),
        autoEnabled = prefs.getBoolean(KEY_AUTO, false),
        lastSuccessAt = prefs.getLong(KEY_LAST_OK, 0L),
        lastErrorAt = prefs.getLong(KEY_LAST_ERR_AT, 0L),
        lastError = prefs.getString(KEY_LAST_ERR, "").orEmpty(),
        lastMessage = prefs.getString(KEY_LAST_MSG, "").orEmpty(),
    )

    fun saveCredentials(
        clientId: String,
        apiKey: String,
        knowledgeBaseName: String,
    ) {
        prefs.edit {
            putString(KEY_CLIENT_ID, clientId.trim())
            putString(KEY_API_KEY, apiKey)
            putString(KEY_KB_NAME, knowledgeBaseName.trim())
        }
    }

    fun saveAuto(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO, enabled) }
    }

    fun setKnowledgeBaseId(id: String) {
        prefs.edit { putString(KEY_KB_ID, id) }
    }

    fun setLastSuccess(message: String) {
        prefs.edit {
            putLong(KEY_LAST_OK, System.currentTimeMillis())
            putString(KEY_LAST_MSG, message)
            putString(KEY_LAST_ERR, "")
        }
    }

    fun setLastError(error: String) {
        prefs.edit {
            putLong(KEY_LAST_ERR_AT, System.currentTimeMillis())
            putString(KEY_LAST_ERR, error.take(500))
            putString(KEY_LAST_MSG, "")
        }
    }

    fun isConfigured(): Boolean {
        val c = loadConfig()
        return c.clientId.isNotBlank() && c.apiKey.isNotBlank() && c.knowledgeBaseName.isNotBlank()
    }

    fun getDayState(date: String): ImaDaySyncState? {
        val raw = prefs.getString(KEY_DAYS, "{}").orEmpty()
        val root = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        if (!root.has(date)) return null
        val o = root.getJSONObject(date)
        val mediaId = o.optString("mediaId")
        val noteId = o.optString("noteId")
        if (mediaId.isBlank() && noteId.isBlank()) return null
        return ImaDaySyncState(
            mediaId = mediaId,
            fileSyncedAt = o.optLong("fileSyncedAt", 0L),
            noteId = noteId,
            lastFrameId = o.optLong("lastFrameId", 0L),
            usageSyncedAt = o.optLong("usageSyncedAt", 0L),
        )
    }

    fun putDayState(date: String, state: ImaDaySyncState) {
        val raw = prefs.getString(KEY_DAYS, "{}").orEmpty()
        val root = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        root.put(
            date,
            JSONObject()
                .put("mediaId", state.mediaId)
                .put("fileSyncedAt", state.fileSyncedAt)
                .put("noteId", state.noteId)
                .put("lastFrameId", state.lastFrameId)
                .put("usageSyncedAt", state.usageSyncedAt),
        )
        prefs.edit { putString(KEY_DAYS, root.toString()) }
    }

    fun clearDayState(date: String) {
        val raw = prefs.getString(KEY_DAYS, "{}").orEmpty()
        val root = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        root.remove(date)
        prefs.edit { putString(KEY_DAYS, root.toString()) }
    }

    fun clearAllDayStates() {
        prefs.edit { putString(KEY_DAYS, "{}") }
    }

    /** 调试：污染日状态 */
    fun poisonDayNoteIds(): Int {
        val raw = prefs.getString(KEY_DAYS, "{}").orEmpty()
        val root = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        val dates = root.keys().asSequence().toList()
        for (date in dates) {
            val o = root.optJSONObject(date) ?: continue
            o.put("mediaId", "deleted_or_missing_$date")
            o.put("fileSyncedAt", 0L)
            o.put("noteId", "deleted_or_missing_$date")
            root.put(date, o)
        }
        prefs.edit { putString(KEY_DAYS, root.toString()) }
        return dates.size
    }

    companion object {
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_KB_NAME = "kb_name"
        private const val KEY_KB_ID = "kb_id"
        private const val KEY_AUTO = "auto_enabled"
        private const val KEY_LAST_OK = "last_ok_at"
        private const val KEY_LAST_ERR_AT = "last_err_at"
        private const val KEY_LAST_ERR = "last_err"
        private const val KEY_LAST_MSG = "last_msg"
        private const val KEY_DAYS = "day_states"
    }
}
