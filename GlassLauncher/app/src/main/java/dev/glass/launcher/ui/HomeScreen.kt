package dev.glass.launcher.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glass.launcher.AppEntry
import dev.glass.launcher.AppRepository
import dev.glass.launcher.LauncherViewModel
import dev.glass.launcher.ui.glass.LocalGlassMaterial
import dev.glass.launcher.ui.glass.LocalGlassQuality
import dev.glass.launcher.ui.glass.rememberGlassQuality
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    vm: LauncherViewModel,
    onPickWallpaper: () -> Unit,
    onOpenCompanionPreview: () -> Unit,
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

    // On a home screen, Back only closes top layer
    BackHandler {
        when {
            vm.settingsOpen -> vm.settingsOpen = false
            vm.menuOpen -> vm.closeContextMenu()
            vm.isEditMode -> vm.isEditMode = false
            else -> vm.drawerOpen = false
        }
    }

    val tilt = remember(vm) { { vm.tilt } }
    val settings = vm.settings
    val material = remember(settings.preset, settings.glassIntensity) { settings.material() }

    CompositionLocalProvider(
        LocalGlassMaterial provides material,
        LocalGlassQuality provides rememberGlassQuality(),
        LocalIconSize provides settings.iconSizeDp.dp,
    ) {
        BackdropHost(wallpaper = vm.wallpaper, tilt = tilt) {
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
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(16.dp),
                ) {
                    ClockCard()

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
                                    TextButton(onClick = { vm.removeHomePage(pagerState.currentPage) }) {
                                        Text("Remove page", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                                    }
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
                                TextButton(onClick = { vm.settingsOpen = true }) {
                                    Text("Glass settings", color = Color.White)
                                }
                                TextButton(onClick = onOpenCompanionPreview) {
                                    Text("Lock screen preview", color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
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
            AnimatedVisibility(
                visible = vm.drawerOpen,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                AppDrawer(
                    apps = vm.apps,
                    recentApps = vm.recentApps,
                    onLaunch = launch,
                    onLongClickApp = { vm.openContextMenu(it) },
                    onClose = { vm.drawerOpen = false },
                )
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

            if (vm.settingsOpen) {
                GlassSettingsSheet(settings = settings, onDismiss = { vm.settingsOpen = false })
            }
        }
    }
}

@Composable
private fun ClockCard() {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }

    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text(
                text = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                color = Color.White,
                fontSize = 56.sp,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
            )
        }
    }
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
