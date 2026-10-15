package com.liuji.xiaoqi.hindsight

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class HindsightCredStore(context: Context) {
    private val app = context.applicationContext

    private val prefs by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "hindsight_webdav_secure",
            masterKeyAlias,
            app,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): WebDavAccount = WebDavAccount(
        url = prefs.getString(KEY_URL, WebDavAccount.DEFAULT_URL) ?: WebDavAccount.DEFAULT_URL,
        user = prefs.getString(KEY_USER, "") ?: "",
        password = prefs.getString(KEY_PASSWORD, "") ?: "",
        rootPath = prefs.getString(KEY_ROOT, WebDavAccount.DEFAULT_ROOT)
            ?: WebDavAccount.DEFAULT_ROOT,
    )

    fun save(account: WebDavAccount) {
        prefs.edit {
            putString(KEY_URL, account.url.trim())
            putString(KEY_USER, account.user.trim())
            putString(KEY_PASSWORD, account.password)
            putString(
                KEY_ROOT,
                account.rootPath.trim().trim('/').ifBlank { WebDavAccount.DEFAULT_ROOT },
            )
        }
    }

    companion object {
        private const val KEY_URL = "url"
        private const val KEY_USER = "user"
        private const val KEY_PASSWORD = "password"
        private const val KEY_ROOT = "root"
    }
}
