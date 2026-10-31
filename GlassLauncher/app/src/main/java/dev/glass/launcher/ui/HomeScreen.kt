package dev.glass.launcher.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glass.launcher.AppEntry
import dev.glass.launcher.AppRepository
import dev.glass.launcher.HomeWidgetEntry
import dev.glass.launcher.HomeWidgetStore
import dev.glass.launcher.BuiltInWidgetType
import dev.glass.launcher.LauncherViewModel
import dev.glass.launcher.settings.ClockAlignment
import dev.glass.launcher.settings.ClockFont
import dev.glass.launcher.settings.LauncherSettings
import dev.glass.launcher.ui.glass.LocalGlassMaterial
import dev.glass.launcher.ui.glass.LocalGlassQuality
import dev.glass.launcher.ui.glass.LocalReduceMotion
import dev.glass.launcher.ui.glass.rememberGlassQuality
import android.text.format.DateFormat
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    vm: LauncherViewModel,
    onPickWallpaper: () -> Unit,
    onApplyWallpaper: () -> Unit,
    onPickForeground: () -> Unit,
    onOpenCompanionPreview: () -> Unit,
    onApplyTheme: () -> Unit,
    onRequestDefaultHome: () -> Unit,
    appWidgetHost: AppWidgetHost,
    onAddWidget: (Int) -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onAddBuiltInWidget: (BuiltInWidgetType, Int) -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onRequestWeatherPermission: () -> Unit,
    onRefreshWeather: () -> Unit,
    onOpenMediaAccessSettings: () -> Unit,
    onPickCustomIcon: (String) -> Unit,
    onExportIconCollection: (String) -> Unit,
    onImportIconCollection: () -> Unit,
) {
    val context = LocalContext.current
    val launch: (AppEntry) -> Unit = { app ->
        vm.drawerOpen = false
        AppRepository.launch(context, app.packageName)
    }

    // Check system animator duration scale for reduced motion
    val animatorScale = remember(context) {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (e: Exception) {
            1f
        }
    }
    val allowAnimations = animatorScale > 0f

    // Wiggle rotation animation for Edit Mode
    val infiniteTransition = rememberInfiniteTransition(label = "wiggle")
    val wiggleAngle by if (allowAnimations && vm.isEditMode) {
        infiniteTransition.animateFloat(
            initialValue = -2f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "wiggleAngle",
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    val pagerState = rememberPagerState(pageCount = { vm.homePages.size })
    var showBuiltInWidgetPicker by remember { mutableStateOf(false) }
    var showWeatherDisclosure by remember { mutableStateOf(false) }
    var showMediaDisclosure by remember { mutableStateOf(false) }
    var pendingWeatherPage by remember { mutableStateOf(0) }

    // On a home screen, Back only closes top layer
    BackHandler {
        when {
            vm.clockEditorOpen -> vm.clockEditorOpen = false
            vm.iconEditorOpen -> vm.iconEditorOpen = false
            vm.wallpaperEditorOpen -> vm.wallpaperEditorOpen = false
            vm.settingsOpen -> vm.settingsOpen = false
            vm.menuOpen -> vm.closeContextMenu()
            vm.isEditMode -> vm.isEditMode = false
            else -> vm.drawerOpen = false
        }
    }

    val tilt = remember(vm) { { vm.tilt } }
    val settings = vm.settings
    val material = remember(settings.preset, settings.glassIntensity) { settings.material() }
    val iosStatusBar = settings.iosStatusBar
    val statusBarHeight = rememberStatusBarHeight()
    HideSystemStatusBar(iosStatusBar)

    CompositionLocalProvider(
        LocalGlassMaterial provides material,
        LocalGlassQuality provides rememberGlassQuality(),
        LocalIconSize provides settings.iconSizeDp.dp,
        LocalAppIconShape provides settings.iconShape,
        LocalAppIconCornerRadius provides settings.iconCornerRadius,
        LocalShowAppIconLabels provides settings.showIconLabels,
        LocalReduceMotion provides !allowAnimations,
    ) {
        BackdropHost(
            wallpaper = vm.wallpaper,
            tilt = tilt,
            wallpaperScale = settings.wallpaperScale,
            wallpaperX = settings.wallpaperX,
            wallpaperY = settings.wallpaperY,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        var totalY = 0f
                        detectVerticalDragGestures(
                            onDragStart = { totalY = 0f },
                            onDragEnd = {
                                if (totalY < -100f) {
                                    vm.drawerOpen = true // Swipe up -> Drawer
                                } else if (totalY > 100f) {
                                    vm.drawerOpen = true // Swipe down -> Search Drawer
                                }
                            },
                            onDragCancel = { totalY = 0f },
                            onVerticalDrag = { _, dy -> totalY += dy },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                if (!vm.isEditMode) {
                                    vm.isEditMode = true
                                }
                            }
                        )
                    },
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        // With our own bar the system one is hidden, so reserve its height ourselves.
                        .then(
                            if (iosStatusBar) Modifier.padding(top = statusBarHeight)
                            else Modifier.statusBarsPadding()
                        )
                        .navigationBarsPadding()
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { vm.settingsOpen = true }) {
                            Text("Settings", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    ClockCard(settings, onClick = { vm.clockEditorOpen = true })

                    Spacer(Modifier.height(12.dp))

                    // Edit Mode Header Controls
                    if (vm.isEditMode) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { vm.addHomePages() }) {
                                    Text("+ Page", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                if (vm.homePages.size > 1) {
                                    TextButton(onClick = {
                                        val page = pagerState.currentPage
                                        // Detach this page's widgets first so their host ids are freed.
                                        vm.widgets.filter { it.page == page }
                                            .forEach { onRemoveWidget(it.appWidgetId) }
                                        vm.removeHomePage(page)
                                    }) {
                                        Text("Remove page", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                                    }
                                }
                                TextButton(onClick = { showBuiltInWidgetPicker = true }) {
                                    Text("+ Widget", color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                                TextButton(onClick = { vm.isEditMode = false }) {
                                    Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { onAddWidget(pagerState.currentPage) }) {
                                    Text("+ Add widget", color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = onPickWallpaper) {
                                    Text("Wallpaper", color = Color.White)
                                }
                                TextButton(onClick = { vm.settingsOpen = true }) {
                                    Text("Glass settings", color = Color.White)
                                }
                                TextButton(onClick = onOpenCompanionPreview) {
                                    Text("Lock preview", color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = onRequestDefaultHome) {
                                    Text("Set as default home app", color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    vm.builtInWidgets.filter { it.page == pagerState.currentPage }.forEach { widget ->
                        BuiltInWidgetSurface(
                            widget = widget,
                            settings = settings,
                            wallpaper = vm.wallpaper,
                            events = vm.calendarEvents,
                            weather = vm.weather,
                            weatherLoading = vm.weatherLoading,
                            weatherError = vm.weatherError,
                            isEditMode = vm.isEditMode,
                            onResize = { vm.resizeBuiltInWidget(widget.id, it) },
                            onRemove = { vm.removeBuiltInWidget(widget.id) },
                            onMove = { vm.moveBuiltInWidget(widget.id, pagerState.currentPage, it) },
                            onRequestCalendarPermission = onRequestCalendarPermission,
                            onRequestWeatherPermission = onRequestWeatherPermission,
                            onRefreshWeather = onRefreshWeather,
                            onOpenMediaAccessSettings = onOpenMediaAccessSettings,
                        )
                    }

                    vm.widgets.filter { it.page == pagerState.currentPage }.forEach { widget ->
                        Spacer(Modifier.height(8.dp))
                        HomeWidgetSurface(
                            widget = widget,
                            appWidgetHost = appWidgetHost,
                            isEditMode = vm.isEditMode,
                            onResize = { delta -> vm.resizeWidget(widget.appWidgetId, delta) },
                            onRemove = { onRemoveWidget(widget.appWidgetId) },
                        )
                    }

                    // Multi-page Grid Pager
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) { pageIdx ->
                        val pageApps = vm.homePages.getOrNull(pageIdx) ?: emptyList()

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 8.dp),
                        ) {
                            if (pageApps.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (vm.isEditMode) "Empty Page\nLong press an app in drawer to add" else "Empty Home Page",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    contentPadding = PaddingValues(8.dp),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    items(pageApps, key = { it.packageName }) { app ->
                                        Box(
                                            modifier = Modifier
                                                .rotate(if (vm.isEditMode) wiggleAngle else 0f)
                                                .semantics {
                                                    customActions = listOf(
                                                        CustomAccessibilityAction("Remove from Home") {
                                                            vm.removeFromHome(app)
                                                            true
                                                        },
                                                        CustomAccessibilityAction("Add to Dock") {
                                                            vm.toggleDock(app)
                                                            true
                                                        }
                                                    )
                                                }
                                        ) {
                                            AppIcon(
                                                app = app,
                                                showLabel = true,
                                                onClick = {
                                                    if (vm.isEditMode) {
                                                        vm.openContextMenu(app)
                                                    } else {
                                                        launch(app)
                                                    }
                                                },
                                                onLongClick = { vm.openContextMenu(app) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Page Indicator Dots
                    if (vm.homePages.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            repeat(vm.homePages.size) { idx ->
                                val active = idx == pagerState.currentPage
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .size(if (active) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(if (active) Color.White else Color.White.copy(alpha = 0.4f)),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    SwipeHandle()
                    Spacer(Modifier.height(12.dp))

                    // Dock
                    Dock(
                        apps = vm.dock,
                        isEditMode = vm.isEditMode,
                        wiggleAngle = wiggleAngle,
                        onLaunch = launch,
                        onLongClickApp = { vm.openContextMenu(it) },
                        onRemoveFromDock = { vm.toggleDock(it) },
                    )
                }
            }

            // Drawer
            // The drawer grows out of the swipe handle with a spring, like glass materialising.
            val fromHandle = TransformOrigin(0.5f, 0.92f)
            AnimatedVisibility(
                visible = vm.drawerOpen,
                enter = if (allowAnimations) {
                    fadeIn(tween(180)) + scaleIn(
                        initialScale = 0.88f,
                        transformOrigin = fromHandle,
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
                    )
                } else {
                    fadeIn()
                },
                exit = if (allowAnimations) {
                    fadeOut(tween(140)) + scaleOut(
                        targetScale = 0.94f,
                        transformOrigin = fromHandle,
                        animationSpec = tween(160),
                    )
                } else {
                    fadeOut()
                },
            ) {
                // The drawer pads itself with the system insets, which are 0 at the top once the
                // system bar is hidden (a cut-out is still counted), so add only what is missing.
                val safeTop = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
                val missingTop = if (iosStatusBar && statusBarHeight > safeTop) statusBarHeight - safeTop else 0.dp
                Box(Modifier.padding(top = missingTop)) {
                    AppDrawer(
                        apps = vm.apps,
                        recentApps = vm.recentApps,
                        onLaunch = launch,
                        onLongClickApp = { vm.openContextMenu(it) },
                        onClose = { vm.drawerOpen = false },
                    )
                }
            }

            // Context Menu Overlay
            if (vm.menuOpen && vm.selectedMenuApp != null) {
                val app = vm.selectedMenuApp!!
                AppContextMenu(
                    app = app,
                    shortcuts = vm.selectedMenuShortcuts,
                    isInHome = vm.isAppInHome(app),
                    isInDock = vm.isAppInDock(app),
                    onToggleHome = {
                        if (vm.isAppInHome(app)) {
                            vm.removeFromHome(app)
                        } else {
                            vm.addToHome(app, pagerState.currentPage)
                        }
                    },
                    onToggleDock = { vm.toggleDock(app) },
                    onOpenAppInfo = { AppRepository.openAppInfo(context, app.packageName) },
                    onUninstall = { AppRepository.uninstallApp(context, app.packageName) },
                    onEnterEditMode = { vm.isEditMode = true },
                    onDismiss = { vm.closeContextMenu() },
                    onLaunchShortcut = { shortcut -> AppRepository.launchShortcut(context, shortcut) },
                )
            }

            // Topmost layer; draws only, never consumes touches.
            if (iosStatusBar) {
                Box(Modifier.fillMaxSize()) {
                    IosStatusBar(Modifier.fillMaxWidth().height(statusBarHeight))
                }
            }

            if (vm.settingsOpen) {
                LauncherSettingsSheet(
                    settings = settings,
                    onChangeWallpaper = {
                        vm.settingsOpen = false
                        onPickWallpaper()
                    },
                    onEditWallpaper = {
                        vm.settingsOpen = false
                        vm.wallpaperEditorOpen = true
                    },
                    onOpenCompanionPreview = {
                        vm.settingsOpen = false
                        onOpenCompanionPreview()
                    },
                    onEditClock = {
                        vm.settingsOpen = false
                        vm.clockEditorOpen = true
                    },
                    onEditIcons = {
                        vm.settingsOpen = false
                        vm.iconEditorOpen = true
                    },
                    onApplyTheme = {
                        vm.settingsOpen = false
                        onApplyTheme()
                    },
                    onRequestDefaultHome = {
                        vm.settingsOpen = false
                        onRequestDefaultHome()
                    },
                    onDismiss = { vm.settingsOpen = false },
                )
            }

            if (vm.clockEditorOpen) {
                ClockEditorSheet(settings) { vm.clockEditorOpen = false }
            }
            if (vm.iconEditorOpen) {
                IconEditorSheet(
                    apps = vm.apps,
                    collections = vm.iconCollections,
                    iconPacks = vm.iconPackCandidates,
                    selectedTheme = vm.selectedIconTheme(),
                    onSelectTheme = vm::selectIconTheme,
                    onApplyIcon = vm::applyIcon,
                    onApplyTheme = vm::applyIconTheme,
                    onPickCustomIcon = onPickCustomIcon,
                    onApplyIconPack = vm::applyIconPack,
                    onSaveCollection = vm::saveIconCollection,
                    onDuplicateCollection = vm::duplicateIconCollection,
                    onRenameCollection = vm::renameIconCollection,
                    onDeleteCollection = vm::deleteIconCollection,
                    onApplyCollection = vm::applyIconCollection,
                    onExportCollection = onExportIconCollection,
                    onImportCollection = onImportIconCollection,
                    onResetIcon = vm::resetIcon,
                    onResetAll = vm::resetAllIcons,
                    onDismiss = { vm.iconEditorOpen = false },
                )
            }
            if (vm.wallpaperEditorOpen) {
                WallpaperEditorSheet(
                    background = vm.wallpaper,
                    foreground = vm.foregroundWallpaper,
                    wallpapers = vm.wallpaperGallery,
                    selectedWallpaperIndex = vm.selectedWallpaperIndex,
                    shuffleOnLock = vm.shuffleOnLock,
                    settings = settings,
                    detectionRunning = vm.subjectDetectionRunning,
                    detectionMessage = vm.subjectDetectionMessage,
                    foregroundRestorable = vm.foregroundRestorable,
                    onPickWallpaper = onPickWallpaper,
                    onSelectWallpaper = vm::selectWallpaper,
                    onUpdateShuffle = vm::updateShuffleOnLock,
                    onDetectSubject = vm::detectForegroundSubject,
                    onCorrectMask = vm::correctSubjectMask,
                    onApplyWallpaper = onApplyWallpaper,
                    onPickForeground = onPickForeground,
                    onClearForeground = vm::clearForeground,
                    onDismiss = { vm.wallpaperEditorOpen = false },
                )
            }

            if (showBuiltInWidgetPicker) {
                AlertDialog(
                    onDismissRequest = { showBuiltInWidgetPicker = false },
                    title = { Text("Add a widget") },
                    text = {
                        Column {
                            BuiltInWidgetType.entries.forEach { type ->
                                TextButton(
                                    onClick = {
                                        showBuiltInWidgetPicker = false
                                        if (type == BuiltInWidgetType.WEATHER) {
                                            pendingWeatherPage = pagerState.currentPage
                                            showWeatherDisclosure = true
                                        } else if (type == BuiltInWidgetType.MUSIC) {
                                            pendingWeatherPage = pagerState.currentPage
                                            showMediaDisclosure = true
                                        } else {
                                            onAddBuiltInWidget(type, pagerState.currentPage)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(type.label, modifier = Modifier.fillMaxWidth())
                                }
                            }
                            Text(
                                "For weather and music, use + Add widget to select a widget supplied by an installed app.",
                                modifier = Modifier.padding(top = 8.dp),
                                fontSize = 12.sp,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showBuiltInWidgetPicker = false }) {
                            Text("Done")
                        }
                    },
                )
            }

            if (showWeatherDisclosure) {
                AlertDialog(
                    onDismissRequest = { showWeatherDisclosure = false },
                    title = { Text("Weather and approximate location") },
                    text = {
                        Text(
                            "To show local conditions, the app sends your approximate network location to Open-Meteo over HTTPS when you refresh weather. The coordinates are used to fetch the forecast; no location is needed for the other widgets.",
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            onAddBuiltInWidget(BuiltInWidgetType.WEATHER, pendingWeatherPage)
                            onRequestWeatherPermission()
                            showWeatherDisclosure = false
                        }) {
                            Text("Continue")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showWeatherDisclosure = false }) {
                            Text("Cancel")
                        }
                    },
                )
            }

            if (showMediaDisclosure) {
                AlertDialog(
                    onDismissRequest = { showMediaDisclosure = false },
                    title = { Text("Media playback access") },
                    text = {
                        Text(
                            "Android requires Notification access to discover media sessions and send play, pause, and next commands. This system grant can expose notifications beyond music. Only enable it if you are comfortable with that access; you can also cancel and use your music app's own home-screen widget.",
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            onAddBuiltInWidget(BuiltInWidgetType.MUSIC, pendingWeatherPage)
                            onOpenMediaAccessSettings()
                            showMediaDisclosure = false
                        }) {
                            Text("Open settings")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showMediaDisclosure = false }) {
                            Text("Cancel")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeWidgetSurface(
    widget: HomeWidgetEntry,
    appWidgetHost: AppWidgetHost,
    isEditMode: Boolean,
    onResize: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val widgetManager = remember(context) { AppWidgetManager.getInstance(context) }
    val providerInfo = remember(widget.appWidgetId) {
        widgetManager.getAppWidgetInfo(widget.appWidgetId)
    }

    Column(Modifier.fillMaxWidth()) {
        if (isEditMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onResize(-HomeWidgetStore.HEIGHT_STEP_DP) }) {
                    Text("−", color = Color.White, fontSize = 18.sp)
                }
                Text(
                    "${widget.heightDp} dp",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                )
                TextButton(onClick = { onResize(HomeWidgetStore.HEIGHT_STEP_DP) }) {
                    Text("+", color = Color.White, fontSize = 18.sp)
                }
                TextButton(onClick = onRemove) {
                    Text("Remove", color = Color(0xFFFF6B6B))
                }
            }
        }

        if (providerInfo == null) {
            Text(
                text = "This widget is no longer available.",
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(widget.heightDp.dp)
                    .padding(16.dp),
            )
        } else {
            val widgetWidthDp = (configuration.screenWidthDp - 32).coerceAtLeast(1)
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(widget.heightDp.dp),
                factory = { viewContext ->
                    appWidgetHost.createView(viewContext, widget.appWidgetId, providerInfo).apply {
                        setAppWidget(widget.appWidgetId, providerInfo)
                    }
                },
                update = { hostView ->
                    hostView.updateWidgetSize(widgetWidthDp, widget.heightDp)
                },
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun AppWidgetHostView.updateWidgetSize(widthDp: Int, heightDp: Int) {
    val options = Bundle()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        updateAppWidgetSize(options, listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
    } else {
        updateAppWidgetSize(options, widthDp, heightDp, widthDp, heightDp)
    }
}

@Composable
private fun ClockCard(settings: LauncherSettings, onClick: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }

    val clockSize by animateFloatAsState(settings.clockSizeSp, label = "clock-size")
    val clockColor by animateColorAsState(Color(settings.clockColor), label = "clock-color")
    val textAlignment = when (settings.clockAlignment) {
        ClockAlignment.START -> TextAlign.Start
        ClockAlignment.CENTER -> TextAlign.Center
        ClockAlignment.END -> TextAlign.End
    }
    val horizontalAlignment = when (settings.clockAlignment) {
        ClockAlignment.START -> Alignment.Start
        ClockAlignment.CENTER -> Alignment.CenterHorizontally
        ClockAlignment.END -> Alignment.End
    }
    val timePattern = when (settings.clockFormat) {
        1 -> "h:mm a"
        2 -> "HH:mm"
        else -> if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    }

    GlassPanel(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Clock. Tap to customize." },
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = horizontalAlignment,
        ) {
            Text(
                text = now.format(DateTimeFormatter.ofPattern(timePattern, Locale.getDefault())),
                color = clockColor,
                fontSize = clockSize.sp,
                fontWeight = FontWeight(settings.clockWeight),
                fontFamily = settings.clockFont.toFontFamily(),
                letterSpacing = settings.clockLetterSpacingSp.sp,
                textAlign = textAlignment,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                textAlign = textAlignment,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun ClockFont.toFontFamily(): FontFamily = when (this) {
    ClockFont.SANS -> FontFamily.SansSerif
    ClockFont.SERIF -> FontFamily.Serif
    ClockFont.CURSIVE -> FontFamily.Cursive
    ClockFont.MONOSPACE -> FontFamily.Monospace
}

@Composable
private fun SwipeHandle() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.55f)),
        )
    }
}

@Composable
private fun Dock(
    apps: List<AppEntry>,
    isEditMode: Boolean,
    wiggleAngle: Float,
    onLaunch: (AppEntry) -> Unit,
    onLongClickApp: (AppEntry) -> Unit,
    onRemoveFromDock: (AppEntry) -> Unit,
) {
    GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 32.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            apps.forEach { app ->
                Box(
                    modifier = Modifier
                        .rotate(if (isEditMode) wiggleAngle else 0f)
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction("Remove from Dock") {
                                    onRemoveFromDock(app)
                                    true
                                }
                            )
                        }
                ) {
                    AppIcon(
                        app = app,
                        showLabel = false,
                        onClick = {
                            if (isEditMode) {
                                onLongClickApp(app)
                            } else {
                                onLaunch(app)
                            }
                        },
                        onLongClick = { onLongClickApp(app) },
                    )
                }
            }
        }
    }
}
