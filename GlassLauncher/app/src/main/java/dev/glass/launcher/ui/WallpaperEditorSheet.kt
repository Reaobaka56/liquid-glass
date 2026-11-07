package dev.glass.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.glass.launcher.WallpaperGalleryPhoto
import dev.glass.launcher.settings.ClockAlignment
import dev.glass.launcher.settings.ClockFont
import dev.glass.launcher.settings.LauncherSettings
import dev.glass.launcher.wallpaper.Parallax
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

@Composable
fun WallpaperEditorSheet(
    background: ImageBitmap?,
    foreground: ImageBitmap?,
    wallpapers: List<WallpaperGalleryPhoto>,
    selectedWallpaperIndex: Int,
    shuffleOnLock: Boolean,
    settings: LauncherSettings,
    detectionRunning: Boolean,
    detectionMessage: String?,
    foregroundRestorable: Boolean,
    onPickWallpaper: () -> Unit,
    onSelectWallpaper: (Int) -> Unit,
    onUpdateShuffle: (Boolean) -> Unit,
    onDetectSubject: () -> Unit,
    onCorrectMask: (List<Pair<Float, Float>>, Float, Boolean) -> Unit,
    onApplyWallpaper: () -> Unit,
    onPickForeground: () -> Unit,
    onClearForeground: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var maskEditing by remember { mutableStateOf(false) }
    var eraseMask by remember { mutableStateOf(true) }
    var brushRadius by remember { mutableStateOf(0.035f) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            kotlinx.coroutines.delay(60_000L)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        GlassPanel(
            Modifier.fillMaxSize().padding(12.dp),
            cornerRadius = 30.dp,
            interactive = false,
        ) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "WALLPAPER STUDIO",
                    color = Color(0xFF63D9F0),
                    fontSize = 12.sp,
                    letterSpacing = 1.8.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "A photo, a quiet clock, and a subject that rises above it.",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 5.dp, bottom = 16.dp),
                )

                Text("STOCK COLLECTION", color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, letterSpacing = 1.3.sp)
                GlassPanel(
                    Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 15.dp),
                    cornerRadius = 19.dp,
                    interactive = false,
                ) {
                    Text(
                        "No bundled stock wallpapers are present in this build. Add photos below; design-reference images are kept separate.",
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("YOUR PHOTOS", color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, letterSpacing = 1.3.sp)
                    TextButton(onClick = onPickWallpaper) { Text("＋ Import", color = Color(0xFF63D9F0)) }
                }
                if (wallpapers.isEmpty()) {
                    Text(
                        "Your private wallpaper gallery is empty.",
                        color = Color.White.copy(alpha = 0.68f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 14.dp),
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        wallpapers.forEach { photo ->
                            Column(
                                Modifier.width(72.dp).clickable { onSelectWallpaper(photo.index) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Image(
                                    bitmap = photo.thumbnail,
                                    contentDescription = "Personal wallpaper ${photo.index + 1}",
                                    modifier = Modifier
                                        .size(width = 72.dp, height = 108.dp)
                                        .clip(RoundedCornerShape(15.dp))
                                        .border(
                                            if (photo.index == selectedWallpaperIndex) 2.dp else 1.dp,
                                            if (photo.index == selectedWallpaperIndex) {
                                                Color(0xFF63D9F0)
                                            } else {
                                                Color.White.copy(alpha = 0.22f)
                                            },
                                            RoundedCornerShape(15.dp),
                                        ),
                                    contentScale = ContentScale.Crop,
                                )
                                Text(
                                    "Photo ${photo.index + 1}",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 5.dp),
                                )
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Photo Shuffle", color = Color.White, fontSize = 14.sp)
                        Text(
                            "Advance to another saved photo when the screen locks.",
                            color = Color.White.copy(alpha = 0.58f),
                            fontSize = 11.sp,
                        )
                    }
                    Switch(
                        checked = shuffleOnLock,
                        onCheckedChange = onUpdateShuffle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF43C7E3),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.24f),
                            uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }

                Row(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) {
                    Button(
                        onClick = onApplyWallpaper,
                        enabled = background != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF37BFD9),
                            contentColor = Color(0xFF07151B),
                            disabledContainerColor = Color.White.copy(alpha = 0.08f),
                            disabledContentColor = Color.White.copy(alpha = 0.4f),
                        ),
                    ) { Text("Preview & apply") }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "DEPTH COMPOSITION",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        letterSpacing = 1.3.sp,
                    )
                    Text(
                        "BACKGROUND  ·  CLOCK  ·  SUBJECT",
                        color = Color(0xFF63D9F0).copy(alpha = 0.82f),
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp,
                    )
                }

                Box(
                    Modifier.fillMaxWidth().height(440.dp).padding(top = 10.dp, bottom = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.width(232.dp).height(430.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color(0xFF141820))
                            .border(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.72f), Color.White.copy(alpha = 0.08f)),
                                ),
                                RoundedCornerShape(28.dp),
                            ),
                    ) {
                        if (background != null) {
                            Image(
                                bitmap = background,
                                contentDescription = "Selected wallpaper background layer",
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    scaleX = if (maskEditing) 1f else Parallax.ZOOM * settings.wallpaperScale
                                    scaleY = if (maskEditing) 1f else Parallax.ZOOM * settings.wallpaperScale
                                    translationX = if (maskEditing) 0f else settings.wallpaperX * size.width
                                    translationY = if (maskEditing) 0f else settings.wallpaperY * size.height
                                },
                                contentScale = ContentScale.Crop,
                            )
                        }

                        val clockWidth = 210.dp
                        val clockHeight = 100.dp
                        val maxX = (232.dp - clockWidth).coerceAtLeast(0.dp)
                        val maxY = (430.dp - clockHeight).coerceAtLeast(0.dp)
                        val maxXPx = with(density) { maxX.toPx() }.coerceAtLeast(1f)
                        val maxYPx = with(density) { maxY.toPx() }.coerceAtLeast(1f)
                        val alignment = when (settings.clockAlignment) {
                            ClockAlignment.START -> TextAlign.Start
                            ClockAlignment.CENTER -> TextAlign.Center
                            ClockAlignment.END -> TextAlign.End
                        }
                        val horizontalAlignment = when (settings.clockAlignment) {
                            ClockAlignment.START -> Alignment.Start
                            ClockAlignment.CENTER -> Alignment.CenterHorizontally
                            ClockAlignment.END -> Alignment.End
                        }
                        Column(
                            Modifier.offset(
                                x = maxX * settings.lockClockX,
                                y = maxY * settings.lockClockY,
                            )
                                .width(clockWidth)
                                .pointerInput(maxXPx, maxYPx) {
                                    detectDragGestures(
                                        onDragEnd = {
                                            settings.updateLockClockPosition(settings.lockClockX, settings.lockClockY)
                                        },
                                        onDragCancel = {
                                            settings.updateLockClockPosition(settings.lockClockX, settings.lockClockY)
                                        },
                                    ) { change, amount ->
                                        change.consume()
                                        settings.updateLockClockPosition(
                                            settings.lockClockX + amount.x / maxXPx,
                                            settings.lockClockY + amount.y / maxYPx,
                                            persist = false,
                                        )
                                    }
                                },
                            horizontalAlignment = horizontalAlignment,
                        ) {
                            Text(
                                text = now.format(
                                    DateTimeFormatter.ofPattern(
                                        when (settings.clockFormat) {
                                            1 -> "h:mm a"
                                            2 -> "HH:mm"
                                            else -> if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
                                        },
                                        Locale.getDefault(),
                                    ),
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(settings.clockColor),
                                fontSize = settings.clockSizeSp.coerceIn(40f, 84f).sp,
                                fontWeight = FontWeight(settings.clockWeight),
                                fontFamily = settings.clockFont.toFontFamily(),
                                letterSpacing = settings.clockLetterSpacingSp.sp,
                                textAlign = alignment,
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = Shadow(Color.Black.copy(alpha = 0.44f), Offset(0f, 3f), 12f),
                                ),
                            )
                            Text(
                                now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                                modifier = Modifier.fillMaxWidth(),
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 13.sp,
                                textAlign = alignment,
                            )
                        }

                        if (foreground != null) {
                            Image(
                                bitmap = foreground,
                                contentDescription = "Separate foreground subject mask",
                                modifier = Modifier.fillMaxSize()
                                    .graphicsLayer {
                                        val wallpaperScale = if (maskEditing) 1f else {
                                            Parallax.ZOOM * settings.wallpaperScale
                                        }
                                        val subjectScale = if (maskEditing) 1f else settings.subjectScale
                                        scaleX = wallpaperScale * subjectScale
                                        scaleY = wallpaperScale * subjectScale
                                        translationX = if (maskEditing) 0f else {
                                            settings.wallpaperX * size.width +
                                                wallpaperScale * settings.subjectX * size.width
                                        }
                                        translationY = if (maskEditing) 0f else {
                                            settings.wallpaperY * size.height +
                                                wallpaperScale * settings.subjectY * size.height
                                        }
                                        alpha = settings.subjectOpacity
                                    }
                                    .pointerInput(foreground, maskEditing, eraseMask, brushRadius) {
                                        if (maskEditing) {
                                            val photoScale = max(
                                                size.width.toFloat() / foreground.width,
                                                size.height.toFloat() / foreground.height,
                                            )
                                            val imageOffsetX =
                                                (size.width - foreground.width * photoScale) / 2f
                                            val imageOffsetY =
                                                (size.height - foreground.height * photoScale) / 2f
                                            val points = mutableListOf<Pair<Float, Float>>()
                                            fun sourcePosition(position: Offset): Pair<Float, Float> =
                                                ((position.x - imageOffsetX) / photoScale / foreground.width)
                                                    .coerceIn(0f, 1f) to
                                                    ((position.y - imageOffsetY) / photoScale / foreground.height)
                                                        .coerceIn(0f, 1f)
                                            detectDragGestures(
                                                onDragStart = { points.clear(); points += sourcePosition(it) },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    points += sourcePosition(change.position)
                                                },
                                                onDragEnd = {
                                                    onCorrectMask(points.toList(), brushRadius, eraseMask)
                                                },
                                                onDragCancel = { points.clear() },
                                            )
                                        } else {
                                            detectDragGestures(
                                                onDragEnd = {
                                                    settings.updateSubjectPosition(settings.subjectX, settings.subjectY)
                                                },
                                                onDragCancel = {
                                                    settings.updateSubjectPosition(settings.subjectX, settings.subjectY)
                                                },
                                            ) { change, amount ->
                                                change.consume()
                                                settings.updateSubjectPosition(
                                                    settings.subjectX + amount.x / size.width,
                                                    settings.subjectY + amount.y / size.height,
                                                    persist = false,
                                                )
                                            }
                                        }
                                    },
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Text(
                                if (background == null) "Import a photo to begin" else "Detect a subject for depth",
                                color = Color.White.copy(alpha = 0.82f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.Center).padding(20.dp),
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onDetectSubject,
                        enabled = background != null && !detectionRunning,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.14f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(if (detectionRunning) "Separating…" else "Detect subject")
                    }
                    Button(
                        onClick = onPickForeground,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.10f),
                            contentColor = Color.White,
                        ),
                    ) { Text(if (foreground == null) "Import cutout" else "Replace cutout") }
                    if (foreground != null) {
                        TextButton(onClick = onClearForeground) { Text("Remove", color = Color.White) }
                    }
                }

                detectionMessage?.let {
                    Text(
                        it,
                        color = Color(0xFF9DEBFA),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (foreground != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = { maskEditing = !maskEditing }) {
                            Text(if (maskEditing) "Finish mask edit" else "Edit mask", color = Color(0xFF63D9F0))
                        }
                        if (maskEditing) {
                            TextButton(onClick = { eraseMask = true }) {
                                Text(if (eraseMask) "● Erase" else "Erase", color = Color.White)
                            }
                            TextButton(
                                onClick = { eraseMask = false },
                                enabled = foregroundRestorable,
                            ) {
                                Text(if (!eraseMask) "● Restore" else "Restore", color = Color.White)
                            }
                        }
                    }
                    if (maskEditing) {
                        Text(
                            if (foregroundRestorable) {
                                "Brush over the preview to remove spill or restore missed parts of the detected subject."
                            } else {
                                "This imported cutout can be erased; restoring pixels is available for detected subjects."
                            },
                            color = Color.White.copy(alpha = 0.64f),
                            fontSize = 11.sp,
                        )
                        EditorSlider("Mask brush size", brushRadius, 0.01f..0.12f) { brushRadius = it }
                    } else {
                        Text(
                            "Drag the subject to adjust how it overlaps the clock.",
                            color = Color.White.copy(alpha = 0.64f),
                            fontSize = 11.sp,
                        )
                    }
                } else {
                    Text(
                        "On-device separation uses Google Play services. First use may download the model; clear silhouettes and contrast work best.",
                        color = Color.White.copy(alpha = 0.64f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text("COMPOSITION", color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, letterSpacing = 1.3.sp)
                EditorSlider("Wallpaper zoom", settings.wallpaperScale, LauncherSettings.WALLPAPER_SCALE_RANGE) {
                    settings.updateWallpaperScale(it)
                }
                EditorSlider("Wallpaper horizontal position", settings.wallpaperX, LauncherSettings.SUBJECT_OFFSET_RANGE) {
                    settings.updateWallpaperPosition(it, settings.wallpaperY)
                }
                EditorSlider("Wallpaper vertical position", settings.wallpaperY, LauncherSettings.SUBJECT_OFFSET_RANGE) {
                    settings.updateWallpaperPosition(settings.wallpaperX, it)
                }
                EditorSlider("Clock size", settings.clockSizeSp, LauncherSettings.CLOCK_SIZE_RANGE) {
                    settings.updateClockSize(it)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = {
                            val fonts = ClockFont.entries
                            settings.updateClockFont(fonts[(fonts.indexOf(settings.clockFont) + 1) % fonts.size])
                        },
                    ) { Text("Font · ${settings.clockFont.label}", color = Color.White) }
                    TextButton(
                        onClick = {
                            settings.updateClockColor(
                                if (settings.clockColor == 0xFF35C4E4.toInt()) {
                                    0xFFFFFFFF.toInt()
                                } else {
                                    0xFF35C4E4.toInt()
                                },
                            )
                        },
                    ) { Text("Clock color · toggle", color = Color(0xFF63D9F0)) }
                }
                if (foreground != null) {
                    EditorSlider("Subject overlap / vertical position", settings.subjectY, LauncherSettings.SUBJECT_OFFSET_RANGE) {
                        settings.updateSubjectPosition(settings.subjectX, it)
                    }
                    EditorSlider("Subject zoom", settings.subjectScale, LauncherSettings.SUBJECT_SCALE_RANGE) {
                        settings.updateSubjectScale(it)
                    }
                }
                Text(
                    "Preview & apply opens Android's wallpaper confirmation. This app can apply its wallpaper service, but it does not replace HONOR's secure lock-screen UI or clock.",
                    color = Color.White.copy(alpha = 0.60f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Done", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun EditorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Text(label, color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp)
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color(0xFF63D9F0),
            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
        ),
    )
}

private fun ClockFont.toFontFamily(): FontFamily = when (this) {
    ClockFont.SANS -> FontFamily.SansSerif
    ClockFont.SERIF -> FontFamily.Serif
    ClockFont.CURSIVE -> FontFamily.Cursive
    ClockFont.MONOSPACE -> FontFamily.Monospace
}
