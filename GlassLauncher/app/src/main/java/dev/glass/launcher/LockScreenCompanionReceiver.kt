package dev.glass.launcher

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class LockScreenCompanionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SCREEN_ON) return

        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (keyguard !is KeyguardManager || !keyguard.isKeyguardLocked) return

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val defaultHome = context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (defaultHome?.activityInfo?.packageName != context.packageName) return

        val launch = Intent(context, LockScreenCompanionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("auto_open", true)
        }

        try {
            context.startActivity(launch)
        } catch (_: Exception) {
            // The system can reject background launches if the app is not currently in the foreground.
        }
    }
}
