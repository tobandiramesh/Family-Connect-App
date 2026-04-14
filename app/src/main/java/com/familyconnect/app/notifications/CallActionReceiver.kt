package com.familyconnect.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.familyconnect.app.MainActivity
import com.familyconnect.app.data.repository.FirebaseService

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(NotificationHelper.EXTRA_CALL_ID) ?: return
        val threadId = intent.getStringExtra(NotificationHelper.EXTRA_THREAD_ID) ?: return

        when (intent.action) {
            NotificationHelper.ACTION_REJECT_CALL -> {
                Log.d("CallActionReceiver", "❌ REJECT button tapped for call=$callId")
                NotificationHelper.cancelCallNotification(context, callId)

                FirebaseService.updateCallStatus(threadId, callId, "rejected") { success ->
                    Log.d("CallActionReceiver", "Firebase updated: success=$success")
                }
                
                // 🔥 Stop the foreground service after reject
                try {
                    context.stopService(Intent(context, CallForegroundService::class.java))
                    Log.d("CallActionReceiver", "✅ CallForegroundService stopped")
                } catch (e: Exception) {
                    Log.e("CallActionReceiver", "❌ Error stopping service: ${e.message}")
                }
                
                // 🔥 RESTART the listener service after call ends
                // Get user mobile from SharedPreferences
                try {
                    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val userMobile = prefs.getString("user_mobile", "") ?: ""
                    if (userMobile.isNotEmpty()) {
                        Log.d("CallActionReceiver", "🔄 Restarting CallListenerService for: $userMobile")
                        CallListenerService.start(context, userMobile, "")
                        Log.d("CallActionReceiver", "✅ CallListenerService restarted")
                    } else {
                        Log.w("CallActionReceiver", "⚠️ No user mobile found to restart service")
                    }
                } catch (e: Exception) {
                    Log.e("CallActionReceiver", "❌ Error restarting listener: ${e.message}")
                }
            }
        }
    }
}
