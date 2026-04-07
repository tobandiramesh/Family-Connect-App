package com.familyconnect.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.familyconnect.app.MainActivity
import com.familyconnect.app.data.repository.FirebaseService

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(NotificationHelper.EXTRA_CALL_ID) ?: return
        val threadId = intent.getStringExtra(NotificationHelper.EXTRA_THREAD_ID) ?: return

        when (intent.action) {
            NotificationHelper.ACTION_ACCEPT_CALL -> {
                val callerName = intent.getStringExtra(NotificationHelper.EXTRA_CALLER_NAME) ?: ""
                
                NotificationHelper.cancelCallNotification(context, callId)
                
                FirebaseService.updateCallStatus(threadId, callId, "accepted") { success ->
                    // Handle result
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
                } catch (e: Exception) {
                    // Silent failure
                }
            }

            NotificationHelper.ACTION_REJECT_CALL -> {
                NotificationHelper.cancelCallNotification(context, callId)

                FirebaseService.updateCallStatus(threadId, callId, "rejected") { success ->
                    // Handle result
                }
            }
        }
    }
}
