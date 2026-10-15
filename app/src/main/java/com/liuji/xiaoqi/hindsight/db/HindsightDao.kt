package com.liuji.xiaoqi.hindsight.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface HindsightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDevices(items: List<HsDeviceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(items: List<HsCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppGroups(items: List<HsAppGroupEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppGroupMembers(items: List<HsAppGroupMemberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivities(items: List<HsActivityEntity>)

    @Query("DELETE FROM hs_activities WHERE device_id = :deviceId AND local_date = :localDate")
    suspend fun deleteActivitiesForDay(deviceId: String, localDate: String)

    @Query("SELECT * FROM hs_activities WHERE local_date >= :fromDate AND local_date <= :toDate")
    suspend fun activitiesBetween(fromDate: String, toDate: String): List<HsActivityEntity>

    @Query("SELECT * FROM hs_categories WHERE deleted = 0")
    suspend fun activeCategories(): List<HsCategoryEntity>

    @Query("SELECT * FROM hs_app_groups WHERE deleted = 0")
    suspend fun activeAppGroups(): List<HsAppGroupEntity>

    @Query("SELECT * FROM hs_app_group_members WHERE deleted = 0")
    suspend fun activeAppGroupMembers(): List<HsAppGroupMemberEntity>

    @Query("SELECT * FROM hs_devices")
    suspend fun devices(): List<HsDeviceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(item: HsMetaEntity)

    @Query("SELECT value FROM hs_meta WHERE key = :key LIMIT 1")
    suspend fun getMeta(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIcons(items: List<HsAppIconEntity>)

    @Query("SELECT * FROM hs_app_icons WHERE process_name = :processName LIMIT 1")
    suspend fun iconFor(processName: String): HsAppIconEntity?

    @Query("SELECT process_name FROM hs_app_icons")
    suspend fun allIconProcessNames(): List<String>

    @Transaction
    suspend fun replaceDayActivities(deviceId: String, localDate: String, rows: List<HsActivityEntity>) {
        deleteActivitiesForDay(deviceId, localDate)
        if (rows.isNotEmpty()) upsertActivities(rows)
    }
}
