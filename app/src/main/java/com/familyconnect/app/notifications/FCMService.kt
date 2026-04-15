package com.familyconnect.app.notifications

import android.util.Log
import android.content.Intent
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.messaging.FirebaseMessaging
import android.content.Context
import android.content.ComponentName
import android.os.Bundle

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
                
                // 🔥 SUBSCRIBE TO TOPICS - WITH ERROR HANDLING
                subscribeToTopicsWithRetry()
            } catch (e: Exception) {
                val msg = "❌ Error getting FCM token: ${e.message}"
                Log.e(TAG, msg, e)
            }
        }
        
        private fun subscribeToTopicsWithRetry() {
            try {
                FirebaseMessaging.getInstance().subscribeToTopic("incoming_calls")
                    .addOnSuccessListener {
                        Log.d(TAG, "✅ [SUCCESS] Subscribed to topic: incoming_calls")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ [FAILED] Subscribe to incoming_calls: ${e.message}", e)
                    }
                
                FirebaseMessaging.getInstance().subscribeToTopic("messages")
                    .addOnSuccessListener {
                        Log.d(TAG, "✅ [SUCCESS] Subscribed to topic: messages")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ [FAILED] Subscribe to messages: ${e.message}", e)
                    }
                
                Log.d(TAG, "📢 Topic subscription requests sent to FCM")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception during topic subscription: ${e.message}", e)
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

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data

        Log.e("FCM_DEBUG", "🔥 FCM RECEIVED: $data")

        if (data["type"] == "incoming_call") {
            val callId = data["callId"] ?: return
            val threadId = data["threadId"] ?: return
            val callerName = data["callerName"] ?: "Unknown"
            val callType = data["callType"] ?: "audio"

            Log.d(TAG, "📞 Incoming call from FCM: $callId from $callerName")
            
            // ✅ FIXED: Removed nested CallForegroundService (was blocking notification taps)
            // CallListenerService (single foreground service) listens to Firebase and posts notifications
            // Single foreground service context allows taps to work on Android 14+
            Log.d(TAG, "   ✅ Notification will be posted by CallListenerService (Firebase listener)")
        }
    }
    
    // ✅ REMOVED: fallbackToCallForegroundService() method - no longer needed
    // CallListenerService handles all notifications via Firebase listener (single foreground service)
}
