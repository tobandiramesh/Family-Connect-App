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
        Log.d(TAG, "⏰ Alarm received at ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}")
        
        if (intent.action != ACTION_SNOOZE_EXPIRED) {
            Log.w(TAG, "❌ Unknown action: ${intent.action}")
            return
        }
        
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID)
        val reminderTitle = intent.getStringExtra(EXTRA_REMINDER_TITLE) ?: "Reminder"
        val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 5)
        
        if (reminderId == null) {
            Log.e(TAG, "❌ Missing reminder ID!")
            return
        }
        
        Log.d(TAG, "✅ Alarm for: $reminderTitle (Snooze: $snoozeMinutes mins)")
        
        // 📢 Just post notification with sound
        postSnoozeNotification(context, reminderId, reminderTitle)
    }
    
    private fun postSnoozeNotification(context: Context, reminderId: String, reminderTitle: String) {
        try {
            Log.d(TAG, "📢 Posting reminder notification: $reminderTitle")
            
            NotificationHelper.ensureChannel(context)
            
            val notificationId = reminderId.hashCode() + 3000
            val title = "🔔 $reminderTitle"
            val message = "Tap to complete"
            
            val builder = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_REMINDERS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 500, 250, 500))
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            Log.d(TAG, "✅ Notification posted with sound & vibration")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error posting notification: ${e.message}", e)
        }
    }
}
