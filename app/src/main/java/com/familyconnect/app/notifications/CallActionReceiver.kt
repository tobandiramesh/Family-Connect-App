package com.familyconnect.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.familyconnect.app.MainActivity
import com.familyconnect.app.data.repository.FirebaseService

class CallActionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "CallActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(NotificationHelper.EXTRA_CALL_ID) ?: return
        val threadId = intent.getStringExtra(NotificationHelper.EXTRA_THREAD_ID) ?: return

        when (intent.action) {
            NotificationHelper.ACTION_ACCEPT_CALL -> {
                Log.d(TAG, "Call accepted from notification: callId=$callId")
                NotificationHelper.cancelCallNotification(context)

                // Update call status in Firebase
                FirebaseService.updateCallStatus(threadId, callId, "accepted") { success ->
                    Log.d(TAG, "Accept call status update: $success")
                }

                // Launch the app to handle the call
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(NotificationHelper.EXTRA_CALL_ID, callId)
                    putExtra(NotificationHelper.EXTRA_THREAD_ID, threadId)
                    putExtra(NotificationHelper.EXTRA_CALLER_NAME,
                        intent.getStringExtra(NotificationHelper.EXTRA_CALLER_NAME) ?: "")
                    putExtra("action", "accept_call")
                }
                context.startActivity(launchIntent)
            }

            NotificationHelper.ACTION_REJECT_CALL -> {
                Log.d(TAG, "Call rejected from notification: callId=$callId")
                NotificationHelper.cancelCallNotification(context)

                // Update call status in Firebase
                FirebaseService.updateCallStatus(threadId, callId, "rejected") { success ->
                    Log.d(TAG, "Reject call status update: $success")
                }
            }
        }
    }
}
