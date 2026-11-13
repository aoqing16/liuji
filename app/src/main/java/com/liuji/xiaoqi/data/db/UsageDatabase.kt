package com.liuji.xiaoqi.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.liuji.xiaoqi.data.frame.FrameFtsIndexer
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory

@Database(
    entities = [
        UsageSessionEntity::class,
        AppMetaEntity::class,
        UserCategoryEntity::class,
        SyncStateEntity::class,
        AppSettingsEntity::class,
        UiFrameEntity::class,
        FrameBlacklistEntity::class,
        PrivacyAppEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
abstract class UsageDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao

    companion object {
        @Volatile
        private var instance: UsageDatabase? = null

        fun getInstance(context: Context): UsageDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    UsageDatabase::class.java,
                    "liuji_usage.db",
                )
                    .openHelperFactory(RequerySQLiteOpenHelperFactory())
                    .addMigrations(
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                    )
                    .build()
                    .also { db ->
                        instance = db
                        FrameFtsIndexer.get(context).ensureStarted()
                    }
            }
        }
    }
}
