package dev.glass.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.text.format.DateFormat
import dev.glass.launcher.settings.LauncherSettings
import dev.glass.launcher.settings.ClockAlignment
import dev.glass.launcher.settings.ClockFont
import dev.glass.launcher.settings.AppIconShape
import dev.glass.launcher.ui.glass.GlassPreset
import dev.glass.launcher.ui.glass.LocalReduceMotion
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Long-press menu on the home screen. */
@Composable
fun HomeMenu(
    onChangeWallpaper: () -> Unit,
    onGlassSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Scrim(onDismiss) {
        GlassPanel(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 56.dp),
            cornerRadius = 24.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                MenuRow("Change wallpaper", onChangeWallpaper)
                MenuRow("Glass settings", onGlassSettings)
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Color.White,
        fontSize = 17.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
    )
}

/**
 * Bottom sheet for tuning the glass. Each preset chip is drawn with its own material at the
 * current intensity, so the chips are a live preview; the sheet itself uses the active material.
 */
@Composable
fun LauncherSettingsSheet(
    settings: LauncherSettings,
    onChangeWallpaper: () -> Unit,
    onEditWallpaper: () -> Unit,
    onOpenCompanionPreview: () -> Unit,
    onEditClock: () -> Unit,
    onEditIcons: () -> Unit,
    onApplyTheme: () -> Unit,
    onRequestDefaultHome: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Slides up with a soft spring and settles with a slight bounce.
    val reduceMotion = LocalReduceMotion.current
    val enter = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) enter.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 320f))
    }

    Scrim(onDismiss, contentAlignment = Alignment.BottomCenter) {
        GlassPanel(
            Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    translationY = (1f - enter.value) * size.height * 0.35f
                    alpha = enter.value.coerceIn(0f, 1f)
                }
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp)
                // Keep touches on the sheet from reaching the scrim and closing it.
                .pointerInput(Unit) { detectTapGestures { } },
            cornerRadius = 32.dp,
            interactive = false, // Big surface with sliders: no squish or finger bulge.
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("Settings", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                Text("Glass appearance", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GlassPreset.entries.forEach { preset ->
                        PresetChip(
                            preset = preset,
                            selected = preset == settings.preset,
                            intensity = settings.glassIntensity,
                            onClick = { settings.updatePreset(preset) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                SettingSlider(
                    label = "Glass intensity",
                    value = settings.glassIntensity,
                    range = LauncherSettings.INTENSITY_RANGE,
                    onChange = settings::updateGlassIntensity,
                )
                SettingSlider(
                    label = "Icon size",
                    value = settings.iconSizeDp,
                    range = LauncherSettings.ICON_SIZE_RANGE,
                    onChange = settings::updateIconSizeDp,
                )
                SettingSlider(
                    label = "Icon corner radius",
                    value = settings.iconCornerRadius,
                    range = LauncherSettings.ICON_CORNER_RANGE,
                    onChange = settings::updateIconCornerRadius,
                )
                ChoiceRow(
                    "Icon shape",
                    AppIconShape.entries.map { it.label },
                    AppIconShape.entries.indexOf(settings.iconShape),
                ) { settings.updateIconShape(AppIconShape.entries[it]) }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("App icon labels", color = Color.White, fontSize = 16.sp)
                        Text(
                            "Show app names below icons on the home screen and in the app drawer.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = settings.showIconLabels,
                        onCheckedChange = settings::updateShowIconLabels,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF34C759),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.3f),
                            uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("Launcher", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("iOS status bar", color = Color.White, fontSize = 16.sp)
                        Text(
                            "Hides the system bar on the home screen. Swipe down from the top edge to reach it.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = settings.iosStatusBar,
                        onCheckedChange = settings::updateIosStatusBar,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF34C759),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.3f),
                            uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }
                TextButton(onClick = onChangeWallpaper, modifier = Modifier.fillMaxWidth()) {
                    Text("Change wallpaper", color = Color.White)
                }
                TextButton(onClick = onEditWallpaper, modifier = Modifier.fillMaxWidth()) {
                    Text("Wallpaper depth editor", color = Color.White)
                }
                TextButton(onClick = onEditClock, modifier = Modifier.fillMaxWidth()) {
                    Text("Customize clock", color = Color.White)
                }
                TextButton(onClick = onEditIcons, modifier = Modifier.fillMaxWidth()) {
                    Text("App icon studio · installed apps, presets, custom icons", color = Color.White)
                }
                TextButton(onClick = onOpenCompanionPreview, modifier = Modifier.fillMaxWidth()) {
                    Text("Lock screen wallpaper preview", color = Color.White)
                }
                TextButton(onClick = onApplyTheme, modifier = Modifier.fillMaxWidth()) {
                    Text("Apply supported changes", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onRequestDefaultHome, modifier = Modifier.fillMaxWidth()) {
                    Text("Set default launcher", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ClockEditorSheet(settings: LauncherSettings, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = LocalReduceMotion.current
    val enter = remember { Animatable(if (reduceMotion) 1f else 0f) }
    var previewTime by remember { androidx.compose.runtime.mutableStateOf(LocalDateTime.of(2024, 6, 15, 9, 41)) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) enter.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 320f))
    }

    Scrim(onDismiss, contentAlignment = Alignment.BottomCenter) {
        GlassPanel(
            Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    translationY = (1f - enter.value) * size.height * 0.25f
                    alpha = enter.value.coerceIn(0f, 1f)
                }
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp)
                .pointerInput(Unit) { detectTapGestures { } },
            cornerRadius = 32.dp,
            interactive = false,
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("Clock style", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Changes are saved on this device. Preview time is separate from the real clock.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(18.dp))
                GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = previewTime.format(
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
                            fontSize = settings.clockSizeSp.sp,
                            fontWeight = FontWeight(settings.clockWeight),
                            fontFamily = settings.clockFont.toFontFamily(),
                            letterSpacing = settings.clockLetterSpacingSp.sp,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            previewTime.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { previewTime = previewTime.minusMinutes(30) }) {
                        Text("− 30 min", color = Color.White)
                    }
                    TextButton(onClick = { previewTime = LocalDateTime.now() }) {
                        Text("Use now", color = Color.White)
                    }
                    TextButton(onClick = { previewTime = previewTime.plusMinutes(30) }) {
                        Text("+ 30 min", color = Color.White)
                    }
                }

                Spacer(Modifier.height(12.dp))
                SettingSlider("Clock size", settings.clockSizeSp, LauncherSettings.CLOCK_SIZE_RANGE, settings::updateClockSize)
                SettingSlider(
                    "Letter spacing",
                    settings.clockLetterSpacingSp,
                    LauncherSettings.CLOCK_SPACING_RANGE,
                    settings::updateClockLetterSpacing,
                )
                SettingSlider("Font weight", settings.clockWeight.toFloat(), 200f..800f) {
                    settings.updateClockWeight((it / 100f).toInt() * 100)
                }
                ChoiceRow("Font", ClockFont.entries.map { it.label }, ClockFont.entries.indexOf(settings.clockFont)) {
                    settings.updateClockFont(ClockFont.entries[it])
                }
                ChoiceRow(
                    "Alignment",
                    ClockAlignment.entries.map { it.label },
                    ClockAlignment.entries.indexOf(settings.clockAlignment),
                ) { settings.updateClockAlignment(ClockAlignment.entries[it]) }
                ChoiceRow("Time format", listOf("System", "12-hour", "24-hour"), settings.clockFormat) {
                    settings.updateClockFormat(it)
                }
                Text("Color", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    listOf(
                        0xFFFFFFFF.toInt(),
                        0xFFFFE6B8.toInt(),
                        0xFFBDEBFF.toInt(),
                        0xFFFFB8D1.toInt(),
                    ).forEach { color ->
                        Box(
                            Modifier
                                .size(34.dp)
                                .then(
                                    if (settings.clockColor == color) Modifier.border(2.dp, Color.White, RoundedCornerShape(50))
                                    else Modifier
                                )
                                .clickable { settings.updateClockColor(color) }
                                .padding(5.dp)
                                .background(Color(color), RoundedCornerShape(50)),
                        )
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, choices: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        choices.forEachIndexed { index, choice ->
            Text(
                choice,
                color = Color.White,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (index == selected) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f))
                    .clickable { onSelect(index) }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
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
private fun PresetChip(
    preset: GlassPreset,
    selected: Boolean,
    intensity: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    GlassPanel(
        modifier
            .height(64.dp)
            .then(if (selected) Modifier.border(1.5.dp, Color.White, shape) else Modifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${preset.label} glass${if (selected) ", selected" else ""}" },
        cornerRadius = 18.dp,
        material = preset.material.scaled(intensity),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(preset.label, color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.White,
            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
        ),
    )
}

/** Full-screen touch catcher: tapping outside the content dismisses it. */
@Composable
private fun Scrim(
    onDismiss: () -> Unit,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = contentAlignment,
        content = content,
    )
}
