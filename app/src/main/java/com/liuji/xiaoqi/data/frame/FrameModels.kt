package com.liuji.xiaoqi.data.frame

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.LinkedHashSet

enum class FrameInterval(val millis: Long, val label: String) {
    /** 用户操作且内容指纹变化时采集（非定时） */
    ON_ACTION(0L, "有操作时"),
    S5(5_000L, "5 秒"),
    S10(10_000L, "10 秒"),
    S30(30_000L, "30 秒"),
    M1(60_000L, "1 分钟"),
    M5(300_000L, "5 分钟"),
    ;

    /** 是否按固定间隔轮询 */
    val isTimed: Boolean get() = millis > 0L

    companion object {
        fun fromMillis(ms: Long): FrameInterval =
            entries.firstOrNull { it.millis == ms } ?: S30
    }
}

data class FrameTextNode(
    val text: String,
    val desc: String = "",
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val depth: Int,
    /** 采集时节点包名；入库按帧级 package_name，json 可不写 */
    val packageName: String = "",
    /** 仅 ChatGPT 写入，用于区分用户输入与 AI 回答 */
    val className: String = "",
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)

    fun hasText(): Boolean = text.isNotBlank()
    fun hasDesc(): Boolean = desc.isNotBlank()
}

data class UiFrameSnapshot(
    val id: Long = 0,
    val capturedAt: Long,
    val packageName: String,
    val activityName: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val texts: List<FrameTextNode>,
    /** 仅来自小窗/悬浮窗，不参与特征库与稀疏自动拉黑 */
    val overlayOnly: Boolean = false,
) {
    val hasAnyText: Boolean get() = texts.any { it.hasText() }
}

/** 帧视图搜索命中（一帧多节点只计一条） */
data class FrameSearchHit(
    val frameId: Long,
    val packageName: String,
    val appLabel: String,
    val capturedAt: Long,
    val snippet: String,
)

object FrameJson {
    /** 闲鱼仍导出 desc；其他应用只导出可见 text */
    const val XIANYU_PACKAGE = "com.taobao.idlefish"
    const val CHATGPT_PACKAGE = "com.openai.chatgpt"
    const val CHATGPT_USER_CLASS = "android.view.View"
    const val CHATGPT_AI_CLASS = "android.widget.TextView"

    fun includeDescForPackage(packageName: String): Boolean =
        packageName == XIANYU_PACKAGE

    fun textsToJson(texts: List<FrameTextNode>): String {
        val arr = JSONArray()
        texts.forEach { n ->
            val o = JSONObject()
                .put("text", n.text)
                .put("desc", n.desc)
                .put("left", n.left)
                .put("top", n.top)
                .put("right", n.right)
                .put("bottom", n.bottom)
                .put("depth", n.depth)
            if (n.className.isNotBlank()) o.put("className", n.className)
            arr.put(o)
        }
        return arr.toString()
    }

    fun textsFromJson(json: String): List<FrameTextNode> {
        if (json.isBlank()) return emptyList()
        val arr = JSONArray(json)
        val out = ArrayList<FrameTextNode>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += FrameTextNode(
                text = o.optString("text"),
                desc = o.optString("desc"),
                left = o.optInt("left"),
                top = o.optInt("top"),
                right = o.optInt("right"),
                bottom = o.optInt("bottom"),
                depth = o.optInt("depth"),
                className = o.optString("className"),
            )
        }
        return out
    }

    /**
     * 导出用：只扫出 text / desc 字符串，不建节点、不读坐标。
     * 文案条数与 [textsFromJson] 一致（trim 后非空）。
     */
    fun collectExportStrings(json: String, includeDesc: Boolean, into: MutableSet<String>) {
        if (json.length < 2) return
        val n = json.length
        var i = 0
        while (i < n) {
            if (json[i] != '"') {
                i++
                continue
            }
            val keyStart = i + 1
            val keyEnd = endOfJsonString(json, i)
            if (keyEnd < 0) return
            var j = keyEnd + 1
            while (j < n && json[j].isWhitespace()) j++
            if (j >= n || json[j] != ':') {
                i = keyEnd + 1
                continue
            }
            j++
            while (j < n && json[j].isWhitespace()) j++
            val want = regionEquals(json, keyStart, keyEnd, "text") ||
                (includeDesc && regionEquals(json, keyStart, keyEnd, "desc"))
            if (j < n && json[j] == '"') {
                if (want) {
                    val read = readJsonString(json, j) ?: return
                    val trimmed = read.first.trim()
                    if (trimmed.isNotEmpty()) into.add(trimmed)
                    i = read.second
                } else {
                    val end = endOfJsonString(json, j)
                    if (end < 0) return
                    i = end + 1
                }
            } else {
                i = j
            }
        }
    }

    private fun regionEquals(json: String, start: Int, endExclusive: Int, key: String): Boolean {
        if (endExclusive - start != key.length) return false
        for (k in key.indices) {
            if (json[start + k] != key[k]) return false
        }
        return true
    }

    /** @return 结束引号下标；[openQuote] 指向起始 `"` */
    private fun endOfJsonString(json: String, openQuote: Int): Int {
        var i = openQuote + 1
        val n = json.length
        while (i < n) {
            when (json[i]) {
                '\\' -> i += 2
                '"' -> return i
                else -> i++
            }
        }
        return -1
    }

    /** @return 解码后的字符串，以及结束引号之后的下标 */
    private fun readJsonString(json: String, openQuote: Int): Pair<String, Int>? {
        val sb = StringBuilder()
        var i = openQuote + 1
        val n = json.length
        while (i < n) {
            val c = json[i]
            when {
                c == '\\' -> {
                    if (i + 1 >= n) return null
                    when (val e = json[i + 1]) {
                        '"', '\\', '/' -> sb.append(e)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (i + 5 >= n) return null
                            val cp = json.substring(i + 2, i + 6).toIntOrNull(16) ?: return null
                            sb.append(cp.toChar())
                            i += 4
                        }
                        else -> sb.append(e)
                    }
                    i += 2
                }
                c == '"' -> return sb.toString() to (i + 1)
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return null
    }

    /**
     * 文本指纹：包名 + 可见 text/desc（去空白、排序去重），不含坐标。
     * 用于判断相邻两帧 UI 文本是否变化。
     */
    fun contentFingerprint(packageName: String, texts: List<FrameTextNode>): String {
        val tokens = LinkedHashSet<String>()
        for (n in texts) {
            n.text.trim().takeIf { it.isNotEmpty() }?.let { tokens.add(it) }
            n.desc.trim().takeIf { it.isNotEmpty() }?.let { tokens.add(it) }
        }
        val sb = StringBuilder(packageName).append('|')
        for (t in tokens.sorted()) {
            sb.append(t).append('\u0001')
        }
        return sha1Hex(sb.toString())
    }

    fun isSparseText(texts: List<FrameTextNode>): Boolean {
        val chars = texts.sumOf { it.text.trim().length + it.desc.trim().length }
        return chars < 8
    }

    private fun sha1Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            for (b in digest) append("%02x".format(b))
        }
    }

    /**
     * 渲染用展示文案：
     * - 整帧无 text → 直接用 desc
     * - 「更多」开启 → 优先 desc，否则 text
     * - 默认 → 仅 text
     */
    fun displayLabel(node: FrameTextNode, showDesc: Boolean, frameHasAnyText: Boolean): String? {
        if (!frameHasAnyText) {
            return node.desc.takeIf { it.isNotBlank() }
        }
        if (showDesc) {
            return node.desc.takeIf { it.isNotBlank() }
                ?: node.text.takeIf { it.isNotBlank() }
        }
        return node.text.takeIf { it.isNotBlank() }
    }
}

/** 日统计时间戳视图条目 */
data class TimelineItem(
    val packageName: String,
    val label: String,
    val categoryId: String,
    val startMillis: Long,
    val endMillis: Long,
) {
    val durationMillis: Long get() = (endMillis - startMillis).coerceAtLeast(0L)
}
