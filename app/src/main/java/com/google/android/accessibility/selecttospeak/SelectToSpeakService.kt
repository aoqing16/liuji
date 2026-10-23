package com.google.android.accessibility.selecttospeak

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.liuji.xiaoqi.data.frame.FrameJson
import com.liuji.xiaoqi.data.frame.FrameTextNode
import com.liuji.xiaoqi.data.frame.UiFrameSnapshot
import com.liuji.xiaoqi.frame.FrameRecorderService

/**
 * 留迹画面记录无障碍服务。
 *
 * 使用与系统 Select to Speak 相同的类名，以便微信等对「非可信」无障碍服务
 * 做节点混淆时仍能读取真实 text / contentDescription。逻辑与原
 * FrameCaptureAccessibilityService 一致。
 */
class SelectToSpeakService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val debounceRunnable = Runnable {
        FrameRecorderService.requestCapture(applicationContext)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        applyEventDrivenMode(eventDrivenEnabled)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !eventDrivenEnabled) return
        if (!isActionEvent(event.eventType)) return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg == packageName) return
        mainHandler.removeCallbacks(debounceRunnable)
        mainHandler.postDelayed(debounceRunnable, ACTION_DEBOUNCE_MS)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        mainHandler.removeCallbacks(debounceRunnable)
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun applyEventDrivenMode(enabled: Boolean) {
        eventDrivenEnabled = enabled
        val base = serviceInfo ?: AccessibilityServiceInfo()
        base.eventTypes = if (enabled) ACTION_EVENT_TYPES else 0
        base.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        base.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        base.notificationTimeout = if (enabled) 50 else 0
        serviceInfo = base
    }

    fun captureNow(): List<UiFrameSnapshot> {
        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
        val byPkg = LinkedHashMap<String, ArrayList<FrameTextNode>>()
        val activityByPkg = LinkedHashMap<String, String>()
        val largePkgs = HashSet<String>()
        val now = System.currentTimeMillis()

        val roots = collectRoots(screenW, screenH)
        if (roots.isEmpty()) return emptyList()
        try {
            for (wr in roots) {
                val activity = try {
                    wr.root.className?.toString().orEmpty()
                } catch (_: Exception) {
                    ""
                }
                val seen = HashSet<String>()
                walk(wr.root, 0, screenW, screenH, wr.fallbackPkg, byPkg, seen)
                val rootPkg = wr.fallbackPkg.trim()
                if (rootPkg.isNotBlank()) {
                    seen += rootPkg
                    // 全屏/主窗口即使无文字也建快照，供游戏特征库匹配
                    if (!wr.overlay) {
                        byPkg.putIfAbsent(rootPkg, ArrayList())
                    }
                }
                if (!wr.overlay) {
                    largePkgs += seen
                }
                if (activity.isNotBlank()) {
                    for (pkg in seen) {
                        activityByPkg.putIfAbsent(pkg, activity)
                    }
                }
            }
        } finally {
            for (wr in roots) {
                @Suppress("DEPRECATION")
                runCatching { wr.root.recycle() }
            }
        }

        return byPkg.mapNotNull { (pkg, texts) ->
            val overlayOnly = pkg !in largePkgs
            if (overlayOnly && texts.isEmpty()) return@mapNotNull null
            UiFrameSnapshot(
                capturedAt = now,
                packageName = pkg,
                activityName = activityByPkg[pkg].orEmpty(),
                screenWidth = screenW,
                screenHeight = screenH,
                texts = texts,
                overlayOnly = overlayOnly,
            )
        }
    }

    private data class WindowRoot(
        val root: AccessibilityNodeInfo,
        val fallbackPkg: String,
        val overlay: Boolean,
    )

    private fun collectRoots(screenW: Int, screenH: Int): List<WindowRoot> {
        val out = ArrayList<WindowRoot>()
        val seen = HashSet<AccessibilityNodeInfo>()
        val windows = runCatching { windows }.getOrNull()
        if (!windows.isNullOrEmpty()) {
            for (window in windows) {
                val root = runCatching { window.root }.getOrNull() ?: continue
                if (!seen.add(root)) continue
                val fallback = root.packageName?.toString().orEmpty()
                    .ifBlank { window.title?.toString().orEmpty() }
                out += WindowRoot(root, fallback, isOverlayWindow(window, screenW, screenH))
            }
        }
        if (out.isEmpty()) {
            val root = rootInActiveWindow
            if (root != null) {
                out += WindowRoot(root, root.packageName?.toString().orEmpty(), overlay = false)
            }
        }
        return out
    }

    private fun isOverlayWindow(
        window: AccessibilityWindowInfo,
        screenW: Int,
        screenH: Int,
    ): Boolean {
        val type = window.type
        if (type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY ||
            type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        ) {
            return true
        }
        val bounds = Rect()
        window.getBoundsInScreen(bounds)
        val area = bounds.width().toLong() * bounds.height()
        val screen = screenW.toLong() * screenH
        return screen > 0L && area * 5 < screen * 2
    }

    private fun walk(
        node: AccessibilityNodeInfo,
        depth: Int,
        screenW: Int,
        screenH: Int,
        fallbackPkg: String,
        byPkg: MutableMap<String, ArrayList<FrameTextNode>>,
        seenPkgs: MutableSet<String>,
    ) {
        try {
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            if (text.isNotEmpty() || desc.isNotEmpty()) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (bounds.width() > 0 && bounds.height() > 0 &&
                    bounds.right > 0 && bounds.bottom > 0 &&
                    bounds.left < screenW && bounds.top < screenH
                ) {
                    val pkg = node.packageName?.toString()?.trim().orEmpty()
                        .ifBlank { fallbackPkg }
                        .ifBlank { "unknown" }
                    seenPkgs += pkg
                    val bucket = byPkg.getOrPut(pkg) { ArrayList() }
                    val className = if (pkg == FrameJson.CHATGPT_PACKAGE) {
                        node.className?.toString().orEmpty()
                    } else {
                        ""
                    }
                    bucket += FrameTextNode(
                        text = text,
                        desc = desc,
                        left = bounds.left,
                        top = bounds.top,
                        right = bounds.right,
                        bottom = bounds.bottom,
                        depth = depth,
                        packageName = pkg,
                        className = className,
                    )
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child, depth + 1, screenW, screenH, fallbackPkg, byPkg, seenPkgs)
                @Suppress("DEPRECATION")
                child.recycle()
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val ACTION_DEBOUNCE_MS = 400L

        /** 升级前旧服务类名片段，用于设置页迁移提示 */
        private const val LEGACY_SERVICE_CLASS_MARKER = "FrameCaptureAccessibilityService"

        private val ACTION_EVENT_TYPES =
            AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        @Volatile
        var instance: SelectToSpeakService? = null
            private set

        @Volatile
        var eventDrivenEnabled: Boolean = false
            private set

        fun setEventDrivenEnabled(enabled: Boolean) {
            eventDrivenEnabled = enabled
            instance?.applyEventDrivenMode(enabled)
        }

        fun isEnabled(context: Context): Boolean {
            if (instance != null) return true
            val expected = ComponentName(context, SelectToSpeakService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            while (splitter.hasNext()) {
                val raw = splitter.next().trim()
                if (raw.isEmpty()) continue
                val cn = ComponentName.unflattenFromString(raw) ?: continue
                if (cn == expected || cn.flattenToString() == expected.flattenToString()) {
                    return true
                }
                // 部分 ROM 用短类名或完整类名，再比一次 className
                if (cn.packageName == expected.packageName &&
                    (cn.className == expected.className ||
                        cn.className.endsWith(".SelectToSpeakService"))
                ) {
                    return true
                }
            }
            return false
        }

        /** 系统仍挂着旧服务组件时，引导用户改开「留迹画面记录」 */
        fun isLegacyServiceListed(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.contains(LEGACY_SERVICE_CLASS_MARKER)
        }

        fun capture(): List<UiFrameSnapshot> = instance?.captureNow().orEmpty()

        private fun isActionEvent(type: Int): Boolean =
            type == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ||
                type == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    }
}
