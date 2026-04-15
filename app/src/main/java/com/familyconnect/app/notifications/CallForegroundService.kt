package com.familyconnect.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import android.app.PendingIntent
import com.familyconnect.app.MainActivity
import com.familyconnect.app.activities.IncomingCallActivity

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

        // 🔥 CRITICAL: Use START_STICKY to keep service alive
        // Service should stay alive for notification + action button handling
        return START_STICKY
    }

    private fun triggerIncomingCall(
        callId: String,
        threadId: String,
        callerName: String,
        callType: String
    ) {
        Log.d(TAG, "   🔥 triggerIncomingCall() starting...")
        
        // 🔥 ABSOLUTELY CRITICAL: Create channel RIGHT HERE in SERVICE context
        // Before this: channel was in Application class (different context/lifecycle)
        // This caused Android to ignore it and use fallback "Other" channel
        // NOW: channel is created in SAME context as notification → Android registers it
        Log.d(TAG, "   🔧 Creating call notification channel in SERVICE context...")
        ensureCallChannelInService()
        Log.d(TAG, "   ✅ Channel created in service context")
        
        val notificationId = Math.abs(callId.hashCode())
        Log.d(TAG, "   🔑 Notification ID: $notificationId")
        
        // 🔥 CRITICAL CHECK: Verify FullScreenIntent permission is granted
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val canUseFullScreen = notificationManager.canUseFullScreenIntent()
        Log.d(TAG, "   🔐 FullScreenIntent permission granted: $canUseFullScreen")
        
        if (!canUseFullScreen) {
            Log.e(TAG, "   ❌ FullScreenIntent permission NOT granted - opening settings...")
            
            try {
                val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = android.net.Uri.parse("package:$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(settingsIntent)
                Log.d(TAG, "   ✅ Opened settings for full screen intent permission")
            } catch (e: Exception) {
                Log.e(TAG, "   ⚠️ Could not open settings: ${e.message}")
            }
        }
        
        // 🔥 DEBUG: Check actual channel importance
        val callChannel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_CALLS)
        val importance = callChannel?.importance ?: -1
        val importanceLabel = when (importance) {
            NotificationManager.IMPORTANCE_NONE -> "NONE (0)"
            NotificationManager.IMPORTANCE_MIN -> "MIN (1)"
            NotificationManager.IMPORTANCE_LOW -> "LOW (2)"
            NotificationManager.IMPORTANCE_DEFAULT -> "DEFAULT (3)"
            NotificationManager.IMPORTANCE_HIGH -> "HIGH (4)"
            NotificationManager.IMPORTANCE_MAX -> "MAX (5)"
            else -> "UNKNOWN ($importance)"
        }
        Log.d(TAG, "   📊 CHANNEL_CALLS importance: $importanceLabel")
        
        // 🔥 CRITICAL: Make Intent with unique requestCode for proper PendingIntent handling
        // Android 12+ reuses PendingIntent with same requestCode → breaks tap
        Log.d(TAG, "   ✅ Creating FullScreenIntent for IncomingCallActivity...")
        val callActivityIntent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            
            putExtra(NotificationHelper.EXTRA_CALL_ID, callId)
            putExtra(NotificationHelper.EXTRA_THREAD_ID, threadId)
            putExtra(NotificationHelper.EXTRA_CALLER_NAME, callerName)
            putExtra(NotificationHelper.EXTRA_CALL_TYPE, callType)
        }
        
        // 🔥 CRITICAL: Use unique requestCode to prevent PendingIntent reuse
        // requestCode=0 causes Android to cache and reuse old PendingIntent
        val uniqueRequestCode = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        Log.d(TAG, "   🔑 PendingIntent requestCode: $uniqueRequestCode (unique, prevents caching)")
        
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            uniqueRequestCode,
            callActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )
        
        // 🔥 STEP 1: Build notification with FullScreenIntent
        // This is the ONLY way Android allows Activity launch from background service
        Log.d(TAG, "   ✅ Building call notification with FullScreenIntent...")
        val callNotification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Incoming Call")
            .setContentText("$callerName is calling...")
            
            .setPriority(NotificationCompat.PRIORITY_MAX) // 🔥 MUST be MAX for heads-up/full-screen on Android 13+
            .setCategory(NotificationCompat.CATEGORY_MESSAGE) // ✅ Changed from CATEGORY_CALL - allows ContentIntent to fire
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false) // 🔥 CRITICAL: Must be false to allow user interaction on Android 14+
            .setAutoCancel(true) // 🔥 Allow user to dismiss notification
            
            // ✅ SIMPLE: Just use contentIntent for tap (no fullscreen, no service behavior flags)
            // These flags suppress click interaction on Android 14+
            .setContentIntent(fullScreenPendingIntent)
            .setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE))
            .setVibrate(longArrayOf(0, 500, 300, 500))
            .build()
        
        // 🔥 CRITICAL: Acquire WakeLock to ensure screen wakes up
        Log.d(TAG, "   🧪 Acquiring WakeLock to wake screen...")
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "app:call_wake"
        )
        wakeLock.acquire(3000) // 3 second timeout
        Log.d(TAG, "   ✅ WakeLock acquired")
        
        // 🔥 CRITICAL FIX: Split notifications for Android 14+ compatibility
        // startForeground() notification becomes non-interactive on Android 14+
        // Solution: Use LOW-PRIORITY service notification for startForeground()
        //          Use NORMAL notification for interactive call UI
        
        // 🔹 PART 1: Create low-priority service notification for startForeground()
        Log.d(TAG, "   ✅ Creating background service notification...")
        val serviceNotification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Call Service Running")
            .setContentText("Listening for incoming calls")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        // Keep service in foreground with non-interactive notification
        startForeground(8002, serviceNotification)
        Log.d(TAG, "   ✅ Service notification posted (non-interactive)")
        
        // 🔹 PART 2: Post INTERACTIVE call notification separately
        // This notification has FullScreenIntent and is fully clickable
        Log.d(TAG, "   ✅ Posting interactive call notification...")
        notificationManager.notify(notificationId, callNotification)
        Log.d(TAG, "   ✅ Call notification posted (interactive with FullScreenIntent)")
        
        Log.d(TAG, "   ✅✅✅ INCOMING CALL NOTIFICATION POSTED - ACTIVITY WILL LAUNCH VIA FULLSCREENINTENT ✅✅✅")
        
        // ✅ Service stays alive to maintain notification
        // When FullScreenIntent triggers, IncomingCallActivity will open
        Log.d(TAG, "   📌 Service remaining active for call lifecycle")
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    // 🔥 CRITICAL: Create call channel in SERVICE context (not Application)
    // This ensures Android properly registers the channel before notification is posted
    // Context mismatch causes Android to fallback to "Other" channel
    private fun ensureCallChannelInService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val callChannel = NotificationChannel(
                NotificationHelper.CHANNEL_CALLS,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming call alerts"
                setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                enableLights(true)
                setBypassDnd(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            
            manager.createNotificationChannel(callChannel)
            Log.d(TAG, "✅ Call channel created in SERVICE context: ${NotificationHelper.CHANNEL_CALLS}")
        }
    }
}
