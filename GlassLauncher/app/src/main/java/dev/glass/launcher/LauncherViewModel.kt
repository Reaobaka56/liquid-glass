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

    var wallpaper by mutableStateOf<ImageBitmap?>(null)
        private set

    /** Phone tilt, each axis in -1..1. Read it inside draw lambdas so it doesn't recompose. */
    var tilt by mutableStateOf(Offset.Zero)
        private set

    var drawerOpen by mutableStateOf(false)

    /** Long-press menu and glass settings sheet visibility. */
    var menuOpen by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)

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
            val picked = AppRepository.dockCandidates(ctx)
                .mapNotNull { byPackage[it] }
                .toMutableList()
            for (candidate in loaded) {
                if (picked.size >= 4) break
                if (picked.none { it.packageName == candidate.packageName }) picked += candidate
            }
            dock = picked.take(4)
        }
    }

    fun startTilt() = tiltTracker.start()

    fun stopTilt() {
        tiltTracker.stop()
        tilt = Offset.Zero
    }

    /**
     * Shows the photo the wallpaper service is currently on. Call whenever the launcher comes
     * back to the front: the service may have stepped to the next Photo Shuffle picture while
     * the screen was off.
     */
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

    /** Replaces the photo set. One photo is a plain wallpaper; several shuffle on lock. */
    fun setWallpapers(uris: List<Uri>, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.replaceWith(uris) }
            shownPath = null
            reloadWallpaper()
            onDone()
        }
    }

    /** True when Glass Wallpaper is the system wallpaper, i.e. the lock screen gets it too. */
    fun liveWallpaperActive(): Boolean {
        val ctx = getApplication<Application>()
        val info = WallpaperManager.getInstance(ctx).wallpaperInfo ?: return false
        return info.component == ComponentName(ctx, GlassWallpaperService::class.java)
    }
}
