package com.familyconnect.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.familyconnect.app.R

/**
 * Full-screen alarm activity for reminder notifications.
 * Shows when a snoozed reminder's alarm time arrives.
 * User must dismiss or snooze.
 */
class ReminderAlarmActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "ReminderAlarmActivity"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_REMINDER_TITLE = "reminder_title"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
    }

    private var reminderId: String? = null
    private var reminderTitle: String? = null
    private var snoozeMinutes: Int = 5
    private var mediaPlayer: android.media.MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminder_alarm)

        Log.d(TAG, "🚨 ReminderAlarmActivity launched!")

        // Get extras
        reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: "Unknown"
        reminderTitle = intent.getStringExtra(EXTRA_REMINDER_TITLE) ?: "Reminder"
        snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 5)

        // Set window flags for lock screen + full screen
        window.apply {
            addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // Setup UI
        setupUI()

        // Play alarm sound
        playAlarmSound()

        // Start vibration
        startVibration()

        Log.d(TAG, "🎬 Activity setup complete. Reminder: $reminderTitle")
    }

    private fun setupUI() {
        val titleView = findViewById<TextView>(R.id.reminder_title)
        val messageView = findViewById<TextView>(R.id.reminder_message)
        val dismissBtn = findViewById<Button>(R.id.btn_dismiss)
        val snoozeBtn = findViewById<Button>(R.id.btn_snooze)

        titleView.text = reminderTitle
        messageView.text = "Tap DISMISS to close or SNOOZE for another $snoozeMinutes minutes"

        dismissBtn.setOnClickListener {
            Log.d(TAG, "❌ Reminder dismissed by user")
            stopAlarm()
            finish()
        }

        snoozeBtn.setOnClickListener {
            Log.d(TAG, "⏱️ Reminder snoozed for $snoozeMinutes mins")
            stopAlarm()
            // Reschedule alarm for snooze period
            scheduleSnoozeAlarm()
            finish()
        }
    }

    private fun playAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = android.media.MediaPlayer.create(this, alarmUri)
            mediaPlayer?.apply {
                isLooping = true  // Loop the sound
                setVolume(1f, 1f)  // Max volume
                start()
                Log.d(TAG, "🔊 Alarm sound started (looping)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error playing alarm sound: ${e.message}", e)
        }
    }

    private fun startVibration() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            // Vibration pattern: wait 0ms, vibrate 500ms, pause 300ms, vibrate 500ms, repeat
            val pattern = longArrayOf(0, 500, 300, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(pattern, 0),  // 0 = repeat from start
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)  // 0 = repeat from start
            }
            Log.d(TAG, "📳 Vibration started (repeating)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting vibration: ${e.message}", e)
        }
    }

    private fun stopAlarm() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
            Log.d(TAG, "🛑 Alarm sound stopped")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping alarm: ${e.message}", e)
        }

        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.cancel()
            } else {
                @Suppress("DEPRECATION")
                vibrator.cancel()
            }
            Log.d(TAG, "🛑 Vibration stopped")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping vibration: ${e.message}", e)
        }
    }

    private fun scheduleSnoozeAlarm() {
        try {
            val nextAlarmTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
            
            // 🔥 UPDATE FIREBASE so data stays in sync
            updateReminderInFirebase(nextAlarmTime)
            
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(this, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_SNOOZE_EXPIRED
                putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
                putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_TITLE, reminderTitle)
                putExtra(ReminderAlarmReceiver.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
            }

            // 🔑 Use unique request code to prevent conflicts
            val uniqueRequestCode = (reminderId?.hashCode() ?: 0) + (nextAlarmTime % 100000).toInt()

            val pendingIntent = PendingIntent.getBroadcast(
                this,
                uniqueRequestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            var alarmScheduled = false
            
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextAlarmTime,
                    pendingIntent
                )
                Log.d(TAG, "✅ setAndAllowWhileIdle SUCCESS")
                alarmScheduled = true
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ setAndAllowWhileIdle failed: ${e.message}")
            }
            
            if (alarmScheduled) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextAlarmTime,
                        pendingIntent
                    )
                    Log.d(TAG, "✅ setExactAndAllowWhileIdle SUCCESS (backup)")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ setExactAndAllowWhileIdle not available: ${e.message}")
                }
            } else {
                Log.e(TAG, "❌ Both alarm methods failed!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error scheduling snooze alarm: ${e.message}", e)
        }
    }
    
    private fun updateReminderInFirebase(nextAlarmTime: Long) {
        try {
            Log.d(TAG, "🔥 Updating Firebase reminder snooze from activity")
            
            val database = com.google.firebase.database.FirebaseDatabase.getInstance(
                "https://family-connect-app-a219b-default-rtdb.asia-southeast1.firebasedatabase.app"
            )
            val reminderRef = database.getReference("reminders/$reminderId")
            
            reminderRef.child("lastSnoozedUntil").setValue(nextAlarmTime).addOnSuccessListener {
                Log.d(TAG, "✅ Updated lastSnoozedUntil in Firebase from activity")
            }
            reminderRef.child("nextNotificationTime").setValue(nextAlarmTime).addOnSuccessListener {
                Log.d(TAG, "✅ Updated nextNotificationTime in Firebase from activity")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating Firebase: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
        Log.d(TAG, "🔚 Activity destroyed")
    }

    override fun onBackPressed() {
        // Prevent back button from closing - must press Dismiss
        Log.d(TAG, "⚠️ Back button pressed (ignored - must press Dismiss)")
    }
}
