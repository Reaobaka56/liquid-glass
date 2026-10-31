package dev.glass.launcher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.LruCache
import android.util.Log
import java.io.Closeable
import java.io.IOException

data class MediaSnapshot(
    val title: String,
    val artist: String,
    val album: String?,
    val artwork: Bitmap?,
    val playing: Boolean,
    val canTogglePlayback: Boolean,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
)

object MediaSessionRepository {
    private const val TAG = "MediaSessionRepository"
    private const val ARTWORK_SIZE = 512
    private const val CACHE_SIZE_KB = 12 * 1024

    private val artworkCache = object : LruCache<String, Bitmap>(CACHE_SIZE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun hasNotificationAccess(context: Context): Boolean {
        val component = ComponentName(context, GlassNotificationListenerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            return context.getSystemService(android.app.NotificationManager::class.java)
                .isNotificationListenerAccessGranted(component)
        }
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        return enabled.split(':').any { it == component.flattenToString() }
    }

    fun snapshot(context: Context): MediaSnapshot? =
        activeController(context)?.let { snapshot(context, it) }

    fun observe(context: Context, onChanged: (MediaSnapshot?) -> Unit): Closeable {
        val manager = context.getSystemService(MediaSessionManager::class.java)
            ?: throw IllegalStateException("Media sessions are unavailable on this device.")
        val component = ComponentName(context, GlassNotificationListenerService::class.java)
        val callbacks = mutableMapOf<MediaSession.Token, MediaController.Callback>()
        var controllers = emptyList<MediaController>()

        fun publish() {
            val current = try {
                selectController(controllers)?.let { snapshot(context, it) }
            } catch (e: SecurityException) {
                Log.w(TAG, "Media-session access was revoked while refreshing metadata.", e)
                null
            }
            onChanged(current)
        }

        fun update(next: List<MediaController>?) {
            val newControllers = next.orEmpty()
            val newTokens = newControllers.mapTo(mutableSetOf()) { it.sessionToken }
            callbacks.keys.filterNot(newTokens::contains).forEach { token ->
                val oldController = controllers.firstOrNull { it.sessionToken == token }
                val callback = callbacks.remove(token)
                if (oldController != null && callback != null) {
                    oldController.unregisterCallback(callback)
                }
            }
            newControllers.forEach { controller ->
                if (controller.sessionToken !in callbacks) {
                    val callback = object : MediaController.Callback() {
                        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
                        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                        override fun onSessionDestroyed() {
                            controllers = controllers.filterNot { it.sessionToken == controller.sessionToken }
                            callbacks.remove(controller.sessionToken)
                            publish()
                        }
                    }
                    controller.registerCallback(callback, Handler(Looper.getMainLooper()))
                    callbacks[controller.sessionToken] = callback
                }
            }
            controllers = newControllers
            publish()
        }

        val listener = MediaSessionManager.OnActiveSessionsChangedListener(::update)
        manager.addOnActiveSessionsChangedListener(listener, component, Handler(Looper.getMainLooper()))
        update(manager.getActiveSessions(component))

        return Closeable {
            manager.removeOnActiveSessionsChangedListener(listener)
            callbacks.forEach { (token, callback) ->
                controllers.firstOrNull { it.sessionToken == token }?.unregisterCallback(callback)
            }
            callbacks.clear()
            controllers = emptyList()
        }
    }

    fun togglePlayback(context: Context) {
        val controller = activeController(context) ?: return
        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    fun skipPrevious(context: Context) {
        activeController(context)?.transportControls?.skipToPrevious()
    }

    fun skipNext(context: Context) {
        activeController(context)?.transportControls?.skipToNext()
    }

    private fun activeController(context: Context): MediaController? {
        if (!hasNotificationAccess(context)) return null
        val manager = context.getSystemService(MediaSessionManager::class.java) ?: return null
        val component = ComponentName(context, GlassNotificationListenerService::class.java)
        return selectController(manager.getActiveSessions(component))
    }

    private fun selectController(controllers: List<MediaController>): MediaController? {
        val playing = controllers.firstOrNull {
            val state = it.playbackState?.state
            state == PlaybackState.STATE_PLAYING ||
                state == PlaybackState.STATE_BUFFERING ||
                state == PlaybackState.STATE_CONNECTING
        }
        if (playing != null) return playing

        return controllers.firstOrNull { controller ->
            controller.playbackState?.state == PlaybackState.STATE_PAUSED &&
                controller.metadata?.hasCurrentTrack() == true
        }
    }

    private fun snapshot(context: Context, controller: MediaController): MediaSnapshot? {
        val metadata = controller.metadata ?: return null
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?.takeIf(String::isNotBlank)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?.takeIf(String::isNotBlank)
            ?: return null
        val playback = controller.playbackState ?: return null
        val actions = playback.actions
        return MediaSnapshot(
            title = title,
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?.takeIf(String::isNotBlank)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                    ?.takeIf(String::isNotBlank)
                ?: "Unknown artist",
            album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
                ?.takeIf(String::isNotBlank),
            artwork = artwork(context, controller.packageName, metadata),
            playing = playback.state == PlaybackState.STATE_PLAYING,
            canTogglePlayback = actions and (
                PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE
                ) != 0L,
            canSkipPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
        )
    }

    private fun MediaMetadata.hasCurrentTrack(): Boolean =
        !getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank() ||
            !getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).isNullOrBlank()

    private fun artwork(context: Context, packageName: String, metadata: MediaMetadata): Bitmap? {
        val embedded = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        val uri = metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
        val cacheKey = listOf(
            packageName,
            metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).orEmpty(),
            uri.orEmpty(),
            embedded?.let(System::identityHashCode)?.toString().orEmpty(),
        ).joinToString("|")
        artworkCache.get(cacheKey)?.let { return it }

        val decoded = embedded?.let(::fitArtwork)
            ?: uri?.let { decodeArtwork(context, Uri.parse(it)) }
            ?: return null
        artworkCache.put(cacheKey, decoded)
        return decoded
    }

    private fun fitArtwork(bitmap: Bitmap): Bitmap {
        val maxDimension = maxOf(bitmap.width, bitmap.height)
        if (maxDimension <= ARTWORK_SIZE) return bitmap
        val scale = ARTWORK_SIZE.toFloat() / maxDimension
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun decodeArtwork(context: Context, uri: Uri): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > ARTWORK_SIZE * 2) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }?.let(::fitArtwork)
        } catch (e: IOException) {
            Log.w(TAG, "Could not read media artwork at $uri.", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Access to media artwork at $uri was denied.", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Media session returned an invalid artwork URI.", e)
            null
        }
    }
}
