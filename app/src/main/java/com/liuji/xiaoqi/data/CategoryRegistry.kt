package com.liuji.xiaoqi.data

import androidx.compose.ui.graphics.Color
import com.liuji.xiaoqi.data.db.UserCategoryEntity

/**
 * 运行时分类目录：DB 自定义类 + 内置兜底。
 */
object CategoryRegistry {

    @Volatile
    private var byId: Map<String, AppCategory> = Categories.all.associateBy { it.id }

    fun updateFromDb(rows: List<UserCategoryEntity>) {
        if (rows.isEmpty()) return
        byId = rows.associate { row ->
            row.id to AppCategory(
                id = row.id,
                name = row.name,
                color = CategoryColors.fromHex(row.color) ?: Categories.Other.color,
            )
        }
    }

    fun of(id: String): AppCategory =
        byId[id] ?: Categories.staticOf(id)

    fun allKnown(): List<AppCategory> = byId.values.sortedBy { it.name }
}
