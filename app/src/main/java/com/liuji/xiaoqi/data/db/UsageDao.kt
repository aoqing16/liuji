package com.liuji.xiaoqi.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface UsageDao {

    @Query(
        """
        SELECT * FROM usage_sessions
        WHERE start_millis < :endMillis AND end_millis > :startMillis
        ORDER BY start_millis ASC
        """,
    )
    suspend fun getSessionsOverlapping(startMillis: Long, endMillis: Long): List<UsageSessionEntity>

    @Query(
        """
        DELETE FROM usage_sessions
        WHERE start_millis < :endMillis AND end_millis > :startMillis
        """,
    )
    suspend fun deleteOverlapping(startMillis: Long, endMillis: Long)

    /**
     * 只删「起点落在重写窗内」的会话，避免误删起点早于采集窗、却与窗重叠的跨夜片段。
     */
    @Query(
        """
        DELETE FROM usage_sessions
        WHERE start_millis >= :startMillis AND start_millis < :endMillis
        """,
    )
    suspend fun deleteStartingInRange(startMillis: Long, endMillis: Long)

    @Insert
    suspend fun insertSessions(sessions: List<UsageSessionEntity>)

    @Transaction
    suspend fun replaceSessionsInRange(
        startMillis: Long,
        endMillis: Long,
        sessions: List<UsageSessionEntity>,
    ) {
        deleteStartingInRange(startMillis, endMillis)
        if (sessions.isNotEmpty()) {
            insertSessions(sessions)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppMeta(items: List<AppMetaEntity>)

    @Query(
        """
        UPDATE app_meta SET category_id = :categoryId, category_user = 0
        WHERE category_id = :oldCategoryId
        """,
    )
    suspend fun reassignCategory(oldCategoryId: String, categoryId: String)

    @Query("SELECT category_id, COUNT(*) AS cnt FROM app_meta GROUP BY category_id")
    suspend fun countAppsByCategory(): List<CategoryAppCount>

    // ── user categories ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(items: List<UserCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(item: UserCategoryEntity)

    @Query("UPDATE user_categories SET color = :color WHERE id = :id AND builtin = 1")
    suspend fun updateBuiltinCategoryColor(id: String, color: String)

    @Query("SELECT * FROM user_categories ORDER BY sort_order ASC, name ASC")
    suspend fun getAllCategories(): List<UserCategoryEntity>

    @Query("SELECT * FROM user_categories WHERE id = :id LIMIT 1")
    suspend fun getCategory(id: String): UserCategoryEntity?

    @Query("DELETE FROM user_categories WHERE id = :id AND builtin = 0")
    suspend fun deleteCategoryIfNotBuiltin(id: String): Int

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM user_categories")
    suspend fun maxCategorySortOrder(): Int

    @Query("SELECT * FROM app_meta WHERE package_name = :packageName LIMIT 1")
    suspend fun getAppMeta(packageName: String): AppMetaEntity?

    @Query("SELECT * FROM app_meta")
    suspend fun getAllAppMeta(): List<AppMetaEntity>

    @Query("SELECT last_synced_millis FROM sync_state WHERE id = 1 LIMIT 1")
    suspend fun getLastSyncedMillis(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(state: SyncStateEntity)

    @Query("SELECT COUNT(*) FROM usage_sessions")
    suspend fun sessionCount(): Long

    @Query("DELETE FROM usage_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM sync_state")
    suspend fun clearSyncState()

    // ── settings ──

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: AppSettingsEntity)

    // ── ui frames ──

    @Insert
    suspend fun insertFrame(frame: UiFrameEntity): Long

    @Query("SELECT * FROM ui_frames WHERE id = :id LIMIT 1")
    suspend fun getFrameById(id: Long): UiFrameEntity?

    @Query(
        """
        SELECT * FROM ui_frames
        WHERE package_name = :packageName
          AND captured_at >= :startMillis AND captured_at < :endMillis
        ORDER BY captured_at ASC
        """,
    )
    suspend fun getFramesForPackage(
        packageName: String,
        startMillis: Long,
        endMillis: Long,
    ): List<UiFrameEntity>

    @Query(
        """
        SELECT * FROM ui_frames
        WHERE package_name = :packageName
        ORDER BY captured_at ASC
        """,
    )
    suspend fun getFramesForPackageAllTime(packageName: String): List<UiFrameEntity>

    @Query(
        """
        SELECT * FROM ui_frames
        WHERE package_name IN (:packages)
        ORDER BY captured_at ASC
        """,
    )
    suspend fun getFramesForPackages(packages: List<String>): List<UiFrameEntity>

    @Query(
        """
        SELECT * FROM ui_frames
        ORDER BY captured_at ASC
        """,
    )
    suspend fun getAllFramesOrdered(): List<UiFrameEntity>

    @Query(
        """
        SELECT * FROM ui_frames
        WHERE captured_at >= :startMillis AND captured_at < :endMillis
        ORDER BY captured_at ASC
        """,
    )
    suspend fun getFramesInRange(startMillis: Long, endMillis: Long): List<UiFrameEntity>

    @Query(
        """
        SELECT * FROM ui_frames
        WHERE captured_at >= :startMillis AND captured_at < :endMillis
          AND id > :afterId
        ORDER BY id ASC
        LIMIT :limit
        """,
    )
    suspend fun getFramesInRangeAfterId(
        startMillis: Long,
        endMillis: Long,
        afterId: Long,
        limit: Int,
    ): List<UiFrameEntity>

    /** 导出扫描：只取 id / 包名 / 文案 JSON，避免把坐标等整行实体物化 */
    @Query(
        """
        SELECT id, package_name, texts_json FROM ui_frames
        WHERE captured_at >= :startMillis AND captured_at < :endMillis
          AND id > :afterId
        ORDER BY id ASC
        LIMIT :limit
        """,
    )
    suspend fun getFrameTextsInRangeAfterId(
        startMillis: Long,
        endMillis: Long,
        afterId: Long,
        limit: Int,
    ): List<FrameTextExportRow>

    @Query("SELECT COUNT(*) FROM ui_frames WHERE package_name = :packageName")
    suspend fun countFramesForPackage(packageName: String): Long

    @Query("SELECT COUNT(*) FROM ui_frames")
    suspend fun frameCount(): Long

    @Query("SELECT DISTINCT package_name FROM ui_frames")
    suspend fun distinctFramePackages(): List<String>

    // ── frame blacklist ──

    @Query(
        """
        SELECT * FROM frame_blacklist
        WHERE user_exempt = 0 AND pending = 0
        ORDER BY label ASC
        """,
    )
    suspend fun getActiveFrameBlacklist(): List<FrameBlacklistEntity>

    @Query(
        """
        SELECT * FROM frame_blacklist
        WHERE user_exempt = 0 AND pending = 1
        ORDER BY label ASC
        """,
    )
    suspend fun getPendingFrameBlacklist(): List<FrameBlacklistEntity>

    @Query("SELECT package_name FROM frame_blacklist WHERE user_exempt = 1")
    suspend fun getExemptFrameBlacklistPackages(): List<String>

    @Query("SELECT * FROM frame_blacklist WHERE package_name = :packageName LIMIT 1")
    suspend fun getFrameBlacklist(packageName: String): FrameBlacklistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFrameBlacklist(item: FrameBlacklistEntity)

    @Query(
        """
        UPDATE frame_blacklist
        SET user_exempt = 1
        WHERE package_name = :packageName
        """,
    )
    suspend fun exemptFrameBlacklist(packageName: String)

    @Query("DELETE FROM frame_blacklist WHERE package_name = :packageName")
    suspend fun deleteFrameBlacklist(packageName: String)

    // ── privacy apps（导出不含帧） ──

    @Query("SELECT * FROM privacy_apps ORDER BY label ASC")
    suspend fun getPrivacyApps(): List<PrivacyAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrivacyApp(item: PrivacyAppEntity)

    @Query("DELETE FROM privacy_apps WHERE package_name = :packageName")
    suspend fun deletePrivacyApp(packageName: String)
}
