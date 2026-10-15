package com.liuji.xiaoqi.hindsight.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        HsDeviceEntity::class,
        HsCategoryEntity::class,
        HsAppGroupEntity::class,
        HsAppGroupMemberEntity::class,
        HsActivityEntity::class,
        HsMetaEntity::class,
        HsAppIconEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class HindsightDatabase : RoomDatabase() {
    abstract fun dao(): HindsightDao

    companion object {
        @Volatile
        private var instance: HindsightDatabase? = null

        fun getInstance(context: Context): HindsightDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HindsightDatabase::class.java,
                    "hindsight_cache.db",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
