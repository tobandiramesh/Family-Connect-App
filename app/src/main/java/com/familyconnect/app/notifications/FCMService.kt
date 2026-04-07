package com.familyconnect.app.notifications

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.messaging.FirebaseMessaging
import android.content.Context

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
        private const val PREF_FCM_TOKEN = "fcm_token_saved"
        private const val PREF_SAVED_MOBILE = "fcm_mobile"
        private const val FIREBASE_DB_URL = "https://family-connect-app-a219b-default-rtdb.asia-southeast1.firebasedatabase.app"
        
        /**
         * Force save FCM token for a specific user (call after login)
         */
        fun saveFCMTokenForUser(context: Context, mobile: String) {
            try {
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    val msg = "📱 Saving FCM token for user: $mobile"
                    Log.d(TAG, msg)
                    saveFCMTokenToFirebaseImpl(context, token, mobile)
                }
            } catch (e: Exception) {
                val msg = "❌ Error getting FCM token: ${e.message}"
                Log.e(TAG, msg)
            }
        }
        
        private fun saveFCMTokenToFirebaseImpl(context: Context, token: String, mobile: String) {
            try {
                val cleanMobile = mobile.filter { it.isDigit() }
                if (cleanMobile.isBlank()) {
                    val msg = "❌ Invalid mobile number: $mobile"
                    Log.w(TAG, msg)
                    return
                }
                
                try {
                    // Save token to Firebase at /users/{mobile}/fcm_token
                    val database = FirebaseDatabase.getInstance(FIREBASE_DB_URL)
                    
                    val usersRef = database.getReference("users")
                    val userRef = usersRef.child(cleanMobile)
                    val tokenRef = userRef.child("fcm_token")
                    
                    tokenRef.setValue(token)
                        .addOnSuccessListener {
                            val msg = "✅ FCM token saved to Firebase for: $cleanMobile"
                            Log.d(TAG, msg)
                            
                            // Also save in local prefs for next token refresh
                            val prefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                            prefs.edit().apply {
                                putString(PREF_SAVED_MOBILE, cleanMobile)
                                putString(PREF_FCM_TOKEN, token)
                                apply()
                            }
                        }
                        .addOnFailureListener { e ->
                            val msg = "❌ Failed to save FCM token: ${e.message} (${e.javaClass.simpleName})"
                            Log.e(TAG, msg)
                        }
                } catch (firebaseEx: Exception) {
                    val msg = "❌ Firebase exception: ${firebaseEx.message}"
                    Log.e(TAG, msg, firebaseEx)
                }
            } catch (e: Exception) {
                val msg = "❌ Error saving FCM token: ${e.message}"
                Log.e(TAG, msg)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val msg = "🔔 FCM token refreshed: ${token.take(10)}..."
        Log.d(TAG, msg)
        
        // Save token for currently logged-in user (if any)
        val prefs = getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        val lastMobile = prefs.getString(PREF_SAVED_MOBILE, null)
        
        if (!lastMobile.isNullOrBlank()) {
            val msg2 = "💾 Auto-saving FCM token for: $lastMobile"
            Log.d(TAG, msg2)
            saveFCMTokenToFirebaseImpl(this, token, lastMobile)
        } else {
            val msg3 = "⚠️ No user logged in yet, token will be saved after login"
            Log.d(TAG, msg3)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val msg = "📨 FCM message received"
        Log.d(TAG, msg)

        val data = message.data
        val type = data["type"] ?: ""

        Log.d(TAG, "🔍 FCM Data payload: $data")
        Log.d(TAG, "📦 Message type: $type")

        when (type) {
            "incoming_call" -> {
                val callId = data["callId"] ?: return
                val threadId = data["threadId"] ?: return
                val callerName = data["callerName"] ?: "Someone"
                val callType = data["callType"] ?: "audio"

                Log.d(TAG, "\n🎯 INCOMING CALL VIA FCM (PRIMARY ENTRY POINT)")
                Log.d(TAG, "   callId: $callId")
                Log.d(TAG, "   threadId: $threadId")
                Log.d(TAG, "   caller: $callerName")
                Log.d(TAG, "   callType: $callType ⭐")
                
                // 🔥 CRITICAL: FCMService MUST post notification directly
                // This is the primary path when app is closed/killed
                Log.d(TAG, "🔥 FCMService posting notification (app may be closed)")
                NotificationHelper.postIncomingCallNotification(
                    context = this,
                    callId = callId,
                    threadId = threadId,
                    callerName = callerName,
                    callType = callType
                )
                Log.d(TAG, "✅ FCMService notification posted successfully")
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
