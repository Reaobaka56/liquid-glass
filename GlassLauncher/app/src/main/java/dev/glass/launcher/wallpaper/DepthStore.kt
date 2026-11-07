package dev.glass.launcher.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Cut-out subjects (transparent PNGs) for the wallpaper photos, kept in their own folder so they
 * are never mistaken for photos. A cut-out is keyed by the photo's name and modification time, so
 * picking a different photo automatically invalidates it.
 */
class DepthStore(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "depth").apply { mkdirs() }

    fun subjectFileFor(photo: File): File =
        File(dir, "${photo.nameWithoutExtension}_${photo.lastModified()}.png")

    fun load(photo: File): Bitmap? {
        val file = subjectFileFor(photo)
        return if (file.exists()) BitmapFactory.decodeFile(file.path) else null
    }

    /** Blocking: call from a background thread. Replaces any older cut-out of the same slot. */
    fun save(photo: File, subject: Bitmap) {
        val prefix = photo.nameWithoutExtension + "_"
        dir.listFiles { f -> f.name.startsWith(prefix) }?.forEach { it.delete() }
        subjectFileFor(photo).outputStream().use { out ->
            subject.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    fun clear(photo: File) {
        subjectFileFor(photo).delete()
    }
}

/**
 * On-device subject segmentation (ML Kit). Runs locally; the model comes through Google Play
 * services and is downloaded once on first use, so the first run needs internet.
 */
object SubjectCutout {

    /** Returns the subject on a transparent background (same size as [source]), or null. */
    suspend fun generate(source: Bitmap): Bitmap? = suspendCancellableCoroutine { cont ->
        try {
            val segmenter = SubjectSegmentation.getClient(
                SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
            )
            segmenter.process(InputImage.fromBitmap(source, 0))
                .addOnSuccessListener { result ->
                    segmenter.close()
                    if (cont.isActive) cont.resume(result.foregroundBitmap)
                }
                .addOnFailureListener {
                    segmenter.close()
                    if (cont.isActive) cont.resume(null)
                }
            cont.invokeOnCancellation { segmenter.close() }
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }
}
