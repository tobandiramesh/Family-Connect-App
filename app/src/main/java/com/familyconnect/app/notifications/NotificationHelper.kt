package com.familyconnect.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.familyconnect.app.MainActivity
import com.familyconnect.app.IncomingCallActivity

object NotificationHelper {
    private const val CHANNEL_ID = "family_connect_updates"
    const val CHANNEL_CALLS = "family_connect_calls"
    const val CHANNEL_MESSAGES = "family_connect_messages"
    const val CHANNEL_SERVICE = "family_connect_service"

    const val CALL_NOTIFICATION_ID = 9001
    const val ACTION_ACCEPT_CALL = "com.familyconnect.app.ACTION_ACCEPT_CALL"
    const val ACTION_REJECT_CALL = "com.familyconnect.app.ACTION_REJECT_CALL"
    const val EXTRA_CALL_ID = "extra_call_id"
    const val EXTRA_THREAD_ID = "extra_thread_id"
    const val EXTRA_CALLER_NAME = "extra_caller_name"
    const val EXTRA_CALL_TYPE = "extra_call_type"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)

            // General updates channel
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Family Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "Event, task, and message reminders"
            manager.createNotificationChannel(channel)

            // Incoming calls channel (MAX priority with ringtone)
            val callChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "Audio and video call notifications"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                enableLights(true)
                setBypassDnd(true)  // Allow sound even in Do Not Disturb mode
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(callChannel)

            // Chat messages channel
            val msgChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New chat message notifications"
                enableVibration(true)
            }
            manager.createNotificationChannel(msgChannel)

            // Foreground service channel (low priority, silent)
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the app listening for calls and messages"
                setShowBadge(false)
            }
            manager.createNotificationChannel(serviceChannel)
        }
    }

    fun post(context: Context, id: Int, title: String, body: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    fun postMessageNotification(context: Context, id: Int, senderName: String, messageBody: String) {
        // ✅ SIMPLE: Direct intent to MainActivity
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, id, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Message from $senderName")
            .setContentText(messageBody.take(100))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    fun postIncomingCallNotification(
        context: Context,
        callId: String,
        threadId: String,
        callerName: String,
        callType: String
    ) {
        Log.d("NotificationHelper", "📢 postIncomingCallNotification: callId=$callId, caller=$callerName")
        
        val intent = Intent(context, IncomingCallActivity::class.java).apply {
            action = "com.familyconnect.app.INCOMING_CALL"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_CALLER_NAME, callerName)
            putExtra(EXTRA_CALL_TYPE, callType)
        }

        // Use FLAG_IMMUTABLE with unique requestCode based on callId
        val requestCode = Math.abs(callId.hashCode())
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Generate unique notification ID
        val notificationId = Math.abs(callId.hashCode())
        
        Log.d("NotificationHelper", "   📋 Notification ID: $notificationId, Request Code: $requestCode")
        Log.d("NotificationHelper", "   🔗 PendingIntent created for IncomingCallActivity")

        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("📞 Incoming Call")
            .setContentText("$callerName is calling...")
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)  // Don't auto-cancel so user intentionally dismisses
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))  // Explicit sound
            .setVibrate(longArrayOf(0, 500, 300, 500))  // Vibration pattern
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            Log.d("NotificationHelper", "✅ Notification posted successfully with ID: $notificationId")
        } catch (e: Exception) {
            Log.e("NotificationHelper", "❌ Error posting notification: ${e.message}", e)
        }
    }

    fun cancelCallNotification(context: Context, callId: String) {
        val notificationId = callId.hashCode().let { if (it < 0) -it else it }
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}
