package dev.glass.launcher.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.glass.launcher.ui.glass.GlassMaterial
import dev.glass.launcher.ui.glass.GlassPreset
import dev.glass.launcher.wallpaper.GlassWallpaperService

enum class ClockFont(val label: String) {
    SANS("Modern"),
    SERIF("Serif"),
    CURSIVE("Cursive"),
    MONOSPACE("Mono");

    companion object {
        fun fromName(name: String?): ClockFont = entries.firstOrNull { it.name == name } ?: SANS
    }
}

enum class ClockAlignment(val label: String) {
    START("Left"),
    CENTER("Center"),
    END("Right");

    companion object {
        fun fromName(name: String?): ClockAlignment =
            entries.firstOrNull { it.name == name } ?: START
    }
}

enum class AppIconShape(val label: String) {
    SQUIRCLE("Squircle"),
    ROUNDED("Rounded"),
    CIRCLE("Circle"),
    SQUARE("Square");

    companion object {
        fun fromName(name: String?): AppIconShape = entries.firstOrNull { it.name == name } ?: SQUIRCLE
    }
}

/**
 * User-adjustable launcher settings, persisted in SharedPreferences.
 *
 * Values are Compose state, so reading them in a composable updates the UI when they change,
 * and every setter writes straight through to disk.
 */
class LauncherSettings(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE)

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

    var iconCornerRadius by mutableFloatStateOf(
        prefs.getFloat(KEY_ICON_CORNER, DEFAULT_ICON_CORNER).coerceIn(ICON_CORNER_RANGE)
    )
        private set

    var iconShape by mutableStateOf(AppIconShape.fromName(prefs.getString(KEY_ICON_SHAPE, null)))
        private set

    var showIconLabels by mutableStateOf(prefs.getBoolean(KEY_ICON_LABELS, true))
        private set

    /** Draw an iOS-style status bar on the home screen and hide the system one there. */
    var iosStatusBar by mutableStateOf(prefs.getBoolean(KEY_IOS_STATUS_BAR, true))
        private set

    var clockSizeSp by mutableFloatStateOf(
        prefs.getFloat(KEY_CLOCK_SIZE, DEFAULT_CLOCK_SIZE).coerceIn(CLOCK_SIZE_RANGE)
    )
        private set

    var clockWeight by mutableIntStateOf(
        prefs.getInt(KEY_CLOCK_WEIGHT, 300).coerceIn(CLOCK_WEIGHT_RANGE)
    )
        private set

    var clockLetterSpacingSp by mutableFloatStateOf(
        prefs.getFloat(KEY_CLOCK_SPACING, 0f).coerceIn(CLOCK_SPACING_RANGE)
    )
        private set

    var clockFont by mutableStateOf(ClockFont.fromName(prefs.getString(KEY_CLOCK_FONT, null)))
        private set

    var clockAlignment by mutableStateOf(
        ClockAlignment.fromName(prefs.getString(KEY_CLOCK_ALIGNMENT, null))
    )
        private set

    var clockColor by mutableIntStateOf(prefs.getInt(KEY_CLOCK_COLOR, 0xFFFFFFFF.toInt()))
        private set

    /** 0 follows Android's locale preference; 12 and 24 force the chosen display format. */
    var clockFormat by mutableIntStateOf(prefs.getInt(KEY_CLOCK_FORMAT, 0).coerceIn(0..2))
        private set

    var lockClockX by mutableFloatStateOf(prefs.getFloat(KEY_LOCK_CLOCK_X, 0.5f).coerceIn(0f..1f))
        private set

    var lockClockY by mutableFloatStateOf(prefs.getFloat(KEY_LOCK_CLOCK_Y, 0.28f).coerceIn(0f..1f))
        private set

    var subjectScale by mutableFloatStateOf(
        prefs.getFloat(KEY_SUBJECT_SCALE, 1f).coerceIn(SUBJECT_SCALE_RANGE)
    )
        private set

    var subjectX by mutableFloatStateOf(
        prefs.getFloat(KEY_SUBJECT_X, 0f).coerceIn(SUBJECT_OFFSET_RANGE)
    )
        private set

    var subjectY by mutableFloatStateOf(
        prefs.getFloat(KEY_SUBJECT_Y, 0f).coerceIn(SUBJECT_OFFSET_RANGE)
    )
        private set

    var subjectOpacity by mutableFloatStateOf(
        prefs.getFloat(KEY_SUBJECT_OPACITY, 1f).coerceIn(0f..1f)
    )
        private set

    fun updateIosStatusBar(value: Boolean) {
        iosStatusBar = value
        prefs.edit().putBoolean(KEY_IOS_STATUS_BAR, value).apply()
    }

    fun updateClockSize(value: Float) {
        clockSizeSp = value.coerceIn(CLOCK_SIZE_RANGE)
        prefs.edit().putFloat(KEY_CLOCK_SIZE, clockSizeSp).apply()
    }

    fun updateClockWeight(value: Int) {
        clockWeight = value.coerceIn(CLOCK_WEIGHT_RANGE)
        prefs.edit().putInt(KEY_CLOCK_WEIGHT, clockWeight).apply()
    }

    fun updateClockLetterSpacing(value: Float) {
        clockLetterSpacingSp = value.coerceIn(CLOCK_SPACING_RANGE)
        prefs.edit().putFloat(KEY_CLOCK_SPACING, clockLetterSpacingSp).apply()
    }

    fun updateClockFont(value: ClockFont) {
        clockFont = value
        prefs.edit().putString(KEY_CLOCK_FONT, value.name).apply()
    }

    fun updateClockAlignment(value: ClockAlignment) {
        clockAlignment = value
        prefs.edit().putString(KEY_CLOCK_ALIGNMENT, value.name).apply()
    }

    fun updateClockColor(value: Int) {
        clockColor = value
        prefs.edit().putInt(KEY_CLOCK_COLOR, value).apply()
    }

    fun updateClockFormat(value: Int) {
        clockFormat = value.coerceIn(0..2)
        prefs.edit().putInt(KEY_CLOCK_FORMAT, clockFormat).apply()
    }

    fun updateLockClockPosition(x: Float, y: Float, persist: Boolean = true) {
        lockClockX = x.coerceIn(0f..1f)
        lockClockY = y.coerceIn(0f..1f)
        if (persist) {
            prefs.edit().putFloat(KEY_LOCK_CLOCK_X, lockClockX)
                .putFloat(KEY_LOCK_CLOCK_Y, lockClockY)
                .apply()
        }
    }

    fun updateSubjectScale(value: Float) {
        subjectScale = value.coerceIn(SUBJECT_SCALE_RANGE)
        prefs.edit().putFloat(KEY_SUBJECT_SCALE, subjectScale).apply()
        GlassWallpaperService.requestRefresh(appContext)
    }

    fun updateSubjectPosition(x: Float, y: Float, persist: Boolean = true) {
        subjectX = x.coerceIn(SUBJECT_OFFSET_RANGE)
        subjectY = y.coerceIn(SUBJECT_OFFSET_RANGE)
        if (persist) {
            prefs.edit().putFloat(KEY_SUBJECT_X, subjectX).putFloat(KEY_SUBJECT_Y, subjectY).apply()
            GlassWallpaperService.requestRefresh(appContext)
        }
    }

    fun updateSubjectOpacity(value: Float) {
        subjectOpacity = value.coerceIn(0f..1f)
        prefs.edit().putFloat(KEY_SUBJECT_OPACITY, subjectOpacity).apply()
        GlassWallpaperService.requestRefresh(appContext)
    }

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

    fun updateIconCornerRadius(value: Float) {
        iconCornerRadius = value.coerceIn(ICON_CORNER_RANGE)
        prefs.edit().putFloat(KEY_ICON_CORNER, iconCornerRadius).apply()
    }

    fun updateIconShape(value: AppIconShape) {
        iconShape = value
        prefs.edit().putString(KEY_ICON_SHAPE, value.name).apply()
    }

    fun updateShowIconLabels(value: Boolean) {
        showIconLabels = value
        prefs.edit().putBoolean(KEY_ICON_LABELS, value).apply()
    }

    /** The material every glass surface should use right now. */
    fun material(): GlassMaterial = preset.material.scaled(glassIntensity)

    companion object {
        val INTENSITY_RANGE = 0f..1.5f
        val ICON_SIZE_RANGE = 48f..72f
        const val DEFAULT_ICON_SIZE = 56f
        val ICON_CORNER_RANGE = 0f..50f
        const val DEFAULT_ICON_CORNER = 22f
        val CLOCK_SIZE_RANGE = 38f..112f
        val CLOCK_WEIGHT_RANGE = 200..800
        val CLOCK_SPACING_RANGE = (-2f)..4f
        const val DEFAULT_CLOCK_SIZE = 56f
        val SUBJECT_SCALE_RANGE = 0.6f..2f
        val SUBJECT_OFFSET_RANGE = (-0.5f)..0.5f

        private const val KEY_PRESET = "glass_preset"
        private const val KEY_INTENSITY = "glass_intensity"
        private const val KEY_ICON_SIZE = "icon_size_dp"
        private const val KEY_ICON_CORNER = "icon_corner_radius"
        private const val KEY_ICON_SHAPE = "icon_shape"
        private const val KEY_ICON_LABELS = "icon_labels"
        private const val KEY_IOS_STATUS_BAR = "ios_status_bar"
        private const val KEY_CLOCK_SIZE = "clock_size_sp"
        private const val KEY_CLOCK_WEIGHT = "clock_weight"
        private const val KEY_CLOCK_SPACING = "clock_letter_spacing"
        private const val KEY_CLOCK_FONT = "clock_font"
        private const val KEY_CLOCK_ALIGNMENT = "clock_alignment"
        private const val KEY_CLOCK_COLOR = "clock_color"
        private const val KEY_CLOCK_FORMAT = "clock_format"
        private const val KEY_LOCK_CLOCK_X = "lock_clock_x"
        private const val KEY_LOCK_CLOCK_Y = "lock_clock_y"
        private const val KEY_SUBJECT_SCALE = "subject_scale"
        private const val KEY_SUBJECT_X = "subject_x"
        private const val KEY_SUBJECT_Y = "subject_y"
        private const val KEY_SUBJECT_OPACITY = "subject_opacity"
    }
}
