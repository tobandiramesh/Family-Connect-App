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
            NotificationHelper.ACTION_ACCEPT_CALL -> {
                Log.d("CallActionReceiver", "✅ ACCEPT button tapped for call=$callId")
                val callerName = intent.getStringExtra(NotificationHelper.EXTRA_CALLER_NAME) ?: ""
                
                NotificationHelper.cancelCallNotification(context, callId)
                
                FirebaseService.updateCallStatus(threadId, callId, "accepted") { success ->
                    Log.d("CallActionReceiver", "Firebase updated: success=$success")
                }

                // Launch the app
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(NotificationHelper.EXTRA_CALL_ID, callId)
                    putExtra(NotificationHelper.EXTRA_THREAD_ID, threadId)
                    putExtra(NotificationHelper.EXTRA_CALLER_NAME, callerName)
                    putExtra("action", "accept_call")
                }
                
                try {
                    context.startActivity(launchIntent)
                    Log.d("CallActionReceiver", "✅ MainActivity launched")
                } catch (e: Exception) {
                    Log.e("CallActionReceiver", "❌ Error launching activity: ${e.message}")
                }
            }

            NotificationHelper.ACTION_REJECT_CALL -> {
                Log.d("CallActionReceiver", "❌ REJECT button tapped for call=$callId")
                NotificationHelper.cancelCallNotification(context, callId)

                FirebaseService.updateCallStatus(threadId, callId, "rejected") { success ->
                    Log.d("CallActionReceiver", "Firebase updated: success=$success")
                }
            }
        }
    }
}
