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
    private val foregroundFile = File(dir, "foreground.layer")
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

    fun foreground(): File? = foregroundFile.takeIf(File::isFile)

    @Synchronized
    @Throws(IOException::class, SecurityException::class)
    fun replaceForeground(uri: Uri): Boolean {
        val staged = File(dir, "foreground.new")
        val backup = File(dir, "foreground.backup")
        staged.delete()
        backup.delete()
        try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open the selected foreground image.")
            input.use { source -> staged.outputStream().use(source::copyTo) }
        } catch (e: IOException) {
            staged.delete()
            throw e
        } catch (e: SecurityException) {
            staged.delete()
            throw e
        }
        if (!isTransparentCutout(staged)) {
            staged.delete()
            throw IOException("Choose an image with a transparent background for the subject layer.")
        }

        if (foregroundFile.exists() && !foregroundFile.renameTo(backup)) {
            staged.delete()
            throw IOException("Could not stage the existing foreground image.")
        }
        if (!staged.renameTo(foregroundFile)) {
            if (backup.exists()) backup.renameTo(foregroundFile)
            staged.delete()
            throw IOException("Could not save the selected foreground image.")
        }
        backup.delete()
        return true
    }

    private fun isTransparentCutout(file: File): Boolean {
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 16 })
            ?: return false
        return try {
            if (!bitmap.hasAlpha()) return false
            var foundTransparent = false
            var foundSubject = false
            for (y in 0 until bitmap.height step 4) {
                for (x in 0 until bitmap.width step 4) {
                    when (android.graphics.Color.alpha(bitmap.getPixel(x, y))) {
                        in 1..244 -> {
                            foundTransparent = true
                            foundSubject = true
                        }
                        0 -> foundTransparent = true
                        in 245..255 -> foundSubject = true
                    }
                    if (foundTransparent && foundSubject) return true
                }
            }
            false
        } finally {
            bitmap.recycle()
        }
    }

    @Synchronized
    fun clearForeground() {
        foregroundFile.delete()
    }

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

    /** Replaces the whole set with [uris]. Returns false for an empty selection. Blocking. */
    @Synchronized
    @Throws(IOException::class, SecurityException::class)
    fun replaceWith(uris: List<Uri>): Boolean {
        if (uris.isEmpty()) return false
        val staged = mutableListOf<File>()
        try {
            uris.forEachIndexed { i, uri ->
                val tmp = File(dir, "new_%03d.tmp".format(i))
                staged += tmp
                val input = appContext.contentResolver.openInputStream(uri)
                    ?: throw IOException("Could not open selected wallpaper.")
                input.use { source -> tmp.outputStream().use(source::copyTo) }
            }
        } catch (e: IOException) {
            staged.forEach(File::delete)
            throw e
        } catch (e: SecurityException) {
            staged.forEach(File::delete)
            throw e
        }

        val existing = photos()
        val backups = existing.mapIndexed { index, photo ->
            val backup = File(dir, "old_%03d.img".format(index))
            if (!photo.renameTo(backup)) {
                backupsRestore(existing, existing.take(index).mapIndexed { backupIndex, _ ->
                    File(dir, "old_%03d.img".format(backupIndex))
                })
                staged.forEach(File::delete)
                throw IOException("Could not stage the existing wallpaper set.")
            }
            backup
        }

        val installed = mutableListOf<File>()
        try {
            staged.forEachIndexed { index, tmp ->
                val destination = File(dir, "%03d.img".format(index))
                if (!tmp.renameTo(destination)) {
                    throw IOException("Could not install the selected wallpaper.")
                }
                installed += destination
            }
        } catch (e: IOException) {
            installed.forEach(File::delete)
            backupsRestore(existing, backups)
            staged.forEach(File::delete)
            throw e
        }
        backups.forEach(File::delete)
        prefs.edit().putInt(KEY_INDEX, 0).putLong(KEY_LAST_ADVANCE, 0L).apply()
        return true
    }

    private fun backupsRestore(existing: List<File>, backups: List<File>) {
        existing.zip(backups).forEach { (original, backup) ->
            if (backup.exists()) backup.renameTo(original)
        }
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
