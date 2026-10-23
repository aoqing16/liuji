package com.liuji.xiaoqi.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE frame_blacklist ADD COLUMN pending INTEGER NOT NULL DEFAULT 0",
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS privacy_apps (
                package_name TEXT NOT NULL PRIMARY KEY,
                label TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            DELETE FROM frame_blacklist
            WHERE package_name IN ('com.tencent.tmgp.sgame', 'org.autojs.autojs6')
            """.trimIndent(),
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS frame_blacklist (
                package_name TEXT NOT NULL PRIMARY KEY,
                label TEXT NOT NULL,
                source TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                user_exempt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_categories (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                color TEXT NOT NULL,
                builtin INTEGER NOT NULL,
                sort_order INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE app_meta ADD COLUMN category_user INTEGER NOT NULL DEFAULT 0
            """.trimIndent(),
        )
    }
}
