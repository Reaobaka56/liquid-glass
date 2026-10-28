package dev.glass.launcher.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Live wallpaper for the home and lock screens.
 *
 * - Draws the current photo from [WallpaperStore] slightly oversized and shifts it with the
 *   phone's tilt, so it looks like a window onto a scene behind the glass.
 * - With several photos, steps to the next one each time the screen turns off, so the next
 *   wake shows a new picture (Photo Shuffle).
 * - With no photo picked, draws the same aurora the launcher uses.
 */
class GlassWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = GlassEngine()

    private inner class GlassEngine : Engine(), Choreographer.FrameCallback {

        private val store = WallpaperStore(applicationContext)
        private val choreographer = Choreographer.getInstance()
        private val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val basePaint = Paint()

        private var width = 0
        private var height = 0
        private var bitmap: Bitmap? = null

        // path + mtime: picking new photos rewrites 000.img in place, so the path alone never changes.
        private var loadedKey: String? = null

        // Set when the photo may have changed (wake, screen off, resize). Without it the photo
        // directory was listed and sorted on every frame while tilting.
        private var sourceDirty = true

        private var visible = false
        private var framePending = false

        private var targetX = 0f
        private var targetY = 0f
        private var shownX = 0f
        private var shownY = 0f

        private val tilt = TiltTracker(applicationContext) { x, y ->
            targetX = x
            targetY = y
            requestFrame()
        }

        private val screenOff = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Step to the next photo as the screen locks, so it is ready for the next wake.
                store.advanceIfDue()
                sourceDirty = true
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            applicationContext.registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }

        override fun onDestroy() {
            applicationContext.unregisterReceiver(screenOff)
            tilt.stop()
            choreographer.removeFrameCallback(this)
            bitmap = null
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            if (w != width || h != height) {
                width = w
                height = h
                loadedKey = null // re-decode at the new size
                sourceDirty = true
            }
            requestFrame()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                sourceDirty = true // photos may have been replaced while hidden
                tilt.start()
                requestFrame()
            } else {
                tilt.stop()
                choreographer.removeFrameCallback(this)
                framePending = false
            }
        }

        private fun requestFrame() {
            if (!visible || framePending) return
            framePending = true
            choreographer.postFrameCallback(this)
        }

        override fun doFrame(frameTimeNanos: Long) {
            framePending = false
            if (!visible) return

            shownX += (targetX - shownX) * EASE
            shownY += (targetY - shownY) * EASE
            draw()

            // Keep animating until the picture has caught up with the phone.
            if (abs(targetX - shownX) > SETTLED || abs(targetY - shownY) > SETTLED) {
                requestFrame()
            }
        }

        private fun ensureBitmap() {
            if (!sourceDirty || width == 0 || height == 0) return
            sourceDirty = false

            val file = store.current()
            if (file == null) {
                bitmap = null
                loadedKey = null
                return
            }
            val key = "${file.path}:${file.lastModified()}"
            if (key == loadedKey) return
            bitmap = store.decode(file, width, height)
            loadedKey = key
        }

        private fun draw() {
            if (width == 0 || height == 0) return
            ensureBitmap()

            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockHardwareCanvas()
                if (canvas != null) drawScene(canvas)
            } catch (e: IllegalStateException) {
                // Surface went away mid-frame; the next frame will recover.
            } catch (e: IllegalArgumentException) {
                // Same: the surface was released while drawing.
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: IllegalStateException) {
                    } catch (e: IllegalArgumentException) {
                    }
                }
            }
        }

        private fun drawScene(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val dx = -shownX * w * Parallax.SHIFT_X
            val dy = -shownY * h * Parallax.SHIFT_Y

            canvas.save()
            // Same transform the launcher applies to its own backdrop: zoom about the centre,
            // then shift, so home and lock line up.
            canvas.translate(w / 2f + dx, h / 2f + dy)
            canvas.scale(Parallax.ZOOM, Parallax.ZOOM)
            canvas.translate(-w / 2f, -h / 2f)

            val photo = bitmap
            if (photo != null) {
                // "Crop" scaling: fill the screen, centred.
                val s = max(w / photo.width, h / photo.height)
                canvas.save()
                canvas.translate((w - photo.width * s) / 2f, (h - photo.height * s) / 2f)
                canvas.scale(s, s)
                canvas.drawBitmap(photo, 0f, 0f, photoPaint)
                canvas.restore()
            } else {
                drawAurora(canvas, w, h)
            }
            canvas.restore()
        }

        /** Mirrors the launcher's built-in aurora so the lock screen matches the home screen. */
        private fun drawAurora(canvas: Canvas, w: Float, h: Float) {
            basePaint.shader = LinearGradient(
                0f, 0f, w, h,
                intArrayOf(0xFF0B1026.toInt(), 0xFF1B1146.toInt(), 0xFF3A1C5C.toInt()),
                null,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, w, h, basePaint)

            val unit = min(w, h)
            fun blob(cx: Float, cy: Float, r: Float, color: Int) {
                val radius = unit * r
                val center = Pair(w * cx, h * cy)
                val a = (0.85f * 255).toInt() shl 24
                blobPaint.shader = RadialGradient(
                    center.first, center.second, radius,
                    a or (color and 0x00FFFFFF),
                    color and 0x00FFFFFF,
                    Shader.TileMode.CLAMP,
                )
                canvas.drawCircle(center.first, center.second, radius, blobPaint)
            }
            blob(0.20f, 0.15f, 0.70f, 0xFF4F7CFF.toInt())
            blob(0.90f, 0.40f, 0.60f, 0xFFFF5FA2.toInt())
            blob(0.30f, 0.85f, 0.75f, 0xFF29D3C2.toInt())
        }
    }

    private companion object {
        const val EASE = 0.2f
        const val SETTLED = 0.002f
    }
}
