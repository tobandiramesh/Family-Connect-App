package com.familyconnect.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.familyconnect.app.data.model.ReminderItem
import com.google.firebase.database.FirebaseDatabase

/**
 * Receives alarm broadcasts when a snoozed reminder's timer expires.
 * Re-triggers the notification and checks if reminder is still active.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ReminderAlarmReceiver"
        const val ACTION_SNOOZE_EXPIRED = "com.familyconnect.app.SNOOZE_EXPIRED"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_REMINDER_TITLE = "reminder_title"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"  // Track snooze duration for rescheduling
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "⏰ Alarm received!")
        
        if (intent.action != ACTION_SNOOZE_EXPIRED) {
            Log.w(TAG, "Unknown action: ${intent.action}")
            return
        }
        
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID)
        val reminderTitle = intent.getStringExtra(EXTRA_REMINDER_TITLE) ?: "Reminder"
        val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 5)  // Default to 5 mins if not provided
        
        if (reminderId == null) {
            Log.e(TAG, "Missing reminder ID in intent extras")
            return
        }
        
        Log.d(TAG, "🔔 Snooze expired for reminder: $reminderId (Title: $reminderTitle, Snooze: $snoozeMinutes mins)")
        
        // 🔊 Post RECURRING snooze notification (with sound & vibration)
        postSnoozeExpiredNotification(context, reminderId, reminderTitle)
        
        // 🔄 RESCHEDULE next alarm for another snooze period (recurring snooze)
        rescheduleSnoozeAlarm(context, reminderId, reminderTitle, snoozeMinutes)
        
        // Optionally: Verify reminder still exists in Firebase and is not completed
        verifyReminderStillActive(reminderId)
    }
    
    private fun postSnoozeExpiredNotification(context: Context, reminderId: String, reminderTitle: String) {
        try {
            Log.d(TAG, "📢 Building RECURRING snooze notification for: $reminderTitle")
            
            // Ensure notification channel exists
            NotificationHelper.ensureChannel(context)
            
            val notificationId = reminderId.hashCode() + 3000
            val title = "🔔 $reminderTitle"
            val message = "Tap to complete or snooze again"
            
            Log.d(TAG, "📝 Title: '$title' | Message: '$message' | ID: $notificationId")
            
            // Build notification directly in receiver with explicit content
            val builder = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_REMINDERS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(false)  // Don't auto-dismiss - user must manually dismiss
                .setOngoing(false)  // Can be swiped away
                .setVibrate(longArrayOf(0, 500, 250, 500))
            
            // Add sound for Android < 8 (channel handles it on Android 8+)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                Log.d(TAG, "🔊 Added sound for Android < 8")
            } else {
                Log.d(TAG, "🔊 Android 8+ - sound handled by CHANNEL_REMINDERS")
            }
            
            val notification = builder.build()
            Log.d(TAG, "✅ Notification built successfully")
            Log.d(TAG, "📢 POSTING recurring snooze notification (will repeat every 5 mins)")
            
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            Log.d(TAG, "✅ Notification posted with ID: $notificationId")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR building notification: ${e.message}", e)
            e.printStackTrace()
        }
    }
    
    private fun rescheduleSnoozeAlarm(context: Context, reminderId: String, reminderTitle: String, snoozeMinutes: Int) {
        try {
            Log.d(TAG, "🔄 Rescheduling next snooze alarm for $snoozeMinutes mins from now")
            
            val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            
            val nextAlarmTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)  // Next snooze period
            
            val intent = android.content.Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ACTION_SNOOZE_EXPIRED
                putExtra(EXTRA_REMINDER_ID, reminderId)
                putExtra(EXTRA_REMINDER_TITLE, reminderTitle)
                putExtra(EXTRA_SNOOZE_MINUTES, snoozeMinutes)
            }
            
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                reminderId.hashCode(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            // Schedule the next recurring alarm
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    nextAlarmTime,
                    pendingIntent
                )
                Log.d(TAG, "✅ Next snooze alarm scheduled for ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(nextAlarmTime))}")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ setExactAndAllowWhileIdle failed, using setAndAllowWhileIdle: ${e.message}")
                alarmManager.setAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    nextAlarmTime,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error rescheduling alarm: ${e.message}", e)
        }
    }
    
    private fun verifyReminderStillActive(reminderId: String) {
        try {
            val database = FirebaseDatabase.getInstance("https://family-connect-app-a219b-default-rtdb.asia-southeast1.firebasedatabase.app")
            val reminderRef = database.getReference("reminders/$reminderId")
            
            reminderRef.get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val completed = snapshot.child("completed").value as? Boolean ?: false
                    if (completed) {
                        Log.d(TAG, "✅ Reminder already completed, ignoring snooze alarm")
                    } else {
                        Log.d(TAG, "✅ Reminder still active after snooze")
                    }
                } else {
                    Log.d(TAG, "ℹ️ Reminder was deleted, ignoring snooze alarm")
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "❌ Error verifying reminder: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception in verifyReminderStillActive: ${e.message}", e)
        }
    }
}
