package dev.glass.launcher.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.glass.launcher.ui.glass.GlassMaterial
import dev.glass.launcher.ui.glass.GlassPreset

/**
 * User-adjustable launcher settings, persisted in SharedPreferences.
 *
 * Values are Compose state, so reading them in a composable updates the UI when they change,
 * and every setter writes straight through to disk.
 */
class LauncherSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("launcher_settings", Context.MODE_PRIVATE)

    var preset by mutableStateOf(GlassPreset.fromName(prefs.getString(KEY_PRESET, null)))
        private set

    /** 1 is the preset as designed; 0 is plain tinted blur; above 1 exaggerates the optics. */
    var glassIntensity by mutableFloatStateOf(
        prefs.getFloat(KEY_INTENSITY, 1f).coerceIn(INTENSITY_RANGE)
    )
        private set

    var iconSizeDp by mutableFloatStateOf(
        prefs.getFloat(KEY_ICON_SIZE, DEFAULT_ICON_SIZE).coerceIn(ICON_SIZE_RANGE)
    )
        private set

    fun updatePreset(value: GlassPreset) {
        preset = value
        prefs.edit().putString(KEY_PRESET, value.name).apply()
    }

    fun updateGlassIntensity(value: Float) {
        glassIntensity = value.coerceIn(INTENSITY_RANGE)
        prefs.edit().putFloat(KEY_INTENSITY, glassIntensity).apply()
    }

    fun updateIconSizeDp(value: Float) {
        iconSizeDp = value.coerceIn(ICON_SIZE_RANGE)
        prefs.edit().putFloat(KEY_ICON_SIZE, iconSizeDp).apply()
    }

    /** The material every glass surface should use right now. */
    fun material(): GlassMaterial = preset.material.scaled(glassIntensity)

    companion object {
        val INTENSITY_RANGE = 0f..1.5f
        val ICON_SIZE_RANGE = 48f..72f
        const val DEFAULT_ICON_SIZE = 56f

        private const val KEY_PRESET = "glass_preset"
        private const val KEY_INTENSITY = "glass_intensity"
        private const val KEY_ICON_SIZE = "icon_size_dp"
    }
}
