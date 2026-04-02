package com.familyconnect.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.familyconnect.app.MainActivity

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

            // Incoming calls channel (high priority with ringtone)
            val callChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
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
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
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
        // Create intent that launches MainActivity with call data
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_FROM_BACKGROUND
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_CALLER_NAME, callerName)
            putExtra("action", "incoming_call")
        }
        
        // Pending intent for full-screen display (lock screen)
        val fullScreenIntent = PendingIntent.getActivity(
            context, 
            callId.hashCode(), 
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Pending intent for tapping the notification body
        val contentIntent = PendingIntent.getActivity(
            context,
            callId.hashCode() + 100,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val acceptIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = ACTION_ACCEPT_CALL
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_CALLER_NAME, callerName)
        }
        val acceptPendingIntent = PendingIntent.getBroadcast(
            context, callId.hashCode() + 1000, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rejectIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = ACTION_REJECT_CALL
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_THREAD_ID, threadId)
        }
        val rejectPendingIntent = PendingIntent.getBroadcast(
            context, callId.hashCode() + 2000, rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callLabel = if (callType == "video") "Incoming Video Call" else "Incoming Audio Call"

        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(callLabel)
            .setContentText("$callerName is calling...")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$callerName is calling you"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(contentIntent)  // ✅ THIS WAS MISSING - handles tapping the notification
            .addAction(android.R.drawable.ic_menu_call, "Accept", acceptPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Reject", rejectPendingIntent)
            .setColorized(true)
            .setColor(0xFF6366F1.toInt())
            .build()

        android.util.Log.d("NotificationHelper", "🔔 POSTING CALL NOTIFICATION: callId=$callId, callerName=$callerName")
        runCatching {
            NotificationManagerCompat.from(context).notify(CALL_NOTIFICATION_ID, notification)
            android.util.Log.d("NotificationHelper", "✅ Notification posted successfully")
        }.onFailure {
            android.util.Log.e("NotificationHelper", "❌ Failed to post notification: ${it.message}")
        }
    }

    fun cancelCallNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(CALL_NOTIFICATION_ID)
    }
}
