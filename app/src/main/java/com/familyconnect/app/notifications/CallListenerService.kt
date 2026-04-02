package com.familyconnect.app.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Foreground service that maintains Firebase Realtime Database listeners
 * for incoming calls and new messages even when the app is in the background.
 */
class CallListenerService : Service() {

    companion object {
        private const val TAG = "CallListenerService"
        private const val FOREGROUND_ID = 8001
        private const val EXTRA_USER_MOBILE = "extra_user_mobile"
        private const val EXTRA_USER_NAME = "extra_user_name"

        fun start(context: Context, userMobile: String, userName: String) {
            val intent = Intent(context, CallListenerService::class.java).apply {
                putExtra(EXTRA_USER_MOBILE, userMobile)
                putExtra(EXTRA_USER_NAME, userName)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallListenerService::class.java))
        }
    }

    private val database = FirebaseDatabase.getInstance()
    private var callListener: ValueEventListener? = null
    private var messageListener: ValueEventListener? = null
    private var userMobile: String = ""
    private var userName: String = ""

    // Track which call/message IDs we've already shown notifications for
    private val notifiedCallIds = mutableSetOf<String>()
    private val notifiedMessageIds = mutableSetOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            userMobile = intent?.getStringExtra(EXTRA_USER_MOBILE) ?: ""
            userName = intent?.getStringExtra(EXTRA_USER_NAME) ?: ""

            if (userMobile.isBlank()) {
                Log.w(TAG, "No user mobile provided, stopping service")
                stopSelf()
                return START_NOT_STICKY
            }

            Log.d(TAG, "Starting CallListenerService for user: $userMobile")

            try {
                // Ensure notification channels exist
                NotificationHelper.ensureChannel(this)
                
                // Start as a foreground service with a silent persistent notification
                val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_SERVICE)
                    .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                    .setContentTitle("Family Connect")
                    .setContentText("Listening for calls and messages")
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .setSilent(true)
                    .build()

                startForeground(FOREGROUND_ID, notification)
                Log.d(TAG, "Foreground service started with notification")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground service", e)
                // Try to stop and restart gracefully
                stopSelf()
                return START_NOT_STICKY
            }

            try {
                // Start listening for incoming calls and messages
                startCallListener()
                startMessageListener()
                Log.d(TAG, "Listeners started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start listeners", e)
            }

            START_STICKY  // Restart service if killed
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in onStartCommand", e)
            stopSelf()
            START_NOT_STICKY
        }
    }

    private fun normalizeMobile(mobile: String): String = mobile.filter(Char::isDigit)

    private fun sameMobile(a: String, b: String): Boolean {
        val aNorm = normalizeMobile(a)
        val bNorm = normalizeMobile(b)
        if (aNorm.isBlank() || bNorm.isBlank()) return false
        return aNorm == bNorm || aNorm.endsWith(bNorm) || bNorm.endsWith(aNorm)
    }

    private fun startCallListener() {
        callListener?.let {
            database.getReference("chats").removeEventListener(it)
        }

        val normalizedMobile = normalizeMobile(userMobile)
        val chatsRef = database.getReference("chats")

        callListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (threadSnapshot in snapshot.children) {
                    for (callSnapshot in threadSnapshot.child("callRequests").children) {
                        val callId = (callSnapshot.child("callId").value as? String)
                            ?.takeIf { it.isNotBlank() }
                            ?: callSnapshot.key.orEmpty()
                        val toUserId = callSnapshot.child("toUserId").value as? String ?: ""
                        val status = callSnapshot.child("status").value as? String ?: ""
                        val fromUserName = callSnapshot.child("fromUserName").value as? String ?: "Someone"
                        val threadId = (callSnapshot.child("threadId").value as? String)
                            ?.takeIf { it.isNotBlank() }
                            ?: threadSnapshot.key.orEmpty()
                        val callType = callSnapshot.child("callType").value as? String ?: "audio"
                        val createdAt = callSnapshot.child("createdAt").value as? Long ?: 0L

                        // Only process pending calls for this user, and not too old (within 60s)
                        if (sameMobile(toUserId, normalizedMobile)
                            && status == "pending"
                            && callId.isNotBlank()
                            && callId !in notifiedCallIds
                            && (System.currentTimeMillis() - createdAt) < 60_000
                        ) {
                            notifiedCallIds.add(callId)
                            Log.d(TAG, "Incoming call detected: $callId from $fromUserName")

                            NotificationHelper.postIncomingCallNotification(
                                context = this@CallListenerService,
                                callId = callId,
                                threadId = threadId,
                                callerName = fromUserName,
                                callType = callType
                            )
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Call listener cancelled: ${error.message}")
            }
        }

        chatsRef.addValueEventListener(callListener!!)
        Log.d(TAG, "Call listener registered for $userMobile")
    }

    private fun startMessageListener() {
        messageListener?.let {
            database.getReference("chats").removeEventListener(it)
        }

        val chatsRef = database.getReference("chats")

        messageListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Find threads this user participates in, check for unread messages
                for (threadSnapshot in snapshot.children) {
                    val p1Mobile = threadSnapshot.child("participant1Mobile").value as? String ?: ""
                    val p2Mobile = threadSnapshot.child("participant2Mobile").value as? String ?: ""

                    // Only process threads where this user is a participant
                    if (p1Mobile != userMobile && p2Mobile != userMobile) continue

                    val threadId = threadSnapshot.child("threadId").value as? String ?: continue
                    val unreadKey = "unread_$userMobile"
                    val unreadCount = (threadSnapshot.child(unreadKey).value as? Long)?.toInt() ?: 0

                    if (unreadCount > 0) {
                        val lastMessage = threadSnapshot.child("lastMessage").value as? String ?: ""
                        val otherName = if (p1Mobile == userMobile) {
                            threadSnapshot.child("participant2Name").value as? String ?: "Someone"
                        } else {
                            threadSnapshot.child("participant1Name").value as? String ?: "Someone"
                        }

                        // Use threadId as a stable key for notification dedup
                        val notifKey = "${threadId}_${unreadCount}"
                        if (notifKey !in notifiedMessageIds) {
                            notifiedMessageIds.add(notifKey)

                            val body = if (unreadCount > 1) {
                                "$unreadCount new messages"
                            } else {
                                lastMessage.take(100)
                            }
                            NotificationHelper.postMessageNotification(
                                context = this@CallListenerService,
                                id = threadId.hashCode(),
                                senderName = otherName,
                                messageBody = body
                            )
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Message listener cancelled: ${error.message}")
            }
        }

        chatsRef.addValueEventListener(messageListener!!)
        Log.d(TAG, "Message listener registered for $userMobile")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CallListenerService destroyed")

        callListener?.let {
            database.getReference("chats").removeEventListener(it)
        }
        messageListener?.let {
            database.getReference("chats").removeEventListener(it)
        }
        callListener = null
        messageListener = null
        
        // Schedule service restart if it was killed unexpectedly
        Log.d(TAG, "Scheduling service restart...")
        scheduleServiceRestart()
    }
    
    private fun scheduleServiceRestart() {
        try {
            if (userMobile.isNotBlank()) {
                // Re-register the service to restart it
                val intent = Intent(this, CallListenerService::class.java).apply {
                    putExtra(EXTRA_USER_MOBILE, userMobile)
                    putExtra(EXTRA_USER_NAME, userName)
                }
                startForegroundService(intent)
                Log.d(TAG, "Service restart scheduled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule restart: ${e.message}")
        }
    }
}
