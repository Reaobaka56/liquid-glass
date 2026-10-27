package dev.glass.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glass.launcher.AppEntry
import dev.glass.launcher.AppRepository
import dev.glass.launcher.LauncherViewModel
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    vm: LauncherViewModel,
    onPickWallpaper: () -> Unit,
) {
    val context = LocalContext.current
    val launch: (AppEntry) -> Unit = { app ->
        vm.drawerOpen = false
        AppRepository.launch(context, app.packageName)
    }

    // On a home screen, Back only closes the drawer; it never leaves the launcher.
    BackHandler { vm.drawerOpen = false }

    BackdropHost(wallpaper = vm.wallpaper) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -120f) vm.drawerOpen = true },
                        onDragCancel = { total = 0f },
                        onVerticalDrag = { _, dy -> total += dy },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onPickWallpaper() })
                },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(20.dp),
            ) {
                ClockCard()
                Spacer(Modifier.weight(1f))
                SwipeHandle()
                Spacer(Modifier.height(14.dp))
                Dock(vm.dock, launch)
            }
        }

        AnimatedVisibility(
            visible = vm.drawerOpen,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            AppDrawer(apps = vm.apps, onLaunch = launch)
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
private fun Dock(apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 32.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            apps.forEach { app ->
                AppIcon(app = app, showLabel = false, onClick = { onLaunch(app) })
            }
        }
    }
}
