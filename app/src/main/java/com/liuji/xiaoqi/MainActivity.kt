package com.liuji.xiaoqi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.liuji.xiaoqi.data.CategoryColors
import com.liuji.xiaoqi.ui.AppScreen
import com.liuji.xiaoqi.ui.CategoryEditorMode
import com.liuji.xiaoqi.ui.DataSource
import com.liuji.xiaoqi.ui.ExportEvent
import com.liuji.xiaoqi.ui.PhoneBottomTab
import com.liuji.xiaoqi.ui.PhoneHomeScaffold
import com.liuji.xiaoqi.ui.UsageUiState
import com.liuji.xiaoqi.ui.UsageViewModel
import com.liuji.xiaoqi.ui.categories.AddAppPickerSheet
import com.liuji.xiaoqi.ui.categories.CategoriesScreen
import com.liuji.xiaoqi.ui.categories.CategoryEditorDialog
import com.liuji.xiaoqi.ui.categories.DeleteCategoryConfirmDialog
import com.liuji.xiaoqi.ui.category.CategoryUsageDetailScreen
import com.liuji.xiaoqi.ui.dashboard.DashboardScreen
import com.liuji.xiaoqi.ui.dashboard.PermissionScreen
import com.liuji.xiaoqi.ui.frame.FrameViewerScreen
import com.liuji.xiaoqi.ui.hindsight.HindsightAppDetailScreen
import com.liuji.xiaoqi.ui.hindsight.HindsightCloudScreen
import com.liuji.xiaoqi.ui.settings.RuntimeLogScreen
import com.liuji.xiaoqi.ui.settings.SettingsScreen
import com.liuji.xiaoqi.ima.ImaSyncPrefs
import com.liuji.xiaoqi.ima.ImaSyncRepository
import com.liuji.xiaoqi.ima.ImaSyncScheduler
import com.liuji.xiaoqi.log.AppLog
import com.liuji.xiaoqi.log.RuntimeLogStore
import com.liuji.xiaoqi.ui.sync.CloudSyncScreen
import com.liuji.xiaoqi.ui.theme.留迹Theme
import kotlinx.coroutines.CoroutineScope

class MainActivity : ComponentActivity() {
    /** 调试用：adb am start … --es seed_ima_client_id … --es seed_ima_api_key … --es seed_ima_kb … [--ez seed_ima_sync true] */
    private fun maybeSeedImaFromIntent(intent: android.content.Intent?) {
        val clientId = intent?.getStringExtra("seed_ima_client_id") ?: return
        val apiKey = intent.getStringExtra("seed_ima_api_key") ?: return
        if (clientId.isBlank() || apiKey.isBlank()) return
        val kb = intent.getStringExtra("seed_ima_kb")?.ifBlank { null } ?: "使用记录"
        AppLog.i("ImaSync", "seed intent: kb=$kb auto=${intent.getBooleanExtra("seed_ima_auto", false)} sync=${intent.getBooleanExtra("seed_ima_sync", false)}")
        val prefs = ImaSyncPrefs(this)
        prefs.saveCredentials(clientId, apiKey, kb)
        prefs.setKnowledgeBaseId("")
        if (intent.getBooleanExtra("seed_ima_reset_days", false)) {
            prefs.clearAllDayStates()
        }
        if (intent.getBooleanExtra("seed_ima_poison_days", false)) {
            val n = prefs.poisonDayNoteIds()
            AppLog.i("ImaSync", "poisoned day states: $n")
        }
        prefs.saveAuto(intent.getBooleanExtra("seed_ima_auto", false))
        ImaSyncScheduler.reconcile(this)
        if (intent.getBooleanExtra("seed_ima_sync", false)) {
            CoroutineScope(Dispatchers.IO).launch {
                val report = runCatching {
                    ImaSyncRepository(this@MainActivity).syncIncremental(forceOverwrite = true)
                }
                    .onFailure { AppLog.e("ImaSync", "seed sync failed", it) }
                    .getOrNull()
                AppLog.i("ImaSync", "seed sync done: ${report?.message}")
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeSeedImaFromIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 日志文件 / 加密偏好 / WorkManager 放到 IO，避免阻塞首帧
        lifecycleScope.launch(Dispatchers.IO) {
            RuntimeLogStore.init(applicationContext)
            AppLog.i("留迹", "应用启动")
            ImaSyncScheduler.reconcile(applicationContext)
        }
        maybeSeedImaFromIntent(intent)
        setContent {
            留迹Theme {
                val viewModel: UsageViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                val lifecycleOwner = LocalLifecycleOwner.current

                val canShowDashboard =
                    state.hasPermission || state.dataSource == DataSource.HINDSIGHT_CLOUD

                BackHandler(
                    enabled = canShowDashboard && (
                        state.showExportDialog ||
                            state.screen != AppScreen.DASHBOARD ||
                            state.screen == AppScreen.RUNTIME_LOG ||
                            state.dataSource == DataSource.HINDSIGHT_CLOUD ||
                            state.timelineDrillStack.isNotEmpty()
                        ),
                ) {
                    viewModel.handleBack()
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            viewModel.onAppResume()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                LaunchedEffect(viewModel) {
                    viewModel.exportEvents.collect { event ->
                        when (event) {
                            is ExportEvent.Share -> {
                                val chooser = android.content.Intent.createChooser(
                                    event.intent,
                                    "导出使用数据 Markdown",
                                ).apply {
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { startActivity(chooser) }
                                    .onFailure {
                                        viewModel.consumeMessage()
                                        // 无可用分享目标时给用户可见提示
                                        android.widget.Toast.makeText(
                                            this@MainActivity,
                                            "无法打开分享：${it.message ?: "无可用应用"}",
                                            android.widget.Toast.LENGTH_LONG,
                                        ).show()
                                    }
                            }
                        }
                    }
                }

                when {
                    state.screen == AppScreen.RUNTIME_LOG -> {
                        RuntimeLogScreen(onBack = viewModel::openSettings)
                    }

                    state.screen == AppScreen.SETTINGS -> {
                        SettingsScreen(
                            frameEnabled = state.frameRecordingEnabled,
                            frameInterval = state.frameInterval,
                            accessibilityEnabled = state.accessibilityEnabled,
                            accessibilityLegacyListed = state.accessibilityLegacyListed,
                            frameCount = state.frameCount,
                            webDavAccount = state.webDavAccount,
                            hindsightConfigured = state.hindsightConfigured,
                            hindsightLastSync = state.hindsightLastSync,
                            cloudSyncing = state.cloudSyncing,
                            onBack = viewModel::openDashboard,
                            onToggleFrame = viewModel::setFrameRecordingEnabled,
                            onIntervalChange = viewModel::setFrameInterval,
                            onOpenAccessibility = {
                                startActivity(viewModel.openAccessibilitySettings())
                            },
                            onRebuildUsage = viewModel::rebuildUsageHistory,
                            usageRebuilding = state.usageRebuilding,
                            onSaveWebDav = viewModel::saveWebDavAccount,
                            onSyncCloud = viewModel::syncHindsightCloud,
                            onOpenRuntimeLog = viewModel::openRuntimeLog,
                            frameBlacklist = state.frameBlacklist,
                            frameBlacklistPending = state.frameBlacklistPending,
                            frameBlacklistCandidates = state.frameBlacklistCandidates,
                            frameBlacklistSearch = state.frameBlacklistSearch,
                            frameBlacklistPickerOpen = state.frameBlacklistPickerOpen,
                            onOpenBlacklistPicker = viewModel::openFrameBlacklistPicker,
                            onDismissBlacklistPicker = viewModel::dismissFrameBlacklistPicker,
                            onBlacklistSearchChange = viewModel::setFrameBlacklistSearch,
                            onAddBlacklist = viewModel::addToFrameBlacklist,
                            onRemoveBlacklist = viewModel::removeFromFrameBlacklist,
                            onConfirmBlacklistPending = viewModel::confirmFrameBlacklistPending,
                            onDismissBlacklistPending = viewModel::dismissFrameBlacklistPending,
                            privacyApps = state.privacyApps,
                            privacyAppCandidates = state.privacyAppCandidates,
                            privacyAppSearch = state.privacyAppSearch,
                            privacyAppPickerOpen = state.privacyAppPickerOpen,
                            onOpenPrivacyAppPicker = viewModel::openPrivacyAppPicker,
                            onDismissPrivacyAppPicker = viewModel::dismissPrivacyAppPicker,
                            onPrivacyAppSearchChange = viewModel::setPrivacyAppSearch,
                            onAddPrivacyApps = viewModel::addPrivacyApps,
                            onRemovePrivacyApp = viewModel::removePrivacyApp,
                            iconLoader = viewModel::appIcon,
                        )
                    }

                    state.screen == AppScreen.FRAME_VIEWER -> {
                        FrameViewerScreen(
                            appLabel = state.frameAppLabel,
                            packageName = state.frameAppPackage.orEmpty(),
                            frames = state.frames,
                            index = state.frameIndex,
                            loading = state.framesLoading,
                            searchQuery = state.frameSearchQuery,
                            searchScope = state.frameSearchScope,
                            searchPackages = state.frameSearchPackages,
                            searchCategories = state.frameSearchCategories,
                            searchCategoriesLoading = state.frameSearchCategoriesLoading,
                            searchStrict = state.frameSearchStrict,
                            searchHits = state.frameSearchHits,
                            searchHitIndex = state.frameSearchHitIndex,
                            searchLoading = state.frameSearchLoading,
                            searchMessage = state.frameSearchMessage,
                            onBack = viewModel::openDashboard,
                            onIndexChange = viewModel::setFrameIndex,
                            onSearchQueryChange = viewModel::setFrameSearchQuery,
                            onSearchScopeChange = viewModel::setFrameSearchScope,
                            onPickSearchPackages = viewModel::setFrameSearchPackages,
                            onLoadSearchCategories = viewModel::loadFrameSearchCategories,
                            onSearchStrictChange = viewModel::setFrameSearchStrict,
                            onRunSearch = viewModel::runFrameSearch,
                            onJumpToSearchHit = viewModel::jumpToFrameSearchHit,
                            onSearchPrev = viewModel::frameSearchPrev,
                            onSearchNext = viewModel::frameSearchNext,
                            onLoadDateFrames = viewModel::applyFrameDateFilter,
                            onReloadPeriodFrames = viewModel::clearFrameDateFilter,
                            iconLoader = viewModel::appIcon,
                        )
                    }

                    !state.hasPermission && state.dataSource == DataSource.PHONE -> {
                        PermissionScreen(
                            onOpenSettings = {
                                startActivity(viewModel.openUsageAccessSettings())
                            },
                            onRefresh = { viewModel.refreshPermissionAndData() },
                            onOpenAppSettings = viewModel::openSettings,
                            onUseCloudBoard = {
                                viewModel.setDataSource(DataSource.HINDSIGHT_CLOUD)
                            },
                        )
                    }

                    state.screen == AppScreen.HINDSIGHT_APP_DETAIL -> {
                        HindsightAppDetailScreen(
                            detail = state.hindsightAppDetail,
                            loading = state.hindsightAppDetailLoading,
                            iconLoader = viewModel::hindsightAppIcon,
                            onClose = viewModel::closeHindsightAppDetail,
                        )
                    }

                    state.dataSource == DataSource.HINDSIGHT_CLOUD -> {
                        HindsightCloudScreen(
                            state = state,
                            deviceLabel = state.hindsightDeviceLabel,
                            onPeriodChange = viewModel::setPeriod,
                            onPrev = { viewModel.shiftOffset(-1) },
                            onNext = { viewModel.shiftOffset(1) },
                            onJumpCurrent = viewModel::jumpToCurrent,
                            onJumpToDate = viewModel::jumpToDate,
                            onSelectHour = viewModel::selectHour,
                            onRefresh = viewModel::forceRefresh,
                            onOpenSettings = viewModel::openSettings,
                            onSwitchToPhone = { viewModel.setDataSource(DataSource.PHONE) },
                            onMessageShown = viewModel::consumeMessage,
                            onAppClick = viewModel::openHindsightAppDetail,
                            onDashboardAppQuery = viewModel::setDashboardAppQuery,
                            onScroll = viewModel::setHindsightListScroll,
                            iconLoader = viewModel::hindsightAppIcon,
                        )
                    }

                    else -> {
                        PhoneHomeScaffold(
                            bottomTab = state.phoneBottomTab,
                            onBottomTab = viewModel::selectPhoneBottomTab,
                        ) {
                            when (state.screen) {
                                AppScreen.CATEGORY_USAGE_DETAIL -> {
                                    val detail = state.categoryUsageDetail
                                    if (detail != null) {
                                        CategoryUsageDetailScreen(
                                            categoryName = detail.categoryName,
                                            categoryColor = detail.categoryColor,
                                            totalMillis = detail.totalMillis,
                                            apps = detail.apps,
                                            iconLoader = viewModel::appIcon,
                                            onBack = viewModel::closeCategoryUsageDetail,
                                            onAppClick = viewModel::openAppFrames,
                                        )
                                    }
                                }
                                else -> when (state.phoneBottomTab) {
                                    PhoneBottomTab.CATEGORIES -> {
                                        CategoriesScreen(
                                            loading = state.categoriesUi.loading,
                                            categoriesWithApps = state.categoriesUi.categoriesWithApps,
                                            listFirstVisibleIndex = state.categoriesUi.listFirstVisibleIndex,
                                            listFirstVisibleOffset = state.categoriesUi.listFirstVisibleOffset,
                                            scrollToCategoryId = state.categoriesUi.scrollToCategoryId,
                                            onScroll = viewModel::setCategoriesScroll,
                                            onScrollToCategoryConsumed = viewModel::consumeScrollToCategory,
                                            onCreateCategory = viewModel::openCreateCategory,
                                            onEditCategory = viewModel::openEditCategory,
                                            onDeleteCategory = viewModel::requestDeleteCategory,
                                            onAddAppToCategory = viewModel::openAddAppToCategory,
                                            onRemoveAppFromCategory = viewModel::removeAppFromCategory,
                                            iconLoader = viewModel::appIcon,
                                        )
                                    }
                                    PhoneBottomTab.CLOUD_SYNC -> {
                                        CloudSyncScreen()
                                    }
                                    else -> {
                                        DashboardScreen(
                                            state = state,
                                            onPeriodChange = viewModel::setPeriod,
                                            onShiftPeriod = viewModel::shiftPeriod,
                                            onPrev = { viewModel.shiftOffset(-1) },
                                            onNext = { viewModel.shiftOffset(1) },
                                            onJumpCurrent = viewModel::jumpToCurrent,
                                            onJumpToDate = viewModel::jumpToDate,
                                            onSelectHour = viewModel::selectHour,
                                            onOpenExport = viewModel::openExportDialog,
                                            onDismissExport = viewModel::dismissExportDialog,
                                            onExportPeriod = { period, includeScreenTexts ->
                                                viewModel.exportPeriod(period, includeScreenTexts)
                                            },
                                            onExportDateRange = viewModel::exportDateRange,
                                            onMessageShown = viewModel::consumeMessage,
                                            onOpenSettings = viewModel::openSettings,
                                            onDayViewMode = viewModel::setDayViewMode,
                                            onTimelineGranularity = viewModel::setTimelineGranularity,
                                            onTimelineBucketClick = viewModel::drillIntoTimelineBucket,
                                            onTimelineDrillUp = viewModel::timelineDrillUp,
                                            onAppClick = viewModel::openAppFrames,
                                            onRefresh = viewModel::forceRefresh,
                                            onDataSource = viewModel::setDataSource,
                                            onDashboardAppQuery = viewModel::setDashboardAppQuery,
                                            onScroll = viewModel::setDashboardScroll,
                                            iconLoader = viewModel::appIcon,
                                            onCategoryClick = viewModel::openCategoryUsageDetail,
                                        )
                                    }
                                }
                            }
                        }
                        CategoryManagementDialogs(state = state, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun CategoryManagementDialogs(state: UsageUiState, viewModel: UsageViewModel) {
    val catUi = state.categoriesUi
    when (val editor = catUi.editor) {
        CategoryEditorMode.Create -> {
            CategoryEditorDialog(
                title = "新建分类",
                initialName = "",
                initialColor = CategoryColors.pickerPresets.first(),
                onDismiss = viewModel::dismissCategoryEditor,
                onConfirm = viewModel::saveCategoryEditor,
            )
        }
        is CategoryEditorMode.Edit -> {
            CategoryEditorDialog(
                title = "编辑分类",
                initialName = editor.category.name,
                initialColor = editor.category.color,
                onDismiss = viewModel::dismissCategoryEditor,
                onConfirm = viewModel::saveCategoryEditor,
            )
        }
        null -> Unit
    }
    catUi.deleteTarget?.let { target ->
        DeleteCategoryConfirmDialog(
            categoryName = target.name,
            appCount = target.appCount,
            onDismiss = viewModel::dismissDeleteCategory,
            onConfirm = viewModel::confirmDeleteCategory,
        )
    }
    catUi.addAppTarget?.let { target ->
        AddAppPickerSheet(
            categoryName = target.name,
            candidates = catUi.allApps.filter { it.categoryId != target.id },
            searchQuery = catUi.addAppSearchQuery,
            onSearchChange = viewModel::setAddAppSearch,
            onDismiss = viewModel::dismissAddAppPicker,
            onConfirm = viewModel::addAppsToCategory,
            iconLoader = viewModel::appIcon,
        )
    }
}
