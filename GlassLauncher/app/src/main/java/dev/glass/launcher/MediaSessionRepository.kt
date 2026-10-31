package dev.glass.launcher

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.media.session.MediaSessionManager
import android.os.Build
import android.provider.Settings

data class MediaSnapshot(
    val title: String,
    val artist: String,
    val playing: Boolean,
)

object MediaSessionRepository {
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

    fun snapshot(context: Context): MediaSnapshot? = activeController(context)?.let { controller ->
        val metadata = controller.metadata
        val state = controller.playbackState?.state
        MediaSnapshot(
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?.takeIf(String::isNotBlank) ?: "Unknown track",
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?.takeIf(String::isNotBlank) ?: "Unknown artist",
            playing = state == PlaybackState.STATE_PLAYING ||
                state == PlaybackState.STATE_BUFFERING ||
                state == PlaybackState.STATE_CONNECTING,
        )
    }

    fun togglePlayback(context: Context) {
        val controller = activeController(context) ?: return
        val state = controller.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    fun skipNext(context: Context) {
        activeController(context)?.transportControls?.skipToNext()
    }

    private fun activeController(context: Context): MediaController? {
        if (!hasNotificationAccess(context)) return null
        val manager = context.getSystemService(MediaSessionManager::class.java) ?: return null
        val controllers = manager.getActiveSessions(
            ComponentName(context, GlassNotificationListenerService::class.java),
        )
        return controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull()
    }
}
