package dev.glass.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glass.launcher.settings.LauncherSettings
import dev.glass.launcher.ui.glass.GlassPreset
import dev.glass.launcher.ui.glass.LocalReduceMotion

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
    onOpenCompanionPreview: () -> Unit,
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
                TextButton(onClick = onOpenCompanionPreview, modifier = Modifier.fillMaxWidth()) {
                    Text("Lock screen preview", color = Color.White)
                }
                TextButton(onClick = onRequestDefaultHome, modifier = Modifier.fillMaxWidth()) {
                    Text("Set as default home app", color = Color.White)
                }
            }
        }
    }
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
