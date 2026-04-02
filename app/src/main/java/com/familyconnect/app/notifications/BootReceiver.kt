package com.familyconnect.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.datastore.preferences.core.stringPreferencesKey
import com.familyconnect.app.FamilyConnectApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Restarts the CallListenerService after device reboot
 * so that notifications continue working even after the phone restarts.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device booted, checking if user was logged in")

            try {
                val app = context.applicationContext as? FamilyConnectApp ?: return
                // Check if user was logged in by reading saved mobile from DataStore
                val savedMobile = runBlocking {
                    app.repository.loggedInMobileFlow.first()
                }
                if (!savedMobile.isNullOrBlank()) {
                    Log.d("BootReceiver", "User $savedMobile was logged in, restarting listener service")
                    CallListenerService.start(context, savedMobile, "")
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error restarting service: ${e.message}")
            }
        }
    }
}
