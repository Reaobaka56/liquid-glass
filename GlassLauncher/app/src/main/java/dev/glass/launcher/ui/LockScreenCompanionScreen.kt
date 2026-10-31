package dev.glass.launcher.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import dev.glass.launcher.settings.LauncherSettings
import dev.glass.launcher.settings.ClockAlignment
import dev.glass.launcher.settings.ClockFont
import dev.glass.launcher.ui.glass.LocalGlassMaterial
import dev.glass.launcher.ui.glass.LocalGlassQuality
import dev.glass.launcher.ui.glass.rememberGlassQuality
import dev.glass.launcher.ui.MusicPlayerSurface
import dev.glass.launcher.wallpaper.Parallax
import dev.glass.launcher.wallpaper.WallpaperStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity

@Composable
fun LockScreenCompanionScreen(
    onContinueToDeviceUnlock: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val settings = remember(context) { LauncherSettings(context) }
    var wallpaper by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var foreground by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showClockEditor by remember { mutableStateOf(false) }
    val material = remember(settings.preset, settings.glassIntensity) { settings.material() }
    BackHandler(showClockEditor) { showClockEditor = false }

    LaunchedEffect(context) {
        val metrics = context.resources.displayMetrics
        val images = withContext(Dispatchers.IO) {
            val store = WallpaperStore(context)
            val background = store.current()?.let {
                store.decode(it, metrics.widthPixels, metrics.heightPixels)
            }
            val subject = store.foreground()?.let {
                store.decode(it, metrics.widthPixels, metrics.heightPixels)
            }
            background?.asImageBitmap() to subject?.asImageBitmap()
        }
        wallpaper = images.first
        foreground = images.second
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
            nowMillis = System.currentTimeMillis()
        }
    }

    CompositionLocalProvider(
        LocalGlassMaterial provides material,
        LocalGlassQuality provides rememberGlassQuality(),
    ) {
        BackdropHost(
            wallpaper = wallpaper,
            wallpaperScale = settings.wallpaperScale,
            wallpaperX = settings.wallpaperX,
            wallpaperY = settings.wallpaperY,
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "WALLPAPER PREVIEW",
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(onClick = onClose) {
                            Text("Close", color = Color.White)
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        GlassPanel(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 22.dp,
                        ) {
                            Text(
                                text = "Preview only: Android's secure lock screen remains in control. The system clock may appear over this wallpaper.",
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        MusicPlayerSurface(
                            modifier = Modifier.fillMaxWidth().height(142.dp),
                            onOpenAccessSettings = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(
                                        context,
                                        "Open Android Settings > Notification access to enable music details.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } catch (e: SecurityException) {
                                    Toast.makeText(
                                        context,
                                        "Android denied access to Notification access settings.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            },
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = onContinueToDeviceUnlock,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(24.dp),
                        ) {
                            Text("Continue to device unlock")
                        }
                    }
                }

                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val clockWidth = 290.dp
                    val clockHeight = 180.dp
                    val maxX = (maxWidth - clockWidth).coerceAtLeast(0.dp)
                    val maxY = (maxHeight - clockHeight).coerceAtLeast(0.dp)
                    val maxXPx = with(density) { maxX.toPx() }.coerceAtLeast(1f)
                    val maxYPx = with(density) { maxY.toPx() }.coerceAtLeast(1f)
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
                    val localDate = LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(nowMillis),
                        java.time.ZoneId.systemDefault(),
                    )
                    val timePattern = when (settings.clockFormat) {
                        1 -> "h:mm a"
                        2 -> "HH:mm"
                        else -> if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
                    }
                    Column(
                        modifier = Modifier
                            .offset(x = maxX * settings.lockClockX, y = maxY * settings.lockClockY)
                            .width(clockWidth)
                            .pointerInput(maxXPx, maxYPx) {
                                detectDragGestures(
                                    onDragEnd = {
                                        settings.updateLockClockPosition(settings.lockClockX, settings.lockClockY)
                                    },
                                    onDragCancel = {
                                        settings.updateLockClockPosition(settings.lockClockX, settings.lockClockY)
                                    },
                                ) { change, dragAmount ->
                                    change.consume()
                                    settings.updateLockClockPosition(
                                        settings.lockClockX + dragAmount.x / maxXPx,
                                        settings.lockClockY + dragAmount.y / maxYPx,
                                        persist = false,
                                    )
                                }
                            }
                            .clickable { showClockEditor = true },
                        horizontalAlignment = horizontalAlignment,
                    ) {
                        Text(
                            text = localDate.format(
                                DateTimeFormatter.ofPattern(timePattern, Locale.getDefault()),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(settings.clockColor),
                            fontSize = settings.clockSizeSp.sp,
                            fontWeight = FontWeight(settings.clockWeight),
                            fontFamily = settings.clockFont.toFontFamily(),
                            letterSpacing = settings.clockLetterSpacingSp.sp,
                            textAlign = textAlignment,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 3f), 12f),
                            ),
                        )
                        Spacer(Modifier.height(8.dp))
                        GlassPanel(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            cornerRadius = 24.dp,
                        ) {
                            Text(
                                text = localDate.format(
                                    DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()),
                                ),
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                color = Color.White,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Text(
                            "Drag to reposition · tap to customize",
                            color = Color.White.copy(alpha = 0.62f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    foreground?.let { subject ->
                        Image(
                            bitmap = subject,
                            contentDescription = "Foreground subject layer",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                            val wallpaperScale = Parallax.ZOOM * settings.wallpaperScale
                                            scaleX = wallpaperScale * settings.subjectScale
                                            scaleY = wallpaperScale * settings.subjectScale
                                            translationX = settings.wallpaperX * size.width +
                                                wallpaperScale * settings.subjectX * size.width
                                            translationY = settings.wallpaperY * size.height +
                                                wallpaperScale * settings.subjectY * size.height
                                    alpha = settings.subjectOpacity
                                },
                            contentScale = ContentScale.Crop,
                        )
                    }
                }

                if (showClockEditor) {
                    ClockEditorSheet(settings) { showClockEditor = false }
                }
            }
        }
    }
}

private fun ClockFont.toFontFamily(): FontFamily = when (this) {
    ClockFont.SANS -> FontFamily.SansSerif
    ClockFont.SERIF -> FontFamily.Serif
    ClockFont.CURSIVE -> FontFamily.Cursive
    ClockFont.MONOSPACE -> FontFamily.Monospace
}