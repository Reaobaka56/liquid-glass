package dev.glass.launcher

import android.app.Application
import android.app.WallpaperManager
import android.content.ComponentName
import android.net.Uri
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    var dock by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    var homePages by mutableStateOf<List<List<AppEntry>>>(listOf(emptyList()))
        private set

    var recentApps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    var wallpaper by mutableStateOf<ImageBitmap?>(null)
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
    var showUsagePermissionDialog by mutableStateOf(false)

    val settings = LauncherSettings(app)

    private val store = WallpaperStore(app)
    private val tiltTracker = TiltTracker(app) { x, y -> tilt = Offset(x, y) }
    private var shownPath: String? = null

    init {
        refreshApps()
        reloadWallpaper()
    }

    fun refreshApps() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val loaded = withContext(Dispatchers.Default) { AppRepository.loadApps(ctx) }
            apps = loaded

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
            val savedHomeItems = withContext(Dispatchers.IO) { AppRepository.loadHomeLayout(ctx) }
            if (savedHomeItems.isNotEmpty()) {
                val maxPage = savedHomeItems.maxOfOrNull { it.page } ?: 0
                val pagesList = MutableList(maxPage + 1) { mutableListOf<AppEntry>() }
                savedHomeItems.sortedWith(compareBy({ it.page }, { it.position })).forEach { item ->
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
        viewModelScope.launch(Dispatchers.IO) {
            val items = mutableListOf<HomeItem>()
            homePages.forEachIndexed { pageIdx, pageApps ->
                pageApps.forEachIndexed { posIdx, app ->
                    items.add(HomeItem(app.packageName, pageIdx, posIdx))
                }
            }
            AppRepository.saveHomeLayout(ctx, items)
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
                if (file?.path == shownPath) return@withContext null to shownPath
                val bitmap = file?.let { store.decode(it, metrics.widthPixels, metrics.heightPixels) }
                bitmap?.asImageBitmap() to file?.path
            }
            val (image, path) = loaded
            if (image != null || path != shownPath) {
                wallpaper = image
                shownPath = path
            }
        }
    }

    fun setWallpapers(uris: List<Uri>, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.replaceWith(uris) }
            shownPath = null
            reloadWallpaper()
            onDone()
        }
    }

    fun liveWallpaperActive(): Boolean {
        val ctx = getApplication<Application>()
        val info = WallpaperManager.getInstance(ctx).wallpaperInfo ?: return false
        return info.component == ComponentName(ctx, GlassWallpaperService::class.java)
    }
}
