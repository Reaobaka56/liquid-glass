package dev.glass.launcher.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat

/**
 * Observes whatever app is playing media and exposes it as Compose state.
 * Needs notification access; [hasAccess] is false until the user grants it.
 */
class MediaUi(private val context: Context) {

    var hasAccess by mutableStateOf(false)
        private set
    var active by mutableStateOf(false)
        private set
    var title by mutableStateOf("")
        private set
    var artist by mutableStateOf("")
        private set
    var art by mutableStateOf<ImageBitmap?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var durationMs by mutableStateOf(0L)
        private set

    private var basePos = 0L
    private var baseTime = 0L
    private var speed = 1f

    private var controller: MediaController? = null
    private val component = ComponentName(context, GlassNotificationListener::class.java)
    private val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = readState()
        override fun onPlaybackStateChanged(state: PlaybackState?) = readState()
        override fun onSessionDestroyed() = refresh()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { refresh() }
    private var listening = false

    fun start() {
        refresh()
        if (hasAccess && !listening) {
            try {
                manager.addOnActiveSessionsChangedListener(sessionsListener, component)
                listening = true
            } catch (e: SecurityException) {
                hasAccess = false
            }
        }
    }

    fun stop() {
        if (listening) {
            manager.removeOnActiveSessionsChangedListener(sessionsListener)
            listening = false
        }
        controller?.unregisterCallback(callback)
        controller = null
    }

    /** Re-checks access and picks the session to show (the one playing, else the first). */
    fun refresh() {
        hasAccess = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        if (!hasAccess) {
            detach()
            return
        }
        val sessions = try {
            manager.getActiveSessions(component)
        } catch (e: SecurityException) {
            hasAccess = false
            detach()
            return
        }
        val pick = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull()
        if (pick?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = pick
            pick?.registerCallback(callback)
        }
        readState()
    }

    private fun detach() {
        controller?.unregisterCallback(callback)
        controller = null
        active = false
        playing = false
    }

    private fun readState() {
        val c = controller
        if (c == null) {
            active = false
            return
        }
        val meta = c.metadata
        val state = c.playbackState
        title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        art = (meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART))?.asImageBitmap()
        durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        playing = state?.state == PlaybackState.STATE_PLAYING
        basePos = state?.position ?: 0L
        baseTime = state?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime()
        speed = state?.playbackSpeed ?: 1f
        active = title.isNotEmpty() || artist.isNotEmpty()
    }

    /** Playback position now, extrapolated from the last update the player reported. */
    fun positionMs(): Long {
        if (!playing) return basePos.coerceIn(0L, durationMs.coerceAtLeast(0L))
        val p = basePos + ((SystemClock.elapsedRealtime() - baseTime) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0L, durationMs) else p.coerceAtLeast(0L)
    }

    fun playPause() {
        val c = controller ?: return
        if (playing) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = controller?.transportControls?.skipToNext()
    fun previous() = controller?.transportControls?.skipToPrevious()
    fun seekTo(ms: Long) = controller?.transportControls?.seekTo(ms)
}
