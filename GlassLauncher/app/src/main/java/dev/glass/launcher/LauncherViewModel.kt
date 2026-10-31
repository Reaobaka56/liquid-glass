package dev.glass.launcher

import android.app.Application
import android.app.WallpaperManager
import android.content.ComponentName
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.glass.launcher.settings.LauncherSettings
import dev.glass.launcher.wallpaper.GlassWallpaperService
import dev.glass.launcher.wallpaper.TiltTracker
import dev.glass.launcher.wallpaper.WallpaperStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.IOException
import org.json.JSONException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    var iconPackCandidates by mutableStateOf<List<IconPackCandidate>>(emptyList())
        private set

    var iconCollections by mutableStateOf<List<IconCollection>>(emptyList())
        private set

    var dock by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    var homePages by mutableStateOf<List<List<AppEntry>>>(listOf(emptyList()))
        private set

    var recentApps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    var wallpaper by mutableStateOf<ImageBitmap?>(null)
        private set

    var wallpaperGallery by mutableStateOf<List<WallpaperGalleryPhoto>>(emptyList())
        private set

    var selectedWallpaperIndex by mutableIntStateOf(0)
        private set

    var shuffleOnLock by mutableStateOf(false)
        private set

    var foregroundWallpaper by mutableStateOf<ImageBitmap?>(null)
        private set

    var foregroundRestorable by mutableStateOf(false)
        private set

    var subjectDetectionRunning by mutableStateOf(false)
        private set

    var subjectDetectionMessage by mutableStateOf<String?>(null)
        private set

    var widgets by mutableStateOf<List<HomeWidgetEntry>>(emptyList())
        private set

    var builtInWidgets by mutableStateOf<List<BuiltInWidgetEntry>>(emptyList())
        private set

    var calendarEvents by mutableStateOf<List<CalendarEvent>>(emptyList())
        private set

    var weather by mutableStateOf<WeatherSnapshot?>(null)
        private set

    var weatherLoading by mutableStateOf(false)
        private set

    var weatherError by mutableStateOf<String?>(null)
        private set

    /** Phone tilt, each axis in -1..1. Read it inside draw lambdas so it doesn't recompose. */
    var tilt by mutableStateOf(Offset.Zero)
        private set

    var drawerOpen by mutableStateOf(false)
    var isEditMode by mutableStateOf(false)

    /** Long-press menu, selected app, and glass settings sheet visibility. */
    var menuOpen by mutableStateOf(false)
    var selectedMenuApp by mutableStateOf<AppEntry?>(null)
    var selectedMenuShortcuts by mutableStateOf<List<AppShortcut>>(emptyList())
    var settingsOpen by mutableStateOf(false)
    var clockEditorOpen by mutableStateOf(false)
    var wallpaperEditorOpen by mutableStateOf(false)
    var iconEditorOpen by mutableStateOf(false)
    var showUsagePermissionDialog by mutableStateOf(false)

    val settings = LauncherSettings(app)

    private val store = WallpaperStore(app)
    private val widgetStore = HomeWidgetStore(app)
    private val builtInWidgetStore = BuiltInWidgetStore(app)
    private val iconAssignmentStore = IconAssignmentStore(app)
    private val tiltTracker = TiltTracker(app) { x, y -> tilt = Offset(x, y) }
    private var shownPath: String? = null
    private var shownForegroundPath: String? = null
    private var shownRevision = -1L

    init {
        widgets = widgetStore.load()
        builtInWidgets = builtInWidgetStore.load()
        shuffleOnLock = store.shuffleOnLock
        reloadIconCollections()
        refreshApps()
        reloadWallpaper()
        refreshCalendarEvents()
        if (builtInWidgets.any { it.type == BuiltInWidgetType.WEATHER }) refreshWeather()
    }

    fun addBuiltInWidget(type: BuiltInWidgetType, page: Int) {
        builtInWidgets = builtInWidgets + BuiltInWidgetEntry(
            id = BuiltInWidgetStore.newId(),
            type = type,
            page = page.coerceAtLeast(0),
            size = BuiltInWidgetSize.MEDIUM,
        )
        persistBuiltInWidgets()
        if (type == BuiltInWidgetType.CALENDAR) refreshCalendarEvents()
    }

    fun removeBuiltInWidget(id: String) {
        builtInWidgets = builtInWidgets.filterNot { it.id == id }
        persistBuiltInWidgets()
    }

    fun resizeBuiltInWidget(id: String, delta: Int) {
        val sizes = BuiltInWidgetSize.entries
        builtInWidgets = builtInWidgets.map { widget ->
            if (widget.id != id) return@map widget
            val index = sizes.indexOf(widget.size)
            widget.copy(size = sizes[(index + delta).coerceIn(sizes.indices)])
        }
        persistBuiltInWidgets()
    }

    fun moveBuiltInWidget(id: String, page: Int, delta: Int) {
        val visible = builtInWidgets.filter { it.page == page }
        val index = visible.indexOfFirst { it.id == id }
        val destination = index + delta
        if (index !in visible.indices || destination !in visible.indices) return
        val reordered = visible.toMutableList().apply {
            add(destination, removeAt(index))
        }
        val orderedIds = reordered.map { it.id }.toSet()
        val otherPages = builtInWidgets.filterNot { it.id in orderedIds }
        builtInWidgets = otherPages + reordered
        persistBuiltInWidgets()
    }

    fun refreshCalendarEvents() {
        viewModelScope.launch {
            calendarEvents = withContext(Dispatchers.IO) {
                CalendarRepository.upcoming(getApplication<Application>())
            }
        }
    }

    fun refreshWeather() {
        if (weatherLoading) return
        viewModelScope.launch {
            weatherLoading = true
            weatherError = null
            try {
                weather = WeatherRepository.current(getApplication<Application>())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("LauncherViewModel", "Could not refresh current weather.", e)
                weatherError = e.message ?: "Weather could not be loaded."
            } finally {
                weatherLoading = false
            }
        }
    }

    private fun persistBuiltInWidgets() {
        builtInWidgetStore.save(builtInWidgets)
    }

    fun addWidget(appWidgetId: Int, page: Int) {
        if (widgets.any { it.appWidgetId == appWidgetId }) return
        widgets = widgets + HomeWidgetEntry(
            appWidgetId = appWidgetId,
            page = page.coerceAtLeast(0),
            heightDp = 180,
        )
        persistWidgets()
    }

    fun removeWidget(appWidgetId: Int) {
        widgets = widgets.filterNot { it.appWidgetId == appWidgetId }
        persistWidgets()
    }

    fun resizeWidget(appWidgetId: Int, deltaDp: Int) {
        widgets = widgets.map { widget ->
            if (widget.appWidgetId == appWidgetId) {
                widget.copy(
                    heightDp = (widget.heightDp + deltaDp)
                        .coerceIn(HomeWidgetStore.MIN_HEIGHT_DP, HomeWidgetStore.MAX_HEIGHT_DP),
                )
            } else {
                widget
            }
        }
        persistWidgets()
    }

    private fun persistWidgets() {
        widgetStore.save(widgets)
    }

    fun refreshApps() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val assignments = iconAssignmentStore.assignments()
            val loaded = withContext(Dispatchers.Default) {
                AppRepository.loadApps(ctx).map { app ->
                    val assignment = assignments[app.packageName]
                    val customIcon = if (assignment?.startsWith("custom|") == true) {
                        try {
                            iconAssignmentStore.loadCustomIcon(app.packageName).asImageBitmap()
                        } catch (e: IOException) {
                            Log.e("LauncherViewModel", "Could not load the saved icon for ${app.packageName}.", e)
                            null
                        } catch (e: SecurityException) {
                            Log.e("LauncherViewModel", "Access to the saved icon for ${app.packageName} was denied.", e)
                            null
                        }
                    } else if (assignment?.startsWith("pack|") == true) {
                        val parts = assignment.split("|", limit = 3)
                        try {
                            if (parts.size == 3) {
                                IconPackRepository.loadIcon(ctx, parts[1], parts[2])
                            } else {
                                throw IOException("Saved icon-pack mapping is invalid.")
                            }
                        } catch (e: Exception) {
                            Log.e("LauncherViewModel", "Could not load the saved icon-pack icon for ${app.packageName}.", e)
                            null
                        }
                    } else null
                    app.copy(iconAssignment = assignment, customIcon = customIcon)
                }
            }
            apps = loaded
            iconPackCandidates = withContext(Dispatchers.IO) {
                IconPackRepository.candidates(ctx, loaded)
            }

            val byPackage = loaded.associateBy { it.packageName }

            // Restore dock or calculate candidates
            val savedDockPkgs = withContext(Dispatchers.IO) { AppRepository.loadDockPackages(ctx) }
            val dockPkgs = if (savedDockPkgs != null) {
                savedDockPkgs
            } else {
                val picked = AppRepository.dockCandidates(ctx).toMutableList()
                for (candidate in loaded) {
                    if (picked.size >= 4) break
                    if (picked.none { it == candidate.packageName }) picked += candidate.packageName
                }
                picked.take(4)
            }
            dock = dockPkgs.mapNotNull { byPackage[it] }

            // Restore Home Pages layout or create default page
            val savedLayout = withContext(Dispatchers.IO) { AppRepository.loadHomeLayout(ctx) }
            if (savedLayout != null) {
                val maxPage = savedLayout.items.maxOfOrNull { it.page } ?: 0
                val pageCount = maxOf(savedLayout.pageCount, maxPage + 1)
                val pagesList = MutableList(pageCount) { mutableListOf<AppEntry>() }
                savedLayout.items.sortedWith(compareBy({ it.page }, { it.position })).forEach { item ->
                    byPackage[item.packageName]?.let { app ->
                        if (item.page in pagesList.indices) {
                            pagesList[item.page].add(app)
                        }
                    }
                }
                homePages = pagesList
            } else {
                // Default initial page with first few apps not in dock
                val nonDockApps = loaded.filter { app -> dock.none { it.packageName == app.packageName } }.take(8)
                homePages = listOf(nonDockApps)
                persistHomeLayout()
            }

            refreshRecentApps()
        }
    }

    fun applyIcon(packageName: String, glyph: String) {
        iconAssignmentStore.setAssignment(packageName, glyph, iconAssignmentStore.selectedTheme)
        updateAppIcon(packageName, "$glyph|${iconAssignmentStore.selectedTheme.name}")
    }

    fun applyCustomIcon(packageName: String, uri: Uri, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val icon = try {
                withContext(Dispatchers.IO) {
                    iconAssignmentStore.setCustomIcon(packageName, uri)
                    iconAssignmentStore.loadCustomIcon(packageName).asImageBitmap()
                }
            } catch (e: IOException) {
                Log.e("LauncherViewModel", "Failed to save custom app icon.", e)
                onDone(false)
                return@launch
            } catch (e: SecurityException) {
                Log.e("LauncherViewModel", "Access to selected custom icon was denied.", e)
                onDone(false)
                return@launch
            }
            updateAppIcon(packageName, "custom|${iconAssignmentStore.selectedTheme.name}", icon)
            onDone(true)
        }
    }

    fun applyIconTheme(theme: AppIconTheme) {
        iconAssignmentStore.applyTheme(apps, theme)
        val assignments = iconAssignmentStore.assignments()
        apps = apps.map { app ->
            app.copy(
                iconAssignment = assignments[app.packageName],
                customIcon = if (assignments[app.packageName]?.startsWith("custom|") == true) {
                    app.customIcon
                } else null,
            )
        }

        val byPackage = apps.associateBy { it.packageName }
        dock = dock.mapNotNull { byPackage[it.packageName] }
        homePages = homePages.map { page -> page.mapNotNull { byPackage[it.packageName] } }
        recentApps = recentApps.mapNotNull { byPackage[it.packageName] }
    }

    fun applyIconPack(packageName: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val mapped = withContext(Dispatchers.IO) {
                    IconPackRepository.loadAssignments(getApplication(), packageName, apps)
                }
                val resolvedIcons = withContext(Dispatchers.IO) {
                    mapped.map { (targetPackage, drawable) ->
                        Triple(
                            targetPackage,
                            "pack|$packageName|$drawable",
                            IconPackRepository.loadIcon(getApplication(), packageName, drawable),
                        )
                    }
                }
                iconAssignmentStore.applyIconPack(mapped, packageName)
                updateAppIcons(resolvedIcons.associate { (targetPackage, assignment, icon) ->
                    targetPackage to (assignment to icon)
                })
                onDone(true, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("LauncherViewModel", "Could not apply icon pack $packageName.", e)
                onDone(false, e.message ?: "The icon pack could not be applied.")
            }
        }
    }

    fun saveIconCollection(name: String, onDone: (Boolean, String?) -> Unit) {
        runIconCollectionOperation(onDone) { iconAssignmentStore.saveCollection(name) }
    }

    fun duplicateIconCollection(sourceName: String, newName: String, onDone: (Boolean, String?) -> Unit) {
        runIconCollectionOperation(onDone) {
            iconAssignmentStore.duplicateCollection(sourceName, newName)
        }
    }

    fun renameIconCollection(oldName: String, newName: String, onDone: (Boolean, String?) -> Unit) {
        runIconCollectionOperation(onDone) {
            iconAssignmentStore.renameCollection(oldName, newName)
        }
    }

    fun deleteIconCollection(name: String, onDone: (Boolean, String?) -> Unit) {
        runIconCollectionOperation(onDone) { iconAssignmentStore.deleteCollection(name) }
    }

    fun applyIconCollection(name: String, onDone: (Boolean, String?) -> Unit) {
        runIconCollectionOperation(onDone) {
            iconAssignmentStore.applyCollection(name)
            refreshApps()
        }
    }

    fun exportIconCollection(name: String, onDone: (String?, String?) -> Unit) {
        try {
            onDone(iconAssignmentStore.exportCollection(name), null)
        } catch (e: JSONException) {
            Log.e("LauncherViewModel", "Could not export icon collection.", e)
            onDone(null, "Saved icon collections could not be read.")
        } catch (e: IOException) {
            Log.e("LauncherViewModel", "Could not read custom icons for export.", e)
            onDone(null, e.message ?: "Custom icons could not be exported.")
        } catch (e: IllegalArgumentException) {
            onDone(null, e.message ?: "The selected icon collection could not be exported.")
        }
    }

    fun importIconCollection(serialized: String, onDone: (Boolean, String?) -> Unit) {
        runIconCollectionOperation(onDone) { iconAssignmentStore.importCollection(serialized) }
    }

    private fun reloadIconCollections() {
        try {
            iconCollections = iconAssignmentStore.collections()
        } catch (e: JSONException) {
            Log.e("LauncherViewModel", "Saved icon collections are invalid.", e)
            iconCollections = emptyList()
        }
    }

    private fun runIconCollectionOperation(
        onDone: (Boolean, String?) -> Unit,
        operation: () -> Unit,
    ) {
        try {
            operation()
            reloadIconCollections()
            onDone(true, null)
        } catch (e: JSONException) {
            Log.e("LauncherViewModel", "Could not update icon collection.", e)
            onDone(false, "Icon collection data could not be read or saved.")
        } catch (e: IOException) {
            Log.e("LauncherViewModel", "Could not read or write icon collection data.", e)
            onDone(false, e.message ?: "Icon collection data could not be read or saved.")
        } catch (e: IllegalArgumentException) {
            onDone(false, e.message ?: "The icon collection could not be updated.")
        }
    }

    fun resetIcon(packageName: String) {
        iconAssignmentStore.reset(packageName)
        updateAppIcon(packageName, null)
    }

    fun resetAllIcons() {
        iconAssignmentStore.resetAll()
        apps = apps.map { it.copy(iconAssignment = null, customIcon = null) }
        val byPackage = apps.associateBy { it.packageName }
        dock = dock.mapNotNull { byPackage[it.packageName] }
        homePages = homePages.map { page -> page.mapNotNull { byPackage[it.packageName] } }
        recentApps = recentApps.mapNotNull { byPackage[it.packageName] }
    }

    fun selectIconTheme(theme: AppIconTheme) {
        iconAssignmentStore.setTheme(theme)
    }

    fun selectedIconTheme(): AppIconTheme = iconAssignmentStore.selectedTheme

    private fun updateAppIcon(packageName: String, assignment: String?, customIcon: ImageBitmap? = null) {
        apps = apps.map {
            if (it.packageName == packageName) it.copy(
                iconAssignment = assignment,
                customIcon = if (
                    assignment?.startsWith("custom|") == true ||
                    assignment?.startsWith("pack|") == true
                ) customIcon else null,
            ) else it
        }
        val updated = apps.firstOrNull { it.packageName == packageName } ?: return
        dock = dock.map { if (it.packageName == packageName) updated else it }
        homePages = homePages.map { page ->
            page.map { if (it.packageName == packageName) updated else it }
        }
        recentApps = recentApps.map { if (it.packageName == packageName) updated else it }
    }

    private fun updateAppIcons(updates: Map<String, Pair<String, ImageBitmap>>) {
        apps = apps.map { app ->
            val (assignment, icon) = updates[app.packageName] ?: return@map app
            app.copy(iconAssignment = assignment, customIcon = icon)
        }
        val byPackage = apps.associateBy { it.packageName }
        dock = dock.mapNotNull { byPackage[it.packageName] }
        homePages = homePages.map { page -> page.mapNotNull { byPackage[it.packageName] } }
        recentApps = recentApps.mapNotNull { byPackage[it.packageName] }
    }

    fun refreshRecentApps() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val recents = withContext(Dispatchers.Default) {
                AppRepository.getRecentApps(ctx, apps, max = 5)
            }
            recentApps = recents
        }
    }

    fun openContextMenu(app: AppEntry) {
        val ctx = getApplication<Application>()
        selectedMenuApp = app
        selectedMenuShortcuts = AppRepository.getShortcuts(ctx, app.packageName)
        menuOpen = true
    }

    fun closeContextMenu() {
        menuOpen = false
        selectedMenuApp = null
        selectedMenuShortcuts = emptyList()
    }

    fun isAppInDock(app: AppEntry): Boolean = dock.any { it.packageName == app.packageName }

    fun isAppInHome(app: AppEntry): Boolean = homePages.any { page -> page.any { it.packageName == app.packageName } }

    fun addToHome(app: AppEntry, targetPage: Int = 0) {
        val pages = homePages.map { it.toMutableList() }.toMutableList()
        if (targetPage >= pages.size) {
            while (pages.size <= targetPage) pages.add(mutableListOf())
        }
        if (pages[targetPage].none { it.packageName == app.packageName }) {
            pages[targetPage].add(app)
            homePages = pages
            persistHomeLayout()
        }
    }

    fun removeFromHome(app: AppEntry) {
        val pages = homePages.map { page ->
            page.filter { it.packageName != app.packageName }.toMutableList()
        }
        homePages = pages
        persistHomeLayout()
    }

    fun toggleDock(app: AppEntry) {
        if (isAppInDock(app)) {
            dock = dock.filter { it.packageName != app.packageName }
        } else {
            if (dock.size < 5) {
                dock = dock + app
            }
        }
        persistDockLayout()
    }

    fun addHomePages() {
        homePages = homePages + listOf(emptyList())
        persistHomeLayout()
    }

    fun removeHomePage(pageIndex: Int) {
        if (homePages.size <= 1) return
        val pages = homePages.toMutableList()
        if (pageIndex in pages.indices) {
            pages.removeAt(pageIndex)
            homePages = pages
            persistHomeLayout()

            // Widgets on later pages move down with them. Widgets on the removed page are
            // detached by the caller (it also has to free their host ids).
            widgets = widgets.map { if (it.page > pageIndex) it.copy(page = it.page - 1) else it }
            persistWidgets()
            builtInWidgets = builtInWidgets
                .filterNot { it.page == pageIndex }
                .map { if (it.page > pageIndex) it.copy(page = it.page - 1) else it }
            persistBuiltInWidgets()
        }
    }

    fun moveHomeItem(fromPage: Int, fromPos: Int, toPage: Int, toPos: Int) {
        val pages = homePages.map { it.toMutableList() }.toMutableList()
        if (fromPage !in pages.indices || toPage !in pages.indices) return
        if (fromPos !in pages[fromPage].indices) return

        val item = pages[fromPage].removeAt(fromPos)
        val clampedToPos = toPos.coerceIn(0, pages[toPage].size)
        pages[toPage].add(clampedToPos, item)
        homePages = pages
        persistHomeLayout()
    }

    private fun persistHomeLayout() {
        val ctx = getApplication<Application>()
        // Read Compose state here on the caller's thread, not inside the IO coroutine.
        val pages = homePages
        viewModelScope.launch(Dispatchers.IO) {
            val items = mutableListOf<HomeItem>()
            pages.forEachIndexed { pageIdx, pageApps ->
                pageApps.forEachIndexed { posIdx, app ->
                    items.add(HomeItem(app.packageName, pageIdx, posIdx))
                }
            }
            AppRepository.saveHomeLayout(ctx, items, pages.size)
        }
    }

    private fun persistDockLayout() {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            AppRepository.saveDockPackages(ctx, dock.map { it.packageName })
        }
    }

    fun startTilt() = tiltTracker.start()

    fun stopTilt() {
        tiltTracker.stop()
        tilt = Offset.Zero
    }

    fun reloadWallpaper() {
        viewModelScope.launch {
            val metrics = getApplication<Application>().resources.displayMetrics
            val loaded = withContext(Dispatchers.IO) {
                val file = store.current()
                val foregroundFile = store.foreground()
                val revision = store.revision()
                val mainChanged = file?.path != shownPath || revision != shownRevision
                val foregroundChanged = foregroundFile?.path != shownForegroundPath ||
                    foregroundFile?.lastModified() != storeForegroundModified ||
                    revision != shownRevision
                val bitmap = if (mainChanged) {
                    file?.let { store.decode(it, metrics.widthPixels, metrics.heightPixels) }
                } else null
                val foreground = if (foregroundChanged) {
                    foregroundFile?.let { store.decode(it, metrics.widthPixels, metrics.heightPixels) }
                } else null
                val gallery = store.photos().mapIndexedNotNull { index, photo ->
                    store.decode(photo, metrics.widthPixels / 3, metrics.heightPixels / 3)
                        ?.asImageBitmap()
                        ?.let { WallpaperGalleryPhoto(index, it) }
                }
                WallpaperImages(
                    main = bitmap?.asImageBitmap(),
                    mainPath = if (mainChanged) file?.path else null,
                    foreground = foreground?.asImageBitmap(),
                    foregroundPath = if (foregroundChanged) foregroundFile?.path else null,
                    foregroundModified = if (foregroundChanged) foregroundFile?.lastModified() else null,
                    gallery = gallery,
                    currentIndex = store.currentIndex(),
                    revision = revision,
                )
            }
            if (loaded.mainPath != null || store.current()?.path != shownPath ||
                loaded.revision != shownRevision
            ) {
                wallpaper = loaded.main
                shownPath = loaded.mainPath ?: store.current()?.path
            }
            if (loaded.foregroundPath != null || store.foreground()?.path != shownForegroundPath ||
                loaded.revision != shownRevision
            ) {
                foregroundWallpaper = loaded.foreground
                shownForegroundPath = loaded.foregroundPath ?: store.foreground()?.path
                storeForegroundModified = loaded.foregroundModified ?: 0L
            }
            shownRevision = loaded.revision
            foregroundRestorable = store.hasForegroundSource()
            wallpaperGallery = loaded.gallery
            selectedWallpaperIndex = loaded.currentIndex
            shuffleOnLock = store.shuffleOnLock
        }
    }

    fun setWallpapers(uris: List<Uri>, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val saved = try {
                withContext(Dispatchers.IO) { store.replaceWith(uris) }
            } catch (e: IOException) {
                Log.e("LauncherViewModel", "Failed to save selected wallpaper.", e)
                false
            } catch (e: SecurityException) {
                Log.e("LauncherViewModel", "Access to selected wallpaper was denied.", e)
                false
            } catch (e: IllegalArgumentException) {
                Log.e("LauncherViewModel", "Selected wallpaper URI is invalid.", e)
                false
            }
            if (saved) {
                shownPath = null
                reloadWallpaper()
                GlassWallpaperService.requestRefresh(getApplication<Application>())
            }
            onDone(saved)
        }
    }

    fun liveWallpaperActive(): Boolean {
        val ctx = getApplication<Application>()
        val info = WallpaperManager.getInstance(ctx).wallpaperInfo ?: return false
        return info.component == ComponentName(ctx, GlassWallpaperService::class.java)
    }

    fun hasSavedWallpaper(): Boolean = store.photos().isNotEmpty()

    fun selectWallpaper(index: Int) {
        if (store.select(index) == null) {
            Log.w("LauncherViewModel", "Ignored invalid wallpaper selection index $index.")
            return
        }
        shownPath = null
        reloadWallpaper()
        GlassWallpaperService.requestRefresh(getApplication<Application>())
    }

    fun updateShuffleOnLock(enabled: Boolean) {
        store.shuffleOnLock = enabled
        shuffleOnLock = enabled
        GlassWallpaperService.requestRefresh(getApplication<Application>())
    }

    fun detectForegroundSubject() {
        if (subjectDetectionRunning) return
        val photo = store.current()
        if (photo == null) {
            subjectDetectionMessage = "Choose a personal photo before detecting a subject."
            return
        }

        subjectDetectionRunning = true
        subjectDetectionMessage = "Preparing on-device subject detection…"
        viewModelScope.launch {
            val metrics = getApplication<Application>().resources.displayMetrics
            val source = try {
                withContext(Dispatchers.IO) {
                    store.decode(photo, metrics.widthPixels, metrics.heightPixels)
                }
            } catch (e: IllegalArgumentException) {
                Log.e("LauncherViewModel", "Could not decode the wallpaper for subject detection.", e)
                null
            }
            if (source == null) {
                subjectDetectionRunning = false
                subjectDetectionMessage = "This photo could not be decoded for subject detection."
                return@launch
            }

            val segmenter = try {
                SubjectSegmentation.getClient(
                    SubjectSegmenterOptions.Builder().enableForegroundBitmap().build(),
                )
            } catch (e: IllegalStateException) {
                source.recycle()
                subjectDetectionRunning = false
                subjectDetectionMessage =
                    "Subject detection needs Google Play services with the subject model available."
                Log.e("LauncherViewModel", "Could not initialize on-device subject detection.", e)
                return@launch
            }

            try {
                segmenter.process(InputImage.fromBitmap(source, 0))
                    .addOnSuccessListener { result ->
                        val foreground = result.foregroundBitmap
                        if (foreground == null) {
                            source.recycle()
                            segmenter.close()
                            subjectDetectionRunning = false
                            subjectDetectionMessage =
                                "No clear foreground subject was detected. Try another photo or import a transparent cutout."
                            return@addOnSuccessListener
                        }
                        viewModelScope.launch {
                            val saved = try {
                                withContext(Dispatchers.IO) {
                                    store.replaceForeground(foreground, source)
                                }
                            } catch (e: IOException) {
                                Log.e("LauncherViewModel", "Could not save the detected subject layer.", e)
                                subjectDetectionMessage = e.message
                                false
                            }
                            if (saved) {
                                shownForegroundPath = null
                                storeForegroundModified = 0L
                                reloadWallpaper()
                                GlassWallpaperService.requestRefresh(getApplication<Application>())
                                subjectDetectionMessage =
                                    "Subject separated. Use Erase / Restore to refine its edge."
                            }
                            foreground.recycle()
                            source.recycle()
                            segmenter.close()
                            subjectDetectionRunning = false
                        }
                    }
                    .addOnFailureListener { error ->
                        Log.e("LauncherViewModel", "On-device subject detection failed.", error)
                        source.recycle()
                        segmenter.close()
                        subjectDetectionRunning = false
                        subjectDetectionMessage =
                            "Subject detection is unavailable. Check Google Play services and try again, or import a transparent cutout."
                    }
            } catch (e: IllegalStateException) {
                Log.e("LauncherViewModel", "Subject detection could not start.", e)
                source.recycle()
                segmenter.close()
                subjectDetectionRunning = false
                subjectDetectionMessage =
                    "Subject detection could not start. Check Google Play services and try again."
            }
        }
    }

    fun correctSubjectMask(
        points: List<Pair<Float, Float>>,
        brushRadius: Float,
        erase: Boolean,
    ) {
        viewModelScope.launch {
            val corrected = try {
                withContext(Dispatchers.IO) {
                    store.correctForegroundMask(points, brushRadius, erase)
                }
            } catch (e: IOException) {
                Log.e("LauncherViewModel", "Could not correct the subject mask.", e)
                subjectDetectionMessage = e.message
                false
            }
            if (corrected) {
                shownForegroundPath = null
                storeForegroundModified = 0L
                reloadWallpaper()
                GlassWallpaperService.requestRefresh(getApplication<Application>())
                subjectDetectionMessage = "Mask correction saved."
            } else if (subjectDetectionMessage == null) {
                subjectDetectionMessage = "Detect a subject before editing its mask."
            }
        }
    }

    fun setForeground(uri: Uri, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val saved = try {
                withContext(Dispatchers.IO) { store.replaceForeground(uri) }
            } catch (e: IOException) {
                Log.e("LauncherViewModel", "Failed to save selected foreground image.", e)
                false
            } catch (e: SecurityException) {
                Log.e("LauncherViewModel", "Access to selected foreground image was denied.", e)
                false
            }
            if (saved) {
                shownForegroundPath = null
                storeForegroundModified = 0L
                reloadWallpaper()
                GlassWallpaperService.requestRefresh(getApplication<Application>())
            }
            onDone(saved)
        }
    }

    fun clearForeground() {
        store.clearForeground()
        shownForegroundPath = null
        storeForegroundModified = 0L
        reloadWallpaper()
        GlassWallpaperService.requestRefresh(getApplication<Application>())
    }

    private var storeForegroundModified = 0L

    private data class WallpaperImages(
        val main: ImageBitmap?,
        val mainPath: String?,
        val foreground: ImageBitmap?,
        val foregroundPath: String?,
        val foregroundModified: Long?,
        val gallery: List<WallpaperGalleryPhoto>,
        val currentIndex: Int,
        val revision: Long,
    )
}

data class WallpaperGalleryPhoto(
    val index: Int,
    val thumbnail: ImageBitmap,
)
