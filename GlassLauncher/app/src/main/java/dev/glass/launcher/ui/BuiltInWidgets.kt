package dev.glass.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.CalendarContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateFormat
import dev.glass.launcher.BuiltInWidgetEntry
import dev.glass.launcher.BuiltInWidgetSize
import dev.glass.launcher.BuiltInWidgetType
import dev.glass.launcher.CalendarEvent
import dev.glass.launcher.CalendarRepository
import dev.glass.launcher.MediaSessionRepository
import dev.glass.launcher.MediaSnapshot
import dev.glass.launcher.WeatherRepository
import dev.glass.launcher.WeatherSnapshot
import dev.glass.launcher.settings.ClockFont
import dev.glass.launcher.settings.LauncherSettings
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BuiltInWidgetSurface(
    widget: BuiltInWidgetEntry,
    settings: LauncherSettings,
    wallpaper: ImageBitmap?,
    events: List<CalendarEvent>,
    weather: WeatherSnapshot?,
    weatherLoading: Boolean,
    weatherError: String?,
    isEditMode: Boolean,
    onResize: (Int) -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onRequestWeatherPermission: () -> Unit,
    onRefreshWeather: () -> Unit,
    onOpenMediaAccessSettings: () -> Unit,
) {
    val context = LocalContext.current
    val height = widget.size.heightDp.dp
    Column(
        (if (widget.size == BuiltInWidgetSize.SMALL) Modifier.width(160.dp) else Modifier.fillMaxWidth())
            .padding(bottom = 8.dp)
            .pointerInput(widget.id, isEditMode) {
                if (isEditMode) {
                    var dragDistance = 0f
                    detectDragGestures(
                        onDragEnd = {
                            when {
                                dragDistance < -48f -> onMove(-1)
                                dragDistance > 48f -> onMove(1)
                            }
                            dragDistance = 0f
                        },
                        onDragCancel = { dragDistance = 0f },
                    ) { change, amount ->
                        change.consume()
                        dragDistance += amount.y
                    }
                }
            },
    ) {
        if (isEditMode) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onMove(-1) }) { Text("↑", color = Color.White) }
                TextButton(onClick = { onMove(1) }) { Text("↓", color = Color.White) }
                TextButton(onClick = { onResize(-1) }) { Text("− size", color = Color.White) }
                Text(widget.size.label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                TextButton(onClick = { onResize(1) }) { Text("+ size", color = Color.White) }
                TextButton(onClick = onRemove) { Text("Remove", color = Color(0xFFFF6B6B)) }
            }
        }

        when (widget.type) {
            BuiltInWidgetType.CLOCK -> ClockWidget(widget, settings, height, context)
            BuiltInWidgetType.BATTERY -> BatteryWidget(height)
            BuiltInWidgetType.CALENDAR -> CalendarWidget(
                height = height,
                events = events,
                hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CALENDAR,
                ) == PackageManager.PERMISSION_GRANTED,
                onRequestPermission = onRequestCalendarPermission,
            )
            BuiltInWidgetType.PHOTO -> PhotoWidget(wallpaper, height)
            BuiltInWidgetType.WEATHER -> WeatherWidget(
                height = height,
                weather = weather,
                loading = weatherLoading,
                error = weatherError,
                hasLocationPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED,
                onRequestPermission = onRequestWeatherPermission,
                onRefresh = onRefreshWeather,
            )
            BuiltInWidgetType.MUSIC -> MusicWidget(
                height = height,
                onOpenAccessSettings = onOpenMediaAccessSettings,
            )
        }
    }
}

@Composable
private fun MusicWidget(
    height: androidx.compose.ui.unit.Dp,
    onOpenAccessSettings: () -> Unit,
) {
    MusicPlayerSurface(
        modifier = Modifier.fillMaxWidth().height(height),
        onOpenAccessSettings = onOpenAccessSettings,
    )
}

@Composable
fun MusicPlayerSurface(
    modifier: Modifier = Modifier,
    onOpenAccessSettings: () -> Unit,
) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(MediaSessionRepository.hasNotificationAccess(context)) }
    var track by remember { mutableStateOf<MediaSnapshot?>(null) }
    var sessionError by remember { mutableStateOf(false) }
    DisposableEffect(context, hasAccess) {
        if (!hasAccess) {
            track = null
            onDispose { }
        } else {
            val observation = try {
                MediaSessionRepository.observe(context) {
                    track = it
                    sessionError = false
                }
            } catch (e: SecurityException) {
                sessionError = true
                null
            } catch (e: IllegalStateException) {
                sessionError = true
                null
            }
            onDispose { observation?.close() }
        }
    }
    LaunchedEffect(context) {
        while (true) {
            hasAccess = MediaSessionRepository.hasNotificationAccess(context)
            sessionError = false
            track = try {
                if (hasAccess) MediaSessionRepository.snapshot(context) else null
            } catch (e: SecurityException) {
                sessionError = true
                null
            }
            delay(1_500L)
        }
    }

    val currentTrack = track
    val artwork = remember(currentTrack?.artwork) { currentTrack?.artwork?.asImageBitmap() }
    val accent = remember(currentTrack?.artwork) {
        currentTrack?.artwork?.let { bitmap ->
            Color(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
        } ?: Color(0xFF39C8E8)
    }

    BoxWithConstraints(modifier) {
        val compact = maxHeight < 120.dp
        GlassPanel(Modifier.fillMaxSize(), cornerRadius = 26.dp, interactive = false) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                accent.copy(alpha = 0.24f),
                                Color(0xFF10131B).copy(alpha = 0.58f),
                                Color(0xFF080A10).copy(alpha = 0.76f),
                            ),
                        ),
                    ),
            ) {
                Column(
                    Modifier.fillMaxSize()
                        .padding(horizontal = if (compact) 9.dp else 14.dp, vertical = if (compact) 5.dp else 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                    ) {
                        Box(
                            Modifier
                                .size(if (compact) 46.dp else 64.dp)
                                .clip(RoundedCornerShape(if (compact) 13.dp else 17.dp))
                                .background(Color.White.copy(alpha = 0.10f))
                                .border(
                                    1.dp,
                                    Brush.linearGradient(
                                        listOf(Color.White.copy(alpha = 0.74f), accent.copy(alpha = 0.28f)),
                                    ),
                                    RoundedCornerShape(if (compact) 13.dp else 17.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Crossfade(targetState = artwork, label = "media-artwork") { image ->
                                if (image != null) {
                                    Image(
                                        bitmap = image,
                                        contentDescription = "Album artwork",
                                        modifier = Modifier.fillMaxSize().clip(
                                            RoundedCornerShape(if (compact) 12.dp else 16.dp),
                                        ),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Text("♪", color = Color.White.copy(alpha = 0.86f), fontSize = 28.sp)
                                }
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (currentTrack == null) "MUSIC" else if (currentTrack.playing) "NOW PLAYING" else "PAUSED",
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.2.sp,
                            )
                            when {
                                !hasAccess || sessionError -> TextButton(
                                    onClick = onOpenAccessSettings,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                ) {
                                    Text(
                                        "Enable notification access",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                currentTrack == null -> Text(
                                    "Play music to see track details",
                                    color = Color.White.copy(alpha = 0.76f),
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                else -> {
                                    Text(
                                        currentTrack.title,
                                        color = Color.White,
                                        fontSize = if (compact) 13.sp else 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        currentTrack.artist,
                                        color = Color.White.copy(alpha = 0.78f),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (!compact) {
                                        currentTrack.album?.let { album ->
                                            Text(
                                                album,
                                                color = Color.White.copy(alpha = 0.58f),
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (compact && currentTrack?.canTogglePlayback == true) {
                            TextButton(
                                onClick = { MediaSessionRepository.togglePlayback(context) },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            ) {
                                Text(if (currentTrack.playing) "Ⅱ" else "▶", color = Color.White, fontSize = 18.sp)
                            }
                        }
                    }
                    if (currentTrack != null && hasAccess && !compact) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (currentTrack.canSkipPrevious) {
                                TextButton(
                                    onClick = { MediaSessionRepository.skipPrevious(context) },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                                ) { Text("⏮", color = Color.White, fontSize = 17.sp) }
                            }
                            if (currentTrack.canTogglePlayback) {
                                TextButton(
                                    onClick = { MediaSessionRepository.togglePlayback(context) },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                                ) {
                                    Text(if (currentTrack.playing) "Ⅱ" else "▶", color = Color.White, fontSize = 20.sp)
                                }
                            }
                            if (currentTrack.canSkipNext) {
                                TextButton(
                                    onClick = { MediaSessionRepository.skipNext(context) },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                                ) { Text("⏭", color = Color.White, fontSize = 17.sp) }
                            }
                        }
                    }
                }
            }
}

@Composable
private fun ClockWidget(
    widget: BuiltInWidgetEntry,
    settings: LauncherSettings,
    height: androidx.compose.ui.unit.Dp,
    context: Context,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(widget.id) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    val pattern = when (settings.clockFormat) {
        1 -> "h:mm a"
        2 -> "HH:mm"
        else -> if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    }
    GlassPanel(Modifier.fillMaxWidth().height(height), cornerRadius = 24.dp) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                now.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault())),
                color = Color(settings.clockColor),
                fontSize = (settings.clockSizeSp * 0.62f).coerceIn(26f, 64f).sp,
                fontWeight = FontWeight(settings.clockWeight),
                fontFamily = settings.clockFont.toFontFamily(),
                letterSpacing = settings.clockLetterSpacingSp.sp,
            )
            Text(
                now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun BatteryWidget(height: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    var percent by remember { mutableIntStateOf(readBatteryPercent(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                percent = readBatteryPercent(context, intent)
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }
    GlassPanel(Modifier.fillMaxWidth().height(height), cornerRadius = 24.dp) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("BATTERY", color = Color.White.copy(alpha = 0.68f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text("$percent%", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light)
            }
            BatteryGlyph(percent)
        }
    }
}

@Composable
private fun BatteryGlyph(percent: Int) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.13f))
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.18f))
                .padding(3.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((24 * percent / 100).coerceAtLeast(2).dp)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (percent <= 20) Color(0xFFFF6259) else Color(0xFF75E6A3)),
            )
        }
    }
}

@Composable
private fun CalendarWidget(
    height: androidx.compose.ui.unit.Dp,
    events: List<CalendarEvent>,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    val context = LocalContext.current
    var today by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            today = LocalDateTime.now()
            delay(60_000L)
        }
    }
    GlassPanel(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clickable {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).setData(CalendarContract.CONTENT_URI),
                )
            },
        cornerRadius = 24.dp,
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    today.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).uppercase(),
                    color = Color(0xFFFF817B),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(today.dayOfMonth.toString(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
            }
            Text(
                today.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 12.sp,
            )
            if (hasPermission) {
                val visibleEvents = events.take(if (height >= BuiltInWidgetSize.LARGE.heightDp.dp) 3 else 1)
                if (visibleEvents.isEmpty()) {
                    Text("No upcoming events", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                } else {
                    visibleEvents.forEach { event ->
                        Text(
                            "${CalendarRepository.eventTimeLabel(event.startsAt)}  ${event.title}",
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                TextButton(onClick = onRequestPermission) {
                    Text("Allow calendar events", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PhotoWidget(wallpaper: ImageBitmap?, height: androidx.compose.ui.unit.Dp) {
    GlassPanel(Modifier.fillMaxWidth().height(height), cornerRadius = 24.dp) {
        if (wallpaper != null) {
            Image(
                bitmap = wallpaper,
                contentDescription = "Selected wallpaper photo",
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Choose a wallpaper photo first", color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun WeatherWidget(
    height: androidx.compose.ui.unit.Dp,
    weather: WeatherSnapshot?,
    loading: Boolean,
    error: String?,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
) {
    GlassPanel(Modifier.fillMaxWidth().height(height), cornerRadius = 24.dp) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("LOCAL WEATHER", color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                when {
                    !hasLocationPermission -> TextButton(onClick = onRequestPermission) {
                        Text("Grant location and refresh", color = Color.White, fontSize = 12.sp)
                    }
                    loading -> Text("Updating…", color = Color.White, fontSize = 14.sp)
                    weather != null -> {
                        Text(
                            "${weather.temperatureC.toInt()}°",
                            color = Color.White,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Light,
                        )
                        Text(
                            "${WeatherRepository.summary(weather.weatherCode)} · Feels like ${weather.apparentTemperatureC.toInt()}°",
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Humidity ${weather.humidityPercent}% · Open-Meteo",
                            color = Color.White.copy(alpha = 0.62f),
                            fontSize = 10.sp,
                        )
                    }
                    else -> {
                        Text(error ?: "Weather not loaded", color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp)
                        TextButton(onClick = onRefresh) { Text("Retry", color = Color.White) }
                    }
                }
            }
            if (hasLocationPermission && !loading) {
                TextButton(onClick = onRefresh) { Text("↻", color = Color.White, fontSize = 20.sp) }
            }
        }
    }
}

private fun readBatteryPercent(context: Context, intent: Intent? = null): Int {
    val battery = intent ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = battery?.getIntExtra("level", -1) ?: -1
    val scale = battery?.getIntExtra("scale", -1) ?: -1
    return if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else 0
}

private fun ClockFont.toFontFamily() = when (this) {
    ClockFont.SANS -> androidx.compose.ui.text.font.FontFamily.SansSerif
    ClockFont.SERIF -> androidx.compose.ui.text.font.FontFamily.Serif
    ClockFont.CURSIVE -> androidx.compose.ui.text.font.FontFamily.Cursive
    ClockFont.MONOSPACE -> androidx.compose.ui.text.font.FontFamily.Monospace
}
