package com.liuji.xiaoqi.data

import android.content.Context
import com.liuji.xiaoqi.log.AppLog
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

object AppCategorizer {

    private val exactRulesRef = AtomicReference<Map<String, String>?>(null)

    fun ensureRulesLoaded(context: Context) {
        if (exactRulesRef.get() != null) return
        synchronized(this) {
            if (exactRulesRef.get() != null) return
            exactRulesRef.set(loadExactRules(context.applicationContext))
        }
    }

    fun categoryIdFor(packageName: String): String {
        val pkg = packageName.lowercase()
        exactRulesRef.get()?.get(pkg)?.let { return it }
        return matchHeuristics(pkg)
    }

    private fun loadExactRules(context: Context): Map<String, String> {
        return runCatching {
            context.assets.open("builtin_app_categories.json").use { stream ->
                val body = stream.bufferedReader().readText()
                val root = JSONObject(body)
                val rules = root.getJSONArray("rules")
                val map = HashMap<String, String>()
                for (i in 0 until rules.length()) {
                    val rule = rules.getJSONObject(i)
                    val category = rule.getString("category")
                    val names = rule.getJSONArray("packageNames")
                    for (j in 0 until names.length()) {
                        map[names.getString(j).lowercase()] = category
                    }
                }
                map
            }
        }.getOrElse { e ->
            AppLog.w("AppCategorizer", "builtin_app_categories.json 加载失败", e)
            emptyMap()
        }
    }

    private fun matchHeuristics(pkg: String): String = when {
        matches(
            pkg,
            "tencent.mm", "tencent.mobileqq", "ss.android.ugc.aweme", "sina.weibo",
            "zhihu.android", "xingin.xhs", "smile.gifmaker", "kuaishou",
            "instagram", "whatsapp", "telegram", "twitter", "facebook",
            "snapchat", "discord", "line.android", "kakao.talk",
        ) -> Categories.Social.id

        matches(
            pkg,
            "android.chrome", "browser", "uc.browser", "qihoo.browser",
            "heytap.browser", "huawei.browser", "samsung.android.app.sbrowser",
            "mozilla.firefox", "opera.", "edge", "baidu.browser",
        ) -> Categories.Browse.id

        matches(
            pkg,
            "video", "bilibili", "iqiyi", "youku", "qiyi", "tencent.qqlive",
            "tencent.weishi", "netflix", "youtube", "spotify", "music",
            "kugou", "netease.cloudmusic", "ximalaya", "tiktok",
            "ss.android.article", "dragon.read",
        ) -> Categories.Video.id

        matches(
            pkg,
            "game", "games", "miHoYo", "hypergryph", "netease.party",
            "tencent.tmgp", "garena", "unity", "roblox", "minecraft",
            "pubg", "sgame", "lol.",
        ) || pkg.contains(".game") || pkg.endsWith("game") -> Categories.Game.id

        matches(
            pkg,
            "office", "docs.docs", "microsoft.office", "wps", "excel",
            "powerpoint", "word", "notion", "evernote", "yuque",
            "feishu", "larksuite", "dingtalk", "tencent.docs",
            "kingsoft", "adobe.reader", "pdf",
        ) -> Categories.Work.id

        matches(
            pkg,
            "tencent.wework", "alibaba.android.rimet", "ss.android.lark",
            "slack", "teams", "zoom", "meeting", "mail", "gmail",
            "outlook", "qqmail", "netease.mail",
        ) -> Categories.Comm.id

        matches(
            pkg,
            "taobao", "tmall", "jd.mall", "jingdong", "pinduoduo",
            "suning", "xianyu", "meituan", "ele.me", "amazon",
            "ebay", "shop", "aliexpress",
        ) -> Categories.Shop.id

        matches(
            pkg,
            "settings", "android.systemui", "launcher", "home",
            "packageinstaller", "permissioncontroller", "vending",
            "gms", "gsf", "xiaomi.finddevice",
        ) || pkg.startsWith("com.android.") || pkg.startsWith("com.google.android.ext")
            -> Categories.System.id

        matches(
            pkg,
            "maps", "amap", "baidu.BaiduMap", "weather", "calculator",
            "clock", "calendar", "filemanager", "files", "gallery",
            "photos", "camera", "translator", "inputmethod", "keyboard",
            "vpn", "cleaner", "security", "download",
        ) -> Categories.Tool.id

        else -> Categories.Other.id
    }

    private fun matches(pkg: String, vararg needles: String): Boolean =
        needles.any { pkg.contains(it.lowercase()) }
}
