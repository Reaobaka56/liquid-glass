package dev.glass.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.app.role.RoleManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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
    private val appWidgetHost by lazy { AppWidgetHost(this, APP_WIDGET_HOST_ID) }
    private val appWidgetManager by lazy { AppWidgetManager.getInstance(this) }
    private var pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingWidgetPage = 0

    // Pick one photo for a plain wallpaper, or several for Photo Shuffle.
    private val pickImages =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { uris ->
            if (uris.isNotEmpty()) vm.setWallpapers(uris) { offerLiveWallpaper() }
        }

    private val pickWidget =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode != RESULT_OK || data == null) {
                deletePendingWidget()
                return@registerForActivityResult
            }

            val widgetId = data.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                pendingWidgetId,
            )
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                deletePendingWidget()
                return@registerForActivityResult
            }

            pendingWidgetId = widgetId
            val provider = data.getWidgetProvider()
                ?: appWidgetManager.getAppWidgetInfo(widgetId)?.provider
            if (provider == null) {
                failPendingWidget("Could not read the selected widget provider.")
                return@registerForActivityResult
            }

            val bound = appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, provider)
            if (bound) {
                configureOrAddWidget(widgetId)
            } else {
                val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                bindWidget.launch(bindIntent)
            }
        }

    private val bindWidget =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                configureOrAddWidget(pendingWidgetId)
            } else {
                deletePendingWidget()
            }
        }

    private val configureWidget =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                vm.addWidget(pendingWidgetId, pendingWidgetPage)
                clearPendingWidget()
            } else {
                deletePendingWidget()
            }
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
                onOpenCompanionPreview = {
                    startActivity(Intent(this, LockScreenCompanionActivity::class.java))
                },
                onRequestDefaultHome = ::requestDefaultHome,
                appWidgetHost = appWidgetHost,
                onAddWidget = ::pickHomeWidget,
                onRemoveWidget = { widgetId ->
                    vm.removeWidget(widgetId)
                    appWidgetHost.deleteAppWidgetId(widgetId)
                },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        appWidgetHost.startListening()
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

    override fun onStop() {
        appWidgetHost.stopListening()
        super.onStop()
    }

    private fun pickHomeWidget(page: Int) {
        pendingWidgetId = appWidgetHost.allocateAppWidgetId()
        pendingWidgetPage = page
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
        try {
            pickWidget.launch(intent)
        } catch (e: ActivityNotFoundException) {
            failPendingWidget("No widget picker is available on this device.")
        }
    }

    private fun configureOrAddWidget(widgetId: Int) {
        val info = appWidgetManager.getAppWidgetInfo(widgetId)
        if (info == null) {
            failPendingWidget("The selected widget is no longer available.")
            return
        }
        val configureActivity = info.configure
        if (configureActivity == null) {
            vm.addWidget(widgetId, pendingWidgetPage)
            clearPendingWidget()
            return
        }

        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .setComponent(configureActivity)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        try {
            configureWidget.launch(intent)
        } catch (e: ActivityNotFoundException) {
            failPendingWidget("The widget's setup screen could not be opened.")
        }
    }

    private fun deletePendingWidget() {
        if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            appWidgetHost.deleteAppWidgetId(pendingWidgetId)
        }
        clearPendingWidget()
    }

    private fun failPendingWidget(message: String) {
        deletePendingWidget()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun clearPendingWidget() {
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingWidgetPage = 0
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

    private fun requestDefaultHome() {
        val roleManager = getSystemService(RoleManager::class.java)
        val intent = if (
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true
        ) {
            roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            Intent(Settings.ACTION_HOME_SETTINGS)
        }

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Open system Home settings to choose Glass Launcher.", Toast.LENGTH_LONG).show()
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
        const val APP_WIDGET_HOST_ID = 0x474C
        const val MAX_PHOTOS = 8
    }
}

@Suppress("DEPRECATION")
private fun Intent.getWidgetProvider(): ComponentName? =
    getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER)
