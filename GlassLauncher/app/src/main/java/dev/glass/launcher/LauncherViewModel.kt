package dev.glass.launcher

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    var dock by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    var wallpaper by mutableStateOf<ImageBitmap?>(null)
        private set

    var drawerOpen by mutableStateOf(false)

    private val wallpaperFile = File(app.filesDir, "wallpaper.img")

    init {
        refreshApps()
        viewModelScope.launch {
            wallpaper = withContext(Dispatchers.IO) { decodeWallpaper() }
        }
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

    fun setWallpaper(uri: Uri) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            wallpaper = withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    wallpaperFile.outputStream().use { output -> input.copyTo(output) }
                }
                decodeWallpaper()
            }
        }
    }

    /** Decodes the saved wallpaper, downsampled so it never exceeds roughly screen size. */
    private fun decodeWallpaper(): ImageBitmap? {
        if (!wallpaperFile.exists()) return null
        val metrics = getApplication<Application>().resources.displayMetrics

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(wallpaperFile.path, bounds)

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= metrics.widthPixels &&
            bounds.outHeight / (sample * 2) >= metrics.heightPixels
        ) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(wallpaperFile.path, options)?.asImageBitmap()
    }
}
