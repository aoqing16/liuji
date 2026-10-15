package com.liuji.xiaoqi.hindsight.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "hs_devices")
data class HsDeviceEntity(
    @PrimaryKey @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val color: String,
    val icon: String,
    val os: String?,
)

@Entity(tableName = "hs_categories")
data class HsCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    val deleted: Boolean = false,
)

@Entity(tableName = "hs_app_groups")
data class HsAppGroupEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    val deleted: Boolean = false,
)

@Entity(tableName = "hs_app_group_members")
data class HsAppGroupMemberEntity(
    @PrimaryKey @ColumnInfo(name = "process_name") val processName: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    val deleted: Boolean = false,
)

@Entity(
    tableName = "hs_activities",
    primaryKeys = ["device_id", "remote_id"],
    indices = [
        Index(value = ["local_date"]),
        Index(value = ["device_id", "local_date"]),
        Index(value = ["process_name"]),
    ],
)
data class HsActivityEntity(
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "remote_id") val remoteId: String,
    @ColumnInfo(name = "started_at") val startedAt: String,
    @ColumnInfo(name = "ended_at") val endedAt: String,
    @ColumnInfo(name = "duration_secs") val durationSecs: Long,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "local_hour") val localHour: Int,
    @ColumnInfo(name = "process_name") val processName: String,
    @ColumnInfo(name = "window_title") val windowTitle: String?,
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "url_host") val urlHost: String?,
)

@Entity(tableName = "hs_meta")
data class HsMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/** 电脑端同步的应用图标（PNG 字节，来自 icons.json base64） */
@Entity(tableName = "hs_app_icons")
data class HsAppIconEntity(
    @PrimaryKey @ColumnInfo(name = "process_name") val processName: String,
    @ColumnInfo(name = "icon_png")
    val iconPng: ByteArray,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HsAppIconEntity) return false
        return processName == other.processName && updatedAt == other.updatedAt &&
            iconPng.contentEquals(other.iconPng)
    }

    override fun hashCode(): Int = 31 * processName.hashCode() + updatedAt.hashCode()
}
