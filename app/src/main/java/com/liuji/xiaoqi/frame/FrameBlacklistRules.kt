package com.liuji.xiaoqi.frame

import android.content.Context
import com.liuji.xiaoqi.log.AppLog
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

object FrameBlacklistRules {

    private data class Rules(
        val exact: Set<String>,
        val prefixes: List<String>,
        val activityTokens: List<String>,
    )

    private val rulesRef = AtomicReference<Rules?>(null)

    fun ensureLoaded(context: Context) {
        if (rulesRef.get() != null) return
        synchronized(this) {
            if (rulesRef.get() != null) return
            rulesRef.set(load(context.applicationContext))
        }
    }

    fun matches(packageName: String, activityName: String): Boolean {
        val rules = rulesRef.get() ?: return false
        val pkg = packageName.lowercase()
        if (pkg in rules.exact) return true
        if (rules.prefixes.any { pkg.startsWith(it) }) return true
        val activity = activityName.lowercase()
        if (activity.isBlank()) return false
        return rules.activityTokens.any { token ->
            activity.contains(token)
        }
    }

    private fun load(context: Context): Rules {
        return runCatching {
            context.assets.open("frame_blacklist_rules.json").use { stream ->
                val root = JSONObject(stream.bufferedReader().readText())
                val exact = HashSet<String>()
                val exactArr = root.optJSONArray("packageExact")
                if (exactArr != null) {
                    for (i in 0 until exactArr.length()) {
                        exact += exactArr.getString(i).lowercase()
                    }
                }
                val prefixes = ArrayList<String>()
                val prefixArr = root.optJSONArray("packagePrefixes")
                if (prefixArr != null) {
                    for (i in 0 until prefixArr.length()) {
                        prefixes += prefixArr.getString(i).lowercase()
                    }
                }
                val tokens = ArrayList<String>()
                val tokenArr = root.optJSONArray("activityTokens")
                if (tokenArr != null) {
                    for (i in 0 until tokenArr.length()) {
                        tokens += tokenArr.getString(i).lowercase()
                    }
                }
                Rules(exact, prefixes, tokens)
            }
        }.getOrElse { e ->
            AppLog.w("FrameBlacklistRules", "frame_blacklist_rules.json 加载失败", e)
            Rules(emptySet(), emptyList(), emptyList())
        }
    }
}
