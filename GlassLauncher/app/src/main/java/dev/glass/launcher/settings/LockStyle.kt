package dev.glass.launcher.settings

import android.content.Context

/**
 * How the clock (and the depth effect) look. Shared by the home screen, the lock-screen studio
 * and the live wallpaper, so it is a plain immutable value stored in SharedPreferences rather
 * than Compose state (the wallpaper service runs outside any composition).
 *
 * @property lockClock draw this clock in the lock-screen wallpaper (hide the system clock in MagicOS
 *   settings, otherwise there are two).
 * @property depthEnabled draw the clock and the cut-out subject as separate layers in the
 *   wallpaper, with the clock between the background and the subject.
 * @property clockWeight 100..900, rounded to the nearest 100.
 * @property clockScale 1 is the default size.
 * @property clockY top of the clock as a fraction of screen height (lock screen only).
 * @property maskThreshold 0 keeps the detected mask as is; higher cuts soft edge pixels away.
 */
data class LockStyle(
    val lockClock: Boolean = false,
    val depthEnabled: Boolean = false,
    val clockWeight: Int = 300,
    val clockScale: Float = 1f,
    val clockY: Float = 0.12f,
    val clockColor: Int = 0xFFFFFFFF.toInt(),
    val stacked: Boolean = true,
    val showDate: Boolean = true,
    val maskThreshold: Float = 0f,
) {
    companion object {
        val WEIGHT_RANGE = 100f..900f
        val SCALE_RANGE = 0.6f..1.6f
        val Y_RANGE = 0.04f..0.42f
        val MASK_RANGE = 0f..0.8f
    }
}

class LockStyleStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("lock_style", Context.MODE_PRIVATE)

    fun load(): LockStyle {
        val d = LockStyle()
        return LockStyle(
            lockClock = prefs.getBoolean("lockClock", d.lockClock),
            depthEnabled = prefs.getBoolean("depth", d.depthEnabled),
            clockWeight = prefs.getInt("weight", d.clockWeight).coerceIn(100, 900),
            clockScale = prefs.getFloat("scale", d.clockScale).coerceIn(LockStyle.SCALE_RANGE),
            clockY = prefs.getFloat("y", d.clockY).coerceIn(LockStyle.Y_RANGE),
            clockColor = prefs.getInt("color", d.clockColor),
            stacked = prefs.getBoolean("stacked", d.stacked),
            showDate = prefs.getBoolean("date", d.showDate),
            maskThreshold = prefs.getFloat("mask", d.maskThreshold).coerceIn(LockStyle.MASK_RANGE),
        )
    }

    fun save(style: LockStyle) {
        prefs.edit()
            .putBoolean("lockClock", style.lockClock)
            .putBoolean("depth", style.depthEnabled)
            .putInt("weight", style.clockWeight)
            .putFloat("scale", style.clockScale)
            .putFloat("y", style.clockY)
            .putInt("color", style.clockColor)
            .putBoolean("stacked", style.stacked)
            .putBoolean("date", style.showDate)
            .putFloat("mask", style.maskThreshold)
            .apply()
    }
}
