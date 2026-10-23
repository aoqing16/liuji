package com.liuji.xiaoqi.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.liuji.xiaoqi.data.AppCategoryAssignment
import com.liuji.xiaoqi.data.AppUsageItem
import com.liuji.xiaoqi.data.CategoryUsageItem
import com.liuji.xiaoqi.data.CategoryWithApps
import com.liuji.xiaoqi.data.HindsightAppDetail
import com.liuji.xiaoqi.data.PeriodSummary
import com.liuji.xiaoqi.data.StatsPeriod
import com.liuji.xiaoqi.data.UsageRepository
import com.liuji.xiaoqi.data.UserCategoryWithCount
import com.liuji.xiaoqi.data.db.PrivacyAppEntity
import com.liuji.xiaoqi.data.export.UsageExportWriter
import com.liuji.xiaoqi.data.frame.FrameInterval
import com.liuji.xiaoqi.data.frame.FrameSearchHit
import com.liuji.xiaoqi.data.frame.TimelineItem
import com.liuji.xiaoqi.data.frame.UiFrameSnapshot
import com.liuji.xiaoqi.frame.FrameBlacklistItem
import com.liuji.xiaoqi.frame.FrameBlacklistStore
import com.liuji.xiaoqi.frame.FrameCaptureAccessibilityService
import com.liuji.xiaoqi.frame.FrameRecorderService
import com.liuji.xiaoqi.hindsight.HindsightAggregator
import com.liuji.xiaoqi.hindsight.HindsightIconStore
import com.liuji.xiaoqi.hindsight.HindsightSync
import com.liuji.xiaoqi.hindsight.WebDavAccount
import com.liuji.xiaoqi.hindsight.db.HindsightDatabase
import com.liuji.xiaoqi.ima.ImaSyncPrefs
import com.liuji.xiaoqi.ima.ImaSyncRepository
import com.liuji.xiaoqi.log.AppLog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class FrameSearchScope {
    CURRENT,
    PICKED,
    ALL,
}

enum class AppScreen {
    DASHBOARD,
    SETTINGS,
    RUNTIME_LOG,
    FRAME_VIEWER,
    HINDSIGHT_APP_DETAIL,
    CATEGORY_USAGE_DETAIL,
}

data class CategoryUsageDetailState(
    val categoryId: String,
    val categoryName: String,
    val categoryColor: androidx.compose.ui.graphics.Color,
    val totalMillis: Long,
    val apps: List<AppUsageItem>,
)

sealed interface CategoryEditorMode {
    data object Create : CategoryEditorMode
    data class Edit(val category: UserCategoryWithCount) : CategoryEditorMode
}

data class CategoriesUiState(
    val loading: Boolean = true,
    val categoriesWithApps: List<CategoryWithApps> = emptyList(),
    val allApps: List<AppCategoryAssignment> = emptyList(),
    val addAppSearchQuery: String = "",
    val editor: CategoryEditorMode? = null,
    val deleteTarget: UserCategoryWithCount? = null,
    val addAppTarget: UserCategoryWithCount? = null,
    /** 分类页 LazyColumn 滚动位置 */
    val listFirstVisibleIndex: Int = 0,
    val listFirstVisibleOffset: Int = 0,
    /** 新建后滚到该分类；UI 消费后清空 */
    val scrollToCategoryId: String? = null,
)

enum class DayViewMode {
    CHART,
    TIMELINE,
}

/** 时间戳视图颗粒度：时/分堆叠同档 App，秒为进入→离开会话 */
enum class TimelineGranularity {
    HOUR,
    MINUTE,
    SECOND,
}

/** 从堆叠时段钻入后的一级：以该粒度展示 [rangeStart, rangeEnd) */
data class TimelineDrillLevel(
    val granularity: TimelineGranularity,
    val rangeStart: Long,
    val rangeEnd: Long,
)

/** 看板数据来源：本机 UsageStats / 电脑 Hindsight 云端只读缓存 */
enum class DataSource {
    PHONE,
    HINDSIGHT_CLOUD,
}

data class UsageUiState(
    val hasPermission: Boolean = false,
    val screen: AppScreen = AppScreen.DASHBOARD,
    val dataSource: DataSource = DataSource.PHONE,
    val period: StatsPeriod = StatsPeriod.DAY,
    val offset: Int = 0,
    val dayViewMode: DayViewMode = DayViewMode.CHART,
    val timelineGranularity: TimelineGranularity = TimelineGranularity.SECOND,
    val timelineDrillStack: List<TimelineDrillLevel> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val cloudSyncing: Boolean = false,
    val usageRebuilding: Boolean = false,
    val exporting: Boolean = false,
    val showExportDialog: Boolean = false,
    val summary: PeriodSummary? = null,
    val timeline: List<TimelineItem> = emptyList(),
    val timelineLoading: Boolean = false,
    val selectedHour: Int? = null,
    val error: String? = null,
    val message: String? = null,
    // settings
    val frameRecordingEnabled: Boolean = false,
    val frameInterval: FrameInterval = FrameInterval.S30,
    val accessibilityEnabled: Boolean = false,
    /** 系统仍挂着升级前的旧无障碍组件，需引导改开「留迹画面记录」 */
    val accessibilityLegacyListed: Boolean = false,
    val frameCount: Long = 0,
    val frameBlacklist: List<FrameBlacklistItem> = emptyList(),
    val frameBlacklistPending: List<FrameBlacklistItem> = emptyList(),
    val frameBlacklistPickerOpen: Boolean = false,
    val frameBlacklistSearch: String = "",
    val frameBlacklistCandidates: List<AppCategoryAssignment> = emptyList(),
    val privacyApps: List<PrivacyAppEntity> = emptyList(),
    val privacyAppPickerOpen: Boolean = false,
    val privacyAppSearch: String = "",
    val privacyAppCandidates: List<AppCategoryAssignment> = emptyList(),
    val webDavAccount: WebDavAccount = WebDavAccount(),
    val hindsightLastSync: String? = null,
    val hindsightConfigured: Boolean = false,
    val hindsightDeviceLabel: String = "Windows",
    /** 图标预加载完成后递增，驱动 Compose 刷新 */
    val hindsightIconEpoch: Int = 0,
    // frame viewer
    val frameAppPackage: String? = null,
    val frameAppLabel: String = "",
    val frames: List<UiFrameSnapshot> = emptyList(),
    val frameIndex: Int = 0,
    val framesLoading: Boolean = false,
    val frameSearchQuery: String = "",
    val frameSearchScope: FrameSearchScope = FrameSearchScope.CURRENT,
    val frameSearchPackages: Set<String> = emptySet(),
    val frameSearchCategories: List<CategoryWithApps> = emptyList(),
    val frameSearchCategoriesLoading: Boolean = false,
    val frameSearchStrict: Boolean = false,
    val frameSearchHits: List<FrameSearchHit> = emptyList(),
    val frameSearchHitIndex: Int = 0,
    val frameSearchLoading: Boolean = false,
    val frameSearchMessage: String? = null,
    // hindsight app detail
    val hindsightAppDetail: HindsightAppDetail? = null,
    val hindsightAppDetailLoading: Boolean = false,
    val categoriesUi: CategoriesUiState = CategoriesUiState(),
    val phoneBottomTab: PhoneBottomTab = PhoneBottomTab.DAY,
    val categoryUsageDetail: CategoryUsageDetailState? = null,
    /** 看板 LazyColumn 滚动位置（二级页返回后恢复） */
    val dashboardFirstVisibleIndex: Int = 0,
    val dashboardFirstVisibleOffset: Int = 0,
    val hindsightListFirstVisibleIndex: Int = 0,
    val hindsightListFirstVisibleOffset: Int = 0,
    /** 首页/电脑看板按应用名或包名过滤 */
    val dashboardAppQuery: String = "",
)

val UsageUiState.effectiveTimelineGranularity: TimelineGranularity
    get() = timelineDrillStack.lastOrNull()?.granularity ?: timelineGranularity

sealed interface ExportEvent {
    data class Share(val intent: Intent) : ExportEvent
}

class UsageViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UsageRepository(application)
    private val hindsightSync = HindsightSync(application)
    private val hindsightAggregator = HindsightAggregator(application)
    private val hindsightIcons = HindsightIconStore(application)

    private val _state = MutableStateFlow(UsageUiState())
    val state: StateFlow<UsageUiState> = _state.asStateFlow()

    private val _exportEvents = MutableSharedFlow<ExportEvent>(extraBufferCapacity = 1)
    val exportEvents: SharedFlow<ExportEvent> = _exportEvents.asSharedFlow()

    private var loadJob: Job? = null
    private var timelineJob: Job? = null
    private var exportJob: Job? = null
    private var cloudJob: Job? = null
    private var settingsJob: Job? = null
    /** 当前 timeline 对应的日 offset；避免重复加载 */
    private var timelineLoadedOffset: Int? = null
    /** 最近一次看板成功加载的本地日 / 时刻，供回前台判断是否过期 */
    private var lastLoadedLocalDate: java.time.LocalDate? = null
    private var lastLoadedAt: Long = 0L

    init {
        // refreshPermissionAndData() 内部已调用 loadSettings()，避免启动期双倍 Room IO
        refreshPermissionAndData()
    }

    fun refreshPermissionAndData() {
        val granted = repository.hasUsageAccess()
        val app = getApplication<Application>()
        _state.update {
            it.copy(
                hasPermission = granted,
                accessibilityEnabled = FrameCaptureAccessibilityService.isEnabled(app),
                accessibilityLegacyListed = FrameCaptureAccessibilityService.isLegacyServiceListed(app),
            )
        }
        loadSettings()
        if (_state.value.dataSource == DataSource.HINDSIGHT_CLOUD) {
            reload()
        } else if (granted) {
            reload()
        } else {
            _state.update { it.copy(loading = false, summary = null) }
        }
    }

    fun setDataSource(source: DataSource) {
        if (_state.value.dataSource == source) return
        timelineLoadedOffset = null
        _state.update {
            it.copy(
                dataSource = source,
                screen = AppScreen.DASHBOARD,
                selectedHour = null,
                dayViewMode = DayViewMode.CHART,
                timeline = emptyList(),
                timelineDrillStack = emptyList(),
                summary = null,
                loading = true,
                hindsightAppDetail = null,
                hindsightAppDetailLoading = false,
            )
        }
        if (source == DataSource.HINDSIGHT_CLOUD) {
            val account = hindsightSync.account()
            if (!account.configured) {
                _state.update {
                    it.copy(
                        loading = false,
                        message = "请先在设置中填写云盘账号与应用密码",
                    )
                }
                openSettings()
                return
            }
            viewModelScope.launch {
                val hasCache = withContext(Dispatchers.IO) { hindsightSync.hasCachedData() }
                if (!hasCache) {
                    syncHindsightCloud()
                } else {
                    reload()
                }
            }
            return
        }
        reload()
    }

    /** 下拉刷新：本机重拉 UsageStats；电脑模式只读拉取 WebDAV */
    fun forceRefresh() {
        if (_state.value.refreshing || _state.value.cloudSyncing) return
        when (_state.value.dataSource) {
            DataSource.HINDSIGHT_CLOUD -> syncHindsightCloud()
            DataSource.PHONE -> {
                if (!_state.value.hasPermission) return
                timelineLoadedOffset = null
                loadJob?.cancel()
                loadJob = viewModelScope.launch {
                    _state.update { it.copy(refreshing = true, error = null, message = null) }
                    val period = _state.value.period
                    val offset = _state.value.offset
                    runCatching {
                        withContext(Dispatchers.IO) {
                            repository.loadSummary(period, offset)
                        }
                    }.onSuccess { summary ->
                        markLoaded()
                        _state.update {
                            it.copy(
                                loading = false,
                                refreshing = false,
                                summary = summary,
                                error = null,
                                message = "已更新使用记录",
                            )
                        }
                        if (period == StatsPeriod.DAY) {
                            loadTimeline(sync = true)
                        }
                    }.onFailure { e ->
                        _state.update {
                            it.copy(
                                loading = false,
                                refreshing = false,
                                error = e.message ?: "刷新失败",
                            )
                        }
                    }
                }
            }
        }
    }

    fun syncHindsightCloud() {
        if (_state.value.cloudSyncing) return
        cloudJob?.cancel()
        cloudJob = viewModelScope.launch {
            _state.update {
                it.copy(cloudSyncing = true, refreshing = true, error = null, message = null)
            }
            runCatching {
                withContext(Dispatchers.IO) { hindsightSync.pullReadOnly(lookbackDays = 60) }
            }.onSuccess { result ->
                hindsightIcons.clearMemory()
                com.liuji.xiaoqi.ui.components.IconBitmapCache.clear()
                _state.update {
                    it.copy(
                        cloudSyncing = false,
                        refreshing = false,
                        message = result.message,
                        hindsightLastSync = java.time.Instant.now().toString(),
                        hindsightIconEpoch = it.hindsightIconEpoch + 1,
                    )
                }
                if (_state.value.dataSource == DataSource.HINDSIGHT_CLOUD) {
                    reload()
                }
                loadSettings()
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        cloudSyncing = false,
                        refreshing = false,
                        error = e.message ?: "云端同步失败",
                    )
                }
            }
        }
    }

    fun saveWebDavAccount(url: String, user: String, password: String, rootPath: String) {
        val existing = hindsightSync.account()
        val next = WebDavAccount(
            url = url.ifBlank { WebDavAccount.DEFAULT_URL },
            user = user.trim(),
            password = password.ifBlank { existing.password },
            rootPath = rootPath.ifBlank { WebDavAccount.DEFAULT_ROOT },
        )
        hindsightSync.saveAccount(next)
        _state.update {
            it.copy(
                webDavAccount = next.copy(password = ""),
                hindsightConfigured = next.configured,
                message = "已保存云盘账号（只读拉取，不会写入云盘）",
            )
        }
        loadSettings()
    }

    /** Activity onResume：权限必刷；本机今日数据过期或跨日则静默重载；AI对话补传已完成日 */
    fun onAppResume() {
        val granted = repository.hasUsageAccess()
        val app = getApplication<Application>()
        val a11y = FrameCaptureAccessibilityService.isEnabled(app)
        val legacy = FrameCaptureAccessibilityService.isLegacyServiceListed(app)
        _state.update {
            it.copy(
                hasPermission = granted,
                accessibilityEnabled = a11y,
                accessibilityLegacyListed = legacy,
            )
        }
        val s = _state.value
        when (s.dataSource) {
            DataSource.PHONE -> {
                if (granted && shouldReloadOnResume(s)) reload()
            }
            DataSource.HINDSIGHT_CLOUD -> {
                if (s.summary == null) reload()
            }
        }
        loadSettings()
        maybeCatchUpAiSync()
    }

    /** 自动同步开启时，打开 App 补传未上传的已完成日（防 WorkManager 不准点） */
    private fun maybeCatchUpAiSync() {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = ImaSyncPrefs(getApplication())
            val cfg = prefs.loadConfig()
            if (!cfg.autoEnabled || !prefs.isConfigured()) return@launch
            runCatching {
                ImaSyncRepository(getApplication()).syncIncremental(forceOverwrite = false)
            }.onFailure { AppLog.w("ImaSync", "resume catch-up", it) }
        }
    }

    private fun shouldReloadOnResume(s: UsageUiState): Boolean {
        if (s.summary == null) return true
        if (s.offset != 0) return false
        val today = java.time.LocalDate.now()
        val dayChanged = lastLoadedLocalDate != null && lastLoadedLocalDate != today
        val stale = lastLoadedAt > 0L &&
            System.currentTimeMillis() - lastLoadedAt >= RESUME_REFRESH_STALE_MS
        return dayChanged || stale
    }

    /** @return true 表示已消费返回（不退出 App） */
    fun handleBack(): Boolean {
        val s = _state.value
        return when {
            s.showExportDialog -> {
                dismissExportDialog()
                true
            }
            s.screen == AppScreen.HINDSIGHT_APP_DETAIL -> {
                closeHindsightAppDetail()
                true
            }
            s.screen == AppScreen.CATEGORY_USAGE_DETAIL -> {
                closeCategoryUsageDetail()
                true
            }
            s.screen == AppScreen.DASHBOARD &&
                s.dayViewMode == DayViewMode.TIMELINE &&
                s.timelineDrillStack.isNotEmpty() -> {
                timelineDrillUp()
                true
            }
            s.screen == AppScreen.RUNTIME_LOG -> {
                openSettings()
                true
            }
            s.screen == AppScreen.FRAME_VIEWER ||
                s.screen == AppScreen.SETTINGS -> {
                openDashboard()
                true
            }
            s.dataSource == DataSource.HINDSIGHT_CLOUD && s.screen == AppScreen.DASHBOARD -> {
                setDataSource(DataSource.PHONE)
                true
            }
            else -> false
        }
    }

    fun openSettings() {
        _state.update { it.copy(screen = AppScreen.SETTINGS) }
        loadSettings()
    }

    fun openRuntimeLog() {
        _state.update { it.copy(screen = AppScreen.RUNTIME_LOG) }
    }

    fun selectPhoneBottomTab(tab: PhoneBottomTab) {
        if (tab == PhoneBottomTab.CATEGORIES) {
            _state.update {
                it.copy(
                    phoneBottomTab = tab,
                    screen = AppScreen.DASHBOARD,
                    categoryUsageDetail = null,
                )
            }
            loadCategoriesData()
            return
        }
        if (tab == PhoneBottomTab.CLOUD_SYNC) {
            _state.update {
                it.copy(
                    phoneBottomTab = tab,
                    screen = AppScreen.DASHBOARD,
                    categoryUsageDetail = null,
                )
            }
            return
        }
        val period = tab.toStatsPeriod() ?: return
        _state.update { it.copy(phoneBottomTab = tab, screen = AppScreen.DASHBOARD) }
        if (_state.value.period != period) {
            setPeriod(period)
        }
    }

    fun setAddAppSearch(query: String) {
        _state.update { it.copy(categoriesUi = it.categoriesUi.copy(addAppSearchQuery = query)) }
    }

    fun openCategoryUsageDetail(item: CategoryUsageItem) {
        val summary = _state.value.summary ?: return
        val hour = if (_state.value.period == StatsPeriod.DAY) _state.value.selectedHour else null
        val apps = summary.appsForHour(hour)
            .filter { it.categoryId == item.categoryId }
            .sortedByDescending { it.millis }
        _state.update {
            it.copy(
                screen = AppScreen.CATEGORY_USAGE_DETAIL,
                categoryUsageDetail = CategoryUsageDetailState(
                    categoryId = item.categoryId,
                    categoryName = item.name,
                    categoryColor = item.color,
                    totalMillis = item.millis,
                    apps = apps,
                ),
            )
        }
    }

    fun closeCategoryUsageDetail() {
        _state.update {
            it.copy(screen = AppScreen.DASHBOARD, categoryUsageDetail = null)
        }
    }

    fun openCreateCategory() {
        _state.update { it.copy(categoriesUi = it.categoriesUi.copy(editor = CategoryEditorMode.Create)) }
    }

    fun openEditCategory(category: UserCategoryWithCount) {
        _state.update {
            it.copy(categoriesUi = it.categoriesUi.copy(editor = CategoryEditorMode.Edit(category)))
        }
    }

    fun dismissCategoryEditor() {
        _state.update { it.copy(categoriesUi = it.categoriesUi.copy(editor = null)) }
    }

    fun saveCategoryEditor(name: String, color: androidx.compose.ui.graphics.Color) {
        val mode = _state.value.categoriesUi.editor ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    when (mode) {
                        CategoryEditorMode.Create ->
                            repository.categories.upsertCategory(null, name, color)
                        is CategoryEditorMode.Edit ->
                            repository.categories.upsertCategory(mode.category.id, name, color)
                    }
                }
            }.onSuccess { categoryId ->
                dismissCategoryEditor()
                loadCategoriesData(
                    refreshDashboard = mode !is CategoryEditorMode.Create,
                    scrollToCategoryId = if (mode is CategoryEditorMode.Create) categoryId else null,
                )
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "保存分类失败") }
            }
        }
    }

    fun setCategoriesScroll(index: Int, offset: Int) {
        _state.update {
            it.copy(
                categoriesUi = it.categoriesUi.copy(
                    listFirstVisibleIndex = index,
                    listFirstVisibleOffset = offset,
                ),
            )
        }
    }

    fun consumeScrollToCategory() {
        _state.update {
            it.copy(categoriesUi = it.categoriesUi.copy(scrollToCategoryId = null))
        }
    }

    fun requestDeleteCategory(category: UserCategoryWithCount) {
        _state.update { it.copy(categoriesUi = it.categoriesUi.copy(deleteTarget = category)) }
    }

    fun dismissDeleteCategory() {
        _state.update { it.copy(categoriesUi = it.categoriesUi.copy(deleteTarget = null)) }
    }

    fun confirmDeleteCategory() {
        val target = _state.value.categoriesUi.deleteTarget ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.categories.deleteCategory(target.id) }
            }.onSuccess {
                dismissDeleteCategory()
                loadCategoriesData(refreshDashboard = true)
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "删除分类失败") }
            }
        }
    }

    fun openAddAppToCategory(category: UserCategoryWithCount) {
        _state.update {
            it.copy(
                categoriesUi = it.categoriesUi.copy(
                    addAppTarget = category,
                    addAppSearchQuery = "",
                ),
            )
        }
    }

    fun dismissAddAppPicker() {
        _state.update {
            it.copy(
                categoriesUi = it.categoriesUi.copy(
                    addAppTarget = null,
                    addAppSearchQuery = "",
                ),
            )
        }
    }

    fun addAppToCategory(app: AppCategoryAssignment) {
        addAppsToCategory(listOf(app))
    }

    fun addAppsToCategory(apps: List<AppCategoryAssignment>) {
        val target = _state.value.categoriesUi.addAppTarget ?: return
        if (apps.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    for (app in apps) {
                        repository.categories.assignApp(app.packageName, target.id, app.label)
                    }
                }
            }.onSuccess {
                dismissAddAppPicker()
                loadCategoriesData(refreshDashboard = true)
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "添加失败") }
            }
        }
    }

    fun removeAppFromCategory(category: UserCategoryWithCount, app: AppCategoryAssignment) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.categories.removeAppFromCategory(app.packageName, app.label)
                }
            }.onSuccess {
                loadCategoriesData(refreshDashboard = true)
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "移除失败") }
            }
        }
    }

    private fun loadCategoriesData(
        refreshDashboard: Boolean = false,
        scrollToCategoryId: String? = null,
    ) {
        viewModelScope.launch {
            val hadData = _state.value.categoriesUi.categoriesWithApps.isNotEmpty()
            // 已有列表时静默刷新，避免整页 Loading 白屏并丢掉滚动位置
            if (!hadData) {
                _state.update { it.copy(categoriesUi = it.categoriesUi.copy(loading = true)) }
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val withApps = repository.categories.listCategoriesWithApps()
                    val allApps = repository.categories.listInstalledAppsForPicker()
                    withApps to allApps
                }
            }.onSuccess { (withApps, allApps) ->
                _state.update {
                    it.copy(
                        categoriesUi = it.categoriesUi.copy(
                            loading = false,
                            categoriesWithApps = withApps,
                            allApps = allApps,
                            scrollToCategoryId = scrollToCategoryId
                                ?: it.categoriesUi.scrollToCategoryId,
                        ),
                    )
                }
                if (refreshDashboard && _state.value.dataSource == DataSource.PHONE) {
                    reload()
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        categoriesUi = it.categoriesUi.copy(loading = false),
                        error = e.message ?: "加载分类失败",
                    )
                }
            }
        }
    }

    /** 清空本地用量会话并全量重采（修复跨午夜 0 点缺失）。 */
    fun rebuildUsageHistory() {
        if (_state.value.usageRebuilding) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    usageRebuilding = true,
                    message = null,
                    error = null,
                    dataSource = DataSource.PHONE,
                    period = StatsPeriod.DAY,
                    offset = 0,
                )
            }
            try {
                timelineLoadedOffset = null
                val summary = withContext(Dispatchers.IO) {
                    repository.clearLocalUsageForResync()
                    repository.loadSummary(StatsPeriod.DAY, 0)
                }
                _state.update {
                    it.copy(
                        usageRebuilding = false,
                        loading = false,
                        summary = summary,
                        screen = AppScreen.DASHBOARD,
                        message = "用量已重建，请查看今日 0 点柱状图",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        usageRebuilding = false,
                        error = e.message ?: "重建用量失败",
                    )
                }
            }
        }
    }

    fun openDashboard() {
        _state.update {
            it.copy(
                screen = AppScreen.DASHBOARD,
                frames = emptyList(),
                frameAppPackage = null,
                hindsightAppDetail = null,
                hindsightAppDetailLoading = false,
                categoryUsageDetail = null,
                frameSearchHits = emptyList(),
                frameSearchHitIndex = 0,
                frameSearchMessage = null,
                frameSearchLoading = false,
            )
        }
    }

    fun openHindsightAppDetail(app: AppUsageItem) {
        if (_state.value.dataSource != DataSource.HINDSIGHT_CLOUD) return
        _state.update {
            it.copy(
                screen = AppScreen.HINDSIGHT_APP_DETAIL,
                hindsightAppDetail = null,
                hindsightAppDetailLoading = true,
            )
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    hindsightAggregator.loadAppDetail(
                        processName = app.packageName,
                        period = _state.value.period,
                        offset = _state.value.offset,
                        hourFilter = _state.value.selectedHour,
                    )
                }
            }.onSuccess { detail ->
                hindsightIcons.ensureLoaded(listOf(detail.processName))
                _state.update {
                    it.copy(
                        hindsightAppDetail = detail,
                        hindsightAppDetailLoading = false,
                        hindsightIconEpoch = it.hindsightIconEpoch + 1,
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        hindsightAppDetailLoading = false,
                        screen = AppScreen.DASHBOARD,
                        error = e.message ?: "加载应用详情失败",
                    )
                }
            }
        }
    }

    fun closeHindsightAppDetail() {
        _state.update {
            it.copy(
                screen = AppScreen.DASHBOARD,
                hindsightAppDetail = null,
                hindsightAppDetailLoading = false,
            )
        }
    }

    fun setDashboardAppQuery(query: String) {
        _state.update { it.copy(dashboardAppQuery = query) }
    }

    fun setDashboardScroll(index: Int, offset: Int) {
        val s = _state.value
        if (s.dashboardFirstVisibleIndex == index && s.dashboardFirstVisibleOffset == offset) return
        _state.update {
            it.copy(dashboardFirstVisibleIndex = index, dashboardFirstVisibleOffset = offset)
        }
    }

    fun setHindsightListScroll(index: Int, offset: Int) {
        val s = _state.value
        if (s.hindsightListFirstVisibleIndex == index && s.hindsightListFirstVisibleOffset == offset) {
            return
        }
        _state.update {
            it.copy(
                hindsightListFirstVisibleIndex = index,
                hindsightListFirstVisibleOffset = offset,
            )
        }
    }

    fun setPeriod(period: StatsPeriod) {
        if (_state.value.period == period) return
        timelineLoadedOffset = null
        _state.update {
            it.copy(
                period = period,
                phoneBottomTab = PhoneBottomTab.fromPeriod(period),
                offset = 0,
                selectedHour = null,
                dashboardFirstVisibleIndex = 0,
                dashboardFirstVisibleOffset = 0,
                hindsightListFirstVisibleIndex = 0,
                hindsightListFirstVisibleOffset = 0,
                dayViewMode = DayViewMode.CHART,
                timeline = emptyList(),
                timelineDrillStack = emptyList(),
                timelineLoading = false,
            )
        }
        reload()
    }

    fun setTimelineGranularity(granularity: TimelineGranularity) {
        val s = _state.value
        if (s.timelineGranularity == granularity && s.timelineDrillStack.isEmpty()) return
        _state.update {
            it.copy(timelineGranularity = granularity, timelineDrillStack = emptyList())
        }
    }

    /** 点击时/分堆叠时段：进入下一级更细视图，仅显示该时段 */
    fun drillIntoTimelineBucket(bucketStart: Long) {
        val next = when (_state.value.effectiveTimelineGranularity) {
            TimelineGranularity.HOUR ->
                TimelineDrillLevel(TimelineGranularity.MINUTE, bucketStart, bucketStart + 3_600_000L)
            TimelineGranularity.MINUTE ->
                TimelineDrillLevel(TimelineGranularity.SECOND, bucketStart, bucketStart + 60_000L)
            TimelineGranularity.SECOND -> return
        }
        _state.update { it.copy(timelineDrillStack = it.timelineDrillStack + next) }
    }

    fun timelineDrillUp() {
        _state.update { it.copy(timelineDrillStack = it.timelineDrillStack.dropLast(1)) }
    }

    fun setDayViewMode(mode: DayViewMode) {
        if (_state.value.dayViewMode == mode) return
        _state.update { it.copy(dayViewMode = mode) }
        if (mode == DayViewMode.TIMELINE) {
            // 已预加载则即时展示，避免点击卡顿
            val offset = _state.value.offset
            if (timelineLoadedOffset != offset || _state.value.timeline.isEmpty()) {
                loadTimeline(sync = false)
            }
        }
    }

    /** 日 ↔ 周 ↔ 月 */
    fun shiftPeriod(delta: Int) {
        val order = listOf(StatsPeriod.DAY, StatsPeriod.WEEK, StatsPeriod.MONTH)
        val idx = order.indexOf(_state.value.period)
        val next = order.getOrNull(idx + delta) ?: return
        setPeriod(next)
    }

    fun shiftOffset(delta: Int) {
        applyOffset((_state.value.offset + delta).coerceAtMost(0))
    }

    fun jumpToCurrent() {
        applyOffset(0)
    }

    /** 日跳到该日；周跳到该日所在周；月跳到该日所在月。不能晚于今天。 */
    fun jumpToDate(date: LocalDate) {
        val today = LocalDate.now()
        if (date.isAfter(today)) return
        val offset = when (_state.value.period) {
            StatsPeriod.DAY -> ChronoUnit.DAYS.between(today, date).toInt()
            StatsPeriod.WEEK -> {
                val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val target = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                ChronoUnit.WEEKS.between(thisWeek, target).toInt()
            }
            StatsPeriod.MONTH -> ChronoUnit.MONTHS.between(
                today.withDayOfMonth(1),
                date.withDayOfMonth(1),
            ).toInt()
        }
        applyOffset(offset.coerceAtMost(0))
    }

    private fun applyOffset(next: Int) {
        if (next == _state.value.offset) return
        timelineLoadedOffset = null
        _state.update {
            it.copy(
                offset = next,
                selectedHour = null,
                timeline = emptyList(),
                timelineDrillStack = emptyList(),
                timelineLoading = false,
                dashboardFirstVisibleIndex = 0,
                dashboardFirstVisibleOffset = 0,
                hindsightListFirstVisibleIndex = 0,
                hindsightListFirstVisibleOffset = 0,
            )
        }
        reload()
    }

    fun selectHour(hour: Int?) {
        _state.update {
            val next = if (it.selectedHour == hour) null else hour
            it.copy(selectedHour = next)
        }
    }

    fun openExportDialog() {
        _state.update { it.copy(showExportDialog = true, message = null) }
    }

    fun dismissExportDialog() {
        _state.update { it.copy(showExportDialog = false) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun exportPeriod(
        period: StatsPeriod,
        includeScreenTexts: Boolean = true,
        useCurrentOffset: Boolean = true,
    ) {
        if (!_state.value.hasPermission || _state.value.exporting) return
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _state.update {
                it.copy(exporting = true, showExportDialog = false, message = null, error = null)
            }
            val offset = if (useCurrentOffset && _state.value.period == period) {
                _state.value.offset
            } else {
                0
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val summary = repository.loadSummary(period, offset)
                    val frameTexts = if (includeScreenTexts) {
                        val privacy = repository.listPrivacyApps().map { it.packageName }.toSet()
                        repository.loadExportFrameTextsForPeriod(
                            period,
                            offset,
                            excludePackages = privacy,
                        )
                    } else {
                        null
                    }
                    val timeline = repository.loadTimelineForPeriod(period, offset, sync = false)
                    val app = getApplication<Application>()
                    val result = UsageExportWriter.writeAndGetShareUri(
                        context = app,
                        summary = summary,
                        frameTexts = frameTexts,
                        timeline = timeline,
                        appLabelOf = repository::appLabel,
                        includeScreenTexts = includeScreenTexts,
                    )
                    result to UsageExportWriter.shareIntent(app, result)
                }
            }.onSuccess { (result, intent) ->
                _state.update { it.copy(exporting = false, message = "已生成 ${result.fileName}") }
                _exportEvents.emit(ExportEvent.Share(intent))
            }.onFailure { e ->
                _state.update {
                    it.copy(exporting = false, error = exportFailureMessage(e))
                }
            }
        }
    }

    fun exportDateRange(
        start: LocalDate,
        end: LocalDate,
        includeScreenTexts: Boolean = true,
    ) {
        if (!_state.value.hasPermission || _state.value.exporting) return
        if (end.isBefore(start)) return
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _state.update {
                it.copy(exporting = true, showExportDialog = false, message = null, error = null)
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val summary = repository.loadSummaryForDateRange(start, end)
                    val frameTexts = if (includeScreenTexts) {
                        val privacy = repository.listPrivacyApps().map { it.packageName }.toSet()
                        repository.loadExportFrameTextsForDateRange(
                            start,
                            end,
                            excludePackages = privacy,
                        )
                    } else {
                        null
                    }
                    val timeline = repository.loadTimelineForDateRange(start, end, sync = false)
                    val app = getApplication<Application>()
                    val result = UsageExportWriter.writeAndGetShareUri(
                        context = app,
                        summary = summary,
                        frameTexts = frameTexts,
                        timeline = timeline,
                        appLabelOf = repository::appLabel,
                        includeScreenTexts = includeScreenTexts,
                    )
                    result to UsageExportWriter.shareIntent(app, result)
                }
            }.onSuccess { (result, intent) ->
                _state.update { it.copy(exporting = false, message = "已生成 ${result.fileName}") }
                _exportEvents.emit(ExportEvent.Share(intent))
            }.onFailure { e ->
                _state.update {
                    it.copy(exporting = false, error = exportFailureMessage(e))
                }
            }
        }
    }

    private fun exportFailureMessage(e: Throwable): String {
        val hint = e.message.orEmpty()
        return when {
            e is OutOfMemoryError || hint.contains("OutOfMemory", ignoreCase = true) ->
                "导出数据过大，内存不足。请缩小日期范围后重试"
            hint.contains("CursorWindow", ignoreCase = true) ||
                hint.contains("Row too big", ignoreCase = true) ->
                "画面数据过多，无法一次读出。请缩小日期范围后重试"
            hint.isNotBlank() -> hint
            else -> "导出失败"
        }
    }

    fun setFrameRecordingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val interval = _state.value.frameInterval
            withContext(Dispatchers.IO) {
                repository.updateFrameSettings(enabled, interval)
            }
            _state.update { it.copy(frameRecordingEnabled = enabled) }
            if (enabled && !FrameCaptureAccessibilityService.isEnabled(getApplication())) {
                _state.update {
                    it.copy(
                        message = "请先在系统无障碍中开启「留迹画面记录」" +
                            if (FrameCaptureAccessibilityService.isLegacyServiceListed(getApplication())) {
                                "；若仍开着旧的「留迹」项请先关闭再开本项"
                            } else {
                                ""
                            },
                    )
                }
            }
            FrameRecorderService.syncWithSettings(getApplication(), enabled)
            loadSettings()
        }
    }

    fun setFrameInterval(interval: FrameInterval) {
        viewModelScope.launch {
            val enabled = _state.value.frameRecordingEnabled
            withContext(Dispatchers.IO) {
                repository.updateFrameSettings(enabled, interval)
            }
            _state.update { it.copy(frameInterval = interval) }
            if (enabled) {
                FrameRecorderService.syncWithSettings(getApplication(), true)
            }
        }
    }

    fun setFrameBlacklistSearch(query: String) {
        _state.update { it.copy(frameBlacklistSearch = query) }
    }

    fun openFrameBlacklistPicker() {
        viewModelScope.launch {
            val blocked = _state.value.frameBlacklist.map { it.packageName }.toSet()
            val apps = withContext(Dispatchers.IO) {
                repository.categories.listInstalledAppsForPicker()
            }.filter { it.packageName !in blocked }
            _state.update {
                it.copy(
                    frameBlacklistPickerOpen = true,
                    frameBlacklistSearch = "",
                    frameBlacklistCandidates = apps,
                )
            }
        }
    }

    fun dismissFrameBlacklistPicker() {
        _state.update {
            it.copy(
                frameBlacklistPickerOpen = false,
                frameBlacklistSearch = "",
                frameBlacklistCandidates = emptyList(),
            )
        }
    }

    fun addToFrameBlacklist(apps: List<AppCategoryAssignment>) {
        if (apps.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val dao = com.liuji.xiaoqi.data.db.UsageDatabase
                        .getInstance(getApplication()).usageDao()
                    for (app in apps) {
                        FrameBlacklistStore.add(
                            dao,
                            app.packageName,
                            app.label,
                            FrameBlacklistStore.SOURCE_USER,
                        )
                    }
                    FrameBlacklistStore.listActive(dao) to FrameBlacklistStore.listPending(dao)
                }
            }.onSuccess { (items, pending) ->
                dismissFrameBlacklistPicker()
                _state.update {
                    it.copy(frameBlacklist = items, frameBlacklistPending = pending)
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "加入黑名单失败") }
            }
        }
    }

    fun removeFromFrameBlacklist(item: FrameBlacklistItem) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val dao = com.liuji.xiaoqi.data.db.UsageDatabase
                        .getInstance(getApplication()).usageDao()
                    FrameBlacklistStore.removeByUser(dao, item.packageName, item.label)
                    FrameBlacklistStore.listActive(dao)
                }
            }.onSuccess { items ->
                _state.update { it.copy(frameBlacklist = items) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "移除黑名单失败") }
            }
        }
    }

    fun confirmFrameBlacklistPending(item: FrameBlacklistItem) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val dao = com.liuji.xiaoqi.data.db.UsageDatabase
                        .getInstance(getApplication()).usageDao()
                    FrameBlacklistStore.confirmPending(dao, item.packageName)
                    FrameBlacklistStore.listActive(dao) to FrameBlacklistStore.listPending(dao)
                }
            }.onSuccess { (items, pending) ->
                _state.update {
                    it.copy(frameBlacklist = items, frameBlacklistPending = pending)
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "加入黑名单失败") }
            }
        }
    }

    fun dismissFrameBlacklistPending(item: FrameBlacklistItem) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val dao = com.liuji.xiaoqi.data.db.UsageDatabase
                        .getInstance(getApplication()).usageDao()
                    FrameBlacklistStore.removeByUser(dao, item.packageName, item.label)
                    FrameBlacklistStore.listPending(dao)
                }
            }.onSuccess { pending ->
                _state.update { it.copy(frameBlacklistPending = pending) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "忽略候选失败") }
            }
        }
    }

    fun setPrivacyAppSearch(query: String) {
        _state.update { it.copy(privacyAppSearch = query) }
    }

    fun openPrivacyAppPicker() {
        viewModelScope.launch {
            val blocked = _state.value.privacyApps.map { it.packageName }.toSet()
            val apps = withContext(Dispatchers.IO) {
                repository.categories.listInstalledAppsForPicker()
            }.filter { it.packageName !in blocked }
            _state.update {
                it.copy(
                    privacyAppPickerOpen = true,
                    privacyAppSearch = "",
                    privacyAppCandidates = apps,
                )
            }
        }
    }

    fun dismissPrivacyAppPicker() {
        _state.update {
            it.copy(
                privacyAppPickerOpen = false,
                privacyAppSearch = "",
                privacyAppCandidates = emptyList(),
            )
        }
    }

    fun addPrivacyApps(apps: List<AppCategoryAssignment>) {
        if (apps.isEmpty()) {
            dismissPrivacyAppPicker()
            return
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    apps.forEach { app ->
                        repository.addPrivacyApp(app.packageName, app.label)
                    }
                    repository.listPrivacyApps()
                }
            }.onSuccess { items ->
                dismissPrivacyAppPicker()
                _state.update { it.copy(privacyApps = items) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "添加隐私应用失败") }
            }
        }
    }

    fun removePrivacyApp(item: PrivacyAppEntity) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.removePrivacyApp(item.packageName)
                    repository.listPrivacyApps()
                }
            }.onSuccess { items ->
                _state.update { it.copy(privacyApps = items) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "移除隐私应用失败") }
            }
        }
    }

    fun openAppFrames(app: AppUsageItem) {
        if (_state.value.dataSource == DataSource.HINDSIGHT_CLOUD) {
            openHindsightAppDetail(app)
            return
        }
        _state.update {
            it.copy(
                screen = AppScreen.FRAME_VIEWER,
                frameAppPackage = app.packageName,
                frameAppLabel = app.label,
                frames = emptyList(),
                frameIndex = 0,
                framesLoading = true,
                frameSearchHits = emptyList(),
                frameSearchHitIndex = 0,
                frameSearchMessage = null,
                frameSearchQuery = "",
            )
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadFramesForPackage(
                        app.packageName,
                        _state.value.period,
                        _state.value.offset,
                    )
                }
            }.onSuccess { frames ->
                _state.update {
                    it.copy(frames = frames, frameIndex = 0, framesLoading = false)
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(framesLoading = false, error = e.message ?: "加载画面失败")
                }
            }
        }
    }

    fun setFrameIndex(index: Int) {
        val size = _state.value.frames.size
        if (size == 0) return
        _state.update { it.copy(frameIndex = index.coerceIn(0, size - 1)) }
    }

    fun setFrameSearchQuery(query: String) {
        _state.update { it.copy(frameSearchQuery = query, frameSearchMessage = null) }
    }

    fun setFrameSearchScope(scope: FrameSearchScope) {
        _state.update { it.copy(frameSearchScope = scope) }
    }

    fun setFrameSearchPackages(packages: Set<String>) {
        _state.update {
            it.copy(
                frameSearchPackages = packages,
                frameSearchScope = if (packages.isEmpty()) {
                    FrameSearchScope.CURRENT
                } else {
                    FrameSearchScope.PICKED
                },
            )
        }
    }

    fun loadFrameSearchCategories() {
        if (_state.value.frameSearchCategories.isNotEmpty() ||
            _state.value.frameSearchCategoriesLoading
        ) {
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(frameSearchCategoriesLoading = true) }
            runCatching {
                withContext(Dispatchers.IO) { repository.categories.listCategoriesWithApps() }
            }.onSuccess { categories ->
                _state.update {
                    it.copy(
                        frameSearchCategories = categories,
                        frameSearchCategoriesLoading = false,
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        frameSearchCategoriesLoading = false,
                        frameSearchMessage = e.message ?: "加载应用分类失败",
                    )
                }
            }
        }
    }

    fun applyFrameDateFilter(epochDay: Long, startMinute: Int, endMinute: Int) {
        val pkg = _state.value.frameAppPackage ?: return
        val date = LocalDate.ofEpochDay(epochDay)
        val zone = ZoneId.systemDefault()
        val startMinuteClamped = startMinute.coerceIn(0, 23 * 60 + 59)
        val endMinuteClamped = endMinute.coerceIn(startMinuteClamped, 23 * 60 + 59)
        val startMillis = date.atTime(startMinuteClamped / 60, startMinuteClamped % 60)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val endMillis = date.atTime(endMinuteClamped / 60, endMinuteClamped % 60)
            .plusMinutes(1)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        viewModelScope.launch {
            _state.update { it.copy(framesLoading = true) }
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadFramesForPackageBetween(pkg, startMillis, endMillis)
                }
            }.onSuccess { frames ->
                _state.update {
                    it.copy(frames = frames, frameIndex = 0, framesLoading = false)
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(framesLoading = false, error = e.message ?: "按日期加载画面失败")
                }
            }
        }
    }

    fun clearFrameDateFilter() {
        val pkg = _state.value.frameAppPackage ?: return
        val period = _state.value.period
        val offset = _state.value.offset
        viewModelScope.launch {
            _state.update { it.copy(framesLoading = true) }
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadFramesForPackage(pkg, period, offset)
                }
            }.onSuccess { frames ->
                _state.update {
                    it.copy(frames = frames, frameIndex = 0, framesLoading = false)
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(framesLoading = false, error = e.message ?: "恢复画面失败")
                }
            }
        }
    }

    fun setFrameSearchStrict(value: Boolean) {
        _state.update { it.copy(frameSearchStrict = value) }
    }

    fun runFrameSearch() {
        val query = _state.value.frameSearchQuery.trim()
        if (query.isEmpty()) {
            _state.update {
                it.copy(
                    frameSearchHits = emptyList(),
                    frameSearchHitIndex = 0,
                    frameSearchMessage = "请输入搜索关键词",
                )
            }
            return
        }
        if (_state.value.frameSearchLoading) return
        val scopePackages = when (_state.value.frameSearchScope) {
            FrameSearchScope.CURRENT -> {
                val pkg = _state.value.frameAppPackage
                if (pkg.isNullOrBlank()) {
                    _state.update { it.copy(frameSearchMessage = "当前无应用上下文") }
                    return
                }
                setOf(pkg)
            }
            FrameSearchScope.ALL -> null
            FrameSearchScope.PICKED -> {
                val picked = _state.value.frameSearchPackages
                if (picked.isEmpty()) {
                    _state.update { it.copy(frameSearchMessage = "请先选择应用") }
                    return
                }
                picked
            }
        }
        val strict = _state.value.frameSearchStrict
        _state.update {
            it.copy(frameSearchLoading = true, frameSearchMessage = null)
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.searchFrames(query, scopePackages, strict)
                }
            }.onSuccess { hits ->
                _state.update {
                    it.copy(
                        frameSearchHits = hits,
                        frameSearchHitIndex = 0,
                        frameSearchLoading = false,
                        frameSearchMessage = if (hits.isEmpty()) "无匹配结果" else null,
                    )
                }
                if (hits.isNotEmpty()) {
                    jumpToFrameSearchHit(0)
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        frameSearchLoading = false,
                        frameSearchHits = emptyList(),
                        frameSearchMessage = e.message ?: "搜索失败",
                    )
                }
            }
        }
    }

    fun frameSearchPrev() {
        val hits = _state.value.frameSearchHits
        if (hits.isEmpty()) return
        val next = (_state.value.frameSearchHitIndex - 1).coerceAtLeast(0)
        jumpToFrameSearchHit(next)
    }

    fun frameSearchNext() {
        val hits = _state.value.frameSearchHits
        if (hits.isEmpty()) return
        val next = (_state.value.frameSearchHitIndex + 1).coerceAtMost(hits.lastIndex)
        jumpToFrameSearchHit(next)
    }

    fun jumpToFrameSearchHit(index: Int) {
        val hits = _state.value.frameSearchHits
        if (hits.isEmpty()) return
        val i = index.coerceIn(0, hits.lastIndex)
        val hit = hits[i]
        _state.update { it.copy(frameSearchHitIndex = i) }
        val currentPkg = _state.value.frameAppPackage
        val alreadyLoaded = currentPkg == hit.packageName &&
            _state.value.frames.any { it.id == hit.frameId }
        if (alreadyLoaded) {
            val idx = _state.value.frames.indexOfFirst { it.id == hit.frameId }
            if (idx >= 0) setFrameIndex(idx)
            return
        }
        // 同包但当前列表是「统计窗口」子集，或跨包：加载该包全时段帧
        _state.update {
            it.copy(
                framesLoading = true,
                frameAppPackage = hit.packageName,
                frameAppLabel = hit.appLabel.ifBlank { hit.packageName },
            )
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadFramesForPackageAllTime(hit.packageName)
                }
            }.onSuccess { frames ->
                val idx = frames.indexOfFirst { it.id == hit.frameId }.coerceAtLeast(0)
                _state.update {
                    it.copy(
                        frames = frames,
                        frameIndex = if (frames.isEmpty()) 0 else idx,
                        framesLoading = false,
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        framesLoading = false,
                        frameSearchMessage = e.message ?: "加载匹配画面失败",
                    )
                }
            }
        }
    }

    fun openUsageAccessSettings() = repository.usageAccessSettingsIntent()

    fun openAccessibilitySettings() = repository.accessibilitySettingsIntent()

    fun appIcon(packageName: String) = repository.loadAppIcon(packageName)

    /** 电脑云端：优先用同步的 icons.json，否则 null（色块占位） */
    fun hindsightAppIcon(processName: String) = hindsightIcons.getCached(processName)

    private fun loadSettings() {
        settingsJob?.cancel()
        settingsJob = viewModelScope.launch {
            val settings = withContext(Dispatchers.IO) { repository.getOrCreateSettings() }
            val count = withContext(Dispatchers.IO) {
                com.liuji.xiaoqi.data.db.UsageDatabase.getInstance(getApplication())
                    .usageDao().frameCount()
            }
            val blacklist = withContext(Dispatchers.IO) {
                val dao = com.liuji.xiaoqi.data.db.UsageDatabase
                    .getInstance(getApplication()).usageDao()
                FrameBlacklistStore.listActive(dao) to FrameBlacklistStore.listPending(dao)
            }
            val privacyApps = withContext(Dispatchers.IO) { repository.listPrivacyApps() }
            val account = hindsightSync.account()
            val lastSync = withContext(Dispatchers.IO) { hindsightSync.lastSyncAt() }
            // IO 结束后再读一次，避免并发旧协程覆盖 onAppResume 的最新值
            val app = getApplication<Application>()
            val a11y = FrameCaptureAccessibilityService.isEnabled(app)
            val legacy = FrameCaptureAccessibilityService.isLegacyServiceListed(app)
            _state.update {
                it.copy(
                    frameRecordingEnabled = settings.frameRecordingEnabled,
                    frameInterval = FrameInterval.fromMillis(settings.frameIntervalMs),
                    accessibilityEnabled = a11y,
                    accessibilityLegacyListed = legacy,
                    frameCount = count,
                    frameBlacklist = blacklist.first,
                    frameBlacklistPending = blacklist.second,
                    privacyApps = privacyApps,
                    // 密码只存加密库，不回填到 UI state
                    webDavAccount = account.copy(password = ""),
                    hindsightConfigured = account.configured,
                    hindsightLastSync = lastSync,
                )
            }
            FrameRecorderService.syncWithSettings(
                getApplication(),
                settings.frameRecordingEnabled,
            )
        }
    }

    private fun loadTimeline(sync: Boolean = false) {
        val offset = _state.value.offset
        val source = _state.value.dataSource
        timelineJob?.cancel()
        timelineJob = viewModelScope.launch {
            _state.update { it.copy(timelineLoading = true) }
            runCatching {
                withContext(Dispatchers.IO) {
                    when (source) {
                        DataSource.PHONE -> repository.loadDayTimeline(offset, sync = sync)
                        DataSource.HINDSIGHT_CLOUD -> hindsightAggregator.loadDayTimeline(offset)
                    }
                }
            }.onSuccess { list ->
                timelineLoadedOffset = offset
                _state.update {
                    it.copy(timeline = list, timelineLoading = false)
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        timelineLoading = false,
                        error = e.message ?: "时间线加载失败",
                    )
                }
            }
        }
    }

    private fun markLoaded() {
        lastLoadedLocalDate = java.time.LocalDate.now()
        lastLoadedAt = System.currentTimeMillis()
    }

    private fun reload() {
        val source = _state.value.dataSource
        if (source == DataSource.PHONE && !_state.value.hasPermission) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val hadData = _state.value.summary != null
            _state.update {
                it.copy(
                    loading = if (hadData) it.loading else true,
                    error = null,
                )
            }
            val period = _state.value.period
            val offset = _state.value.offset
            runCatching {
                withContext(Dispatchers.IO) {
                    when (source) {
                        DataSource.PHONE -> repository.loadSummary(period, offset)
                        DataSource.HINDSIGHT_CLOUD -> hindsightAggregator.loadSummary(period, offset)
                    }
                }
            }.onSuccess { summary ->
                markLoaded()
                _state.update { it.copy(loading = false, summary = summary, error = null) }
                if (source == DataSource.HINDSIGHT_CLOUD) {
                    val names = summary.apps.map { it.packageName } +
                        summary.appsByHour.values.flatten().map { it.packageName }
                    hindsightIcons.ensureLoaded(names)
                    val devices = withContext(Dispatchers.IO) {
                        HindsightDatabase.getInstance(getApplication()).dao().devices()
                    }
                    val label = devices.firstOrNull()?.displayName ?: "Windows"
                    _state.update {
                        it.copy(
                            hindsightDeviceLabel = label,
                            hindsightIconEpoch = it.hindsightIconEpoch + 1,
                        )
                    }
                } else {
                    // 后台预热图标，避免列表首次出现时在主线程同步解码
                    viewModelScope.launch(Dispatchers.IO) {
                        summary.apps.take(24).forEach { app ->
                            repository.loadAppIcon(app.packageName)
                        }
                    }
                    if (period == StatsPeriod.DAY) {
                        loadTimeline(sync = false)
                    }
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(loading = false, error = e.message ?: "加载失败")
                }
            }
        }
    }

    private companion object {
        const val RESUME_REFRESH_STALE_MS = 60_000L
    }
}
