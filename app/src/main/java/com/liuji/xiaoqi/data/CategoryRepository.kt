package com.liuji.xiaoqi.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.liuji.xiaoqi.data.db.AppMetaEntity
import com.liuji.xiaoqi.data.db.UsageDatabase
import com.liuji.xiaoqi.data.db.UserCategoryEntity
import java.util.UUID

class CategoryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = UsageDatabase.getInstance(appContext).usageDao()
    private val packageManager = appContext.packageManager

    suspend fun ensureSeeded() {
        val existing = dao.getAllCategories()
        if (existing.isNotEmpty()) return
        val seeded = Categories.all.mapIndexed { index, cat ->
            UserCategoryEntity(
                id = cat.id,
                name = cat.name,
                color = CategoryColors.toHex(cat.color),
                builtin = true,
                sortOrder = index,
            )
        }
        dao.upsertCategories(seeded)
    }

    suspend fun listCategoriesWithCounts(): List<UserCategoryWithCount> {
        ensureSeeded()
        AppCategorizer.ensureRulesLoaded(appContext)
        val rows = dao.getAllCategories()
        CategoryRegistry.updateFromDb(rows)
        val counts = dao.getAllAppMeta()
            .groupBy { meta -> resolvedCategoryId(meta) }
            .mapValues { it.value.size }
        return rows.map { row ->
            UserCategoryWithCount(
                id = row.id,
                name = row.name,
                color = CategoryColors.fromHex(row.color) ?: Categories.Other.color,
                builtin = row.builtin,
                sortOrder = row.sortOrder,
                appCount = counts[row.id] ?: 0,
            )
        }
    }

    suspend fun listApps(): List<AppCategoryAssignment> {
        ensureSeeded()
        AppCategorizer.ensureRulesLoaded(appContext)
        CategoryRegistry.updateFromDb(dao.getAllCategories())
        return dao.getAllAppMeta()
            .sortedBy { it.label.lowercase() }
            .map { meta -> metaToAssignment(meta) }
    }

    /**
     * 分类「添加应用」候选：LAUNCHER 应用 ∪ app_meta 已有包 ∪ 帧库出现过的包，
     * 以便 SystemUI 等无桌面图标的包仍可搜到并归类。
     */
    suspend fun listInstalledAppsForPicker(): List<AppCategoryAssignment> {
        ensureSeeded()
        AppCategorizer.ensureRulesLoaded(appContext)
        CategoryRegistry.updateFromDb(dao.getAllCategories())
        val metaByPkg = dao.getAllAppMeta().associateBy { it.packageName }
        val selfPkg = appContext.packageName
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolves = packageManager.queryIntentActivities(launchIntent, PackageManager.MATCH_ALL)
        val launcherLabels = HashMap<String, String>()
        val seen = LinkedHashSet<String>()
        for (ri in resolves) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (pkg == selfPkg) continue
            seen.add(pkg)
            val label = ri.loadLabel(packageManager)?.toString()?.trim()?.ifBlank { null }
            if (label != null) launcherLabels[pkg] = label
        }
        for (pkg in metaByPkg.keys) {
            if (pkg != selfPkg) seen.add(pkg)
        }
        for (pkg in dao.distinctFramePackages()) {
            if (pkg.isNotBlank() && pkg != selfPkg) seen.add(pkg)
        }
        val out = ArrayList<AppCategoryAssignment>(seen.size)
        for (pkg in seen) {
            val meta = metaByPkg[pkg]
            val label = meta?.label?.takeIf { it.isNotBlank() }
                ?: launcherLabels[pkg]
                ?: resolveInstalledLabel(pkg)
                ?: pkg.substringAfterLast('.')
            if (meta != null) {
                out += metaToAssignment(meta).copy(label = label)
            } else {
                val catId = AppCategorizer.categoryIdFor(pkg)
                val cat = CategoryRegistry.of(catId)
                out += AppCategoryAssignment(
                    packageName = pkg,
                    label = label,
                    categoryId = catId,
                    categoryName = cat.name,
                    categoryColor = cat.color,
                    userAssigned = false,
                )
            }
        }
        return out.sortedBy { it.label.lowercase() }
    }

    private fun resolveInstalledLabel(packageName: String): String? = try {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info)?.toString()?.trim()?.ifBlank { null }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun metaToAssignment(meta: AppMetaEntity): AppCategoryAssignment {
        val catId = resolvedCategoryId(meta)
        val cat = CategoryRegistry.of(catId)
        return AppCategoryAssignment(
            packageName = meta.packageName,
            label = meta.label,
            categoryId = catId,
            categoryName = cat.name,
            categoryColor = cat.color,
            userAssigned = meta.categoryUser,
        )
    }

    private fun resolvedCategoryId(meta: AppMetaEntity): String =
        if (meta.categoryUser) meta.categoryId else AppCategorizer.categoryIdFor(meta.packageName)

    suspend fun upsertCategory(
        id: String?,
        name: String,
        color: androidx.compose.ui.graphics.Color,
    ): String {
        ensureSeeded()
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "分类名称不能为空" }
        val categoryId = id ?: UUID.randomUUID().toString()
        val existing = id?.let { dao.getCategory(it) }
        val sortOrder = existing?.sortOrder
            ?: (dao.maxCategorySortOrder() + 1)
        dao.upsertCategory(
            UserCategoryEntity(
                id = categoryId,
                name = trimmed,
                color = CategoryColors.toHex(color),
                builtin = existing?.builtin ?: false,
                sortOrder = sortOrder,
            ),
        )
        return categoryId
    }

    suspend fun deleteCategory(id: String) {
        ensureSeeded()
        val cat = dao.getCategory(id) ?: return
        if (cat.builtin) error("内置分类不可删除")
        dao.reassignCategory(id, Categories.Other.id)
        val deleted = dao.deleteCategoryIfNotBuiltin(id)
        if (deleted == 0) error("无法删除该分类")
    }

    suspend fun assignApp(packageName: String, categoryId: String, label: String) {
        ensureSeeded()
        dao.getCategory(categoryId) ?: error("分类不存在")
        val now = System.currentTimeMillis()
        dao.upsertAppMeta(
            listOf(
                AppMetaEntity(
                    packageName = packageName,
                    label = label,
                    categoryId = categoryId,
                    categoryUser = true,
                    updatedAt = now,
                ),
            ),
        )
    }

    /** 从当前分类移除：归入「其他」并标记为用户指定，避免规则再次拉回。 */
    suspend fun removeAppFromCategory(packageName: String, label: String) {
        assignApp(packageName, Categories.Other.id, label)
    }

    suspend fun listCategoriesWithApps(): List<CategoryWithApps> {
        ensureSeeded()
        val categories = listCategoriesWithCounts()
        val allApps = listApps()
        return categories.map { cat ->
            CategoryWithApps(
                category = cat,
                apps = allApps
                    .filter { it.categoryId == cat.id }
                    .sortedBy { it.label.lowercase() },
            )
        }
    }
}

data class CategoryWithApps(
    val category: UserCategoryWithCount,
    val apps: List<AppCategoryAssignment>,
)

data class UserCategoryWithCount(
    val id: String,
    val name: String,
    val color: androidx.compose.ui.graphics.Color,
    val builtin: Boolean,
    val sortOrder: Int,
    val appCount: Int,
)

data class AppCategoryAssignment(
    val packageName: String,
    val label: String,
    val categoryId: String,
    val categoryName: String,
    val categoryColor: androidx.compose.ui.graphics.Color,
    val userAssigned: Boolean,
)
