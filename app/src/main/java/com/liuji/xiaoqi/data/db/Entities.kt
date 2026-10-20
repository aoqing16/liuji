package com.liuji.xiaoqi.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "usage_sessions",
    indices = [
        Index(value = ["start_millis", "end_millis"]),
        Index(value = ["package_name"]),
    ],
)
data class UsageSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "start_millis") val startMillis: Long,
    @ColumnInfo(name = "end_millis") val endMillis: Long,
    @ColumnInfo(name = "category_id") val categoryId: String,
)

@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    /** 1 = 用户手动指定，同步时不得覆盖 category_id */
    @ColumnInfo(name = "category_user") val categoryUser: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "user_categories")
data class UserCategoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    /** #AARRGGBB */
    @ColumnInfo(name = "color") val color: String,
    @ColumnInfo(name = "builtin") val builtin: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "last_synced_millis") val lastSyncedMillis: Long,
)

/** 应用设置（单行） */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "frame_recording_enabled") val frameRecordingEnabled: Boolean = false,
    /** 帧记录间隔毫秒：0=操作变化；5000 / 10000 / 30000 / 60000 / 300000=定时 */
    @ColumnInfo(name = "frame_interval_ms") val frameIntervalMs: Long = 30_000L,
)

/**
 * UI 帧快照（只存数据，渲染另做）。
 * textsJson: [{"text":"...","left":0,"top":0,"right":0,"bottom":0,"depth":0}, ...]
 */
@Entity(
    tableName = "ui_frames",
    indices = [
        Index(value = ["package_name", "captured_at"]),
        Index(value = ["captured_at"]),
    ],
)
data class UiFrameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "captured_at") val capturedAt: Long,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "activity_name") val activityName: String,
    @ColumnInfo(name = "screen_width") val screenWidth: Int,
    @ColumnInfo(name = "screen_height") val screenHeight: Int,
    @ColumnInfo(name = "text_count") val textCount: Int,
    @ColumnInfo(name = "texts_json") val textsJson: String,
)

/** 导出扫描投影，不是表实体 */
data class FrameTextExportRow(
    val id: Long,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "texts_json") val textsJson: String,
)

/** 画面记录黑名单；user_exempt=true 表示用户已移除，自动/特征库不再加回 */
@Entity(tableName = "frame_blacklist")
data class FrameBlacklistEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "user_exempt") val userExempt: Boolean = false,
)

/** 隐私应用：导出时不带该包的帧文本 / text_dictionary */
@Entity(tableName = "privacy_apps")
data class PrivacyAppEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
