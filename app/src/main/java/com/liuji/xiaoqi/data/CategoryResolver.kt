package com.liuji.xiaoqi.data

import android.content.Context
import com.liuji.xiaoqi.data.db.AppMetaEntity
import com.liuji.xiaoqi.data.db.UsageDatabase

class CategoryResolver(context: Context) {

    private val dao = UsageDatabase.getInstance(context).usageDao()
    private val appContext = context.applicationContext

    @Volatile
    private var userMeta: Map<String, AppMetaEntity> = emptyMap()

    init {
        AppCategorizer.ensureRulesLoaded(appContext)
    }

    suspend fun refresh() {
        CategoryRepository(appContext).ensureSeeded()
        val categories = dao.getAllCategories()
        CategoryRegistry.updateFromDb(categories)
        userMeta = dao.getAllAppMeta().associateBy { it.packageName }
    }

    fun resolve(packageName: String): String {
        val meta = userMeta[packageName]
        if (meta?.categoryUser == true) {
            return meta.categoryId
        }
        return AppCategorizer.categoryIdFor(packageName)
    }

    fun displayCategory(categoryId: String): AppCategory = CategoryRegistry.of(categoryId)
}
