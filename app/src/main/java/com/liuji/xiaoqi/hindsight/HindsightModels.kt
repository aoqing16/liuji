package com.liuji.xiaoqi.hindsight

import org.json.JSONArray
import org.json.JSONObject

data class WebDavAccount(
    val url: String = DEFAULT_URL,
    val user: String = "",
    val password: String = "",
    val rootPath: String = DEFAULT_ROOT,
) {
    val configured: Boolean get() = user.isNotBlank() && password.isNotBlank()

    companion object {
        const val DEFAULT_URL = "https://dav.jianguoyun.com/dav/"
        const val DEFAULT_ROOT = "hindsight"
    }
}

data class HsManifest(
    val deviceId: String,
    val version: Int,
    val files: Map<String, Long>,
    val serverModified: String?,
)

data class HsDeviceMeta(
    val deviceId: String,
    val displayName: String,
    val color: String,
    val icon: String,
    val os: String?,
)

data class HsCategory(
    val id: String,
    val name: String,
    val color: String,
    val deleted: Boolean,
)

data class HsAppGroup(
    val id: String,
    val displayName: String,
    val categoryId: String?,
    val deleted: Boolean,
)

data class HsAppGroupMember(
    val processName: String,
    val groupId: String,
    val deleted: Boolean,
)

data class HsActivity(
    val remoteId: String,
    val deviceId: String,
    val startedAt: String,
    val endedAt: String,
    val durationSecs: Long,
    val localDate: String,
    val localHour: Int,
    val processName: String,
    val windowTitle: String?,
    val categoryId: String,
    val urlHost: String?,
)

data class HsAppIcon(
    val processName: String,
    val iconPngBase64: String,
    val updatedAt: String,
    val deleted: Boolean,
)

object HindsightJson {
    fun parseManifest(deviceId: String, body: String, serverModified: String?): HsManifest {
        val o = JSONObject(body)
        val filesObj = o.optJSONObject("files") ?: JSONObject()
        val files = LinkedHashMap<String, Long>()
        val keys = filesObj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            files[k] = filesObj.optLong(k, 0L)
        }
        return HsManifest(
            deviceId = deviceId,
            version = o.optInt("version", 1),
            files = files,
            serverModified = serverModified,
        )
    }

    fun parseDeviceMeta(body: String): HsDeviceMeta {
        val o = JSONObject(body)
        return HsDeviceMeta(
            deviceId = o.optString("deviceId"),
            displayName = o.optString("displayName", "设备"),
            color = o.optString("color", "#60a5fa"),
            icon = o.optString("icon", "Monitor"),
            os = o.optString("os").ifBlank { null },
        )
    }

    fun parseCategories(body: String): List<HsCategory> {
        val arr = JSONArray(body)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    HsCategory(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        color = o.optString("color", "#B0B7C3"),
                        deleted = !o.isNull("deletedAt") && o.optString("deletedAt").isNotBlank(),
                    ),
                )
            }
        }
    }

    fun parseAppGroups(body: String): List<HsAppGroup> {
        val arr = JSONArray(body)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    HsAppGroup(
                        id = o.optString("id"),
                        displayName = o.optString("displayName"),
                        categoryId = o.optString("categoryId").ifBlank { null },
                        deleted = !o.isNull("deletedAt") && o.optString("deletedAt").isNotBlank(),
                    ),
                )
            }
        }
    }

    fun parseAppGroupMembers(body: String): List<HsAppGroupMember> {
        val arr = JSONArray(body)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    HsAppGroupMember(
                        processName = o.optString("processName"),
                        groupId = o.optString("groupId"),
                        deleted = !o.isNull("deletedAt") && o.optString("deletedAt").isNotBlank(),
                    ),
                )
            }
        }
    }

    fun parseIcons(body: String): List<HsAppIcon> {
        val arr = JSONArray(body)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    HsAppIcon(
                        processName = o.optString("processName"),
                        iconPngBase64 = o.optString("iconPngBase64"),
                        updatedAt = o.optString("updatedAt"),
                        deleted = !o.isNull("deletedAt") && o.optString("deletedAt").isNotBlank(),
                    ),
                )
            }
        }
    }

    fun parseActivitiesNdjson(deviceId: String, body: String): List<HsActivity> {
        val out = ArrayList<HsActivity>()
        body.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            runCatching {
                val o = JSONObject(line)
                out += HsActivity(
                    remoteId = o.opt("id")?.toString() ?: return@runCatching,
                    deviceId = deviceId,
                    startedAt = o.optString("startedAt"),
                    endedAt = o.optString("endedAt"),
                    durationSecs = o.optLong("durationSecs"),
                    localDate = o.optString("localDate"),
                    localHour = o.optInt("localHour"),
                    processName = o.optString("processName"),
                    windowTitle = o.optString("windowTitle").ifBlank { null },
                    categoryId = o.optString("categoryId", "other"),
                    urlHost = o.optString("urlHost").ifBlank { null },
                )
            }
        }
        return out
    }
}
