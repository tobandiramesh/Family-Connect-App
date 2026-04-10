package com.familyconnect.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "⏰ Alarm received!")
        
        if (intent.action != ACTION_SNOOZE_EXPIRED) {
            Log.w(TAG, "Unknown action: ${intent.action}")
            return
        }
        
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID)
        val reminderTitle = intent.getStringExtra(EXTRA_REMINDER_TITLE)
        
        if (reminderId == null || reminderTitle == null) {
            Log.e(TAG, "Missing reminder ID or title in intent extras")
            return
        }
        
        Log.d(TAG, "🔔 Snooze expired for reminder: $reminderId ($reminderTitle)")
        
        // 🔊 Post notification that snooze expired (WITH SOUND & VIBRATION)
        NotificationHelper.postReminderNotification(
            context,
            reminderId.hashCode() + 3000,
            "🔔 Snoozed Reminder",
            "$reminderTitle - snooze ended!"
        )
        
        // Optionally: Verify reminder still exists in Firebase and is not completed
        verifyReminderStillActive(reminderId)
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
