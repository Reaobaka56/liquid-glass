package dev.glass.launcher

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import dev.glass.launcher.ui.HomeScreen
import dev.glass.launcher.wallpaper.GlassWallpaperService

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()

    // Pick one photo for a plain wallpaper, or several for Photo Shuffle.
    private val pickImages =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { uris ->
            if (uris.isNotEmpty()) vm.setWallpapers(uris) { offerLiveWallpaper() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeScreen(
                vm = vm,
                onPickWallpaper = {
                    pickImages.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        vm.refreshApps()
        // The wallpaper service may have moved to the next shuffle photo while the screen was off.
        vm.reloadWallpaper()
    }

    override fun onResume() {
        super.onResume()
        vm.startTilt()
    }

    override fun onPause() {
        vm.stopTilt()
        super.onPause()
    }

    /**
     * The lock screen only gets the photo (and its tilt and shuffle) when Glass Wallpaper is the
     * system wallpaper, so open the system's "set wallpaper" preview if it isn't yet.
     */
    private fun offerLiveWallpaper() {
        if (vm.liveWallpaperActive()) return
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(this, GlassWallpaperService::class.java),
        )
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Device has no live wallpaper support; the home screen still uses the photo.
        }
    }

    /** Pressing the Home button while the drawer is open closes it. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            vm.drawerOpen = false
        }
    }

    private companion object {
        const val MAX_PHOTOS = 8
    }
}
