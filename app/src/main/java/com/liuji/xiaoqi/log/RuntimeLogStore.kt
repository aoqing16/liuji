package com.liuji.xiaoqi.log

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 轻量环形缓冲 + 本地文件持久化，供设置页「运行日志」展示。
 * 不依赖 adb logcat。
 *
 * 磁盘读写在单线程执行器上，避免主线程卡顿；内存缓冲仍同步更新以便 UI 即时可见。
 */
object RuntimeLogStore {
    private const val MAX_ENTRIES = 800
    private const val MAX_FILE_BYTES = 256 * 1024
    private const val FILE_NAME = "runtime.log"

    private val lock = Any()
    private val entries = ArrayDeque<String>(MAX_ENTRIES)
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private var logFile: File? = null
    private val inited = AtomicBoolean(false)
    private val timeFmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA)
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "runtime-log-io").apply { isDaemon = true }
    }

    fun init(context: Context) {
        if (!inited.compareAndSet(false, true)) return
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        logFile = file
        io.execute {
            synchronized(lock) {
                if (file.exists()) {
                    runCatching {
                        val lines = file.readLines(Charsets.UTF_8)
                        val keep = lines.takeLast(MAX_ENTRIES)
                        entries.clear()
                        entries.addAll(keep)
                        publishLocked()
                    }
                }
            }
        }
    }

    fun append(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val ts = synchronized(timeFmt) { timeFmt.format(Date()) }
        val base = "$ts $level/$tag: $message"
        val line = if (throwable != null) {
            "$base\n${throwable.stackTraceToString().trimEnd()}"
        } else {
            base
        }
        synchronized(lock) {
            line.lineSequence().filter { it.isNotEmpty() }.forEach { part ->
                while (entries.size >= MAX_ENTRIES) {
                    entries.removeFirst()
                }
                entries.addLast(part)
            }
            publishLocked()
        }
        io.execute {
            synchronized(lock) {
                persistLocked(line)
            }
        }
    }

    fun snapshot(): String = synchronized(lock) {
        entries.joinToString("\n")
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            publishLocked()
        }
        io.execute {
            synchronized(lock) {
                logFile?.writeText("", Charsets.UTF_8)
            }
        }
    }

    private fun publishLocked() {
        _text.value = entries.joinToString("\n")
    }

    private fun persistLocked(newLine: String) {
        val file = logFile ?: return
        runCatching {
            if (file.exists() && file.length() > MAX_FILE_BYTES) {
                val keep = entries.joinToString("\n")
                file.writeText(if (keep.isEmpty()) "" else "$keep\n", Charsets.UTF_8)
            } else {
                file.appendText("$newLine\n", Charsets.UTF_8)
            }
        }
    }
}
