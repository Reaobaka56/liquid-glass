package dev.glass.launcher

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.glass.launcher.ui.LockScreenCompanionScreen

class LockScreenCompanionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(false)
        }
        enableEdgeToEdge()
        setContent {
            LockScreenCompanionScreen(
                onContinueToDeviceUnlock = ::continueToDeviceUnlock,
                onClose = ::finish,
            )
        }
    }

    private fun continueToDeviceUnlock() {
        val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isKeyguardLocked) {
            finish()
            return
        }

        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    finish()
                }
            },
        )
    }
}