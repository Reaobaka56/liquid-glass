package dev.glass.launcher.media

import android.service.notification.NotificationListenerService

/** Does nothing itself; having it enabled grants access to media sessions and notifications. */
class GlassNotificationListener : NotificationListenerService()
