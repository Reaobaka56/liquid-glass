package dev.glass.launcher.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.text.format.DateFormat
import dev.glass.launcher.settings.LockStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

data class TimeParts(val hours: String, val minutes: String, val date: String)

/**
 * Draws the lock-screen layers with plain [Canvas] calls, so the live wallpaper and the studio's
 * preview produce the same picture from the same code.
 *
 * Layer order (this is what makes the "clock behind the subject" look):
 *   1. background photo   2. clock + date   3. cut-out subject
 *
 * Everything here runs on the main thread (wallpaper engines and Compose both draw there), so
 * the shared Paint objects are safe.
 */
object LockComposer {

    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint()

    private var cachedMinute = -1L
    private var cachedIs24 = false
    private var cachedParts = TimeParts("", "", "")

    /** Formatted once per minute, not once per frame. */
    fun timeParts(context: Context, millis: Long): TimeParts {
        val minute = millis / 60_000L
        val is24 = DateFormat.is24HourFormat(context)
        if (minute == cachedMinute && is24 == cachedIs24) return cachedParts
        val locale = Locale.getDefault()
        val date = Date(millis)
        cachedParts = TimeParts(
            hours = SimpleDateFormat(if (is24) "HH" else "h", locale).format(date),
            minutes = SimpleDateFormat("mm", locale).format(date),
            date = SimpleDateFormat("EEEE, d MMMM", locale).format(date),
        )
        cachedMinute = minute
        cachedIs24 = is24
        return cachedParts
    }

    private val faces = HashMap<Int, Typeface>()

    /** System font families stand in for weights, which works on every supported Android version. */
    fun typefaceFor(weight: Int): Typeface = faces.getOrPut(weight) {
        when {
            weight <= 150 -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
            weight <= 350 -> Typeface.create("sans-serif-condensed-light", Typeface.NORMAL)
            weight <= 450 -> Typeface.create("sans-serif", Typeface.NORMAL)
            weight <= 550 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            weight <= 750 -> Typeface.create("sans-serif", Typeface.BOLD)
            else -> Typeface.create("sans-serif-black", Typeface.NORMAL)
        }
    }

    /** Clock + date. Y is the top of the block as a fraction of the height. */
    fun drawClock(canvas: Canvas, w: Float, h: Float, style: LockStyle, time: TimeParts) {
        val base = w * (if (style.stacked) 0.40f else 0.24f) * style.clockScale
        clockPaint.typeface = typefaceFor(style.clockWeight)
        // Tall and narrow like the iOS lock clock, slightly see-through so the photo shows in it.
        clockPaint.textScaleX = if (style.stacked) 0.82f else 1f
        clockPaint.textSize = if (style.stacked) base * 1.15f else base
        clockPaint.color = if (style.stacked) (style.clockColor and 0x00FFFFFF) or (0xD0 shl 24) else style.clockColor
        clockPaint.setShadowLayer(base * 0.06f, 0f, base * 0.02f, 0x59000000)

        val cx = w / 2f
        val firstBaseline = h * style.clockY + base * 0.80f
        val dateBaseline: Float
        if (style.stacked) {
            val line = base * 0.86f
            canvas.drawText(time.hours, cx, firstBaseline, clockPaint)
            canvas.drawText(time.minutes, cx, firstBaseline + line, clockPaint)
            dateBaseline = firstBaseline + line + base * 0.22f
        } else {
            canvas.drawText(time.hours + ":" + time.minutes, cx, firstBaseline, clockPaint)
            dateBaseline = firstBaseline + base * 0.28f
        }

        if (style.showDate) {
            datePaint.typeface = typefaceFor(max(style.clockWeight, 400))
            datePaint.textSize = base * 0.17f
            datePaint.color = (style.clockColor and 0x00FFFFFF) or (0xD9 shl 24)
            datePaint.setShadowLayer(base * 0.03f, 0f, base * 0.01f, 0x59000000)
            canvas.drawText(time.date, cx, dateBaseline, datePaint)
        }
    }

    private var maskT = -1f
    private var maskFilter: ColorMatrixColorFilter? = null

    /** Raises the alpha cut-off so soft, half-transparent edge pixels of the mask disappear. */
    private fun maskFor(threshold: Float): ColorMatrixColorFilter? {
        if (threshold <= 0.01f) return null
        if (threshold != maskT) {
            val k = 1f / (1f - threshold)
            maskFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, 0f,
                        0f, 1f, 0f, 0f, 0f,
                        0f, 0f, 1f, 0f, 0f,
                        0f, 0f, 0f, k, -threshold * k * 255f,
                    )
                )
            )
            maskT = threshold
        }
        return maskFilter
    }

    /** The cut-out, scaled like the background ("crop to fill") so the two line up exactly. */
    fun drawSubject(canvas: Canvas, w: Float, h: Float, subject: Bitmap, style: LockStyle) {
        imagePaint.colorFilter = maskFor(style.maskThreshold)
        drawCover(canvas, w, h, subject)
        imagePaint.colorFilter = null
    }

    /** Static version of the whole composition, for the studio preview. */
    fun drawPreview(
        canvas: Canvas,
        w: Float,
        h: Float,
        background: Bitmap?,
        subject: Bitmap?,
        style: LockStyle,
        time: TimeParts,
    ) {
        if (background != null) {
            drawCover(canvas, w, h, background)
        } else {
            fillPaint.color = 0xFF14102B.toInt()
            canvas.drawRect(0f, 0f, w, h, fillPaint)
        }
        drawClock(canvas, w, h, style, time)
        if (style.depthEnabled && subject != null) drawSubject(canvas, w, h, subject, style)
    }

    private fun drawCover(canvas: Canvas, w: Float, h: Float, bitmap: Bitmap) {
        val s = max(w / bitmap.width, h / bitmap.height)
        canvas.save()
        canvas.translate((w - bitmap.width * s) / 2f, (h - bitmap.height * s) / 2f)
        canvas.scale(s, s)
        canvas.drawBitmap(bitmap, 0f, 0f, imagePaint)
        canvas.restore()
    }
}
