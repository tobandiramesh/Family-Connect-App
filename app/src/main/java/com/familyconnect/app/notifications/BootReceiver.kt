package com.familyconnect.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.familyconnect.app.utils.CredentialsManager

/**
 * Restarts the CallListenerService after device reboot
 * so that notifications continue working even after the phone restarts.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "🔧 Device booted - checking for logged-in user...")

            try {
                // Check if there's a logged-in user with saved credentials
                val userMobile = CredentialsManager.getUserMobile(context)
                val userName = CredentialsManager.getUserName(context)

                if (!userMobile.isNullOrBlank() && !userName.isNullOrBlank()) {
                    Log.d(TAG, "✅ Found saved credentials - restarting CallListenerService for: $userMobile")
                    CallListenerService.start(context, userMobile, userName)
                    Log.d(TAG, "🎯 CallListenerService started on boot")
                } else {
                    Log.d(TAG, "ℹ️ No saved credentials - user needs to login first")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error restarting service on boot: ${e.message}", e)
            }
        }
    }
}
