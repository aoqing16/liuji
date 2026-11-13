package com.liuji.xiaoqi.ui.frame

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuji.xiaoqi.data.AppUsageItem
import com.liuji.xiaoqi.data.CategoryWithApps
import com.liuji.xiaoqi.data.frame.FrameSearchHit
import com.liuji.xiaoqi.ui.FrameSearchDateMode
import com.liuji.xiaoqi.ui.FrameSearchScope
import com.liuji.xiaoqi.ui.components.AppUsageGrid
import com.liuji.xiaoqi.ui.theme.Accent
import com.liuji.xiaoqi.ui.theme.CardBackground
import com.liuji.xiaoqi.ui.theme.PageBackground
import com.liuji.xiaoqi.ui.theme.Space
import com.liuji.xiaoqi.ui.theme.TextMuted
import com.liuji.xiaoqi.ui.theme.TextStrong

data class FrameBrowseCategory(
    val categoryId: String,
    val categoryName: String,
    val categoryColor: Color,
    val apps: List<AppUsageItem>,
    val totalFrameCount: Long,
)

@Composable
fun FrameBrowseScreen(
    loading: Boolean,
    categories: List<FrameBrowseCategory>,
    searchQuery: String,
    searchMulti: Boolean,
    searchKeywords: List<String>,
    searchMatchAll: Boolean,
    searchScope: FrameSearchScope,
    searchPackages: Set<String>,
    searchCategories: List<CategoryWithApps>,
    searchCategoriesLoading: Boolean,
    searchStrict: Boolean,
    searchHits: List<FrameSearchHit>,
    searchHitIndex: Int,
    searchLoading: Boolean,
    searchLoadingMore: Boolean,
    searchHasMore: Boolean,
    searchTotal: Int?,
    searchMessage: String?,
    indexProgress: String?,
    searchSortDescending: Boolean = true,
    searchDateMode: FrameSearchDateMode = FrameSearchDateMode.ALL,
    searchSingleEpochDay: Long = 0L,
    searchRangeStartEpochDay: Long = 0L,
    searchRangeEndEpochDay: Long = 0L,
    onOpenSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchMultiChange: (Boolean) -> Unit,
    onSearchKeywordChange: (Int, String) -> Unit,
    onAddSearchKeyword: () -> Unit,
    onRemoveSearchKeyword: (Int) -> Unit,
    onSearchMatchAllChange: (Boolean) -> Unit,
    onSearchScopeChange: (FrameSearchScope) -> Unit,
    onPickSearchSinglePackage: (String) -> Unit,
    onPickSearchPackages: (Set<String>) -> Unit,
    onLoadSearchCategories: () -> Unit,
    onSearchStrictChange: (Boolean) -> Unit,
    onRunSearch: () -> Unit,
    onToggleSort: () -> Unit,
    onJumpToHit: (Int) -> Unit,
    onLoadMoreNearEnd: () -> Unit,
    onSearchDateModeChange: (FrameSearchDateMode) -> Unit,
    onSearchSingleDayChange: (Long) -> Unit,
    onSearchDateRangeChange: (Long, Long) -> Unit,
    onAppClick: (AppUsageItem) -> Unit,
    onOpenSettings: () -> Unit,
    iconLoader: (String) -> Drawable?,
) {
    var showSearchDialog by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var appPickerSingle by remember { mutableStateOf(false) }
    val singleSearchLabel = remember(searchScope, searchPackages, searchCategories) {
        if (searchScope != FrameSearchScope.CURRENT) {
            null
        } else {
            val pkg = searchPackages.firstOrNull() ?: return@remember null
            searchCategories.asSequence()
                .flatMap { it.apps.asSequence() }
                .firstOrNull { it.packageName == pkg }
                ?.label
                ?: pkg
        }
    }

    if (showSearchDialog) {
        FrameSearchDialog(
            query = searchQuery,
            multi = searchMulti,
            keywords = searchKeywords,
            matchAll = searchMatchAll,
            searchScope = searchScope,
            singlePackageLabel = singleSearchLabel,
            pickedCount = if (searchScope == FrameSearchScope.PICKED) searchPackages.size else 0,
            strict = searchStrict,
            hits = searchHits,
            hitIndex = searchHitIndex,
            loading = searchLoading,
            loadingMore = searchLoadingMore,
            hasMore = searchHasMore,
            total = searchTotal,
            message = searchMessage,
            indexProgress = indexProgress,
            sortDescending = searchSortDescending,
            dateMode = searchDateMode,
            singleEpochDay = searchSingleEpochDay,
            rangeStartEpochDay = searchRangeStartEpochDay,
            rangeEndEpochDay = searchRangeEndEpochDay,
            onQueryChange = onSearchQueryChange,
            onMultiChange = onSearchMultiChange,
            onKeywordChange = onSearchKeywordChange,
            onAddKeyword = onAddSearchKeyword,
            onRemoveKeyword = onRemoveSearchKeyword,
            onMatchAllChange = onSearchMatchAllChange,
            onOpenSinglePicker = {
                onLoadSearchCategories()
                appPickerSingle = true
                showAppPicker = true
            },
            onScopeAll = { onSearchScopeChange(FrameSearchScope.ALL) },
            onOpenMultiPicker = {
                onLoadSearchCategories()
                appPickerSingle = false
                showAppPicker = true
            },
            onStrictChange = onSearchStrictChange,
            onDateModeChange = onSearchDateModeChange,
            onSingleDayChange = onSearchSingleDayChange,
            onDateRangeChange = onSearchDateRangeChange,
            onToggleSort = onToggleSort,
            onSearch = onRunSearch,
            onJump = { i ->
                onJumpToHit(i)
                showSearchDialog = false
            },
            onLoadMoreNearEnd = onLoadMoreNearEnd,
            onDismiss = { showSearchDialog = false },
        )
    }

    if (showAppPicker) {
        FrameAppScopePicker(
            categories = searchCategories,
            loading = searchCategoriesLoading,
            initialSelected = if (appPickerSingle) {
                searchPackages.take(1).toSet()
            } else {
                searchPackages
            },
            iconLoader = iconLoader,
            singleSelect = appPickerSingle,
            onDismiss = { showAppPicker = false },
            onConfirm = { packages ->
                if (appPickerSingle) {
                    packages.firstOrNull()?.let(onPickSearchSinglePackage)
                } else if (packages.isNotEmpty()) {
                    onPickSearchPackages(packages)
                }
                showAppPicker = false
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBackground)
            .statusBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(
                text = "画面记录",
                color = TextStrong,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                onOpenSearch()
                showSearchDialog = true
            }) {
                Icon(Icons.Outlined.Search, contentDescription = "搜索画面", tint = Accent)
            }
        }

        when {
            loading && categories.isEmpty() -> {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = Accent)
                    Spacer(Modifier.height(12.dp))
                    Text("加载有画面的应用…", color = TextMuted)
                }
            }
            categories.isEmpty() -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "还没有画面记录",
                        color = TextStrong,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "请到设置中开启「画面记录」与无障碍权限。",
                        color = TextMuted,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onOpenSettings) {
                        Text("去设置", color = Accent)
                    }
                }
            }
            else -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    categories.forEach { cat ->
                        FrameBrowseCategorySection(
                            category = cat,
                            iconLoader = iconLoader,
                            onAppClick = onAppClick,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun FrameBrowseCategorySection(
    category: FrameBrowseCategory,
    iconLoader: (String) -> Drawable?,
    onAppClick: (AppUsageItem) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(
                Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(category.categoryColor),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = category.categoryName,
                color = TextStrong,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.size(6.dp))
        Text(
            text = "近 7 天 ${category.totalFrameCount} 帧 · ${category.apps.size} 个应用",
            color = TextMuted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.size(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardBackground)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            AppUsageGrid(
                apps = category.apps,
                iconLoader = iconLoader,
                defaultLimit = 50,
                onAppClick = onAppClick,
                valueText = { "${it.millis} 帧" },
            )
        }
    }
}
