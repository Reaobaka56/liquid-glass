package dev.glass.launcher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glass.launcher.settings.LockStyle
import dev.glass.launcher.wallpaper.DepthStore
import dev.glass.launcher.wallpaper.LockComposer
import dev.glass.launcher.wallpaper.SubjectCutout
import dev.glass.launcher.wallpaper.WallpaperStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SWATCHES = listOf(
    0xFFFFFFFF.toInt(), 0xFFFFF1D6.toInt(), 0xFFFFC2D9.toInt(),
    0xFFBFE3FF.toInt(), 0xFFC8F7DC.toInt(), 0xFF111111.toInt(),
)

/**
 * Lock screen studio: edit the clock and the depth effect with a live preview.
 *
 * The preview is drawn by [LockComposer], the same code the live wallpaper uses, so what you see
 * here is what the lock screen draws.
 */
@Composable
fun LockStudio(
    style: LockStyle,
    onStyleChange: (LockStyle) -> Unit,
    onPickWallpaper: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { WallpaperStore(context) }
    val depthStore = remember { DepthStore(context) }
    val metrics = context.resources.displayMetrics

    val photo = remember { store.current() }
    var background by remember { mutableStateOf<Bitmap?>(null) }
    var subject by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(photo) {
        withContext(Dispatchers.IO) {
            if (photo != null) {
                background = store.decode(photo, metrics.widthPixels / 2, metrics.heightPixels / 2)
                subject = depthStore.load(photo)
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(20_000L)
        }
    }

    fun cutOut() {
        val file = photo ?: return
        scope.launch {
            working = true
            status = "Finding the subject… the first run downloads a small model, so stay online."
            val full = withContext(Dispatchers.IO) {
                store.decode(file, metrics.widthPixels, metrics.heightPixels)
            }
            val cut = if (full != null) SubjectCutout.generate(full) else null
            if (cut != null) {
                withContext(Dispatchers.IO) { depthStore.save(file, cut) }
                subject = cut
                onStyleChange(style.copy(depthEnabled = true))
                status = "Done. Clock now sits behind the subject."
            } else {
                status = "Couldn't find a clear subject. Try a photo with a person or object in front."
            }
            working = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Lock screen studio", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                GlassButton("Done", onClose)
            }

            // Live preview, shaped like the phone.
            Box(
                Modifier
                    .weight(0.9f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat())
                        .clip(RoundedCornerShape(28.dp)),
                ) {
                    val time = LockComposer.timeParts(context, nowMs)
                    drawIntoCanvas { c ->
                        LockComposer.drawPreview(
                            c.nativeCanvas, size.width, size.height, background, subject, style, time,
                        )
                    }
                }
            }

            GlassPanel(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                cornerRadius = 28.dp,
                interactive = false,
            ) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                ) {
                    Text("Wallpaper depth", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Puts the clock between the background and the subject of your photo.",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton("Change photo", onPickWallpaper, Modifier.weight(1f))
                        GlassButton(
                            if (working) "Working…" else if (subject == null) "Cut out subject" else "Redo cut-out",
                            onClick = { if (!working) cutOut() },
                            modifier = Modifier.weight(1f),
                            enabled = photo != null && !working,
                        )
                    }
                    if (photo == null) {
                        Text(
                            "Pick a photo first. The depth effect needs one.",
                            color = Color(0xFFFFB86B),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    status?.let {
                        Text(it, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    StudioSwitch(
                        "Depth effect",
                        "Needs a cut-out. Moves in layers when you tilt the phone.",
                        style.depthEnabled && subject != null,
                        enabled = subject != null,
                    ) { onStyleChange(style.copy(depthEnabled = it)) }
                    StudioSlider(
                        "Mask edge", style.maskThreshold, LockStyle.MASK_RANGE,
                    ) { onStyleChange(style.copy(maskThreshold = it)) }

                    Spacer(Modifier.height(10.dp))
                    Text("Clock", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    StudioSwitch(
                        "Draw on lock screen",
                        "Hide the system lock clock in your phone's settings, or you'll see two.",
                        style.lockClock,
                    ) { onStyleChange(style.copy(lockClock = it)) }
                    StudioSwitch("Stacked", "Hours over minutes.", style.stacked) {
                        onStyleChange(style.copy(stacked = it))
                    }
                    StudioSwitch("Show date", null, style.showDate) {
                        onStyleChange(style.copy(showDate = it))
                    }
                    StudioSlider("Weight", style.clockWeight.toFloat(), LockStyle.WEIGHT_RANGE) {
                        onStyleChange(style.copy(clockWeight = (Math.round(it / 100f) * 100).coerceIn(100, 900)))
                    }
                    StudioSlider("Size", style.clockScale, LockStyle.SCALE_RANGE) {
                        onStyleChange(style.copy(clockScale = it))
                    }
                    StudioSlider("Position", style.clockY, LockStyle.Y_RANGE) {
                        onStyleChange(style.copy(clockY = it))
                    }
                    Text("Colour", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                    Row(
                        Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SWATCHES.forEach { argb ->
                            val selected = style.clockColor == argb
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) Color.White else Color.White.copy(alpha = 0.4f),
                                        CircleShape,
                                    )
                                    .clickable { onStyleChange(style.copy(clockColor = argb)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioSwitch(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White.copy(alpha = if (enabled) 1f else 0.5f), fontSize = 15.sp)
            if (subtitle != null) {
                Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF34C759),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.White.copy(alpha = 0.3f),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun StudioSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) = SettingSlider(label, value, range, onChange)
