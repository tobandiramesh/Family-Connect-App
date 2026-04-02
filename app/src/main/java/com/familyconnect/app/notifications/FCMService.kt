package com.familyconnect.app.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Handles Firebase Cloud Messaging push notifications.
 * This ensures notifications are received even when the app process is killed.
 *
 * For FCM to trigger on calls/messages, you need Firebase Cloud Functions
 * that send FCM pushes when new data is written to the Realtime Database.
 */
class FCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed: ${token.take(10)}...")
        // Token can be saved to Firebase DB under the user's profile
        // for targeted push notifications from Cloud Functions
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM message received: ${message.data}")

        val data = message.data
        val type = data["type"] ?: ""

        when (type) {
            "incoming_call" -> {
                val callId = data["callId"] ?: return
                val threadId = data["threadId"] ?: return
                val callerName = data["callerName"] ?: "Someone"
                val callType = data["callType"] ?: "audio"

                NotificationHelper.postIncomingCallNotification(
                    context = this,
                    callId = callId,
                    threadId = threadId,
                    callerName = callerName,
                    callType = callType
                )
            }

            "new_message" -> {
                val senderName = data["senderName"] ?: "Someone"
                val messageBody = data["messageBody"] ?: "New message"
                val threadId = data["threadId"] ?: ""

                NotificationHelper.postMessageNotification(
                    context = this,
                    id = threadId.hashCode(),
                    senderName = senderName,
                    messageBody = messageBody
                )
            }

            else -> {
                // Handle notification payload (when app is in foreground)
                message.notification?.let { notification ->
                    NotificationHelper.post(
                        context = this,
                        id = message.messageId.hashCode(),
                        title = notification.title ?: "Family Connect",
                        body = notification.body ?: ""
                    )
                }
            }
        }
    }
}
