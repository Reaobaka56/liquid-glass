package dev.glass.launcher.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.IOException

/**
 * The launcher's photo set, shared between the launcher UI and [GlassWallpaperService].
 *
 * Photos are copied into the app's own storage when picked, so no URI permissions have to
 * be kept alive. One photo is a plain wallpaper; several photos are a shuffle set, and the
 * wallpaper service steps to the next one every time the screen turns off.
 */
class WallpaperStore(context: Context) {

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "wallpapers").apply { mkdirs() }
    private val prefs = appContext.getSharedPreferences("wallpaper", Context.MODE_PRIVATE)

    init {
        // Older versions stored a single photo as wallpaper.img; keep it as photo #1.
        val legacy = File(appContext.filesDir, "wallpaper.img")
        if (legacy.exists() && photos().isEmpty()) {
            legacy.renameTo(File(dir, "000.img"))
        }
    }

    /** Whether the wallpaper service advances to the next photo when the screen locks. */
    var shuffleOnLock: Boolean
        get() = prefs.getBoolean(KEY_SHUFFLE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHUFFLE, value).apply()

    fun photos(): List<File> =
        dir.listFiles { f -> f.isFile && f.extension == "img" }?.sortedBy { it.name }.orEmpty()

    /** The photo that should be showing right now, or null if none has been picked. */
    fun current(): File? {
        val list = photos()
        if (list.isEmpty()) return null
        return list[prefs.getInt(KEY_INDEX, 0).mod(list.size)]
    }

    /**
     * Moves to the next photo, but only if the last move was at least [minGapMs] ago. The
     * home and lock screens can each run their own wallpaper engine; this keeps them from
     * both stepping on the same screen-off.
     */
    @Synchronized
    fun advanceIfDue(minGapMs: Long = 1_500L): File? {
        val list = photos()
        if (list.size < 2 || !shuffleOnLock) return current()
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_ADVANCE, 0L) < minGapMs) return current()
        prefs.edit()
            .putInt(KEY_INDEX, (prefs.getInt(KEY_INDEX, 0) + 1).mod(list.size))
            .putLong(KEY_LAST_ADVANCE, now)
            .apply()
        return current()
    }

    /** Replaces the whole set with [uris]. Blocking: call from a background thread. */
    @Synchronized
    fun replaceWith(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val copied = mutableListOf<File>()
        uris.forEachIndexed { i, uri ->
            val tmp = File(dir, "new_%03d.tmp".format(i))
            val ok = runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                } != null
            }.getOrDefault(false)
            if (ok) copied += tmp else tmp.delete()
        }
        if (copied.isEmpty()) return

        photos().forEach { it.delete() }
        copied.forEachIndexed { i, tmp -> tmp.renameTo(File(dir, "%03d.img".format(i))) }
        prefs.edit().putInt(KEY_INDEX, 0).putLong(KEY_LAST_ADVANCE, 0L).apply()
    }

    /**
     * Decodes [file] downsampled so it is not much larger than [targetW] x [targetH], and applies
     * the EXIF orientation (phone photos are usually stored sideways with a rotate tag, which
     * BitmapFactory ignores).
     */
    fun decode(file: File, targetW: Int, targetH: Int): Bitmap? {
        val orientation = exifOrientation(file)
        val swapped = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE
        // Stored width/height are swapped relative to what the screen will see.
        val needW = if (swapped) targetH else targetW
        val needH = if (swapped) targetW else targetH

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= needW &&
            bounds.outHeight / (sample * 2) >= needH
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(file.path, options) ?: return null

        val matrix = exifMatrix(orientation) ?: return decoded
        return try {
            val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (oriented !== decoded) decoded.recycle()
            oriented
        } catch (e: OutOfMemoryError) {
            decoded // Better a sideways photo than none.
        }
    }

    private fun exifOrientation(file: File): Int = try {
        ExifInterface(file.path)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (e: IOException) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun exifMatrix(orientation: Int): Matrix? {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return null
        }
        return m
    }

    private companion object {
        const val KEY_INDEX = "index"
        const val KEY_SHUFFLE = "shuffle_on_lock"
        const val KEY_LAST_ADVANCE = "last_advance"
    }
}
