package com.liuji.xiaoqi.frame

import com.liuji.xiaoqi.data.db.FrameBlacklistEntity
import com.liuji.xiaoqi.data.db.UsageDao
import java.util.concurrent.atomic.AtomicReference

data class FrameBlacklistItem(
    val packageName: String,
    val label: String,
    val source: String,
)

object FrameBlacklistStore {
    const val SOURCE_USER = "user"
    const val SOURCE_AUTO = "auto"
    const val SOURCE_BUILTIN = "builtin"

    private val blockedRef = AtomicReference<Set<String>>(emptySet())
    private val exemptRef = AtomicReference<Set<String>>(emptySet())

    fun isBlocked(packageName: String): Boolean = packageName in blockedRef.get()

    fun isExempt(packageName: String): Boolean = packageName in exemptRef.get()

    suspend fun refresh(dao: UsageDao) {
        blockedRef.set(dao.getActiveFrameBlacklist().map { it.packageName }.toSet())
        exemptRef.set(dao.getExemptFrameBlacklistPackages().toSet())
    }

    suspend fun listActive(dao: UsageDao): List<FrameBlacklistItem> =
        dao.getActiveFrameBlacklist().map {
            FrameBlacklistItem(it.packageName, it.label, it.source)
        }.also { items ->
            blockedRef.set(items.map { it.packageName }.toSet())
        }

    suspend fun add(
        dao: UsageDao,
        packageName: String,
        label: String,
        source: String,
    ): Boolean {
        val existing = dao.getFrameBlacklist(packageName)
        if (existing?.userExempt == true) return false
        if (existing != null && !existing.userExempt) {
            blockedRef.updateAndGet { it + packageName }
            return false
        }
        dao.upsertFrameBlacklist(
            FrameBlacklistEntity(
                packageName = packageName,
                label = label.ifBlank { packageName },
                source = source,
                createdAt = System.currentTimeMillis(),
                userExempt = false,
            ),
        )
        blockedRef.updateAndGet { it + packageName }
        exemptRef.updateAndGet { it - packageName }
        return true
    }

    suspend fun removeByUser(dao: UsageDao, packageName: String, label: String) {
        val existing = dao.getFrameBlacklist(packageName)
        dao.upsertFrameBlacklist(
            FrameBlacklistEntity(
                packageName = packageName,
                label = existing?.label?.takeIf { it.isNotBlank() } ?: label.ifBlank { packageName },
                source = existing?.source ?: SOURCE_USER,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                userExempt = true,
            ),
        )
        blockedRef.updateAndGet { it - packageName }
        exemptRef.updateAndGet { it + packageName }
    }
}
