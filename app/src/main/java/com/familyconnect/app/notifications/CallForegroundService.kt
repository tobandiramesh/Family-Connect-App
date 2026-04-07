package com.familyconnect.app.notifications

import android.app.Service
import android.content.Intent
import android.content.Context
import android.os.IBinder
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import android.telecom.TelecomManager

class CallForegroundService : Service() {
    
    private val TAG = "CallForegroundService"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🚀 onStartCommand() called")
        
        val callId = intent?.getStringExtra(NotificationHelper.EXTRA_CALL_ID)
        val threadId = intent?.getStringExtra(NotificationHelper.EXTRA_THREAD_ID)
        val callerName = intent?.getStringExtra(NotificationHelper.EXTRA_CALLER_NAME)
        val callType = intent?.getStringExtra(NotificationHelper.EXTRA_CALL_TYPE)
        
        Log.d(TAG, "   📞 Call data: id=$callId, from=$callerName, type=$callType")

        try {
            triggerIncomingCall(callId ?: "", threadId ?: "", callerName ?: "Unknown", callType ?: "audio")
        } catch (e: Exception) {
            Log.e(TAG, "   ❌ Error: ${e.message}", e)
        }

        return START_NOT_STICKY
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.READ_PHONE_STATE)
    private fun triggerIncomingCall(
        callId: String,
        threadId: String,
        callerName: String,
        callType: String
    ) {
        Log.d(TAG, "🔥 triggerIncomingCall() - Using TELECOM (not notifications)")
        
        // 🔥 DEBUG: Log ALL data before creating bundle
        Log.e("CALL_DEBUG", "🔥 CREATING TELECOM CALL WITH:")
        Log.e("CALL_DEBUG", "   callId: $callId")
        Log.e("CALL_DEBUG", "   threadId: $threadId")
        Log.e("CALL_DEBUG", "   callerName: $callerName")
        Log.e("CALL_DEBUG", "   callType: $callType")
        
        try {
            // ✅ CORRECT APPROACH: Use Telecom Framework
            // This delegates to Android system to show proper call UI
            // No notifications, no PendingIntent issues, no extras lost
            
            // Get phone account handle
            // We use the one registered during app initialization
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            
            // Try to get our registered phone account
            val phoneAccounts = telecomManager.callCapablePhoneAccounts
            Log.d(TAG, "Available phone accounts: ${phoneAccounts.size}")
            
            val phoneAccountHandle = if (phoneAccounts.isNotEmpty()) {
                // Use the first available account (ours)
                phoneAccounts[0]
            } else {
                Log.e(TAG, "❌ No phone accounts available")
                null
            }
            
            // 🔥 CRITICAL: Pass ALL data via Bundle (not extras)
            // Bundle goes to MyConnectionService.onCreateIncomingConnection()
            val callExtras = Bundle().apply {
                putString("callId", callId)
                putString("threadId", threadId)
                putString("callerName", callerName)
                putString("callType", callType)
            }
            
            Log.d(TAG, "   🔥 Calling telecomManager.addNewIncomingCall()...")
            Log.e("CALL_DEBUG", "✅ Bundle created with all data:")
            Log.e("CALL_DEBUG", "   callId=${callExtras.getString("callId")}")
            Log.e("CALL_DEBUG", "   threadId=${callExtras.getString("threadId")}")
            Log.e("CALL_DEBUG", "   callerName=${callExtras.getString("callerName")}")
            Log.e("CALL_DEBUG", "   callType=${callExtras.getString("callType")}")
            
            if (phoneAccountHandle != null) {
                telecomManager.addNewIncomingCall(phoneAccountHandle, callExtras)
                Log.d(TAG, "✅ addNewIncomingCall() succeeded - System UI handling display")
                Log.e("CALL_DEBUG", "✅ TELECOM CALL INITIATED - Android system will show UI")
                Toast.makeText(this, "✅ Telecom call routing to system UI", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "❌ PhoneAccountHandle is null - call cannot be routed")
                // Fallback: Show minimal foreground notification
                showFallbackNotification(callerName)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in addNewIncomingCall: ${e.message}", e)
            Log.e("CALL_DEBUG", "❌ Telecom error, falling back to notification:", e)
            // Fallback to notification
            showFallbackNotification(callerName)
        }
        
        // Keep service alive for a bit, then stop
        Thread {
            try {
                Thread.sleep(1500)
                stopSelf()
                Log.d(TAG, "   🛑 Service stopped")
            } catch (e: Exception) {
                Log.e(TAG, "   ⚠️ Error stopping service: ${e.message}")
            }
        }.start()
    }
    
    private fun showFallbackNotification(callerName: String) {
        // 🔥 FALLBACK: Only if Telecom fails
        // Create minimal foreground notification
        Log.d(TAG, "Creating fallback foreground notification...")
        val tempNotification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Incoming Call")
            .setContentText("$callerName is calling...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NotificationHelper.CALL_NOTIFICATION_ID, tempNotification)
        Toast.makeText(this, "⚠️ Using fallback notification", Toast.LENGTH_SHORT).show()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
