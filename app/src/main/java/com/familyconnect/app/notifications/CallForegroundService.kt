package com.familyconnect.app.notifications

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.PendingIntent
import com.familyconnect.app.IncomingCallActivity

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

    private fun triggerIncomingCall(
        callId: String,
        threadId: String,
        callerName: String,
        callType: String
    ) {
        Log.d(TAG, "   🔥 triggerIncomingCall() starting...")
        
        // 🔥 CRITICAL FIX: Strong PendingIntent configuration for background launches
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            action = "INCOMING_CALL"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(NotificationHelper.EXTRA_CALL_ID, callId)
            putExtra(NotificationHelper.EXTRA_THREAD_ID, threadId)
            putExtra(NotificationHelper.EXTRA_CALLER_NAME, callerName)
            putExtra(NotificationHelper.EXTRA_CALL_TYPE, callType)
        }

        // 🔥 CRITICAL: Fixed request code (1001) - NOT 0 - ensures stable PendingIntent
        val pendingIntent = PendingIntent.getActivity(
            this,
            1001,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 🔥 STEP 1: Create MINIMAL foreground notification FIRST
        Log.d(TAG, "   ✅ STEP 1: Creating minimal foreground notification...")
        val tempNotification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Incoming Call")
            .setContentText("Connecting...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // 🔥 STEP 2: Start foreground BEFORE full-screen notification
        Log.d(TAG, "   ✅ STEP 2: Starting foreground service...")
        startForeground(NotificationHelper.CALL_NOTIFICATION_ID, tempNotification)
        Log.d(TAG, "   ✅ STEP 2: Foreground service started")

        // 🔥 STEP 3: NOW build FULL-SCREEN notification (after foreground established)
        Log.d(TAG, "   ✅ STEP 3: Building full-screen notification...")
        val fullScreenNotification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("📞 Incoming Call")
            .setContentText("$callerName is calling...")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE))
            .setVibrate(longArrayOf(0, 500, 300, 500))
            .build()

        // 🔥 STEP 4: Update notification (now with full-screen intent)
        Log.d(TAG, "   ✅ STEP 4: Updating notification with full-screen intent...")
        val manager = NotificationManagerCompat.from(this)
        manager.notify(NotificationHelper.CALL_NOTIFICATION_ID, fullScreenNotification)
        Log.d(TAG, "   ✅ STEP 4: Notification updated")

        // 🔥 STEP 5: Android will launch activity via PendingIntent (NOT via startActivity)
        // ❌ REMOVED: startActivity(intent) - this is BLOCKED when app is backgrounded
        // ✅ TRUSTING: PendingIntent in notification to launch IncomingCallActivity
        Log.d(TAG, "   ✅ STEP 5: Notification with PendingIntent ready (Android will launch activity)")
        
        Log.d(TAG, "   ✅✅✅ INCOMING CALL NOTIFICATION POSTED SUCCESSFULLY ✅✅✅")
        
        // Stop service after a delay
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

    override fun onBind(intent: Intent?): IBinder? = null
}
