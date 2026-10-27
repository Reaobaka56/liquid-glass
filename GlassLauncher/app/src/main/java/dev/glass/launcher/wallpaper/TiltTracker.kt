package dev.glass.launcher.wallpaper

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** Shared by the launcher and the wallpaper so the two layers move identically. */
object Parallax {
    /** The wallpaper is drawn this much larger than the screen, leaving room to shift. */
    const val ZOOM = 1.12f

    /** Maximum shift as a fraction of screen width / height at full tilt (must stay under (ZOOM - 1) / 2). */
    const val SHIFT_X = 0.045f
    const val SHIFT_Y = 0.03f
}

/**
 * Turns the accelerometer into a gentle tilt value, each axis in -1..1.
 *
 * The tilt is measured against a slowly moving resting posture, so holding the phone at
 * whatever angle is comfortable gives (0, 0), and only changes in angle move the picture.
 * [onTilt] is called on the main thread.
 */
class TiltTracker(
    context: Context,
    private val onTilt: (x: Float, y: Float) -> Unit,
) : SensorEventListener {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var primed = false
    private var gx = 0f
    private var gy = 0f
    private var baseX = 0f
    private var baseY = 0f

    fun start() {
        val sensor = accelerometer ?: return
        primed = false
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val ax = (event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        val ay = (event.values[1] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        if (!primed) {
            gx = ax; gy = ay; baseX = ax; baseY = ay
            primed = true
        }

        // Low-pass to remove hand jitter, then a much slower baseline for the resting angle.
        gx += (ax - gx) * SMOOTHING
        gy += (ay - gy) * SMOOTHING
        baseX += (gx - baseX) * BASELINE_RATE
        baseY += (gy - baseY) * BASELINE_RATE

        onTilt(
            ((gx - baseX) * GAIN).coerceIn(-1f, 1f),
            ((gy - baseY) * GAIN).coerceIn(-1f, 1f),
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val SMOOTHING = 0.12f
        const val BASELINE_RATE = 0.01f
        const val GAIN = 2.5f
    }
}
