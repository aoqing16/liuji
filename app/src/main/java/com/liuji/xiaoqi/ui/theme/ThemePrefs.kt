package com.liuji.xiaoqi.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * 主题偏好的同步缓存：启动闪屏 / 窗口背景要在 Room 异步读库之前就能选对 night 资源。
 */
object ThemePrefs {
    private const val PREFS = "liuji_theme"
    private const val KEY_MODE = "theme_mode"

    fun read(context: Context): ThemeMode {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, ThemeMode.SYSTEM.storageValue)
        return ThemeMode.fromStorage(raw)
    }

    fun write(context: Context, mode: ThemeMode) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.storageValue)
            .apply()
    }

    fun applyNightMode(context: Context, mode: ThemeMode = read(context)) {
        val nightMode = when (mode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }
}
