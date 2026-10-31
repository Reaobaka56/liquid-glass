package dev.glass.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.glass.launcher.settings.LauncherSettings
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WallpaperEditorSheet(
    background: ImageBitmap?,
    foreground: ImageBitmap?,
    settings: LauncherSettings,
    onPickForeground: () -> Unit,
    onClearForeground: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
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
            Modifier
                .fillMaxSize()
                .padding(12.dp),
            cornerRadius = 30.dp,
            interactive = false,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("Wallpaper depth editor", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Background → editable clock → transparent subject cutout. Drag the subject in the preview.",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(430.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF182039)),
                ) {
                    if (background != null) {
                        Image(
                            bitmap = background,
                            contentDescription = "Wallpaper background layer",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(top = 76.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            now.format(
                                DateTimeFormatter.ofPattern(
                                    when (settings.clockFormat) {
                                        1 -> "h:mm a"
                                        2 -> "HH:mm"
                                        else -> if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
                                    },
                                    Locale.getDefault(),
                                ),
                            ),
                            color = Color(settings.clockColor),
                            fontSize = (settings.clockSizeSp * 0.8f).sp,
                            fontWeight = FontWeight(settings.clockWeight),
                        )
                        Text(
                            now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                        )
                    }
                    if (foreground != null) {
                        Image(
                            bitmap = foreground,
                            contentDescription = "Transparent foreground subject layer",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = settings.subjectScale
                                    scaleY = settings.subjectScale
                                    translationX = settings.subjectX * size.width
                                    translationY = settings.subjectY * size.height
                                    alpha = settings.subjectOpacity
                                }
                                .pointerInput(Unit) {
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
                                },
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            "Choose a transparent PNG subject cutout",
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onPickForeground,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(if (foreground == null) "Choose subject cutout" else "Replace cutout")
                    }
                    if (foreground != null) {
                        TextButton(onClick = onClearForeground) { Text("Remove", color = Color.White) }
                    }
                }
                Text(
                    "The selected image is saved as a separate layer. Use a PNG with transparent background; automatic subject detection and brush-based mask repair are not built in yet.",
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(8.dp))
                EditorSlider("Zoom / crop", settings.subjectScale, LauncherSettings.SUBJECT_SCALE_RANGE) {
                    settings.updateSubjectScale(it)
                }
                EditorSlider("Horizontal position", settings.subjectX, LauncherSettings.SUBJECT_OFFSET_RANGE) {
                    settings.updateSubjectPosition(it, settings.subjectY)
                }
                EditorSlider("Vertical position", settings.subjectY, LauncherSettings.SUBJECT_OFFSET_RANGE) {
                    settings.updateSubjectPosition(settings.subjectX, it)
                }
                EditorSlider("Depth layer intensity", settings.subjectOpacity, 0f..1f) {
                    settings.updateSubjectOpacity(it)
                }
                Text(
                    "On the actual lock screen, Android still draws its secure system clock above the wallpaper. This editor previews the editable clock and subject layer; MagicOS may show its own clock too.",
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 11.sp,
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
            activeTrackColor = Color.White,
            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
        ),
    )
}
