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
    private val pendingRef = AtomicReference<Set<String>>(emptySet())

    fun isBlocked(packageName: String): Boolean = packageName in blockedRef.get()

    fun isExempt(packageName: String): Boolean = packageName in exemptRef.get()

    fun isPending(packageName: String): Boolean = packageName in pendingRef.get()

    suspend fun refresh(dao: UsageDao) {
        blockedRef.set(dao.getActiveFrameBlacklist().map { it.packageName }.toSet())
        exemptRef.set(dao.getExemptFrameBlacklistPackages().toSet())
        pendingRef.set(dao.getPendingFrameBlacklist().map { it.packageName }.toSet())
    }

    suspend fun listActive(dao: UsageDao): List<FrameBlacklistItem> =
        dao.getActiveFrameBlacklist().map {
            FrameBlacklistItem(it.packageName, it.label, it.source)
        }.also { items ->
            blockedRef.set(items.map { it.packageName }.toSet())
        }

    suspend fun listPending(dao: UsageDao): List<FrameBlacklistItem> =
        dao.getPendingFrameBlacklist().map {
            FrameBlacklistItem(it.packageName, it.label, it.source)
        }.also { items ->
            pendingRef.set(items.map { it.packageName }.toSet())
        }

    suspend fun add(
        dao: UsageDao,
        packageName: String,
        label: String,
        source: String,
    ): Boolean {
        val existing = dao.getFrameBlacklist(packageName)
        if (existing?.userExempt == true) return false
        if (existing != null && !existing.pending) {
            blockedRef.updateAndGet { it + packageName }
            return false
        }
        dao.upsertFrameBlacklist(
            FrameBlacklistEntity(
                packageName = packageName,
                label = label.ifBlank { packageName },
                source = source,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                userExempt = false,
                pending = false,
            ),
        )
        blockedRef.updateAndGet { it + packageName }
        pendingRef.updateAndGet { it - packageName }
        exemptRef.updateAndGet { it - packageName }
        return true
    }

    /** 稀疏持续到点：只记待确认，不屏蔽。已在名单、待确认或用户忽略过则跳过。 */
    suspend fun propose(
        dao: UsageDao,
        packageName: String,
        label: String,
    ): Boolean {
        if (isBlocked(packageName) || isExempt(packageName) || isPending(packageName)) return false
        val existing = dao.getFrameBlacklist(packageName)
        if (existing != null) {
            when {
                existing.userExempt -> exemptRef.updateAndGet { it + packageName }
                existing.pending -> pendingRef.updateAndGet { it + packageName }
                else -> blockedRef.updateAndGet { it + packageName }
            }
            return false
        }
        dao.upsertFrameBlacklist(
            FrameBlacklistEntity(
                packageName = packageName,
                label = label.ifBlank { packageName },
                source = SOURCE_AUTO,
                createdAt = System.currentTimeMillis(),
                userExempt = false,
                pending = true,
            ),
        )
        pendingRef.updateAndGet { it + packageName }
        return true
    }

    suspend fun confirmPending(dao: UsageDao, packageName: String) {
        val existing = dao.getFrameBlacklist(packageName) ?: return
        if (existing.userExempt || !existing.pending) return
        dao.upsertFrameBlacklist(existing.copy(pending = false))
        pendingRef.updateAndGet { it - packageName }
        blockedRef.updateAndGet { it + packageName }
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
                pending = false,
            ),
        )
        blockedRef.updateAndGet { it - packageName }
        pendingRef.updateAndGet { it - packageName }
        exemptRef.updateAndGet { it + packageName }
    }
}
