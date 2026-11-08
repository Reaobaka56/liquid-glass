package dev.glass.launcher

import android.annotation.SuppressLint
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
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()
    private val appWidgetHost by lazy { AppWidgetHost(this, APP_WIDGET_HOST_ID) }
    private val appWidgetManager by lazy { AppWidgetManager.getInstance(this) }
    private var pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingWidgetPage = 0
    private var applyRequested = false
    private var awaitingWallpaperConfirmation = false
    private var pendingCustomIconPackage: String? = null
    private var pendingIconCollectionExport: String? = null

    // Pick one photo for a plain wallpaper, or several for Photo Shuffle.
    private val pickImages =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { uris ->
            if (uris.isNotEmpty()) {
                vm.setWallpapers(uris) { saved ->
                    if (saved) {
                        offerLiveWallpaper()
                    } else {
                        applyRequested = false
                        Toast.makeText(
                            this,
                            "The selected wallpaper could not be saved. Your current wallpaper was kept.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            } else if (applyRequested) {
                applyRequested = false
                Toast.makeText(this, "No wallpaper was selected; nothing was applied.", Toast.LENGTH_LONG).show()
            }
        }

    private val pickForeground =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                vm.setForeground(uri) { saved ->
                    if (!saved) {
                        Toast.makeText(
                            this,
                            "Could not save that foreground image. The previous layer was kept.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }

    private val pickCustomIcon =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val packageName = pendingCustomIconPackage
            pendingCustomIconPackage = null
            if (uri != null && packageName != null) {
                vm.applyCustomIcon(packageName, uri) { saved ->
                    if (!saved) Toast.makeText(this, "Could not apply that image as the app icon.", Toast.LENGTH_LONG).show()
                }
            }
        }

    private val exportIconCollection =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val serialized = pendingIconCollectionExport
            pendingIconCollectionExport = null
            if (uri == null || serialized == null) return@registerForActivityResult
            try {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(serialized)
                } ?: throw IOException("Could not open the selected export destination.")
                Toast.makeText(this, "Icon collection exported.", Toast.LENGTH_LONG).show()
            } catch (e: IOException) {
                Toast.makeText(this, e.message ?: "Could not export the icon collection.", Toast.LENGTH_LONG).show()
            } catch (e: SecurityException) {
                Toast.makeText(this, "Access to the selected export destination was denied.", Toast.LENGTH_LONG).show()
            }
        }

    private val importIconCollection =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val serialized = contentResolver.openInputStream(uri)?.use { input ->
                    val content = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > 12 * 1024 * 1024) {
                            throw IOException("The selected icon collection is larger than 12 MB.")
                        }
                        content.write(buffer, 0, read)
                    }
                    content.toString(StandardCharsets.UTF_8.name())
                }
                    ?: throw IOException("Could not open the selected collection file.")
                vm.importIconCollection(serialized) { success, message ->
                    Toast.makeText(
                        this,
                        if (success) "Icon collection restored."
                        else message ?: "Could not restore the icon collection.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } catch (e: IOException) {
                Toast.makeText(this, e.message ?: "Could not read the icon collection.", Toast.LENGTH_LONG).show()
            } catch (e: SecurityException) {
                Toast.makeText(this, "Access to the selected collection file was denied.", Toast.LENGTH_LONG).show()
            }
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

    // The role dialog needs a real startActivityForResult (it reads the calling package).
    private val requestHomeRole =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (isDefaultHome()) {
                if (applyRequested && vm.liveWallpaperActive()) {
                    Toast.makeText(
                        this,
                        "Applied: Glass Launcher is the default home app and Glass Wallpaper is active.",
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    Toast.makeText(this, "Glass Launcher is now the default home app.", Toast.LENGTH_LONG).show()
                }
                applyRequested = false
            } else if (applyRequested) {
                applyRequested = false
                Toast.makeText(
                    this,
                    "The launcher was not changed. Select Glass Launcher in Android's Home app settings.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    private val requestCalendarPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            vm.refreshCalendarEvents()
            if (!granted) {
                Toast.makeText(this, "Calendar events remain hidden until calendar access is allowed.", Toast.LENGTH_LONG).show()
            }
        }

    private val requestWeatherLocation =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                vm.refreshWeather()
            } else {
                Toast.makeText(this, "Weather needs approximate location to load local conditions.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The widget picker is another app, so we can be killed while it is open.
        pendingWidgetId = savedInstanceState?.getInt(STATE_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        pendingWidgetPage = savedInstanceState?.getInt(STATE_WIDGET_PAGE, 0) ?: 0
        applyRequested = savedInstanceState?.getBoolean(STATE_APPLY_REQUESTED, false) ?: false
        awaitingWallpaperConfirmation =
            savedInstanceState?.getBoolean(STATE_WALLPAPER_CONFIRMATION, false) ?: false
        enableEdgeToEdge()
        setContent {
            HomeScreen(
                vm = vm,
                onPickWallpaper = {
                    pickImages.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onApplyWallpaper = { offerLiveWallpaper(force = true) },
                onPickForeground = {
                    pickForeground.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onOpenCompanionPreview = {
                    startActivity(Intent(this, LockScreenCompanionActivity::class.java))
                },
                onApplyTheme = ::applySupportedTheme,
                onRequestDefaultHome = ::requestDefaultHome,
                appWidgetHost = appWidgetHost,
                onAddWidget = ::pickHomeWidget,
                onRemoveWidget = { widgetId ->
                    vm.removeWidget(widgetId)
                    appWidgetHost.deleteAppWidgetId(widgetId)
                },
                onAddBuiltInWidget = { type, page -> vm.addBuiltInWidget(type, page) },
                onRequestCalendarPermission = {
                    requestCalendarPermission.launch(android.Manifest.permission.READ_CALENDAR)
                },
                onRequestWeatherPermission = {
                    requestWeatherLocation.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                },
                onRefreshWeather = vm::refreshWeather,
                onOpenMediaAccessSettings = ::openMediaAccessSettings,
                onPickCustomIcon = { packageName ->
                    pendingCustomIconPackage = packageName
                    pickCustomIcon.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onExportIconCollection = { name ->
                    vm.exportIconCollection(name) { serialized, error ->
                        if (serialized == null) {
                            Toast.makeText(this, error ?: "Could not export the icon collection.", Toast.LENGTH_LONG).show()
                        } else {
                            pendingIconCollectionExport = serialized
                            val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64)
                            exportIconCollection.launch("$safeName.json")
                        }
                    }
                },
                onImportIconCollection = {
                    importIconCollection.launch(arrayOf("application/json", "text/json"))
                },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        appWidgetHost.startListening()
        vm.refreshApps()
        vm.refreshCalendarEvents()
        // The wallpaper service may have moved to the next shuffle photo while the screen was off.
        vm.reloadWallpaper()
    }

    override fun onResume() {
        super.onResume()
        vm.startTilt()
        if (awaitingWallpaperConfirmation) {
            awaitingWallpaperConfirmation = false
            if (vm.liveWallpaperActive()) {
                if (applyRequested) {
                    requestDefaultHome()
                } else {
                    Toast.makeText(this, "Glass Wallpaper is active as the system wallpaper.", Toast.LENGTH_LONG).show()
                }
            } else {
                applyRequested = false
                Toast.makeText(
                    this,
                    "Wallpaper was not applied. Android keeps the current system wallpaper.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    override fun onPause() {
        vm.stopTilt()
        super.onPause()
    }

    override fun onStop() {
        appWidgetHost.stopListening()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_WIDGET_ID, pendingWidgetId)
        outState.putInt(STATE_WIDGET_PAGE, pendingWidgetPage)
        outState.putBoolean(STATE_APPLY_REQUESTED, applyRequested)
        outState.putBoolean(STATE_WALLPAPER_CONFIRMATION, awaitingWallpaperConfirmation)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Needed for AppWidgetHost.startAppWidgetConfigureActivityForResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CONFIGURE_WIDGET) return
        if (resultCode == RESULT_OK && pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            vm.addWidget(pendingWidgetId, pendingWidgetPage)
            clearPendingWidget()
        } else {
            deletePendingWidget()
        }
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

        // Launch through the host: config activities are often not exported, so starting the
        // component directly throws SecurityException for them.
        try {
            appWidgetHost.startAppWidgetConfigureActivityForResult(
                this, widgetId, 0, REQUEST_CONFIGURE_WIDGET, null,
            )
        } catch (e: ActivityNotFoundException) {
            failPendingWidget("The widget's setup screen could not be opened.")
        } catch (e: SecurityException) {
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
    private fun offerLiveWallpaper(force: Boolean = false) {
        if (vm.liveWallpaperActive() && !force) {
            GlassWallpaperService.requestRefresh(this)
            if (applyRequested) {
                requestDefaultHome()
            } else {
                Toast.makeText(this, "Wallpaper updated on the active Glass Wallpaper.", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (!vm.hasSavedWallpaper()) {
            pickImages.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
            return
        }
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(this, GlassWallpaperService::class.java),
        )
        try {
            startActivity(intent)
            awaitingWallpaperConfirmation = true
        } catch (e: ActivityNotFoundException) {
            applyRequested = false
            Toast.makeText(
                this,
                "This device does not provide a live-wallpaper picker. The launcher preview still uses your photo.",
                Toast.LENGTH_LONG,
            ).show()
        } catch (e: SecurityException) {
            awaitingWallpaperConfirmation = false
            applyRequested = false
            Toast.makeText(this, "Android denied access to the live-wallpaper picker.", Toast.LENGTH_LONG).show()
        }
    }

    private fun applySupportedTheme() {
        applyRequested = true
        when {
            !vm.hasSavedWallpaper() -> pickImages.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
            !vm.liveWallpaperActive() -> offerLiveWallpaper()
            else -> requestDefaultHome()
        }
    }

    @SuppressLint("NewApi")
    private fun requestDefaultHome() {
        val roleManager = getSystemService(RoleManager::class.java)
        val roleAvailable = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true
        if (roleAvailable && roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
            val message = if (applyRequested && vm.liveWallpaperActive()) {
                "Already applied: Glass Launcher is the default home app and Glass Wallpaper is active."
            } else {
                "Glass Launcher is already the default home app."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            applyRequested = false
            return
        }
        val intent = if (roleAvailable) {
            roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            Intent(Settings.ACTION_HOME_SETTINGS)
        }

        try {
            requestHomeRole.launch(intent)
        } catch (e: ActivityNotFoundException) {
            applyRequested = false
            Toast.makeText(this, "Open system Home settings to choose Glass Launcher.", Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            applyRequested = false
            Toast.makeText(this, "Android denied access to Home app settings.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openMediaAccessSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Open Android Settings > Notification access to enable media controls.", Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Android denied access to Notification access settings.", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("NewApi")
    private fun isDefaultHome(): Boolean {
        val roleManager = getSystemService(RoleManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true
        ) {
            return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(homeIntent, 0)?.activityInfo?.packageName == packageName
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
        const val REQUEST_CONFIGURE_WIDGET = 0x474D
        const val STATE_WIDGET_ID = "pending_widget_id"
        const val STATE_WIDGET_PAGE = "pending_widget_page"
        const val STATE_APPLY_REQUESTED = "apply_requested"
        const val STATE_WALLPAPER_CONFIRMATION = "wallpaper_confirmation"
        const val MAX_PHOTOS = 8
    }
}

@Suppress("DEPRECATION")
private fun Intent.getWidgetProvider(): ComponentName? =
    getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER)
