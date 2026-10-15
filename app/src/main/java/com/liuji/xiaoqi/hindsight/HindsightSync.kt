package com.liuji.xiaoqi.hindsight

import android.content.Context
import android.util.Base64
import com.liuji.xiaoqi.hindsight.db.HsActivityEntity
import com.liuji.xiaoqi.hindsight.db.HsAppGroupEntity
import com.liuji.xiaoqi.hindsight.db.HsAppGroupMemberEntity
import com.liuji.xiaoqi.hindsight.db.HsAppIconEntity
import com.liuji.xiaoqi.hindsight.db.HsCategoryEntity
import com.liuji.xiaoqi.hindsight.db.HsDeviceEntity
import com.liuji.xiaoqi.hindsight.db.HsMetaEntity
import com.liuji.xiaoqi.hindsight.db.HindsightDatabase
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 只读拉取坚果云 `/hindsight/` 数据到本地 Room 缓存。
 * 绝不调用任何写入 WebDAV 的方法。
 */
class HindsightSync(context: Context) {
    private val app = context.applicationContext
    private val credStore = HindsightCredStore(app)
    private val dao = HindsightDatabase.getInstance(app).dao()

    data class SyncResult(
        val devices: Int,
        val activityDays: Int,
        val activityRows: Int,
        val icons: Int,
        val message: String,
    )

    fun account(): WebDavAccount = credStore.load()

    fun saveAccount(account: WebDavAccount) = credStore.save(account)

    suspend fun lastSyncAt(): String? = dao.getMeta(META_LAST_SYNC)

    suspend fun hasCachedData(): Boolean = dao.devices().isNotEmpty()

    /**
     * @param lookbackDays 拉取最近多少天的 activities 日文件
     */
    suspend fun pullReadOnly(lookbackDays: Int = 60): SyncResult {
        val account = credStore.load()
        require(account.configured) { "请先在设置中填写坚果云 WebDAV 账号与应用密码" }

        val dav = WebDavClient(account.url, account.user, account.password)
        val root = account.rootPath.trim('/')

        // 1) 只列同步根：找 manifest.<deviceId>.json
        val rootEntries = dav.propfind(root, depth = 1)
        val manifests = rootEntries
            .filter { !it.isCollection && it.displayName.startsWith("manifest.") && it.displayName.endsWith(".json") }

        if (manifests.isEmpty()) {
            throw IllegalStateException("云端 /$root/ 下未找到 manifest.*.json，请确认电脑端已完成同步")
        }

        val today = LocalDate.now()
        val fromDate = today.minusDays(lookbackDays.toLong() - 1)
        val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
        val wantedDates = (0 until lookbackDays).map { today.minusDays(it.toLong()) }.toSet()

        var activityDays = 0
        var activityRows = 0
        var deviceCount = 0
        var iconCount = 0

        for (entry in manifests) {
            val name = entry.displayName
            val deviceId = name.removePrefix("manifest.").removeSuffix(".json")
            if (deviceId.isBlank()) continue

            val manifestBody = dav.getText("$root/$name")
            val manifest = HindsightJson.parseManifest(deviceId, manifestBody, entry.lastModified)

            // meta / categories / groups / icons（整表文件）
            pullDeviceMeta(dav, root, deviceId)
            pullCategories(dav, root, deviceId, manifest)
            pullAppGroups(dav, root, deviceId, manifest)
            pullAppGroupMembers(dav, root, deviceId, manifest)
            iconCount += pullIcons(dav, root, deviceId)
            deviceCount++

            // 日活动文件：仅下载最近 lookbackDays，且 manifest 中存在的
            for (date in wantedDates) {
                val dateStr = date.format(dateFmt)
                if (date.isBefore(fromDate)) continue
                val year = date.year
                val rel = "activities/$year/$dateStr.ndjson"
                if (!manifest.files.containsKey(rel)) continue
                val body = runCatching { dav.getText("$root/$deviceId/$rel") }.getOrNull()
                    ?: continue
                val parsed = HindsightJson.parseActivitiesNdjson(deviceId, body)
                val entities = parsed.map {
                    HsActivityEntity(
                        deviceId = it.deviceId,
                        remoteId = it.remoteId,
                        startedAt = it.startedAt,
                        endedAt = it.endedAt,
                        durationSecs = it.durationSecs,
                        localDate = it.localDate,
                        localHour = it.localHour,
                        processName = it.processName,
                        windowTitle = it.windowTitle,
                        categoryId = it.categoryId,
                        urlHost = it.urlHost,
                    )
                }
                dao.replaceDayActivities(deviceId, dateStr, entities)
                activityDays++
                activityRows += entities.size
            }
        }

        val now = java.time.Instant.now().toString()
        dao.putMeta(HsMetaEntity(META_LAST_SYNC, now))

        return SyncResult(
            devices = deviceCount,
            activityDays = activityDays,
            activityRows = activityRows,
            icons = iconCount,
            message = "已同步 $deviceCount 台设备 · $activityDays 天 · $activityRows 条会话 · $iconCount 图标",
        )
    }

    private suspend fun pullDeviceMeta(dav: WebDavClient, root: String, deviceId: String) {
        val body = runCatching { dav.getText("$root/$deviceId/meta.json") }.getOrNull() ?: return
        val meta = HindsightJson.parseDeviceMeta(body)
        dao.upsertDevices(
            listOf(
                HsDeviceEntity(
                    deviceId = meta.deviceId.ifBlank { deviceId },
                    displayName = meta.displayName,
                    color = meta.color,
                    icon = meta.icon,
                    os = meta.os,
                ),
            ),
        )
    }

    private suspend fun pullCategories(
        dav: WebDavClient,
        root: String,
        deviceId: String,
        manifest: HsManifest,
    ) {
        if (!manifest.files.containsKey("categories.json") &&
            runCatching { dav.propfind("$root/$deviceId", 1) }.getOrNull()
                ?.none { it.displayName == "categories.json" } == true
        ) {
            // still try get
        }
        val body = runCatching { dav.getText("$root/$deviceId/categories.json") }.getOrNull() ?: return
        val cats = HindsightJson.parseCategories(body).map {
            HsCategoryEntity(it.id, it.name, it.color, it.deleted)
        }
        if (cats.isNotEmpty()) dao.upsertCategories(cats)
    }

    private suspend fun pullAppGroups(
        dav: WebDavClient,
        root: String,
        deviceId: String,
        manifest: HsManifest,
    ) {
        if (!manifest.files.containsKey("app_groups.json")) {
            // try anyway for older peers
        }
        val body = runCatching { dav.getText("$root/$deviceId/app_groups.json") }.getOrNull() ?: return
        val groups = HindsightJson.parseAppGroups(body).map {
            HsAppGroupEntity(it.id, it.displayName, it.categoryId, it.deleted)
        }
        if (groups.isNotEmpty()) dao.upsertAppGroups(groups)
    }

    private suspend fun pullAppGroupMembers(
        dav: WebDavClient,
        root: String,
        deviceId: String,
        manifest: HsManifest,
    ) {
        val body = runCatching { dav.getText("$root/$deviceId/app_group_members.json") }.getOrNull()
            ?: return
        val members = HindsightJson.parseAppGroupMembers(body).map {
            HsAppGroupMemberEntity(it.processName, it.groupId, it.deleted)
        }
        if (members.isNotEmpty()) dao.upsertAppGroupMembers(members)
    }

    /** 拉取电脑端导出的应用图标（解决手机端无 Windows 进程图标的问题） */
    private suspend fun pullIcons(dav: WebDavClient, root: String, deviceId: String): Int {
        val body = runCatching { dav.getText("$root/$deviceId/icons.json") }.getOrNull() ?: return 0
        val parsed = runCatching { HindsightJson.parseIcons(body) }.getOrElse { emptyList() }
        val entities = ArrayList<HsAppIconEntity>(parsed.size.coerceAtMost(256))
        for (icon in parsed) {
            runCatching {
                if (icon.deleted || icon.processName.isBlank() || icon.iconPngBase64.isBlank()) {
                    return@runCatching
                }
                val bytes = Base64.decode(icon.iconPngBase64, Base64.DEFAULT)
                if (bytes.isEmpty()) return@runCatching
                entities += HsAppIconEntity(
                    processName = icon.processName,
                    iconPng = bytes,
                    updatedAt = icon.updatedAt,
                )
            }
        }
        // 分批 upsert，降低单次事务峰值
        entities.chunked(ICON_UPSERT_BATCH).forEach { batch ->
            if (batch.isNotEmpty()) dao.upsertIcons(batch)
        }
        return entities.size
    }

    companion object {
        private const val META_LAST_SYNC = "last_sync_at"
        private const val ICON_UPSERT_BATCH = 30
    }
}
