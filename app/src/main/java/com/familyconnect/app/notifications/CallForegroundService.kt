package com.familyconnect.app.notifications

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import android.app.PendingIntent
import android.util.Log
import com.familyconnect.app.activities.IncomingCallActivity

/**
 * ✅ SIMPLE, CLEAN SERVICE
 * 
 * Shows incoming call notification with:
 * - Simple contentIntent (tap opens activity)
 * - No FullScreenIntent (avoids restrictions)
 * - No foreground-service interference
 * - Works on Android 13-16 (Pixel included)
 */
class CallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val callId = intent?.getStringExtra(NotificationHelper.EXTRA_CALL_ID) ?: ""
        val callerName = intent?.getStringExtra(NotificationHelper.EXTRA_CALLER_NAME) ?: "Unknown"

        Log.d("CALL_DEBUG", "🚀 Showing incoming call notification: $callId from $callerName")

        showIncomingCallNotification(callId, callerName)

        stopSelf() // 🔥 Important: no long-running service

        return START_NOT_STICKY
    }

    private fun showIncomingCallNotification(callId: String, callerName: String) {

        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationHelper.EXTRA_CALL_ID, callId)
            putExtra(NotificationHelper.EXTRA_CALLER_NAME, callerName)
        }

        // 🔥 CRITICAL FIX: Use TaskStackBuilder to create proper app back stack
        // This allows Android to launch non-root activities from background notifications
        val pendingIntent = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(
                0,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // 🔥 MAIN CALL NOTIFICATION
        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Incoming Call")
            .setContentText("$callerName is calling...")
            .setPriority(NotificationCompat.PRIORITY_HIGH)  // HIGH for visibility
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setGroup(NotificationHelper.CALL_GROUP_KEY)  // 🔥 CRITICAL: Use grouped notification
            .setGroupSummary(false)  // This is a child, not the summary
            .setAutoCancel(true)
            .setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE))
            .setVibrate(longArrayOf(0, 500, 300, 500))
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(Math.abs(callId.hashCode()), notification)
        
        // 🔥 POST DUMMY SUMMARY NOTIFICATION
        // Forces Android to treat as grouped → allows normal tap behavior
        NotificationHelper.postGroupSummaryNotification(this)
        
        Log.d("CALL_DEBUG", "✅ Main notification + summary posted successfully")
    }
}

