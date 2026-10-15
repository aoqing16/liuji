package com.liuji.xiaoqi.hindsight

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.liuji.xiaoqi.hindsight.db.HindsightDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 电脑云端应用图标：
 * 1. 优先用同步下来的 icons.json（PNG）写入 Room
 * 2. 内存 LRU 缓存 Drawable，供 Compose 同步读取
 * 3. 未命中返回 null → UI 用首字母色块占位
 *
 * 注意：LruCache 禁止 value=null，缺失图标用 [missing] 集合记录。
 * 不主动 recycle Bitmap，避免与 IconBitmapCache / Compose 绘制竞态崩溃。
 */
class HindsightIconStore(context: Context) {
    private val app = context.applicationContext
    private val dao = HindsightDatabase.getInstance(app).dao()
    private val memory = LruCache<String, Drawable>(MAX_MEMORY_ICONS)
    private val missing = HashSet<String>()
    private val lock = Any()

    fun getCached(processName: String): Drawable? {
        synchronized(lock) {
            if (missing.contains(processName)) return null
            return memory.get(processName)
        }
    }

    suspend fun ensureLoaded(processNames: Collection<String>) {
        withContext(Dispatchers.IO) {
            processNames.distinct().forEach { name ->
                if (name.isBlank()) return@forEach
                synchronized(lock) {
                    if (memory.get(name) != null || missing.contains(name)) return@forEach
                }
                val drawable = loadFromDb(name)
                synchronized(lock) {
                    if (drawable != null) {
                        memory.put(name, drawable)
                    } else {
                        missing.add(name)
                    }
                }
            }
        }
    }

    private suspend fun loadFromDb(processName: String): Drawable? {
        val row = dao.iconFor(processName) ?: return null
        val bmp = decodeBitmap(row.iconPng) ?: return null
        return BitmapDrawable(app.resources, bmp)
    }

    private fun decodeBitmap(bytes: ByteArray): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, TARGET_ICON_PX)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
            if (bmp.width > TARGET_ICON_PX || bmp.height > TARGET_ICON_PX) {
                val scaled = Bitmap.createScaledBitmap(bmp, TARGET_ICON_PX, TARGET_ICON_PX, true)
                if (scaled !== bmp) {
                    // 仅回收本函数刚创建、尚未交给外部的临时图
                    bmp.recycle()
                    bmp = scaled
                }
            }
            bmp
        }.getOrNull()
    }

    fun clearMemory() {
        synchronized(lock) {
            memory.evictAll()
            missing.clear()
        }
    }

    private companion object {
        const val MAX_MEMORY_ICONS = 96
        const val TARGET_ICON_PX = 96

        fun sampleSizeFor(width: Int, height: Int, target: Int): Int {
            var sample = 1
            var w = width
            var h = height
            while (w / 2 >= target && h / 2 >= target) {
                w /= 2
                h /= 2
                sample *= 2
            }
            return sample.coerceAtLeast(1)
        }
    }
}
