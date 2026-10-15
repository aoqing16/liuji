package com.liuji.xiaoqi.data.db

import androidx.room.ColumnInfo

data class CategoryAppCount(
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "cnt") val count: Int,
)
